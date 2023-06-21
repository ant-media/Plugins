package io.antmedia.plugin;

import io.antmedia.rest.Endpoint;
import io.antmedia.rest.model.Result;
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
        Endpoint endpoint = Mockito.spy(new Endpoint());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        WebDriver driver = Mockito.spy(WebDriver.class);
        when(plugin.getDrivers()).thenReturn(drivers);
        Result result;

        when(drivers.containsKey(anyString())).thenReturn(true);
        result = plugin.startWebpageRecording("streamId", "websocketUrl", endpoint);
        assertFalse(result.isSuccess());

        when(drivers.containsKey(anyString())).thenReturn(false);
        when(plugin.createDriver()).thenReturn(null);
        result = plugin.startWebpageRecording("streamId", "websocketUrl", endpoint);
        assertFalse(result.isSuccess());
        driver.quit();
    }

    @Test
    public void testStopWebpageRecording() throws InterruptedException {
        WebpageRecordingPlugin plugin = Mockito.spy(new WebpageRecordingPlugin());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        when(plugin.getDrivers()).thenReturn(drivers);

        when(drivers.containsKey(anyString())).thenReturn(false);
        Result result = plugin.stopWebpageRecording("streamId");
        assertFalse(result.isSuccess());
    }
}