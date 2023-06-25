package io.antmedia.rest;

public class Endpoint {

    private String pythonScriptPath;

    public Endpoint() {
    }

    public Endpoint(String pythonScriptPath) {
        this.pythonScriptPath = pythonScriptPath;
    }

    public String getPythonScriptPath() {
        return pythonScriptPath;
    }

    public void setPythonScriptPath(String pythonScriptPath) {
        this.pythonScriptPath = pythonScriptPath;
    }

}