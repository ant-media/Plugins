package io.antmedia.plugin.mutedstreamreplicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.enterprise.adaptive.EncoderAdaptor;
import io.antmedia.enterprise.adaptive.StreamAdaptor;
import io.antmedia.enterprise.adaptive.base.VideoEncoder;
import io.antmedia.muxer.Muxer;
import io.vertx.core.Vertx;

public class MutedStreamManager {
	private static final long TARGET_BUFFER_CLEAR_PERIOD_MS = 50;
	private static final Logger logger = LoggerFactory.getLogger(MutedStreamManager.class);

	private final EncoderAdaptor sourceAdaptor;
	private final EncoderAdaptor targetAdaptor;
	private final List<AttachedReceiver> attachedReceivers = new ArrayList<>();
	private long targetBufferCleanerTimerId = -1;

	public MutedStreamManager(EncoderAdaptor sourceAdaptor, EncoderAdaptor targetAdaptor) {
		this.sourceAdaptor = sourceAdaptor;
		this.targetAdaptor = targetAdaptor;
	}

	public boolean start() {
		stop();
		if (sourceAdaptor == null || targetAdaptor == null) {
			logger.warn("Cannot start muted stream manager because source or target adaptor is null");
			return false;
		}

		targetAdaptor.setEnableAudio(false);

		startTargetBufferCleaner();

		int attachedCount = 0;
		attachedCount += attachDirectMuxer();
		attachedCount += attachRenditionMuxers();

		if (attachedCount == 0) {
			logger.warn("No muted packet receiver could be attached for source stream {} and target stream {}",
					sourceAdaptor.getStreamId(), targetAdaptor.getStreamId());
			return false;
		}

		return true;
	}

	/**
	 * Direct muxers on the target stream are the best-effort path for formats that do not depend on renditions.
	 */
	private int attachDirectMuxer() {
		List<Muxer> targetMuxers = targetAdaptor.getMuxerList();
		if (targetMuxers == null || targetMuxers.isEmpty()) {
			logger.info("Target stream {} has no direct muxers to mirror", targetAdaptor.getStreamId());
			return 0;
		}

		MutedPacketReceiver receiver = new MutedPacketReceiver(targetMuxers);
		if (!sourceAdaptor.addMuxer(receiver)) {
			logger.warn("Could not attach direct muted packet receiver from {} to {}",
					sourceAdaptor.getStreamId(), targetAdaptor.getStreamId());
			return 0;
		}
		attachedReceivers.add(AttachedReceiver.forMuxAdaptor(sourceAdaptor, receiver));
		return 1;
	}

	private int attachRenditionMuxers() {
		Map<Integer, List<VideoEncoder>> targetEncodersByHeight = getTargetEncodersByHeight();
		if (targetEncodersByHeight.isEmpty()) {
			logger.info("Target stream {} has no rendition encoders to mirror", targetAdaptor.getStreamId());
			return 0;
		}

		int attachedCount = 0;
		for (StreamAdaptor sourceStreamAdaptor : sourceAdaptor.getStreamAdaptorList()) {
			for (VideoEncoder sourceVideoEncoder : sourceStreamAdaptor.getVideoEncoderList()) {
				List<VideoEncoder> matchingTargetEncoders = targetEncodersByHeight.get(sourceVideoEncoder.getResolutionHeight());
				if (matchingTargetEncoders == null || matchingTargetEncoders.isEmpty()) {
					continue;
				}

				for (VideoEncoder targetVideoEncoder : matchingTargetEncoders) {
					List<Muxer> targetMuxers = targetVideoEncoder.getMuxerList();
					if (targetMuxers == null || targetMuxers.isEmpty()) {
						continue;
					}

					// Remove preview muxer for muted streams, since it causes some weird log spam.
					List<Muxer> filteredMuxers = targetMuxers.stream()
							.filter(m -> !(m instanceof io.antmedia.enterprise.preview.PreviewMuxer))
							.collect(java.util.stream.Collectors.toList());
					if (filteredMuxers.isEmpty()) {
						continue;
					}

					logger.info("Attaching muted rendition receiver for source stream {} at {}p to {} target muxers: {}",
							sourceAdaptor.getStreamId(), sourceVideoEncoder.getResolutionHeight(),
							filteredMuxers.size(),
							filteredMuxers.stream().map(m -> m.getClass().getSimpleName()
									+ "[idx=" + m.getRegisteredStreamIndexList() + "]")
									.collect(java.util.stream.Collectors.joining(", ")));

					MutedPacketReceiver receiver = new MutedPacketReceiver(filteredMuxers);
					sourceVideoEncoder.addMuxer(receiver);
					attachedReceivers.add(AttachedReceiver.forVideoEncoder(sourceVideoEncoder, receiver));
					attachedCount++;
				}
			}
		}
		return attachedCount;
	}

	private Map<Integer, List<VideoEncoder>> getTargetEncodersByHeight() {
		Map<Integer, List<VideoEncoder>> targetEncodersByHeight = new LinkedHashMap<>();
		for (StreamAdaptor targetStreamAdaptor : targetAdaptor.getStreamAdaptorList()) {
			for (VideoEncoder targetVideoEncoder : targetStreamAdaptor.getVideoEncoderList()) {
				targetEncodersByHeight
						.computeIfAbsent(targetVideoEncoder.getResolutionHeight(), ignored -> new ArrayList<>())
						.add(targetVideoEncoder);
			}
		}
		return targetEncodersByHeight;
	}

	public void stop() {
		stopTargetBufferCleaner();
		for (AttachedReceiver attachedReceiver : attachedReceivers) {
			attachedReceiver.detach();
		}
		attachedReceivers.clear();
	}

	//this is a trick to prevent feeding the encoder adaptor with packets
	private void startTargetBufferCleaner() {
		stopTargetBufferCleaner();

		Vertx vertx = targetAdaptor.getVertx();
		if (vertx == null) {
			logger.warn("Vertx is not available for muted target stream {}", targetAdaptor.getStreamId());
			return;
		}

		targetAdaptor.setBufferTimeMs(TARGET_BUFFER_CLEAR_PERIOD_MS);
		targetBufferCleanerTimerId = vertx.setPeriodic(TARGET_BUFFER_CLEAR_PERIOD_MS, id -> targetAdaptor.getBufferQueue().clear());
	}

	private void stopTargetBufferCleaner() {
		Vertx vertx = targetAdaptor != null ? targetAdaptor.getVertx() : null;
		if (targetBufferCleanerTimerId != -1 && vertx != null) {
			vertx.cancelTimer(targetBufferCleanerTimerId);
		}
		targetBufferCleanerTimerId = -1;
	}

	private static class AttachedReceiver {
		private final MutedPacketReceiver receiver;
		private final Runnable removeAction;

		private AttachedReceiver(MutedPacketReceiver receiver, Runnable removeAction) {
			this.receiver = receiver;
			this.removeAction = removeAction;
		}

		public static AttachedReceiver forMuxAdaptor(EncoderAdaptor adaptor, MutedPacketReceiver receiver) {
			return new AttachedReceiver(receiver, () -> adaptor.removeMuxer(receiver));
		}

		public static AttachedReceiver forVideoEncoder(VideoEncoder encoder, MutedPacketReceiver receiver) {
			return new AttachedReceiver(receiver, () -> encoder.removeMuxer(receiver));
		}

		public void detach() {
			removeAction.run();
			receiver.writeTrailer();
		}
	}
}
