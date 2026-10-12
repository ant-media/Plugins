package io.antmedia.plugin;

import java.util.List;

public class AIVisionPeriodicAnalysisStatus {

	private String requestId;
	private List<String> streamIds;
	private boolean applyToAll;
	private String prompt;
	private int period;
	private long startedAt;
	private boolean analyzing;
	private int resultCount;
	private int alertCount;
	private AIVisionAnalysisResponse lastResult;

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
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

	public long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(long startedAt) {
		this.startedAt = startedAt;
	}

	public boolean isAnalyzing() {
		return analyzing;
	}

	public void setAnalyzing(boolean analyzing) {
		this.analyzing = analyzing;
	}

	public int getResultCount() {
		return resultCount;
	}

	public void setResultCount(int resultCount) {
		this.resultCount = resultCount;
	}

	public int getAlertCount() {
		return alertCount;
	}

	public void setAlertCount(int alertCount) {
		this.alertCount = alertCount;
	}

	public AIVisionAnalysisResponse getLastResult() {
		return lastResult;
	}

	public void setLastResult(AIVisionAnalysisResponse lastResult) {
		this.lastResult = lastResult;
	}
}

