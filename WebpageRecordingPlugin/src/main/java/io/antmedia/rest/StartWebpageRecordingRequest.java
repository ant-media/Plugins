package io.antmedia.rest;

public class StartWebpageRecordingRequest {
    private String streamId;

    private String websocketUrl;

    private String webpageUrl;

    public StartWebpageRecordingRequest() {
    }

    public StartWebpageRecordingRequest(String streamId, String websocketUrl, String webpageUrl) {
        this.streamId = streamId;
        this.websocketUrl = websocketUrl;
        this.webpageUrl = webpageUrl;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }

    public String getWebpageUrl() {
        return webpageUrl;
    }

    public void setWebpageUrl(String webpageUrl) {
        this.webpageUrl = webpageUrl;
    }
}
