package io.antmedia.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;

@Component(value = "plugin.aivision")
public class AIVisionPlugin implements ApplicationContextAware {

	private static final Logger logger = LoggerFactory.getLogger(AIVisionPlugin.class);
	public static final String PLUGIN_PATH = "aivision";

	private final Map<String, AIVisionPacketListener> listeners = new ConcurrentHashMap<>();
	private final OpenAICompatibleVisionClient aiClient = new OpenAICompatibleVisionClient();
	private final AIVisionSettings settings = new AIVisionSettings();

	private ApplicationContext applicationContext;
	private Vertx vertx;
	private String appName;
	private File imageDirectory;
	private String imageUrlPrefix;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");

		IAntMediaStreamHandler app = getApplication();
		appName = app.getScope().getName();
		imageDirectory = new File(IAntMediaStreamHandler.WEBAPPS_PATH + appName + File.separator + "streams", PLUGIN_PATH);
		imageUrlPrefix = "/" + appName + "/streams/" + PLUGIN_PATH;
		imageDirectory.mkdirs();
		logger.info("AI Vision plugin initialized for app {}. Images will be stored in {}", appName, imageDirectory.getAbsolutePath());
	}

	public Result register(String streamId) {
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

	public Result unregister(String streamId) {
		AIVisionPacketListener listener = listeners.remove(streamId);
		if (listener == null) {
			return new Result(false, "Stream is not registered");
		}

		getApplication().removePacketListener(streamId, listener);
		listener.stop();
		logger.info("AI Vision packet listener unregistered for stream {}", streamId);
		return new Result(true, "AI Vision packet listener unregistered for stream " + streamId);
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
			response.setAiResponse(aiResponse);
			List<String> alerts = findAlerts(prompt, aiResponse);
			response.setAlerts(alerts);
			response.setAlertTriggered(!alerts.isEmpty());
			return response;
		}
		catch (Exception e) {
			logger.warn("AI analysis failed for stream {}", streamId, e);
			String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return new AIVisionAnalysisResponse(false, "AI analysis failed: " + errorMessage);
		}
	}

	public AIVisionSettings updateSettings(AIVisionSettings newSettings) {
		if (newSettings == null) {
			return settings;
		}
		if (newSettings.getBaseUrl() != null) {
			settings.setBaseUrl(newSettings.getBaseUrl());
		}
		if (newSettings.getToken() != null) {
			settings.setToken(newSettings.getToken());
		}
		if (newSettings.getModel() != null && !newSettings.getModel().isBlank()) {
			settings.setModel(newSettings.getModel());
		}
		logger.info("AI Vision settings updated. baseUrl: {}, model: {}", settings.getBaseUrl(), settings.getModel());
		return settings;
	}

	public AIVisionSettings getSettings() {
		return settings;
	}

	public AIVisionImage getLatestImage(String streamId) {
		AIVisionPacketListener listener = listeners.get(streamId);
		return listener != null ? listener.getLatestImage() : null;
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
