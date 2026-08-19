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

	private static final String BINARY_NAME = "mediamtx";
	private static final String CONFIG_NAME = "mediamtx-antmedia.yml";

	private static final long RESTART_GRACE_MS = 5000;
	private static final int MAX_RAPID_RESTARTS = 5;
	private static final int PROBE_TIMEOUT_MS = 350;

	private static Process process;
	private static File configFile;
	private static String configYaml;
	private static String binary;
	private static long lastStartMs;
	private static int rapidRestarts;
	private static boolean stopping;
	private static boolean shutdownHookRegistered;

	/** Off the class monitor on purpose: spawn() holds it while it blocks and a reconcile pass must not wait. */
	private static volatile int port;
	private static volatile int generation;
	private static volatile boolean givenUp;

	private MediaMtxProcess() {
	}

	static synchronized void start(RtspOutSettings settings) {
		if (isRunning()) {
			if (settings.getRtspPort() != port) {
				logger.warn("MediaMTX is already running on port {}, ignoring requested port {}",
						port, settings.getRtspPort());
			}
			return;
		}

		String resolvedBinary = resolveBinary();
		if (resolvedBinary == null) {
			logger.error("mediamtx binary not found in plugins dir or installed in /usr/local/bin");
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
		configYaml = renderConfig(settings);
		port = settings.getRtspPort();
		stopping = false;
		givenUp = false;
		rapidRestarts = 0;
		registerShutdownHook();
		spawn();
	}

	static synchronized void ensureStarted(RtspOutSettings settings) {
		// Starts MediaMTX only if it never started at all.
		if (generation == 0 && !givenUp) {
			start(settings);
		}
	}

	static synchronized void stop() {
		stopping = true;
		if (process != null) {
			process.destroy();
			process = null;
		}
	}

	static synchronized boolean isRunning() {
		return process != null && process.isAlive();
	}

	/** True once MediaMTX accepts connections, which lags process start by a few hundred ms. */
	static boolean isListening(int rtspPort) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), rtspPort), PROBE_TIMEOUT_MS);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	static synchronized String getBinaryPath() {
		return binary;
	}

	static int getPort() {
		return port;
	}

	/** Bumped on every spawn, so muxers left over from a restarted MediaMTX can be spotted.
	 * 0 before the first start. */
	static int getGeneration() {
		return generation;
	}

	/** True once the supervisor stopped restarting.
	 *  Nothing will publish again before a server restart. */
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
					binary, port, configFile.getAbsolutePath(), generation);

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
					+ "until the server restarts. Check that port {} is free and review {}",
					MAX_RAPID_RESTARTS, port, configFile.getAbsolutePath());
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

	static String renderConfig(RtspOutSettings settings) {
		return """
				# Generated by the Ant Media RTSP output plugin. Overwritten on every server start.
				# warn on purpose, info writes a few lines per connection into the server log.
				logLevel: warn
				logDestinations: [stdout]

				api: false
				metrics: false
				pprof: false
				playback: false

				rtsp: true
				rtspTransports: [tcp, udp]
				rtspAddress: :%d

				rtmp: false
				hls: false
				webrtc: false
				srt: false
				moq: false

				paths:
				  all_others:
				""".formatted(settings.getRtspPort());
	}

	/** Next to the plugin jar first, then the usual install locations. */
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

	/** Directory plugin's jar was loaded from */
	private static File pluginDirectory() {
		try {
			return new File(MediaMtxProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI())
					.getParentFile();
		} catch (Exception e) {
			return null;
		}
	}
}
