package io.antmedia.test.rest;

import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;
import io.antmedia.rest.RestService;
import io.antmedia.rest.model.Result;
import io.lindstrom.m3u8.model.MediaPlaylist;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RestServiceTest {

    private RestService restService;
    private ClipCreatorPlugin clipCreatorPlugin;
    private ServletContext servletContext;
    private ApplicationContext applicationContext;
    private ClipCreatorSettings clipCreatorSettings;

    @Before
    public void setup() {
        servletContext = mock(ServletContext.class);
        applicationContext = mock(ApplicationContext.class);
        clipCreatorPlugin = mock(ClipCreatorPlugin.class);
        clipCreatorSettings = mock(ClipCreatorSettings.class);

        when(servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE))
                .thenReturn(applicationContext);
        when(clipCreatorPlugin.getClipCreatorSettings()).thenReturn(clipCreatorSettings);
        when(clipCreatorSettings.getMp4CreationIntervalSeconds()).thenReturn(600);

        when(applicationContext.getBean("plugin."+ClipCreatorPlugin.PLUGIN_KEY)).thenReturn(clipCreatorPlugin);

        restService = new RestService();
        restService.setServletContext(servletContext);
    }

    @Test
    public void testCreateMp4_FileNotFound() {
        String streamId = "testStream";

        when(clipCreatorPlugin.getM3u8File(streamId)).thenReturn(null);

        Response response = restService.createMp4(streamId);

        assertEquals(Response.Status.EXPECTATION_FAILED.getStatusCode(), response.getStatus());
        assertEquals("No m3u8 HLS playlist exists for stream " + streamId, response.getEntity());
    }

    @Test
    public void testCreateMp4_NoSegments() {
        String streamId = "testStream";
        File m3u8File = new File("test.m3u8");

        when(clipCreatorPlugin.getM3u8File(streamId)).thenReturn(m3u8File);
        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(new HashMap<>());
        when(clipCreatorPlugin.readPlaylist(m3u8File)).thenReturn(mock(MediaPlaylist.class));
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), anyLong(), eq(m3u8File)))
                .thenReturn(new ArrayList<>());

        Response response = restService.createMp4(streamId);

        assertEquals(Response.Status.EXPECTATION_FAILED.getStatusCode(), response.getStatus());
        assertEquals("No HLS playlist segment exists for stream " + streamId + " in this interval.", response.getEntity());
    }

    @Test
    public void testCreateMp4_Mp4CreationFails() {
        String streamId = "testStream";
        File m3u8File = new File("test.m3u8");
        ArrayList<File> tsFilesToMerge = new ArrayList<>();
        tsFilesToMerge.add(new File("segment1.ts"));

        when(clipCreatorPlugin.getM3u8File(streamId)).thenReturn(m3u8File);
        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(new HashMap<>());
        when(clipCreatorPlugin.readPlaylist(m3u8File)).thenReturn(mock(MediaPlaylist.class));
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), eq(m3u8File)))
                .thenReturn(tsFilesToMerge);
        when(clipCreatorPlugin.convertHlsToMp4(m3u8File, tsFilesToMerge, streamId)).thenReturn(null);

        Response response = restService.createMp4(streamId);

        assertEquals(Response.Status.EXPECTATION_FAILED.getStatusCode(), response.getStatus());
        assertEquals("Could not create MP4 for " + streamId, response.getEntity());
    }

    @Test
    public void testCreateMp4_Success() {
        String streamId = "testStream";
        File m3u8File = new File("test.m3u8");
        File mp4File = new File("output.mp4");
        ArrayList<File> tsFilesToMerge = new ArrayList<>();
        tsFilesToMerge.add(new File("segment1.ts"));

        when(clipCreatorPlugin.getM3u8File(streamId)).thenReturn(m3u8File);
        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(new HashMap<>());
        when(clipCreatorPlugin.readPlaylist(m3u8File)).thenReturn(mock(MediaPlaylist.class));
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), eq(m3u8File)))
                .thenReturn(tsFilesToMerge);
        when(clipCreatorPlugin.convertHlsToMp4(m3u8File, tsFilesToMerge, streamId)).thenReturn(mp4File);

        Response response = restService.createMp4(streamId);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("attachment; filename=\"" + mp4File.getName() + "\"", response.getHeaderString("Content-Disposition"));

        Map<String,Long> mp4CreateTimeForStreamMap = new HashMap<>();
        mp4CreateTimeForStreamMap.put(streamId, System.currentTimeMillis());

        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(mp4CreateTimeForStreamMap);
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), anyLong(), eq(m3u8File)))
                .thenReturn(tsFilesToMerge);

        response = restService.createMp4(streamId);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("attachment; filename=\"" + mp4File.getName() + "\"", response.getHeaderString("Content-Disposition"));

    }

    @Test
    public void testStartPeriodicCreation() {
        int mp4CreationIntervalSeconds = 300;
        Response response = restService.startPeriodicCreation(mp4CreationIntervalSeconds);
        verify(clipCreatorPlugin, times(1)).startPeriodicCreationTimer(mp4CreationIntervalSeconds);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testStopPeriodicCreation() {
        Response response = restService.stopPeriodicCreation();
        verify(clipCreatorPlugin, times(1)).stopPeriodicCreationTimer();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    public void testReloadSettings() {
        Response response = restService.reloadSettings();
        verify(clipCreatorPlugin, times(1)).reloadSettings();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

}
