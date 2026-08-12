package io.antmedia.plugin;

public class AIVisionAnalysisRequest {

	private String streamId;
	private String prompt;

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
}
