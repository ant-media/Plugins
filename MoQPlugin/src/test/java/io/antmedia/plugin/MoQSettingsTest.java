package io.antmedia.plugin;

import static org.junit.Assert.*;

import org.junit.Test;

public class MoQSettingsTest {

    @Test
    public void testDefaultValues() {
        MoQSettings settings = new MoQSettings();
        assertTrue(settings.isUseEmbeddedRelay());
        assertTrue(settings.isIngestEnabled());
        assertEquals("https://localhost:4443/moq", settings.getExternalRelayUrl());
        assertEquals(2000, settings.getIngestPollIntervalMs());
    }

    @Test
    public void testSettersGetters() {
        MoQSettings settings = new MoQSettings();

        settings.setUseEmbeddedRelay(false);
        assertFalse(settings.isUseEmbeddedRelay());

        settings.setExternalRelayUrl("https://relay.example.com/moq");
        assertEquals("https://relay.example.com/moq", settings.getExternalRelayUrl());

        settings.setIngestEnabled(false);
        assertFalse(settings.isIngestEnabled());

        settings.setIngestPollIntervalMs(5000);
        assertEquals(5000, settings.getIngestPollIntervalMs());
    }

    @Test
    public void testGetRelayUrl_embeddedRelay() {
        MoQSettings settings = new MoQSettings();
        settings.setUseEmbeddedRelay(true);
        assertEquals("http://localhost:" + MoQPlugin.EMBEDDED_RELAY_PORT + "/moq", settings.getRelayUrl());
    }

    @Test
    public void testGetRelayUrl_externalRelay() {
        MoQSettings settings = new MoQSettings();
        settings.setUseEmbeddedRelay(false);
        settings.setExternalRelayUrl("https://relay.example.com:9000/moq");
        assertEquals("https://relay.example.com:9000/moq", settings.getRelayUrl());
    }

    @Test
    public void testGetAnnounceBaseUrl_withMoqSuffix() {
        MoQSettings settings = new MoQSettings();
        settings.setUseEmbeddedRelay(true);
        // embedded relay URL is "http://localhost:4443/moq"
        assertEquals("http://localhost:4443", settings.getAnnounceBaseUrl());
    }

    @Test
    public void testGetAnnounceBaseUrl_withoutMoqSuffix() {
        MoQSettings settings = new MoQSettings();
        settings.setUseEmbeddedRelay(false);
        settings.setExternalRelayUrl("https://relay.example.com:9000/something");
        // does not end with "/moq", so returned unchanged
        assertEquals("https://relay.example.com:9000/something", settings.getAnnounceBaseUrl());
    }

    @Test
    public void testGetAnnounceBaseUrl_externalRelay() {
        MoQSettings settings = new MoQSettings();
        settings.setUseEmbeddedRelay(false);
        settings.setExternalRelayUrl("https://relay.example.com:9000/moq");
        assertEquals("https://relay.example.com:9000", settings.getAnnounceBaseUrl());
    }
}
