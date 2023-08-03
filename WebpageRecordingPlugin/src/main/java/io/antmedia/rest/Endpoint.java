package io.antmedia.rest;

public class Endpoint {

    private String URL;

    private int width;

    private int height;

    private String jsCommand;

    public Endpoint() {
    }

    public Endpoint(String URL, int width, int height, String jsCommand) {
        this.URL = URL;
        this.width = width;
        this.height = height;
        this.jsCommand = jsCommand;
    }

    public String getJsCommand() {
        return jsCommand;
    }

    public void setJsCommand(String jsCommand) {
        this.jsCommand = jsCommand;
    }

	public String getUrl() {
		return URL;
	}

	public void setUrl(String URL) {
		this.URL = URL;
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

}
