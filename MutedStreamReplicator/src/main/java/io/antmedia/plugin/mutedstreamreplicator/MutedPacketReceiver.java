package io.antmedia.plugin.mutedstreamreplicator;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVRational;

import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.muxer.Muxer;

public class MutedPacketReceiver extends Muxer {
	private static final Logger logger = LoggerFactory.getLogger(MutedPacketReceiver.class);

	private final List<Muxer> targetMuxerList;
	private volatile boolean firstKeyframeSeen = false;

	public MutedPacketReceiver(List<Muxer> targetMuxerList) {
		super(null);
		this.targetMuxerList = targetMuxerList != null ? targetMuxerList : Collections.emptyList();
	}

	@Override
	public void init(IScope scope, String name, int resolution, boolean overrideIfExist, String subFolder, int bitrate) {
	}

	@Override
	public synchronized boolean addStream(AVCodecParameters codecParameters, AVRational timebase, int streamIndex) {
		return true;
	}

	@Override
	public synchronized boolean addStream(AVCodec codec, AVCodecContext codecContext, int streamIndex) {
		return true;
	}

	@Override
	public synchronized void writePacket(AVPacket pkt, AVStream stream) {
		if (!firstKeyframeSeen) {
			if (stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO && (pkt.flags() & AV_PKT_FLAG_KEY) != 0) {
				firstKeyframeSeen = true;
			} else {
				return;
			}
		}
		for (Muxer muxer : getTargetMuxerSnapshot()) {
			muxer.writePacket(pkt, stream);
		}
	}

	@Override
	public synchronized void writePacket(AVPacket pkt, AVCodecContext codecContext) {
		if (!firstKeyframeSeen) {
			if (codecContext.codec_type() == AVMEDIA_TYPE_VIDEO && (pkt.flags() & AV_PKT_FLAG_KEY) != 0) {
				firstKeyframeSeen = true;
			} else {
				return;
			}
		}
		for (Muxer muxer : getTargetMuxerSnapshot()) {
			muxer.writePacket(pkt, codecContext);
		}
	}
	
	@Override
	public synchronized void writeAudioBuffer(ByteBuffer audioFrame, int streamIndex, long timestamp) {
	}

	@Override
	public synchronized void writeVideoBuffer(ByteBuffer encodedVideoFrame, long dts, int frameRotation,
			int streamIndex, boolean isKeyFrame, long firstFrameTimeStamp, long pts) {
		for (Muxer muxer : getTargetMuxerSnapshot()) {
			muxer.writeVideoBuffer(encodedVideoFrame, dts, frameRotation, streamIndex, isKeyFrame, firstFrameTimeStamp, pts);
		}		
	}

	@Override
	public synchronized void writeTrailer() {
		isRunning.set(false);
	}

	@Override
	public synchronized boolean prepareIO() {
		isRunning.set(true);
		return true;
	}

	@Override
	public boolean isCodecSupported(int codecId) {
		return true;
	}

	@Override
	public AVFormatContext getOutputFormatContext() {
		return null;
	}

	/**
	 * Copy the current target list so packet fan-out does not hold the encoder lock while writing.
	 */
	private List<Muxer> getTargetMuxerSnapshot() {
		synchronized (targetMuxerList) {
			if (targetMuxerList.isEmpty()) {
				logger.debug("Muted packet receiver has no target muxers for stream {}", streamId);
				return Collections.emptyList();
			}
			return new ArrayList<>(targetMuxerList);
		}
	}
}
