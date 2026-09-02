package io.antmedia.plugin;

public class MoQSettings {

    /** If true, the plugin manages an embedded moq-relay process. */
    private boolean useEmbeddedRelay = true;

    /** URL of the external relay (WebTransport). Used when useEmbeddedRelay is false.
     * relay URL resolution lives in MoQPlugin#getRelayUrl() */
    private String externalRelayUrl = "https://localhost:4443/moq";

    /** When set, streams are also published to this CDN (EX: Cloudflare MoQ relay url) */
    private String moqCdnUrl = "";

    /** How often to poll the relay's /announced endpoint, in milliseconds. */
    private int ingestPollIntervalMs = 2000;

    public boolean isUseEmbeddedRelay() { return useEmbeddedRelay; }
    public void setUseEmbeddedRelay(boolean v) { this.useEmbeddedRelay = v; }

    /** relay URL resolution lives in MoQPlugin#getRelayUrl() */
    public String getExternalRelayUrl() { return externalRelayUrl; }
    public void setExternalRelayUrl(String v) { this.externalRelayUrl = v; }

    public String getMoqCdnUrl() { return moqCdnUrl; }
    public void setMoqCdnUrl(String v) { this.moqCdnUrl = v; }

    public int getIngestPollIntervalMs() { return ingestPollIntervalMs; }
    public void setIngestPollIntervalMs(int v) { this.ingestPollIntervalMs = v; }
}
