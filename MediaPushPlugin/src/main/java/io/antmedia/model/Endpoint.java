package io.antmedia.model;

public class Endpoint {

    private String url;

    private int width;

    private int height;

    private String jsCommand;

    private String token;

    //it can be mp4 or webm
    private String recordType;
    
    public Endpoint() {
    }

    public Endpoint(String URL, int width, int height, String jsCommand, String token) {
        this.setUrl(URL);
        this.width = width;
        this.height = height;
        this.jsCommand = jsCommand;
        this.token = token;
    }

    public String getJsCommand() {
        return jsCommand;
    }

    public void setJsCommand(String jsCommand) {
        this.jsCommand = jsCommand;
    }

	public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getRecordType() {
		return recordType;
	}

	public void setRecordType(String recordType) {
		this.recordType = recordType;
	}
}
