package io.antmedia.plugin;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Subscriber;
import io.antmedia.datastore.db.types.Token;
import io.antmedia.filter.TokenFilterManager;
import io.antmedia.rest.RestServiceBase;
import io.antmedia.rest.model.Result;
import io.antmedia.security.ITokenService;
import io.antmedia.websocket.WebSocketConstants;

/**
 * Runs the play side security checks.
 * Same settings and same token service, same rules as playback over HLS
 */
class PlaybackAuthorizer {

	private static final Logger logger = LoggerFactory.getLogger(PlaybackAuthorizer.class);

	private static final String ACTION_READ = "read";
	private static final String ACTION_PUBLISH = "publish";
	private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1");

	private static final long SESSION_TTL_MS = 60L * 1000;
	private static final long SWEEP_INTERVAL_MS = 30L * 1000;

	private final RtspOutPlugin plugin;

	/** Credential session to when it was last seen. Only exists to expire the token service maps. */
	private final Map<String, Long> sessionsSeen = new HashMap<>();
	private long lastSweepMs = System.currentTimeMillis();

	PlaybackAuthorizer(RtspOutPlugin plugin) {
		this.plugin = plugin;
	}

	Result authorize(RtspAuthRequest request) {
		String denial = check(request);
		if (denial == null) {
			return new Result(true, "");
		}

		logger.warn("RTSP {} of {} from {} refused: {}", clean(request.getAction()), clean(request.getPath()),
				clean(request.getIp()), denial);
		return new Result(false, denial);
	}

	/** Null when the request is allowed, otherwise why it is not. */
	private String check(RtspAuthRequest request) {
		if (ACTION_PUBLISH.equals(request.getAction())) {
			// our own FFmpeg is the only publisher, and MediaMTX binds every interface
			return LOOPBACK.contains(request.getIp()) ? null : "publishing is only allowed from the server itself";
		}

		if (!ACTION_READ.equals(request.getAction())) {
			return "action is not allowed";
		}

		AppSettings appSettings = plugin.getAppSettings();
		if (appSettings == null) {
			return "the application is still getting initialized";
		}

		// before the checks, so a credential past its window cannot ride a stale cache entry
		sweepExpiredSessions();

		String streamId = plugin.streamIdOf(request.getPath());
		Map<String, String> params = parseQuery(request.getQuery());
		// getToken() already mirrors the RTSP password today, getPassword() is there if that ever changes
		String token = clean(firstNotBlank(
				params.get(WebSocketConstants.TOKEN), request.getToken(), request.getPassword()));
		String subscriberId = clean(firstNotBlank(params.get(WebSocketConstants.SUBSCRIBER_ID), request.getUser()));
		String subscriberCode = clean(params.get(WebSocketConstants.SUBSCRIBER_CODE));
		String sessionId = sessionId(request.getIp(), streamId, token, subscriberId, subscriberCode);

		String denial = checkTokens(appSettings, streamId, sessionId, subscriberId, token, subscriberCode);
		if (denial != null) {
			return denial;
		}

		denial = checkSubscriberBlocked(streamId, subscriberId);
		if (denial != null) {
			return denial;
		}

		rememberSession(sessionId);
		return null;
	}

	/** Mirrors TokenFilterManager, in the same order. */
	private String checkTokens(AppSettings appSettings, String streamId, String sessionId, String subscriberId,
			String token, String subscriberCode) {

		if (!TokenFilterManager.isAnySecurityEnabled(appSettings)) {
			return null;
		}

		if (StringUtils.isBlank(streamId)) {
			return "the stream id cannot be read from the RTSP path";
		}

		ITokenService tokenService = plugin.getTokenService();
		if (tokenService == null) {
			// probably community edition
			return "the token service is not available";
		}

		if (appSettings.isEnableTimeTokenForPlay() && !tokenService.checkTimeBasedSubscriber(
				subscriberId, streamId, sessionId, subscriberCode, Subscriber.PLAY_TYPE)) {
			return "the subscriber id or code is invalid";
		}

		if (appSettings.isPlayTokenControlEnabled()
				&& !tokenService.checkToken(token, streamId, sessionId, Token.PLAY_TOKEN)) {
			return "the token is invalid";
		}

		if (appSettings.isHashControlPlayEnabled()
				&& !tokenService.checkHash(token, streamId, sessionId, Token.PLAY_TOKEN)) {
			return "the hash is invalid";
		}

		if (appSettings.isPlayJwtControlEnabled()
				&& !tokenService.checkJwtToken(token, streamId, sessionId, Token.PLAY_TOKEN)) {
			return "the JWT token is invalid";
		}

		return null;
	}

	/** Mirrors SubscriberBlockFilter, runs even with all other security off. Its broadcast lookup is
	 * skipped, a missing stream is a MediaMTX 404 anyway. */
	private String checkSubscriberBlocked(String streamId, String subscriberId) {
		if (StringUtils.isAnyBlank(streamId, subscriberId)) {
			return null;
		}

		DataStore dataStore = plugin.getDataStore();
		if (dataStore == null) {
			return null;
		}

		Subscriber subscriber = dataStore.getSubscriber(streamId, subscriberId);
		return subscriber != null && subscriber.isBlocked(Subscriber.PLAY_TYPE) ? "the subscriber is blocked" : null;
	}

	/** One reader is authorized twice under different ids, so address plus credential is the only key. */
	private static String sessionId(String ip, String streamId, String token, String subscriberId,
			String subscriberCode) {
		return StringUtils.defaultString(ip) + "|" + streamId + "|" + StringUtils.defaultString(token)
				+ "|" + StringUtils.defaultString(subscriberId)
				+ "|" + StringUtils.defaultString(subscriberCode);
	}

	private synchronized void rememberSession(String sessionId) {
		sessionsSeen.put(sessionId, System.currentTimeMillis());
	}

	/** Nothing reports an RTSP session ending, so the token service maps are expired from this side. */
	private synchronized void sweepExpiredSessions() {
		long now = System.currentTimeMillis();
		if (now - lastSweepMs < SWEEP_INTERVAL_MS) {
			return;
		}
		lastSweepMs = now;

		ITokenService tokenService = plugin.getTokenService();
		sessionsSeen.entrySet().removeIf(session -> {
			if (now - session.getValue() < SESSION_TTL_MS) {
				return false;
			}
			if (tokenService != null) {
				tokenService.getAuthenticatedMap().remove(session.getKey());
				tokenService.getSubscriberAuthenticatedMap().remove(session.getKey());
			}
			return true;
		});
	}

	private static Map<String, String> parseQuery(String query) {
		Map<String, String> params = new HashMap<>();
		if (StringUtils.isBlank(query)) {
			return params;
		}

		for (String pair : query.split("&")) {
			int separator = pair.indexOf('=');
			if (separator > 0) {
				params.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
			}
		}
		return params;
	}

	private static String decode(String value) {
		try {
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return value;
		}
	}

	private static String firstNotBlank(String... values) {
		for (String value : values) {
			if (StringUtils.isNotBlank(value)) {
				return value;
			}
		}
		return null;
	}

	/** Log injection guard, the same one the AMS filters use. */
	private static String clean(String value) {
		return value != null ? value.replaceAll(RestServiceBase.REPLACE_CHARS, "_") : null;
	}
}
