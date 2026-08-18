package io.antmedia.plugin;

public class AIVisionAnalysisRequest {

	private String streamId;
	private String prompt;
	private String lastImageUrl;
	private long lastFrameTimestamp;

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

	public String getLastImageUrl() {
		return lastImageUrl;
	}

	public void setLastImageUrl(String lastImageUrl) {
		this.lastImageUrl = lastImageUrl;
	}

	public long getLastFrameTimestamp() {
		return lastFrameTimestamp;
	}

	public void setLastFrameTimestamp(long lastFrameTimestamp) {
		this.lastFrameTimestamp = lastFrameTimestamp;
	}
}
