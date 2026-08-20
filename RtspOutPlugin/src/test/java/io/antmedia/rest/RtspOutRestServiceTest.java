package io.antmedia.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.RtspOutPlugin;
import io.antmedia.rest.model.Result;
import jakarta.servlet.ServletContext;

public class RtspOutRestServiceTest {

	private static final String STREAM_ID = "stream1";
	private static final String URL = "rtsp://10.0.0.1:8554/LiveApp/" + STREAM_ID;

	private RtspOutRestService rest;
	private RtspOutPlugin plugin;

	@Before
	public void setUp() {
		plugin = mock(RtspOutPlugin.class);
		when(plugin.getRtspUrl(STREAM_ID)).thenReturn(URL);
		when(plugin.getStatus()).thenReturn(Map.of("app", "LiveApp", "rtspPort", 8554));

		ApplicationContext appCtx = mock(ApplicationContext.class);
		when(appCtx.getBean(RtspOutPlugin.BEAN_NAME)).thenReturn(plugin);

		ServletContext servletContext = mock(ServletContext.class);
		when(servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE))
				.thenReturn(appCtx);

		rest = new RtspOutRestService();
		rest.servletContext = servletContext;
	}

	/** Every call has to reach the bean of the app the request came in on, not a shared one. */
	@Test
	public void testStatusComesFromThisAppsBean() {
		assertEquals("LiveApp", rest.getStatus().get("app"));
		assertEquals(8554, rest.getStatus().get("rtspPort"));
	}

	@Test
	public void testEnableAnswersWithTheUrlToPlay() {
		Result result = rest.enable(STREAM_ID);

		verify(plugin).enable(STREAM_ID);
		assertTrue(result.isSuccess());
		assertTrue("an operator needs the URL back, not just an ok", result.getMessage().contains(URL));
	}

	@Test
	public void testDisableTurnsTheStreamOff() {
		Result result = rest.disable(STREAM_ID);

		verify(plugin).disable(STREAM_ID);
		assertTrue(result.isSuccess());
		assertTrue(result.getMessage().contains(STREAM_ID));
	}
}
