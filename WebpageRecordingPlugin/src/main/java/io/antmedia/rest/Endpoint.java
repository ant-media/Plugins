package io.antmedia.rest;

public class Endpoint {

    private String url;

    private String kalturaId;

    private int width;

    private int height;

    public Endpoint() {
    }

    public Endpoint(String url, String kalturaId, int width, int height) {
        this.url = url;
        this.kalturaId = kalturaId;
        this.width = width;
        this.height = height;
    }

    public String getKalturaId() {
        return kalturaId;
    }

    public void setKalturaId(String kalturaId) {
        this.kalturaId = kalturaId;
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

    public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
