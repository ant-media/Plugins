package io.antmedia.rest;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import io.antmedia.AntMediaApplicationAdapter;
import jakarta.servlet.ServletContext;
import org.junit.Before;
import org.junit.Test;
import jakarta.ws.rs.core.UriInfo;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.URISyntaxException;

public class RestServiceUnitTest {

    private RestService restService;
    private HttpRequest httpRequest;
    private UriInfo uriInfo;
    private URI uri;
    private ServletContext servletContext;
    private ApplicationContext applicationContext;
    private AntMediaApplicationAdapter antMediaApplicationAdapter;

    @Before
    public void setUp() {
        restService = new RestService();
        httpRequest = mock(HttpRequest.class);
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
        when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
        httpRequest.getHeaders().add("X-Forwarded-Proto", "https");
        httpRequest.getHeaders().add("X-Forwarded-Host", "example.com");
        httpRequest.getHeaders().add("X-Forwarded-Port", "8080");
        when(uri.getPath()).thenReturn("example.com:8080/applicationName");
        when(uriInfo.getBaseUri()).thenReturn(uri); // Mocking to return null as it's not used in this scenario

        String websocketUrl = restService.getWebSocketURL(httpRequest, uriInfo);
        assertEquals("wss://example.com:8080/applicationName/websocket", websocketUrl);
    }

    @Test
    public void shouldFallbackToUriInfoWhenHeadersAreMissing() throws URISyntaxException {
        when(httpRequest.getHeaders()).thenReturn(new HttpHeaders()); // No headers added
        when(uri.getPath()).thenReturn("fallback.com:8080/applicationName");
        when(uriInfo.getBaseUri()).thenReturn(new URI("http://fallback.com:8080/applicationName"));

        String websocketUrl = restService.getWebSocketURL(httpRequest, uriInfo);
        assertEquals("ws://fallback.com:8080/applicationName/websocket", websocketUrl);
    }

    @Test
    public void shouldHandleMissingPortGracefully() {
        when(httpRequest.getHeaders()).thenReturn(new HttpHeaders());
        httpRequest.getHeaders().add("X-Forwarded-Proto", "https");
        httpRequest.getHeaders().add("X-Forwarded-Host", "example.com");
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
}