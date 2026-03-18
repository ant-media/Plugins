package io.antmedia.plugin;

public class MoQSettings {

    /** If true, the plugin manages an embedded moq-relay process. */
    private boolean useEmbeddedRelay = true;

    /** URL of the external relay. Used when useEmbeddedRelay is false. */
    private String externalRelayUrl = "https://localhost:4443/moq";

    /** Returns the relay URL to use based on current config. */
    public String getRelayUrl() {
        if (useEmbeddedRelay) {
            return "https://localhost:" + MoQPlugin.EMBEDDED_RELAY_PORT + "/moq";
        }
        return externalRelayUrl;
    }

    public boolean isUseEmbeddedRelay() { return useEmbeddedRelay; }
    public void setUseEmbeddedRelay(boolean useEmbeddedRelay) { this.useEmbeddedRelay = useEmbeddedRelay; }

    public String getExternalRelayUrl() { return externalRelayUrl; }
    public void setExternalRelayUrl(String externalRelayUrl) { this.externalRelayUrl = externalRelayUrl; }
}
