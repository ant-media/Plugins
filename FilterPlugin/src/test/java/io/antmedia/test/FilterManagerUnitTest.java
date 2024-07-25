package io.antmedia.test;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.Utils;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;

public class FilterManagerUnitTest {


	private static Logger logger = LoggerFactory.getLogger(FilterManagerUnitTest.class);

	/**
	 * Fix for this issue https://github.com/ant-media/Ant-Media-Server/issues/4605
	 */
	@Test
	public void testReturnsErrorForNnonStreamingCase() {
		FiltersManager filtersManager = spy(new FiltersManager());
		String filterString = "{\"filterId\":\"CHVStreamFilter\",\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();

		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getAppSettings()).thenReturn(new AppSettings());
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );

		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertFalse(result.isSuccess());

	}

	/**
	 * Fix for the below issues
	 * https://github.com/ant-media/Ant-Media-Server/issues/4580
	 * https://github.com/ant-media/Ant-Media-Server/issues/4541
	 */
	@Test
	public void testReturnErrorForUnActiveFilter() {
		FiltersManager filtersManager = spy(new FiltersManager());

		String filterString = "{\"filterId\":\"filter1\",\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);

		FilterAdaptor filterAdaptor = filtersManager.getFilterAdaptor(filterConfiguration.getFilterId(), false);

		FilterAdaptor filterAdaptor2 = filtersManager.getFilterAdaptor(filterConfiguration.getFilterId(), false);
		assertEquals(filterAdaptor, filterAdaptor2);

		filterAdaptor.setFilterConfiguration(filterConfiguration);

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);

		boolean result = filtersManager.delete(filterConfiguration.getFilterId(), app);
		assertTrue(result);

		result = filtersManager.delete(filterConfiguration.getFilterId(), app);
		assertFalse(result);
	}



	@Test
	public void testCheckResultandFilterId() {
		FiltersManager filtersManager = spy(new FiltersManager());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();

		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		assertNull(filterConfiguration.getFilterId());
		when(app.getAppSettings()).thenReturn(new AppSettings());
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore);

		Broadcast broadcast = new Broadcast();
		try {
			broadcast.setStreamId("stream1");
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		dataStore.save(broadcast);

		when(app.createCustomBroadcast("stream2")).thenReturn(Mockito.mock(IFrameListener.class));
		when(app.createCustomBroadcast("stream3")).thenReturn(Mockito.mock(IFrameListener.class));
		FilterAdaptor filterAdaptor = Mockito.mock(FilterAdaptor.class);
		Mockito.doReturn(filterAdaptor).when(filtersManager).getFilterAdaptor(Mockito.anyString(), Mockito.anyBoolean());
		Mockito.when(filterAdaptor.createOrUpdateFilter(Mockito.any(), Mockito.any())).thenReturn(new Result(true));


		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertTrue(result.isSuccess());
		assertNotNull(filterConfiguration.getFilterId());

	}

	/**
	 * This test crashes the JVM before the fix
	 */
	@Test
	public void testVflip() {
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream1\"],\"videoFilter\":\"[in0]vflip[out0]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"false\",\"type\":\"synchronous\"}";
		String rawFile = "src/test/resources/raw_frame_640_480_yuv420.yuv";


		testBugVideoFilterNotWorking(filterString, rawFile, 640, 480, 500);
	}

	@Test
	public void testVirtualBackground() 
	{
		//movie=src/test/resources/background.jpeg[background];[background][in0]chromakey=0x6de61b:0.1:0.2[transparent];
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream1\"],\"videoFilter\":\"movie=src/test/resources/background.png[background];[in0]chromakey=0x6de61b:0.1:0.2[transparent];[background][transparent]overlay[out0]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"false\",\"type\":\"synchronous\"}";
		String rawFile = "src/test/resources/green_screen_1280x720.yuv";

		testBugVideoFilterNotWorking(filterString, rawFile, 1280, 720, 100);
	}




	public void testBugVideoFilterNotWorking(String filterString, String rawFile, int sourceWidth, int sourceHeight, int pts) {


		avutil.av_log_set_level(avutil.AV_LOG_TRACE);

		FiltersManager filtersManager = spy(new FiltersManager());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);

		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		assertNull(filterConfiguration.getFilterId());

		AppSettings appSettings = new AppSettings();
		appSettings.setEncoderSettings(Arrays.asList(new EncoderSettings(480, 500000, 64000, false)));

		when(app.getAppSettings()).thenReturn(appSettings);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore);



		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertFalse(result.isSuccess());

		Broadcast broadcast = new Broadcast();
		try {
			broadcast.setStreamId("stream1");
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		dataStore.save(broadcast);

		FilterAdaptor filterAdaptor = new FilterAdaptor("filter1", false);
		AVCodecParameters videoCodecParameters = new AVCodecParameters();
		videoCodecParameters.width(sourceWidth);
		videoCodecParameters.height(sourceHeight);

		videoCodecParameters.codec_id(AV_CODEC_ID_H264);
		videoCodecParameters.codec_type(AVMEDIA_TYPE_VIDEO);		
		videoCodecParameters.format(AV_PIX_FMT_YUV420P);
		videoCodecParameters.codec_tag(0);

		StreamParametersInfo streamParams = new StreamParametersInfo();
		streamParams.setEnabled(true);
		streamParams.setCodecParameters(videoCodecParameters);
		streamParams.setTimeBase(Utils.TIME_BASE_FOR_MS);


		filterAdaptor.setVideoStreamInfo("stream1", streamParams);

		filterAdaptor.setAudioStreamInfo("stream1", new StreamParametersInfo());

		Mockito.when(filtersManager.getFilterAdaptor(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(filterAdaptor);

		result = filtersManager.createFilter(filterConfiguration, app);
		assertTrue(result.isSuccess());

		AVFrame rawVideoFrame = new AVFrame();

		rawVideoFrame.width(sourceWidth);
		rawVideoFrame.height(sourceHeight);
		rawVideoFrame.format(AV_PIX_FMT_YUV420P);

		rawVideoFrame.pts(1);
		try {
			//byte[] frameData = Files.readAllBytes(Path.of("src/test/resources/green_screen_yuv420p_2554_1428.yuv"));

			byte[] frameData = Files.readAllBytes(Path.of(rawFile));

			BytePointer rawVideoBuffer = new BytePointer(av_malloc(frameData.length));

			av_image_fill_arrays(new PointerPointer(rawVideoFrame), rawVideoFrame.linesize(), rawVideoBuffer, AV_PIX_FMT_YUV420P, rawVideoFrame.width(), rawVideoFrame.height(), 1);
			rawVideoFrame.linesize(0, sourceWidth);
			rawVideoFrame.linesize(1, sourceWidth/2);
			rawVideoFrame.linesize(2, sourceWidth/2);

			rawVideoBuffer.position(0);
			rawVideoBuffer.put(frameData, 0, frameData.length);
			rawVideoFrame.pts(pts);
			Utils.save(rawVideoFrame, "before_process_"+rawVideoFrame.width()+"x"+rawVideoFrame.height());


			AVFrame onVideoFrame = filterAdaptor.onVideoFrame("stream1", rawVideoFrame);
			assertNotNull(onVideoFrame);
			//assertEquals(sourceWidth, onVideoFrame.width());
			//assertEquals(sourceHeight, onVideoFrame.height());

			Utils.save(onVideoFrame, "after_process_"+ onVideoFrame.width() + "x" + onVideoFrame.height());

			assertEquals(pts, onVideoFrame.pts());

			filterAdaptor.close(app);

		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testFixVideoPTSInFilter() {
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"test\"],\"videoFilter\":\"movie=src/test/resources/background.png[background];[in0]chromakey=0x6de61b:0.1:0.2[transparent];[background][transparent]overlay[out0]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"false\",\"type\":\"asynchronous\"}";
		String rawFile = "src/test/resources/green_screen_1280x720.yuv";

		int sourceWidth = 1280;
		int sourceHeight = 720;
		
		testFixVideoPtsInFilter(filterString, rawFile, sourceWidth, sourceHeight);
	}
	
	@Test
	public void testFixVideoPTSInLastPointFilter() {
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"test\"],\"videoFilter\":\"movie=src/test/resources/background.png[background];[in0]chromakey=0x6de61b:0.1:0.2[transparent];[background][transparent]overlay[out0]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"false\",\"type\":\"lastpoint\"}";
		String rawFile = "src/test/resources/green_screen_1280x720.yuv";

		int sourceWidth = 1280;
		int sourceHeight = 720;
		
		testFixVideoPtsInFilter(filterString, rawFile, sourceWidth, sourceHeight);
	}
	

	long pts = -1;
	int callCount = 0;

	
	public void testFixVideoPtsInFilter(String filterString, String rawFile, int sourceWidth, int sourceHeight) {
		


		FiltersManager filtersManager = spy(new FiltersManager());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);

		Mockito.when(app.getVertx()).thenReturn(Vertx.vertx());

		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		assertNull(filterConfiguration.getFilterId());

		AppSettings appSettings = new AppSettings();
		appSettings.setEncoderSettings(Arrays.asList(new EncoderSettings(480, 500000, 64000, false)));

		when(app.getAppSettings()).thenReturn(appSettings);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore);

		Broadcast broadcast = new Broadcast();
		try {
			broadcast.setStreamId("stream1");
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		dataStore.save(broadcast);

		callCount = 0;
		IFrameListener frameListener = new IFrameListener() {

			@Override
			public void writeTrailer(String streamId) {
				// TODO Auto-generated method stub

			}

			@Override
			public void start() {
				// TODO Auto-generated method stub

			}

			@Override
			public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
				// TODO Auto-generated method stub

			}

			@Override
			public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
				// TODO Auto-generated method stub

			}

			@Override
			public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {

				Utils.save(videoFrame, streamId);
				pts = videoFrame.pts();
				callCount++;
				return null;
			}

			@Override
			public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		when(app.createCustomBroadcast(Mockito.anyString())).thenReturn(frameListener);

		FilterAdaptor filterAdaptor = new FilterAdaptor("filter1", false);
		AVCodecParameters videoCodecParameters = new AVCodecParameters();
		videoCodecParameters.width(sourceWidth);
		videoCodecParameters.height(sourceHeight);

		videoCodecParameters.codec_id(AV_CODEC_ID_H264);
		videoCodecParameters.codec_type(AVMEDIA_TYPE_VIDEO);		
		videoCodecParameters.format(AV_PIX_FMT_YUV420P);
		videoCodecParameters.codec_tag(0);

		StreamParametersInfo streamParams = new StreamParametersInfo();
		streamParams.setEnabled(true);
		streamParams.setCodecParameters(videoCodecParameters);
		streamParams.setTimeBase(Utils.TIME_BASE_FOR_MS);


		filterAdaptor.setVideoStreamInfo("stream1", streamParams);

		filterAdaptor.setAudioStreamInfo("stream1", new StreamParametersInfo());

		Mockito.when(filtersManager.getFilterAdaptor(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(filterAdaptor);

		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertTrue(result.isSuccess());

		AVFrame rawVideoFrame = new AVFrame();

		rawVideoFrame.width(sourceWidth);
		rawVideoFrame.height(sourceHeight);
		rawVideoFrame.format(AV_PIX_FMT_YUV420P);


		rawVideoFrame.pts(0);

		int inputPts = 1000;
		try {
			//byte[] frameData = Files.readAllBytes(Path.of("src/test/resources/green_screen_yuv420p_2554_1428.yuv"));

			byte[] frameData = Files.readAllBytes(Path.of(rawFile));

			BytePointer rawVideoBuffer = new BytePointer(av_malloc(frameData.length));

			av_image_fill_arrays(new PointerPointer(rawVideoFrame), rawVideoFrame.linesize(), rawVideoBuffer, AV_PIX_FMT_YUV420P, rawVideoFrame.width(), rawVideoFrame.height(), 1);
			rawVideoFrame.linesize(0, sourceWidth);
			rawVideoFrame.linesize(1, sourceWidth/2);
			rawVideoFrame.linesize(2, sourceWidth/2);

			rawVideoBuffer.position(0);
			rawVideoBuffer.put(frameData, 0, frameData.length);
			Utils.save(rawVideoFrame, "before_process_"+rawVideoFrame.width()+"x"+rawVideoFrame.height());


			filterAdaptor.onVideoFrame("stream1", rawVideoFrame);

			//give two frames because first frame pts is reset

			rawVideoFrame.pts(inputPts);
			filterAdaptor.onVideoFrame("stream1", rawVideoFrame);


			Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> callCount == 2);
			//it was failing before the fix
			assertEquals(inputPts, pts);
			
			//it should not throw exception
			filterAdaptor.onVideoFrame("stream1", null);


			filterAdaptor.close(app);

		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}

	boolean videoFrameReceived = false;

	/**
	 * This test crashes the JVM before the fix
	 */
	@Test
	public void testVideoDecodeFilter() {

		avutil.av_log_set_level(avutil.AV_LOG_INFO);

		FiltersManager filtersManager = spy(new FiltersManager());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);

		Mockito.when(app.getVertx()).thenReturn(Vertx.vertx());

		int width = 640;  //set the width and height according to the input file
		int height = 360;
		String filepath = "src/test/resources/test_video_360p_video_only.ts";

		videoFrameReceived = false;
		IFrameListener frameListener = new IFrameListener() {

			@Override
			public void writeTrailer(String streamId) {
				// TODO Auto-generated method stub

			}

			@Override
			public void start() {
				// TODO Auto-generated method stub

			}

			@Override
			public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
				// TODO Auto-generated method stub

			}

			@Override
			public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
				// TODO Auto-generated method stub

			}

			@Override
			public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {

				//
				//tmpFrame = videoFrame;
				/*
				//if these asserts are not equal, the test is blocked here
				 *
				 */
				assertEquals(640, videoFrame.width());
				assertEquals(360, videoFrame.height());

				logger.info("line size: {}", videoFrame.linesize(0));

				//	assertTrue(videoFrame.linesize(0) > 0);
				Utils.save(videoFrame, streamId);
				videoFrameReceived = true;

				return null;
			}

			@Override
			public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		when(app.createCustomBroadcast(Mockito.anyString())).thenReturn(frameListener);
		//async filter
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\"],\"videoFilter\":\"[in0]vflip[out0]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"false\",\"type\":\"asynchronous\"}";
		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		assertNull(filterConfiguration.getFilterId());

		AppSettings appSettings = new AppSettings();

		when(app.getAppSettings()).thenReturn(appSettings);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore);

		Broadcast broadcast = new Broadcast();
		try {
			broadcast.setStreamId("stream1");
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		assertEquals("stream1", dataStore.save(broadcast));

		//prepare filter adaptor
		FilterAdaptor filterAdaptor = new FilterAdaptor("filter1", true);
		AVCodecParameters videoCodecParameters = new AVCodecParameters();

		videoCodecParameters.width(width);
		videoCodecParameters.height(height);
		videoCodecParameters.codec_id(AV_CODEC_ID_H264);
		videoCodecParameters.codec_type(AVMEDIA_TYPE_VIDEO);		
		videoCodecParameters.format(AV_PIX_FMT_YUV420P);
		videoCodecParameters.codec_tag(0);

		StreamParametersInfo streamParams = new StreamParametersInfo();
		streamParams.setEnabled(true);
		streamParams.setTimeBase(Utils.TIME_BASE_FOR_MS);

		filterAdaptor.setAudioStreamInfo("stream1", new StreamParametersInfo());
		//video parameters are set after 

		//set the codec parameters
		streamParams.setCodecParameters(videoCodecParameters);
		filterAdaptor.setVideoStreamInfo("stream1", streamParams);

		//create filter
		Mockito.when(filtersManager.getFilterAdaptor(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(filterAdaptor);
		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertTrue(result.isSuccess());

		//// Read the file

		AVFormatContext inputFormatContext = new AVFormatContext(null); 

		int ret = avformat_open_input(inputFormatContext, filepath, null, null);
		assertTrue(Utils.getErrorDefinition(ret), ret >= 0);

		ret = avformat_find_stream_info(inputFormatContext, (AVDictionary) null);
		assertTrue(Utils.getErrorDefinition(ret), ret >= 0);

		avformat.av_dump_format(inputFormatContext,0, (String)null, 0);

		AVPacket pkt = avcodec.av_packet_alloc();

		while ((ret = av_read_frame(inputFormatContext, pkt)) == 0) 
		{
			assertTrue(Utils.getErrorDefinition(ret), ret >= 0);
			filterAdaptor.onVideoPacket("stream1", pkt);
			//int streamIndex = pkt.stream_index();
			//byte[] data = new byte[pkt.size()];
			//pkt.data().position(0).get(data);
			av_packet_unref(pkt);
		}


		Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> videoFrameReceived);
	}



}
