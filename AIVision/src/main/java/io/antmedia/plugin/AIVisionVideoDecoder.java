package io.antmedia.plugin;

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
import static org.bytedeco.ffmpeg.global.avutil.av_strerror;
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

import io.antmedia.plugin.api.StreamParametersInfo;

public class AIVisionVideoDecoder {

	private static final Logger logger = LoggerFactory.getLogger(AIVisionVideoDecoder.class);

	private final String streamId;
	private final StreamParametersInfo streamParameters;
	private AVCodecContext videoContext;
	private AVFrame decodedFrame;
	private AVRational decoderTimeBase;
	private boolean running;

	public AIVisionVideoDecoder(String streamId, StreamParametersInfo streamParameters) {
		this.streamId = streamId;
		this.streamParameters = streamParameters;
		this.decoderTimeBase = streamParameters.getTimeBase();
		decodedFrame = av_frame_alloc();
		if (decodedFrame == null) {
			throw new IllegalArgumentException("Could not allocate decoded video frame");
		}

		AVCodecParameters codecParameters = streamParameters.getCodecParameters();
		AVCodec codec = avcodec_find_decoder(codecParameters.codec_id());
		if (codec == null) {
			throw new IllegalArgumentException("Could not find decoder for codec id " + codecParameters.codec_id());
		}

		running = openDecoder(codec, codecParameters);
		codec.close();
		if (!running) {
			stop();
			throw new IllegalStateException("Could not open video decoder for stream " + streamId);
		}
	}

	public void setDecoderTimeBase(AVRational decoderTimeBase) {
		this.decoderTimeBase = decoderTimeBase;
	}

	public synchronized AVFrame decodeVideoPacket(AVPacket packet) {
		if (!running) {
			return null;
		}

		AVRational sourceTimeBase = streamParameters.getTimeBase();
		if (sourceTimeBase != null && decoderTimeBase != null) {
			av_packet_rescale_ts(packet, sourceTimeBase, decoderTimeBase);
		}

		int ret = avcodec_send_packet(videoContext, packet);
		if (ret < 0) {
			logger.debug("Could not send video packet to decoder for stream {}", streamId);
			return null;
		}

		ret = avcodec_receive_frame(videoContext, decodedFrame);
		if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
			return null;
		}
		if (ret < 0) {
			logger.debug("Could not receive decoded video frame for stream {}", streamId);
			return null;
		}

		return decodedFrame;
	}

	public synchronized void stop() {
		running = false;
		if (decodedFrame != null) {
			av_frame_free(decodedFrame);
			decodedFrame.close();
			decodedFrame = null;
		}
		if (videoContext != null) {
			avcodec_free_context(videoContext);
			videoContext.close();
			videoContext = null;
		}
	}

	private boolean openDecoder(AVCodec codec, AVCodecParameters codecParameters) {
		videoContext = avcodec_alloc_context3(codec);
		if (videoContext == null) {
			return false;
		}

		if (avcodec_parameters_to_context(videoContext, codecParameters) < 0) {
			avcodec_free_context(videoContext);
			videoContext = null;
			return false;
		}

		videoContext.thread_count(2);
		int ret = avcodec_open2(videoContext, codec, (AVDictionary) null);
		if (ret < 0) {
			byte[] error = new byte[256];
			av_strerror(ret, error, error.length);
			logger.warn("Could not open decoder for stream {}: {}", streamId, new String(error).trim());
			avcodec_free_context(videoContext);
			videoContext = null;
			return false;
		}
		return true;
	}
}
