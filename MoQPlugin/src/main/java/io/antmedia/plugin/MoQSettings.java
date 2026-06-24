package io.antmedia.plugin;

public class MoQSettings {

    /** If true, the plugin manages an embedded moq-relay process. */
    private boolean useEmbeddedRelay = true;

    /** URL of the external relay (WebTransport). Used when useEmbeddedRelay is false.
     * relay URL resolution lives in MoQPlugin#getRelayUrl() */
    private String externalRelayUrl = "https://localhost:4443/moq";

    /** If true, the plugin polls the relay for external publishers and ingests them into AMS. */
    private boolean ingestEnabled = true;

    /** How often to poll the relay's /announced endpoint, in milliseconds. */
    private int ingestPollIntervalMs = 2000;

    public boolean isUseEmbeddedRelay() { return useEmbeddedRelay; }
    public void setUseEmbeddedRelay(boolean v) { this.useEmbeddedRelay = v; }

    /** relay URL resolution lives in MoQPlugin#getRelayUrl() */
    public String getExternalRelayUrl() { return externalRelayUrl; }
    public void setExternalRelayUrl(String v) { this.externalRelayUrl = v; }

    public boolean isIngestEnabled() { return ingestEnabled; }
    public void setIngestEnabled(boolean v) { this.ingestEnabled = v; }

    public int getIngestPollIntervalMs() { return ingestPollIntervalMs; }
    public void setIngestPollIntervalMs(int v) { this.ingestPollIntervalMs = v; }
}
