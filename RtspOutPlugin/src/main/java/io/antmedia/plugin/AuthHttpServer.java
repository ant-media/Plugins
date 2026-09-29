package io.antmedia.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.antmedia.rest.model.Result;
import jakarta.ws.rs.HttpMethod;

/**
 * Where MediaMTX posts its auth requests. Bound to loopback so it is unreachable from outside, and kept
 * off the app's REST API since MediaMTX can't add headers in case of filters.
 */
final class AuthHttpServer {

	private static final Logger logger = LoggerFactory.getLogger(AuthHttpServer.class);
	private static final Gson gson = new Gson();

	static final String PATH = "/rtsp-out/auth";

	private static final int MAX_BODY_BYTES = 8192;
	private static final int THREADS = 4;

	private static HttpServer server;
	private static ExecutorService executor;
	private static int port;
	private static boolean shutdownHookRegistered;

	private AuthHttpServer() {
	}

	static synchronized int start() {
		if (server != null) {
			return port;
		}

		try {
			HttpServer started = HttpServer.create(
					new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			started.createContext(PATH, AuthHttpServer::handle);
			// the handler hits the datastore, so it must not run on the dispatcher thread
			executor = Executors.newFixedThreadPool(THREADS, runnable -> {
				Thread thread = new Thread(runnable, "rtsp-out-auth");
				thread.setDaemon(true);
				return thread;
			});
			started.setExecutor(executor);
			started.start();

			server = started;
			port = started.getAddress().getPort();
			registerShutdownHook();
			logger.info("RTSP out auth endpoint listening on 127.0.0.1:{}{}", port, PATH);
			return port;
		} catch (IOException e) {
			logger.error("Could not open the RTSP out auth endpoint", e);
			return 0;
		}
	}

	static synchronized void stop() {
		if (server == null) {
			return;
		}

		server.stop(0);
		// stop() leaves a supplied executor alone, and its threads would outlive an app redeploy
		executor.shutdownNow();
		server = null;
		executor = null;
		port = 0;
	}

	static synchronized boolean isRunning() {
		return server != null;
	}

	private static void registerShutdownHook() {
		if (!shutdownHookRegistered) {
			Runtime.getRuntime().addShutdownHook(new Thread(AuthHttpServer::stop));
			shutdownHookRegistered = true;
		}
	}

	private static void handle(HttpExchange exchange) throws IOException {
		try {
			if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
				respond(exchange, 403, "");
				return;
			}

			if (!HttpMethod.POST.equals(exchange.getRequestMethod())) {
				respond(exchange, 405, "");
				return;
			}

			RtspAuthRequest request = gson.fromJson(readBody(exchange), RtspAuthRequest.class);
			if (request == null) {
				respond(exchange, 400, "empty request");
				return;
			}

			Result result = RtspOutPlugin.authorize(request);
			respond(exchange, result.isSuccess() ? 200 : 401, result.getMessage());
		} catch (JsonSyntaxException e) {
			respondUnlessAnswered(exchange, 400, "malformed request");
		} catch (Exception e) {
			logger.error("RTSP out auth request failed", e);
			respondUnlessAnswered(exchange, 500, "");
		} finally {
			exchange.close();
		}
	}

	/** The failure paths can be reached after a response is already on the wire, typically a dead client. */
	private static void respondUnlessAnswered(HttpExchange exchange, int status, String message) {
		if (exchange.getResponseCode() != -1) {
			return;
		}
		try {
			respond(exchange, status, message);
		} catch (IOException e) {
			logger.debug("RTSP out auth response could not be sent: {}", e.getMessage());
		}
	}

	private static String readBody(HttpExchange exchange) throws IOException {
		try (InputStream body = exchange.getRequestBody()) {
			return new String(body.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
		}
	}

	private static void respond(HttpExchange exchange, int status, String message) throws IOException {
		byte[] payload = message != null ? message.getBytes(StandardCharsets.UTF_8) : new byte[0];
		exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);

		if (payload.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(payload);
			}
		}
	}
}
