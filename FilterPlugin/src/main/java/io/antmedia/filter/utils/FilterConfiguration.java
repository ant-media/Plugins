package io.antmedia.filter.utils;

import java.util.List;

public class FilterConfiguration {
	public static final String ASYNCHRONOUS = "asynchronous";
	public static final String SYNCHRONOUS = "synchronous";
	public static final String LASTPOINT = "lastpoint";

	/**
	 * The id of the filter
	 */
	private String filterId;
	
	/**
	 * Stream id list of the input streams. 
	 * These stream ids are in the server side
	 */
	private List<String> inputStreams;
	
	/**
	 * Stream id list of the output.
	 * 
	 */
	private List<String> outputStreams;
	
	/**
	 * FFmpeg video filter text 
	 */
	private String videoFilter;
	
	/**
	 * FFmpeg audio filter text
	 */
	private String audioFilter;
	
	/**
	 * Type of the plugin
	 */
	private String type = ASYNCHRONOUS;
	
	/**
	 * Video enabled flag
	 */
	private boolean videoEnabled;
	
	/**
	 * Audio enabled flag
	 */
	private boolean audioEnabled;
	
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
	
	public boolean isVideoEnabled() {
		return videoEnabled;
	}
	
	public void setVideoEnabled(boolean videoEnabled) {
		this.videoEnabled = videoEnabled;
	}
	
	public boolean isAudioEnabled() {
		return audioEnabled;
	}
	
	public void setAudioEnabled(boolean audioEnabled) {
		this.audioEnabled = audioEnabled;
	}
}
