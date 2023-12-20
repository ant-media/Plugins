package io.antmedia.Model;

public class ChromeExtensionInfo {

    private String name;

    private String id;

    private String version;

    public ChromeExtensionInfo() {
    }

    public ChromeExtensionInfo(String name, String id, String version) {
        this.name = name;
        this.id = id;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
