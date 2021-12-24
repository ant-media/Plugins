package io.antmedia.filter.utils;


import static org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_frame;
import static org.bytedeco.ffmpeg.global.avfilter.*;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_alloc;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_config;
import static org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_parse;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_ref;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;


import java.util.Map;

import org.bytedeco.ffmpeg.avfilter.AVFilterGraph;
import org.bytedeco.ffmpeg.avfilter.AVFilterInOut;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private Object lock = new Object();
	private boolean initiated = false;
	private IFilteredFrameListener listener;

	private long currentPts = 0; 


	public FilterGraph(String filterDescription, Map<String, Filter> sourceFiltersMap, Map<String, Filter> sinkFiltersMap) {
		this.sourceFiltersMap = sourceFiltersMap;
		this.sinkFiltersMap = sinkFiltersMap;
		
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
		
		if (avfilter_graph_parse(filterGraph, filterDescription, listOfInputs, listOfOutputs, null) < 0) {
			logger.error("error avfilter_graph_parse");
			return;
		}

		if (avfilter_graph_config(filterGraph, null) < 0) {
			logger.error("error avfilter_graph_config");
			return;
		}
		setInitiated(true);
	}
	
	public void doFilter(String streamId, AVFrame frame) {
		synchronized(lock) {
			if (!isInitiated()) return;
			
			int ret;
			
			Filter sourceFilter = sourceFiltersMap.get(streamId);
			// TODO this check is for such a case:
			// new filtergraph that contains streamX is under construction
			// but frame for streamX comes the previous filtergraph
			if(sourceFilter == null) {
				return;
			}
			
			if(sourceFilter.isFirstFrame) {
				sourceFilter.offset = currentPts - frame.pts();
				sourceFilter.isFirstFrame = false;
			}
			long allignedPts = frame.pts()+sourceFilter.offset;
			frame.pts(allignedPts);
			
			currentPts = Math.max(allignedPts, currentPts);
			
			/* push the decoded frame into the filtergraph */
			if ((ret = av_buffersrc_add_frame(sourceFiltersMap.get(streamId).filterContext, frame)) < 0) {
				logger.error("Error while feeding the filtergraph "+ret);
				return;
			}

			for (String outStreamId : sinkFiltersMap.keySet()) {
				// get filtered frame for each output
				while(true) {
					ret = av_buffersink_get_frame(sinkFiltersMap.get(outStreamId).filterContext, filterOutputFrame);

					if (ret < 0) {
						break;
					}
					else {
						//Utils.save(filterOutputFrame, "out"+(count++));
						listener.onFilteredFrame(outStreamId, filterOutputFrame);
					}
					av_frame_unref(filterOutputFrame);
				}
			}
		}
	}
	
	
	public AVFrame doFilterSync(String streamId, AVFrame frame) {
		synchronized(lock) {
			if (!isInitiated()) return null;
			
			int ret;
			
			Filter sourceFilter = sourceFiltersMap.get(streamId);
			// TODO this check is for such a case:
			// new filtergraph that contains streamX is under construction
			// but frame for streamX comes the previous filtergraph
			if(sourceFilter == null) {
				return null;
			}
			
			if(sourceFilter.isFirstFrame) {
				sourceFilter.offset = currentPts - frame.pts();
				sourceFilter.isFirstFrame = false;
			}
			long allignedPts = frame.pts()+sourceFilter.offset;
			frame.pts(allignedPts);
			
			currentPts = Math.max(allignedPts, currentPts);
			
			/* push the decoded frame into the filtergraph */
			if ((ret = av_buffersrc_add_frame(sourceFiltersMap.get(streamId).filterContext, frame)) < 0) {
				logger.error("Error while feeding the filtergraph "+ret);
				return null;
			}

			for (String outStreamId : sinkFiltersMap.keySet()) {
				// get filtered frame for each output
				while(true) {
					ret = av_buffersink_get_frame(sinkFiltersMap.get(outStreamId).filterContext, filterOutputFrame);

					if (ret < 0) {
						break;
					}
					else {
						if(streamId.equals(outStreamId)) {
							return filterOutputFrame;
						}
						else {
							return null;
						}
					}
				}
			}
		}
		return null;
	}

	public void setListener(IFilteredFrameListener listener) {
		this.listener = listener;
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
}
