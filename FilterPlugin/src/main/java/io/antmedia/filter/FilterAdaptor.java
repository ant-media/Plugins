package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_CH_LAYOUT_MONO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_clone;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.FilterGraph;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.vertx.core.Vertx;

/**
 * Filter Adaptor get audio and video frames/packets and feeds the FilterGraph
 * 
 * FilterGraph uses Filter, FilterConfiguration, FilterGraph for filtering operation. 
 * It receives the output of FilterGraph and feeds the output through IFilteredFrameListener
 *
 */

public class FilterAdaptor implements IFrameListener, IPacketListener{
	private FilterGraph videoFilterGraph = null;
	private FilterGraph audioFilterGraph = null;

	List<String> currentInStreams = new ArrayList<>();
	Map<String, IFrameListener> currentOutStreams = new LinkedHashMap<>();

	// stream Id to video/audio stream params map
	private Map<String, StreamParametersInfo> videoStreamParamsMap = new LinkedHashMap<>();
	private Map<String, StreamParametersInfo> audioStreamParamsMap = new LinkedHashMap<>();
	private FilterConfiguration filterConfiguration;
	private Vertx vertx;
	private static final Logger logger = LoggerFactory.getLogger(FilterAdaptor.class);
	
	private Map<String, VideoDecoder> videoDecodersMap = new LinkedHashMap<>();

	
	private static final int WIDTH = 720;
	private static final int HEIGHT = 480;
	private boolean selfDecodeStreams = true;

	public FilterAdaptor(boolean selfDecodeVideo) {
		this.selfDecodeStreams  = selfDecodeVideo;
	}

	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
		if(audioFilterGraph == null || !audioFilterGraph.isInitiated() || audioFilterGraph.getListener() == null) {
			return audioFrame;
		}
		AVFrame filterInputframe;
		AVFrame filterOutputFrame = null;
		if(filterConfiguration.getType().equals(FilterConfiguration.ASYNCHRONOUS)) {
			//copy the input frame then return it immediately
			filterInputframe = av_frame_clone(audioFrame);

			vertx.executeBlocking(a->{
				audioFilterGraph.doFilter(streamId, filterInputframe);
				av_frame_free(filterInputframe);
			},b->{});
			filterOutputFrame = audioFrame;
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.LASTPOINT)) {
			filterInputframe = audioFrame;
			vertx.executeBlocking(a->{
				audioFilterGraph.doFilter(streamId, filterInputframe);
			},b->{});

			filterOutputFrame = null; //lastpoint
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.SYNCHRONOUS)){
			filterInputframe = audioFrame;
			filterOutputFrame = audioFilterGraph.doFilterSync(streamId, filterInputframe);
		}
		return filterOutputFrame;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		if(videoFilterGraph == null || !videoFilterGraph.isInitiated() || videoFilterGraph.getListener() == null) {
			return videoFrame;
		}
		AVFrame filterInputframe;
		AVFrame filterOutputFrame = null;
		
		if(videoFrame.width() != videoStreamParamsMap.get(streamId).codecParameters.width()
				&& videoFrame.height() != videoStreamParamsMap.get(streamId).codecParameters.height()) {
			System.out.println("update");
			
			videoStreamParamsMap.get(streamId).codecParameters.width(videoFrame.width());
			videoStreamParamsMap.get(streamId).codecParameters.height(videoFrame.height());

			update();
			
		}
		
		if(filterConfiguration.getType().equals(FilterConfiguration.ASYNCHRONOUS)) {
			//copy the input frame then refilteredVideoFramesturn it immediately
			filterInputframe = av_frame_clone(videoFrame);
			
			vertx.executeBlocking(a->{
				videoFilterGraph.doFilter(streamId, filterInputframe);
				av_frame_free(filterInputframe);
			},b->{});
			filterOutputFrame = videoFrame;
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.LASTPOINT)) {
			filterInputframe = videoFrame;
			filterOutputFrame = null; //lastpoint
			vertx.executeBlocking(a->{
				videoFilterGraph.doFilter(streamId, filterInputframe);
			},b->{});
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.SYNCHRONOUS)){
			filterInputframe = videoFrame;
			filterOutputFrame = videoFilterGraph.doFilterSync(streamId, filterInputframe);
			if(filterOutputFrame.width() == 0) {
				filterOutputFrame = null;
			}

		}
		return filterOutputFrame;
	}
	
	@Override
	public void writeTrailer(String streamId) {
		
	}

	@Override
	public synchronized void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		videoStreamParamsMap.put(streamId, videoStreamInfo);
		
		if(selfDecodeStreams) {
			
			VideoDecoder decoder = new VideoDecoder(streamId, videoStreamInfo);
			if(decoder.isInitialized()) {
				videoDecodersMap.put(streamId, decoder);
			}
		}
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		audioStreamParamsMap.put(streamId, audioStreamInfo);
	}
	
	@Override
	public void start() {
		// TODO Auto-generated method stub
	}

	/*
	 * Update filter graph according to the newly added or removed streams
	 */
	public boolean update() 
	{
		Map<String, Filter> videoSourceFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> videoSinkFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> audioSourceFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> audioSinkFiltersMap = new LinkedHashMap<>();
		int i = 0;
		
		
		//prepare buffer for video and audio frames to feed the filter graph
		for (String streamId : currentInStreams) 
		{
			StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);

			String videoFilterArgs = "video_size="+videoStreamParams.codecParameters.width()+"x"+videoStreamParams.codecParameters.height()+":"
					+ "pix_fmt="+videoStreamParams.codecParameters.format()+":"
					+ "time_base="+1+"/"+1000+":"
					+ "pixel_aspect=1/1";
			
			if(!videoStreamParams.enabled) {
				//define dummy args
				videoFilterArgs = "video_size=360x360:pix_fmt=0:time_base=1/20:pixel_aspect=1/1";
			}
			
			videoSourceFiltersMap.put(streamId, new Filter("buffer", videoFilterArgs, "in"+i));


			StreamParametersInfo audioStreamParams = audioStreamParamsMap.get(streamId);
			
			String audioFilterArgs = "channel_layout="+audioStreamParams.codecParameters.channel_layout()+":"
					+ "sample_fmt="+audioStreamParams.codecParameters.format()+":"
					+ "time_base="+1+"/"+audioStreamParams.timeBase.num()+"/"+audioStreamParams.timeBase.den()+":"
					+ "sample_rate="+audioStreamParams.codecParameters.sample_rate();
			
			audioSourceFiltersMap.put(streamId, new Filter("abuffer", audioFilterArgs, "in"+i));
			i++;
		}
		FilterGraph prevVideoFilterGraph = videoFilterGraph;

		
		/*
		 * Buffersinks for video and audio to get the output of the filter graph
		 */
		i = 0;
		for (String streamId : currentOutStreams.keySet()) {
			videoSinkFiltersMap.put(streamId, new Filter("buffersink", null, "out"+i));
			audioSinkFiltersMap.put(streamId, new Filter("abuffersink", null, "out"+i));
			i++;
		}
		
		long currentVideoPts = 0;
		if(videoFilterGraph != null) {
			currentVideoPts = videoFilterGraph.getCurrentPts();
		}
		
		/*
		 * Initialize the video filter graph which does the real job
		 * Filter text and inputs buffer and output buffer sinks are provided as parameter
		 */
		videoFilterGraph = new FilterGraph(filterConfiguration.getVideoFilter(), videoSourceFiltersMap , videoSinkFiltersMap);
		if(!videoFilterGraph.isInitiated()) {
			logger.error("Video filter graph can not be initiated: {}", filterConfiguration.getVideoFilter());
			return false;
		}
		videoFilterGraph.setCurrentPts(currentVideoPts);
		
		/*
		 * Set the listener of video filter graph. FilterGrapah calls the listener for the filtered output frame
		 */
		videoFilterGraph.setListener((streamId, frame)->{
			if(frame != null && currentOutStreams.containsKey(streamId)) {
				IFrameListener frameListener = currentOutStreams.get(streamId);
				if(frameListener != null) { 
					//framelistener is a custombroadcast
					frameListener.onVideoFrame(streamId, frame);
				}
			}
		});
		
		if(prevVideoFilterGraph != null) {
			prevVideoFilterGraph.close();
		}
		
		FilterGraph prevAudioFilterGraph = audioFilterGraph;
		long currentAudioPts = 0;

		
		if(audioFilterGraph != null) {
			currentAudioPts = audioFilterGraph.getCurrentPts();
		}
		
		audioFilterGraph = new FilterGraph(filterConfiguration.getAudioFilter(), audioSourceFiltersMap , audioSinkFiltersMap);
		if(!audioFilterGraph.isInitiated()) {
			logger.error("Audio filter graph can not be initiated:{}", filterConfiguration.getAudioFilter());
			return false;
		}
		audioFilterGraph.setCurrentPts(currentAudioPts);
		audioFilterGraph.setListener((streamId, frame)->{
			if(frame != null && currentOutStreams.containsKey(streamId)) {
				IFrameListener frameListener = currentOutStreams.get(streamId);
				if(frameListener != null) { 
					//framelistener is a custombroadcast
					frameListener.onAudioFrame(streamId, frame);
				}
			}
		});
		
		if(prevAudioFilterGraph != null) {
			prevAudioFilterGraph.close();
		}
		
		return true;
	}
	
	/*
	 * This method is used for the creation and also for the update of the filter
	 * For example new inputs mat be added as an update
	 */
	
	public synchronized boolean createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter app) {
		if(vertx == null) {
			vertx = app.getVertx();
		}
		
		this.filterConfiguration = filterConfiguration;
		
		/*
		 * create custom broadcast for each output and add them to the map
		 *  if an outputstream is same with an input stream no need the create custombroadcast 
		 * 		instead we feed input stream with filtered frame
		 */
		for (String streamId : filterConfiguration.getOutputStreams()) {
			if(!currentOutStreams.containsKey(streamId)) {
				if(filterConfiguration.getInputStreams().contains(streamId)) {
					currentOutStreams.put(streamId, null);
				}
				else {
					IFrameListener broadcast = app.createCustomBroadcast(streamId);
					startBroadcast(streamId, broadcast, filterConfiguration.isVideoEnabled(), filterConfiguration.isAudioEnabled());
					currentOutStreams.put(streamId, broadcast);
				}
			}		
		}

		
		// check the inseted or removed streams to the filter as an update
		List<String> inserted = filterConfiguration.getInputStreams();
		List<String> removed = new ArrayList<>();
		for (String streamId : currentInStreams) {
			if(filterConfiguration.getInputStreams().contains(streamId)) {
				inserted.remove(streamId);
			}
			else {
				removed.add(streamId);
			}
		}
		
		boolean noChange = inserted.isEmpty() && removed.isEmpty();
		
		if(!noChange) {
			
			// deregister plugin for the streams removed from filter
			for (String streamId : removed) {
				app.removeFrameListener(streamId, this);
				currentInStreams.remove(streamId);
			}
			
			// register plugin for the streams inserted to the filter
			for (String streamId : inserted) {
				
				if(selfDecodeStreams) {
					app.addPacketListener(streamId, this);
				}
				else {
					app.addFrameListener(streamId, this);
				}
				currentInStreams.add(streamId);
			}
			
			filterConfiguration.setInputStreams(currentInStreams);
		}
		
		//we need to update the filter graph to make the configuration changes will be effective
		return update();
	}
	
	private void startBroadcast(String streamId, IFrameListener broadcast, boolean videoEnabled, boolean audioEnabled) {
		AVCodecParameters videoCodecParameters = new AVCodecParameters();
		videoCodecParameters.width(WIDTH);
		videoCodecParameters.height(HEIGHT);
		videoCodecParameters.codec_id(AV_CODEC_ID_H264);
		videoCodecParameters.codec_type(AVMEDIA_TYPE_VIDEO);		
		videoCodecParameters.format(AV_PIX_FMT_YUV420P);
		videoCodecParameters.codec_tag(0);
		
		AVCodecParameters audioCodecParameters = new AVCodecParameters();
		//audioCodecParameters.codec_id(AV_CODEC_ID_OPUS);
		audioCodecParameters.codec_id(AV_CODEC_ID_AAC);
		
		audioCodecParameters.codec_type(AVMEDIA_TYPE_AUDIO);		
		audioCodecParameters.format(AV_SAMPLE_FMT_FLTP);
		audioCodecParameters.sample_rate(44100);
		//audioCodecParameters.sample_rate(48000);
		audioCodecParameters.channels(1);
		audioCodecParameters.channel_layout(AV_CH_LAYOUT_MONO);
		
		audioCodecParameters.codec_tag(0);
		
		StreamParametersInfo videoStreamParametersInfo = new StreamParametersInfo();
		StreamParametersInfo audioStreamParametersInfo = new StreamParametersInfo();
		
		videoStreamParametersInfo.codecParameters = videoCodecParameters;
		videoStreamParametersInfo.enabled = videoEnabled;
		audioStreamParametersInfo.codecParameters = audioCodecParameters;
		audioStreamParametersInfo.enabled = audioEnabled;
		
		if(videoEnabled) {
			broadcast.setVideoStreamInfo(streamId, videoStreamParametersInfo);
		}
		broadcast.setAudioStreamInfo(streamId, audioStreamParametersInfo);
		broadcast.start();
	}

	public FilterConfiguration getCurrentFilterConfiguration() {
		return filterConfiguration;
	}

	public boolean close(AntMediaApplicationAdapter app) {
		for(String streamId : currentInStreams) {
			app.removeFrameListener(streamId, this);
			app.removePacketListener(streamId, this);
		}
		for (String streamId : filterConfiguration.getOutputStreams()) {
			app.stopCustomBroadcast(streamId);
		}
		videoFilterGraph.close();
		audioFilterGraph.close();

		return true;
		
	}

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket packet) {
		if(selfDecodeStreams) {
			if(videoDecodersMap.containsKey(streamId)) {
				AVFrame frame = videoDecodersMap.get(streamId).decodeVideoPacket(packet);
				if(frame != null) {
					onVideoFrame(streamId, frame);
				}
				else {
				}
			}
			else {
				logger.warn("Decoder is not initialized for {}", streamId);
			}
		}
		return packet;
	}
	
	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		return packet;
	}

	public void setVideoFilterGraphForTest(FilterGraph filterGraph) {
		this.videoFilterGraph = filterGraph;
	}

	public void setAudioFilterGraphForTest(FilterGraph filterGraph) {
		this.audioFilterGraph = filterGraph;
	}

}
