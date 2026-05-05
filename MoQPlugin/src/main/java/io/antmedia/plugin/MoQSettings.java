package io.antmedia.plugin;

public class MoQSettings {

    /** If true, the plugin manages an embedded moq-relay process. */
    private boolean useEmbeddedRelay = true;

    /** URL of the external relay (WebTransport). Used when useEmbeddedRelay is false. */
    private String externalRelayUrl = "https://localhost:4443/moq";

    /** If true, the plugin polls the relay for external publishers and ingests them into AMS. */
    private boolean ingestEnabled = true;

    /** How often to poll the relay's /announced endpoint, in milliseconds. */
    private int ingestPollIntervalMs = 2000;

    /** WebTransport URL used by moq-cli to connect to the relay. */
    public String getRelayUrl() {
        if (useEmbeddedRelay) {
            return "http://localhost:" + MoQPlugin.EMBEDDED_RELAY_PORT + "/moq";
        }
        return externalRelayUrl;
    }

    // Getters / setters

    public boolean isUseEmbeddedRelay() { return useEmbeddedRelay; }
    public void setUseEmbeddedRelay(boolean v) { this.useEmbeddedRelay = v; }

    public String getExternalRelayUrl() { return externalRelayUrl; }
    public void setExternalRelayUrl(String v) { this.externalRelayUrl = v; }

    public boolean isIngestEnabled() { return ingestEnabled; }
    public void setIngestEnabled(boolean v) { this.ingestEnabled = v; }

    public int getIngestPollIntervalMs() { return ingestPollIntervalMs; }
    public void setIngestPollIntervalMs(int v) { this.ingestPollIntervalMs = v; }
}
