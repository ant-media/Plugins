package io.antmedia.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import io.antmedia.AppSettings;
import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.IAppSettingsUpdateListener;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;

@Component(value = "plugin.aivision")
public class AIVisionPlugin implements ApplicationContextAware, IStreamListener {

	private static final Logger logger = LoggerFactory.getLogger(AIVisionPlugin.class);
	public static final String PLUGIN_PATH = "aivision";
	public static final String PLUGIN_KEY = "aivision";
	private static final String SETTINGS_KEY = "plugin." + PLUGIN_KEY;

	private final Map<String, AIVisionPacketListener> listeners = new ConcurrentHashMap<>();
	private final Map<String, PeriodicAnalysis> periodicAnalyses = new ConcurrentHashMap<>();
	private final AtomicLong resultSequence = new AtomicLong();
	private final OpenAICompatibleVisionClient aiClient = new OpenAICompatibleVisionClient();
	private final AIVisionWebhookNotifier webhookNotifier = new AIVisionWebhookNotifier();
	private final Gson gson = new Gson();
	private volatile AIVisionSettings settings = new AIVisionSettings();

	private ApplicationContext applicationContext;
	private Vertx vertx;
	private AntMediaApplicationAdapter application;
	private String appName;
	private File imageDirectory;
	private String imageUrlPrefix;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");

		IAntMediaStreamHandler app = getApplication();
		application = (AntMediaApplicationAdapter) app;
		appName = app.getScope().getName();
		imageDirectory = new File(IAntMediaStreamHandler.WEBAPPS_PATH + appName + File.separator + "streams", PLUGIN_PATH);
		imageUrlPrefix = "/" + appName + "/streams/" + PLUGIN_PATH;
		imageDirectory.mkdirs();
		AppSettings appSettings = app.getAppSettings();
		Object storedSettings = appSettings.getCustomSetting(SETTINGS_KEY);
		boolean settingsNeedPersistence = storedSettings == null || gson.toJson(storedSettings).contains("registerToAllStreams");
		settings = getAIVisionSettings(appSettings);
		app.addSettingsUpdateListener(new IAppSettingsUpdateListener() {
			@Override
			public boolean settingsUpdated(AppSettings newSettings) {
				settings = getAIVisionSettings(newSettings);
				logger.info("AI Vision settings updated from AppSettings for app {}. baseUrl: {}, model: {}",
						appName, settings.getBaseUrl(), settings.getModel());
				return true;
			}

			@Override
			public AppSettings getCurrentSettings() {
				return null;
			}
		});
		if (settingsNeedPersistence) {
			logger.info("Persisting normalized AI Vision settings to AppSettings for app {}", appName);
			updateSettings(settings);
		}
		application.addStreamListener(this);
		logger.info("AI Vision plugin initialized for app {}. Images will be stored in {}", appName, imageDirectory.getAbsolutePath());
	}

	private Result register(String streamId) {
		if (streamId == null || streamId.isBlank()) {
			return new Result(false, "streamId is required");
		}

		AIVisionPacketListener existing = listeners.get(streamId);
		if (existing != null) {
			return new Result(true, "Stream is already registered");
		}

		AIVisionPacketListener listener = new AIVisionPacketListener(streamId, imageDirectory, imageUrlPrefix, vertx);
		boolean added = getApplication().addPacketListener(streamId, listener);
		if (!added) {
			return new Result(false, "Could not register packet listener for stream " + streamId);
		}

		listeners.put(streamId, listener);
		logger.info("AI Vision packet listener registered for stream {}", streamId);
		return new Result(true, "AI Vision packet listener registered for stream " + streamId);
	}

	private Result unregister(String streamId) {
		AIVisionPacketListener listener = listeners.remove(streamId);
		if (listener == null) {
			return new Result(false, "Stream is not registered");
		}

		getApplication().removePacketListener(streamId, listener);
		listener.stop();
		logger.info("AI Vision packet listener unregistered for stream {}", streamId);
		return new Result(true, "AI Vision packet listener unregistered for stream " + streamId);
	}

	@Override
	public void streamStarted(Broadcast broadcast) {
		if (broadcast != null) {
			String streamId = broadcast.getStreamId();
			register(streamId);
			periodicAnalyses.values().stream()
					.filter(analysis -> analysis.applyToAll)
					.forEach(analysis -> analysis.streamIds.addIfAbsent(streamId));
		}
	}

	@Override
	public void streamFinished(Broadcast broadcast) {
		if (broadcast != null) {
			String streamId = broadcast.getStreamId();
			unregister(streamId);
			periodicAnalyses.values().forEach(analysis -> {
				analysis.streamIds.remove(streamId);
				analysis.lastFrameTimestamps.remove(streamId);
			});
		}
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		// No room-specific action is required.
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		// No room-specific action is required.
	}

	public AIVisionAnalysisResponse analyze(String streamId, String prompt) {
		return analyze(streamId, prompt, 0);
	}

	public AIVisionAnalysisResponse analyze(String streamId, String prompt, long lastFrameTimestamp) {
		if (streamId == null || streamId.isBlank()) {
			return new AIVisionAnalysisResponse(false, "streamId is required");
		}
		if (prompt == null || prompt.isBlank()) {
			return new AIVisionAnalysisResponse(false, "prompt is required");
		}

		AIVisionPacketListener listener = listeners.get(streamId);
		if (listener == null) {
			Result registerResult = register(streamId);
			if (!registerResult.isSuccess()) {
				return new AIVisionAnalysisResponse(false, registerResult.getMessage());
			}
			listener = listeners.get(streamId);
		}

		AIVisionImage latestImage = waitForFreshFrame(listener, lastFrameTimestamp);
		if (latestImage == null) {
			return new AIVisionAnalysisResponse(false, "No newer decoded frame is available yet for stream " + streamId);
		}

		try {
			String enhancedPrompt = buildAnalysisPrompt(prompt);
			String aiResponse = aiClient.analyze(settings, enhancedPrompt, latestImage.getFile());
			AIVisionAnalysisResponse response = new AIVisionAnalysisResponse(true, "AI analysis completed");
			response.setStreamId(streamId);
			response.setPrompt(prompt);
			response.setImagePath(latestImage.getFile().getAbsolutePath());
			response.setImageUrl(latestImage.getUrl());
			response.setFrameTimestamp(latestImage.getTimestamp());
			response.setResultId(resultSequence.incrementAndGet());
			response.setAnalysisTimestamp(System.currentTimeMillis());
			response.setAiResponse(aiResponse);
			List<String> alerts = findAlerts(prompt, aiResponse);
			response.setAlerts(alerts);
			response.setAlertTriggered(!alerts.isEmpty());
			if (response.isAlertTriggered()) {
				webhookNotifier.notify(settings.getWebhookUrl(), response);
			}
			return response;
		}
		catch (Exception e) {
			logger.warn("AI analysis failed for stream {}", streamId, e);
			String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return new AIVisionAnalysisResponse(false, "AI analysis failed: " + errorMessage);
		}
	}

	public Result startPeriodicAnalysis(String prompt, int periodSeconds, List<String> streamIds, boolean applyToAll) {
		if (prompt == null || prompt.isBlank()) {
			return new Result(false, "prompt is required");
		}
		if (!applyToAll && (streamIds == null || streamIds.isEmpty())) {
			return new Result(false, "streamIds is required");
		}
		if (periodSeconds < 1 || periodSeconds > 86400) {
			return new Result(false, "period must be between 1 and 86400 seconds");
		}

		List<String> targets = new ArrayList<>();
		for (String streamId : streamIds != null ? streamIds : Collections.<String>emptyList()) {
			if (streamId != null && !streamId.isBlank() && !targets.contains(streamId)) {
				targets.add(streamId);
			}
		}
		if (!applyToAll && targets.isEmpty()) {
			return new Result(false, "at least one valid streamId is required");
		}
		for (String streamId : targets) {
			Result registerResult = register(streamId);
			if (!registerResult.isSuccess()) {
				return registerResult;
			}
		}

		String requestId = UUID.randomUUID().toString();
		PeriodicAnalysis analysis = new PeriodicAnalysis(prompt, periodSeconds, targets, applyToAll);
		periodicAnalyses.put(requestId, analysis);
		runPeriodicAnalysis(requestId, analysis);
		analysis.timerId = vertx.setPeriodic(periodSeconds * 1000L, id -> runPeriodicAnalysis(requestId, analysis));
		logger.info("Periodic AI Vision analysis {} started for prompt '{}' on streams {} with period {} seconds", requestId, prompt, targets, periodSeconds);
		return new Result(true, requestId, "Periodic analysis started");
	}

	public Result stopPeriodicAnalysis(String requestId) {
		if (requestId == null || requestId.isBlank()) {
			return new Result(false, "requestId is required");
		}
		PeriodicAnalysis analysis = periodicAnalyses.remove(requestId);
		if (analysis == null) {
			return new Result(false, "Periodic analysis is not running");
		}
		if (analysis.timerId != -1) {
			vertx.cancelTimer(analysis.timerId);
		}
		logger.info("Periodic AI Vision analysis {} stopped", requestId);
		return new Result(true, "Periodic analysis stopped");
	}

	public List<AIVisionAnalysisResponse> getPeriodicResults(String requestId, long afterResultId) {
		if (requestId == null || requestId.isBlank()) {
			return Collections.emptyList();
		}
		PeriodicAnalysis analysis = periodicAnalyses.get(requestId);
		if (analysis == null) {
			return Collections.emptyList();
		}

		List<AIVisionAnalysisResponse> newResults = new ArrayList<>();
		for (AIVisionAnalysisResponse response : analysis.results) {
			if (response.getResultId() > afterResultId) {
				newResults.add(response);
			}
		}
		return newResults;
	}

	public List<AIVisionPeriodicAnalysisStatus> getPeriodicAnalyses() {
		List<AIVisionPeriodicAnalysisStatus> statuses = new ArrayList<>();
		periodicAnalyses.forEach((requestId, analysis) -> {
			AIVisionPeriodicAnalysisStatus status = new AIVisionPeriodicAnalysisStatus();
			status.setRequestId(requestId);
			status.setStreamIds(analysis.streamIds);
			status.setApplyToAll(analysis.applyToAll);
			status.setPrompt(analysis.prompt);
			status.setPeriod(analysis.periodSeconds);
			status.setStartedAt(analysis.startedAt);
			status.setAnalyzing(analysis.running.get());
			status.setResultCount(analysis.results.size());
			status.setAlertCount((int) analysis.results.stream().filter(AIVisionAnalysisResponse::isAlertTriggered).count());
			status.setLastResult(analysis.results.peekLast());
			statuses.add(status);
		});
		statuses.sort((first, second) -> Long.compare(second.getStartedAt(), first.getStartedAt()));
		return statuses;
	}

	private void runPeriodicAnalysis(String requestId, PeriodicAnalysis analysis) {
		if (periodicAnalyses.get(requestId) != analysis || !analysis.running.compareAndSet(false, true)) {
			return;
		}

		vertx.executeBlocking(() -> {
			for (String streamId : analysis.streamIds) {
				long lastFrameTimestamp = analysis.lastFrameTimestamps.getOrDefault(streamId, 0L);
				AIVisionAnalysisResponse response = analyze(streamId, analysis.prompt, lastFrameTimestamp);
				if (response.isSuccess()) {
					analysis.lastFrameTimestamps.put(streamId, response.getFrameTimestamp());
				}
				else {
					response.setStreamId(streamId);
					response.setPrompt(analysis.prompt);
					response.setResultId(resultSequence.incrementAndGet());
					response.setAnalysisTimestamp(System.currentTimeMillis());
				}
				analysis.results.addLast(response);
				while (analysis.results.size() > settings.getMaxResultsPerAnalysis()) {
					analysis.results.pollFirst();
				}
			}
			return null;
		}, false).onComplete(result -> analysis.running.set(false));
	}

	private static class PeriodicAnalysis {
		private final String prompt;
		private final int periodSeconds;
		private final CopyOnWriteArrayList<String> streamIds;
		private final boolean applyToAll;
		private final long startedAt = System.currentTimeMillis();
		private final AtomicBoolean running = new AtomicBoolean();
		private final Deque<AIVisionAnalysisResponse> results = new ConcurrentLinkedDeque<>();
		private final Map<String, Long> lastFrameTimestamps = new ConcurrentHashMap<>();
		private volatile long timerId = -1;

		private PeriodicAnalysis(String prompt, int periodSeconds, List<String> streamIds, boolean applyToAll) {
			this.prompt = prompt;
			this.periodSeconds = periodSeconds;
			this.streamIds = new CopyOnWriteArrayList<>(streamIds);
			this.applyToAll = applyToAll;
		}
	}

	public synchronized AIVisionSettings updateSettings(AIVisionSettings newSettings) {
		if (newSettings == null) {
			return settings;
		}
		AIVisionSettings updatedSettings = copySettings(settings);
		if (newSettings.getBaseUrl() != null) {
			updatedSettings.setBaseUrl(newSettings.getBaseUrl());
		}
		if (newSettings.getToken() != null) {
			updatedSettings.setToken(newSettings.getToken());
		}
		if (newSettings.getModel() != null && !newSettings.getModel().isBlank()) {
			updatedSettings.setModel(newSettings.getModel());
		}
		updatedSettings.setWebhookUrl(newSettings.getWebhookUrl());
		if (newSettings.getMaxResultsPerAnalysis() > 0) {
			updatedSettings.setMaxResultsPerAnalysis(newSettings.getMaxResultsPerAnalysis());
		}

		AppSettings appSettings = application.getAppSettings();
		appSettings.setCustomSetting(SETTINGS_KEY, gson.toJson(updatedSettings));
		boolean saved = application.updateSettings(appSettings, true, false);
		if (!saved) {
			logger.warn("AI Vision settings could not be persisted for app {}", appName);
		}
		settings = updatedSettings;
		logger.info("AI Vision settings updated. baseUrl: {}, model: {}", settings.getBaseUrl(), settings.getModel());
		return settings;
	}

	public AIVisionSettings getAIVisionSettings(AppSettings appSettings) {
		if (appSettings == null) {
			return new AIVisionSettings();
		}

		Object value = appSettings.getCustomSetting(SETTINGS_KEY);
		if (value == null) {
			logger.info("Using default AI Vision settings because no settings exist in AppSettings for app {}", appName);
			return new AIVisionSettings();
		}

		try {
			AIVisionSettings parsed = value instanceof String
					? gson.fromJson((String) value, AIVisionSettings.class)
					: gson.fromJson(gson.toJsonTree(value), AIVisionSettings.class);
			return parsed != null ? normalizeSettings(parsed) : new AIVisionSettings();
		}
		catch (Exception e) {
			logger.error("Invalid AI Vision settings, using defaults for app {}", appName, e);
			return new AIVisionSettings();
		}
	}

	private AIVisionSettings normalizeSettings(AIVisionSettings source) {
		AIVisionSettings normalized = new AIVisionSettings();
		normalized.setBaseUrl(source.getBaseUrl());
		normalized.setToken(source.getToken());
		normalized.setWebhookUrl(source.getWebhookUrl());
		if (source.getMaxResultsPerAnalysis() > 0) {
			normalized.setMaxResultsPerAnalysis(source.getMaxResultsPerAnalysis());
		}
		if (source.getModel() != null && !source.getModel().isBlank()) {
			normalized.setModel(source.getModel());
		}
		return normalized;
	}

	private AIVisionSettings copySettings(AIVisionSettings source) {
		return normalizeSettings(source);
	}

	public AIVisionSettings getSettings() {
		return settings;
	}

	private AIVisionImage waitForFreshFrame(AIVisionPacketListener listener, long lastFrameTimestamp) {
		if (listener == null) {
			return null;
		}

		long deadline = System.currentTimeMillis() + 2000;
		while (listener.getLatestFrameTimestamp() <= lastFrameTimestamp && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		try {
			return listener.saveLatestFrame(lastFrameTimestamp);
		}
		catch (Exception e) {
			logger.warn("Could not save latest analysis frame", e);
			return null;
		}
	}

	private String buildAnalysisPrompt(String prompt) {
		return prompt + "\n\n"
				+ "If this prompt is a yes/no question and the answer is yes, include exactly one line in this format: "
				+ "ALERT:{explanation}. If the answer is no, do not include ALERT. Keep the rest of the answer concise.";
	}

	private List<String> findAlerts(String prompt, String aiResponse) {
		List<String> alerts = new ArrayList<>();
		if (aiResponse == null || aiResponse.isBlank()) {
			return alerts;
		}

		for (String line : aiResponse.split("\\R")) {
			int alertIndex = line.toUpperCase().indexOf("ALERT:");
			if (alertIndex < 0) {
				continue;
			}
			String explanation = line.substring(alertIndex + "ALERT:".length()).trim();
			explanation = explanation.replaceAll("^\\{", "").replaceAll("\\}$", "").trim();
			alerts.add(explanation.isBlank() ? "Alert triggered" : explanation);
		}
		return alerts;
	}

	private IAntMediaStreamHandler getApplication() {
		return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
}
