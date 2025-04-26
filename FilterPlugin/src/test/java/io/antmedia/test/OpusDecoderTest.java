package io.antmedia.test;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.junit.Test;

import io.antmedia.filter.AudioDecoder;

public class OpusDecoderTest {
	
	
	@Test
	public void testPrepare() {
		
		AudioDecoder opusDecoder = new AudioDecoder("streamId");
		
		AVCodecParameters audioCodecParameters = new AVCodecParameters();
		
		audioCodecParameters.sample_rate(48000);
		AVChannelLayout channelLayout = new AVChannelLayout();
		av_channel_layout_default(channelLayout, 2);
		
		audioCodecParameters.ch_layout(channelLayout);
		
		audioCodecParameters.codec_id(AV_CODEC_ID_OPUS);
		audioCodecParameters.codec_type(AVMEDIA_TYPE_AUDIO);
		audioCodecParameters.format(AV_SAMPLE_FMT_FLTP);
		
		//reference: https://ffmpeg.org/doxygen/2.8/libopusenc_8c_source.html#l00080
		byte extraData[] = new byte[19];
		
		extraData[0] = 'O';
		extraData[1] = 'p';
		extraData[2] = 'u';
		extraData[3] = 's';
		extraData[4] = 'H';
		extraData[5] = 'e';
		extraData[6] = 'a';
		extraData[7] = 'd';
		extraData[8] = 1; //version
		extraData[9] = 2; //channels 
		extraData[10] = 0x38; //
		extraData[11] = 0x1;
		extraData[12] = (byte) 0x80;
		extraData[13] = (byte) 0xBB;
		extraData[14] = 0;
		extraData[15] = 0;
		extraData[16] = 0;
		extraData[17] = 0;
		extraData[18] = 0;
		
		BytePointer audioExtraDataPointer = new BytePointer(av_malloc(extraData.length)).capacity(extraData.length);
		audioExtraDataPointer.position(0).put(extraData);
		audioCodecParameters.extradata(audioExtraDataPointer);
		audioCodecParameters.extradata_size(extraData.length);	
	
		
		opusDecoder.prepareAudioDecoder(audioCodecParameters);
		
		
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
		
		
		
	}

}
