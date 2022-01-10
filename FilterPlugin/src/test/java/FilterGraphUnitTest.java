import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

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
		assertFalse(createFilterGraph("dummy_filter_description", 1, 1));
		assertTrue(createFilterGraph("[in0][in1]vstack[out0]", 2, 1));
		assertFalse(createFilterGraph("[in0][in1]dummy[out0]", 2, 1));
		assertTrue(createFilterGraph("[in0]split[out0][out1]", 1, 2));
	}
	
	public boolean createFilterGraph(String filterDescription, int sources, int sinks) {
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

		return new FilterGraph(filterDescription, sourceFiltersMap, sinkFiltersMap).isInitiated();
	}

}
