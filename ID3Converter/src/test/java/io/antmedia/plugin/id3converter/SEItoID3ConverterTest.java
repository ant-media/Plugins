package io.antmedia.plugin.id3converter;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.app.SEItoID3Converter;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;

public class SEItoID3ConverterTest {
	
	protected static Logger logger = LoggerFactory.getLogger(SEItoID3ConverterTest.class);

	
	
	/**
	 * This test is like an integration test
	 * 
	 */
	@Test
	public void testHEVCSEItoID3() {
		
		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();

		int ret = 0;
		
		String absolutePath = "src/test/resources/stream_hevc_injected_sei_messages.ts";
		MuxAdaptor muxAdaptor = feedPlugin(inputFormatContext, absolutePath);
		
		ArgumentCaptor<String> seiMessages = ArgumentCaptor.forClass(String.class);
		Mockito.verify(muxAdaptor, Mockito.times(13)).addID3Data(seiMessages.capture());
				
		avformat.avformat_close_input(inputFormatContext);
		
		for (String value : seiMessages.getAllValues()) {
			assertTrue(value.startsWith("hello") || value.startsWith("x265"));
		}
		

	}
	
	@Test
	public void testAVCSEItoID3() {
		
		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();

		int ret = 0;
		
		String absolutePath = "src/test/resources/stream_avc_injected_sei_messages.ts";
		MuxAdaptor muxAdaptor = feedPlugin(inputFormatContext, absolutePath);
		
		ArgumentCaptor<String> seiMessages = ArgumentCaptor.forClass(String.class);
		Mockito.verify(muxAdaptor, Mockito.times(10)).addID3Data(seiMessages.capture());
				
		avformat.avformat_close_input(inputFormatContext);
		
		for (String value : seiMessages.getAllValues()) {
			assertTrue(value.startsWith("hello"));
		}
		

	}

	private MuxAdaptor feedPlugin(AVFormatContext inputFormatContext, String absolutePath) {
		int ret;
		if ((ret = avformat_open_input(inputFormatContext, absolutePath, null, (AVDictionary) null)) < 0) {
			byte[] data = new byte[2048];
			av_strerror(ret, data, data.length);
			fail("cannot open input context. Error is " +  new String(data, 0, data.length));
		}

		System.out.println("testFile: finding stream info: " + absolutePath);
		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
		if (ret < 0) {
			fail("Could not find stream information\n");
		}

		int streamCount = inputFormatContext.nb_streams();

		if (streamCount == 0) {
			fail("Could not find stream information\n");
		}
		
		IAntMediaStreamHandler appAdaptor = Mockito.mock(IAntMediaStreamHandler.class);
		MuxAdaptor muxAdaptor = Mockito.mock(MuxAdaptor.class);
		Mockito.when(appAdaptor.getMuxAdaptor(Mockito.anyString())).thenReturn(muxAdaptor);
		
		SEItoID3Converter seiToID3Converter = new SEItoID3Converter("stream1", appAdaptor);
		AVPacket pkt = new AVPacket();
		while ((ret = av_read_frame(inputFormatContext, pkt)) >= 0) 
		{	
			if (inputFormatContext.streams(pkt.stream_index()).codecpar().codec_type() != avutil.AVMEDIA_TYPE_VIDEO) {
				av_packet_unref(pkt);
				continue;
			}
			
			seiToID3Converter.onVideoPacket(absolutePath, pkt);
			
			av_packet_unref(pkt);
		}
		return muxAdaptor;
	}
	

}
