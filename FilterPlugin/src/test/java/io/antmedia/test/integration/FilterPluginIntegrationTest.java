package io.antmedia.test.integration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.User;
import io.antmedia.datastore.db.types.VoD;
import io.antmedia.datastore.db.types.Broadcast.PlayListItem;
import io.antmedia.datastore.db.types.BroadcastUpdate;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.filter.utils.FilterConfiguration;
// import io.antmedia.plugin.api.IFilterAdaptor; // Removed this unused import
import io.antmedia.plugin.FiltersManager;
import io.antmedia.rest.model.Result;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.awaitility.Awaitility;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.junit.Assert.*;

public class FilterPluginIntegrationTest {

	public static String ffmpegPath = "/usr/bin/ffmpeg";
	public static String appName = "LiveApp";
	private Gson gson = new Gson();
	private Process rtmpSendingProcess;
	public static String ffprobePath = "/usr/bin/ffprobe";
	private static Process tmpExec;
	public static Logger logger = LoggerFactory.getLogger(FilterPluginIntegrationTest.class);
	//public static String ffmpegPath = "/usr/local/bin/ffmpeg";
	public static String SERVER_ADDR;
	public static int OS_TYPE;
	public static final int MAC_OS_X = 0;
	public static final int LINUX = 1;
	public static final int WINDOWS = 2;
	public static boolean audioExists;
	public static boolean videoExists;
    
	private static final String ROOT_SERVICE_URL = "http://localhost:5080/LiveApp/rest";


	private static String TEST_USER_EMAIL = "test@antmedia.io";
	private static String TEST_USER_PASS = "05a671c66aefea124cc08b76ea6d30bb";
	private static BasicCookieStore httpCookieStore;


	private static  String DEFAULT_INSTALLATION_PATH = "/usr/local/antmedia/";
	static {
		String osName = System.getProperty("os.name", "").toLowerCase();
		if (osName.startsWith("mac os x") || osName.startsWith("darwin")) {
			OS_TYPE = MAC_OS_X;
			DEFAULT_INSTALLATION_PATH = "/Users/mekya/softwares/ant-media-server/";
		} 
	}
	
	
	@Before
	public void before() throws Exception {
		try {
			httpCookieStore = new BasicCookieStore();

			Result firstLogin = callisFirstLogin();
			if (firstLogin.isSuccess()) {
				User user = new User();
				user.setEmail(TEST_USER_EMAIL);
				user.setPassword(TEST_USER_PASS);
				Result createInitialUser = callCreateInitialUser(user);
				assertTrue(createInitialUser.isSuccess());


			}


			User user = new User();
			user.setEmail(TEST_USER_EMAIL);
			user.setPassword(TEST_USER_PASS);
			assertTrue(callAuthenticateUser(user).isSuccess());


		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}

	@After
	public void after() {
		if (rtmpSendingProcess != null && rtmpSendingProcess.isAlive()) {
			rtmpSendingProcess.destroy();
		}
	}


	static {
		String osName = System.getProperty("os.name", "").toLowerCase();
		if (osName.startsWith("mac os x") || osName.startsWith("darwin")) {
			OS_TYPE = MAC_OS_X;
		} else if (osName.startsWith("windows")) {
			OS_TYPE = WINDOWS;
		} else if (osName.startsWith("linux")) {
			OS_TYPE = LINUX;
		}

		if (OS_TYPE == MAC_OS_X) {
			ffmpegPath = "/usr/local/bin/ffmpeg";
			ffprobePath = "/usr/local/bin/ffprobe";
		}

		try {
			SERVER_ADDR =  InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testScaleAndPadFilter() throws Exception {
		// AppSettings appSettings = callGetAppSettings(appName); // May not be needed unless specific app settings affect filters

		String inputStreamId = "inputStream_" + RandomStringUtils.randomAlphanumeric(5);
		String outputStreamId = "outputStream_" + RandomStringUtils.randomAlphanumeric(5);

		FilterConfiguration filterConfig = new FilterConfiguration();
		filterConfig.setFilterId("testFilter_" + RandomStringUtils.randomAlphanumeric(5));
		filterConfig.setInputStreams(List.of(inputStreamId));
		filterConfig.setOutputStreams(List.of(outputStreamId));
		filterConfig.setVideoFilter("[in0]scale=360:240:force_original_aspect_ratio=decrease,pad=720:480:(ow-iw)/2:(oh-ih)/2:color=blue[out0]"); // Example: scale and pad filter
		filterConfig.setAudioFilter("[in0]anull[out0]"); // Example: audio passthrough
		filterConfig.setType(FilterConfiguration.ASYNCHRONOUS); // Or SYNCHRONOUS, LASTPOINT depending on test case
		filterConfig.setVideoEnabled(true);
		filterConfig.setAudioEnabled(true); // Enable or disable based on what you want to test
        filterConfig.setVideoOutputHeight(480);

		//send the stream to the input stream ID
		rtmpSendingProcess = execute(ffmpegPath
				+ " -re -i src/test/resources/test_video_360p.flv -codec copy -f flv rtmp://localhost/LiveApp/"
				+ inputStreamId);

		//check that the input stream is publishing
		Awaitility.await().atMost(15, TimeUnit.SECONDS)
		.pollInterval(1, TimeUnit.SECONDS)
		.until(() -> {
			Broadcast broadcast = getBroadcast(inputStreamId);
			return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
		});
		logger.info("Input stream {} is broadcasting.", inputStreamId);

		Result creationResult = callCreateFilter(filterConfig);
		assertTrue("Filter creation failed: " + creationResult.getMessage(), creationResult.isSuccess());
		String createdFilterId = creationResult.getDataId(); // Assuming dataId contains the created filterId
		assertNotNull(createdFilterId);

		//check that the output stream is also broadcasting and playable
		Awaitility.await().atMost(20, TimeUnit.SECONDS)
		.pollInterval(1, TimeUnit.SECONDS)
		.until(() -> {
			Broadcast broadcast = getBroadcast(outputStreamId);
			logger.info("Output stream {} status: {}", outputStreamId, broadcast != null ? broadcast.getStatus() : "null");
			return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
		});

		Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> {
			return testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId + "_adaptive.m3u8", 0, false);
		});

		// Add assertions here to verify filter effects if possible
		// For example, if using a scale filter, you might want to use ffprobe on the output stream
		// to check its resolution. This is more advanced and might require a separate utility method.
		StreamResolution resolution = getStreamResolution("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId + "_adaptive.m3u8");
		assertNotNull("Could not get resolution for output stream: " + outputStreamId, resolution);
		assertEquals("Output stream height does not match filter.", 480, resolution.height);
		logger.info("Verified output stream resolution: {}x{}", resolution.width, resolution.height);

		// Cleanup: delete the filter
		Result deleteResult = callDeleteFilter(createdFilterId);
		assertTrue("Filter deletion failed: " + deleteResult.getMessage(), deleteResult.isSuccess());
	}


	@Test
	public void testOverlayVideoFilterAndPublish() throws Exception {
		List<Process> ffmpegProcesses = new java.util.ArrayList<>();
		String inputStreamId1 = "inputStream1_" + RandomStringUtils.randomAlphanumeric(5);
		String inputStreamId2 = "inputStream2_" + RandomStringUtils.randomAlphanumeric(5);
		String outputStreamId = "outputStream_overlay_" + RandomStringUtils.randomAlphanumeric(5);

		try {
			FilterConfiguration filterConfig = new FilterConfiguration();
			filterConfig.setFilterId("overlayFilter_" + RandomStringUtils.randomAlphanumeric(5));
			filterConfig.setInputStreams(List.of(inputStreamId1, inputStreamId2));
			filterConfig.setOutputStreams(List.of(outputStreamId));
			filterConfig.setVideoFilter("[in0]scale=240:160[s0];[in1][s0]overlay[out0]");
			filterConfig.setAudioFilter("[in0][in1]amix=inputs=2[out0]");
			filterConfig.setType(FilterConfiguration.ASYNCHRONOUS);
			filterConfig.setVideoEnabled(true);
			filterConfig.setAudioEnabled(true);
            filterConfig.setVideoOutputHeight(360);

			// Start publishing to input stream 1
			Process rtmpSendingProcess1 = execute(ffmpegPath
					+ " -re -i src/test/resources/test_video_360p.flv -codec copy -f flv rtmp://localhost/LiveApp/"
					+ inputStreamId1);
			ffmpegProcesses.add(rtmpSendingProcess1);

			// Start publishing to input stream 2
			Process rtmpSendingProcess2 = execute(ffmpegPath
					+ " -re -i src/test/resources/test_video_360p.flv -codec copy -f flv rtmp://localhost/LiveApp/"
					+ inputStreamId2);
			ffmpegProcesses.add(rtmpSendingProcess2);

			// Check that input stream 1 is publishing
			Awaitility.await().atMost(15, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						Broadcast broadcast = getBroadcast(inputStreamId1);
						return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
					});
			logger.info("Input stream {} is broadcasting.", inputStreamId1);

			// Check that input stream 2 is publishing
			Awaitility.await().atMost(15, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						Broadcast broadcast = getBroadcast(inputStreamId2);
						return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
					});
			logger.info("Input stream {} is broadcasting.", inputStreamId2);

			Result creationResult = callCreateFilter(filterConfig);
			assertTrue("Filter creation failed: " + creationResult.getMessage(), creationResult.isSuccess());
			String createdFilterId = creationResult.getDataId();
			assertNotNull(createdFilterId);

			// Check that the output stream is also broadcasting and playable
			Awaitility.await().atMost(20, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						Broadcast broadcast = getBroadcast(outputStreamId);
						logger.info("Output stream {} status: {}", outputStreamId, broadcast != null ? broadcast.getStatus() : "null");
						return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
					});

			Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> {
				// adaptive m3u8 can be ..., streamId_adaptive.m3u8 or streamId.m3u8
				// let's check streamId.m3u8 first then streamId_adaptive.m3u8
				boolean playable = testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId + ".m3u8", 0, false);
				if (!playable) {
					playable = testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId + "_adaptive.m3u8", 0, false);
				}
				return playable;
			});

			// The output resolution should be the resolution of the base stream (in1), which is 640x360
			StreamResolution resolution = getStreamResolution("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId + "_adaptive.m3u8");
			if (resolution == null) {
				// Fallback if adaptive is not immediately available or if primary manifest is the one with resolution
				resolution = getStreamResolution("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId + ".m3u8");
			}

			assertNotNull("Could not get resolution for output stream: " + outputStreamId, resolution);
			assertEquals("Output stream height does not match base stream (in1).", 360, resolution.height);
			logger.info("Verified output stream resolution for overlay: {}x{}", resolution.width, resolution.height);

			// Cleanup: delete the filter
			Result deleteResult = callDeleteFilter(createdFilterId);
			assertTrue("Filter deletion failed: " + deleteResult.getMessage(), deleteResult.isSuccess());
		} finally {
			for (Process proc : ffmpegProcesses) {
				if (proc != null && proc.isAlive()) {
					proc.destroy();
					proc.waitFor(5, TimeUnit.SECONDS); // Wait for graceful shutdown
					if (proc.isAlive()) {
						proc.destroyForcibly();
					}
				}
			}
		}
	}

	@Test
	public void testSplitFilterAndPublish() throws Exception {
		List<Process> ffmpegProcesses = new java.util.ArrayList<>();
		String inputStreamId = "inputStream_split_" + RandomStringUtils.randomAlphanumeric(5);
		String outputStreamId1 = "outputStream_split1_" + RandomStringUtils.randomAlphanumeric(5);
		String outputStreamId2 = "outputStream_split2_" + RandomStringUtils.randomAlphanumeric(5);

		try {
			FilterConfiguration filterConfig = new FilterConfiguration();
			filterConfig.setFilterId("splitFilter_" + RandomStringUtils.randomAlphanumeric(5));
			filterConfig.setInputStreams(List.of(inputStreamId));
			filterConfig.setOutputStreams(List.of(outputStreamId1, outputStreamId2));
			filterConfig.setVideoFilter("[in0]split[out0][out1]");
			filterConfig.setAudioFilter("[in0]asplit=2[out0][out1]");
			filterConfig.setType(FilterConfiguration.ASYNCHRONOUS);
			filterConfig.setVideoEnabled(true);
			filterConfig.setAudioEnabled(true);
			filterConfig.setVideoOutputHeight(360);


			// Start publishing to input stream
			Process rtmpSendingProcess = execute(ffmpegPath
					+ " -re -i src/test/resources/test_video_360p.flv -codec copy -f flv rtmp://localhost/LiveApp/"
					+ inputStreamId);
			ffmpegProcesses.add(rtmpSendingProcess);

			// Check that input stream is publishing
			Awaitility.await().atMost(15, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						Broadcast broadcast = getBroadcast(inputStreamId);
						return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
					});
			logger.info("Input stream {} is broadcasting.", inputStreamId);

			Result creationResult = callCreateFilter(filterConfig);
			assertTrue("Filter creation failed: " + creationResult.getMessage(), creationResult.isSuccess());
			String createdFilterId = creationResult.getDataId();
			assertNotNull(createdFilterId);

			// Check that output stream 1 is broadcasting and playable
			Awaitility.await().atMost(20, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						Broadcast broadcast = getBroadcast(outputStreamId1);
						logger.info("Output stream 1 ({}) status: {}", outputStreamId1, broadcast != null ? broadcast.getStatus() : "null");
						return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
					});

			Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> {
				return testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId1 + "_adaptive.m3u8", 0, false);
			});
			logger.info("Output stream 1 ({}) is playable.", outputStreamId1);

			// Check that output stream 2 is broadcasting and playable
			Awaitility.await().atMost(20, TimeUnit.SECONDS)
					.pollInterval(1, TimeUnit.SECONDS)
					.until(() -> {
						Broadcast broadcast = getBroadcast(outputStreamId2);
						logger.info("Output stream 2 ({}) status: {}", outputStreamId2, broadcast != null ? broadcast.getStatus() : "null");
						return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
					});

			Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> {
				return testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId2 + "_adaptive.m3u8", 0, false);
			});
			logger.info("Output stream 2 ({}) is playable.", outputStreamId2);

			// Verify resolutions of output streams (should match the input 640x360 or the specified videoOutputHeight if scaling occurs)
			// The input test_video_360p.flv is 640x360.
			StreamResolution resolution1 = getStreamResolution("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId1 + "_adaptive.m3u8");
			assertNotNull("Could not get resolution for output stream 1: " + outputStreamId1, resolution1);
			assertEquals("Output stream 1 height does not match expected.", 360, resolution1.height);
			// Width can vary with adaptive streaming, height is more reliable for basic check without specific scaling.
			// assertEquals("Output stream 1 width does not match expected.", 640, resolution1.width);
			logger.info("Verified output stream 1 resolution: {}x{}", resolution1.width, resolution1.height);

			StreamResolution resolution2 = getStreamResolution("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + outputStreamId2 + "_adaptive.m3u8");
			assertNotNull("Could not get resolution for output stream 2: " + outputStreamId2, resolution2);
			assertEquals("Output stream 2 height does not match expected.", 360, resolution2.height);
			// assertEquals("Output stream 2 width does not match expected.", 640, resolution2.width);
			logger.info("Verified output stream 2 resolution: {}x{}", resolution2.width, resolution2.height);

			// Cleanup: delete the filter
			Result deleteResult = callDeleteFilter(createdFilterId);
			assertTrue("Filter deletion failed: " + deleteResult.getMessage(), deleteResult.isSuccess());
		} finally {
			for (Process proc : ffmpegProcesses) {
				if (proc != null && proc.isAlive()) {
					proc.destroy();
					proc.waitFor(5, TimeUnit.SECONDS); // Wait for graceful shutdown
					if (proc.isAlive()) {
						proc.destroyForcibly();
					}
				}
			}
		}
	}

	public static boolean testFile(String absolutePath, int expectedDurationInMS, boolean fullRead) {
		int ret;
		audioExists = false;
		videoExists = false;
		logger.info("Tested File: {}", absolutePath);

		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();
		if (inputFormatContext == null) {
			System.out.println("cannot allocate input context");
			return false;
		}

		if ((ret = avformat_open_input(inputFormatContext, absolutePath, null, (AVDictionary) null)) < 0) {
			System.out.println("cannot open input context: " + absolutePath);
			return false;
		}


		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
		if (ret < 0) {
			System.out.println("Could not find stream information\n");
			return false;
		}

		int streamCount = inputFormatContext.nb_streams();
		if (streamCount == 0) {
			return false;
		}

		boolean streamExists = false;
		for (int i = 0; i < streamCount; i++) {
			AVCodecParameters codecpar = inputFormatContext.streams(i).codecpar();

			if (codecpar.codec_type() == AVMEDIA_TYPE_VIDEO)
			{
				assertTrue(codecpar.width() != 0);
				assertTrue(codecpar.height() != 0);
				assertTrue(codecpar.format() != AV_PIX_FMT_NONE);
				// videoStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS); // Commenting out unused variable

				// videoDuration = av_rescale_q(inputFormatContext.streams(i).duration(),  inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS); // Commenting out unused variable


				videoExists = true;
				streamExists = true;
			} else if (codecpar.codec_type() == AVMEDIA_TYPE_AUDIO)
			{
				assertTrue(codecpar.sample_rate() != 0);
				// audioStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS); // Commenting out unused variable

				// audioDuration = av_rescale_q(inputFormatContext.streams(i).duration(),  inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS); // Commenting out unused variable
				audioExists = true;
				streamExists = true;
			}
		}
		if (!streamExists) {
			return streamExists;
		}

		int i = 0;
		while (fullRead || i < 3) {
			AVPacket pkt = new AVPacket();
			ret = av_read_frame(inputFormatContext, pkt);

			if (ret < 0) {
				break;

			}
			i++;
			avcodec.av_packet_unref(pkt);
			pkt.close();
			pkt = null;
		}

		if (inputFormatContext.duration() != AV_NOPTS_VALUE) {
			long durationInMS = inputFormatContext.duration() / 1000;

			if (expectedDurationInMS != 0) {
				if ((durationInMS < (expectedDurationInMS - 4000)) || (durationInMS > (expectedDurationInMS + 4000))) {
					System.out.println("Failed: duration of the stream: " + durationInMS + " expected duration is: " + expectedDurationInMS);
					return false;
				}
			}
		}

		avformat_close_input(inputFormatContext);
		return true;

	}


	public static Process execute(final String command) {
		tmpExec = null;
		new Thread() {
			public void run() {
				try {

					tmpExec = Runtime.getRuntime().exec(command);
					InputStream errorStream = tmpExec.getErrorStream();
					byte[] data = new byte[1024];
					int length = 0;

					while ((length = errorStream.read(data, 0, data.length)) > 0) {
						logger.info(new String(data, 0, length));
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			};
		}.start();

		while (tmpExec == null) {
			try {
				System.out.println("Waiting for exec get initialized...");
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		tmpExec.onExit().thenAccept(proc -> {
			logger.info("Command({}) is exited with status: {}", command, proc.exitValue());
		});


		return tmpExec;
	}

	public static Broadcast getBroadcast(String streamId) {
		try {
			/// get broadcast
			String url = ROOT_SERVICE_URL + "/v2/broadcasts/"+streamId;

			CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
			Gson gson = new Gson();


			HttpUriRequest get = RequestBuilder.get().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					// .setEntity(new StringEntity(gson.toJson(broadcast)))
					.build();

			CloseableHttpResponse response = client.execute(get);

			StringBuffer result = readResponse(response);

			if (response.getStatusLine().getStatusCode() == 404) {
				//stream is not found
				logger.info("Response to getBroadcast is 404. It means stream is not found or deleted");
				return null;
			}
			else if (response.getStatusLine().getStatusCode() != 200){
				throw new Exception("Status code not 200 ");
			}
			System.out.println("result string: " + result.toString());
			assertFalse(result.toString().contains("dbId"));
			Broadcast tmp2 = gson.fromJson(result.toString(), Broadcast.class);
			return tmp2;
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;
	}

	public static StringBuffer readResponse(HttpResponse response) throws IOException {
		StringBuffer result = new StringBuffer();

		if(response.getEntity() != null) {
			BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

			String line = "";
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
		}
		return result;
	}

	public static AppSettings callGetAppSettings(String appName) throws Exception {
		String rootUrl = "http://" + InetAddress.getLocalHost().getHostAddress() + ":5080/rest/v2";

		String url = rootUrl + "/applications/settings/" + appName;

		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build();
		Gson gson = new Gson();

		HttpUriRequest post = RequestBuilder.get().setUri(url).build();

		HttpResponse response = client.execute(post);

		StringBuffer result = readResponse(response);

		if (response.getStatusLine().getStatusCode() != 200) {
			System.out.println("status code: " + response.getStatusLine().getStatusCode());
			throw new Exception(result.toString());
		}
		logger.info("result string: " + result.toString());
		AppSettings tmp = gson.fromJson(result.toString(), AppSettings.class);
		assertNotNull(tmp);
		return tmp;
	}

	public static Result callisFirstLogin() throws Exception {
		String rootUrl = "http://" + InetAddress.getLocalHost().getHostAddress() + ":5080/rest/v2";

		String url = rootUrl + "/first-login-status";
		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
		Gson gson = new Gson();
		HttpUriRequest post = RequestBuilder.get().setUri(url).setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.build();

		HttpResponse response = client.execute(post);

		StringBuffer result = readResponse(response);

		if (response.getStatusLine().getStatusCode() != 200) {
			throw new Exception(result.toString());
		}
		logger.info("result string: " + result.toString());
		Result tmp = gson.fromJson(result.toString(), Result.class);
		assertNotNull(tmp);
		return tmp;

	}

	public static Result callCreateInitialUser(User user) throws Exception {
		String rootUrl = "http://" + InetAddress.getLocalHost().getHostAddress() + ":5080/rest/v2";


		String url = rootUrl + "/users/initial";
		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
		Gson gson = new Gson();
		HttpUriRequest post = RequestBuilder.post().setUri(url).setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setEntity(new StringEntity(gson.toJson(user))).build();

		HttpResponse response = client.execute(post);

		StringBuffer result = readResponse(response);

		if (response.getStatusLine().getStatusCode() != 200) {
			throw new Exception(result.toString());
		}
		logger.info("result string: " + result.toString());
		Result tmp = gson.fromJson(result.toString(), Result.class);
		assertNotNull(tmp);
		return tmp;
	}

	private static Result callAuthenticateUser(User user) throws Exception {
		String rootUrl = "http://" + InetAddress.getLocalHost().getHostAddress() + ":5080/rest/v2";

		String url = rootUrl + "/users/authenticate";
		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build();
		Gson gson = new Gson();
		HttpUriRequest post = RequestBuilder.post().setUri(url).setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setEntity(new StringEntity(gson.toJson(user))).build();

		HttpResponse response = client.execute(post);

		StringBuffer result = readResponse(response);

		if (response.getStatusLine().getStatusCode() != 200) {
			return new Result(false);
		}
		logger.info("result string: " + result.toString());
		Result tmp = gson.fromJson(result.toString(), Result.class);
		assertNotNull(tmp);
		return tmp;
	}

	public static Result callSetAppSettings(String appName, AppSettings appSettingsModel) throws Exception {
		String rootUrl = "http://" + InetAddress.getLocalHost().getHostAddress() + ":5080/rest/v2";

		String url = rootUrl + "/applications/settings/" + appName;
		try (CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build())
		{
			Gson gson = new Gson();

			HttpUriRequest post = RequestBuilder.post().setUri(url).setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setEntity(new StringEntity(gson.toJson(appSettingsModel))).build();

			try (CloseableHttpResponse response = client.execute(post)) {

				StringBuffer result = readResponse(response);

				if (response.getStatusLine().getStatusCode() != 200) {
					return new Result(false);
				}
				logger.info("result string: " + result.toString());
				Result tmp = gson.fromJson(result.toString(), Result.class);
				assertNotNull(tmp);
				return tmp;
			}catch (Exception e){
				e.printStackTrace();
			}
			return new Result(false);
		}

	}


	public static boolean callStartPeriodicRecording(int seconds) throws Exception {
		String url = ROOT_SERVICE_URL + "/clip-creator/periodic-recording/" + seconds;

		try (CloseableHttpClient client = HttpClients.custom()
				.setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore)
				.build()) {

			HttpUriRequest post = RequestBuilder.post()
					.setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.build();

			try (CloseableHttpResponse response = client.execute(post)) {

				if (response.getStatusLine().getStatusCode() != 200) {
					throw new RuntimeException("Failed to execute REST method " + response.getStatusLine().getStatusCode());
				}


				return response.getStatusLine().getStatusCode() == 200;

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Error while downloading MP4 file", e);
			}
		}
	}


	public static boolean callStopPeriodicRecording() throws Exception {
		String url = ROOT_SERVICE_URL + "/clip-creator/periodic-recording";

		try (CloseableHttpClient client = HttpClients.custom()
				.setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore)
				.build()) {

			HttpUriRequest post = RequestBuilder.delete()
					.setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.build();

			try (CloseableHttpResponse response = client.execute(post)) {

				if (response.getStatusLine().getStatusCode() != 200) {
					throw new RuntimeException("Failed to execute REST method " + response.getStatusLine().getStatusCode());
				}


				return response.getStatusLine().getStatusCode() == 200;

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Error while downloading MP4 file", e);
			}
		}
	}

	public static Result callDeleteMp4NotInDB() throws IOException 
	{
		String url = ROOT_SERVICE_URL + "/clip-creator/mp4-not-in-db";


		try (CloseableHttpClient client = HttpClients.custom()
				.setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore)
				.build()) 
		{

			HttpUriRequest delete = RequestBuilder.delete()
					.setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.build();

			try (CloseableHttpResponse response = client.execute(delete)) {

				StringBuffer result = readResponse(response);
				logger.info("delete mp4 not in db result: " + result.toString());
				Gson gson = new Gson();
				return gson.fromJson(result.toString(), Result.class);


			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Error while downloading MP4 file", e);
			}
		}


	}

	public static File callCreateMp4(String streamId) throws Exception {
		String url = ROOT_SERVICE_URL + "/clip-creator/mp4/" + streamId + "?returnFile=true";

		try (CloseableHttpClient client = HttpClients.custom()
				.setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore)
				.build()) {

			HttpUriRequest post = RequestBuilder.post()
					.setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.build();

			try (CloseableHttpResponse response = client.execute(post)) {

				if (response.getStatusLine().getStatusCode() != 200) {
					throw new RuntimeException("Failed to create MP4: " + response.getStatusLine().getStatusCode());
				}

				File tmpFile = File.createTempFile("stream_" + streamId, ".mp4");

				try (InputStream inputStream = response.getEntity().getContent();
						FileOutputStream fos = new FileOutputStream(tmpFile)) {

					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = inputStream.read(buffer)) != -1) {
						fos.write(buffer, 0, bytesRead);
					}
				}

				logger.info("MP4 file saved at: " + tmpFile.getAbsolutePath());

				return tmpFile;

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Error while downloading MP4 file", e);
			}
		}
	}

	public static Result callDeleteVodBulk(String vodIds) throws Exception {
		String url = ROOT_SERVICE_URL + "/v2/vods?ids="+ vodIds ;
		try (CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build())
		{
			Gson gson = new Gson();

			HttpUriRequest delete = RequestBuilder.delete().setUri(url).setHeader(HttpHeaders.CONTENT_TYPE, "application/json").build();

			try (CloseableHttpResponse response = client.execute(delete)) {

				StringBuffer result = readResponse(response);

				if (response.getStatusLine().getStatusCode() != 200) {
					return new Result(false);
				}
				logger.info("result string: " + result.toString());
				Result tmp = gson.fromJson(result.toString(), Result.class);
				assertNotNull(tmp);
				return tmp;
			}catch (Exception e){
				e.printStackTrace();
			}
			return new Result(false);
		}

	}




	public static List<VoD> callGetVoDList(int offset, int size, String streamId) {
		try {

			String url = ROOT_SERVICE_URL + "/v2/vods/list/"+offset+"/" + size + "?sort_by=date&order_by=asc" + (streamId != null ? "&streamId=" : "");

			CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();

			HttpUriRequest get = RequestBuilder.get().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					// .setEntity(new StringEntity(gson.toJson(broadcast)))
					.build();

			CloseableHttpResponse response = client.execute(get);

			StringBuffer result = readResponse(response);

			if (response.getStatusLine().getStatusCode() != 200) {
				throw new Exception(result.toString());
			}
			System.out.println("Get vod list string: " + result.toString());
			Type listType = new TypeToken<List<VoD>>() {
			}.getType();
			Gson gson = new Gson();
			return gson.fromJson(result.toString(), listType);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Result callUpdateBroadcast(String id, String name, String description, String lat, String longitude, String altidue, String metadata) {
		String url = ROOT_SERVICE_URL + "/v2/broadcasts/" + id;

		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
		BroadcastUpdate broadcast = new BroadcastUpdate();
		try {
			broadcast.setStreamId(id);
		} catch (Exception e1) {
			e1.printStackTrace();
			fail(e1.getMessage());
		}
		broadcast.setName(name);
		broadcast.setDescription(description);
		broadcast.setLatitude(lat);
		broadcast.setLongitude(longitude);
		broadcast.setAltitude(altidue);
		broadcast.setMetaData(metadata);


		try {
			Gson gson = new Gson();
			HttpUriRequest put = RequestBuilder.put().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setEntity(new StringEntity(gson.toJson(broadcast))).build();

			HttpResponse response = client.execute(put);

			StringBuffer result = readResponse(response);

			if (response.getStatusLine().getStatusCode() != 200) {
				throw new Exception(result.toString());
			}
			System.out.println("result string: " + result.toString());
			Result tmp = gson.fromJson(result.toString(), Result.class);

			return tmp;
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;
	}

	public static Broadcast callCreateBroadcast(Broadcast broadcast) throws Exception {

		String url = ROOT_SERVICE_URL + "/v2/broadcasts/create";

		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();
		Gson gson = new Gson();

		HttpUriRequest post = RequestBuilder.post().setUri(url).setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setEntity(new StringEntity(gson.toJson(broadcast))).build();

		HttpResponse response = client.execute(post);

		StringBuffer result = readResponse(response);

		if (response.getStatusLine().getStatusCode() != 200) {
			throw new Exception(result.toString());
		}
		System.out.println("result string: " + result.toString());
		Broadcast tmp = gson.fromJson(result.toString(), Broadcast.class);
		assertNotNull(tmp);
		assertNotSame(0L, tmp.getDate());

		return tmp;

	}

	// FilterPlugin specific REST call methods
	public static Result callCreateFilter(FilterConfiguration filterConfiguration) throws Exception {
		String url = ROOT_SERVICE_URL + "/v2/filters/create";
		try (CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build()) {
			Gson gson = new Gson();
			HttpUriRequest post = RequestBuilder.post().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setEntity(new StringEntity(gson.toJson(filterConfiguration))).build();

			try (CloseableHttpResponse response = client.execute(post)) {
				StringBuffer result = readResponse(response);
				logger.info("Create filter response: {} status: {}", result.toString(), response.getStatusLine().getStatusCode());
				Result tmp = gson.fromJson(result.toString(), Result.class);
				if (response.getStatusLine().getStatusCode() != 200) {
					tmp.setSuccess(false);
				}
				return tmp;
			}
		}
	}

	public static Result callGetFilter(String filterId) throws Exception {
		String url = ROOT_SERVICE_URL + "/v2/filters/" + filterId;
		try (CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build()) {
			HttpUriRequest get = RequestBuilder.get().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json").build();

			try (CloseableHttpResponse response = client.execute(get)) {
				StringBuffer result = readResponse(response);
				logger.info("Get filter response: {} status: {}", result.toString(), response.getStatusLine().getStatusCode());
				Gson gson = new Gson();
				Result tmp = gson.fromJson(result.toString(), Result.class);
				if (response.getStatusLine().getStatusCode() != 200) {
					tmp.setSuccess(false);
				}
				return tmp;
			}
		}
	}

	public static Result callDeleteFilter(String filterId) throws Exception {
		String url = ROOT_SERVICE_URL + "/v2/filters/" + filterId;
		try (CloseableHttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(httpCookieStore).build()) {
			HttpUriRequest delete = RequestBuilder.delete().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json").build();

			try (CloseableHttpResponse response = client.execute(delete)) {
				StringBuffer result = readResponse(response);
				logger.info("Delete filter response: {} status: {}", result.toString(), response.getStatusLine().getStatusCode());
				Gson gson = new Gson();
				Result tmp = gson.fromJson(result.toString(), Result.class);
				if (response.getStatusLine().getStatusCode() != 200) {
					tmp.setSuccess(false);
				}
				return tmp;
			}
		}
	}

	// Helper class for stream resolution
	private static class StreamResolution {
		int width;
		int height;

		public StreamResolution(int width, int height) {
			this.width = width;
			this.height = height;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			StreamResolution that = (StreamResolution) obj;
			return width == that.width && height == that.height;
		}

		@Override
		public int hashCode() {
			return 31 * width + height;
		}

		@Override
		public String toString() {
			return "StreamResolution{" +
					"width=" + width +
					", height=" + height +
					'}';
		}
	}

	// Utility method to get stream resolution using ffprobe
	public static StreamResolution getStreamResolution(String streamUrl) {
		try {
			String command = String.format("%s -v quiet -print_format json -show_streams %s", ffprobePath, streamUrl);
			Process process = Runtime.getRuntime().exec(command);
			
			StringBuilder output = new StringBuilder();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line);
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				logger.error("ffprobe command failed with exit code {} for URL: {}. Output: {}", exitCode, streamUrl, output.toString());
				// Read error stream for more details
				StringBuilder errorOutput = new StringBuilder();
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				while ((line = errorReader.readLine()) != null) {
					errorOutput.append(line);
				}
				logger.error("ffprobe error output: {}", errorOutput.toString());
				return null;
			}

			Gson gson = new Gson();
			// Parse the JSON output to find video stream dimensions
			// This is a simplified parsing structure; might need to be more robust
			// Example ffprobe JSON: { "streams": [ { "codec_type": "video", "width": 1280, "height": 720 }, ... ] }
			com.google.gson.JsonObject jsonObject = gson.fromJson(output.toString(), com.google.gson.JsonObject.class);
			com.google.gson.JsonArray streams = jsonObject.getAsJsonArray("streams");
			for (com.google.gson.JsonElement streamElement : streams) {
				com.google.gson.JsonObject stream = streamElement.getAsJsonObject();
				if (stream.has("codec_type") && "video".equals(stream.get("codec_type").getAsString())) {
					int width = stream.get("width").getAsInt();
					int height = stream.get("height").getAsInt();
					return new StreamResolution(width, height);
				}
			}
		} catch (IOException | InterruptedException e) {
			logger.error("Error getting stream resolution for URL: " + streamUrl, e);
			Thread.currentThread().interrupt(); // Restore interruption status
		}
		return null; // Return null if resolution can't be determined
	}

} 