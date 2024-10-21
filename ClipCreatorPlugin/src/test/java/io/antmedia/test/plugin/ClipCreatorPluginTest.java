package io.antmedia.test.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ClipCreatorConverter;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;
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
    public void testCreateMp4Periodicly() throws Exception {
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

        verify(plugin, times(1)).loadSettings();
        verify(plugin, times(1)).startPeriodicCreationTimer(new ClipCreatorSettings().getMp4CreationIntervalSeconds());
        verify(vertx, times(1)).setPeriodic(anyLong(), any());

        String streamId = "testStream";

        ArrayList<File> m3u8FileList = new ArrayList<>();

        File m3u8File = mock(File.class);

        when(m3u8File.getPath()).thenReturn("path");

        when(m3u8File.getName()).thenReturn("testStream.m3u8");

        m3u8FileList.add(m3u8File);

        when(plugin.getM3u8FileList()).thenReturn(m3u8FileList);

        Broadcast broadcast = mock(Broadcast.class);

        when(dataStore.get(streamId)).thenReturn(broadcast);
        when(broadcast.getStatus()).thenReturn(AntMediaApplicationAdapter.BROADCAST_STATUS_BROADCASTING);

        File tsFile = mock(File.class);
        Path segmentPath = mock(Path.class);
        when(segmentPath.toFile()).thenReturn(tsFile);

        Path mockPath = mock(Path.class);
        File parentFile = mock(File.class);
        MediaPlaylist mockPlaylist = mock(MediaPlaylist.class);

        when(mockPath.resolve(anyString())).thenReturn(segmentPath);

        MediaSegment mockSegment1 = mock(MediaSegment.class);
        MediaSegment mockSegment2 = mock(MediaSegment.class);

        when(m3u8File.getParentFile()).thenReturn(parentFile);
        when(parentFile.toPath()).thenReturn(mockPath);

        List<MediaSegment> mediaSegments = new ArrayList<>();
        mediaSegments.add(mockSegment1);
        mediaSegments.add(mockSegment2);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime pastTime = now.minusSeconds(10);

        when(mockSegment1.programDateTime()).thenReturn(Optional.of(now));
        when(mockSegment2.programDateTime()).thenReturn(Optional.of(pastTime));
        when(mockSegment1.uri()).thenReturn("segment1.ts");
        when(mockSegment2.uri()).thenReturn("segment2.ts");

        when(mockPlaylist.mediaSegments()).thenReturn(mediaSegments);

        when(mockPlaylist.mediaSegments().get(1).programDateTime()).thenReturn(Optional.of(now));

        MediaPlaylistParser mockParser = mock(MediaPlaylistParser.class);
        plugin.setM3u8Parser(mockParser);  // Set the mock parser directly

        when(plugin.readPlaylist(m3u8File)).thenReturn(mockPlaylist);
        when(mockParser.readPlaylist(any(Path.class))).thenReturn(mockPlaylist);


        when(m3u8File.getAbsolutePath()).thenReturn("testStream.m3u8");

        try (MockedStatic<ClipCreatorConverter> mockedStatic = mockStatic(ClipCreatorConverter.class)) {
            mockedStatic.when(() -> ClipCreatorConverter.createMp4(any(), anyString())).thenReturn(true);
            plugin.convertHlsStreamsToMp4(20);
            assertEquals(1, plugin.getCreatedMp4Count());
        }

    }

    @Test
    public void testConvertHlsToMp4() {
        String streamId = "testStream";
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        File m3u8File = new File("src/test/resources/testStream.m3u8");
        ArrayList<File> tsFilesToMerge = new ArrayList<>();
        tsFilesToMerge.add(new File("src/test/resources/testStream000000000.ts"));
        tsFilesToMerge.add(new File("src/test/resources/testStream000000001.ts"));
        tsFilesToMerge.add(new File("src/test/resources/testStream000000002.ts"));
        tsFilesToMerge.add(new File("src/test/resources/testStream000000003.ts"));
        AntMediaApplicationAdapter mockApplication = mock(AntMediaApplicationAdapter.class);

        doReturn(mockApplication).when(plugin).getApplication();
        doNothing().when(mockApplication).muxingFinished(anyString(), any(), anyLong(), anyLong(), anyInt(), anyString(), anyString());
        File mp4File = plugin.convertHlsToMp4(m3u8File, tsFilesToMerge, streamId);
        assertNotNull(mp4File);
        assertTrue(mp4File.getTotalSpace() > 0);
        assertTrue(mp4File.delete());

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
    public void testGetSegmentFilesWithinInterval() throws IOException {
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        MediaPlaylistParser parser = new MediaPlaylistParser();
        File m3u8File = new File("src/test/resources/testStream.m3u8");
        MediaPlaylist mediaPlaylist = parser.readPlaylist(m3u8File.toPath());
        int intervalSeconds = 3;
        ArrayList<File> fileArrayList = plugin.getSegmentFilesWithinTimeRange(mediaPlaylist, intervalSeconds, m3u8File);
        assertEquals(1, fileArrayList.size());
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

    @Test
    public void testGetM3u8FileList(){
        ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
        plugin.setStreamsFolder("src/test/resources");
        ArrayList<File> m3u8FileList = plugin.getM3u8FileList();
        assertEquals(1, m3u8FileList.size());
    }
}