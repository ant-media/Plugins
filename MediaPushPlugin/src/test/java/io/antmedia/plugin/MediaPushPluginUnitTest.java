package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.model.Endpoint;
import io.antmedia.rest.model.Result;
import io.github.bonigarcia.wdm.WebDriverManager;

public class MediaPushPluginUnitTest  {
	
	
	private static Logger log = LoggerFactory.getLogger(MediaPushPluginUnitTest.class);
	
	@Rule
	public TestRule watcher = new TestWatcher() {
		protected void starting(Description description) {
			System.out.println("Starting test: " + description.getMethodName());
		}

		protected void failed(Throwable e, Description description) {
			e.printStackTrace();
			System.out.println("Failed test: " + description.getMethodName());
		};
		protected void finished(Description description) {
			System.out.println("Finishing test: " + description.getMethodName());
		};
	};

	@BeforeClass
	public static void beforeClass() 
	{
		WebDriverManager.chromedriver().setup();
	}
	
	
	@Test
	public void testResolutions() throws IOException 
	{
		
		MediaPushPlugin pushPlugin =new MediaPushPlugin();
		
		Endpoint endpoint = new Endpoint();
		endpoint.setUrl("https://google.com");
		endpoint.setWidth(640);
		endpoint.setHeight(360);
		
		
		//ChromeDriver driver = (ChromeDriver) pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "stream1");
		
		//driver.get(endpoint.getUrl());

		 RemoteWebDriver remoteWebDriver = pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "test");
		 remoteWebDriver.get(endpoint.getUrl());
		 
		 List<Long> resolution = getResolution(endpoint, remoteWebDriver);
		 
		 System.out.println("resolution: " +resolution);
		 
		 assertEquals(640, (long)resolution.get(0));
		 assertEquals(360, (long)resolution.get(1));
		
		 //pushPlugin.startMediaPush("stream1","ws://localhost:5080/WebRTCAppEE/websocket", "http://localhost:5080/WebRTCAppEE/rest/v1/media-push/publisher.js", endpoint);
			
		 remoteWebDriver.quit();
		 
		
		 endpoint.setWidth(1280);
		 endpoint.setHeight(720);
		 
		 remoteWebDriver = pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "test");
		 remoteWebDriver.get(endpoint.getUrl());
		 
		 resolution = getResolution(endpoint, remoteWebDriver);
		 
		 assertEquals(1280, (long)resolution.get(0));
		 assertEquals(720, (long)resolution.get(1));
		 remoteWebDriver.quit();
		 
		 
		 endpoint.setWidth(1920);
		 endpoint.setHeight(1080);
		 
		 remoteWebDriver = pushPlugin.createDriver(endpoint.getWidth(), endpoint.getHeight(), "test");
		 remoteWebDriver.get(endpoint.getUrl());
		 
		 
		 
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
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(false, "Command cannot be executed.");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(driver.executeScript(command)).thenThrow(new RuntimeException("Command execution failed."));

        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
    }

    @Test
    public void testStartMediaPush_WhenStreamIdIsEmptyOrNull_ShouldGenerateStreamId() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
      
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String websocketUrl = "websocketUrl";
        String url = "http://google.com";
        int width = 1280;
        int height = 720;
        String token = "token";
        Endpoint endpoint = new Endpoint();
        endpoint.setHeight(height);
        endpoint.setWidth(width);
        endpoint.setUrl(url);
        
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        Mockito.doReturn(Mockito.mock(Result.class)).when(plugin).startBroadcastingWithBrowser(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        
        // Act
        Result result = plugin.startMediaPush("", websocketUrl, url, endpoint);

        // Assert
        ArgumentCaptor<String> streamIdCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<String> websocketUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> publisherJSUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Endpoint> requestCaptor = ArgumentCaptor.forClass(Endpoint.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

       
        // Capturing the arguments
        Mockito.verify(plugin).startBroadcastingWithBrowser(streamIdCaptor.capture(), websocketUrlCaptor.capture(), publisherJSUrlCaptor.capture(), requestCaptor.capture(), urlCaptor.capture());

        // Assertions
        
        assertTrue(StringUtils.isNotBlank(streamIdCaptor.getValue()));
        assertTrue(streamIdCaptor.getValue().length()> 12);
        var firstStreamId = streamIdCaptor.getValue();
        
        
        result = plugin.startMediaPush(null, websocketUrl, url, endpoint);
        Mockito.verify(plugin, Mockito.times(2)).startBroadcastingWithBrowser(streamIdCaptor.capture(), websocketUrlCaptor.capture(), publisherJSUrlCaptor.capture(), requestCaptor.capture(), urlCaptor.capture());
        assertTrue(StringUtils.isNotBlank(streamIdCaptor.getValue()));
        assertTrue(streamIdCaptor.getValue().length()> 12);
        assertNotEquals(firstStreamId, streamIdCaptor.getValue());
        
       
    }
    
    
    @Test
    public void testStartMediaPush_InvalidUrl_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "invalid_url";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        
        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        log.info("incoming result is {}", result.getMessage());

        assertTrue(result.getMessage().contains("not a valid url"));
        verify(plugin, never()).createDriver(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyString());
    }
    

    @Test
    public void testStartMediaPush_WhenDriverExistsForStreamId_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "http://google.com";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        
        plugin.getDrivers().put(streamId, driver);
        

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        log.info("incoming result is {}", result.getMessage());

        assertTrue(result.getMessage().contains("already exist"));
        verify(plugin, never()).createDriver(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    public void testStartMediaPush_WhenDriverCreationFails_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String url = "http://example.com";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        Mockito.doThrow(new IOException()).when(plugin).createDriver(width, height, "streamId");

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        verify(plugin).createDriver(width, height, "streamId");
    }

    @Test
    public void testStartMediaPush_WhenExecuteScriptTimeoutOccurs_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        String streamId = "streamId";
        String websocketUrl = "websocketUrl";
        String httpPublisherUrl = "not reachable";
        String url = "http://example.com";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);

        assertFalse(plugin.getDrivers().containsKey(streamId));

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, httpPublisherUrl , endpoint);

        // Assert
        assertFalse(result.isSuccess());
        
        assertTrue(result.getMessage().contains("Timeout"));
        verify(plugin).createDriver(width, height, "streamId");
        assertFalse(plugin.getDrivers().containsKey(streamId));

    }



    @Test
    public void testStopMediaPush_WhenDriverExists_ShouldStopMediaPushAndReturnSuccessResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = Mockito.mock(HashMap.class);
        RemoteWebDriver driver = Mockito.mock(RemoteWebDriver.class);
        String streamId = "streamId";
        Result expectedResult = new Result(true, "Media Push stopped");

        plugin.getDrivers().put(streamId, driver);

        // Act
        Result result = plugin.stopMediaPush(streamId);

        // Assert
        //it's false because executeScript timeout is triggered
        assertFalse(result.isSuccess());
        
        //check that it's timeout exception
        assertTrue(result.getMessage().contains("Timeoutexception"));
        
        //make sure driver quit 
        verify(driver).quit();
        
        //make sure it's removed
        assertEquals(0, plugin.getDrivers().size());
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