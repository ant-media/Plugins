package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_rescale_ts;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name;
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
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12;


import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.SystemUtils;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.antmedia.statistic.GPUUtils;

public class VideoDecoder {
	static Logger logger = LoggerFactory.getLogger(VideoDecoder.class);
	private AVCodecContext videoContext;
	private String streamId;
	private AVFrame decodedFrame;
	private StreamParametersInfo streamParameters;
	private boolean running = false;
	
	private AVRational decoderTimeBase;

	
	public VideoDecoder(String streamId, StreamParametersInfo streamParameters) {
		this.streamId = streamId;
		
		this.streamParameters = streamParameters;
		
		if ((decodedFrame = av_frame_alloc()) == null) {
			throw new IllegalArgumentException("av_frame_alloc() error: Could not allocate raw picture frame.");
		}
		
		String decoderName = null;
		AVCodec codec = null;
		if (GPUUtils.getInstance().getDeviceCount() > 0 && streamParameters.getCodecParameters().codec_id() == AV_CODEC_ID_H264) {
			if (SystemUtils.OS_TYPE == SystemUtils.LINUX || SystemUtils.OS_TYPE == SystemUtils.WINDOWS) {
				decoderName = "h264_cuvid";
			} else if (SystemUtils.OS_TYPE == SystemUtils.MAC_OS_X) {
				/**
				 *  when using h264_vda, it throws signal error 0xb
				 *  av_default_vda_free
				 *  it is probably due to we cannot initialize vda and i think
				 *  codec parameters are false
				 *  encoderName = "h264_vda";
				 */

			}
		}

		boolean result = false;
		if (decoderName != null) {
			codec = avcodec_find_decoder_by_name(decoderName);
			result = openDecoder(codec, streamParameters.getCodecParameters());
		}

		if (!result) {
			if (codec != null) {
				codec.close();
				codec = null;
			}
			codec = avcodec_find_decoder(streamParameters.getCodecParameters().codec_id());
			result = openDecoder(codec, streamParameters.getCodecParameters());
		}
		else {
			//h264_cuvid output pixel format is NV12, so update it
			streamParameters.getCodecParameters().format(AV_PIX_FMT_NV12);
		}
		
		BytePointer name = codec.name();
		logger.info("video decoder name: {} video context timebase:{}/{} wxh:{}x{}" , name.getString(),  videoContext.time_base().num(), videoContext.time_base().den(), videoContext.width(), videoContext.height());
		codec.close();
		
		running = result;
		
		if(!running) {
			stop();
		}
	}
	
	public void setDecoderTimeBase(AVRational decoderTimeBase) {
		this.decoderTimeBase = decoderTimeBase;
	}

	public boolean openDecoder(AVCodec codec, AVCodecParameters par) {
		videoContext = avcodec_alloc_context3(codec);
		if (videoContext == null) {
			logger.warn("cannot allocate video context : {} " , codec.name().getString());
			return false;
		}

		int ret;
		/* copy the stream parameters from the muxer */
		if (avcodec_parameters_to_context(videoContext, par) < 0) {
			logger.warn("cannot copy context parameters: {} " , codec.name().getString());
			avcodec_free_context(videoContext);
			return false;
		}

		videoContext.thread_count(6);

		// Open video codec
		if ((ret = avcodec_open2(videoContext, codec, (AVDictionary) null)) < 0) {
			if (logger.isErrorEnabled()) {
				byte[] data = new byte[2048];
				av_strerror(ret, data, data.length);
				logger.error(" Opening decoder error: {}" , new String(data, 0, data.length));
			}
			avcodec_free_context(videoContext);
			return false;
		}

		return true;
	}
	
	public synchronized AVFrame decodeVideoPacket(AVPacket pkt) {
		
		if (!isRunning()) {
			logger.error("Video decoder is not running for streamId: {}" , streamId);
			return null;
		}
		logger.debug("Video packet is received for streamId:{} pkt pts:{} timebase:{}/{} target timebase: {}/{}", streamId, pkt.pts(), 
				streamParameters.getTimeBase().num(), streamParameters.getTimeBase().den(), decoderTimeBase.num(), decoderTimeBase.den());
		
	
		av_packet_rescale_ts(pkt,
				streamParameters.getTimeBase(),
				decoderTimeBase
				);
		
		int ret = avcodec_send_packet(videoContext, pkt);
		if (ret < 0) {
			logger.error("Cannot send video packet for decoding for stream: {}", streamId);
		}

		ret = avcodec_receive_frame(videoContext, decodedFrame);
		
		if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF()) {
			logger.debug("Video decoder is not ready to decode the packet for streamId: {} ret: {}" , streamId, Utils.getErrorDefinition(ret));
			return null;
		}
		else if (ret < 0) {
			logger.error("Decode video frame error: {}" , Utils.getErrorDefinition(ret));
			return null;
		}

		return decodedFrame;
	}
	
	public synchronized void stop()  {
		running = false;
		synchronized (org.bytedeco.ffmpeg.presets.avcodec.class) {
			releaseUnsafe();
		}
	}

	void releaseUnsafe()  {
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

	public boolean isRunning() {
		return running;
	}
}
