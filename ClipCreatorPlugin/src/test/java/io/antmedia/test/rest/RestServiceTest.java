package io.antmedia.test.rest;

import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;
import io.antmedia.plugin.Mp4CreationResponse;
import io.antmedia.rest.ClipCreatorRestService;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RestServiceTest {

    private ClipCreatorRestService restService;
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

        when(clipCreatorPlugin.getDataStore()).thenReturn(new InMemoryDataStore("testdb"));
        restService = new ClipCreatorRestService();
        restService.setServletContext(servletContext);
        
    }


    @Test
    public void testCreateMp4_Mp4CreationFails() throws Exception {
        String streamId = "testStream";
        File m3u8File = new File("test.m3u8");
        ArrayList<File> tsFilesToMerge = new ArrayList<>();
        tsFilesToMerge.add(new File("segment1.ts"));

        when(clipCreatorPlugin.getM3u8File(streamId)).thenReturn(m3u8File);
        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(new HashMap<>());
        when(clipCreatorPlugin.readPlaylist(m3u8File)).thenReturn(mock(MediaPlaylist.class));
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), anyLong(), eq(m3u8File)))
                .thenReturn(tsFilesToMerge);
        
        Mp4CreationResponse createMp4Response = mock(Mp4CreationResponse.class);
        when(createMp4Response.isSuccess()).thenReturn(false);
        when(createMp4Response.getMessage()).thenReturn("Could not create MP4 for " + streamId);
        when(clipCreatorPlugin.convertHlsToMp4(any(), anyBoolean())).thenReturn(createMp4Response);
        

        Response response = restService.createMp4(streamId, true);

        assertEquals(Response.Status.EXPECTATION_FAILED.getStatusCode(), response.getStatus());
        assertEquals("No broadcast exists for stream " + streamId, ((Result)response.getEntity()).getMessage());
        
        
        Broadcast broadcast = new Broadcast();
        broadcast.setStreamId(streamId);
        clipCreatorPlugin.getDataStore().save(broadcast);
        
        
        response = restService.createMp4(streamId, false);
        assertEquals(Response.Status.EXPECTATION_FAILED.getStatusCode(), response.getStatus());
        assertEquals("Could not create MP4 for " + streamId, ((Result)response.getEntity()).getMessage());


        Mp4CreationResponse mp4CreationResponse = new Mp4CreationResponse(new File("test.mp4"), "vodId");
        mp4CreationResponse.setSuccess(true);
        when(clipCreatorPlugin.convertHlsToMp4(any(), anyBoolean())).thenReturn(mp4CreationResponse);
        
        response = restService.createMp4(streamId, false);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("MP4 created successfully for stream " + streamId, ((Result)response.getEntity()).getMessage());

        
        
    }

    @Test
    public void testCreateMp4_Success() throws Exception {
        String streamId = "testStream";
        File m3u8File = new File("test.m3u8");
        File mp4File = new File("output.mp4");
        Mp4CreationResponse createMp4Response = mock(Mp4CreationResponse.class);
        when(createMp4Response.isSuccess()).thenReturn(true);
        when(createMp4Response.getFile()).thenReturn(mp4File);

        ArrayList<File> tsFilesToMerge = new ArrayList<>();
        tsFilesToMerge.add(new File("segment1.ts"));

        when(clipCreatorPlugin.getM3u8File(streamId)).thenReturn(m3u8File);
        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(new HashMap<>());
        when(clipCreatorPlugin.readPlaylist(m3u8File)).thenReturn(mock(MediaPlaylist.class));
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), anyLong(), eq(m3u8File)))
                .thenReturn(tsFilesToMerge);
        when(clipCreatorPlugin.convertHlsToMp4(any(), anyBoolean())).thenReturn(createMp4Response);
        
        Broadcast broadcast = new Broadcast();
        broadcast.setStreamId(streamId);
        clipCreatorPlugin.getDataStore().save(broadcast);

        Response response = restService.createMp4(streamId, true);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("attachment; filename=\"" + mp4File.getName() + "\"", response.getHeaderString("Content-Disposition"));


        Map<String,Long> mp4CreateTimeForStreamMap = new HashMap<>();
        mp4CreateTimeForStreamMap.put(streamId, System.currentTimeMillis());

        when(clipCreatorPlugin.getLastMp4CreateTimeForStream()).thenReturn(mp4CreateTimeForStreamMap);
        when(clipCreatorPlugin.getSegmentFilesWithinTimeRange(any(MediaPlaylist.class), anyLong(), anyLong(), eq(m3u8File)))
                .thenReturn(tsFilesToMerge);

        response = restService.createMp4(streamId, true);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("attachment; filename=\"" + mp4File.getName() + "\"", response.getHeaderString("Content-Disposition"));

        response = restService.createMp4(streamId, false);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertTrue(((Result) response.getEntity()).isSuccess());
    }

}
