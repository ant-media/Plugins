package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AV_CH_LAYOUT_STEREO;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpusDecoder {
	private Logger logger = LoggerFactory.getLogger(OpusDecoder.class);
	
	protected AVCodecContext audioContext;
	protected String streamId;
	protected AVFrame samplesFrame;
	protected int[] gotFrame;
	private boolean initialized = false;

	private AVChannelLayout channelLayout;


	public void prepare(String streamId) 
	{
		gotFrame = new int[1];
		this.streamId = streamId;
		AVCodecParameters audioCodecParameters = new AVCodecParameters();
		
		audioCodecParameters.sample_rate(48000);
		channelLayout = new AVChannelLayout();
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
	
		prepareAudioDecoder(audioCodecParameters);
	}
	
	public void prepareAudioDecoder(AVCodecParameters audioCodecParameters) 
	{
		AVCodec codec = avcodec_find_decoder(audioCodecParameters.codec_id());
		if (codec == null) {
			throw new IllegalArgumentException("avcodec_find_decoder() error: Unsupported video format or codec not found: "
					+ audioCodecParameters.codec_id() + ".");
		}
		
		logger.info("audio decoder name:  {} for {} sample rate:{}" , codec.name().getString(), streamId, audioCodecParameters.sample_rate());

		// Allocate a codec context for the decoder
		if ((audioContext = avcodec_alloc_context3(codec)) == null) {
			throw new IllegalArgumentException("avcodec_alloc_context3() error: Could not allocate video decoding context.");
		}

		if (avcodec_parameters_to_context(audioContext, audioCodecParameters) < 0) {
			throw new IllegalArgumentException(
					"avcodec_parameters_to_context() error: Could not copy the audio stream parameters.");
		}

		int ret;
		if ((ret = avcodec_open2(audioContext, codec, (AVDictionary) null)) < 0) {
			throw new IllegalArgumentException("avcodec_open2() error " + ret + ": Could not open audio codec.");
		}
		
		audioContext.time_base().den(1000);

		// Allocate audio samples frame
		if ((samplesFrame = av_frame_alloc()) == null) {
			throw new NullPointerException("av_frame_alloc() error: Could not allocate audio frame.");
		}
		codec.close();
		
		initialized = true;
	}
	
	public void stop() {
		if (samplesFrame != null) {
			av_frame_free(samplesFrame);
			samplesFrame.close();
			samplesFrame = null;
		}
		
		if (audioContext != null) {
			avcodec_free_context(audioContext);
			audioContext.close();
			audioContext = null;
		}
	}

	public boolean isInitialized() {
		return initialized;
	}


	public AVFrame decode(AVPacket packet) {
		
		int ret = avcodec_send_packet(audioContext, packet);
		av_packet_unref(packet);
		if (ret < 0) {
			logger.error("Cannot send video packet for decoding for stream: {}", Utils.getErrorDefinition(ret));
		}
		
		ret = avcodec_receive_frame(audioContext, samplesFrame);

		if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
			return null;
		}
		else if (ret < 0) {
			logger.error("Decode video frame error: {}" , Utils.getErrorDefinition(ret));
			return null;
		}
		
		samplesFrame.pts(samplesFrame.best_effort_timestamp());
		return samplesFrame;
	}
}
