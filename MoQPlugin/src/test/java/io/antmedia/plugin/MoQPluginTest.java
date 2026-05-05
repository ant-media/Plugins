package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MoQMuxer;
import io.antmedia.muxer.MuxAdaptor;
import io.vertx.core.Vertx;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MoQPluginTest {

    private MoQPlugin plugin;
    private ApplicationContext context;
    private Vertx vertx;
    private IAntMediaStreamHandler streamHandler;
    private IScope scope;
    private AppSettings appSettings;
    private DataStore dataStore;
    private MoQStreamFetcher fakeFetcher;

    @Before
    public void setUp() throws Exception {
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

        // Disable embedded relay and ingest poller to keep init side effects out of tests
        MoQSettings safe = new MoQSettings();
        safe.setUseEmbeddedRelay(false);
        safe.setIngestEnabled(false);
        doReturn(safe).when(plugin).loadSettings();

        // Real fetcher creation would NPE on a mock IScope, so stub it
        doReturn(fakeFetcher).when(plugin).createFetcher(any(), any(), any(), any());

        plugin.setApplicationContext(context);
    }

    @Test
    public void testSetApplicationContext_wiresUp() {
        verify(streamHandler).addStreamListener(plugin);
        verify(vertx).setPeriodic(anyLong(), any());
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
        assertTrue(def.isIngestEnabled());
        assertEquals(2000, def.getIngestPollIntervalMs());

        // Valid JSON: parsed
        when(as.getCustomSetting("plugin.moq"))
                .thenReturn("{\"useEmbeddedRelay\":false,\"ingestEnabled\":false}");
        MoQSettings parsed = p.loadSettings();
        assertFalse(parsed.isUseEmbeddedRelay());
        assertFalse(parsed.isIngestEnabled());

        // Garbage JSON: defaults
        when(as.getCustomSetting("plugin.moq")).thenReturn("not valid json {{{");
        MoQSettings fallback = p.loadSettings();
        assertTrue(fallback.isUseEmbeddedRelay());
        assertTrue(fallback.isIngestEnabled());
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

        plugin.streamStarted(broadcast("s1"));
        verify(adaptor, atLeastOnce()).addMuxer(any(MoQMuxer.class), eq(0));
        assertTrue(map.containsKey("s1"));
        assertFalse(map.get("s1").isEmpty());
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
    public void testStreamStarted_webRTC_usesSourceHeight() {
        // WebRTC path: !directMuxingSupported && getVideoCodecParameters != null
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(false);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);

        AVCodecParameters codecpar = new AVCodecParameters();
        codecpar.height(720);
        when(adaptor.getVideoCodecParameters()).thenReturn(codecpar);

        plugin.streamStarted(broadcast("s1"));

        // Source muxer added with the actual source height (720), not 0
        verify(adaptor).addMuxer(any(MoQMuxer.class), eq(720));
        codecpar.close();
    }

    @Test
    public void testStreamFinished() {
        // Unknown stream: no-op
        MuxAdaptor unknownAdaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("ghost")).thenReturn(unknownAdaptor);
        plugin.streamFinished(broadcast("ghost"));
        verify(unknownAdaptor, never()).removeMuxer(any());

        // Tracked stream: muxer removed
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);

        plugin.streamStarted(broadcast("s1"));
        plugin.streamFinished(broadcast("s1"));
        verify(adaptor, atLeastOnce()).removeMuxer(any(MoQMuxer.class));
    }

    @Test
    public void testStartIngest() throws Exception {
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");

        // Stream already in DB: fetcher created, no save needed
        when(dataStore.get("s1")).thenReturn(mock(Broadcast.class));
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
        when(dataStore.get("boom")).thenReturn(mock(Broadcast.class));
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
    public void testDestroy_stopsAllIngests() throws Exception {
        MoQStreamFetcher f2 = mock(MoQStreamFetcher.class);
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("a", fakeFetcher);
        ingests.put("b", f2);

        plugin.destroy();

        verify(fakeFetcher).stopStream();
        verify(f2).stopStream();
        assertTrue(ingests.isEmpty());
    }

    @Test
    public void testSetApplicationContext_ingestEnabled_startsAnnouncePoller() throws Exception {
        // Fresh vertx + fresh context so we count only this plugin's interactions
        Vertx freshVertx = mock(Vertx.class);
        when(freshVertx.setPeriodic(anyLong(), any())).thenReturn(7L);
        ApplicationContext freshCtx = mock(ApplicationContext.class);
        when(freshCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(streamHandler);
        when(freshCtx.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(freshVertx);

        MoQPlugin p = spy(new MoQPlugin());
        doReturn(fakeFetcher).when(p).createFetcher(any(), any(), any(), any());
        MoQSettings ingest = new MoQSettings();
        ingest.setUseEmbeddedRelay(false);
        ingest.setIngestEnabled(true);
        doReturn(ingest).when(p).loadSettings();

        p.setApplicationContext(freshCtx);

        // Two periodics registered: one for log polling, one for the announce poller
        verify(freshVertx, times(2)).setPeriodic(anyLong(), any());
        assertNotNull(getField(p, "announcePoller"));

        // destroy() routes through announcePoller.stop() which cancels timer id=7
        p.destroy();
        verify(freshVertx).cancelTimer(7L);
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
    public void testReadAvailable_emptyAndThrowingStreams() throws Exception {
        Method readAvailable = MoQPlugin.class.getDeclaredMethod(
                "readAvailable", InputStream.class, String.class);
        readAvailable.setAccessible(true);

        // available()==0 short-circuits after the first available() call
        InputStream empty = spy(new ByteArrayInputStream(new byte[0]));
        readAvailable.invoke(plugin, empty, "empty");
        verify(empty).available();
        verify(empty, never()).read(any(byte[].class), anyInt(), anyInt());

        // IOException from available() is swallowed; read() never reached
        InputStream throwing = mock(InputStream.class);
        when(throwing.available()).thenThrow(new IOException("boom"));
        readAvailable.invoke(plugin, throwing, "throwing");
        verify(throwing).available();
        verify(throwing, never()).read(any(byte[].class), anyInt(), anyInt());

        // null stream returns immediately without throwing
        readAvailable.invoke(plugin, null, "null");
    }

    @Test
    public void testStartRelay_alreadyRunning_returnsEarly() throws Exception {
        Field f = MoQPlugin.class.getDeclaredField("relayProcess");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Process> ref =
                (java.util.concurrent.atomic.AtomicReference<Process>) f.get(null);
        Process saved = ref.get();
        try {
            Process running = mock(Process.class);
            ref.set(running);

            Method m = MoQPlugin.class.getDeclaredMethod("startRelay");
            m.setAccessible(true);
            m.invoke(null);

            assertSame("the early-return guard should leave our mock process in place",
                    running, ref.get());
            verify(running, never()).destroy();
        } finally {
            ref.set(saved);
        }
    }

    @Test
    public void testStartRelay_spawnFails_catchesIOException() throws Exception {
        Field f = MoQPlugin.class.getDeclaredField("relayProcess");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Process> ref =
                (java.util.concurrent.atomic.AtomicReference<Process>) f.get(null);
        Process saved = ref.get();
        ref.set(null);
        try (org.mockito.MockedStatic<MoQPlugin> mocked = mockStatic(MoQPlugin.class, CALLS_REAL_METHODS)) {
            // Replace buildRelayProcessBuilder with one that points at a non-existent binary,
            // so .start() throws IOException -> startRelay's catch path runs
            mocked.when(MoQPlugin::buildRelayProcessBuilder)
                  .thenReturn(new ProcessBuilder("/__moq_no_such_binary_" + System.nanoTime()));

            Method m = MoQPlugin.class.getDeclaredMethod("startRelay");
            m.setAccessible(true);
            m.invoke(null);

            assertNull("relayProcess stays null when spawn fails", ref.get());
        } finally {
            ref.set(saved);
        }
    }

    @Test
    public void testBuildRelayProcessBuilder() throws Exception {
        String savedRoot = System.getProperty("red5.root");
        try {
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
        } finally {
            if (savedRoot != null) System.setProperty("red5.root", savedRoot);
            else System.clearProperty("red5.root");
        }
    }

    private static Broadcast broadcast(String streamId) {
        Broadcast b = mock(Broadcast.class);
        when(b.getStreamId()).thenReturn(streamId);
        return b;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
