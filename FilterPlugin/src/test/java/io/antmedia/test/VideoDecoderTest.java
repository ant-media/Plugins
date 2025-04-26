package io.antmedia.test;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.javacpp.BytePointer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.filter.AudioDecoder;
import io.antmedia.filter.Utils;
import io.antmedia.filter.VideoDecoder;
import io.antmedia.plugin.api.StreamParametersInfo;

public class VideoDecoderTest {
	
	private static Logger logger = LoggerFactory.getLogger(FilterManagerUnitTest.class);

	
	@Test
	public void testDecodeVideo() {
		
		AVCodecParameters videoCodecParameters = new AVCodecParameters();

		videoCodecParameters.width(640);
		videoCodecParameters.height(360);
		videoCodecParameters.codec_id(AV_CODEC_ID_H264);
		videoCodecParameters.codec_type(AVMEDIA_TYPE_VIDEO);		
		videoCodecParameters.format(AV_PIX_FMT_YUV420P);
		videoCodecParameters.codec_tag(0);

		StreamParametersInfo streamParams = new StreamParametersInfo();
		streamParams.setEnabled(true);
		streamParams.setTimeBase(Utils.TIME_BASE_FOR_MS);
		//set the codec parameters
		streamParams.setCodecParameters(videoCodecParameters);
		
		VideoDecoder videoDecoder = new VideoDecoder("streamId", streamParams);
		
		assertTrue(videoDecoder.isRunning());
		
		AVFormatContext inputFormatContext = new AVFormatContext(null); 
		String filepath = "src/test/resources/test_video_360p.ts";

		int ret = avformat_open_input(inputFormatContext, filepath, null, null);
		assertTrue(Utils.getErrorDefinition(ret), ret >= 0);

		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
		assertTrue(Utils.getErrorDefinition(ret), ret >= 0);

		avformat.av_dump_format(inputFormatContext,0, (String)null, 0);

		AVPacket pkt = avcodec.av_packet_alloc();

		int decodedFrameCount = 0;
		while ((ret = av_read_frame(inputFormatContext, pkt)) == 0) 
		{
			if (inputFormatContext.streams(pkt.stream_index()).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO)
			{
				assertTrue(Utils.getErrorDefinition(ret), ret >= 0);
				if (videoDecoder.decodeVideoPacket(pkt) != null) {
					decodedFrameCount++;
				}
				//int streamIndex = pkt.stream_index();
				//byte[] data = new byte[pkt.size()];
				//pkt.data().position(0).get(data);
			}
			
			av_packet_unref(pkt);
		}
		
		logger.info("Decoded frame count: {}", decodedFrameCount);
		assertTrue(decodedFrameCount > 1000);
		
		videoDecoder.stop();
		
		assertFalse(videoDecoder.isRunning());
		
		//no crash
		assertNull(videoDecoder.decodeVideoPacket(pkt));
		/*
		AVPacket packet = new AVPacket();
				
		packet.stream_index(0);
		packet.pts(1);
		packet.dts(1);
		packet.flags(AV_PKT_FLAG_KEY);
		
		byte[] data = new byte[]{10, 20, 30};
		
		packet.data(new BytePointer(ByteBuffer.allocate(100)));
		packet.size(100);
		packet.position(0);
		
		assertNotNull(opusDecoder.decodeAudioFrame(new AVRational().num(1).den(1000), packet));
		
		data = new byte[]{10, 20, 30};
		packet.data(new BytePointer(data));
		packet.size(data.length);
		assertNull(opusDecoder.decodeAudioFrame(new AVRational().num(1).den(1000), packet));
		
		
		assertNotNull(opusDecoder.getSamplesFrame());
		assertNotNull(opusDecoder.getAudioContext());

		opusDecoder.stop();
		
		
		assertNull(opusDecoder.getSamplesFrame());
		assertNull(opusDecoder.getAudioContext());
		
		assertFalse(opusDecoder.isRunning());
		
		assertNull(opusDecoder.decodeAudioFrame(new AVRational().num(1).den(1000), packet));
		
		
		//no crash
		opusDecoder.stop();
		
		*/

		
	}

}
