package io.antmedia.test.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ClipCreatorConverter;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;
import io.antmedia.plugin.Mp4CreationResponse;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.vertx.core.Vertx;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.smallrye.common.constraint.Assert.assertNotNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class ClipCreatorPluginTest {

    @Test
    public void testCreateMp4Periodicly() throws Exception 
    {
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());

        ApplicationContext context = Mockito.mock(ApplicationContext.class);

        Vertx vertx = Mockito.mock(io.vertx.core.Vertx.class);
        when(context.getBean("vertxCore")).thenReturn(vertx);

        AntMediaApplicationAdapter applicationAdapter = Mockito.mock(AntMediaApplicationAdapter.class);
        when(applicationAdapter.getScope()).thenReturn(Mockito.mock(org.red5.server.api.scope.IScope.class));

        AppSettings appSettings = new AppSettings();
        when(applicationAdapter.getAppSettings()).thenReturn(appSettings);

        DataStore dataStore = Mockito.mock(io.antmedia.datastore.db.DataStore.class);
        when(applicationAdapter.getDataStore()).thenReturn(dataStore);

        when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(applicationAdapter);

        plugin.setApplicationContext(context);

        verify(plugin, times(1)).getClipCreatorSettings(appSettings);
        
        
        String streamId = "testStream";

        Broadcast broadcast = new Broadcast();
        broadcast.setStreamId(streamId);
        plugin.streamStarted(broadcast);
        verify(vertx, times(1)).setPeriodic(anyLong(), any());

        
        assertNotNull(plugin.getStreamRecoderTimer().get(streamId));

        
        plugin.streamFinished(broadcast);
        
        
        verify(vertx, times(1)).cancelTimer(anyLong());

        
        verify(vertx, times(1)).setPeriodic(anyLong(), any());
        assertNull(plugin.getStreamRecoderTimer().get(streamId));

    }

    @Test
    public void testConvertHlsToMp4() throws Exception {
        String streamId = "testStream";
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        DataStore dataStore = new InMemoryDataStore("db");
        plugin.setDataStore(dataStore);
     
        AntMediaApplicationAdapter mockApplication = mock(AntMediaApplicationAdapter.class);

        doReturn(mockApplication).when(plugin).getApplication();
        doNothing().when(mockApplication).muxingFinished(any(), any(), any(), anyLong(), anyLong(), anyInt(), anyString(), anyString());
        
        Broadcast broadcast = new Broadcast();
        broadcast.setStreamId("testStream");
        plugin.setStreamsFolder("src/test/resources");
        
        Mp4CreationResponse createMp4Response = plugin.convertHlsToMp4(broadcast, true);
        assertNotNull(createMp4Response.getFile());
        assertTrue(createMp4Response.getFile().getTotalSpace() > 0);
        assertTrue(createMp4Response.getFile().delete());

    }

    @Test
    public void testGetSegmentFilesWithinTimeRange() throws IOException {
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        MediaPlaylistParser parser = new MediaPlaylistParser();
        File m3u8File = new File("src/test/resources/testStream.m3u8");
        MediaPlaylist mediaPlaylist = parser.readPlaylist(m3u8File.toPath());
        long startTimeMilis = 1727644047000L;
        long endTimeMilis = 1727644051862L;
        ArrayList<File> fileArrayList = plugin.getSegmentFilesWithinTimeRange(mediaPlaylist, startTimeMilis, endTimeMilis, m3u8File);
        assertEquals(3, fileArrayList.size());
    }


    @Test
    public void testGetM3u8File_Success(){
        String streamId = "testStream";
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        plugin.setStreamsFolder("src/test/resources");
        File m3u8File = plugin.getM3u8File(streamId);
        assertNotNull(m3u8File);
        assertTrue(m3u8File.getTotalSpace() > 0);
    }

    @Test
    public void testGetM3u8File_Fail(){
        String streamId = "testStream2";
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        plugin.setStreamsFolder("src/test/resources");
        File m3u8File = plugin.getM3u8File(streamId);
        assertNull(m3u8File);
    }

}