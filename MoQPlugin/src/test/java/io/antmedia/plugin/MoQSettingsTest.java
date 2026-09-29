package io.antmedia.plugin;

import static org.junit.Assert.*;

import org.junit.Test;

public class MoQSettingsTest {

    /** These defaults are the contract MoQPlugin.loadSettings() falls back to when the JSON is absent or broken. */
    @Test
    public void testDefaults() {
        MoQSettings s = new MoQSettings();
        assertTrue(s.isUseEmbeddedRelay());
        assertEquals("https://localhost:4443/moq", s.getExternalRelayUrl());
        assertEquals("", s.getMoqCdnUrl());
        assertEquals(2000, s.getIngestPollIntervalMs());
    }
}
