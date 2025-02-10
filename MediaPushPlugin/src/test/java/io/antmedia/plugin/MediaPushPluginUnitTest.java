package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.JWTFilter;
import io.antmedia.filter.TokenFilterManager;
import io.antmedia.settings.ServerSettings;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.model.Endpoint;
import io.antmedia.rest.model.Result;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.context.ApplicationContext;

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
	public void testCreateDriverWithExtraSwitches() {
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        
       
        
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
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        
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
    public void testStartMediaPushWithWithIP() throws IOException, InterruptedException {
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        String myIp = "192.168.0.1";
        Endpoint endpoint = new Endpoint();
        endpoint.setUrl("http://example.com");
        endpoint.setWidth(1280);
        endpoint.setHeight(720);
        endpoint.setToken("token");
        endpoint.setExtraChromeSwitches("--disable-gpu,--start-fullscreen");


        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        doReturn(driver).when(plugin).openDriver(endpoint.getWidth(),endpoint.getHeight(),endpoint.getRecordType(),Arrays.asList("--disable-gpu", "--start-fullscreen"),"streamId","http://example.antmedia.io/media-push/media-push-publisher.html",endpoint.getUrl());
        WebDriverWait wait = mock(WebDriverWait.class);
        when(wait.until(any(Function.class))).thenReturn(true);
        doReturn(wait).when(plugin).createWebDriverWait(any(WebDriver.class),anyInt());

        AntMediaApplicationAdapter adapter = mock(AntMediaApplicationAdapter.class);
        doReturn(adapter).when(plugin).getApplication();
        ServerSettings settings = mock(ServerSettings.class);
        doReturn(settings).when(adapter).getServerSettings();
        doReturn(myIp).when(settings).getHostAddress();

        plugin.startMediaPush("streamId", "ws://example.antmedia.io", endpoint);
        verify(driver).executeScript("window.startBroadcasting({websocketURL:'ws://example.antmedia.io',streamId:'streamId',width:1280,height:720,token:'token',driverIp:'{\"driverIp\":\"192.168.0.1\"}'});");
    }

    @Test
    public void testSendCommand_WhenDriverExists_ShouldExecuteCommand() {
        // Arrange
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = mock(HashMap.class);
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        
        when(driver.switchTo()).thenReturn(mock(TargetLocator.class));
        
        
        JavascriptExecutor js = mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(true, streamId, "");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(js.executeScript(command)).thenReturn(null);
        doNothing().when(plugin).waitToBeFrameAvailable(Mockito.any());

        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
    }

    @Test
    public void testSendCommand_WhenDriverDoesNotExist_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = mock(HashMap.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(false, "Driver does not exist for stream id: " + streamId);

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(false);
        doNothing().when(plugin).waitToBeFrameAvailable(Mockito.any());


        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
    }
    
    @Test
	public void testSendCommand() throws IOException {
		MediaPushPlugin plugin = new MediaPushPlugin();

		File file = new File("src/test/resources/content.html");
		File mediaPushUrl = new File("src/main/resources/media-push-publisher.html");
		
		RemoteWebDriver driver = plugin.openDriver(640, 360, null, null, "streamId", "file://" + mediaPushUrl.getAbsolutePath(),  "file://" + file.getAbsolutePath());
		
		
		Result result = plugin.sendCommand("streamId", "return window.hello");
		
		
		assertEquals("Run Ant Media Run", result.getMessage());
		
		assertTrue(result.isSuccess());
		
		driver.quit();

	}

    @Test
    public void testSendCommand_WhenCommandExecutionFails_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = mock(HashMap.class);
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(false, "Command cannot be executed.");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(driver.executeScript(command)).thenThrow(new RuntimeException("Command execution failed."));
        doNothing().when(plugin).waitToBeFrameAvailable(Mockito.any());

        when(driver.switchTo()).thenReturn(mock(TargetLocator.class));

        // Act
        Result result = plugin.sendCommand(streamId, command);

        // Assert
        assertFalse(result.isSuccess());
    }
    
    @Test
    public void testStartMediaPush_WhenStreamIdIsEmptyOrNull_ShouldGenerateStreamId() throws IOException {
        // Arrange
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
      
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
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        Endpoint endpoint = mock(Endpoint.class);
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
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
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        Endpoint endpoint = mock(Endpoint.class);
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
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
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        Endpoint endpoint = mock(Endpoint.class);
        String streamId = "streamId";
        String websocketUrl = "ws://test.antmedia.io";
        String url = "http://example.com";
        int width = 1280;
        int height = 720;

        when(endpoint.getUrl()).thenReturn(url);
        when(endpoint.getWidth()).thenReturn(width);
        when(endpoint.getHeight()).thenReturn(height);
        when(plugin.getDrivers()).thenReturn(new HashMap<>());
        doThrow(new IOException()).when(plugin).createDriver(width, height, "streamId", null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl,endpoint.getWidth(), endpoint.getHeight(), endpoint.getUrl(), endpoint.getToken(), null);

        // Assert
        assertFalse(result.isSuccess());
        verify(plugin).createDriver(width, height, "streamId", null);
    }
    
    @Test
    public void testGetPublisherUrl() throws InvalidArgumentException, URISyntaxException {
        MediaPushPlugin plugin = spy(new MediaPushPlugin());

        String url = plugin.getPublisherHTMLURL("ws://example.antmedia.io");
        
        log.info("returned url: {}", url);
        assertEquals("http://example.antmedia.io/media-push/media-push-publisher.html", url);
        
    }
    
    

    @Test
    public void testStartMediaPush_WhenExecuteScriptTimeoutOccurs_ShouldReturnErrorResult() throws IOException {
        // Arrange
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        Endpoint endpoint = mock(Endpoint.class);
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
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = mock(HashMap.class);
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
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
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        HashMap<String, RemoteWebDriver> drivers = mock(HashMap.class);
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

    @Test
    public void testValidIP(){
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        assertFalse(plugin.isValidIP("test.abc.fd"));
        assertFalse(plugin.isValidIP(""));
        assertFalse(plugin.isValidIP(null));
        assertTrue(plugin.isValidIP("127.0.0.1"));
        assertFalse(plugin.isValidIP("192.168.0."));
        assertFalse(plugin.isValidIP("192..0.1"));
    }
    @Test
    public void testStopRequestForward() throws IOException, URISyntaxException {
        String streamId = "stream1";
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        doReturn(null).when(plugin).getBroadcast(anyString());
        Result result = plugin.stopMediaPush(streamId);
        assertEquals(result.getMessage(),"Driver does not exist for stream id: "+streamId);

        Broadcast broadcast = mock(Broadcast.class);
        doReturn(broadcast).when(plugin).getBroadcast(anyString());
        doReturn(null).when(broadcast).getMetaData();
        plugin.stopMediaPush(streamId);
        assertEquals(result.getMessage(),"Driver does not exist for stream id: "+streamId);

        doReturn("null").when(broadcast).getMetaData();
        plugin.stopMediaPush(streamId);
        assertEquals(result.getMessage(),"Driver does not exist for stream id: "+streamId);

        String metaData = "{\"driverIp\":\"54.36..133\"}"; // test isvalid ip
        doReturn(metaData).when(broadcast).getMetaData();
        verify(plugin,times(0)).forwardMediaPushStopRequest(anyString(),anyString());
        plugin.stopMediaPush(streamId);

        result.setMessage("Driver does not exist for stream id: " + streamId);

        metaData = "{\"driverIp\":\"54.36.24.133\"}";
        doReturn(metaData).when(broadcast).getMetaData();
        doReturn(false).when(plugin).forwardMediaPushStopRequest(anyString(),anyString());
        plugin.stopMediaPush(streamId);

        result.setMessage("Driver does not exist for stream id: " + streamId);

        metaData = "{\"driverIp\":\"54.36.24.133\"}";
        doReturn(metaData).when(broadcast).getMetaData();
        plugin.stopMediaPush(streamId);
        doReturn(true).when(plugin).forwardMediaPushStopRequest(anyString(),anyString());
        verify(plugin,times(2)).forwardMediaPushStopRequest(streamId,"54.36.24.133");
        System.out.println(result.getMessage());

        plugin = spy(new MediaPushPlugin());

        CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);
        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        MockedStatic<HttpClients> httpClientStaticMock = mockStatic(HttpClients.class);
        when(HttpClients.custom()).thenReturn(builder);
        doReturn(builder).when(builder).setRedirectStrategy((any(LaxRedirectStrategy.class)));
        doReturn(mockHttpClient).when(builder).build();

        AntMediaApplicationAdapter adapter = mock(AntMediaApplicationAdapter.class);
        doReturn(adapter).when(plugin).getApplication();
        AppSettings appSettings = mock(AppSettings.class);
        doReturn("test").when(appSettings).getClusterCommunicationKey();
        doReturn(appSettings).when(adapter).getAppSettings();
        ServerSettings settings = mock(ServerSettings.class);
        doReturn(settings).when(adapter).getServerSettings();

        doReturn(5080).when(settings).getDefaultHttpPort();

        MockedStatic<JWTFilter> jwtStaticMock = mockStatic(JWTFilter.class);
        when(JWTFilter.generateJwtToken(anyString(),anyLong())).thenReturn("test");

        ApplicationContext ctx = mock(ApplicationContext.class);
        plugin.applicationContext = ctx;
        doReturn("/LiveApp").when(ctx).getApplicationName();

        URI url = new URI("http://"+ "127.0.0.1" + ":"+ "5080"  + "/LiveApp" + "/rest/v1/media-push/stop/"+ streamId);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        doReturn(statusLine).when(response).getStatusLine();
        doReturn(200).when(statusLine).getStatusCode();
        doReturn(response).when(mockHttpClient).execute(any());

        plugin.forwardMediaPushStopRequest(streamId,"127.0.0.1");

        ArgumentCaptor<HttpUriRequest> requestCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(mockHttpClient).execute(requestCaptor.capture());
        HttpUriRequest capturedRequest = requestCaptor.getValue();
        assertEquals("POST", capturedRequest.getMethod());
        assertEquals(capturedRequest.getURI(),url);
        assertEquals(capturedRequest.getURI(),url);
        Header[] header = capturedRequest.getHeaders(TokenFilterManager.TOKEN_HEADER_FOR_NODE_COMMUNICATION);
        assertEquals("test", header[0].getValue());

        jwtStaticMock.close();
        httpClientStaticMock.close();
    }
    @Test
    public void getBroadcastTest() throws URISyntaxException, IOException {
        MediaPushPlugin plugin = spy(new MediaPushPlugin());
        String streamId = "stream1";

        CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);
        HttpClientBuilder builder = mock(HttpClientBuilder.class);
        MockedStatic<HttpClients> httpClientStaticMock = mockStatic(HttpClients.class);
        when(HttpClients.custom()).thenReturn(builder);
        doReturn(builder).when(builder).setRedirectStrategy((any(LaxRedirectStrategy.class)));
        doReturn(mockHttpClient).when(builder).build();

        AntMediaApplicationAdapter adapter = mock(AntMediaApplicationAdapter.class);
        doReturn(adapter).when(plugin).getApplication();
        AppSettings appSettings = mock(AppSettings.class);
        doReturn("test").when(appSettings).getClusterCommunicationKey();
        doReturn(appSettings).when(adapter).getAppSettings();
        ServerSettings settings = mock(ServerSettings.class);
        doReturn(settings).when(adapter).getServerSettings();

        doReturn(5080).when(settings).getDefaultHttpPort();

        ApplicationContext ctx = mock(ApplicationContext.class);
        plugin.applicationContext = ctx;
        doReturn("/LiveApp").when(ctx).getApplicationName();

        URI url = new URI("http://"+ "127.0.0.1" + ":"+ "5080"  + "/LiveApp" + "/rest/v2/broadcasts/"+ streamId);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        String broadcast = "{\"streamId\":\"stream1\",\"status\":\"broadcasting\"}";
        InputStream stream = new ByteArrayInputStream(broadcast.getBytes());
        doReturn(stream).when(entity).getContent();
        doReturn(entity).when(response).getEntity();
        StatusLine statusLine = mock(StatusLine.class);
        doReturn(statusLine).when(response).getStatusLine();
        doReturn(200).when(statusLine).getStatusCode();
        doReturn(response).when(mockHttpClient).execute(any());

        Broadcast broadcast1 = plugin.getBroadcast(streamId);
        assertEquals(streamId, broadcast1.getStreamId());

        ArgumentCaptor<HttpUriRequest> requestCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(mockHttpClient).execute(requestCaptor.capture());
        HttpUriRequest capturedRequest = requestCaptor.getValue();
        assertEquals("GET", capturedRequest.getMethod());
        assertEquals(capturedRequest.getURI(),url);
        httpClientStaticMock.close();
    }


}