package io.antmedia.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The single MediaMTX process shared by every app in this JVM. Static because one server means one RTSP port. */
final class MediaMtxProcess {

	private static final Logger logger = LoggerFactory.getLogger(MediaMtxProcess.class);

	private static final String BINARY_NAME = "antmedia-mediamtx";
	private static final String CONFIG_NAME = "mediamtx-antmedia.yml";

	/** The standard RTSP ports. Not settings: one server means one MediaMTX, and clients expect these. */
	static final int RTSP_PORT = 8554;
	private static final int RTP_PORT = 8000;
	private static final int RTCP_PORT = RTP_PORT + 1;

	/** Auth is off, but publishing stays ours: MediaMTX binds every interface and would let anyone push in. */
	private static final String NO_CALLBACK_AUTH = """
			authMethod: internal
			authInternalUsers:
			  - user: any
			    ips: ["127.0.0.1", "::1"]
			    permissions:
			      - action: publish
			  - user: any
			    ips: []
			    permissions:
			      - action: read""";

	private static final long RESTART_GRACE_MS = 5000;
	private static final int MAX_RAPID_RESTARTS = 5;
	private static final int PROBE_TIMEOUT_MS = 350;

	private static File configFile;
	private static String configYaml;
	private static long lastStartMs;
	private static int rapidRestarts;
	private static boolean stopping;
	private static boolean shutdownHookRegistered;

	/** Written under the monitor, read without it. spawn() holds it while blocking, readers must not wait. */
	private static volatile Process process;
	private static volatile String binary;
	private static volatile int generation;
	private static volatile boolean givenUp;

	private MediaMtxProcess() {
	}

	static synchronized void start(String authUrl) {
		// every app calls this at boot, and the restart budget is for the server, not for each of them
		if (isRunning() || givenUp) {
			return;
		}

		String resolvedBinary = resolveBinary();
		if (resolvedBinary == null) {
			logger.error("{} not found in the plugins dir, /usr/local/bin or on PATH", BINARY_NAME);
			givenUp = true;
			return;
		}

		File file = createConfigFile();
		if (file == null) {
			givenUp = true;
			return;
		}

		binary = resolvedBinary;
		configFile = file;
		configYaml = renderConfig(authUrl);
		stopping = false;
		rapidRestarts = 0;
		registerShutdownHook();
		spawn();
	}

	/** Starts MediaMTX only if it never started at all. start() turns down the rest. */
	static synchronized void ensureStarted(String authUrl) {
		if (generation == 0) {
			start(authUrl);
		}
	}

	static synchronized void stop() {
		stopping = true;
		if (process != null) {
			process.destroy();
			process = null;
		}
	}

	static boolean isRunning() {
		Process running = process;
		return running != null && running.isAlive();
	}

	/** True once MediaMTX accepts connections, which lags process start by a few hundred ms. */
	static boolean isListening() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), RTSP_PORT), PROBE_TIMEOUT_MS);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	static String getBinaryPath() {
		return binary;
	}

	/** Bumped on every spawn, so stale muxers can be spotted. 0 before the first start. */
	static int getGeneration() {
		return generation;
	}

	/** True once the supervisor stopped restarting. Nothing publishes again before a server restart. */
	static boolean isGivenUp() {
		return givenUp;
	}

	private static void spawn() {
		try {
			// /tmp gets swept, and the config has to survive a restart days after the first start
			Files.writeString(configFile.toPath(), configYaml);

			Process started = new ProcessBuilder(binary, configFile.getAbsolutePath())
					.directory(configFile.getParentFile())
					.redirectErrorStream(true)
					.start();

			process = started;
			generation++;
			lastStartMs = System.currentTimeMillis();
			logger.info("MediaMTX started from {} on RTSP port {} with config {}, generation {}",
					binary, RTSP_PORT, configFile.getAbsolutePath(), generation);

			drainLogs(started);
			// last, because a process that dies at once completes this inline and handleExit respawns
			started.onExit().thenAccept(MediaMtxProcess::handleExit);
		} catch (IOException e) {
			logger.error("Could not start MediaMTX from {}", binary, e);
			process = null;
			givenUp = true;
		}
	}

	private static synchronized void handleExit(Process exited) {
		if (stopping || exited != process) {
			return;
		}

		rapidRestarts = System.currentTimeMillis() - lastStartMs < RESTART_GRACE_MS ? rapidRestarts + 1 : 0;
		if (rapidRestarts >= MAX_RAPID_RESTARTS) {
			logger.error("MediaMTX died {} times in a row right after starting, giving up. There is no RTSP output "
					+ "until the server restarts. Check that tcp {} and udp {} and {} are free and review {}",
					MAX_RAPID_RESTARTS, RTSP_PORT, RTP_PORT, RTCP_PORT, configFile.getAbsolutePath());
			process = null;
			givenUp = true;
			return;
		}

		logger.warn("MediaMTX exited with code {}, restarting", exited.exitValue());
		spawn();
	}

	private static void drainLogs(Process p) {
		Thread reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					logger.info("[mediamtx] {}", line);
				}
			} catch (IOException e) {
				logger.debug("MediaMTX log reader stopped: {}", e.getMessage());
			}
		}, "mediamtx-log");
		reader.setDaemon(true);
		reader.start();
	}

	private static void registerShutdownHook() {
		if (!shutdownHookRegistered) {
			Runtime.getRuntime().addShutdownHook(new Thread(MediaMtxProcess::stop));
			shutdownHookRegistered = true;
		}
	}

	/** Generated state in tmp dir, and nobody else can pre-create it. */
	private static File createConfigFile() {
		try {
			File dir = Files.createTempDirectory("mediamtx-antmedia").toFile();
			dir.deleteOnExit();
			File file = new File(dir, CONFIG_NAME);
			file.deleteOnExit();
			return file;
		} catch (IOException e) {
			logger.error("Could not create a temp directory for the MediaMTX config", e);
			return null;
		}
	}

	static String renderConfig(String authUrl) {
		String auth = authUrl != null ? "authMethod: http\nauthHTTPAddress: " + authUrl : NO_CALLBACK_AUTH;

		return """
				# Generated by the Ant Media RTSP output plugin. Overwritten on every server start.
				# warn on purpose, info writes a few lines per connection into the server log.
				logLevel: warn
				logDestinations: [stdout]

				%s

				api: false
				metrics: false
				pprof: false
				playback: false

				rtsp: true
				rtspTransports: [tcp, udp]
				rtspAddress: :%d
				rtpAddress: :%d
				rtcpAddress: :%d

				rtmp: false
				hls: false
				webrtc: false
				srt: false
				moq: false

				paths:
				  all_others:
				""".formatted(auth, RTSP_PORT, RTP_PORT, RTCP_PORT);
	}

	private static String resolveBinary() {
		List<String> candidates = new ArrayList<>();

		File pluginDir = pluginDirectory();
		if (pluginDir != null) {
			candidates.add(new File(pluginDir, BINARY_NAME).getAbsolutePath());
		}

		candidates.add("/usr/local/bin/" + BINARY_NAME);

		String path = System.getenv("PATH");
		if (path != null) {
			for (String entry : path.split(File.pathSeparator)) {
				candidates.add(new File(entry, BINARY_NAME).getAbsolutePath());
			}
		}

		return candidates.stream().filter(MediaMtxProcess::isExecutable).findFirst().orElse(null);
	}

	private static boolean isExecutable(String path) {
		File file = new File(path);
		return file.isFile() && file.canExecute();
	}

	private static File pluginDirectory() {
		try {
			return new File(MediaMtxProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getParentFile();
		} catch (Exception e) {
			return null;
		}
	}
}
