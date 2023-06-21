package io.antmedia.rest;

public class Endpoint {

    private String url;

    private String kalturaId;

    public Endpoint() {
    }

    public Endpoint(String url, String kalturaId) {
        this.url = url;
        this.kalturaId = kalturaId;
    }

    public String getKalturaId() {
        return kalturaId;
    }

    public void setKalturaId(String kalturaId) {
        this.kalturaId = kalturaId;
    }

    public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
