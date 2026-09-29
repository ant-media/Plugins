package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.List;

public class AIVisionAnalysisResponse {

	private boolean success;
	private boolean alertTriggered;
	private String message;
	private String streamId;
	private String prompt;
	private String imageUrl;
	private String imagePath;
	private long frameTimestamp;
	private String aiResponse;
	private List<String> alerts = new ArrayList<>();

	public AIVisionAnalysisResponse() {
	}

	public AIVisionAnalysisResponse(boolean success, String message) {
		this.success = success;
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public boolean isAlertTriggered() {
		return alertTriggered;
	}

	public void setAlertTriggered(boolean alertTriggered) {
		this.alertTriggered = alertTriggered;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getStreamId() {
		return streamId;
	}

	public void setStreamId(String streamId) {
		this.streamId = streamId;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public long getFrameTimestamp() {
		return frameTimestamp;
	}

	public void setFrameTimestamp(long frameTimestamp) {
		this.frameTimestamp = frameTimestamp;
	}

	public String getAiResponse() {
		return aiResponse;
	}

	public void setAiResponse(String aiResponse) {
		this.aiResponse = aiResponse;
	}

	public List<String> getAlerts() {
		return alerts;
	}

	public void setAlerts(List<String> alerts) {
		this.alerts = alerts != null ? alerts : new ArrayList<>();
	}
}
