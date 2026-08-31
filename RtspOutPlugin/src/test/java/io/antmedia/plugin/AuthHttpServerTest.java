package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import io.antmedia.AppSettings;

public class AuthHttpServerTest {

	private static final String APP = "AuthHttpServerTestApp";

	@After
	public void tearDown() throws Exception {
		AuthHttpServer.stop();
		apps().remove(APP);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, RtspOutPlugin> apps() throws Exception {
		Field field = RtspOutPlugin.class.getDeclaredField("APPS");
		field.setAccessible(true);
		return (Map<String, RtspOutPlugin>) field.get(null);
	}

	/** An app that is up and has no play security on. Enough for the authorizer to say yes. */
	private static void registerOpenApp() throws Exception {
		RtspOutPlugin plugin = new RtspOutPlugin();
		Field appSettings = RtspOutPlugin.class.getDeclaredField("appSettings");
		appSettings.setAccessible(true);
		appSettings.set(plugin, mock(AppSettings.class));
		apps().put(APP, plugin);
	}

	private int post(String body) throws IOException {
		HttpURLConnection connection = open("POST");
		connection.setDoOutput(true);
		connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
		return connection.getResponseCode();
	}

	private HttpURLConnection open(String method) throws IOException {
		URL url = new URL("http://127.0.0.1:" + AuthHttpServer.start() + AuthHttpServer.PATH);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod(method);
		return connection;
	}

	/** Off the box this must be unreachable, so the bind address itself is what matters. */
	@Test
	public void testBindsToLoopbackOnly() throws Exception {
		int port = AuthHttpServer.start();

		assertNotEquals(0, port);
		assertTrue(AuthHttpServer.isRunning());

		Field field = AuthHttpServer.class.getDeclaredField("server");
		field.setAccessible(true);
		InetAddress bound = ((HttpServer) field.get(null)).getAddress().getAddress();
		assertTrue("bound to " + bound + ", reachable from the network", bound.isLoopbackAddress());

		// same port back, so every app shares the one listener
		assertEquals(port, AuthHttpServer.start());
	}

	/** The whole point of the listener, so it needs a test that is not a refusal. */
	@Test
	public void testAllowedReaderGetsThrough() throws Exception {
		registerOpenApp();

		assertEquals(200, post("{\"action\":\"read\",\"path\":\"" + APP + "/stream1\",\"ip\":\"10.0.0.9\"}"));
	}

	@Test
	public void testUnknownAppIsRefused() throws IOException {
		assertEquals(401, post("{\"action\":\"read\",\"path\":\"NoSuchApp/stream1\",\"ip\":\"10.0.0.9\"}"));
	}

	@Test
	public void testEmptyBodyIsRejected() throws IOException {
		assertEquals(400, post(""));
	}

	/** MediaMTX waits on this call, so a failure has to come back as a status code, not as nothing. */
	@Test
	public void testAnUnexpectedFailureStillAnswers() throws Exception {
		// a mock never runs the field initialisers, so its authorizer is null and authorize() throws
		apps().put(APP, mock(RtspOutPlugin.class));

		assertEquals(500, post("{\"action\":\"read\",\"path\":\"" + APP + "/stream1\",\"ip\":\"10.0.0.9\"}"));
	}

	@Test
	public void testMalformedBodyIsRejected() throws IOException {
		assertEquals(400, post("not json at all"));
	}

	@Test
	public void testUnknownJsonFieldsAreIgnored() throws IOException {
		// MediaMTX may add fields, and a parse failure here would refuse a viewer
		assertEquals(401, post("{\"action\":\"read\",\"path\":\"NoSuchApp/s\",\"somethingNew\":true}"));
	}

	@Test
	public void testOnlyPostIsAccepted() throws IOException {
		assertEquals(405, open("GET").getResponseCode());
	}

	@Test
	public void testRestartLeavesNoThreadsBehind() {
		long before = Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> thread.getName().startsWith("rtsp-out-auth")).count();

		for (int i = 0; i < 3; i++) {
			AuthHttpServer.start();
			AuthHttpServer.stop();
		}

		long after = Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> thread.getName().startsWith("rtsp-out-auth") && thread.isAlive()).count();
		assertTrue("worker threads outlived stop(): " + before + " -> " + after, after <= before);
		assertFalse(AuthHttpServer.isRunning());
	}
}
