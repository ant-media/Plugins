package io.antmedia.plugin;

import java.io.File;

public class Mp4CreationResponse 
{

	private File file;
	private String vodId;

	public Mp4CreationResponse(File file, String vodId) {
		this.file = file;
		this.vodId = vodId;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public String getVodId() {
		return vodId;
	}

	public void setVodId(String vodId) {
		this.vodId = vodId;
	}
}
