package io.antmedia.rest;

public class ResponsePair {

    public static final int SUCCESS_CODE = 200;
    public static final int INTERNAL_SERVER_ERROR_CODE = 500;
    private int responseCode;
    private String response;

    public ResponsePair() {
    }

    public ResponsePair(int responseCode, String response) {
        this.responseCode = responseCode;
        this.response = response;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
