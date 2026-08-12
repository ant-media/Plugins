package io.antmedia.plugin;

import java.io.File;

public class AIVisionImage {

	private final File file;
	private final String url;

	public AIVisionImage(File file, String url) {
		this.file = file;
		this.url = url;
	}

	public File getFile() {
		return file;
	}

	public String getUrl() {
		return url;
	}
}
