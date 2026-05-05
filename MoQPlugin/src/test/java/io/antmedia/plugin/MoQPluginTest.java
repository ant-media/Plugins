package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MoQMuxer;
import io.antmedia.muxer.MuxAdaptor;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.Set;
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

        // Stop real fetcher creation from blowing up on a mock IScope
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
        Broadcast ghost = mock(Broadcast.class);
        when(ghost.getStreamId()).thenReturn("ghost");
        plugin.streamStarted(ghost);
        assertFalse(map.containsKey("ghost"));

        // Adaptor present: muxer added and tracked
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);
        when(adaptor.getEncoderSettingsList()).thenReturn(null);

        Broadcast b = mock(Broadcast.class);
        when(b.getStreamId()).thenReturn("s1");
        plugin.streamStarted(b);

        verify(adaptor, atLeastOnce()).addMuxer(any(MoQMuxer.class), eq(0));
        assertTrue(map.containsKey("s1"));
        assertFalse(map.get("s1").isEmpty());
    }

    @Test
    public void testStreamFinished() throws Exception {
        // Unknown stream: no-op
        MuxAdaptor unknownAdaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("ghost")).thenReturn(unknownAdaptor);
        Broadcast ghost = mock(Broadcast.class);
        when(ghost.getStreamId()).thenReturn("ghost");
        plugin.streamFinished(ghost);
        verify(unknownAdaptor, never()).removeMuxer(any());

        // Tracked stream: muxer removed
        MuxAdaptor adaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor("s1")).thenReturn(adaptor);
        when(adaptor.directMuxingSupported()).thenReturn(true);
        when(adaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);
        when(adaptor.getEncoderSettingsList()).thenReturn(null);
        Broadcast b = mock(Broadcast.class);
        when(b.getStreamId()).thenReturn("s1");

        plugin.streamStarted(b);
        plugin.streamFinished(b);
        verify(adaptor, atLeastOnce()).removeMuxer(any(MoQMuxer.class));
    }

    @Test
    public void testStartIngest() throws Exception {
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");

        // Stream already in DB: fetcher created, no save needed
        Broadcast existing = mock(Broadcast.class);
        when(dataStore.get("s1")).thenReturn(existing);
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
    }

    @Test
    public void testStopIngest() throws Exception {
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("s1", fakeFetcher);

        // Existing stream: stops and removes
        plugin.stopIngest("s1");
        verify(fakeFetcher).stopStream();
        assertFalse(ingests.containsKey("s1"));

        // Non-existing: no throw, no second stopStream
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
