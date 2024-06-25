package io.antmedia.test;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.antmedia.filter.utils.FilterConfiguration;

public class FilterConfigurationUnitTest {

    @Test
    public void testGetSetInputStreams() {
        List<String> inputStreams = new ArrayList<>(Arrays.asList("stream1", "stream2"));
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setInputStreams(inputStreams);
        assertEquals(inputStreams, filterConfig.getInputStreams());
    }

    @Test
    public void testGetSetOutputStreams() {
        List<String> outputStreams = new ArrayList<>(Arrays.asList("stream3", "stream4"));
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setOutputStreams(outputStreams);
        assertEquals(outputStreams, filterConfig.getOutputStreams());
    }

    @Test
    public void testGetSetVideoFilter() {
        String videoFilter = "filter";
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setVideoFilter(videoFilter);
        assertEquals(videoFilter, filterConfig.getVideoFilter());
    }

    @Test
    public void testGetSetAudioFilter() {
        String audioFilter = "filter";
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setAudioFilter(audioFilter);
        assertEquals(audioFilter, filterConfig.getAudioFilter());
    }

    @Test
    public void testGetSetFilterId() {
        String filterId = "filterId";
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setFilterId(filterId);
        assertEquals(filterId, filterConfig.getFilterId());
    }

    @Test
    public void testGetSetType() {
        String type = "asynchronous";
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setType(type);
        assertEquals(type, filterConfig.getType());
    }

    @Test
    public void testIsSetVideoEnabled() {
        boolean videoEnabled = true;
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setVideoEnabled(videoEnabled);
        assertEquals(videoEnabled, filterConfig.isVideoEnabled());
    }

    @Test
    public void testIsSetAudioEnabled() {
        boolean audioEnabled = true;
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setAudioEnabled(audioEnabled);
        assertEquals(audioEnabled, filterConfig.isAudioEnabled());
    }

    @Test
    public void testGetSetVideoOutputHeight() {
        int videoOutputHeight = 720;
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setVideoOutputHeight(videoOutputHeight);
        assertEquals(videoOutputHeight, filterConfig.getVideoOutputHeight());
    }

    @Test
    public void testGetSetVideoOutputBitrate() {
        int videoOutputBitrate = 2000;
        FilterConfiguration filterConfig = new FilterConfiguration();
        filterConfig.setVideoOutputBitrate(videoOutputBitrate);
        assertEquals(videoOutputBitrate, filterConfig.getVideoOutputBitrate());
    }
}
