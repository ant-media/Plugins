package io.antmedia.plugin;

import io.antmedia.rest.ResponsePair;
import junit.framework.TestCase;
import org.junit.Test;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;

import java.net.URISyntaxException;
import java.util.HashMap;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class WebpageRecordingPluginTest extends TestCase {

    @Test
    public void testStartWebpageRecording() throws URISyntaxException, InterruptedException {
        WebpageRecordingPlugin plugin = Mockito.spy(new WebpageRecordingPlugin());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        WebDriver driver = Mockito.spy(WebDriver.class);
        when(plugin.getDrivers()).thenReturn(drivers);
        ResponsePair responsePair;

        when(drivers.containsKey(anyString())).thenReturn(true);
        responsePair = plugin.startWebpageRecording("streamId", "websocketUrl", "url");
        assertEquals(500, responsePair.getResponseCode());

        when(drivers.containsKey(anyString())).thenReturn(false);
        when(plugin.createDriver()).thenReturn(null);
        responsePair = plugin.startWebpageRecording("streamId", "websocketUrl", "url");
        assertEquals(500, responsePair.getResponseCode());
        driver.quit();
    }

    @Test
    public void testStopWebpageRecording() throws InterruptedException {
        WebpageRecordingPlugin plugin = Mockito.spy(new WebpageRecordingPlugin());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        when(plugin.getDrivers()).thenReturn(drivers);
        ResponsePair responsePair;

        when(drivers.containsKey(anyString())).thenReturn(false);
        responsePair = plugin.stopWebpageRecording("streamId");
        assertEquals(500, responsePair.getResponseCode());
    }
}