package io.antmedia.test.clip_creator;

import io.antmedia.plugin.ClipCreatorSettings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClipCreatorSettingsTest {

    @Test
    public void testDefaultValues() {
        ClipCreatorSettings settings = new ClipCreatorSettings();
        assertEquals(600, settings.getMp4CreationIntervalSeconds());
    }

    @Test
    public void testSettersAndGetters() {
        ClipCreatorSettings settings = new ClipCreatorSettings();
        settings.setMp4CreationIntervalSeconds(20);
        assertEquals(20, settings.getMp4CreationIntervalSeconds());
    }


}
