package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AudioDecoder
{
	protected AVCodecContext audioContext;
	protected String streamId;
	protected AVFrame samplesFrame;
	protected Logger logger;
	
	public AudioDecoder(String streamId) {
		this.streamId = streamId;
		logger =  LoggerFactory.getLogger(AudioDecoder.class);
	}
	
	public void prepareAudioDecoder(AVCodecParameters audioCodecParameters) 
	{
		AVCodec codec = avcodec_find_decoder(audioCodecParameters.codec_id());
		if (codec == null) {
			throw new IllegalArgumentException("avcodec_find_decoder() error: Unsupported video format or codec not found: "
					+ audioCodecParameters.codec_id() + ".");
		}
		
		logger.info("audio decoder name:{} for {} sample rate:{}" , codec.name().getString(), streamId, audioCodecParameters.sample_rate());

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

		// Allocate audio samples frame
		if ((samplesFrame = av_frame_alloc()) == null) {
			throw new NullPointerException("av_frame_alloc() error: Could not allocate audio frame.");
		}
		codec.close();
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
	
	public AVFrame decodeAudioFrame(AVRational timebase, AVPacket pkt) {
		
		sendAudioPacket(timebase, pkt);
		
		return receiveAudioPacket();
	}
	
	protected void sendAudioPacket(AVRational timebase, AVPacket pkt)
	{
		if (timebase.num() != audioContext.time_base().num() || 
				timebase.den() != audioContext.time_base().den()) 
		{
			//rescale the timebase if it's different
			av_packet_rescale_ts(pkt,
					timebase,
					audioContext.time_base()
				);
			logger.trace("sendAudioPacket incoming timebase:{}/{} audioContext timebase:{}/{} for stream:{}", timebase.num(), timebase.den(), 
					audioContext.time_base().num(), audioContext.time_base().den(), streamId);
			
		}

		int ret = avcodec_send_packet(audioContext, pkt);
		if (ret < 0) {
			logger.error("Cannot send audio packet for decoding for stream: {}", streamId);
		}
	}
	
	protected AVFrame receiveAudioPacket()
	{
		int ret = avcodec_receive_frame(audioContext, samplesFrame);

		if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
			logger.trace("No audio packet from decoder for stream: {} it is AGAIN:{} or EOF:{} ", streamId, ret == AVERROR_EAGAIN(), ret == AVERROR_EOF());

			return null;
		}
		else if (ret < 0 && logger.isErrorEnabled()) {
			logger.error("Decode video frame error: {} streamId: {}" , Utils.getErrorDefinition(ret), streamId);
			return null;
		}

		samplesFrame.pts(samplesFrame.best_effort_timestamp());
		return samplesFrame;
	}
}
