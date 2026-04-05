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

    @Before
    public void setUp() throws Exception {
        plugin = spy(new MoQPlugin());

        context = mock(ApplicationContext.class);
        vertx = mock(Vertx.class);
        streamHandler = mock(IAntMediaStreamHandler.class);
        scope = mock(IScope.class);
        appSettings = mock(AppSettings.class);
        dataStore = mock(DataStore.class);

        when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(streamHandler);
        when(context.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(vertx);
        when(streamHandler.getScope()).thenReturn(scope);
        when(scope.getName()).thenReturn("live");
        when(streamHandler.getAppSettings()).thenReturn(appSettings);
        when(streamHandler.getDataStore()).thenReturn(dataStore);
        when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);

        MoQSettings safeSettings = new MoQSettings();
        safeSettings.setUseEmbeddedRelay(false);
        safeSettings.setIngestEnabled(false);
        doReturn(safeSettings).when(plugin).loadSettings();

        when(appSettings.getCustomSetting(any())).thenReturn(null);

        plugin.setApplicationContext(context);
    }

    @Test
    public void testSetApplicationContext_registersListener() {
        verify(streamHandler).addStreamListener(plugin);
    }

    @Test
    public void testSetApplicationContext_setupsPeriodic() {
        verify(vertx).setPeriodic(anyLong(), any());
    }

    @Test
    public void testLoadSettings_noCustomSetting_returnsDefaults() throws Exception {
        // Use a fresh non-spy plugin with field-injected appSettings
        MoQPlugin freshPlugin = new MoQPlugin();
        AppSettings freshAppSettings = mock(AppSettings.class);
        when(freshAppSettings.getCustomSetting("plugin.moq")).thenReturn(null);
        setField(freshPlugin, "appSettings", freshAppSettings);

        MoQSettings result = freshPlugin.loadSettings();
        assertNotNull(result);
        assertTrue(result.isUseEmbeddedRelay());
        assertTrue(result.isIngestEnabled());
        assertEquals(2000, result.getIngestPollIntervalMs());
    }

    @Test
    public void testLoadSettings_validJson_parsesSettings() throws Exception {
        MoQPlugin freshPlugin = new MoQPlugin();
        AppSettings freshAppSettings = mock(AppSettings.class);
        when(freshAppSettings.getCustomSetting("plugin.moq"))
                .thenReturn("{\"useEmbeddedRelay\":false,\"ingestEnabled\":false}");
        setField(freshPlugin, "appSettings", freshAppSettings);

        MoQSettings result = freshPlugin.loadSettings();
        assertNotNull(result);
        assertFalse(result.isUseEmbeddedRelay());
        assertFalse(result.isIngestEnabled());
    }

    @Test
    public void testLoadSettings_invalidJson_returnsDefaults() throws Exception {
        MoQPlugin freshPlugin = new MoQPlugin();
        AppSettings freshAppSettings = mock(AppSettings.class);
        when(freshAppSettings.getCustomSetting("plugin.moq")).thenReturn("not valid json {{{");
        setField(freshPlugin, "appSettings", freshAppSettings);

        MoQSettings result = freshPlugin.loadSettings();
        assertNotNull(result);
        assertTrue(result.isUseEmbeddedRelay());
        assertTrue(result.isIngestEnabled());
    }

    @Test
    public void testStreamStarted_nullMuxAdaptor_noMuxersAdded() throws Exception {
        when(streamHandler.getMuxAdaptor("testStream")).thenReturn(null);

        Broadcast broadcast = mock(Broadcast.class);
        when(broadcast.getStreamId()).thenReturn("testStream");

        plugin.streamStarted(broadcast);

        // muxersByStream should not have an entry for this stream
        ConcurrentMap<String, Set<MoQMuxer>> muxersByStream = getField(plugin, "muxersByStream");
        assertFalse(muxersByStream.containsKey("testStream"));
    }

    @Test
    public void testStreamStarted_withDirectMuxAdaptor_addsMuxers() throws Exception {
        String streamId = "testStream";
        MuxAdaptor mockAdaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor(streamId)).thenReturn(mockAdaptor);
        when(mockAdaptor.directMuxingSupported()).thenReturn(true);
        when(mockAdaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);
        when(mockAdaptor.getEncoderSettingsList()).thenReturn(null);

        Broadcast broadcast = mock(Broadcast.class);
        when(broadcast.getStreamId()).thenReturn(streamId);

        plugin.streamStarted(broadcast);

        verify(mockAdaptor, atLeastOnce()).addMuxer(any(MoQMuxer.class), anyInt());
    }

    @Test
    public void testStreamFinished_noTrackedStream_noError() {
        when(streamHandler.getMuxAdaptor("unknownStream")).thenReturn(null);

        Broadcast broadcast = mock(Broadcast.class);
        when(broadcast.getStreamId()).thenReturn("unknownStream");

        // Should not throw
        plugin.streamFinished(broadcast);
    }

    @Test
    public void testStreamFinished_trackedStream_removesMuxers() throws Exception {
        String streamId = "testStream";
        MuxAdaptor mockAdaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor(streamId)).thenReturn(mockAdaptor);
        when(mockAdaptor.directMuxingSupported()).thenReturn(true);
        when(mockAdaptor.addMuxer(any(MoQMuxer.class), anyInt())).thenReturn(true);
        when(mockAdaptor.getEncoderSettingsList()).thenReturn(null);

        Broadcast broadcast = mock(Broadcast.class);
        when(broadcast.getStreamId()).thenReturn(streamId);

        // First populate
        plugin.streamStarted(broadcast);

        // Then finish
        plugin.streamFinished(broadcast);

        verify(mockAdaptor, atLeastOnce()).removeMuxer(any(MoQMuxer.class));
    }

    @Test
    public void testDestroy_clearsIngests() throws Exception {
        MoQStreamFetcher mockFetcher = mock(MoQStreamFetcher.class);
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put("stream1", mockFetcher);

        plugin.destroy();

        verify(mockFetcher).stopStream();
        assertTrue(ingests.isEmpty());
    }

    @Test
    public void testStartIngest_streamInDataStore_createsFetcher() throws Exception {
        String streamId = "ingestStream";
        Broadcast broadcast = mock(Broadcast.class);
        when(dataStore.get(streamId)).thenReturn(broadcast);

        MoQStreamFetcher mockFetcher = mock(MoQStreamFetcher.class);
        doReturn(mockFetcher).when(plugin).createFetcher(eq(streamId), any(), any(), any());

        plugin.startIngest(streamId);

        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        assertTrue(ingests.containsKey(streamId));
    }

    @Test
    public void testStartIngest_notInDataStore_acceptOnlyDatastore_returnsEarly() throws Exception {
        String streamId = "unknownStream";
        when(dataStore.get(streamId)).thenReturn(null);
        when(appSettings.isAcceptOnlyStreamsInDataStore()).thenReturn(true);

        plugin.startIngest(streamId);

        verify(dataStore, never()).save(any(Broadcast.class));
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        assertFalse(ingests.containsKey(streamId));
    }

    @Test
    public void testStartIngest_notInDataStore_acceptAll_savesBroadcast() throws Exception {
        String streamId = "newStream";
        when(dataStore.get(streamId)).thenReturn(null);
        when(appSettings.isAcceptOnlyStreamsInDataStore()).thenReturn(false);

        plugin.startIngest(streamId);

        verify(dataStore).save(any(Broadcast.class));
    }

    @Test
    public void testStopIngest_existing_stopsAndRemoves() throws Exception {
        String streamId = "stream1";
        MoQStreamFetcher mockFetcher = mock(MoQStreamFetcher.class);
        ConcurrentMap<String, MoQStreamFetcher> ingests = getField(plugin, "activeIngests");
        ingests.put(streamId, mockFetcher);

        plugin.stopIngest(streamId);

        verify(mockFetcher).stopStream();
        assertFalse(ingests.containsKey(streamId));
    }

    @Test
    public void testStopIngest_nonExisting_noError() {
        // Should not throw
        plugin.stopIngest("nonExistentStream");
    }

    @Test
    public void testJoinedLeftRoom_noOp() {
        // Should not throw
        plugin.joinedTheRoom("room1", "stream1");
        plugin.leftTheRoom("room1", "stream1");
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
