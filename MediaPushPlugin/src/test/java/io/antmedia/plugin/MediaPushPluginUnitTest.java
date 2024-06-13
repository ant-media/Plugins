package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.openqa.selenium.InvalidArgumentException;
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
		//WebDriverManager.chromedriver().setup();
	}
	
	@Test
	public void testCreateDriverWithExtraSwitches() {
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        
       
        
        try {
			RemoteWebDriver driver = plugin.createDriver(1280, 720, "streamId", Arrays.asList("--disable-gpu", "--start-fullscreen"));
			driver.quit();
			
			driver = plugin.createDriver(1280, 720, "streamId", null);
			driver.quit();
			
			driver = plugin.createDriver(1280, 720, "streamId", Arrays.asList());
			driver.quit();
			
			driver = plugin.createDriver(1280, 720, "streamId", Arrays.asList(""));
			driver.quit();
						
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}
	
	@Test
	public void testStartMediaPushWithEndpoint() throws IOException {
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        
        Endpoint endpoint = new Endpoint();
        endpoint.setUrl("http://example.com");
        endpoint.setWidth(1280);
        endpoint.setHeight(720);
        endpoint.setToken("token");
        endpoint.setExtraChromeSwitches("--disable-gpu,--start-fullscreen"); 
        
        
        plugin.startMediaPush("streamId", "ws://example.antmedia.io", endpoint);
        
        verify(plugin).createDriver(1280, 720, "streamId", Arrays.asList("--disable-gpu", "--start-fullscreen"));
        

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

        Object obj = "test";
        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        expectedResult.setDataId(obj.toString());
        when(driver.executeScript(command)).thenReturn(obj);



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
      
        String streamId = plugin.checkAndGetStreamId("");
        
        assertTrue(StringUtils.isNotBlank(streamId));
        
        streamId = plugin.checkAndGetStreamId(null);
        
        assertTrue(StringUtils.isNotBlank(streamId));
        
        String streamId2 = plugin.checkAndGetStreamId(streamId);
        
        assertEquals(streamId, streamId2);
       
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
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint.getWidth(), endpoint.getHeight(), endpoint.getUrl(), endpoint.getToken(), null);

        // Assert
        assertFalse(result.isSuccess());
        log.info("incoming result is {}", result.getMessage());

        assertTrue(result.getMessage().contains("not a valid url"));
        verify(plugin, never()).createDriver(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyList());
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
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint.getWidth(), endpoint.getHeight(), endpoint.getUrl(), endpoint.getToken(), null);

        // Assert
        assertFalse(result.isSuccess());
        log.info("incoming result is {}", result.getMessage());

        assertTrue(result.getMessage().contains("already exist"));
        verify(plugin, never()).createDriver(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyString(), Mockito.anyList());
    }

    @Test
    public void testStartMediaPush_WhenDriverCreationFails_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        String streamId = "streamId";
        String websocketUrl = "ws://test.antmedia.io";
        String url = "http://example.com";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        Mockito.doThrow(new IOException()).when(plugin).createDriver(width, height, "streamId", null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl,endpoint.getWidth(), endpoint.getHeight(), endpoint.getUrl(), endpoint.getToken(), null);

        // Assert
        assertFalse(result.isSuccess());
        verify(plugin).createDriver(width, height, "streamId", null);
    }
    
    @Test
    public void testGetPublisherUrl() throws InvalidArgumentException, URISyntaxException {
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());

        String url = plugin.getPublisherHTMLURL("ws://example.antmedia.io");
        
        log.info("returned url: {}", url);
        assertEquals("http://example.antmedia.io/media-push/media-push-publisher.html", url);
        
    }

    @Test
    public void testStartMediaPush_WhenExecuteScriptTimeoutOccurs_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        String streamId = "streamId";
        String websocketUrl = "ws://example.antmedia.io";
        String url = "http://example.com";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);

        assertFalse(plugin.getDrivers().containsKey(streamId));

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl ,endpoint.getWidth(), endpoint.getHeight(), endpoint.getUrl(), endpoint.getToken(), null);

        // Assert
        assertFalse(result.isSuccess());
        log.info("Message is: {}",result.getMessage());
       
        verify(plugin).createDriver(width, height, "streamId", null);
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