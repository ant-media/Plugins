package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static io.antmedia.plugin.TestReflect.*;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MoQMuxer;
import io.antmedia.muxer.MuxAdaptor;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MoQPluginTest {

    private static final String NO_SUCH_ROOT = "/tmp/__moq_no_such_dir__";

    private MoQPlugin plugin;
    private ApplicationContext context;
    private Vertx vertx;
    private IAntMediaStreamHandler streamHandler;
    private IScope scope;
    private AppSettings appSettings;
    private DataStore dataStore;
    private MoQStreamFetcher fakeFetcher;

    // The relay lifecycle lives in JVM-wide statics. Snapshot them once here instead of letting
    // every test carry its own save/restore, where one escaped exception poisons the rest of the run.
    private Process savedRelayProcess;
    private long savedRestartAttempt;
    private boolean savedHookRegistered;
    private boolean savedRelayStopping;
    private String savedRed5Root;

    @Before
    public void setUp() throws Exception {
        savedRelayProcess   = relaySlot().get();
        savedRestartAttempt = (long) staticField(MoQPlugin.class, "lastRelayRestartAttempt");
        savedHookRegistered = (boolean) staticField(MoQPlugin.class, "shutdownHookRegistered");
        savedRelayStopping  = (boolean) staticField(MoQPlugin.class, "relayStopping");
        savedRed5Root       = System.getProperty("red5.root");

        plugin = spy(new MoQPlugin());

        context = mock(ApplicationContext.class);
        vertx = mock(Vertx.class);
        streamHandler = mock(IAntMediaStreamHandler.class);
        scope = mock(IScope.class);
        appSettings = mock(AppSettings.class);
        dataStore = mock(DataStore.class);
        fakeFetcher = mock(MoQStreamFetcher.class);

        when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(streamHandler);
        when(context.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(vertx);
        when(streamHandler.getScope()).thenReturn(scope);
        when(scope.getName()).thenReturn("live");
        when(streamHandler.getAppSettings()).thenReturn(appSettings);
        when(streamHandler.getDataStore()).thenReturn(dataStore);
        when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);
        when(appSettings.getCustomSetting(any())).thenReturn(null);

        // Disable the embedded relay to keep init side effects out of tests
        MoQSettings safe = new MoQSettings();
        safe.setUseEmbeddedRelay(false);
        doReturn(safe).when(plugin).loadSettings();

        // Real fetcher creation would NPE on a mock IScope, so stub it
        doReturn(fakeFetcher).when(plugin).createFetcher(any(), any(), any(), any());

        plugin.setApplicationContext(context);
    }

    @After
    public void restoreRelayStatics() {
        Process leftover = relaySlot().get();
        if (leftover != null && leftover != savedRelayProcess) {
            leftover.destroyForcibly();
        }
        relaySlot().set(savedRelayProcess);
        setStaticField(MoQPlugin.class, "lastRelayRestartAttempt", savedRestartAttempt);
        setStaticField(MoQPlugin.class, "shutdownHookRegistered", savedHookRegistered);
        setStaticField(MoQPlugin.class, "relayStopping", savedRelayStopping);

        if (savedRed5Root != null) {
            System.setProperty("red5.root", savedRed5Root);
        } else {
            System.clearProperty("red5.root");
        }

        Thread.interrupted(); // a couple of tests raise the interrupt flag on purpose
    }

    @Test
    public void testLoadSettings() throws Exception {
        MoQPlugin p = new MoQPlugin();
        AppSettings as = mock(AppSettings.class);
        setField(p, "appSettings", as);

        // No JSON: defaults
        when(as.getCustomSetting("plugin.moq")).thenReturn(null);
        MoQSettings def = p.loadSettings();
        assertTrue(def.isUseEmbeddedRelay());
        assertEquals("", def.getMoqCdnUrl());
        assertEquals(2000, def.getIngestPollIntervalMs());

        // Valid JSON: parsed
        when(as.getCustomSetting("plugin.moq"))
                .thenReturn("{\"useEmbeddedRelay\":false,\"moqCdnUrl\":\"https://cdn.example.com/token\"}");
        MoQSettings parsed = p.loadSettings();
        assertFalse(parsed.isUseEmbeddedRelay());
        assertEquals("https://cdn.example.com/token", parsed.getMoqCdnUrl());

        // Garbage JSON: defaults
        when(as.getCustomSetting("plugin.moq")).thenReturn("not valid json {{{");
        MoQSettings fallback = p.loadSettings();
        assertTrue(fallback.isUseEmbeddedRelay());
        assertEquals("", fallback.getMoqCdnUrl());
    }

    @Test
    public void testStreamStarted_withCdn_publishesToRelayAndCdn() throws Exception {
        MoQSettings withCdn = new MoQSettings();
        withCdn.setUseEmbeddedRelay(false);
        withCdn.setExternalRelayUrl("https://relay.example.com/moq");
        withCdn.setMoqCdnUrl("https://cdn.example.com/token");
        doReturn(withCdn).when(plugin).loadSettings();

        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);

        plugin.streamStarted(broadcast("s1"));

        ConcurrentMap<String, Set<MoQMuxer>> map = getField(plugin, "muxersByStream");
        Set<String> targets = new HashSet<>();
        for (MoQMuxer muxer : map.get("s1")) {
            targets.add(getField(muxer, "relayUrl"));
        }
        assertEquals(new HashSet<>(Arrays.asList("https://relay.example.com/moq", "https://cdn.example.com/token")), targets);
    }

    @Test
    public void testStreamStarted() throws Exception {
        ConcurrentMap<String, Set<MoQMuxer>> map = getField(plugin, "muxersByStream");

        // No mux adaptor: nothing tracked
        when(streamHandler.getMuxAdaptor("ghost")).thenReturn(null);
        plugin.streamStarted(broadcast("ghost"));
        assertFalse(map.containsKey("ghost"));

        // Adaptor present: muxer added and tracked
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);
        when(adaptor.getEncoderSettingsList()).thenReturn(null); // no ABR ladder configured

        plugin.streamStarted(broadcast("s1"));
        verify(adaptor, atLeastOnce()).addMuxer(any(MoQMuxer.class), eq(0));
        assertTrue(map.containsKey("s1"));
        assertEquals("without an ABR ladder only the source muxer is attached", 1, map.get("s1").size());
    }

    @Test
    public void testStreamStarted_withEncoderSettings_addsOneMuxerPerVariant() throws Exception {
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);
        when(adaptor.getEncoderSettingsList()).thenReturn(Arrays.asList(
                new EncoderSettings(360, 500_000, 64_000, false),
                new EncoderSettings(720, 2_000_000, 128_000, false)
        ));

        plugin.streamStarted(broadcast("s1"));

        // 1 source muxer (height=0) + 2 variants (360, 720)
        verify(adaptor).addMuxer(any(MoQMuxer.class), eq(0));
        verify(adaptor).addMuxer(any(MoQMuxer.class), eq(360));
        verify(adaptor).addMuxer(any(MoQMuxer.class), eq(720));

        ConcurrentMap<String, Set<MoQMuxer>> map = getField(plugin, "muxersByStream");
        assertEquals(3, map.get("s1").size());
    }

    @Test
    public void testAddMuxers_addMuxerRejected_muxerIsNotTracked() throws Exception {
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.getEncoderSettingsList()).thenReturn(Arrays.asList(
                new EncoderSettings(360, 500_000, 64_000, false)));
        // MuxAdaptor refuses both muxers (e.g. codec unsupported)
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(false);

        plugin.streamStarted(broadcast("s1"));

        ConcurrentMap<String, Set<MoQMuxer>> map = getField(plugin, "muxersByStream");
        assertTrue("a rejected muxer must not be tracked, or writeTrailer would never run on it",
                map.get("s1").isEmpty());
    }

    @Test
    public void testStreamStarted_webRtc_addsTheSourceMuxerAtTheSourceHeight() {
        // WebRTC (!directMuxingSupported) matches muxers to encoders by exact height, so height 0
        // would never match and the muxer would get no data
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(false);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);

        try (AVCodecParameters codecpar = new AVCodecParameters()) {
            codecpar.height(720);
            when(adaptor.getVideoCodecParameters()).thenReturn(codecpar);

            plugin.streamStarted(broadcast("s1"));
            verify(adaptor).addMuxer(any(MoQMuxer.class), eq(720));
        }

        // Codec parameters not negotiated yet: nothing better than 0 to fall back to
        MuxAdaptor notNegotiated = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s2")).thenReturn(notNegotiated);
        when(notNegotiated.directMuxingSupported()).thenReturn(false);
        when(notNegotiated.getVideoCodecParameters()).thenReturn(null);
        when(notNegotiated.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);

        plugin.streamStarted(broadcast("s2"));

        verify(notNegotiated).addMuxer(any(MoQMuxer.class), eq(0));
    }

    @Test
    public void testStreamFinished() throws Exception {
        // Never started: nothing to remove
        MuxAdaptor unknown = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("ghost")).thenReturn(unknown);
        plugin.streamFinished(broadcast("ghost"));
        verify(unknown, never()).removeMuxer(any());

        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);

        plugin.streamStarted(broadcast("s1"));
        plugin.streamFinished(broadcast("s1"));
        verify(adaptor).removeMuxer(any(MoQMuxer.class));

        // Adaptor disappears between start and finish (race, or a late teardown): the null guard
        // in streamFinished skips removeMuxer but the tracking entry still has to go
        plugin.streamStarted(broadcast("s1"));
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(null);
        plugin.streamFinished(broadcast("s1"));

        verify(adaptor, times(1)).removeMuxer(any(MoQMuxer.class));
        ConcurrentMap<String, ?> map = getField(plugin, "muxersByStream");
        assertFalse("the tracking entry has to go even when the adaptor is gone", map.containsKey("s1"));
    }

    @Test
    public void testIngestGetters() throws Exception {
        // Empty before any ingest is started
        assertTrue(plugin.getActiveIngestStreamIds().isEmpty());
        assertNull(plugin.getIngestHandler("unknown"));

        // After populating activeIngests directly, both getters reflect it
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("s1", fakeFetcher);
        assertSame(fakeFetcher, plugin.getIngestHandler("s1"));
        assertTrue(plugin.getActiveIngestStreamIds().contains("s1"));
    }

    @Test
    public void testStartIngest() throws Exception {
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");

        // Stream already in DB: fetcher created, no save needed
        Broadcast known = mock(Broadcast.class);
        when(dataStore.get("s1")).thenReturn(known);
        plugin.startIngest("s1");
        verify(plugin).createFetcher(eq("s1"), eq("live"), anyString(), eq(scope));
        verify(fakeFetcher).startStream();
        verify(dataStore, never()).save(any(Broadcast.class));
        assertSame(fakeFetcher, ingests.get("s1"));

        // Unknown stream + acceptOnly: early return, no save, no fetcher
        when(dataStore.get("ghost")).thenReturn(null);
        when(appSettings.isAcceptOnlyStreamsInDataStore()).thenReturn(true);
        plugin.startIngest("ghost");
        verify(dataStore, never()).save(any(Broadcast.class));
        verify(plugin, never()).createFetcher(eq("ghost"), any(), any(), any());
        assertFalse(ingests.containsKey("ghost"));

        // Unknown stream + acceptAll: saves a Broadcast and creates a fetcher
        when(dataStore.get("new")).thenReturn(null);
        when(appSettings.isAcceptOnlyStreamsInDataStore()).thenReturn(false);
        plugin.startIngest("new");
        verify(dataStore).save(any(Broadcast.class));
        verify(plugin).createFetcher(eq("new"), eq("live"), anyString(), eq(scope));

        // createFetcher throws: caught and logged, no entry added
        Broadcast doomed = mock(Broadcast.class);
        when(dataStore.get("boom")).thenReturn(doomed);
        doThrow(new RuntimeException("simulated bind failure"))
                .when(plugin).createFetcher(eq("boom"), any(), any(), any());
        plugin.startIngest("boom");
        assertFalse(ingests.containsKey("boom"));
    }

    @Test
    public void testStopIngest() throws Exception {
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("s1", fakeFetcher);

        plugin.stopIngest("s1");
        verify(fakeFetcher).stopStream();
        assertFalse(ingests.containsKey("s1"));

        // Non-existing: no throw, no second stopStream call
        plugin.stopIngest("nope");
        verify(fakeFetcher, times(1)).stopStream();
    }

    @Test
    public void testDestroy() throws Exception {
        MoQStreamFetcher second = mock(MoQStreamFetcher.class);
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("a", fakeFetcher);
        ingests.put("b", second);

        plugin.destroy();

        verify(fakeFetcher).stopStream();
        verify(second).stopStream();
        assertTrue(ingests.isEmpty());
        // Both timers cancelled: the log poller directly, the announce poller through stop()
        verify(vertx, times(2)).cancelTimer(anyLong());

        // Spring can call @PreDestroy again on a failed shutdown, so the second pass must be inert
        plugin.destroy();
        verify(vertx, times(2)).cancelTimer(anyLong());
        verify(fakeFetcher, times(1)).stopStream();

        // It can also fire on a plugin whose setApplicationContext never completed, which leaves
        // both vertx and the announce poller null
        new MoQPlugin().destroy();
    }

    @Test
    public void testSetApplicationContext() throws Exception {
        verify(streamHandler).addStreamListener(plugin);

        // Fresh vertx + fresh context so we count only this plugin's interactions
        Vertx freshVertx = mock(Vertx.class);
        // Distinct ids, as real Vert.x hands out: 7 to the log poller, 8 to the announce poller.
        when(freshVertx.setPeriodic(anyLong(), any())).thenReturn(7L, 8L);
        ApplicationContext freshCtx = mock(ApplicationContext.class);
        when(freshCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(streamHandler);
        when(freshCtx.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(freshVertx);

        MoQPlugin p = spy(new MoQPlugin());
        doReturn(fakeFetcher).when(p).createFetcher(any(), any(), any(), any());
        MoQSettings ingest = new MoQSettings();
        ingest.setUseEmbeddedRelay(false);
        doReturn(ingest).when(p).loadSettings();

        p.setApplicationContext(freshCtx);

        // Two periodics registered: one for log polling, one for the announce poller
        verify(freshVertx, times(2)).setPeriodic(anyLong(), any());
        assertNotNull(getField(p, "announcePoller"));

        // destroy() must cancel both: the log poller directly, the announce poller via stop()
        p.destroy();
        verify(freshVertx).cancelTimer(7L);
        verify(freshVertx).cancelTimer(8L);
    }

    @Test
    public void testPollCliLogs_ingestSide() throws Exception {
        // f1 has small data (drained in one read), f2 has > 2000 bytes (forces the skip-overflow loop),
        // f3 returns null (must be tolerated)
        ByteArrayInputStream small = new ByteArrayInputStream("hello\n".getBytes());
        byte[] big = new byte[3000];
        ByteArrayInputStream large = new ByteArrayInputStream(big);

        MoQStreamFetcher f1 = mock(MoQStreamFetcher.class);
        when(f1.getLogStream()).thenReturn(small);
        MoQStreamFetcher f2 = mock(MoQStreamFetcher.class);
        when(f2.getLogStream()).thenReturn(large);
        MoQStreamFetcher f3 = mock(MoQStreamFetcher.class);
        when(f3.getLogStream()).thenReturn(null);

        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("a", f1);
        ingests.put("b", f2);
        ingests.put("c", f3);

        Method poll = MoQPlugin.class.getDeclaredMethod("pollCliLogs");
        poll.setAccessible(true);
        poll.invoke(plugin);

        verify(f1).getLogStream();
        verify(f2).getLogStream();
        verify(f3).getLogStream();
        assertEquals("small stream fully drained", 0, small.available());
        assertEquals("large stream's overflow skipped past the 2000-byte read window", 0, large.available());
    }

    @Test
    public void testPollCliLogs_muxerSide() throws Exception {
        // Separate test because mock(MoQMuxer.class) requires FFmpeg natives
        MoQMuxer muxer = mock(MoQMuxer.class);
        when(muxer.getCliErrorStream()).thenReturn(new ByteArrayInputStream("muxlog\n".getBytes()));
        when(muxer.getOutputURL()).thenReturn("moq://live/s1/source");

        ConcurrentMap<String, Set<MoQMuxer>> muxers = getField(plugin, "muxersByStream");
        Set<MoQMuxer> set = ConcurrentHashMap.newKeySet();
        set.add(muxer);
        muxers.put("s1", set);

        Method poll = MoQPlugin.class.getDeclaredMethod("pollCliLogs");
        poll.setAccessible(true);
        poll.invoke(plugin);

        verify(muxer).getCliErrorStream();
        verify(muxer).getOutputURL();
    }

    @Test
    public void testReadAvailable() throws Exception {
        Method readAvailable = MoQPlugin.class.getDeclaredMethod(
                "readAvailable", InputStream.class, String.class);
        readAvailable.setAccessible(true);

        // available()==0 short-circuits after the first available() call
        InputStream empty = spy(new ByteArrayInputStream(new byte[0]));
        readAvailable.invoke(plugin, empty, "empty");
        verify(empty).available();
        verify(empty, never()).read(any(byte[].class), anyInt(), anyInt());

        // IOException from available() is swallowed, read() is never reached
        InputStream throwing = mock(InputStream.class);
        when(throwing.available()).thenThrow(new IOException("boom"));
        readAvailable.invoke(plugin, throwing, "throwing");
        verify(throwing).available();
        verify(throwing, never()).read(any(byte[].class), anyInt(), anyInt());

        readAvailable.invoke(plugin, null, "null"); // must not throw

        // Claims data is waiting but read() and skip() hand back nothing: the skip loop has to
        // give up instead of spinning forever on the vert.x timer thread.
        AtomicInteger skips = new AtomicInteger();
        InputStream liar = new InputStream() {
            @Override public int available() { return 4096; }
            @Override public int read() { return -1; }
            @Override public int read(byte[] b, int off, int len) { return 0; }
            @Override public long skip(long n) { skips.incrementAndGet(); return 0; }
        };

        readAvailable.invoke(plugin, liar, "liar");

        assertEquals("a stream that never yields must be abandoned after one skip attempt",
                1, skips.get());
    }

    @Test
    public void testStartRelay_spawnFails_catchesIOException() throws Exception {
        relaySlot().set(null);
        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            // Replace buildRelayProcessBuilder with one that points at a non-existent binary,
            // so .start() throws IOException and startRelay's catch path runs
            mocked.when(MoQPlugin::buildRelayProcessBuilder)
                  .thenReturn(new ProcessBuilder("/__moq_no_such_binary_" + System.nanoTime()));

            Method startRelay = MoQPlugin.class.getDeclaredMethod("startRelay");
            startRelay.setAccessible(true);
            startRelay.invoke(null);

            assertNull("relayProcess stays null when spawn fails", relaySlot().get());
        }
    }

    @Test
    public void testMaybeRestartRelay_guards() {
        // A relay that is still up is left completely alone
        Process alive = mock(Process.class);
        when(alive.isAlive()).thenReturn(true);
        relaySlot().set(alive);
        setStaticField(MoQPlugin.class, "lastRelayRestartAttempt", 12345L);

        MoQPlugin.maybeRestartRelay(alive);

        assertSame("a live relay must stay in the slot", alive, relaySlot().get());
        assertEquals("a live relay must not bump lastRelayRestartAttempt",
                12345L, (long) staticField(MoQPlugin.class, "lastRelayRestartAttempt"));
        verify(alive, never()).exitValue();

        // A dead relay inside the grace window is left alone too, or a relay that cannot bind
        // would be respawned on every single log poll
        Process dead = mock(Process.class);
        when(dead.isAlive()).thenReturn(false);
        relaySlot().set(dead);
        long graceStart = System.currentTimeMillis();
        setStaticField(MoQPlugin.class, "lastRelayRestartAttempt", graceStart);

        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            MoQPlugin.maybeRestartRelay(dead);
            mocked.verify(MoQPlugin::buildRelayProcessBuilder, never());
        }

        assertSame("the grace window must keep the dead process in the slot", dead, relaySlot().get());
        assertEquals(graceStart, (long) staticField(MoQPlugin.class, "lastRelayRestartAttempt"));
        verify(dead, never()).exitValue();
    }

    @Test
    public void testMaybeRestartRelay_deadProcess_clearsSlotAndAttemptsRespawn() {
        Process dead = mock(Process.class);
        when(dead.isAlive()).thenReturn(false);
        when(dead.exitValue()).thenReturn(139);
        relaySlot().set(dead);
        setStaticField(MoQPlugin.class, "lastRelayRestartAttempt", 0L); // outside grace window

        // Stub spawn to a bogus binary, so startRelay's IOException path runs and no real process leaks
        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            mocked.when(MoQPlugin::buildRelayProcessBuilder)
                  .thenReturn(new ProcessBuilder("/__moq_no_such_binary_" + System.nanoTime()));

            long before = System.currentTimeMillis();
            MoQPlugin.maybeRestartRelay(dead);

            // Slot CAS'd out, attempt time bumped past the grace floor, exit code was logged
            assertNull("dead process must be CAS'd out of the slot", relaySlot().get());
            long after = (long) staticField(MoQPlugin.class, "lastRelayRestartAttempt");
            assertTrue("lastRelayRestartAttempt must be bumped to now", after >= before);
            verify(dead).exitValue();
        }
    }

    /**
     * This one lets the real startRelay register its JVM shutdown hook, which cannot be removed
     * again without a reference to the thread. Harmless because the teardown empties the relay
     * slot, so the hook finds nothing to kill when the surefire JVM exits.
     */
    @Test(timeout = 30_000)
    public void testStartRelay_successPath_setsSlotAndRegistersHookOnce() throws Exception {
        relaySlot().set(null);
        setStaticField(MoQPlugin.class, "shutdownHookRegistered", false);

        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            // /bin/sh exit 0 is universally available, exits immediately, harmless
            mocked.when(MoQPlugin::buildRelayProcessBuilder)
                  .thenReturn(new ProcessBuilder("/bin/sh", "-c", "exit 0"));

            Method startRelay = MoQPlugin.class.getDeclaredMethod("startRelay");
            startRelay.setAccessible(true);

            // 1st call: spawns and registers the hook
            startRelay.invoke(null);
            Process firstProc = relaySlot().get();
            assertNotNull("startRelay success path must populate the slot", firstProc);
            assertTrue("shutdownHookRegistered must be flipped to true",
                    (boolean) staticField(MoQPlugin.class, "shutdownHookRegistered"));

            // 2nd call: relay already in the slot, early return, no re-spawn
            startRelay.invoke(null);
            assertSame("startRelay with non-null slot must short-circuit", firstProc, relaySlot().get());

            // 3rd call after clearing the slot: spawns again but skips the hook block
            firstProc.destroy();
            firstProc.waitFor();
            relaySlot().set(null);
            startRelay.invoke(null);
            assertNotNull("re-entry must spawn a new process", relaySlot().get());
            assertTrue("hook stays registered across restarts",
                    (boolean) staticField(MoQPlugin.class, "shutdownHookRegistered"));
        }
    }

    @Test
    public void testDestroyRelayOnShutdown() throws Exception {
        relaySlot().set(null);
        MoQPlugin.destroyRelayOnShutdown(); // an empty slot must not throw
        assertNull(relaySlot().get());

        // A relay that honours SIGTERM is never escalated
        Process cooperative = mock(Process.class);
        when(cooperative.waitFor(anyLong(), any())).thenReturn(true);
        relaySlot().set(cooperative);

        MoQPlugin.destroyRelayOnShutdown();

        verify(cooperative).destroy();
        verify(cooperative, never()).destroyForcibly();
        assertNull("shutdown must clear the slot", relaySlot().get());

        // One that sits past the grace window gets killed
        Process stubborn = mock(Process.class);
        when(stubborn.waitFor(anyLong(), any())).thenReturn(false);
        relaySlot().set(stubborn);

        MoQPlugin.destroyRelayOnShutdown();

        verify(stubborn).destroyForcibly();

        // An interrupt while waiting also kills, and must be re-raised rather than swallowed.
        // The teardown clears the flag again.
        Process interrupts = mock(Process.class);
        when(interrupts.waitFor(anyLong(), any())).thenThrow(new InterruptedException("shutdown"));
        relaySlot().set(interrupts);

        MoQPlugin.destroyRelayOnShutdown();

        verify(interrupts).destroyForcibly();
        assertTrue("the interrupt must be re-raised", Thread.currentThread().isInterrupted());
    }

    @Test
    public void testMaybeRestartRelay_whileStopping_doesNotRespawn() throws Exception {
        Process p = mock(Process.class);
        when(p.isAlive()).thenReturn(true);
        when(p.waitFor(anyLong(), any())).thenReturn(true);
        relaySlot().set(p);
        MoQPlugin.destroyRelayOnShutdown();

        // the log poller fires after the hook ran; it must not bring the relay back
        Process dead = mock(Process.class);
        when(dead.isAlive()).thenReturn(false);
        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            MoQPlugin.maybeRestartRelay(dead);
            mocked.verify(MoQPlugin::buildRelayProcessBuilder, never());
        }

        assertNull("relay must stay down once shutdown started", relaySlot().get());
    }

    private static ProcessHandle handle(long pid, String command, Long parentPid) {
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(info.command()).thenReturn(Optional.ofNullable(command));
        ProcessHandle h = mock(ProcessHandle.class);
        when(h.pid()).thenReturn(pid);
        when(h.info()).thenReturn(info);
        if (parentPid == null) {
            when(h.parent()).thenReturn(Optional.empty());
        } else {
            ProcessHandle parent = mock(ProcessHandle.class);
            when(parent.pid()).thenReturn(parentPid);
            when(h.parent()).thenReturn(Optional.of(parent));
        }
        return h;
    }

    @Test
    public void testIsOrphanedMoq_reapsOnlyOurStrandedBinaries() {
        Set<String> ours = Set.of("/usr/local/bin/moq", "/usr/local/bin/moq-relay");

        assertTrue("a stranded moq-relay must be reaped",
                MoQPlugin.isOrphanedMoq(handle(100, "/usr/local/bin/moq-relay", 1L), ours));

        assertFalse("an unrelated binary must never be reaped",
                MoQPlugin.isOrphanedMoq(handle(101, "/usr/bin/ffmpeg", 1L), ours));

        assertFalse("a relay owned by a live parent must never be reaped",
                MoQPlugin.isOrphanedMoq(handle(102, "/usr/local/bin/moq-relay", 4242L), ours));

        assertFalse("a process we cannot inspect must never be reaped",
                MoQPlugin.isOrphanedMoq(handle(103, null, 1L), ours));
    }

    @Test(timeout = 60_000)
    public void testReapOrphanedProcesses_realScanSparesBystanders() throws Exception {
        Process child = new ProcessBuilder("/bin/sh", "-c", "sleep 30").start();
        try {
            MoQPlugin.reapOrphanedProcesses();
            assertTrue("a live child of this JVM must never be reaped", child.isAlive());
        } finally {
            child.destroyForcibly();
            child.waitFor();
        }
    }

    @Test
    public void testPollCliLogs_deadRelay_triggersRestart() throws Exception {
        Process dead = mock(Process.class);
        when(dead.isAlive()).thenReturn(false);
        when(dead.exitValue()).thenReturn(137);
        when(dead.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        relaySlot().set(dead);
        setStaticField(MoQPlugin.class, "lastRelayRestartAttempt", 0L);

        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            mocked.when(MoQPlugin::buildRelayProcessBuilder)
                  .thenReturn(new ProcessBuilder("/__moq_no_such_binary_" + System.nanoTime()));

            Method poll = MoQPlugin.class.getDeclaredMethod("pollCliLogs");
        poll.setAccessible(true);
        poll.invoke(plugin);

            // pollCliLogs reached the relay branch, drained stdin, and routed dead process to restart
            verify(dead).getInputStream();
            verify(dead).exitValue();
            assertNull("pollCliLogs must propagate dead process clearing through maybeRestartRelay",
                    relaySlot().get());
        }
    }

    @Test
    public void testBuildRelayProcessBuilder() throws Exception {
        // No cert files present -> self-signed mode with --tls-generate
        System.setProperty("red5.root", "/tmp/__moq_no_such_dir__" + System.nanoTime());
        ProcessBuilder pb = MoQPlugin.buildRelayProcessBuilder();
        assertTrue(pb.command().contains("--tls-generate"));
        assertFalse(pb.command().contains("--tls-cert"));

        // With cert files present -> HTTPS mode with --tls-cert
        Path root = Files.createTempDirectory("moqRoot");
        Path conf = root.resolve("conf");
        Files.createDirectories(conf);
        Files.writeString(conf.resolve("fullchain.pem"), "cert");
        Files.writeString(conf.resolve("privkey.pem"), "key");
        System.setProperty("red5.root", root.toString());

        ProcessBuilder pb2 = MoQPlugin.buildRelayProcessBuilder();
        assertTrue(pb2.command().contains("--tls-cert"));
        assertFalse(pb2.command().contains("--tls-generate"));
    }

    @Test
    public void testGetRelayUrl_embeddedTracksCertFilesOnDisk() throws Exception {
        MoQSettings embedded = new MoQSettings(); // useEmbeddedRelay defaults to true
        doReturn(embedded).when(plugin).loadSettings();

        // No certs on disk: relay runs with --tls-generate, so we must dial it over http
        System.setProperty("red5.root", "/tmp/__moq_no_such_dir__" + System.nanoTime());
        assertFalse(MoQPlugin.embeddedRelayHasTls());
        assertEquals("http://localhost:4443/moq", plugin.getRelayUrl(embedded));
        assertEquals("the no-arg overload must agree with the explicit one",
                "http://localhost:4443/moq", plugin.getRelayUrl());
        assertTrue("embedded relay means a self-signed cert, so callers must skip verification",
                plugin.isLocalRelay());

        // Half a cert pair is not a cert pair: the relay would fall back to --tls-generate,
        // so the URL must stay http or every client would fail the handshake.
        Path root = Files.createTempDirectory("moqHalfCert");
        Files.createDirectories(root.resolve("conf"));
        Files.writeString(root.resolve("conf").resolve("fullchain.pem"), "cert");
        System.setProperty("red5.root", root.toString());
        assertFalse("cert without key must not count as TLS", MoQPlugin.embeddedRelayHasTls());
        assertEquals("http://localhost:4443/moq", plugin.getRelayUrl(embedded));

        // Full pair: relay serves HTTPS/WSS, so the URL flips to https
        Files.writeString(root.resolve("conf").resolve("privkey.pem"), "key");
        assertTrue(MoQPlugin.embeddedRelayHasTls());
        assertEquals("https://localhost:4443/moq", plugin.getRelayUrl(embedded));

        // External relay is passed through verbatim, certs on disk or not
        MoQSettings external = new MoQSettings();
        external.setUseEmbeddedRelay(false);
        external.setExternalRelayUrl("https://relay.example.com/moq");
        assertEquals("https://relay.example.com/moq", plugin.getRelayUrl(external));
        doReturn(external).when(plugin).loadSettings();
        assertFalse(plugin.isLocalRelay());
    }

    @Test(timeout = 30_000)
    public void testSetApplicationContext_embeddedRelay_startsIt() {
        relaySlot().set(null);
        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            mocked.when(MoQPlugin::buildRelayProcessBuilder)
                  .thenReturn(new ProcessBuilder("/bin/sh", "-c", "exit 0"));

            MoQPlugin p = spy(new MoQPlugin());
            doReturn(fakeFetcher).when(p).createFetcher(any(), any(), any(), any());
            doReturn(new MoQSettings()).when(p).loadSettings(); // useEmbeddedRelay = true

            p.setApplicationContext(context);

            assertNotNull("useEmbeddedRelay must actually spawn the relay on init", relaySlot().get());
        }
    }

    @Test
    public void testLogPollTimer_tickDrivesPollCliLogs() throws Exception {
        Vertx freshVertx = mock(Vertx.class);
        ArgumentCaptor<Handler<Long>> handlers = ArgumentCaptor.forClass(Handler.class);
        when(freshVertx.setPeriodic(anyLong(), handlers.capture())).thenReturn(1L, 2L);
        when(context.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(freshVertx);

        MoQPlugin p = spy(new MoQPlugin());
        doReturn(fakeFetcher).when(p).createFetcher(any(), any(), any(), any());
        MoQSettings noRelay = new MoQSettings();
        noRelay.setUseEmbeddedRelay(false);
        doReturn(noRelay).when(p).loadSettings();
        p.setApplicationContext(context);

        MoQStreamFetcher tracked = mock(MoQStreamFetcher.class);
        when(tracked.getLogStream()).thenReturn(new ByteArrayInputStream("moq says hi\n".getBytes()));
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(p, "activeIngests");
        ingests.put("s1", tracked);

        // Fire the log-poll timer body that setApplicationContext registered
        handlers.getAllValues().get(0).handle(1L);

        verify(tracked).getLogStream();
    }

    @Test
    public void testCreateFetcher_buildsFetcherPointedAtTheRelay() throws Exception {
        // StreamFetcher's constructor resolves AppSettings through the red5 scope
        ApplicationContext appCtx = mock(ApplicationContext.class);
        when(appCtx.getBean(AppSettings.BEAN_NAME)).thenReturn(appSettings);
        IContext red5Ctx = mock(IContext.class);
        when(red5Ctx.getApplicationContext()).thenReturn(appCtx);
        IScope realScope = mock(IScope.class);
        when(realScope.getContext()).thenReturn(red5Ctx);

        MoQPlugin p = spy(new MoQPlugin());
        MoQSettings external = new MoQSettings();
        external.setUseEmbeddedRelay(false);
        external.setExternalRelayUrl("https://relay.example.com/moq");
        doReturn(external).when(p).loadSettings();
        setField(p, "vertx", vertx);

        MoQStreamFetcher fetcher = p.createFetcher("s1", "live", "https://relay.example.com/moq", realScope);
        try {
            assertEquals("live/s1/publish", getField(fetcher, "broadcastName"));
            assertEquals("https://relay.example.com/moq", getField(fetcher, "relayUrl"));
            assertFalse("external relay must keep certificate verification on",
                    (boolean) getField(fetcher, "tlsDisableVerify"));
            assertFalse("the poller owns restart, so the fetcher must not restart itself",
                    fetcher.isRestartStream());
        } finally {
            ((java.net.ServerSocket) getField(fetcher, "serverSocket")).close();
        }
    }

    @Test(timeout = 60_000)
    public void testAwaitOrphanExit() throws Exception {
        Method await = MoQPlugin.class.getDeclaredMethod("awaitOrphanExit", ProcessHandle.class);
        await.setAccessible(true);

        // Already dead: returns without burning the grace window
        Process finished = new ProcessBuilder("/bin/sh", "-c", "exit 0").start();
        finished.waitFor();
        long quick = System.currentTimeMillis();
        await.invoke(null, finished.toHandle());
        assertTrue("an already-dead orphan must not be waited on at all",
                System.currentTimeMillis() - quick < 1000);

        // Still alive past the grace window: logs and gives up rather than blocking startup
        Process stubborn = new ProcessBuilder("/bin/sh", "-c", "sleep 30").start();
        try {
            long start = System.currentTimeMillis();
            await.invoke(null, stubborn.toHandle());
            long waited = System.currentTimeMillis() - start;
            assertTrue("must actually wait out the grace window, got " + waited + "ms", waited >= 1500);
            assertTrue("must give up on a surviving orphan, not wait on it forever", waited < 15_000);
            assertTrue(stubborn.isAlive());
        } finally {
            stubborn.destroyForcibly();
            stubborn.waitFor();
        }
    }

    private static Broadcast broadcast(String streamId) {
        Broadcast b = mock(Broadcast.class);
        when(b.getStreamId()).thenReturn(streamId);
        return b;
    }

    private static AtomicReference<Process> relaySlot() {
        return staticField(MoQPlugin.class, "relayProcess");
    }
}
