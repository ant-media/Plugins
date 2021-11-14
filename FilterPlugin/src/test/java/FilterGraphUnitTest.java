import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterGraph;

public class FilterGraphUnitTest {
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
