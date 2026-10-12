package io.antmedia.plugin;

import java.util.List;

public class AIVisionPeriodicAnalysisRequest {

	private String prompt;
	private int period;
	private List<String> streamIds;
	private boolean applyToAll;

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public int getPeriod() {
		return period;
	}

	public void setPeriod(int period) {
		this.period = period;
	}

	public List<String> getStreamIds() {
		return streamIds;
	}

	public void setStreamIds(List<String> streamIds) {
		this.streamIds = streamIds;
	}

	public boolean isApplyToAll() {
		return applyToAll;
	}

	public void setApplyToAll(boolean applyToAll) {
		this.applyToAll = applyToAll;
	}
}
