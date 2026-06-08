package io.antmedia.test.clip_creator;

import io.antmedia.AppSettings;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;

import org.bson.Document;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ClipCreatorSettingsTest {

    private static final String KEY = "plugin." + ClipCreatorPlugin.PLUGIN_KEY;

    @Test
    public void testDefaultValues() {
        ClipCreatorSettings settings = new ClipCreatorSettings();
        assertEquals(600, settings.getMp4CreationIntervalSeconds());
        assertEquals(true, settings.isEnabled());
    }

    @Test
    public void testSettersAndGetters() {
        ClipCreatorSettings settings = new ClipCreatorSettings();
        settings.setMp4CreationIntervalSeconds(20);
        assertEquals(20, settings.getMp4CreationIntervalSeconds());
    }

    /**
     * Sanity check: standalone-style value (a Map) is parsed correctly today. This PASSES on the
     * current code and documents why the cluster bug is invisible in non-cluster setups: a
     * {@link LinkedHashMap}'s toString {@code {enabled=false}} is leniently parsed by Gson.
     */
    @Test
    public void testStandaloneMapValueParsesEnabledFalse() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mp4CreationIntervalSeconds", 120);
        value.put("enabled", false);

        AppSettings appSettings = new AppSettings();
        appSettings.setCustomSetting(KEY, value);

        ClipCreatorSettings parsed = new ClipCreatorPlugin().getClipCreatorSettings(appSettings);

        assertFalse("Standalone: enabled=false must be honored", parsed.isEnabled());
        assertEquals(120, parsed.getMp4CreationIntervalSeconds());
    }

    /**
     * Reproduces the cluster-mode bug. In cluster mode AppSettings is persisted via Morphia/BSON,
     * so the custom setting comes back as an {@link org.bson.Document}, whose toString
     * {@code Document{{enabled=false}}} is NOT valid JSON. {@link ClipCreatorPlugin#getClipCreatorSettings}
     * parses with {@code gson.fromJson(value.toString(), ...)}, the parse throws, and the catch block
     * falls back to {@code new ClipCreatorSettings()} whose default is {@code enabled = true}.
     *
     * This test FAILS on the current code (isEnabled() returns true instead of the configured false).
     * Fixing the parsing to convert structurally instead of via toString() makes it pass.
     */
    @Test
    public void testClusterDocumentValueHonorsEnabledFalse() {
        // Exactly what the operator configured, and exactly how Morphia hands it back in a cluster.
        Document value = new Document("mp4CreationIntervalSeconds", 120).append("enabled", false);

        AppSettings appSettings = new AppSettings();
        appSettings.setCustomSetting(KEY, value);

        ClipCreatorSettings parsed = new ClipCreatorPlugin().getClipCreatorSettings(appSettings);

        assertFalse("Cluster: enabled=false must be honored, but the plugin defaults back to enabled=true",
                parsed.isEnabled());
        assertEquals("Cluster: mp4CreationIntervalSeconds must survive parsing, but it falls back to the default",
                120, parsed.getMp4CreationIntervalSeconds());
    }


}
