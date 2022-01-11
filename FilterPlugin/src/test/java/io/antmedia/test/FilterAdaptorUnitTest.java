package io.antmedia.test;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.Pointer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.FilterGraph;
import io.antmedia.filter.utils.IFilteredFrameListener;
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
		doReturn(true).when(filterAdaptor).update();
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);
		
		filterAdaptor.createFilter(filterConf, app);

		
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

		filterAdaptor.onVideoFrame(streamId, frame);
		verify(filterGraph, timeout(3000)).doFilter(eq(streamId), any());
	}
	
	
	@Test
	public void testFilterGraphAudioFeed() {
		FilterAdaptor filterAdaptor = spy(new FilterAdaptor(false));
		doReturn(true).when(filterAdaptor).update();
		FilterConfiguration filterConf = new FilterConfiguration();
		filterConf.setInputStreams(new ArrayList<>());
		filterConf.setOutputStreams(new ArrayList<>());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getVertx()).thenReturn(vertx);
		
		filterAdaptor.createFilter(filterConf, app);

		
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


}
