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
import static org.bytedeco.ffmpeg.global.avcodec.AV_INPUT_BUFFER_PADDING_SIZE;
import static org.bytedeco.ffmpeg.global.avcodec.av_bsf_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.av_bsf_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.av_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;
import static org.bytedeco.ffmpeg.global.avutil.av_mallocz;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.bytedeco.ffmpeg.avcodec.AVBSFContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;

import io.antmedia.muxer.parser.codec.AACAudio;
import io.vertx.core.Vertx;

/** Publishes into the local MediaMTX with RTSP ANNOUNCE. The rtsp muxer is AVFMT_NOFILE, so openIO() does
 * nothing and the destination has to go on the context itself. */
public class RtspMuxer extends Muxer {

	/** Measured against the bundled MediaMTX. VP9 and AV1 need -strict experimental, AC-3 gets a 400. */
	private static final Set<Integer> SUPPORTED_CODECS = Set.of(
			AV_CODEC_ID_H264, AV_CODEC_ID_H265, AV_CODEC_ID_VP8,
			AV_CODEC_ID_MPEG4, AV_CODEC_ID_MPEG2VIDEO, AV_CODEC_ID_MPEG1VIDEO, AV_CODEC_ID_MJPEG,
			AV_CODEC_ID_AAC, AV_CODEC_ID_OPUS, AV_CODEC_ID_MP3, AV_CODEC_ID_VORBIS,
			AV_CODEC_ID_PCM_MULAW, AV_CODEC_ID_PCM_ALAW, AV_CODEC_ID_ADPCM_G722, AV_CODEC_ID_ADPCM_G726);

	private final String url;
	private volatile boolean writable = true;
	private boolean trailerWritten;

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
		if (outputFormatContext == null && writable) {
			outputFormatContext = new AVFormatContext(null);
			if (avformat_alloc_output_context2(outputFormatContext, null, format, url) < 0) {
				logger.error("Could not create RTSP output context for {}", url);
				outputFormatContext = null;
			}
		}
		return outputFormatContext;
	}

	/** MPEG-TS ingest leaves AAC extradata empty and keeps the config in every ADTS header instead, but the
	 * SDP has to carry it before the first packet goes out. */
	@Override
	public synchronized boolean addStream(AVCodecParameters params, AVRational timebase, int streamIndex,
			Optional<String> language) {
		byte[] config = params.codec_id() == AV_CODEC_ID_AAC && params.extradata_size() == 0
				? audioSpecificConfig(params)
				: null;

		if (config == null) {
			return super.addStream(params, timebase, streamIndex, language);
		}

		// the payloader only strips the ADTS header itself while extradata is empty
		setAudioBitreamFilter("aac_adtstoasc");
		if (!super.addStream(params, timebase, streamIndex, language)) {
			return false;
		}

		// freed with the format context, so it has to come out of av_malloc and carry the decoder padding
		AVFormatContext context = getOutputFormatContext();
		AVCodecParameters added = context.streams(context.nb_streams() - 1).codecpar();
		added.extradata(new BytePointer(av_mallocz(config.length + (long) AV_INPUT_BUFFER_PADDING_SIZE))
				.capacity(config.length).put(config));
		added.extradata_size(config.length);
		return true;
	}

	@Override
	protected void writeVideoFrame(AVPacket pkt, AVFormatContext context) {
		write(pkt, context);
	}

	@Override
	protected void writeAudioFrame(AVPacket pkt, AVRational inputTimebase, AVRational outputTimebase,
			AVFormatContext context, long dts) {
		if (!writable) {
			return;
		}

		for (AVBSFContext filter : bsfAudioFilterContextMap.getOrDefault(pkt.stream_index(), Set.of())) {
			if (av_bsf_send_packet(filter, pkt) < 0 || av_bsf_receive_packet(filter, pkt) < 0) {
				logPacketIssue("Audio bitstream filter dropped a packet for stream {}", streamId);
				return;
			}
		}
		write(pkt, context);
	}

	/** AMS tears a muxer down through several paths and they all reach this one. Only the first counts. */
	@Override
	public synchronized void writeTrailer() {
		writable = false;
		if (!trailerWritten) {
			trailerWritten = true;
			super.writeTrailer();
		}
	}

	/** Also runs when the header fails, and it frees the AVPackets. */
	@Override
	protected synchronized void clearResource() {
		super.clearResource();
		writable = false;
	}

	@Override
	public synchronized boolean prepareIO() {
		return writable && super.prepareIO();
	}

	/** No filter loop, RTSP registers no video bitstream filters. */
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

	/** 2 byte AudioSpecificConfig: 5 bits object type, 4 bits sample rate index, 4 bits channel config.
	 * Null when the parameters have no config that describes them. */
	private static byte[] audioSpecificConfig(AVCodecParameters params) {
		int rateIndex = ArrayUtils.indexOf(AACAudio.AAC_SAMPLERATES, params.sample_rate());
		int channels = params.ch_layout().nb_channels();
		if (rateIndex < 0 || channels < 1 || channels == 7 || channels > 8) {
			return null;
		}

		// ADTS carries 2 bits of profile, so anything outside that came from elsewhere and is AAC-LC in the frames
		int profile = params.profile();
		int objectType = profile >= 0 && profile <= 3 ? profile + 1 : 2;
		int channelConfig = channels == 8 ? 7 : channels;

		return new byte[] {
				(byte) ((objectType << 3) | (rateIndex >> 1)),
				(byte) (((rateIndex & 1) << 7) | (channelConfig << 3)) };
	}

	@Override
	public boolean isCodecSupported(int codecId) {
		return SUPPORTED_CODECS.contains(codecId);
	}

	public boolean isWritable() {
		return writable;
	}
}
