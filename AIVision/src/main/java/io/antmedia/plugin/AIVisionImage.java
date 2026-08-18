package io.antmedia.plugin;

import java.io.File;

public class AIVisionImage {

	private final File file;
	private final String url;
	private final long timestamp;

	public AIVisionImage(File file, String url) {
		this(file, url, System.currentTimeMillis());
	}

	public AIVisionImage(File file, String url, long timestamp) {
		this.file = file;
		this.url = url;
		this.timestamp = timestamp;
	}

	public File getFile() {
		return file;
	}

	public String getUrl() {
		return url;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
