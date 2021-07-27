package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avfilter.avfilter_get_by_name;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_create_filter;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_inout_alloc;

import java.util.ArrayList;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avfilter.AVFilter;
import org.bytedeco.ffmpeg.avfilter.AVFilterContext;
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avfilter.AVFilterInOut;
import org.bytedeco.javacpp.BytePointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* 
 * source filters that do not have an audio/video input
 * sink filters that will not have audio/video output
 * https://ffmpeg.org/ffmpeg-filters.html
 * 
 * we should pass our stream frames through a source filter to add it to filter graph
 * we should get frames from sink filter and feed our streams with them
 */

public class Filter {
	private static final Logger logger = LoggerFactory.getLogger(Filter.class);
	AVFilterContext filterContext = new AVFilterContext();
	AVFilterInOut filterInOut;
	AVFilter filterBuffer;
	
	String label;
	private String filterArgs;
	private String filterName;
	private BytePointer labelPointer;
	public long offset = 0;
	public boolean isFirstFrame = true;
	
	public static ArrayList<BytePointer> labels = new ArrayList<BytePointer>();

	public Filter(String filterName, String filterArgs, String label) {
		this.filterName = filterName;
		this.label = label;
		this.filterArgs = filterArgs;
		this.labelPointer = new BytePointer(label);
		
		/*
		 * FIXME:
		 * this is a hack to keep reference of the labelPointer reference
		 * otherwise when it is deallocated we get double free error
		 */
		labels.add(labelPointer);
	}

	public void initFilterContex(AVFilterGraph filterGraph) {
		filterBuffer = avfilter_get_by_name(filterName);
		int ret = avfilter_graph_create_filter(filterContext, filterBuffer, label, filterArgs, null, filterGraph);
		if (ret < 0) {
			logger.error("Cannot create buffer source");
		}
	}

	/*
	 * returns
	 * filter out for Source filter 
	 * filter in for Sink filter
	 */
	public AVFilterInOut getFilterInOut() {
		if(filterInOut == null) {
			filterInOut = avfilter_inout_alloc();
			filterInOut.name(labelPointer);
			filterInOut.filter_ctx(filterContext);
			filterInOut.pad_idx(0);
			filterInOut.next(null);
		}
		return filterInOut;
	}
}