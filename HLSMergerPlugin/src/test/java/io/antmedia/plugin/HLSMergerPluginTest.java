package io.antmedia.plugin;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.HLSMuxer;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.Muxer;
import io.vertx.core.Vertx;


public class HLSMergerPluginTest {

    private HLSMergerPlugin plugin;
    private ApplicationContext context;
    private Vertx vertx;
    private IAntMediaStreamHandler streamHandler;
    private IScope scope;

    @Before
    public void setup() {
        plugin = new HLSMergerPlugin();

        context = mock(ApplicationContext.class);
        vertx = mock(Vertx.class);
        streamHandler = mock(IAntMediaStreamHandler.class);
        scope = mock(IScope.class);

        when(context.getBean(eq(AntMediaApplicationAdapter.BEAN_NAME))).thenReturn(streamHandler);
        when(context.getBean(eq("vertxCore"))).thenReturn(vertx);
        when(streamHandler.getScope()).thenReturn(scope);

        plugin.setApplicationContext(context);
    }


    @Test
    public void testGetMuxAdaptor() {
        MuxAdaptor mockAdaptor = mock(MuxAdaptor.class);
        when(streamHandler.getMuxAdaptor(eq("stream1"))).thenReturn(mockAdaptor);

        MuxAdaptor adaptor = plugin.getMuxAdaptor("stream1");
        assertNotNull(adaptor);
        assertEquals(mockAdaptor, adaptor);
    }

    @Test
    public void testMergeStreams() {
        when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);

        boolean result = plugin.mergeStreams("test", new String[]{"stream1", "stream2"});
        assertTrue(result);
        assertTrue(plugin.getMergeTimers().containsKey("test"));
    }

    @Test
    public void testMergeStreamsInternal() throws Exception {
        HLSMergerPlugin spyPlugin = spy(plugin);

        // Mock dependencies for stream1
        MuxAdaptor mockAdaptor1 = mock(MuxAdaptor.class);
        HLSMuxer mockHlsMuxer1 = mock(HLSMuxer.class);
        Broadcast mockBroadcast1 = mock(Broadcast.class);

        // Mock dependencies for stream2
        MuxAdaptor mockAdaptor2 = mock(MuxAdaptor.class);
        HLSMuxer mockHlsMuxer2 = mock(HLSMuxer.class);
        Broadcast mockBroadcast2 = mock(Broadcast.class);

        // Mock `getMuxAdaptor` to return adaptors for stream1 and stream2
        when(streamHandler.getMuxAdaptor(eq("stream1"))).thenReturn(mockAdaptor1);
        when(streamHandler.getMuxAdaptor(eq("stream2"))).thenReturn(mockAdaptor2);

        // Mock muxer list and properties for stream1
        when(mockAdaptor1.getMuxerList()).thenReturn(java.util.Arrays.asList(mockHlsMuxer1));
        when(mockHlsMuxer1.getAverageBitrate()).thenReturn(500_000L);
        when(mockAdaptor1.getWidth()).thenReturn(1920);
        when(mockAdaptor1.getHeight()).thenReturn(1080);
        when(mockAdaptor1.getVideoCodecId()).thenReturn(AV_CODEC_ID_H264);
        when(mockAdaptor1.getBroadcast()).thenReturn(mockBroadcast1);
        when(mockBroadcast1.getSubFolder()).thenReturn("subfolder");

        // Mock muxer list and properties for stream2
        when(mockAdaptor2.getMuxerList()).thenReturn(java.util.Arrays.asList(mockHlsMuxer2));
        when(mockHlsMuxer2.getAverageBitrate()).thenReturn(800_000L);
        when(mockAdaptor2.getWidth()).thenReturn(1280);
        when(mockAdaptor2.getHeight()).thenReturn(720);
        when(mockAdaptor2.getVideoCodecId()).thenReturn(AV_CODEC_ID_H264);
        when(mockAdaptor2.getBroadcast()).thenReturn(mockBroadcast2);
        when(mockBroadcast2.getSubFolder()).thenReturn("subfolder");

        // Stub `writeHLSFile` to avoid file interactions
        doNothing().when(spyPlugin).writeHLSFile(nullable(String.class), nullable(String.class), nullable(String.class));

        // Call the method under test
        spyPlugin.mergeStreamsInternal("test", new String[]{"stream1", "stream2"});

        // Capture and verify the written content
        ArgumentCaptor<String> m3u8Content = ArgumentCaptor.forClass(String.class);
        verify(spyPlugin).writeHLSFile(eq("test"), m3u8Content.capture(), eq("subfolder"));

        // Assert the HLS content is correctly generated
        String capturedContent = m3u8Content.getValue();
        assertNotNull(capturedContent);
        assertTrue(capturedContent.contains("#EXTM3U")); // Verify basic structure
        assertTrue(capturedContent.contains("stream1.m3u8")); // Verify stream1 reference
        assertTrue(capturedContent.contains("stream2.m3u8")); // Verify stream2 reference

        // Additional checks for properties
        assertTrue(capturedContent.contains("#EXT-X-STREAM-INF")); // Verify stream info section exists
        assertTrue(capturedContent.contains("BANDWIDTH=500000")); // Verify bitrate for stream1
        assertTrue(capturedContent.contains("BANDWIDTH=800000")); // Verify bitrate for stream2
    }


    @Test
    public void testStopMerge() {
        plugin.getMergeTimers().put("test", 1L);
        when(vertx.cancelTimer(anyLong())).thenReturn(true);

        boolean result = plugin.stopMerge("test");
        assertTrue(result);
        assertFalse(plugin.getMergeTimers().containsKey("test"));
    }

    @Test
    public void testAddMultipleAudioStreams() {
        when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);

        boolean result = plugin.addMultipleAudioStreams("test", "videoStream", new String[]{"audio1", "audio2"});
        assertTrue(result);
        assertTrue(plugin.getMergeTimers().containsKey("test"));
    }

    @Test
    public void testAddMultipleAudioInternal() {
        HLSMergerPlugin spyPlugin = spy(plugin);

        // Mock dependencies for video and audio streams
        MuxAdaptor mockAudioAdaptor1 = mock(MuxAdaptor.class);
        MuxAdaptor mockAudioAdaptor2 = mock(MuxAdaptor.class);
        MuxAdaptor mockVideoAdaptor = mock(MuxAdaptor.class);
        Broadcast audioBroadcast1 = mock(Broadcast.class);
        Broadcast audioBroadcast2 = mock(Broadcast.class);
        Broadcast videoBroadcast = mock(Broadcast.class);

        // Mock `getMuxAdaptor` to return audio and video adaptors
        when(streamHandler.getMuxAdaptor(eq("audio1"))).thenReturn(mockAudioAdaptor1);
        when(streamHandler.getMuxAdaptor(eq("audio2"))).thenReturn(mockAudioAdaptor2);
        when(streamHandler.getMuxAdaptor(eq("videoStream"))).thenReturn(mockVideoAdaptor);

        // Mock broadcast objects and subfolder
        when(mockAudioAdaptor1.getBroadcast()).thenReturn(audioBroadcast1);
        when(mockAudioAdaptor2.getBroadcast()).thenReturn(audioBroadcast2);
        when(mockVideoAdaptor.getBroadcast()).thenReturn(videoBroadcast);
        when(videoBroadcast.getSubFolder()).thenReturn("subfolder");
        when(audioBroadcast1.getSubFolder()).thenReturn("subfolder");
        when(audioBroadcast2.getSubFolder()).thenReturn("subfolder");

        // Mock video properties
        when(mockVideoAdaptor.getWidth()).thenReturn(1920);
        when(mockVideoAdaptor.getHeight()).thenReturn(1080);

        // Stub `writeHLSFile` to avoid actual file interactions
        doNothing().when(spyPlugin).writeHLSFile(nullable(String.class), nullable(String.class), nullable(String.class));

        // Call the method under test
        String[] audioStreams = new String[]{"audio1", "audio2"};
        spyPlugin.addMultipleAudioInternal("test", "videoStream", audioStreams);

        // Capture and verify the written content
        ArgumentCaptor<String> m3u8Content = ArgumentCaptor.forClass(String.class);
        verify(spyPlugin).writeHLSFile(eq("test"), m3u8Content.capture(), eq("subfolder"));

        // Assert the HLS content is correctly generated
        String capturedContent = m3u8Content.getValue();
        assertNotNull(capturedContent);
        assertTrue(capturedContent.contains("#EXTM3U")); // Verify basic structure
        assertTrue(capturedContent.contains("audio1.m3u8")); // Verify audio1 stream reference
        assertTrue(capturedContent.contains("audio2.m3u8")); // Verify audio2 stream reference
        assertTrue(capturedContent.contains("videoStream.m3u8")); // Verify video stream reference

        // Additional checks for the structure
        assertTrue(capturedContent.contains("#EXT-X-MEDIA")); // Verify audio streams inclusion
        assertTrue(capturedContent.contains("TYPE=AUDIO")); // Verify audio stream type is specified
    }


}
