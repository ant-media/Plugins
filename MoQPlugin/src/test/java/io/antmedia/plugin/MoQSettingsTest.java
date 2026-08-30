package io.antmedia.plugin;

import static org.junit.Assert.*;

import org.junit.Test;

public class MoQSettingsTest {

    @Test
    public void testDefaults() {
        MoQSettings s = new MoQSettings();
        assertTrue(s.isUseEmbeddedRelay());
        assertEquals("https://localhost:4443/moq", s.getExternalRelayUrl());
        assertEquals("", s.getMoqCdnUrl());
        assertEquals(2000, s.getIngestPollIntervalMs());
    }

    @Test
    public void testSettersAndGetters() {
        MoQSettings s = new MoQSettings();

        s.setUseEmbeddedRelay(false);
        s.setExternalRelayUrl("https://relay.example.com/moq");
        s.setMoqCdnUrl("https://cdn.example.com/token");
        s.setIngestPollIntervalMs(5000);

        assertFalse(s.isUseEmbeddedRelay());
        assertEquals("https://relay.example.com/moq", s.getExternalRelayUrl());
        assertEquals("https://cdn.example.com/token", s.getMoqCdnUrl());
        assertEquals(5000, s.getIngestPollIntervalMs());
    }

}
