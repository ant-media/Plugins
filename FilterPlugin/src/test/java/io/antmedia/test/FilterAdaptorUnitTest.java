package io.antmedia.test;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.Pointer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.Utils;
import io.antmedia.filter.utils.Filter;
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
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(false));
		Result result = new Result(true);
		doReturn(result).when(filterAdaptor).update();
		doNothing().when(filterAdaptor).rescaleFramePtsToMs(any(), any());
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);
		
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		
		filterAdaptor.startFilterProcess(filterConf, app);

		
		String streamId = "stream"+RandomUtils.nextInt(0, 10000);
		AVFrame frame = new AVFrame();
		FilterGraph filterGraph = mock(FilterGraph.class);

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame);

		filterAdaptor.setVideoFilterGraphForTest(filterGraph);

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame);

		when(filterGraph.isInitiated()).thenReturn(true);

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame);

		when(filterGraph.getListener()).thenReturn(mock(IFilteredFrameListener.class));

		StreamParametersInfo vsi = new StreamParametersInfo();
		vsi.setCodecParameters(mock(AVCodecParameters.class));
		vsi.setTimeBase(Utils.TIME_BASE_FOR_MS);
		filterAdaptor.setVideoStreamInfo(streamId, vsi);
		
		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, timeout(3000)).doFilter(eq(streamId), any());
	}
	
	
	@Test
	public void testFilterGraphAudioFeed() {
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(false));
		Result result = new Result(true);
		
		doReturn(result).when(filterAdaptor).update();
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);
		
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		
		filterAdaptor.startFilterProcess(filterConf, app);

		
		String streamId = "stream"+RandomUtils.nextInt(0, 10000);
		AVFrame frame = new AVFrame();
		FilterGraph filterGraph = mock(FilterGraph.class);

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame);

		filterAdaptor.setAudioFilterGraphForTest(filterGraph);

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame);

		when(filterGraph.isInitiated()).thenReturn(true);

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, never()).doFilter(streamId, frame);

		when(filterGraph.getListener()).thenReturn(mock(IFilteredFrameListener.class));

		filterAdaptor.onAudioFrame(streamId, frame);
		verify(filterGraph, timeout(3000)).doFilter(eq(streamId), any());
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
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(false));
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		
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
		
		assertTrue(filterAdaptor.startFilterProcess(conf, app).isSuccess());
	}

	public StreamParametersInfo getStreamInfo() {
		StreamParametersInfo si = new StreamParametersInfo();
		AVCodecParameters codecParams = mock(AVCodecParameters.class);
		when(codecParams.height()).thenReturn(360);
		when(codecParams.width()).thenReturn(640);
		when(codecParams.channel_layout()).thenReturn(2L);
		when(codecParams.sample_rate()).thenReturn(16000);


		AVRational tb = mock(AVRational.class);
		when(tb.num()).thenReturn(1);
		when(tb.den()).thenReturn(1000);

		
		si.setEnabled(true);
		si.setCodecParameters(codecParams);
		
		
		si.setTimeBase(tb);

		return si;
	}


}
