package io.antmedia.test.integration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.User;
import io.antmedia.datastore.db.types.VoD;
import io.antmedia.datastore.db.types.Broadcast.PlayListItem;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;
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

public class ClipCreatorPluginIntegrationTest {

    public static String ffmpegPath = "/usr/bin/ffmpeg";
    public static String appName = "LiveApp";
    private Gson gson = new Gson();
	private Process rtmpSendingProcess;
    public static String ffprobePath = "/usr/bin/ffprobe";
    private static Process tmpExec;
    public static Logger logger = LoggerFactory.getLogger(ClipCreatorPluginIntegrationTest.class);
    //public static String ffmpegPath = "/usr/local/bin/ffmpeg";
    public static String SERVER_ADDR;
    public static int OS_TYPE;
    public static final int MAC_OS_X = 0;
    public static final int LINUX = 1;
    public static final int WINDOWS = 2;
    public static boolean audioExists;
    public static boolean videoExists;
    public static long videoDuration;
    public static long videoStartTimeMs;
    public static long audioStartTimeMs;
    public static long audioDuration;
    private static final String ROOT_SERVICE_URL = "http://localhost:5080/LiveApp/rest";


    private static String TEST_USER_EMAIL = "test@antmedia.io";
    private static String TEST_USER_PASS = "05a671c66aefea124cc08b76ea6d30bb";
    private static BasicCookieStore httpCookieStore;


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

      //delete all VoDs
    	List<VoD> currVoDList = callGetVoDList(0,50, null);
        while (!currVoDList.isEmpty())
        {
            String voDIds = currVoDList.stream()
                    .map(VoD::getVodId)
                    .collect(Collectors.joining(","));
            assertTrue(callDeleteVodBulk(voDIds).isSuccess());
            currVoDList = callGetVoDList(0,50, null);
        }
        
        
        currVoDList = callGetVoDList(0,50, null);
        assertTrue(currVoDList.isEmpty());

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
    public void testPeriodicNoMp4CreationIfDisabled() throws Exception {
    	 AppSettings appSettings = callGetAppSettings(appName);
    	 
    	 int mp4CreationIntervalSeconds = 20;

    	 //make sure it's disabled
    	 ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();
    	 clipCreatorSettings.setEnabled(false);
         clipCreatorSettings.setMp4CreationIntervalSeconds(mp4CreationIntervalSeconds);
    	 appSettings.getCustomSettings().put("plugin."+ClipCreatorPlugin.PLUGIN_KEY, clipCreatorSettings);
    	 
         assertTrue(callSetAppSettings(appName, appSettings).isSuccess());
         
         //send the stream
         String streamId = "testStream" + RandomStringUtils.randomAlphanumeric(5);
         rtmpSendingProcess = execute(ffmpegPath
                 + " -re -i src/test/resources/test_video_360p.flv  -codec copy -f flv rtmp://localhost/LiveApp/"
                 + streamId);

         //check that it's publishing
         Awaitility.await().atMost(15, TimeUnit.SECONDS)
                 .pollInterval(1, TimeUnit.SECONDS)
                 .until(() -> {
                     Broadcast broadcast = getBroadcast(streamId);
                     return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
                            
                 });

         //check that it's streaming
         Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
             return !testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + streamId + ".m3u8", 0, false);
         });
         
       
         //wait 25 seconds and check that there is no VoD because it's disabled
         Awaitility.await().atMost(40, TimeUnit.SECONDS).pollDelay(25, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
             List<VoD> voDList = callGetVoDList(0,50, streamId);
             logger.info("VoD List size: {}", voDList.size());
             return voDList != null && voDList.size() == 0;
         });
    	 
    }
    
    @Test
    public void testPeriodicMp4CreationREST() throws Exception {
    	 AppSettings appSettings = callGetAppSettings(appName);
    	 
    	 int mp4CreationIntervalSeconds = 20;

    	 //make sure it's disabled
    	 ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();
    	 clipCreatorSettings.setEnabled(false);
         clipCreatorSettings.setMp4CreationIntervalSeconds(mp4CreationIntervalSeconds);
    	 appSettings.getCustomSettings().put("plugin."+ClipCreatorPlugin.PLUGIN_KEY, clipCreatorSettings);
    	 
    	  appSettings.setHlsPlayListType("event");
    	 
         assertTrue(callSetAppSettings(appName, appSettings).isSuccess());
         
         //send the stream
         String streamId = "testStream" + RandomStringUtils.randomAlphanumeric(5);
         rtmpSendingProcess = execute(ffmpegPath
                 + " -re -i src/test/resources/test_video_360p.flv  -codec copy -f flv rtmp://localhost/LiveApp/"
                 + streamId);

         //check that it's publishing
         Awaitility.await().atMost(15, TimeUnit.SECONDS)
                 .pollInterval(1, TimeUnit.SECONDS)
                 .until(() -> {
                     Broadcast broadcast = getBroadcast(streamId);
                     return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
                            
                 });

         //check that it's streaming
         Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
             return !testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + streamId + ".m3u8", 0, false);
         });
         
         
        assertTrue(callStartPeriodicRecording(20));
       
        String name = "testStream";
        String description = "testDescription";
        String latidue = "41.0825";
        String longitude = "29.0093";
        String altitude = "100";
        String metadata = "metadata";
        
        Result result = callUpdateBroadcast(streamId,  name, description, latidue, longitude, altitude, metadata);
        assertTrue(result.isSuccess());
        
         //wait 25 seconds and check that there is no VoD because it's disabled
         Awaitility.await().atMost(40, TimeUnit.SECONDS).pollDelay(25, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
             List<VoD> voDList = callGetVoDList(0,50, streamId);
             logger.info("VoD List size: {}", voDList.size());
             return voDList != null && voDList.size() == 1;
         });
         
         List<VoD> voDList = callGetVoDList(0,50, streamId);
         VoD createdMp4VoD = voDList.get(0);
         
         long upperBoundary = mp4CreationIntervalSeconds * 1000 + 4000;
         long lowerBoundary = mp4CreationIntervalSeconds * 1000 - 4000;
         
         logger.info("duration:{} upperBoundary:{} lower boundary:{} ", createdMp4VoD.getDuration(), upperBoundary, lowerBoundary);

         assertEquals(createdMp4VoD.getStreamId(), streamId);
         assertTrue(createdMp4VoD.getDuration() > lowerBoundary && createdMp4VoD.getDuration() < upperBoundary);
         assertTrue(createdMp4VoD.getFileSize() > 0);
         assertTrue(createdMp4VoD.getStreamName().startsWith(name));
         assertEquals(description, createdMp4VoD.getDescription());
         assertEquals(latidue, createdMp4VoD.getLatitude());
         assertEquals(longitude, createdMp4VoD.getLongitude());
         assertEquals(altitude, createdMp4VoD.getAltitude());
         assertEquals(metadata, createdMp4VoD.getMetadata());
         
         
         
         name = "testStreamUpdate";
         description = "testDescriptiontestStreamUpdate";
         latidue = "41.0825testStreamUpdate";
         longitude = "29.0093testStreamUpdate";
         altitude = "100testStreamUpdate";
         metadata = "metadatatestStreamUpdate";
         
         result = callUpdateBroadcast(streamId,  name, description, latidue, longitude, altitude, metadata);
         assertTrue(result.isSuccess());
         
         assertTrue(callStopPeriodicRecording());
         
    
         Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
             List<VoD> voDList2 = callGetVoDList(0,50, streamId);
             return voDList2 != null && voDList2.size() == 2;
         });

         voDList = callGetVoDList(0,50, streamId);

         createdMp4VoD = voDList.get(1);
         
         assertEquals(createdMp4VoD.getStreamId(), streamId);
         assertTrue(createdMp4VoD.getFileSize() > 0);
         assertTrue(createdMp4VoD.getStreamName().startsWith(name));
         assertEquals(description, createdMp4VoD.getDescription());
         assertEquals(latidue, createdMp4VoD.getLatitude());
         assertEquals(longitude, createdMp4VoD.getLongitude());
         assertEquals(altitude, createdMp4VoD.getAltitude());
         assertEquals(metadata, createdMp4VoD.getMetadata());
         

    	 
    }

    @Test
    public void testPeriodicMp4CreationThroughAppSettings() throws Exception {
        
        //change settings to event
        AppSettings appSettings = callGetAppSettings(appName);
        appSettings.setHlsPlayListType("event");
        
        int mp4CreationIntervalSeconds = 20;

        ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();
        clipCreatorSettings.setMp4CreationIntervalSeconds(mp4CreationIntervalSeconds);
        clipCreatorSettings.setEnabled(true);
        appSettings.getCustomSettings().put("plugin."+ClipCreatorPlugin.PLUGIN_KEY, clipCreatorSettings);

        assertTrue(callSetAppSettings(appName, appSettings).isSuccess());
        

        //send the stream
        String streamId = "testStream" + RandomStringUtils.randomAlphanumeric(5);
        rtmpSendingProcess = execute(ffmpegPath
                + " -re -i src/test/resources/test_video_360p.flv  -codec copy -f flv rtmp://localhost/LiveApp/"
                + streamId);

        //check that it's publishing
        Awaitility.await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    Broadcast broadcast = getBroadcast(streamId);
                    return broadcast != null && IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus());
                           
                });

        //check that there is a playback url
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            return testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + streamId + ".m3u8", 0, false);
        });
        
        String name = "testStream";
        String description = "testDescription";
        String latidue = "41.0825";
        String longitude = "29.0093";
        String altitude = "100";
        String metadata = "metadata";
        
        Result result = callUpdateBroadcast(streamId,  name, description, latidue, longitude, altitude, metadata);
        assertTrue(result.isSuccess());

        Awaitility.await().atMost(40, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDList = callGetVoDList(0,50, streamId);
            logger.info("VoD List size: {}", voDList.size());
            return voDList != null && voDList.size() == 1;
        });

        List<VoD> voDList = callGetVoDList(0,50, streamId);

        VoD createdMp4VoD = voDList.get(0);
        long upperBoundary = (mp4CreationIntervalSeconds+5)*1000;
        long lowerBoundary = (mp4CreationIntervalSeconds-5)*1000;
        assertEquals(createdMp4VoD.getStreamId(), streamId);
        
        logger.info("duration:{} upperBoundary:{} lower boundary:{} ", createdMp4VoD.getDuration(), upperBoundary, lowerBoundary);
        assertTrue(createdMp4VoD.getDuration() > lowerBoundary && createdMp4VoD.getDuration() < upperBoundary);
        assertTrue(createdMp4VoD.getFileSize() > 0);
        assertTrue(createdMp4VoD.getStreamName().startsWith(name));
        assertEquals(description, createdMp4VoD.getDescription());
        assertEquals(latidue, createdMp4VoD.getLatitude());
        assertEquals(longitude, createdMp4VoD.getLongitude());
        assertEquals(altitude, createdMp4VoD.getAltitude());
        assertEquals(metadata, createdMp4VoD.getMetadata());
        
        name = "testStreamUpdate";
        description = "testDescriptiontestStreamUpdate";
        latidue = "41.0825testStreamUpdate";
        longitude = "29.0093testStreamUpdate";
        altitude = "100testStreamUpdate";
        metadata = "metadatatestStreamUpdate";
        
        result = callUpdateBroadcast(streamId,  name, description, latidue, longitude, altitude, metadata);
        assertTrue(result.isSuccess());


        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDList2 = callGetVoDList(0,50, streamId);
            return voDList2 != null && voDList2.size() == 2;
        });

        voDList = callGetVoDList(0,50, streamId);

        createdMp4VoD = voDList.get(1);

        assertEquals(createdMp4VoD.getStreamId(), streamId);
        assertTrue(createdMp4VoD.getDuration() > lowerBoundary && createdMp4VoD.getDuration() < upperBoundary);
        assertTrue(createdMp4VoD.getFileSize() > 0);
        assertTrue(createdMp4VoD.getStreamName().startsWith(name));
        assertEquals(description, createdMp4VoD.getDescription());
        assertEquals(latidue, createdMp4VoD.getLatitude());
        assertEquals(longitude, createdMp4VoD.getLongitude());
        assertEquals(altitude, createdMp4VoD.getAltitude());
        assertEquals(metadata, createdMp4VoD.getMetadata());

        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(10, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDList2 = callGetVoDList(0,50, streamId);
            return voDList2 != null && voDList2.size() == 3;
        });

        voDList = callGetVoDList(0,50, streamId);

        createdMp4VoD = voDList.get(2);

        assertEquals(createdMp4VoD.getStreamId(), streamId);
        assertTrue(createdMp4VoD.getDuration() > lowerBoundary && createdMp4VoD.getDuration() < upperBoundary);
        assertTrue(createdMp4VoD.getFileSize() > 0);

        long vodListSize = voDList.size();

        rtmpSendingProcess.destroy();
        
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(10, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDList2 = callGetVoDList(0,50, streamId);
            return voDList2 != null && voDList2.size() == vodListSize + 1;
        });
        
        
    }

    @Test
    public void testRestMp4Creation() throws Exception {
        int mp4CreationIntervalSeconds = 25;

        ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();
        clipCreatorSettings.setMp4CreationIntervalSeconds(mp4CreationIntervalSeconds);
        clipCreatorSettings.setEnabled(false);

        AppSettings appSettings = callGetAppSettings(appName);
        appSettings.setHlsPlayListType("event");
        appSettings.setCustomSetting("plugin."+ ClipCreatorPlugin.PLUGIN_KEY, gson.toJson(clipCreatorSettings, ClipCreatorSettings.class));

        assertTrue(callSetAppSettings(appName, appSettings).isSuccess());
        String streamId = "testStream" + RandomStringUtils.randomAlphanumeric(5);
        
        
        assertTrue(callStartPeriodicRecording(mp4CreationIntervalSeconds));
        

        Broadcast broadcast = new Broadcast();
        broadcast.setStreamId(streamId);
        
        String name = "testStream";
        String description = "testDescription";
        String latidue = "41.0825";
        String longitude = "29.0093";
        String altitude = "100";
        String metadata = "metadata";
        
        broadcast.setName(name);
        broadcast.setDescription(description);
        broadcast.setLatitude(latidue);
        broadcast.setLongitude(longitude);
        broadcast.setAltitude(altitude);
        broadcast.setMetaData(metadata);
        
        
        Broadcast callCreateBroadcast = callCreateBroadcast(broadcast);
        assertNotNull(callCreateBroadcast);

        
        rtmpSendingProcess = execute(ffmpegPath
                + " -re -i src/test/resources/test_video_360p.flv  -codec copy -f flv rtmp://localhost/LiveApp/"
                + streamId);

        long startTime = System.currentTimeMillis();
        Awaitility.await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    Broadcast broadcastLocal = getBroadcast(streamId);
                    return broadcastLocal != null
                            && broadcastLocal.getStreamId() != null
                            && broadcastLocal.getStreamId().contentEquals(streamId);
                });

       
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
            return testFile("http://" + SERVER_ADDR + ":5080/LiveApp/streams/" + streamId + ".m3u8", 0, false);
        });

        Awaitility.await().atMost(40, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDList = callGetVoDList(0,50, streamId);
            logger.info("VoD List size: {}", voDList.size());
            return voDList != null && voDList.size() == 1;
        });
        
        List<VoD> voDList = callGetVoDList(0,50, streamId);

        VoD createdMp4VoD = voDList.get(0);

        long upperBoundary = mp4CreationIntervalSeconds * 1000 + 4000;
        long lowerBoundary = mp4CreationIntervalSeconds * 1000 - 4000;
        
        assertEquals(createdMp4VoD.getStreamId(), streamId);
        assertTrue(createdMp4VoD.getDuration() > lowerBoundary && createdMp4VoD.getDuration() < upperBoundary);
        assertTrue(createdMp4VoD.getFileSize() > 0);
        assertTrue(createdMp4VoD.getStreamName().startsWith(name));
        assertEquals(description, createdMp4VoD.getDescription());
        assertEquals(latidue, createdMp4VoD.getLatitude());
        assertEquals(longitude, createdMp4VoD.getLongitude());
        assertEquals(altitude, createdMp4VoD.getAltitude());
        assertEquals(metadata, createdMp4VoD.getMetadata());
        
        Awaitility.await().atMost(40, TimeUnit.SECONDS).pollInterval(10, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDListLocal = callGetVoDList(0,50, streamId);
            logger.info("VoD List size: {}", voDListLocal.size());
            return voDListLocal != null && voDListLocal.size() == 2;
        });
        
        
        Awaitility.await().atMost(6, TimeUnit.SECONDS).pollDelay(5, TimeUnit.SECONDS).until(() -> true);
        
        long endTime = System.currentTimeMillis();
        
        long duration = (endTime - startTime);
        long expectedDuration = duration %  (mp4CreationIntervalSeconds * 1000);

        
        logger.info("duration:{} last segment expected duration: {}", duration, expectedDuration);
        File mp4File = callCreateMp4(streamId);
        
        
        assertTrue(testFile(mp4File.getAbsolutePath(), (int)expectedDuration, true));

        assertTrue(mp4File.delete());

       voDList = callGetVoDList(0, 50, streamId);
       
       
        
        //get the latest one
        createdMp4VoD = voDList.get(voDList.size()-1);
        upperBoundary = expectedDuration + 4000;
        lowerBoundary = expectedDuration - 4000;
        assertEquals(createdMp4VoD.getStreamId(), streamId);
        
        logger.info("last mp4 duration:{} and expected duration:{}ms", createdMp4VoD.getDuration(), expectedDuration);

        assertTrue(createdMp4VoD.getDuration() > lowerBoundary && createdMp4VoD.getDuration() < upperBoundary);
        assertTrue(createdMp4VoD.getFileSize() > 0);
        assertTrue(createdMp4VoD.getStreamName().startsWith(name));
        assertEquals(description, createdMp4VoD.getDescription());
        assertEquals(latidue, createdMp4VoD.getLatitude());
        assertEquals(longitude, createdMp4VoD.getLongitude());
        assertEquals(altitude, createdMp4VoD.getAltitude());
        assertEquals(metadata, createdMp4VoD.getMetadata());

        
        
        name = "testStreamUpdate";
        description = "testDescriptiontestStreamUpdate";
        latidue = "41.0825testStreamUpdate";
        longitude = "29.0093testStreamUpdate";
        altitude = "100testStreamUpdate";
        metadata = "metadatatestStreamUpdate";
        
        Result result = callUpdateBroadcast(streamId,  name, description, latidue, longitude, altitude, metadata);
        assertTrue(result.isSuccess());
        
        int lastVoDCount = voDList.size();
        
        rtmpSendingProcess.destroy();
        
        
        Awaitility.await().atMost(40, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).until(() -> {
            List<VoD> voDListLocal = callGetVoDList(0,50, streamId);
            logger.info("VoD List size: {}", voDListLocal.size());
            return voDListLocal != null && voDListLocal.size() == lastVoDCount + 1;
        });
        
        voDList = callGetVoDList(0, 50, streamId);
        
        //get the latest one
        createdMp4VoD = voDList.get(voDList.size()-1);
        assertEquals(description, createdMp4VoD.getDescription());
        assertEquals(latidue, createdMp4VoD.getLatitude());
        assertEquals(longitude, createdMp4VoD.getLongitude());
        assertEquals(altitude, createdMp4VoD.getAltitude());
        assertEquals(metadata, createdMp4VoD.getMetadata());



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
                videoStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);

                videoDuration = av_rescale_q(inputFormatContext.streams(i).duration(),  inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);


                videoExists = true;
                streamExists = true;
            } else if (codecpar.codec_type() == AVMEDIA_TYPE_AUDIO)
            {
                assertTrue(codecpar.sample_rate() != 0);
                audioStartTimeMs = av_rescale_q(inputFormatContext.streams(i).start_time(), inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);

                audioDuration = av_rescale_q(inputFormatContext.streams(i).duration(),  inputFormatContext.streams(i).time_base(), MuxAdaptor.TIME_BASE_FOR_MS);
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
		Broadcast broadcast = new Broadcast();
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
			HttpUriRequest post = RequestBuilder.put().setUri(url)
					.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
					.setEntity(new StringEntity(gson.toJson(broadcast))).build();

			HttpResponse response = client.execute(post);

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


}
