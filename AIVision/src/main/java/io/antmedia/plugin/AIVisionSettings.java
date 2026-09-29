package io.antmedia.plugin;

public class AIVisionSettings {

	private String baseUrl = "http://localhost:11434/v1";
	private String token = "";
	private String model = "llava";

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
