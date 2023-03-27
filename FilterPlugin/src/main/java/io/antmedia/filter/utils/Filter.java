package io.antmedia.filter.utils;

import static org.bytedeco.ffmpeg.global.avfilter.avfilter_get_by_name;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_create_filter;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_inout_alloc;

import java.util.ArrayList;
import java.util.List;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avfilter.AVFilter;
import org.bytedeco.ffmpeg.avfilter.AVFilterContext;
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avfilter.AVFilterInOut;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.filter.Utils;

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
	AVFilter avFilter;
	
	String label;
	private String filterArgs;
	private String filterName;
	private BytePointer labelPointer;
	protected long offset = 0;
	protected boolean isFirstFrame = true;
	
	private int pixelFormat = -1;
	
	public static List<BytePointer> labels = new ArrayList<>();

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
		avFilter = avfilter_get_by_name(filterName);
		int ret = avfilter_graph_create_filter(filterContext, avFilter, label, filterArgs, null, filterGraph);
		if (ret < 0) 
		{
			String message = "Cannot create filter context. Label="+ label + " filterArgs:" + filterArgs + " filterName: " + filterName + " .Error is " + Utils.getErrorDefinition(ret);
			logger.error(message);
			throw new IllegalStateException(message);
		}
		
		if (pixelFormat != -1) {
			BytePointer bytePointer = new BytePointer(Pointer.sizeof(IntPointer.class));
			bytePointer.putInt(pixelFormat);
			bytePointer.position(0);
			
			ret = avutil.av_opt_set_bin(filterContext,"pix_fmts", bytePointer, Pointer.sizeof(IntPointer.class), avutil.AV_OPT_SEARCH_CHILDREN);
			if (ret < 0) 
			{
				logAndThrowException(ret);
			}
		}
	}
	
	public void logAndThrowException(int errorCode) {
		String message = "Cannot set pixel format: "+ pixelFormat + " FilterArgs:" + filterArgs + " filterName: " + filterName + " .Error is " + Utils.getErrorDefinition(errorCode);
		logger.error(message);
		throw new IllegalStateException(message);
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
	
	public void setPixelFormat(int pixelFormat) {
		this.pixelFormat = pixelFormat;
	}
	
	public AVFilterContext getFilterContext() {
		return filterContext;
	}
}