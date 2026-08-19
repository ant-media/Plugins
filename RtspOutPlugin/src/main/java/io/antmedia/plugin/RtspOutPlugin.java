package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.RtspMuxer;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.settings.ServerSettings;
import io.vertx.core.Vertx;
import jakarta.annotation.PreDestroy;

@Component(value = RtspOutPlugin.BEAN_NAME)
public class RtspOutPlugin implements ApplicationContextAware, IStreamListener {

	public static final String BEAN_NAME = "plugin.rtsp-out";
	public static final String SETTINGS_KEY = "plugin.rtsp-out";

	private static final Logger logger = LoggerFactory.getLogger(RtspOutPlugin.class);
	private static final Gson gson = new Gson();
	private static final String LOOPBACK = "127.0.0.1";

	/** How often a stream that is missing an endpoint gets another go. */
	private static final long RECONCILE_INTERVAL_MS = 2000;

	/** Attach attempts per endpoint per MediaMTX generation. Only attempts that reached a live MediaMTX count. */
	private static final int MAX_ATTACH_ATTEMPTS = 10;

	private IAntMediaStreamHandler app;
	private AppSettings appSettings;
	private ServerSettings serverSettings;
	private Vertx vertx;
	private String appName;
	private long reconcileTimerId = -1;

	/** per stream REST override of enabledByDefault */
	private final Map<String, Boolean> overrides = new ConcurrentHashMap<>();

	private final Map<String, StreamSession> sessions = new ConcurrentHashMap<>();

	/** Keeps periodic passes from piling up behind one that is still doing native work. */
	private final AtomicBoolean reconciling = new AtomicBoolean();

	/** What one published stream owns. Start and finish come from different threads. */
	private static final class StreamSession {

		/** Live endpoints, keyed by MediaMTX path: the source plus one per rendition. */
		final Map<String, RtspMuxer> attached = new LinkedHashMap<>();
		final Map<String, Integer> attempts = new HashMap<>();

		/** The MediaMTX the attached muxers publish into. < 0 means they are stale. */
		int generation = -1;

		/** Every wanted endpoint is attached or out of attempts. */
		boolean settled;
		boolean closed;

		synchronized boolean needsWork(int currentGeneration) {
			if (closed) {
				return false;
			}

			if (!settled || generation != currentGeneration) {
				return true;
			}

			return attached.values().stream().anyMatch(muxer -> !muxer.isWritable());
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext ctx) throws BeansException {
		vertx = (Vertx) ctx.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME);
		serverSettings = (ServerSettings) ctx.getBean(ServerSettings.BEAN_NAME);
		app = (IAntMediaStreamHandler) ctx.getBean(AntMediaApplicationAdapter.BEAN_NAME);
		appSettings = app.getAppSettings();
		appName = app.getScope().getName();

		app.addStreamListener(this);

		RtspOutSettings settings = loadSettings();
		logger.info("RTSP out plugin initialized for app {}. enabled={} enabledByDefault={} port={}",
				appName, settings.isEnabled(), settings.isEnabledByDefault(), settings.getRtspPort());

		if (settings.isEnabled()) {
			MediaMtxProcess.start(settings);
		}
		reconcileTimerId = vertx.setPeriodic(RECONCILE_INTERVAL_MS, id -> reconcileLater());
	}

	/** App undeploy. MediaMTX is shared and only the shutdown hook stops it. Nulls because init may have thrown. */
	@PreDestroy
	public void shutdown() {
		if (vertx != null && reconcileTimerId != -1) {
			vertx.cancelTimer(reconcileTimerId);
			reconcileTimerId = -1;
		}

		if (app != null) {
			app.removeStreamListener(this);
		}

		new ArrayList<>(sessions.keySet()).forEach(this::closeSession);
	}

	public RtspOutSettings loadSettings() {
		Object raw = appSettings.getCustomSetting(SETTINGS_KEY);
		if (raw != null) {
			try {
				RtspOutSettings parsed = gson.fromJson(raw.toString(), RtspOutSettings.class);
				if (parsed != null) {
					return parsed;
				}
			} catch (Exception e) {
				logger.error("Invalid RTSP out settings, using defaults: {}", e.getMessage());
			}
		}
		return new RtspOutSettings();
	}

	public void enable(String streamId) {
		overrides.put(streamId, true);
	}

	public void disable(String streamId) {
		overrides.put(streamId, false);
	}

	private boolean isEnabledFor(String streamId, RtspOutSettings settings) {
		return settings.isEnabled() && overrides.getOrDefault(streamId, settings.isEnabledByDefault());
	}

	/** The port MediaMTX is really on. The first app to load wins, the rest publish and advertise that one. */
	private int rtspPort() {
		int running = MediaMtxProcess.getPort();
		return running != 0 ? running : loadSettings().getRtspPort();
	}

	/** Playback URL as a client outside the server would use it. */
	public String getRtspUrl(String path) {
		return rtspUrl(serverSettings.getHostAddress(), rtspPort(), path);
	}

	private String rtspUrl(String host, int port, String path) {
		return "rtsp://" + host + ":" + port + "/" + appName + "/" + path;
	}

	/** Same _<height>p suffix recordings use. */
	private static String renditionPath(String streamId, int height) {
		return streamId + "_" + height + "p";
	}

	public Map<String, Object> getStatus() {
		RtspOutSettings settings = loadSettings();
		int port = rtspPort();

		Map<String, Object> endpoints = new LinkedHashMap<>();
		sessions.forEach((streamId, session) -> {
			synchronized (session) {
				endpoints.put(streamId, List.copyOf(session.attached.keySet()));
			}
		});

		Map<String, Object> status = new LinkedHashMap<>();
		status.put("app", appName);
		status.put("enabled", settings.isEnabled());
		status.put("enabledByDefault", settings.isEnabledByDefault());
		status.put("rtspPort", port);
		status.put("configuredRtspPort", settings.getRtspPort());
		status.put("streamOverrides", Map.copyOf(overrides));
		status.put("activeStreams", endpoints);
		status.put("mediamtxBinary", MediaMtxProcess.getBinaryPath());
		status.put("mediamtxRunning", MediaMtxProcess.isRunning());
		status.put("mediamtxListening", port != 0 && MediaMtxProcess.isListening(port));
		status.put("mediamtxGeneration", MediaMtxProcess.getGeneration());
		status.put("mediamtxGaveUp", MediaMtxProcess.isGivenUp());
		return status;
	}

	@Override
	public void streamStarted(Broadcast broadcast) {
		String streamId = broadcast.getStreamId();
		RtspOutSettings settings = loadSettings();

		if (!isEnabledFor(streamId, settings)) {
			logger.debug("RTSP out is disabled for stream {}", streamId);
			return;
		}

		if (sessions.putIfAbsent(streamId, new StreamSession()) != null) {
			logger.warn("RTSP out is already serving stream {}, ignoring the duplicate start", streamId);
			return;
		}

		try {
			// already on a Vert.x worker thread, so the same blocking pass the timer runs is fine inline
			reconcile(List.of(streamId));
		} catch (Exception e) {
			// the periodic pass picks it up, and the other stream listeners still get their turn
			logger.error("RTSP out could not be started for stream {}, leaving it to the reconciler", streamId, e);
		}
	}

	@Override
	public void streamFinished(Broadcast broadcast) {
		closeSession(broadcast.getStreamId());
	}

	private void closeSession(String streamId) {
		StreamSession session = sessions.remove(streamId);
		if (session == null) {
			return;
		}

		synchronized (session) {
			// under the lock and before the detach, so a pass that is waiting on us cannot attach after
			session.closed = true;
			int count = session.attached.size();
			detachAll(streamId, session);
			logger.info("RTSP out stopped for stream {}, closed {} endpoints", streamId, count);
		}
	}

	/** Hands one pass to a worker thread. The socket probe and prepareIO must never see the event loop. */
	private void reconcileLater() {
		if (sessions.isEmpty() || !reconciling.compareAndSet(false, true)) {
			return;
		}
		try {
			vertx.executeBlocking(() -> {
				try {
					reconcile(sessions.keySet());
				} catch (Exception e) {
					logger.error("RTSP out reconcile pass failed for app {}", appName, e);
				} finally {
					reconciling.set(false);
				}
				return null;
			}, false);
		} catch (Exception e) {
			reconciling.set(false);
			logger.error("Could not schedule the RTSP out reconcile pass for app {}", appName, e);
		}
	}

	/** In case stream drops or somehting,
	 * Brings streams back to the endpoints they should have.
	 * Blocking.. */
	private void reconcile(Collection<String> streamIds) {
		int generation = MediaMtxProcess.getGeneration();
		if (MediaMtxProcess.isGivenUp()) {
			return;
		}

		List<String> due = streamIds.stream()
				.filter(streamId -> {
					StreamSession session = sessions.get(streamId);
					return session != null && session.needsWork(generation);
				})
				.toList();
		if (due.isEmpty()) {
			return;
		}

		RtspOutSettings settings = loadSettings();
		if (settings.isEnabled()) {
			// covers a boot start that never happened, so the master switch works without a server restart
			MediaMtxProcess.ensureStarted(settings);
		}

		// no attempts are spent while MediaMTX is down, the streams just wait for the next pass
		int port = MediaMtxProcess.getPort();
		if (port == 0) {
			logger.debug("MediaMTX never started, {} stream(s) in app {} are waiting for RTSP", due.size(), appName);
			return;
		}
		if (!MediaMtxProcess.isListening(port)) {
			logger.debug("MediaMTX is not listening on port {} yet, {} stream(s) in app {} are waiting for RTSP",
					port, due.size(), appName);
			return;
		}

		for (String streamId : due) {
			StreamSession session = sessions.get(streamId);
			if (session == null) {
				continue;
			}
			try {
				apply(streamId, session, generation, port);
			} catch (Exception e) {
				// one stream must not take the rest of the pass down with it
				logger.error("RTSP out could not be reconciled for stream {}", streamId, e);
			}
		}
	}

	private void apply(String streamId, StreamSession session, int generation, int port) {
		synchronized (session) {
			if (session.closed) {
				return;
			}

			if (session.generation != generation) {
				// these publish into a MediaMTX that is gone, they cannot be reused
				detachAll(streamId, session);
				session.attempts.clear();
				session.generation = generation;
				session.settled = false;
			}

			MuxAdaptor muxAdaptor = app.getMuxAdaptor(streamId);
			if (muxAdaptor == null || muxAdaptor.isStopRequestExist()) {
				// mid teardown, or not registered yet. Opening a path now would leave one nothing closes
				return;
			}

			rebuildDeadEndpoints(session, muxAdaptor);

			boolean settled = true;
			for (Map.Entry<String, Integer> wanted : missingEndpoints(streamId, session, muxAdaptor).entrySet()) {
				String path = wanted.getKey();
				int attempt = session.attempts.getOrDefault(path, 0) + 1;
				if (attempt > MAX_ATTACH_ATTEMPTS) {
					continue;
				}
				session.attempts.put(path, attempt);

				RtspMuxer muxer = attach(muxAdaptor, port, path, wanted.getValue());
				if (muxer != null) {
					session.attached.put(path, muxer);
				} else if (attempt == MAX_ATTACH_ATTEMPTS) {
					logger.warn("Giving up on RTSP endpoint {} after {} attempts, MuxAdaptor had nothing to bind "
							+ "at height {}", path, attempt, wanted.getValue());
				} else {
					settled = false;
				}
			}
			session.settled = settled;
		}
	}

	/** Replaces endpoints that died while the stream stayed up. A muxer cannot be revived, only replaced. */
	private void rebuildDeadEndpoints(StreamSession session, MuxAdaptor muxAdaptor) {
		session.attached.entrySet().removeIf(endpoint -> {
			if (endpoint.getValue().isWritable()) {
				return false;
			}
			logger.warn("RTSP endpoint {} stopped publishing, rebuilding it", endpoint.getKey());
			muxAdaptor.removeMuxer(endpoint.getValue());
			return true;
		});
	}

	/** The source under the bare stream id, plus one path per rendition. Skips attached, bindHeight() has side effects. */
	private Map<String, Integer> missingEndpoints(String streamId, StreamSession session, MuxAdaptor muxAdaptor) {
		Map<String, Integer> missing = new LinkedHashMap<>();
		if (!session.attached.containsKey(streamId)) {
			missing.put(streamId, bindHeight(muxAdaptor));
		}

		List<EncoderSettings> renditions = muxAdaptor.getEncoderSettingsList();
		if (renditions != null) {
			for (EncoderSettings rendition : renditions) {
				int height = rendition.getHeight();
				String path = renditionPath(streamId, height);
				if (height > 0 && !session.attached.containsKey(path)) {
					missing.put(path, height);
				}
			}
		}
		return missing;
	}

	/** Binds one muxer to one MediaMTX path. Returns null when the adaptor had nothing to bind. */
	private RtspMuxer attach(MuxAdaptor muxAdaptor, int port, String path, int height) {
		RtspMuxer muxer = new RtspMuxer(rtspUrl(LOOPBACK, port, path), vertx);

		if (!muxAdaptor.addMuxer(muxer, height)) {
			// EncoderAdaptor attaches to the encoders before prepareIO and never detaches when it fails.
			muxAdaptor.removeMuxer(muxer);
			logger.debug("No RTSP endpoint at {} for height {}, retrying", path, height);
			return null;
		}

		if (muxAdaptor.isEnableVideo() && muxer.getVideoHeight() == 0) {
			logger.warn("RTSP endpoint {} bound audio only, nothing matched height {}", path, height);
		}
		logger.info("RTSP out is serving {} at {}, bound at height {}",
				path, rtspUrl(serverSettings.getHostAddress(), port, path), height);
		return muxer;
	}

	private void detachAll(String streamId, StreamSession session) {
		if (session.attached.isEmpty()) {
			return;
		}

		// Usually already unregistered, both ingest paths call muxAdaptorRemoved before stopPublish.
		MuxAdaptor muxAdaptor = app.getMuxAdaptor(streamId);
		for (RtspMuxer muxer : session.attached.values()) {
			if (muxAdaptor != null) {
				muxAdaptor.removeMuxer(muxer);
			} else {
				muxer.writeTrailer();
			}
		}
		session.attached.clear();
	}

	/**
	 * Direct muxing reads 0 as any resolution, WebRTC ingest would bind audio only at 0.
	 * Keep getVideoCodecParameters(), getHeight() is still 0 here and this call is what sets it.
	 */
	private int bindHeight(MuxAdaptor muxAdaptor) {
		if (muxAdaptor.directMuxingSupported()) {
			return 0;
		}
		AVCodecParameters videoParameters = muxAdaptor.getVideoCodecParameters();
		return videoParameters != null ? videoParameters.height() : 0;
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		// not used
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		// not used
	}
}
