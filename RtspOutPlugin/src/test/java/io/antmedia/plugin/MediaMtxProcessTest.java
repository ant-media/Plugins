package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;

public class MediaMtxProcessTest {

	@After
	public void tearDown() throws Exception {
		MediaMtxProcess.stop();
		set("process", null);
		set("binary", null);
		set("configFile", null);
		set("givenUp", false);
		set("stopping", false);
		set("generation", 0);
		set("rapidRestarts", 0);
		fakeBinary().delete();
	}

	@Test
	public void testRenderConfigServesOnlyRtsp() {
		String config = MediaMtxProcess.renderConfig(null);

		assertTrue(config.contains("rtsp: true"));
		assertTrue(config.contains("rtspTransports: [tcp, udp]"));
		assertTrue(config.contains("logLevel: warn"));

		// the standard ports, fixed on purpose. UDP playback is dead without the last two
		assertTrue(config.contains("rtspAddress: :8554"));
		assertTrue(config.contains("rtpAddress: :8000"));
		assertTrue(config.contains("rtcpAddress: :8001"));

		// the probe and the URLs use this, so it has to be the port MediaMTX was told to serve
		assertEquals(8554, MediaMtxProcess.RTSP_PORT);

		for (String server : new String[] { "rtmp", "hls", "webrtc", "srt", "moq", "api", "metrics", "pprof",
				"playback" }) {
			assertTrue(server + " must be off", config.contains(server + ": false"));
		}
	}

	@Test
	public void testRenderConfigCallsBackWhenAuthIsOn() {
		String url = "http://127.0.0.1:41234/auth";
		String config = MediaMtxProcess.renderConfig(url);

		assertTrue(config.contains("\nauthMethod: http\nauthHTTPAddress: " + url + "\n"));
		assertFalse(config.contains("authInternalUsers"));
	}

	@Test
	public void testRenderConfigStillKeepsPublishingLocalWithoutAuth() {
		String config = MediaMtxProcess.renderConfig(null);

		assertTrue(config.contains("\nauthMethod: internal\n"));
		// reading is open, but publishing must never be
		assertTrue(config.contains("""
				authInternalUsers:
				  - user: any
				    ips: ["127.0.0.1", "::1"]
				    permissions:
				      - action: publish
				  - user: any
				    ips: []
				    permissions:
				      - action: read"""));
	}

	@Test
	public void testStopEndsTheProcessAndDoesNotRestartIt() throws Exception {
		// it prints first, so the log drain runs too. MediaMTX output belongs in the server log
		installFakeBinary("#!/bin/sh\necho listener opened\nexec sleep 30\n");

		MediaMtxProcess.start(null);
		assertTrue(MediaMtxProcess.isRunning());
		assertEquals(1, MediaMtxProcess.getGeneration());

		Process running = (Process) field("process");
		MediaMtxProcess.stop();
		running.onExit().get(5, TimeUnit.SECONDS);
		Thread.sleep(100);

		assertFalse(MediaMtxProcess.isRunning());
		assertEquals("the supervisor read a deliberate stop as a crash", 1, MediaMtxProcess.getGeneration());
		assertFalse(MediaMtxProcess.isGivenUp());
	}

	/** The binary is resolvable here, so anything but the give-up guard would have spawned it. */
	@Test
	public void testStartIsRefusedOnceTheSupervisorGaveUp() throws Exception {
		installFakeBinary();
		set("givenUp", true);

		MediaMtxProcess.start(null);

		assertEquals(0, MediaMtxProcess.getGeneration());
		assertNull(MediaMtxProcess.getBinaryPath());
	}

	/** A binary that dies at once is the port conflict case, and the restart budget is the server's. */
	@Test
	public void testGiveUpSurvivesTheNextAppStarting() throws Exception {
		installFakeBinary();

		MediaMtxProcess.start(null);
		// a real process has to die for handleExit to run, so this is the one test that waits
		waitUntil(MediaMtxProcess::isGivenUp);

		int spawns = MediaMtxProcess.getGeneration();
		assertEquals(field("MAX_RAPID_RESTARTS"), spawns);

		MediaMtxProcess.start(null);
		MediaMtxProcess.start(null);

		assertTrue(MediaMtxProcess.isGivenUp());
		assertEquals(spawns, MediaMtxProcess.getGeneration());
	}

	/** The reconciler calls this every pass, so it may only ever cover a boot that never happened. */
	@Test
	public void testEnsureStartedOnlyCoversANeverStartedProcess() throws Exception {
		installFakeBinary("#!/bin/sh\nexec sleep 30\n");
		set("generation", 3);

		MediaMtxProcess.ensureStarted(null);
		assertEquals(3, MediaMtxProcess.getGeneration());
		assertNull(MediaMtxProcess.getBinaryPath());

		// nothing ever started, which is the master switch being turned on without a server restart
		set("generation", 0);
		MediaMtxProcess.ensureStarted(null);
		assertEquals(1, MediaMtxProcess.getGeneration());
	}

	/** Where resolveBinary() looks first, so start() picks this up instead of a real MediaMTX. */
	private static File fakeBinary() throws Exception {
		Method pluginDirectory = MediaMtxProcess.class.getDeclaredMethod("pluginDirectory");
		pluginDirectory.setAccessible(true);
		return new File((File) pluginDirectory.invoke(null), "antmedia-mediamtx");
	}

	/** Dies immediately, which is what a port conflict looks like to the supervisor. */
	private static void installFakeBinary() throws Exception {
		installFakeBinary("#!/bin/sh\nexit 1\n");
	}

	private static void installFakeBinary(String script) throws Exception {
		File binary = fakeBinary();
		Files.writeString(binary.toPath(), script);
		assertTrue(binary.setExecutable(true));
		binary.deleteOnExit();
	}

	private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
		assertTrue("condition never became true within the deadline", condition.getAsBoolean());
	}

	private static Object field(String name) throws Exception {
		Field field = MediaMtxProcess.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	private static void set(String name, Object value) throws Exception {
		Field field = MediaMtxProcess.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
