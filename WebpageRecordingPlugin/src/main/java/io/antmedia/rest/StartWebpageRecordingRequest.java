package io.antmedia.rest;

public class StartWebpageRecordingRequest {
    private String streamId;

    private String webpageUrl;

    public StartWebpageRecordingRequest() {
    }

    public StartWebpageRecordingRequest(String streamId, String webpageUrl) {
        this.streamId = streamId;
        this.webpageUrl = webpageUrl;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getWebpageUrl() {
        return webpageUrl;
    }

    public void setWebpageUrl(String webpageUrl) {
        this.webpageUrl = webpageUrl;
    }
}
