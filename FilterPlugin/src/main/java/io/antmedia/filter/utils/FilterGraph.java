package io.antmedia.filter.utils;


import static org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_frame;
import static org.bytedeco.ffmpeg.global.avfilter.*;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_alloc;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_config;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_parse;
import static org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_ref;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size;
import static org.bytedeco.ffmpeg.global.swscale.SWS_ACCURATE_RND;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;
import static org.bytedeco.ffmpeg.presets.avutil.AVERROR_EAGAIN;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;


import java.util.Map;

import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avfilter.AVFilterInOut;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.filter.Utils;

/*
 * A filtergraph is a directed graph of connected filters.
 *  https://ffmpeg.org/ffmpeg-filters.html
 */
public class FilterGraph {
	private static final Logger logger = LoggerFactory.getLogger(FilterGraph.class);

	AVFilterInOut listOfOutputs;
	AVFilterInOut listOfInputs;
	
	Map<String, Filter> sourceFiltersMap;
	Map<String, Filter> sinkFiltersMap;

	AVFilterGraph filterGraph = new AVFilterGraph();
	AVFrame filterOutputFrame = new AVFrame();
	
	protected SwsContext swsCtx = null; 
	protected AVFrame picture;
	private BytePointer pictureBufptr;

	private Object lock = new Object();
	private boolean initiated = false;
	private IFilteredFrameListener listener;

	private long currentPts = 0;

	private String filterDescription; 


	public FilterGraph(String filterDescription, Map<String, Filter> sourceFiltersMap, Map<String, Filter> sinkFiltersMap) {
		this.sourceFiltersMap = sourceFiltersMap;
		this.sinkFiltersMap = sinkFiltersMap;
		this.filterDescription = filterDescription;
		
		filterOutputFrame = av_frame_alloc();
		filterGraph = avfilter_graph_alloc();
		
		Filter prev = null;
		for (Filter source : sourceFiltersMap.values()) {
			source.initFilterContex(filterGraph);
			if(prev == null) {
				//this means first filter so get its output as listOfOutputs and link others' to it
				listOfOutputs = source.getFilterInOut();
			}
			else {
				prev.getFilterInOut().next(source.getFilterInOut());
			}
			prev = source;
		}
		
		prev = null;
		for (Filter sink : this.sinkFiltersMap.values()) {
			sink.initFilterContex(filterGraph);
			if(prev == null) {
				//this means first filter so get its input as listOfInputs and link others' to it
				listOfInputs = sink.getFilterInOut();
			}
			else {
				prev.getFilterInOut().next(sink.getFilterInOut());
			}
			prev = sink;
		}
		
		int ret;
		if ((ret = avfilter_graph_parse(filterGraph, filterDescription, listOfInputs, listOfOutputs, null)) < 0) {
			
			logger.error("error avfilter_graph_parse: {}", Utils.getErrorDefinition(ret));
			return;
		}

		if ((ret = avfilter_graph_config(filterGraph, null)) < 0) {
			logger.error("error avfilter_graph_config: {}", Utils.getErrorDefinition(ret));
			return;
		}
		setInitiated(true);
	}
	
	public synchronized void prepareFrame(AVFrame frame) 
	{
		if (picture == null || picture.width() != frame.width() || picture.height() != frame.height() || picture.format() != frame.format()) {
			if (picture != null) {
				freeSwsResources();
			}
			
			picture = av_frame_alloc();
			int size = av_image_get_buffer_size(frame.format(), frame.width(), frame.height(), 32);
			pictureBufptr = new BytePointer(size);
	
			// Assign appropriate parts of buffer to image planes in picture_rgb
			// Note that picture_rgb is an AVFrame, but AVFrame is a superset of AVPicture
			av_image_fill_arrays(picture.data(), picture.linesize(), pictureBufptr, frame.format(), frame.width(), frame.height(), 32);
			picture.format(frame.format());
			picture.width(frame.width());
			picture.height(frame.height());
		}
	}
	
	
	
	public synchronized AVFrame resetIfRequired(AVFrame frame) {
		
		if (frame.linesize(0) < 0) 
		{
			prepareFrame(frame);
			swsCtx = sws_getCachedContext(swsCtx, frame.width(), frame.height(), frame.format(),
					frame.width(), frame.height(), frame.format(),
					SWS_BILINEAR|SWS_ACCURATE_RND, null, null, (DoublePointer)null);
	
			sws_scale(swsCtx, frame.data(), frame.linesize(),
					0, frame.height(), picture.data(), picture.linesize());
	
			picture.pts(frame.pts());
			
			return picture;
		}
		return frame;
		
	}
	
	/**
	 * 
	 * @param streamId
	 * @param frame
	 * @param sync true to 
	 * @return filtered AVFrame if sync is true, return null if synch is false
	 */
	public AVFrame doFilter(String streamId, AVFrame frame, boolean sync) {
		synchronized(lock) {
			
			if (!isInitiated()) 
			{
				logger.warn("Filter graph is not initated yet for stream:{}", streamId);
				return null;
			}
						
			int ret;
			
			
			Filter sourceFilter = sourceFiltersMap.get(streamId);
			// TODO this check is for such a case:
			// new filtergraph that contains streamX is under construction
			// but frame for streamX comes the previous filtergraph
			if(sourceFilter == null) {
				logger.warn("Source filter is null so no filter will applied to stream:{}", streamId);
				return null;
			}
			
			if(sourceFilter.isFirstFrame && frame != null) {
				sourceFilter.offset = currentPts - frame.pts();
				sourceFilter.isFirstFrame = false;
			}
			if (frame != null) {
				long allignedPts = frame.pts() + sourceFilter.offset;
				frame.pts(allignedPts);
				
				currentPts = Math.max(allignedPts, currentPts);
			}
			
			/* push the decoded frame into the filtergraph */
			if ((ret = av_buffersrc_add_frame_flags(sourceFiltersMap.get(streamId).filterContext, frame, AV_BUFFERSRC_FLAG_PUSH)) < 0) {
				logger.error("Error while feeding the filtergraph {}", ret);
				return null;
			}

			for (String outStreamId : sinkFiltersMap.keySet()) 
			{
				// get filtered frame for each output
				while(true) {
					ret = av_buffersink_get_frame(sinkFiltersMap.get(outStreamId).filterContext, filterOutputFrame);

					
					if (ret < 0) 
					{
						if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF()) {
							logger.error("Error in filter for av_buffersink_get_frame return:{} ", Utils.getErrorDefinition(ret));
						}
						break;
						
					}
					else {
						if (sync) 
						{
							//TODO: There may be some memory leak in this usage
							//TODO: What if there are more than one output streams
							if(streamId.equals(outStreamId)) {
								if (filterOutputFrame.linesize(0) < 0) 
								{
									return resetIfRequired(filterOutputFrame);
								}
								else {
									return filterOutputFrame;
								}
								
							}
						}
						else {
							if (filterOutputFrame.linesize(0) < 0) {
								AVFrame tmpFrame = resetIfRequired(filterOutputFrame);
								listener.onFilteredFrame(outStreamId, tmpFrame);
							}
							else {
								listener.onFilteredFrame(outStreamId, filterOutputFrame);
							}
							logger.debug("Filtered frame is sent to listener for streamId: {} and for filter config:{}", outStreamId, filterDescription);
						}
					}
					av_frame_unref(filterOutputFrame);
				}
			}
		}
		
		return null;
	}

	public void setListener(IFilteredFrameListener listener) {
		this.listener = listener;
	}
	
	public IFilteredFrameListener getListener() {
		return listener;
	}
	
	public void freeSwsResources() {
		if (swsCtx != null) {
			sws_freeContext(swsCtx);
			swsCtx.close();
			swsCtx = null;
		}

		if (pictureBufptr != null) {
			//we don't free with av_free because we keep it on java side(not using malloc)
			pictureBufptr.close();
			pictureBufptr = null;
		}
		if (picture != null) {
			av_frame_free(picture);
			picture.close();
			picture = null;
		}
	}

	public void close() {
		synchronized(lock) {
			setInitiated(false);

			avfilter_graph_free(filterGraph);
			filterGraph = null;

			av_frame_free(filterOutputFrame);
			filterOutputFrame = null;

			sourceFiltersMap.clear();
			sinkFiltersMap.clear();
			
			freeSwsResources();
			
		}
	}

	public long getCurrentPts() {
		return currentPts;
	}

	public void setCurrentPts(long currentPts) {
		this.currentPts = currentPts;
	}

	public boolean isInitiated() {
		return initiated;
	}

	public void setInitiated(boolean initiated) {
		this.initiated = initiated;
	}

	public AVFrame getPicture() {
		return picture;
	}
}
