package io.antmedia.plugin;

import static org.junit.Assert.*;

import org.junit.Test;

public class MoQSettingsTest {

    @Test
    public void testDefaults() {
        MoQSettings s = new MoQSettings();
        assertTrue(s.isUseEmbeddedRelay());
        assertTrue(s.isIngestEnabled());
        assertEquals("https://localhost:4443/moq", s.getExternalRelayUrl());
        assertEquals(2000, s.getIngestPollIntervalMs());
    }

    @Test
    public void testSettersAndGetters() {
        MoQSettings s = new MoQSettings();

        s.setUseEmbeddedRelay(false);
        s.setExternalRelayUrl("https://relay.example.com/moq");
        s.setIngestEnabled(false);
        s.setIngestPollIntervalMs(5000);

        assertFalse(s.isUseEmbeddedRelay());
        assertEquals("https://relay.example.com/moq", s.getExternalRelayUrl());
        assertFalse(s.isIngestEnabled());
        assertEquals(5000, s.getIngestPollIntervalMs());
    }

    @Test
    public void testGetRelayUrl() {
        MoQSettings s = new MoQSettings();

        s.setUseEmbeddedRelay(true);
        assertEquals("http://localhost:" + MoQPlugin.EMBEDDED_RELAY_PORT + "/moq", s.getRelayUrl());

        s.setUseEmbeddedRelay(false);
        s.setExternalRelayUrl("https://relay.example.com:9000/moq");
        assertEquals("https://relay.example.com:9000/moq", s.getRelayUrl());
    }
}
