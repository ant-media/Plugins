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

public class MediaPushPluginTest extends TestCase {

    @Test
    public void testStartMediaPush() throws URISyntaxException, InterruptedException {
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.spy(new Endpoint());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        WebDriver driver = Mockito.spy(WebDriver.class);
        when(plugin.getDrivers()).thenReturn(drivers);
        Result result;

        when(drivers.containsKey(anyString())).thenReturn(true);
        result = plugin.startMediaPush("streamId", "websocketUrl", endpoint);
        assertFalse(result.isSuccess());

        when(drivers.containsKey(anyString())).thenReturn(false);
        when(plugin.createDriver(endpoint)).thenReturn(null);
        result = plugin.startMediaPush("streamId", "websocketUrl", endpoint);
        assertFalse(result.isSuccess());
        driver.quit();
    }

    @Test
    public void testStopMediaPush() throws InterruptedException {
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        when(plugin.getDrivers()).thenReturn(drivers);

        when(drivers.containsKey(anyString())).thenReturn(false);
        Result result = plugin.stopMediaPush("streamId");
        assertFalse(result.isSuccess());
    }
}
