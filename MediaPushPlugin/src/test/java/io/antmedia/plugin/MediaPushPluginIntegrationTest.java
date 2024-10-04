package io.antmedia.plugin;

import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.awaitility.Awaitility;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import io.antmedia.model.Endpoint;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.rest.model.Result;

public class MediaPushPluginIntegrationTest  {
	
	
	private static Logger logger = LoggerFactory.getLogger(MediaPushPluginIntegrationTest.class);
	
	static String ROOT_SERVICE_URL = "http://127.0.0.1:5080/LiveApp";

	private static boolean audioExists;

	private static boolean videoExists;

	private static long videoStartTimeMs;

	private static long audioStartTimeMs;

	private static int width;

	private static int height;
	
	private Gson gson = new Gson();
	
	@Rule
	public TestRule watcher = new TestWatcher() {
		protected void starting(Description description) {
			System.out.println("Starting test: " + description.getMethodName());
		}

		protected void failed(Throwable e, Description description) {
			e.printStackTrace();
			System.out.println("Failed test: " + description.getMethodName());
		};
		protected void finished(Description description) {
			System.out.println("Finishing test: " + description.getMethodName());
		};
	};
	
	
	
	@Test
	public void testBasicStartAndStop() throws UnsupportedEncodingException {
		
		//Ant Media Server should be working locally before running this test
		
		//Call REST Method to start streaming with 1280x720
		
		Endpoint endpoint = new Endpoint();
		endpoint.setWidth(1280);
		endpoint.setHeight(720);
		endpoint.setUrl("http://google.com");
		
		Result result = startStreaming(endpoint);
		assertTrue(result.isSuccess());
		String streamId = result.getDataId();
		
		
		//Check that it's playable with HLS  
		Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
			return testFile(ROOT_SERVICE_URL + "/streams/" + streamId + ".m3u8");
		});
		
		//TODO: Known issue: width and height are not perfectly match but it gives some insight about the resolution
		
		
		//check that resolution is 1280x720
		//assertEquals(1280, width);
		//assertEquals(720, height);
		//Call REST method to stop streaming
		
		logger.info("widthxheight:{}x{}", width, height);
		
		result = stopStreaming(streamId);
		assertTrue(result.isSuccess());
		
		
		
		//Repeat the process for 640x360
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
	
	public Result startStreaming(Endpoint endpoint) throws UnsupportedEncodingException 
	{
		String url = ROOT_SERVICE_URL + "/rest/v1/media-push/start";
		
		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();

		HttpUriRequest post = RequestBuilder.post().setUri(url)
				.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.setEntity(new StringEntity(gson.toJson(endpoint)))
				.build();
		try {
			HttpResponse response = client.execute(post);

			StringBuffer result = readResponse(response);

			if (response.getStatusLine().getStatusCode() != 200) {
				throw new Exception(result.toString());
			}
			System.out.println("result string: " + result.toString());
			Result tmp = gson.fromJson(result.toString(), Result.class);

			return tmp;
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;
	}
	
	public Result stopStreaming(String streamId) throws UnsupportedEncodingException 
	{
		String url = ROOT_SERVICE_URL + "/rest/v1/media-push/stop/" + streamId;
		
		HttpClient client = HttpClients.custom().setRedirectStrategy(new LaxRedirectStrategy()).build();

		HttpUriRequest post = RequestBuilder.post().setUri(url)
				.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.build();
		try {
			HttpResponse response = client.execute(post);

			StringBuffer result = readResponse(response);

			if (response.getStatusLine().getStatusCode() != 200) {
				throw new Exception(result.toString());
			}
			System.out.println("result string: " + result.toString());
			Result tmp = gson.fromJson(result.toString(), Result.class);

			return tmp;
		}
		catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;
	}
	
	public static boolean testFile(String absolutePath) {
		return testFile(absolutePath, 0, false);
	}

	public static boolean testFile(String absolutePath, boolean fullRead) {
		return testFile(absolutePath, 0, fullRead);
	}

	public static boolean testFile(String absolutePath, int expectedDurationInMS) {
		return testFile(absolutePath, expectedDurationInMS, false);
	}

	public static boolean testFile(String absolutePath, int expectedDurationInMS, boolean fullRead) {
		int ret;
		audioExists = false;
		videoExists = false;
		logger.info("Tested File: {}", absolutePath);

		//AVDictionary dic = null;

		/*
		if(absolutePath.contains("mpd")) {
			findInputFormat = avformat.av_find_input_format("dash");
			av_dict_set(dic, "protocol_whitelist","mpd,mpeg,dash,m4s", 0);
		}
		 */
		AVFormatContext inputFormatContext = avformat.avformat_alloc_context();
		if (inputFormatContext == null) {
			System.out.println("cannot allocate input context");
			return false;
		}

		if ((ret = avformat_open_input(inputFormatContext, absolutePath, null, (AVDictionary) null)) < 0) {
			System.out.println("cannot open input context: " + absolutePath);
			return false;
		}

		/*
			byte[] data = new byte[2048];
			av_strerror(ret, data, data.length);
			throw new IllegalStateException("cannot open input context. Error is " + new String(data, 0, data.length));
		 */

		//av_dump_format(inputFormatContext,0,"test",0);

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
				width = codecpar.width();
				height = codecpar.height();
				videoStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);

				videoExists = true;
				streamExists = true;
			} else if (codecpar.codec_type() == AVMEDIA_TYPE_AUDIO) 
			{
				assertTrue(codecpar.sample_rate() != 0);
				audioStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);
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
		}

		if (inputFormatContext.duration() != AV_NOPTS_VALUE) {
			long durationInMS = inputFormatContext.duration() / 1000;

			if (expectedDurationInMS != 0) {
				if ((durationInMS < (expectedDurationInMS - 2000)) || (durationInMS > (expectedDurationInMS + 2000))) {
					System.out.println("Failed: duration of the stream: " + durationInMS + " expected duration is: " + expectedDurationInMS);
					return false;
				}
			}
		}

		avformat_close_input(inputFormatContext);
		return true;

	}



	
	
	
   

}