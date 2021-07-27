package io.antmedia.filter;

import java.util.List;

public class FilterConfiguration {
	protected String filterId;
	protected List<String> inputStreams;
	protected List<String> outputStreams;
	protected String videoFilter;
	protected String audioFilter;
	protected String type = "custom";

	public List<String> getInputStreams() {
		return inputStreams;
	}
	public void setInputStreams(List<String> inputStreams) {
		this.inputStreams = inputStreams;
	}
	public List<String> getOutputStreams() {
		return outputStreams;
	}
	public void setOutputStreams(List<String> outputStreams) {
		this.outputStreams = outputStreams;
	}
	public String getVideoFilter() {
		return videoFilter;
	}
	public void setVideoFilter(String videoFilter) {
		this.videoFilter = videoFilter;
	}
	public String getAudioFilter() {
		return audioFilter;
	}
	public void setAudioFilter(String audioFilter) {
		this.audioFilter = audioFilter;
	}
	public String getFilterId() {
		return filterId;
	}
	public void setFilterId(String filterId) {
		this.filterId = filterId;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
}
