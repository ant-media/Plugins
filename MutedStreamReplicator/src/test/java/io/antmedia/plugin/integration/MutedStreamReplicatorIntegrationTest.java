package io.antmedia.plugin.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.awaitility.core.ThrowingRunnable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;

import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.User;
import io.antmedia.rest.model.Result;

public class MutedStreamReplicatorIntegrationTest {

	private static final Gson GSON = new Gson();
	private static final String APP_NAME = "LiveApp";
	private static final String TEST_USER_EMAIL = "test@antmedia.io";
	private static final String TEST_USER_PASSWORD_HASH = "05a671c66aefea124cc08b76ea6d30bb";
	private static final String MUTED_SUFFIX = "-muted";
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	private HttpClient httpClient;
	private Process ffmpegProcess;
	private AppSettings originalSettings;
	private String streamId;
	private String mutedStreamId;

	@Before
	public void before() {
		httpClient = HttpClient.newBuilder()
				.connectTimeout(REQUEST_TIMEOUT)
				.cookieHandler(cookieManager)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	@After
	public void after() throws Exception {
		stopProcess(ffmpegProcess);
		deleteBroadcastIfExists(mutedStreamId);
		deleteBroadcastIfExists(streamId);
		restoreAppSettings();
	}

	@Test
	public void testMutedReplicaIsGeneratedForAdaptiveRtmpStream() throws Exception {
		authenticateConsole();

		originalSettings = getAppSettings();
		AppSettings updatedSettings = cloneSettings(originalSettings);
		updatedSettings.setEncoderSettings(Arrays.asList(
				new EncoderSettings(240, 300_000, 64_000, true),
				new EncoderSettings(360, 500_000, 64_000, true)));
		assertTrue(setAppSettings(updatedSettings).isSuccess());

		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
			List<EncoderSettings> encoderSettings = getAppSettings().getEncoderSettings();
			assertNotNull(encoderSettings);
			assertEquals(2, encoderSettings.size());
		});

		streamId = "muted-replicator-" + System.currentTimeMillis();
		mutedStreamId = streamId + MUTED_SUFFIX;

		ffmpegProcess = startPublishingStream(streamId);

		await().atMost(45, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> isBroadcasting(streamId));
		await().atMost(45, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> isBroadcasting(mutedStreamId));

		String originalPlaylist = streamPlaylistUrl(streamId);
		String originalAdaptivePlaylist = adaptivePlaylistUrl(streamId);
		String mutedPlaylist = streamPlaylistUrl(mutedStreamId);
		String mutedAdaptivePlaylist = adaptivePlaylistUrl(mutedStreamId);

		await().atMost(60, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).until(() -> urlAvailable(originalPlaylist));
		await().atMost(60, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).until(() -> urlAvailable(originalAdaptivePlaylist));
		await().atMost(60, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).until(() -> urlAvailable(mutedPlaylist));
		await().atMost(60, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).until(() -> urlAvailable(mutedAdaptivePlaylist));

		assertTrue("Original stream should contain video and audio", probeStream(originalPlaylist, true, true));
		assertTrue("Muted stream should be probeable and contain video", probeStream(mutedPlaylist, true, false));
		assertTrue("Original adaptive playlist should be probeable", probeStream(originalAdaptivePlaylist, true, false));
		assertTrue("Muted adaptive playlist should be probeable", probeStream(mutedAdaptivePlaylist, true, false));
	}

	private void authenticateConsole() throws Exception {
		Result firstLoginStatus = get(ROOTServiceUrl() + "/first-login-status", Result.class);
		if (firstLoginStatus.isSuccess()) {
			User user = new User();
			user.setEmail(TEST_USER_EMAIL);
			user.setPassword(TEST_USER_PASSWORD_HASH);
			assertTrue(post(ROOTServiceUrl() + "/users/initial", user, Result.class).isSuccess());
		}

		User user = new User();
		user.setEmail(TEST_USER_EMAIL);
		user.setPassword(TEST_USER_PASSWORD_HASH);
		assertTrue(post(ROOTServiceUrl() + "/users/authenticate", user, Result.class).isSuccess());
	}

	private AppSettings getAppSettings() throws Exception {
		return get(ROOTServiceUrl() + "/applications/settings/" + APP_NAME, AppSettings.class);
	}

	private Result setAppSettings(AppSettings appSettings) throws Exception {
		return post(ROOTServiceUrl() + "/applications/settings/" + APP_NAME, appSettings, Result.class);
	}

	private AppSettings cloneSettings(AppSettings appSettings) {
		return GSON.fromJson(GSON.toJson(appSettings), AppSettings.class);
	}

	private Process startPublishingStream(String publishedStreamId) throws IOException {
		String ffmpegPath = executablePath("ffmpeg");
		String command = ffmpegPath
				+ " -stream_loop -1 -re -i " + quote(resolveSampleFile().getAbsolutePath())
				+ " -codec copy -f flv rtmp://127.0.0.1/" + APP_NAME + "/" + publishedStreamId;
		return new ProcessBuilder("bash", "-lc", command)
				.redirectErrorStream(true)
				.start();
	}

	private boolean isBroadcasting(String targetStreamId) throws Exception {
		Broadcast broadcast = getBroadcast(targetStreamId);
		return broadcast != null && "broadcasting".equalsIgnoreCase(broadcast.getStatus());
	}

	private Broadcast getBroadcast(String targetStreamId) throws Exception {
		HttpResponse<String> response = send(requestBuilder(appRestUrl() + "/broadcasts/" + targetStreamId).GET().build());
		if (response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
			return null;
		}
		assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
		return GSON.fromJson(response.body(), Broadcast.class);
	}

	private void deleteBroadcastIfExists(String targetStreamId) throws Exception {
		if (targetStreamId == null) {
			return;
		}

		HttpResponse<String> response = send(requestBuilder(appRestUrl() + "/broadcasts/" + targetStreamId).DELETE().build());
		assertTrue("Delete should return 200 or 404 for " + targetStreamId,
				response.statusCode() == HttpURLConnection.HTTP_OK || response.statusCode() == HttpURLConnection.HTTP_NOT_FOUND);
	}

	private String streamPlaylistUrl(String targetStreamId) {
		return "http://127.0.0.1:5080/" + APP_NAME + "/streams/" + targetStreamId + ".m3u8";
	}

	private String adaptivePlaylistUrl(String targetStreamId) {
		return "http://127.0.0.1:5080/" + APP_NAME + "/streams/" + targetStreamId + "_adaptive.m3u8";
	}

	private boolean urlAvailable(String url) throws Exception {
		HttpResponse<String> response = send(requestBuilder(url)
				.method("HEAD", HttpRequest.BodyPublishers.noBody())
				.build());
		return response.statusCode() == HttpURLConnection.HTTP_OK;
	}

	private boolean probeStream(String url, boolean requireVideo, boolean requireAudio) throws Exception {
		Process process = new ProcessBuilder(
				executablePath("ffprobe"),
				"-v", "error",
				"-show_entries", "stream=codec_type",
				"-of", "default=noprint_wrappers=1:nokey=1",
				url)
				.redirectErrorStream(true)
				.start();

		String output = readAll(process.getInputStream());
		assertEquals("ffprobe should exit successfully for " + url + ". Output: " + output, 0, process.waitFor());

		boolean hasVideo = output.contains("video");
		boolean hasAudio = output.contains("audio");

		return (!requireVideo || hasVideo) && (!requireAudio || hasAudio);
	}

	private <T> T get(String url, Class<T> responseType) throws Exception {
		HttpResponse<String> response = send(requestBuilder(url).GET().build());
		assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
		return GSON.fromJson(response.body(), responseType);
	}

	private <T> T post(String url, Object body, Class<T> responseType) throws Exception {
		HttpRequest request = requestBuilder(url)
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
				.build();
		HttpResponse<String> response = send(request);
		assertEquals("Unexpected status code for " + url + " body: " + response.body(), HttpURLConnection.HTTP_OK, response.statusCode());
		return GSON.fromJson(response.body(), responseType);
	}

	private HttpRequest.Builder requestBuilder(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json");
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private void restoreAppSettings() throws Exception {
		if (originalSettings == null) {
			return;
		}

		await().atMost(20, TimeUnit.SECONDS).untilAsserted(new ThrowingRunnable() {
			@Override
			public void run() throws Throwable {
				assertTrue(setAppSettings(originalSettings).isSuccess());
			}
		});
	}

	private File resolveSampleFile() {
		List<File> candidates = Arrays.asList(
				new File("../../Ant-Media-Server/src/test/resources/test.flv"),
				new File("../Ant-Media-Server/src/test/resources/test.flv"),
				new File("src/test/resources/test.flv"));
		for (File candidate : candidates) {
			if (candidate.exists()) {
				return candidate;
			}
		}
		throw new IllegalStateException("Cannot find test.flv sample file for ffmpeg publish test");
	}

	private String ROOTServiceUrl() throws Exception {
		return "http://" + InetAddress.getLocalHost().getHostAddress() + ":5080/rest/v2";
	}

	private String appRestUrl() {
		return "http://127.0.0.1:5080/" + APP_NAME + "/rest/v2";
	}

	private String executablePath(String executable) {
		String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
		if (osName.startsWith("mac os x") || osName.startsWith("darwin")) {
			return "/usr/local/bin/" + executable;
		}
		return executable;
	}

	private void stopProcess(Process process) throws InterruptedException {
		if (process == null) {
			return;
		}
		process.destroy();
		if (!process.waitFor(5, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			process.waitFor(5, TimeUnit.SECONDS);
		}
	}

	private String readAll(InputStream inputStream) throws IOException {
		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line).append('\n');
			}
		}
		return builder.toString();
	}

	private String quote(String value) {
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}
}
