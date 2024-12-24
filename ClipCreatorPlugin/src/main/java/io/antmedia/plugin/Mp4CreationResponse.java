package io.antmedia.plugin;

import java.io.File;

public class Mp4CreationResponse 
{

	private File file;
	private String vodId;
	private boolean success = false;
	private String message;

	public Mp4CreationResponse() {
	}
	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}
	
	

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

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message the message to set
	 */
	public void setMessage(String message) {
		this.message = message;
	}
	
	
	
	
}
