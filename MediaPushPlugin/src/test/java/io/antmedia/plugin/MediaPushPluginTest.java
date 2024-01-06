package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.antmedia.Model.ChromeExtensionInfo;
import io.antmedia.Model.Endpoint;
import io.antmedia.rest.model.Result;
import io.github.bonigarcia.wdm.WebDriverManager;

public class MediaPushPluginTest  {

	
	
	
	@Test
	public void testCreateChrome() throws IOException 
	{
		WebDriverManager.chromedriver().setup();
		MediaPushPlugin pushPlugin =new MediaPushPlugin();
		
		Endpoint endpoint = new Endpoint();
		endpoint.setURL("https://google.com");
		endpoint.setWidth(640);
		endpoint.setHeight(360);
		
		
		//ChromeDriver driver = (ChromeDriver) pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "stream1");
		
		//driver.get(endpoint.getURL());

		 RemoteWebDriver remoteWebDriver = pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "test");
		 remoteWebDriver.get(endpoint.getURL());
		 
		 List<Long> resolution = getResolution(endpoint, remoteWebDriver);
		 
		 System.out.println("resolution: " +resolution);
		 
		 assertEquals(640, (long)resolution.get(0));
		 assertEquals(360, (long)resolution.get(1));
		
		 //pushPlugin.startMediaPush("stream1","ws://localhost:5080/WebRTCAppEE/websocket", "http://localhost:5080/WebRTCAppEE/rest/v1/media-push/publisher.js", endpoint);
			
		 remoteWebDriver.quit();
		 
		 remoteWebDriver = pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "test");
		 remoteWebDriver.get(endpoint.getURL());
		 
		 endpoint.setWidth(1280);
		 endpoint.setHeight(720);
		 
		 resolution = getResolution(endpoint, remoteWebDriver);
		 
		 assertEquals(1280, (long)resolution.get(0));
		 assertEquals(720, (long)resolution.get(1));
		 remoteWebDriver.quit();
		 
		 
		 
		 remoteWebDriver = pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "test");
		 remoteWebDriver.get(endpoint.getURL());
		 
		 endpoint.setWidth(1920);
		 endpoint.setHeight(1080);
		 
		 resolution = getResolution(endpoint, remoteWebDriver);
		 
		 assertEquals(1920, (long)resolution.get(0));
		 assertEquals(1080, (long)resolution.get(1));
		 remoteWebDriver.quit();
		 
	}

	private List<Long> getResolution(Endpoint endpoint, RemoteWebDriver remoteWebDriver) {
		return (List<Long>) remoteWebDriver.executeScript("const stream = await navigator.mediaDevices.getDisplayMedia({video: {"
		 		+ "		width:  "+ endpoint.getWidth() +","
		 		+ "		height: "+ endpoint.getHeight() + " " 
		 		+ "	}, "
		 		+ "	audio:true,"
		 		+ "	preferCurrentTab:true"
		 		+ "	});"
		 		+ "const videoTrack = stream.getVideoTracks()[0];"
		 		+ "const settings = videoTrack.getSettings();"
		 		+ "console.log(`Actual video resolution: ${settings.width}x${settings.height}`);"
		 		+ "return [settings.width, settings.height];"
		 		);
	}
	
    @Test
    public void testSendCommand_WhenDriverExists_ShouldExecuteCommand() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = Mockito.mock(HashMap.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(true, streamId, "Command executed");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(js.executeScript(command)).thenReturn(null);

        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
    }

    @Test
    public void testSendCommand_WhenDriverDoesNotExist_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = Mockito.mock(HashMap.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(false, "Driver is not exists for stream id: " + streamId);

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(false);

        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
    }

    @Test
    public void testSendCommand_WhenCommandExecutionFails_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = Mockito.mock(HashMap.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(false, "Command cannot be executed.");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(command)).thenThrow(new RuntimeException("Command execution failed."));

        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
    }


    @Test
    public void testStartMediaPush_WhenStreamIdIsNull_ShouldGenerateStreamId() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String websocketUrl = "websocketUrl";
        String url = "url";
        String generatedStreamId = "generatedStreamId";
        int width = 1280;
        int height = 720;
        String token = "token";
        String expectedScript = String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s', width: '%s', height: '%s' }, '*')", generatedStreamId, websocketUrl, width, height);
        Result expectedResult = new Result(true, generatedStreamId, "Media Push started");

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        when(plugin.createDriver(width, height, "streamId")).thenReturn(driver);
        when(driver.getTitle()).thenReturn("");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(null, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
    }

    @Test
    public void testStartMediaPush_WhenStreamIdIsEmpty_ShouldGenerateStreamId() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String websocketUrl = "websocketUrl";
        String url = "url";
        String generatedStreamId = "generatedStreamId";
        int width = 1280;
        int height = 720;
        String token = "token";
        String expectedScript = String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s', width: '%s', height: '%s' }, '*')", generatedStreamId, websocketUrl, width, height);
        Result expectedResult = new Result(true, generatedStreamId, "Media Push started");

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        when(plugin.createDriver(width, height, "streamId")).thenReturn(driver);
        when(driver.getTitle()).thenReturn("");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush("", websocketUrl, websocketUrl, endpoint);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
    }

    @Test
    public void testStartMediaPush_WhenDriverExistsForStreamId_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "url";
        int width = 1280;
        int height = 720;
        Result expectedResult = new Result(false, "Driver already exists for stream id: " + streamId);

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        when(plugin.createDriver(width, height, "streamId")).thenReturn(driver);
        when(driver.getTitle()).thenReturn("");
        when(plugin.getDrivers().containsKey(streamId)).thenReturn(true);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin, never()).createDriver(width, height, "streamId");
        verify(driver, never()).get(url);
    }

    @Test
    public void testStartMediaPush_WhenDriverCreationFails_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "url";
        int width = 1280;
        int height = 720;
        Result expectedResult = new Result(false, "Driver cannot created");

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        Mockito.doReturn(null).when(plugin).createDriver(width, height, "streamId");

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
    }

    @Test
    public void testStartMediaPush_WhenPageLoadTimeoutOccurs_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "url";
        int width = 1280;
        int height = 720;
        int timeout = 20;
        Result expectedResult = new Result(false, streamId, "Timeout while loading the page");

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        when(plugin.createDriver(width, height, "streamId")).thenReturn(driver);
        when(driver.getTitle()).thenReturn(null, "", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(Mockito.anyString())).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl , endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
        verify(driver).get(url);
        verify(driver, times(timeout)).getTitle();
        verify(driver).quit();
        verify(plugin.getDrivers()).remove(streamId);
    }

    @Test
    public void testStartMediaPush_WhenWebRTCAdaptorStateIsNull_ShouldReturnErrorResult() throws InterruptedException, IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "url";
        int width = 1280;
        int height = 720;
        int timeout = 10;
        String expectedScript = String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s', width: '%s', height: '%s' }, '*')", streamId, websocketUrl, width, height);
        Result expectedResult = new Result(false, streamId, "WebRTC Adaptor state is null or empty");

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        when(plugin.createDriver(width, height, "streamId")).thenReturn(driver);
        when(driver.getTitle()).thenReturn("", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);
        when(js.executeScript(Mockito.anyString())).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
        verify(js, times(timeout)).executeScript(Mockito.anyString());
        verify(driver, times(timeout)).getTitle();
        verify(driver).quit();
        verify(plugin.getDrivers()).remove(streamId);
    }

    @Test
    public void testStartMediaPush_WhenWebRTCAdaptorStateIsStarted_ShouldReturnSuccessResult() throws InterruptedException, IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "url";
        int width = 1280;
        int height = 720;
        int timeout = 10;
        String expectedScript = String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s', width: '%s', height: '%s' }, '*')", streamId, websocketUrl, width, height);
        String webRTCAdaptorState = "started";
        Result expectedResult = new Result(true, streamId, "Media Push started");

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        when(plugin.createDriver(width, height, "streamId")).thenReturn(driver);
        when(driver.getTitle()).thenReturn("", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);
        when(js.executeScript(Mockito.anyString())).thenReturn(webRTCAdaptorState);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
        verify(js, times(timeout)).executeScript(Mockito.anyString());
        verify(driver, times(timeout)).getTitle();
    }

    @Test
    public void testStartMediaPush_WhenWebRTCAdaptorStateIsError_ShouldReturnErrorResult() throws InterruptedException, IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "url";
        int width = 1280;
        int height = 720;
        int timeout = 10;
        String expectedScript = String.format("window.postMessage({ command:  'WR_START_BROADCASTING', streamId: '%s', websocketURL: '%s', width: '%s', height: '%s' }, '*')", streamId, websocketUrl, width, height);
        String webRTCAdaptorState = "error";
        String errorMessage = "errorMessage";
        Result expectedResult = new Result(false, streamId, "Error while starting the stream. Error: " + errorMessage);

        when(endpoint.getURL()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        Mockito.doReturn(driver).when(plugin).createDriver(width, height, "streamId");
        when(driver.getTitle()).thenReturn("", "");
        when(js.executeScript(expectedScript)).thenReturn(null);
        when(js.executeScript(Mockito.anyString())).thenReturn(webRTCAdaptorState);
        when(js.executeScript("return localStorage.getItem('webRTCAdaptorError')")).thenReturn(errorMessage);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(width, height, "streamId");
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
        verify(js, times(timeout)).executeScript(Mockito.anyString());
        verify(driver, times(timeout)).getTitle();
        verify(driver).quit();
        verify(plugin.getDrivers()).remove(streamId);
    }

    @Test
    public void testStopMediaPush_WhenDriverExists_ShouldStopMediaPushAndReturnSuccessResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = Mockito.mock(HashMap.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        Result expectedResult = new Result(true, "Media Push stopped");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);

        // Act
        Result result = plugin.stopMediaPush(streamId);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(js).executeScript(String.format("window.postMessage({ command:  'STOP', streamId: '%s' }, '*')", streamId));
        verify(driver).quit();
        verify(drivers).remove(streamId);
    }

    @Test
    public void testStopMediaPush_WhenDriverDoesNotExist_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = Mockito.mock(HashMap.class);
        String streamId = "streamId";
        Result expectedResult = new Result(false, "Driver does not exist for stream id: " + streamId, 404);

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(false);

        // Act
        Result result = plugin.stopMediaPush(streamId);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
    }

   

}