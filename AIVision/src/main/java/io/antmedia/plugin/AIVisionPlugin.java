package io.antmedia.plugin;

import java.io.File;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

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

		AIVisionImage latestImage = listener != null ? listener.getLatestImage() : null;
		if (latestImage == null) {
			return new AIVisionAnalysisResponse(false, "No decoded I-frame is available yet for stream " + streamId);
		}

		try {
			String aiResponse = aiClient.analyze(settings, prompt, latestImage.getFile());
			AIVisionAnalysisResponse response = new AIVisionAnalysisResponse(true, "AI analysis completed");
			response.setStreamId(streamId);
			response.setPrompt(prompt);
			response.setImagePath(latestImage.getFile().getAbsolutePath());
			response.setImageUrl(latestImage.getUrl());
			response.setAiResponse(aiResponse);
			return response;
		}
		catch (Exception e) {
			logger.warn("AI analysis failed for stream {}", streamId, e);
			String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return new AIVisionAnalysisResponse(false, "AI analysis failed: " + errorMessage);
		}
	}

	public AIVisionAnalysisResponse blurPeople(String streamId) {
		if (streamId == null || streamId.isBlank()) {
			return new AIVisionAnalysisResponse(false, "streamId is required");
		}

		AIVisionPacketListener listener = listeners.get(streamId);
		if (listener == null) {
			Result registerResult = register(streamId);
			if (!registerResult.isSuccess()) {
				return new AIVisionAnalysisResponse(false, registerResult.getMessage());
			}
			listener = listeners.get(streamId);
		}

		AIVisionImage latestImage = listener != null ? listener.getLatestImage() : null;
		if (latestImage == null) {
			return new AIVisionAnalysisResponse(false, "No decoded I-frame is available yet for stream " + streamId);
		}

		try {
			List<AIVisionDetectionBox> people = aiClient.detectPeople(settings, latestImage.getFile());
			BufferedImage image = ImageIO.read(latestImage.getFile());
			if (image == null) {
				return new AIVisionAnalysisResponse(false, "Could not read latest image for stream " + streamId);
			}

			for (AIVisionDetectionBox person : people) {
				blurBox(image, person);
			}

			imageDirectory.mkdirs();
			long timestamp = System.currentTimeMillis();
			String fileName = streamId + "-" + timestamp + "-people-blurred.png";
			File imageFile = new File(imageDirectory, fileName);
			ImageIO.write(image, "png", imageFile);

			AIVisionAnalysisResponse response = new AIVisionAnalysisResponse(true, "People blur completed");
			response.setStreamId(streamId);
			response.setPrompt("Detect visible people and blur full person bounding boxes");
			response.setImagePath(imageFile.getAbsolutePath());
			response.setImageUrl(imageUrlPrefix + "/" + fileName);
			response.setAiResponse("Blurred people: " + people.size());
			return response;
		}
		catch (Exception e) {
			logger.warn("People blur failed for stream {}", streamId, e);
			String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return new AIVisionAnalysisResponse(false, "People blur failed: " + errorMessage);
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

	private void blurBox(BufferedImage image, AIVisionDetectionBox box) throws IOException {
		int imageWidth = image.getWidth();
		int imageHeight = image.getHeight();
		int x = toPixels(box.getX(), imageWidth);
		int y = toPixels(box.getY(), imageHeight);
		int width = toPixels(box.getWidth(), imageWidth);
		int height = toPixels(box.getHeight(), imageHeight);

		if (width <= 0 || height <= 0) {
			return;
		}

		x = clamp(x, 0, imageWidth - 1);
		y = clamp(y, 0, imageHeight - 1);
		width = clamp(width, 1, imageWidth - x);
		height = clamp(height, 1, imageHeight - y);

		BufferedImage region = image.getSubimage(x, y, width, height);
		int scaledWidth = Math.max(1, width / 18);
		int scaledHeight = Math.max(1, height / 18);
		BufferedImage small = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D smallGraphics = small.createGraphics();
		try {
			smallGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			smallGraphics.drawImage(region, 0, 0, scaledWidth, scaledHeight, null);
		}
		finally {
			smallGraphics.dispose();
		}

		Graphics2D imageGraphics = image.createGraphics();
		try {
			imageGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			imageGraphics.drawImage(small, x, y, width, height, null);
		}
		finally {
			imageGraphics.dispose();
		}
	}

	private int toPixels(double value, int size) {
		if (value > 0 && value <= 1) {
			return (int) Math.round(value * size);
		}
		return (int) Math.round(value);
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private IAntMediaStreamHandler getApplication() {
		return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
}
