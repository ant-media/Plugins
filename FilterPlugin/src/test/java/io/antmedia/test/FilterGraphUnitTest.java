package io.antmedia.test;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_alloc;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Mockito;

import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterGraph;

public class FilterGraphUnitTest {

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

	@Test
	public void testFilterGraphParse() {
		assertFalse(createFilterGraph("dummy_filter_description", 1, 1).isInitiated());
		assertTrue(createFilterGraph("[in0][in1]vstack[out0]", 2, 1).isInitiated());
		assertFalse(createFilterGraph("[in0][in1]dummy[out0]", 2, 1).isInitiated());
		assertTrue(createFilterGraph("[in0]split[out0][out1]", 1, 2).isInitiated());
	}

	@Test
	public void testFilterThrowException() {

		try {
			String audioFilterArgs = "channel_layout=2:";

			Filter sinkFilter = new Filter("buffersink", audioFilterArgs, "out");

			AVFilterGraph filterGraph = avfilter_graph_alloc();
			sinkFilter.initFilterContex(filterGraph);
			
			fail("It should throw exception above");
		}
		catch(Exception e) {
			//No need to implement. It should throw exception
		}
	}
	
	
	@Test
	public void testVideoFilterThrowException() {

		try {
			Filter videoSink = new Filter("buffersink", null, "out");

			
			videoSink.logAndThrowException(-22);
			
			fail("It should throw exception above");
		}
		catch(Exception e) {
			//No need to implement. It should throw exception
		}
	}
	


	public FilterGraph createFilterGraph(String filterDescription, int sources, int sinks) {
		Map<String, Filter> sourceFiltersMap = new LinkedHashMap<String, Filter>();

		for (int i = 0; i < sources; i++) {
			Filter sourceFilter = new Filter("buffer", "video_size=360x360:pix_fmt=0:time_base=1/20:pixel_aspect=1/1", "in"+i); 
			sourceFiltersMap.put("stream"+i, sourceFilter);
		}

		Map<String, Filter> sinkFiltersMap = new LinkedHashMap<String, Filter>();
		for (int i = 0; i < sinks; i++) {
			Filter sinkFilter = new Filter("buffersink", null, "out"+i); 
			sinkFiltersMap.put("output"+i, sinkFilter);
		}

		return new FilterGraph(filterDescription, sourceFiltersMap, sinkFiltersMap);
	}
	
	@Test
	public void testPrepareFrame() {
		FilterGraph graph = Mockito.spy(createFilterGraph("[in0][in1]vstack[out0]", 2, 1));
		AVFrame frame = new AVFrame();
		frame.width(480);
		frame.height(360);
		frame.format(avutil.AV_PIX_FMT_YUV420P);
		
		graph.prepareFrame(frame);
		
		assertNotNull(graph.getPicture());
		assertEquals(480, graph.getPicture().width());
		assertEquals(360, graph.getPicture().height());
		assertEquals(avutil.AV_PIX_FMT_YUV420P, graph.getPicture().format());
		
		
		frame.width(240);
		graph.prepareFrame(frame);
		Mockito.verify(graph).freeSwsResources();
		assertNotNull(graph.getPicture());
		assertEquals(240, graph.getPicture().width());
		assertEquals(360, graph.getPicture().height());
		assertEquals(avutil.AV_PIX_FMT_YUV420P, graph.getPicture().format());
		
		frame.height(240);
		graph.prepareFrame(frame);
		Mockito.verify(graph, Mockito.times(2)).freeSwsResources();
		assertNotNull(graph.getPicture());
		assertEquals(240, graph.getPicture().width());
		assertEquals(240, graph.getPicture().height());
		assertEquals(avutil.AV_PIX_FMT_YUV420P, graph.getPicture().format());
		
		
		frame.format(avutil.AV_PIX_FMT_YUYV422);
		graph.prepareFrame(frame);
		Mockito.verify(graph, Mockito.times(3)).freeSwsResources();
		assertNotNull(graph.getPicture());
		assertEquals(240, graph.getPicture().width());
		assertEquals(240, graph.getPicture().height());
		assertEquals(avutil.AV_PIX_FMT_YUYV422, graph.getPicture().format());
		
		graph.prepareFrame(frame);
		Mockito.verify(graph, Mockito.times(3)).freeSwsResources();
		
		
	}

}
