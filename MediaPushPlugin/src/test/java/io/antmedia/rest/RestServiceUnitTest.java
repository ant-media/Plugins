package io.antmedia.rest;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.model.Endpoint;
import io.antmedia.plugin.MediaPushPlugin;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import jakarta.ws.rs.core.UriInfo;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.URISyntaxException;

public class RestServiceUnitTest {

    private MediaPushRestService restService;
    private HttpServletRequest httpRequest;
    private UriInfo uriInfo;
    private URI uri;
    private ServletContext servletContext;
    private ApplicationContext applicationContext;
    private AntMediaApplicationAdapter antMediaApplicationAdapter;

    @Before
    public void setUp() {
        restService = spy(new MediaPushRestService());
        httpRequest = mock(HttpServletRequest.class);
        uriInfo = mock(UriInfo.class);
        uri = mock(URI.class);
        servletContext = mock(ServletContext.class);
        applicationContext = mock(ApplicationContext.class);
        antMediaApplicationAdapter = mock(AntMediaApplicationAdapter.class);

        when(servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE)).thenReturn(applicationContext);
        when(applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(antMediaApplicationAdapter);
        when(antMediaApplicationAdapter.getName()).thenReturn("applicationName");

        restService.servletContext = servletContext;
    }

    @Test
    public void shouldReturnCorrectWebsocketUrlWhenHeadersArePresent() {
        when(httpRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpRequest.getHeader("X-Forwarded-Host")).thenReturn("example.com");
        when(httpRequest.getHeader("X-Forwarded-Port")).thenReturn("8080");
        when(uri.getPath()).thenReturn("example.com:8080/applicationName");
        when(uriInfo.getBaseUri()).thenReturn(uri); // Mocking to return null as it's not used in this scenario

        String websocketUrl = restService.getWebSocketURL(httpRequest, uriInfo);
        assertEquals("wss://example.com:8080/applicationName/websocket", websocketUrl);
    }

    @Test
    public void shouldFallbackToUriInfoWhenHeadersAreMissing() throws URISyntaxException {
        when(httpRequest.getHeader("X-Forwarded-Proto")).thenReturn(null);
        when(httpRequest.getHeader("X-Forwarded-Host")).thenReturn(null);
        when(httpRequest.getHeader("X-Forwarded-Port")).thenReturn(null);
        when(uri.getPath()).thenReturn("fallback.com:8080/applicationName");
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://fallback.com:8080/applicationName"));

        String websocketUrl = restService.getWebSocketURL(httpRequest, uriInfo);
        assertEquals("ws://fallback.com:8080/applicationName/websocket", websocketUrl);
    }

    @Test
    public void shouldHandleMissingPortGracefully() {
        when(httpRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpRequest.getHeader("X-Forwarded-Host")).thenReturn("example.com");
        when(uri.getPath()).thenReturn("example.com/applicationName");
        // No port header added
        when(uriInfo.getBaseUri()).thenReturn(null); // Mocking to return null as it's not used in this scenario

        String websocketUrl = restService.getWebSocketURL(httpRequest, uriInfo);
        assertEquals("wss://example.com/applicationName/websocket", websocketUrl);
    }

    @Test
    public void testGetApplicationName() {
        String appName = restService.getApplicationName();
        assertEquals("applicationName", appName);
    }
    
    @Test
    public void testStartStop() {
    	when(httpRequest.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(httpRequest.getHeader("X-Forwarded-Host")).thenReturn("example.com");
        when(uri.getPath()).thenReturn("example.com/applicationName");
        when(uriInfo.getBaseUri()).thenReturn(null); 
        
        MediaPushPlugin plugin = mock(MediaPushPlugin.class);
		doReturn(plugin).when(restService).getPluginApp();
        Endpoint request = new Endpoint("http://example.com", 1280, 720, "myJSCommand", "");
		String streamId = "test";
		restService.startMediaPush(request, httpRequest, uriInfo, streamId );
		verify(plugin).startMediaPush(streamId, "wss://example.com/applicationName/websocket", request);
    
		
		//runs script
		restService.mediaPushSendCommand(request, streamId);
		verify(plugin).sendCommand(streamId, request.getJsCommand());

		
		
		restService.stopMediaPush(streamId);
		verify(plugin).stopMediaPush(streamId);

    }
}