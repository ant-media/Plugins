package io.antmedia.Model;

public class Endpoint {

    private String URL;

    private int width;

    private int height;

    private String jsCommand;

    private String token;

    public Endpoint() {
    }

    public Endpoint(String URL, int width, int height, String jsCommand, String token) {
        this.URL = URL;
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

    public String getURL() {
        return URL;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
