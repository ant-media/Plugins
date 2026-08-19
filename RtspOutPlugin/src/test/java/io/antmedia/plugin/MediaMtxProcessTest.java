package io.antmedia.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.Test;

public class MediaMtxProcessTest {

	@Test
	public void testRenderConfigServesOnlyRtsp() {
		RtspOutSettings settings = new RtspOutSettings();
		settings.setRtspPort(9554);

		String config = MediaMtxProcess.renderConfig(settings);

		assertTrue(config.contains("rtsp: true"));
		assertTrue(config.contains("rtspAddress: :9554"));
		assertTrue(config.contains("rtspTransports: [tcp, udp]"));
		assertTrue(config.contains("logLevel: warn"));

		for (String server : new String[] { "rtmp", "hls", "webrtc", "srt", "moq", "api", "metrics", "pprof",
				"playback" }) {
			assertTrue(server + " must be off", config.contains(server + ": false"));
		}
	}

	@Test
	public void testIsListening() throws IOException {
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
			assertTrue(MediaMtxProcess.isListening(port));
		}
		assertFalse(MediaMtxProcess.isListening(port));
	}
}
