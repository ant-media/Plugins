package io.antmedia.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antmedia.Model.ChromeExtensionInfo;
import io.antmedia.Model.Endpoint;
import io.antmedia.rest.model.Result;
import junit.framework.TestCase;
import org.junit.Test;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;

public class MediaPushPluginTest extends TestCase {

    @Test
    public void testSendCommand_WhenDriverExists_ShouldExecuteCommand() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
        JavascriptExecutor js = Mockito.mock(JavascriptExecutor.class);
        String streamId = "streamId";
        String command = "someCommand";
        Result expectedResult = new Result(true, streamId, "Command executed");

        when(plugin.getDrivers()).thenReturn(drivers);
        when(drivers.containsKey(streamId)).thenReturn(true);
        when(drivers.get(streamId)).thenReturn(driver);
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
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
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
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
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
    public void testGetExtensionFileFromResource() throws IOException {
        MediaPushPlugin plugin = new MediaPushPlugin();
        File targetFile = plugin.getExtensionFileFromResource();

        assertNotNull(targetFile);
        assertTrue(targetFile.exists());
        assertTrue(targetFile.isFile());

        targetFile.delete();
    }

    @Test
    public void testStartMediaPush_WhenStreamIdIsNull_ShouldGenerateStreamId() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn("");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(null, websocketUrl, endpoint);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
    }

    @Test
    public void testStartMediaPush_WhenStreamIdIsEmpty_ShouldGenerateStreamId() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn("");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush("", websocketUrl, endpoint);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
    }

    @Test
    public void testStartMediaPush_WhenDriverExistsForStreamId_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn("");
        when(plugin.getDrivers().containsKey(streamId)).thenReturn(true);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin, never()).createDriver(endpoint);
        verify(driver, never()).get(url);
    }

    @Test
    public void testStartMediaPush_WhenDriverCreationFails_ShouldReturnErrorResult() {
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
        when(plugin.createDriver(endpoint)).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
    }

    @Test
    public void testStartMediaPush_WhenPageLoadTimeoutOccurs_ShouldReturnErrorResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn(null, "", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(anyString())).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
        verify(driver).get(url);
        verify(driver, times(timeout)).getTitle();
        verify(driver).quit();
        verify(plugin.getDrivers()).remove(streamId);
    }

    @Test
    public void testStartMediaPush_WhenWebRTCAdaptorStateIsNull_ShouldReturnErrorResult() throws InterruptedException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn("", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);
        when(js.executeScript(anyString())).thenReturn(null);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
        verify(js, times(timeout)).executeScript(anyString());
        verify(driver, times(timeout)).getTitle();
        verify(driver).quit();
        verify(plugin.getDrivers()).remove(streamId);
    }

    @Test
    public void testStartMediaPush_WhenWebRTCAdaptorStateIsStarted_ShouldReturnSuccessResult() throws InterruptedException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn("", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);
        when(js.executeScript(anyString())).thenReturn(webRTCAdaptorState);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(expectedResult.getDataId(), result.getDataId());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
        verify(js, times(timeout)).executeScript(anyString());
        verify(driver, times(timeout)).getTitle();
    }

    @Test
    public void testStartMediaPush_WhenWebRTCAdaptorStateIsError_ShouldReturnErrorResult() throws InterruptedException {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        when(plugin.createDriver(endpoint)).thenReturn(driver);
        when(driver.getTitle()).thenReturn("", "");
        when(driver instanceof JavascriptExecutor).thenReturn(true);
        when((JavascriptExecutor) driver).thenReturn(js);
        when(js.executeScript(expectedScript)).thenReturn(null);
        when(js.executeScript(anyString())).thenReturn(webRTCAdaptorState);
        when(js.executeScript("return localStorage.getItem('webRTCAdaptorError')")).thenReturn(errorMessage);

        // Act
        Result result = plugin.startMediaPush(streamId, websocketUrl, endpoint);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals(expectedResult.getMessage(), result.getMessage());
        verify(plugin).getDrivers();
        verify(plugin).createDriver(endpoint);
        verify(driver).get(url);
        verify(js).executeScript(expectedScript);
        verify(js, times(timeout)).executeScript(anyString());
        verify(driver, times(timeout)).getTitle();
        verify(driver).quit();
        verify(plugin.getDrivers()).remove(streamId);
    }

    @Test
    public void testStopMediaPush_WhenDriverExists_ShouldStopMediaPushAndReturnSuccessResult() {
        // Arrange
        MediaPushPlugin plugin = Mockito.spy(new MediaPushPlugin());
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
        WebDriver driver = Mockito.mock(WebDriver.class);
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
        HashMap<String, WebDriver> drivers = Mockito.mock(HashMap.class);
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
    public void getChromeExtensionInfoFromResources_ShouldReturnCorrectInfo() throws IOException {
        // Arrange
        MediaPushPlugin plugin = new MediaPushPlugin();
        ChromeExtensionInfo expectedInfo = new ChromeExtensionInfo();
        expectedInfo.setId("expectedId");
        expectedInfo.setVersion("expectedVersion");

        ObjectMapper mapper = Mockito.mock(ObjectMapper.class);
        InputStream inputStream = new ByteArrayInputStream("{\"id\":\"expectedId\",\"version\":\"expectedVersion\"}".getBytes());
        ClassLoader classLoader = Mockito.mock(ClassLoader.class);

        Mockito.when(classLoader.getResourceAsStream("extension-info.json")).thenReturn(inputStream);
        Mockito.when(mapper.readValue(inputStream, ChromeExtensionInfo.class)).thenReturn(expectedInfo);
        ReflectionTestUtils.setField(plugin, "mapper", mapper);
        ReflectionTestUtils.setField(plugin, "classLoader", classLoader);

        // Act
        ChromeExtensionInfo actualInfo = plugin.getChromeExtensionInfoFromResources();

        // Assert
        assertEquals(expectedInfo.getId(), actualInfo.getId());
        assertEquals(expectedInfo.getVersion(), actualInfo.getVersion());
    }

    @Test(expected = IOException.class)
    public void getChromeExtensionInfoFromResources_WhenIOExceptionOccurs_ShouldThrowException() throws IOException {
        // Arrange
        MediaPushPlugin plugin = new MediaPushPlugin();
        ObjectMapper mapper = Mockito.mock(ObjectMapper.class);
        InputStream inputStream = Mockito.mock(InputStream.class);
        ClassLoader classLoader = Mockito.mock(ClassLoader.class);

        Mockito.when(classLoader.getResourceAsStream("extension-info.json")).thenReturn(inputStream);
        Mockito.when(mapper.readValue(inputStream, ChromeExtensionInfo.class)).thenThrow(new IOException());
        ReflectionTestUtils.setField(plugin, "mapper", mapper);
        ReflectionTestUtils.setField(plugin, "classLoader", classLoader);

        // Act
        plugin.getChromeExtensionInfoFromResources();
    }

    @Test
    public void getChromeExtensionInfoFromResources_WhenResourceNotFound_ShouldReturnNull() throws IOException {
        // Arrange
        MediaPushPlugin plugin = new MediaPushPlugin();
        ClassLoader classLoader = Mockito.mock(ClassLoader.class);

        Mockito.when(classLoader.getResourceAsStream("extension-info.json")).thenReturn(null);
        ReflectionTestUtils.setField(plugin, "classLoader", classLoader);

        // Act
        ChromeExtensionInfo actualInfo = plugin.getChromeExtensionInfoFromResources();

        // Assert
        assertNull(actualInfo);
    }

}