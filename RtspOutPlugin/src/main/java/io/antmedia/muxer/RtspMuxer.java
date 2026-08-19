package io.antmedia.muxer;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_ADPCM_G722;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_ADPCM_G726;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H265;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MJPEG;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MP3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG1VIDEO;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG2VIDEO;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_ALAW;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_MULAW;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VORBIS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8;
import static org.bytedeco.ffmpeg.global.avformat.av_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;

import java.util.Set;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVRational;

import io.vertx.core.Vertx;

/**
 * Publishes a stream into the local MediaMTX with RTSP ANNOUNCE. FFmpeg's rtsp muxer is AVFMT_NOFILE and
 * opens its own connection in writeHeader, so openIO() does nothing and the destination goes on the context.
 */
public class RtspMuxer extends Muxer {

	/** Measured against the pinned MediaMTX.
	 * VP9 and AV1 need -strict experimental, AC-3 gets a 400. */
	private static final Set<Integer> SUPPORTED_CODECS = Set.of(
			AV_CODEC_ID_H264, AV_CODEC_ID_H265, AV_CODEC_ID_VP8,
			AV_CODEC_ID_MPEG4, AV_CODEC_ID_MPEG2VIDEO, AV_CODEC_ID_MPEG1VIDEO, AV_CODEC_ID_MJPEG,
			AV_CODEC_ID_AAC, AV_CODEC_ID_OPUS, AV_CODEC_ID_MP3, AV_CODEC_ID_VORBIS,
			AV_CODEC_ID_PCM_MULAW, AV_CODEC_ID_PCM_ALAW, AV_CODEC_ID_ADPCM_G722, AV_CODEC_ID_ADPCM_G726);

	private final String url;
	private volatile boolean writable = true;

	public RtspMuxer(String url, Vertx vertx) {
		super(vertx);
		this.format = "rtsp";
		this.url = url;

		// Publish leg only. Players negotiate their own transport with MediaMTX.
		options.put("rtsp_transport", "tcp");
		// FFmpeg defaults to 1472, above the 1440 MediaMTX accepts before re-fragmenting every packet.
		options.put("pkt_size", "1400");
	}

	@Override
	public String getOutputURL() {
		return url;
	}

	@Override
	public AVFormatContext getOutputFormatContext() {
		if (outputFormatContext == null) {
			outputFormatContext = new AVFormatContext(null);
			if (avformat_alloc_output_context2(outputFormatContext, null, format, url) < 0) {
				logger.error("Could not create RTSP output context for {}", url);
				outputFormatContext = null;
			}
		}
		return outputFormatContext;
	}

	@Override
	protected void writeVideoFrame(AVPacket pkt, AVFormatContext context) {
		write(pkt, context);
	}

	@Override
	protected void writeAudioFrame(AVPacket pkt, AVRational inputTimebase, AVRational outputTimebase,
			AVFormatContext context, long dts) {
		write(pkt, context);
	}

	@Override
	public synchronized void writeTrailer() {
		writable = false;
		super.writeTrailer();
	}

	/** No filter loop, RTSP registers no bitstream filters. Gives up on the first failed write. */
	private void write(AVPacket pkt, AVFormatContext context) {
		if (!writable) {
			return;
		}

		int ret = av_write_frame(context, pkt);
		if (ret < 0) {
			writable = false;
			logger.warn("RTSP publish to {} failed for stream {}, dropping the rest of the stream. Error is {}",
					url, streamId, getErrorDefinition(ret));
		}
	}

	@Override
	public boolean isCodecSupported(int codecId) {
		return SUPPORTED_CODECS.contains(codecId);
	}

	public boolean isWritable() {
		return writable;
	}
}
