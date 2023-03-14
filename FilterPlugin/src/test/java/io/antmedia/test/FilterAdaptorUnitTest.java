package io.antmedia.test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avutil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.Utils;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.FilterGraph;
import io.antmedia.filter.utils.IFilteredFrameListener;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.antmedia.rest.model.Result;
import io.vertx.core.Vertx;

public class FilterAdaptorUnitTest {
	
	@Rule
	public TestRule watcher = new TestWatcher() {
		protected void starting(Description description) {
			System.out.println("Starting test: " + description.getMethodName());
		}

		protected void failed(Throwable e, Description description) {
			System.out.println("Failed test: " + description.getMethodName() + " e: " + ExceptionUtils.getStackTrace(e));
		};

		protected void finished(Description description) {
			System.out.println("Finishing test: " + description.getMethodName());
		};
	};
	
	private static Vertx vertx;

	@AfterClass
	public static void afterClass() {
		vertx.close();
	}

	@BeforeClass
	public static void beforeClass() {
		vertx = Vertx.vertx();
	}

	
	@Test
	public void testFilterGraphVideoFeed() {
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(RandomStringUtils.randomAlphanumeric(12), false));
		doReturn(new Result(true)).when(filterAdaptor).update();
		doNothing().when(filterAdaptor).rescaleFramePtsToMs(any(), any());
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);
		
		filterAdaptor.createOrUpdateFilter(filterConf, app);

		
		String streamId = "stream"+RandomUtils.nextInt(0, 10000);
		AVFrame frame = new AVFrame();
		FilterGraph filterGraph = mock(FilterGraph.class);

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame, any());

		filterAdaptor.setVideoFilterGraphForTest(filterGraph);

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame, any());

		when(filterGraph.isInitiated()).thenReturn(true);

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame, any());

		when(filterGraph.getListener()).thenReturn(mock(IFilteredFrameListener.class));

		StreamParametersInfo vsi = new StreamParametersInfo();
		vsi.setCodecParameters(mock(AVCodecParameters.class));
		vsi.setTimeBase(Utils.TIME_BASE_FOR_MS);
		filterAdaptor.setVideoStreamInfo(streamId, vsi);
		
		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, timeout(3000)).doFilter(eq(streamId), any(), any());
	}
	
	
	@Test
	public void testFilterGraphAudioFeed() {
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(RandomStringUtils.randomAlphanumeric(12), false));
		doReturn(new Result(true)).when(filterAdaptor).update();
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);
		
		filterAdaptor.createOrUpdateFilter(filterConf, app);

		
		String streamId = "stream"+RandomUtils.nextInt(0, 10000);
		AVFrame frame = new AVFrame();
		FilterGraph filterGraph = mock(FilterGraph.class);

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame, any());

		filterAdaptor.setAudioFilterGraphForTest(filterGraph);

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame, any());

		when(filterGraph.isInitiated()).thenReturn(true);

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame, any());

		when(filterGraph.getListener()).thenReturn(mock(IFilteredFrameListener.class));

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, timeout(3000)).doFilter(eq(streamId), any(), any());
	}
	
	@Test
	public void testVideoAudioFiltering() {
		testFiltering(true, "[in0][in1][in2]vstack=inputs=3[out0]", true, "[in0][in1][in2]amix=inputs=3[out0]");
	}
	
	//use some of the inputs in the filter
	@Test 
	public void testPartialVideoAudioFiltering() {
		testFiltering(true, "[in0][in2]vstack=inputs=2[out0]", true, "[in0][in2]amix=inputs=2[out0]");
	}
	
	@Test
	public void testVideoOnlyFiltering() {
		testFiltering(true, "[in0][in1][in2]vstack=inputs=3[out0]", false, "dummy");
	}
	
	@Test
	public void testAudioOnlyFiltering() {
		testFiltering(false, "dummy", true, "[in0][in1][in2]amix=inputs=3[out0]");
	}
	
	
	public void testFiltering(boolean videoEnabled, String videoFilter, boolean audioEnabled, String audioFilter) {
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(RandomStringUtils.randomAlphanumeric(12), false));
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.createCustomBroadcast(anyString())).thenReturn(mock(IFrameListener.class));

		String stream1 = "inStream1";
		String stream2 = "inStream2";
		String stream3 = "inStream3";
		
		String output1 = "outStream1";


		StreamParametersInfo vsi1 = getStreamInfo();
		StreamParametersInfo vsi2 = getStreamInfo();
		StreamParametersInfo vsi3 = getStreamInfo();

		StreamParametersInfo asi1 = getStreamInfo();
		StreamParametersInfo asi2 = getStreamInfo();
		StreamParametersInfo asi3 = getStreamInfo();

		
		filterAdaptor.setVideoStreamInfo(stream1, vsi1);
		filterAdaptor.setAudioStreamInfo(stream1, asi1);
		
		filterAdaptor.setVideoStreamInfo(stream2, vsi2);
		filterAdaptor.setAudioStreamInfo(stream2, asi2);
		
		filterAdaptor.setVideoStreamInfo(stream3, vsi3);
		filterAdaptor.setAudioStreamInfo(stream3, asi3);
		
		FilterConfiguration conf = new FilterConfiguration();
		conf.setVideoEnabled(videoEnabled);
		conf.setAudioEnabled(audioEnabled);
		conf.setVideoFilter(videoFilter);
		conf.setAudioFilter(audioFilter);
		conf.setInputStreams(Arrays.asList(stream1, stream2, stream3));
		conf.setOutputStreams(Arrays.asList(output1));
		
		assertTrue(filterAdaptor.createOrUpdateFilter(conf, app).isSuccess());
		
		filterAdaptor.close(app);
		
		//increase coverage and checking not throwing exception
		filterAdaptor.close(app);
		
	}

	public StreamParametersInfo getStreamInfo() {
		StreamParametersInfo si = new StreamParametersInfo();
		AVCodecParameters codecParams = mock(AVCodecParameters.class);
		when(codecParams.height()).thenReturn(360);
		when(codecParams.width()).thenReturn(640);
		
		AVChannelLayout channelLayout = new AVChannelLayout();
		avutil.av_channel_layout_default(channelLayout, 2);
		when(codecParams.ch_layout()).thenReturn(channelLayout);
		when(codecParams.sample_rate()).thenReturn(16000);


		AVRational tb = mock(AVRational.class);
		when(tb.num()).thenReturn(1);
		when(tb.den()).thenReturn(1000);

		
		si.setEnabled(true);
		si.setCodecParameters(codecParams);
		
		
		si.setTimeBase(tb);

		return si;
	}
	
	/*
	 * In synchronous mode output frame pts should be the same with input,
	 * because we apply filter on going original stream without creating a new stream.
	 */
	@Test
	public void testTimeBaseInSyncMode() {
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(RandomStringUtils.randomAlphanumeric(12), false));
		doReturn(new Result(true)).when(filterAdaptor).update();
		doNothing().when(filterAdaptor).rescaleFramePtsToMs(any(), any());
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());
		filterConf.setType(FilterConfiguration.SYNCHRONOUS);

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);

		filterAdaptor.createOrUpdateFilter(filterConf, app);

		String streamId = "stream"+RandomUtils.nextInt(0, 10000);
		FilterGraph filterGraph = mock(FilterGraph.class);
		filterAdaptor.setVideoFilterGraphForTest(filterGraph);
		filterAdaptor.setAudioFilterGraphForTest(filterGraph);

		when(filterGraph.isInitiated()).thenReturn(true);
		when(filterGraph.getListener()).thenReturn(mock(IFilteredFrameListener.class));
		AVFrame filterOutputFrame = new AVFrame();
		filterOutputFrame.width(100);
		filterOutputFrame.height(100);
		when(filterGraph.doFilter(eq(streamId), any(), any())).thenReturn(filterOutputFrame);

		StreamParametersInfo streamInfo = getStreamInfo();
		filterAdaptor.setVideoStreamInfo(streamId, streamInfo);
		filterAdaptor.setAudioStreamInfo(streamId, streamInfo);

		//for video
		for(int i = 0; i < 100; i++) {
			AVFrame frame = new AVFrame();
			frame.width(streamInfo.getCodecParameters().width());
			frame.height(streamInfo.getCodecParameters().height());
			frame.pts(RandomUtils.nextLong(0, 5000));
			AVFrame filteredFrame = filterAdaptor.onVideoFrame(streamId, frame);

			//check the output is different than input but pts values are same
			assertNotEquals(filteredFrame, frame);
			assertEquals(filteredFrame.pts(), frame.pts());
		}

		//for audio
		for(int i = 0; i < 100; i++) {
			AVFrame frame = new AVFrame();
			frame.pts(RandomUtils.nextLong(0, 5000));
			AVFrame filteredFrame = filterAdaptor.onAudioFrame(streamId, frame);

			//check the output is different than input but pts values are same
			assertNotEquals(filteredFrame, frame);
			assertEquals(filteredFrame.pts(), frame.pts());
		}
	}
}
