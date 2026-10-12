package io.antmedia.plugin;

public class AIVisionSettings {

	private String baseUrl = "http://localhost:11434/v1";
	private String token = "";
	private String model = "llava";
	private String webhookUrl = "";
	private int maxResultsPerAnalysis = 100;

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = trimTrailingSlash(baseUrl);
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token != null ? token : "";
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getWebhookUrl() {
		return webhookUrl;
	}

	public void setWebhookUrl(String webhookUrl) {
		this.webhookUrl = webhookUrl != null ? webhookUrl.trim() : "";
	}

	public int getMaxResultsPerAnalysis() {
		return maxResultsPerAnalysis;
	}

	public void setMaxResultsPerAnalysis(int maxResultsPerAnalysis) {
		this.maxResultsPerAnalysis = Math.max(1, Math.min(maxResultsPerAnalysis, 1000));
	}

	private String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "http://localhost:11434/v1";
		}
		String trimmed = value.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		if (!trimmed.endsWith("/v1")) {
			trimmed = trimmed + "/v1";
		}
		return trimmed;
	}
}
