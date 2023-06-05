package io.antmedia.rest;

public class Endpoint {

    private String url;

    public Endpoint() {
    }

    public Endpoint(String url) {
        this.url = url;
    }

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
