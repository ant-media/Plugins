package io.antmedia.plugin.integration;

import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.awaitility.Awaitility;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.rest.BroadcastRestService.SimpleStat;
import io.antmedia.rest.model.Result;

public class HLSMergerPluginIntegrationTest {
	private static Process tmpExec;
	
	private String hdTestFilePath = "src/test/resources/test_video_720p.flv";
	private String sdTestFilePath = "src/test/resources/test_video_360p.flv";
	static String APP_URL = "http://127.0.0.1:5080/LiveApp";
	static String ROOT_SERVICE_URL = APP_URL+"/rest";


	@Test
	public void testMultiResolutionHLS() {
		try {

			String hdStreamId = "hdStream";
			String sdStreamId = "sdStream";


			Process hdStream = execute(
					"ffmpeg -re -i " 
							+ hdTestFilePath 
							+ " -c copy -f flv rtmp://127.0.0.1/LiveApp/"
							+ hdStreamId);


			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
				return null != getBroadcast(hdStreamId);
			}); 

			Process sdStream = execute(
					"ffmpeg -re -i " 
							+ sdTestFilePath 
							+ " -c copy -f flv rtmp://127.0.0.1/LiveApp/"
							+ sdStreamId);

			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
				return null != getBroadcast(sdStreamId);
			}); 

			String mergedStreamId = "mergedMultiResolutionStream";

			callMergeMultiResolutionStreams(hdStreamId, sdStreamId, mergedStreamId);

			Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
				return testFile(APP_URL+"/streams/" + mergedStreamId+ ".m3u8");
			});


			String content = getM3U8Content(APP_URL+"/streams/" + mergedStreamId+ ".m3u8");

			String[] lines = content.split("\n");
			
			hdStream.destroyForcibly();
			sdStream.destroyForcibly();
			
			
			assertTrue(lines[1].contains("RESOLUTION=1280x720"));
			assertTrue(lines[2].contains("hdStream.m3u8"));
			assertTrue(lines[3].contains("RESOLUTION=640x360"));
			assertTrue(lines[4].contains("sdStream.m3u8"));
			
			callStopMultiResolutionStream(mergedStreamId);

			Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
				return !testFile(APP_URL+"/streams/" + mergedStreamId+ ".m3u8");
			});

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}
	
	
	@Test
	public void testMultiAudioHLS() {
		try {

			String videoStreamId = "videoStream";
			String audioStream1Id = "audioStream1";
			String audioStream2Id = "audioStream2";


			Process videoStream = execute(
					"ffmpeg -re -i " 
							+ hdTestFilePath 
							+ " -an -c copy -f flv rtmp://127.0.0.1/LiveApp/"
							+ videoStreamId);


			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
				return null != getBroadcast(videoStreamId);
			}); 

			Process audioStream1 = execute(
					"ffmpeg -re -i " 
							+ sdTestFilePath 
							+ " -vn -c copy -f flv rtmp://127.0.0.1/LiveApp/"
							+ audioStream1Id);

			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
				return null != getBroadcast(audioStream1Id);
			});
			
			Process audioStream2 = execute(
					"ffmpeg -re -i " 
							+ sdTestFilePath 
							+ " -vn -c copy -f flv rtmp://127.0.0.1/LiveApp/"
							+ audioStream2Id);

			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
				return null != getBroadcast(audioStream2Id);
			});

			String mergedStreamId = "mergedMultiAudioStream";

			callMergeMultiAudioStreams(videoStreamId, audioStream1Id, audioStream2Id, mergedStreamId);

			Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
				return testFile(APP_URL+"/streams/" + mergedStreamId+ ".m3u8");
			});


			String content = getM3U8Content(APP_URL+"/streams/" + mergedStreamId+ ".m3u8");

			String[] lines = content.split("\n");
			
			videoStream.destroyForcibly();
			audioStream1.destroyForcibly();
			audioStream2.destroyForcibly();

			
			assertTrue(lines[1].equals("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"audioStream1\",DEFAULT=NO,AUTOSELECT=YES,URI=\"audioStream1.m3u8\""));
			assertTrue(lines[2].contains("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"audioStream2\",DEFAULT=NO,AUTOSELECT=YES,URI=\"audioStream2.m3u8\""));
			assertTrue(lines[3].contains("AUDIO=\"audio\""));
			assertTrue(lines[4].contains("videoStream.m3u8"));
			
			
			callStopMultiResolutionStream(mergedStreamId);

			Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
				return !testFile(APP_URL+"/streams/" + mergedStreamId+ ".m3u8");
			});

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

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
						System.out.println(new String(data, 0, length));
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

		return tmpExec;
	}
	
	
	private Result callStop(String mergedStreamId, String restMethod) {
		String url = ROOT_SERVICE_URL + "/v1/hls-merger/"+restMethod+"/" + mergedStreamId;

        HttpClient client = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();

        try {
            // Build the POST request
            HttpUriRequest delete = RequestBuilder.delete()
                    .setUri(url)
                    .setHeader("Content-Type", "application/json")
                    .build();

            // Execute the request
            HttpResponse response = client.execute(delete);

            // Read the response
            String result = EntityUtils.toString(response.getEntity());

            // Check HTTP status code
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new Exception("HTTP error code: " + response.getStatusLine().getStatusCode() + " - " + result);
            }

            // Deserialize the response JSON into Result object
            Gson gson = new Gson();
            return gson.fromJson(result, Result.class);

        } catch (Exception e) {
            e.printStackTrace();
            // Handle failure (replace 'fail' with appropriate handling if not in a test context)
            throw new RuntimeException("Failed to merge streams: " + e.getMessage(), e);
        }
		
	}
	
	private Result callStopMultiResolutionStream(String mergedStreamId) {
		return callStop(mergedStreamId, "multi-resolution-stream");
	}
	
	private Result callStopMultiAudioStream(String mergedStreamId) {
		return callStop(mergedStreamId, "multi-audio-stream");
	}
	
	public Result callMergeMultiResolutionStreams(String stream1, String stream2, String mergedStreamId) {
        String url = ROOT_SERVICE_URL + "/v1/hls-merger/multi-resolution-stream/" + mergedStreamId;

        HttpClient client = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();

        try {
            // Create JSON array using Gson
            JsonArray streamsArray = new JsonArray();
            streamsArray.add(stream1);
            streamsArray.add(stream2);

            String jsonPayload = new Gson().toJson(streamsArray);

            // Build the POST request
            HttpUriRequest post = RequestBuilder.post()
                    .setUri(url)
                    .setHeader("Content-Type", "application/json")
                    .setEntity(new StringEntity(jsonPayload))
                    .build();

            // Execute the request
            HttpResponse response = client.execute(post);

            // Read the response
            String result = EntityUtils.toString(response.getEntity());

            // Check HTTP status code
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new Exception("HTTP error code: " + response.getStatusLine().getStatusCode() + " - " + result);
            }

            // Deserialize the response JSON into Result object
            Gson gson = new Gson();
            return gson.fromJson(result, Result.class);

        } catch (Exception e) {
            e.printStackTrace();
            // Handle failure (replace 'fail' with appropriate handling if not in a test context)
            throw new RuntimeException("Failed to merge streams: " + e.getMessage(), e);
        }
    }
	
	public Result callMergeMultiAudioStreams(String videoStreamId, String audioStreamId1, String audioStreamId2, String mergedStreamId) {
        String url = ROOT_SERVICE_URL + "/v1/hls-merger/"+videoStreamId+"/multi-audio-stream/" + mergedStreamId;

        HttpClient client = HttpClients.custom()
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();

        try {
            // Create JSON array using Gson
            JsonArray streamsArray = new JsonArray();
            streamsArray.add(audioStreamId1);
            streamsArray.add(audioStreamId2);

            String jsonPayload = new Gson().toJson(streamsArray);

            // Build the POST request
            HttpUriRequest post = RequestBuilder.post()
                    .setUri(url)
                    .setHeader("Content-Type", "application/json")
                    .setEntity(new StringEntity(jsonPayload))
                    .build();

            // Execute the request
            HttpResponse response = client.execute(post);

            // Read the response
            String result = EntityUtils.toString(response.getEntity());

            // Check HTTP status code
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new Exception("HTTP error code: " + response.getStatusLine().getStatusCode() + " - " + result);
            }

            // Deserialize the response JSON into Result object
            Gson gson = new Gson();
            return gson.fromJson(result, Result.class);

        } catch (Exception e) {
            e.printStackTrace();
            // Handle failure (replace 'fail' with appropriate handling if not in a test context)
            throw new RuntimeException("Failed to merge streams: " + e.getMessage(), e);
        }
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
				System.out.println("Response to getBroadcast is 404. It means stream is not found or deleted");
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
	
	private String getM3U8Content(String urlString) throws Exception {
		URL url = new URL(urlString);

        // Open a connection and create a BufferedReader
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

        // Read the URL content into a StringBuilder
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        // Close the reader
        reader.close();

        // Print the content
        System.out.println("URL Content:");
        System.out.println(content.toString());

        return content.toString();
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
		boolean audioExists = false;
		boolean videoExists = false;

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

				videoExists = true;
				streamExists = true;
			} else if (codecpar.codec_type() == AVMEDIA_TYPE_AUDIO) 
			{
				assertTrue(codecpar.sample_rate() != 0);
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
