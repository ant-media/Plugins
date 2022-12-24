package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_ref;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_CH_LAYOUT_MONO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_clone;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q_rnd;
import static org.bytedeco.ffmpeg.global.avutil.AV_ROUND_NEAR_INF;
import static org.bytedeco.ffmpeg.global.avutil.AV_ROUND_PASS_MINMAX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.FilterGraph;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.antmedia.rest.model.Result;
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

	Map<String, IFrameListener> currentOutStreams = new LinkedHashMap<>();

	// stream Id to video/audio stream params map
	private Map<String, StreamParametersInfo> videoStreamParamsMap = new LinkedHashMap<>();
	private Map<String, StreamParametersInfo> audioStreamParamsMap = new LinkedHashMap<>();
	private FilterConfiguration filterConfiguration;
	private Vertx vertx;
	private static final Logger logger = LoggerFactory.getLogger(FilterAdaptor.class);
	
	private Map<String, VideoDecoder> videoDecodersMap = new LinkedHashMap<>();
	private Map<String, OpusDecoder> audioDecodersMap = new LinkedHashMap<>();

	
	private static final int WIDTH = 720;
	private static final int HEIGHT = 480;
	private boolean selfDecodeStreams = true;
	/*
	 * In case decoding in the plugin, video frames are generated later than audio.
	 * So this parameters is used to provide synchronization of video and audio frames.
	 */
	private long audioVideoOffset; 
	private boolean firstVideoReceived = false;;

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
			long orgPts = filterInputframe.pts();
			filterOutputFrame = audioFilterGraph.doFilterSync(streamId, filterInputframe);
			filterOutputFrame.pts(orgPts);
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
		
		StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);
		
		if(videoFrame.width() != videoStreamParams.getCodecParameters().width()
				&& videoFrame.height() != videoStreamParams.getCodecParameters().height()) {
			
			videoStreamParams.getCodecParameters().width(videoFrame.width());
			videoStreamParams.getCodecParameters().height(videoFrame.height());

			update();
		}
		
		if(filterConfiguration.getType().equals(FilterConfiguration.ASYNCHRONOUS)) {
			//copy the input frame then refilteredVideoFramesturn it immediately
			filterInputframe = av_frame_clone(videoFrame);
			rescaleFramePtsToMs(filterInputframe, videoStreamParams.getTimeBase());
			
			vertx.executeBlocking(a->{
				videoFilterGraph.doFilter(streamId, filterInputframe);
				av_frame_free(filterInputframe);
			},b->{});
			filterOutputFrame = videoFrame;
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.LASTPOINT)) {
			filterInputframe = videoFrame;
			rescaleFramePtsToMs(filterInputframe, videoStreamParams.getTimeBase());
			
			filterOutputFrame = null; //lastpoint
			vertx.executeBlocking(a->{
				videoFilterGraph.doFilter(streamId, filterInputframe);
			},b->{});
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.SYNCHRONOUS)){
			filterInputframe = videoFrame;
			long orgPts = filterInputframe.pts();
			rescaleFramePtsToMs(filterInputframe, videoStreamParams.getTimeBase());
			filterOutputFrame = videoFilterGraph.doFilterSync(streamId, filterInputframe);
			if(filterOutputFrame.width() == 0) {
				filterOutputFrame = null;
			}
			filterOutputFrame.pts(orgPts);

		}
		return filterOutputFrame;
	}

	public void rescaleFramePtsToMs(AVFrame filterInputframe, AVRational timebase) {
		if(!selfDecodeStreams) {
			filterInputframe.pts(av_rescale_q_rnd(filterInputframe.pts(), timebase, Utils.TIME_BASE_FOR_MS, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
		}
	}
	
	@Override
	public void writeTrailer(String streamId) {
		
	}

	@Override
	public synchronized void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		videoStreamParamsMap.put(streamId, videoStreamInfo);
		
		if(selfDecodeStreams || videoStreamInfo.isHostedInOtherNode()) {
			
			VideoDecoder decoder = new VideoDecoder(streamId, videoStreamInfo);
			if(decoder.isInitialized()) {
				videoDecodersMap.put(streamId, decoder);
			}
		}
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		audioStreamParamsMap.put(streamId, audioStreamInfo);
		
		if(audioStreamInfo.isHostedInOtherNode()) {
			OpusDecoder decoder = new OpusDecoder();
			decoder.prepare(streamId);
			if(decoder.isInitialized()) {
				audioDecodersMap.put(streamId, decoder);
			}
		}
	}
	
	@Override
	public void start() {
		// TODO Auto-generated method stub
	}

	/*
	 * Update filter graph according to the newly added or removed streams
	 */
	public Result update() 
	{
		
		Result result = new Result(false);
		
		Map<String, Filter> videoSourceFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> videoSinkFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> audioSourceFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> audioSinkFiltersMap = new LinkedHashMap<>();
		int i = 0;
		
		
		//prepare buffer for video and audio frames to feed the filter graph
		for (String streamId : filterConfiguration.getInputStreams()) 
		{
			StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);
			if(videoStreamParams.isEnabled()) {

				String videoFilterArgs = "video_size="+videoStreamParams.getCodecParameters().width()+"x"+videoStreamParams.getCodecParameters().height()+":"
						+ "pix_fmt="+videoStreamParams.getCodecParameters().format()+":"
						+ "time_base="+Utils.TIME_BASE_FOR_MS.num()+"/"+Utils.TIME_BASE_FOR_MS.den()+":"
						+ "pixel_aspect=1/1";


				if(filterConfiguration.getVideoFilter().contains("["+"in"+i+"]")) {
					videoSourceFiltersMap.put(streamId, new Filter("buffer", videoFilterArgs, "in"+i));
				}
			}

			StreamParametersInfo audioStreamParams = audioStreamParamsMap.get(streamId);
			
			if(audioStreamParams.isEnabled()) {
				String audioFilterArgs = "channel_layout="+audioStreamParams.getCodecParameters().channel_layout()+":"
						+ "sample_fmt="+audioStreamParams.getCodecParameters().format()+":"
						+ "time_base="+audioStreamParams.getTimeBase().num()+"/"+audioStreamParams.getTimeBase().den()+":"
						+ "sample_rate="+audioStreamParams.getCodecParameters().sample_rate();
				
				if(filterConfiguration.getAudioFilter().contains("["+"in"+i+"]")) {
					audioSourceFiltersMap.put(streamId, new Filter("abuffer", audioFilterArgs, "in"+i));
				}
			}

			i++;
		}
		
		/*
		 * Buffersinks for video and audio to get the output of the filter graph
		 */
		i = 0;
		for (String streamId : currentOutStreams.keySet()) {
			videoSinkFiltersMap.put(streamId, new Filter("buffersink", null, "out"+i));
			audioSinkFiltersMap.put(streamId, new Filter("abuffersink", null, "out"+i));
			i++;
		}
		
		if(filterConfiguration.isVideoEnabled()) {
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
				result.setMessage("Video filter graph can not be initiated: "+filterConfiguration.getVideoFilter());
				logger.error("Video filter graph can not be initiated: {}", filterConfiguration.getVideoFilter());
				return result;
			}
			videoFilterGraph.setCurrentPts(currentVideoPts);

			/*
			 * Set the listener of video filter graph. FilterGraph calls the listener for the filtered output frame
			 */
			videoFilterGraph.setListener((streamId, frame)->{
				if(frame != null && currentOutStreams.containsKey(streamId)) {
					IFrameListener frameListener = currentOutStreams.get(streamId);
					if(frameListener != null) { 
						if(!firstVideoReceived) {
							firstVideoReceived = true;
						}
						//framelistener is a custombroadcast
						frameListener.onVideoFrame(streamId, frame);
					}
				}
			});
		}
		
		if(filterConfiguration.isAudioEnabled()) {
			long currentAudioPts = 0;

			if(audioFilterGraph != null) {
				currentAudioPts = audioFilterGraph.getCurrentPts();
			}

			audioFilterGraph = new FilterGraph(filterConfiguration.getAudioFilter(), audioSourceFiltersMap , audioSinkFiltersMap);
			if(!audioFilterGraph.isInitiated()) {
				result.setMessage("Audio filter graph can not be initiated: "+filterConfiguration.getVideoFilter());
				logger.error("Audio filter graph can not be initiated:{}", filterConfiguration.getAudioFilter());
				return result;
			}
			audioFilterGraph.setCurrentPts(currentAudioPts);
			audioFilterGraph.setListener((streamId, frame)->{
				if(frame != null && currentOutStreams.containsKey(streamId)) {
					IFrameListener frameListener = currentOutStreams.get(streamId);
					if(frameListener != null) { 
						if(!firstVideoReceived) {
							audioVideoOffset = frame.pts();
						}
						else {
							frame.pts(frame.pts()-audioVideoOffset);						
							
							//framelistener is a custombroadcast
							frameListener.onAudioFrame(streamId, frame);
						}
					}
				}
			});
		}
		
		result.setSuccess(true);
		result.setDataId(filterConfiguration.getFilterId());
		result.setMessage("Filter with id:" + filterConfiguration.getFilterId() + " is updated");
		
		return result;
	}
	
	/*
	 * This method is used for the creation and also for the update of the filter
	 * For example new inputs mat be added as an update
	 */
	
	public synchronized Result startFilterProcess(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter app) {
		if(vertx == null) {
			vertx = app.getVertx();
		}
		
		Result result = new Result(false);
		
		this.filterConfiguration = filterConfiguration;
		
		// Check database if stream is not exist or stream is not broadcasting status
   		for(String streamId : filterConfiguration.getInputStreams()) {
   			Broadcast broadcast = app.getDataStore().get(streamId);
   			if(broadcast == null || (broadcast != null && !broadcast.getStatus().contains(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING))) {
   				result.setMessage("Filter saved. But input stream ID: "+ streamId +" is not actively streaming");
   				return result;
   			}
   		}
   		for(String streamId : filterConfiguration.getOutputStreams()) {
   			Broadcast broadcast = app.getDataStore().get(streamId);
   			if(broadcast != null && broadcast.getStatus().contains(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING)) {
   				result.setMessage("Filter saved but output stream ID: "+ streamId +" is already broadcasting");
   				return result;
   			}
  		}
    	
		/*
		 * create custom broadcast for each output and add them to the map
		 *  if an outputstream is same with an input stream no need the create custombroadcast 
		 * 		instead we feed input stream with filtered frame
		 */
		for (String streamId : filterConfiguration.getOutputStreams()) {
					IFrameListener broadcast = app.createCustomBroadcast(streamId);
					startBroadcast(streamId, broadcast, filterConfiguration.isVideoEnabled(), filterConfiguration.isAudioEnabled());
					currentOutStreams.put(streamId, broadcast);
		}

		// register plugin for the streams inserted to the filter
			for (String streamId : filterConfiguration.getInputStreams()) {
				
				if(selfDecodeStreams) {
					app.addFrameListener(streamId, this); //to get decoded audioframes
					app.addPacketListener(streamId, this); //to get video packets
				}
				else {
					app.addFrameListener(streamId, this);
				}
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
		
		videoStreamParametersInfo.setCodecParameters(videoCodecParameters);
		videoStreamParametersInfo.setEnabled(videoEnabled);
		audioStreamParametersInfo.setCodecParameters(audioCodecParameters);
		audioStreamParametersInfo.setEnabled(audioEnabled);
		
		if(videoEnabled) {
			videoStreamParametersInfo.setTimeBase(Utils.TIME_BASE_FOR_MS);
			broadcast.setVideoStreamInfo(streamId, videoStreamParametersInfo);
		}
		if(audioEnabled) {
			broadcast.setAudioStreamInfo(streamId, audioStreamParametersInfo);
		}
		broadcast.start();
	}

	public FilterConfiguration getCurrentFilterConfiguration() {
		return filterConfiguration;
	}

	public boolean close(AntMediaApplicationAdapter app) {
		for(String streamId : filterConfiguration.getInputStreams()) {
			app.removeFrameListener(streamId, this);
			app.removePacketListener(streamId, this);
		}
		for (String streamId : filterConfiguration.getOutputStreams()) {
			app.stopCustomBroadcast(streamId);
		}
		
		if(getVideoFilterGraph() != null) {
			getVideoFilterGraph().close();
		}
		if (getAudioFilterGraph() != null){
			getAudioFilterGraph().close();
		}

		return true;
		
	}

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket packet) {
		StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);
		if(selfDecodeStreams || (videoStreamParams != null && videoStreamParams.isHostedInOtherNode())) {
			if(videoDecodersMap.containsKey(streamId)) {
				AVPacket tempPacket = new AVPacket();
				av_packet_ref(tempPacket, packet);
				vertx.executeBlocking(l->{
					AVFrame frame = videoDecodersMap.get(streamId).decodeVideoPacket(tempPacket);
					av_packet_unref(tempPacket);
					tempPacket.close();
					if(frame != null) {
						onVideoFrame(streamId, frame);
					}
				});
			}
			else {
				logger.warn("Decoder is not initialized for {}", streamId);
			}
		}
		return packet;
	}
	
	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		StreamParametersInfo audioStreamParams = audioStreamParamsMap.get(streamId);
		//decode audio if only if it comes from another node in cluster mode.
		if(audioStreamParams != null && audioStreamParams.isHostedInOtherNode()) {
			if(audioDecodersMap.containsKey(streamId)) {
				AVFrame frame = audioDecodersMap.get(streamId).decode(packet);

				if(frame != null) {
					onAudioFrame(streamId, frame);
				}
				else {
				}
			}
			else {
				logger.warn("Opus Decoder is not initialized for {}", streamId);
			}
		}
		return packet;
	}

	public void setVideoFilterGraphForTest(FilterGraph filterGraph) {
		this.videoFilterGraph = filterGraph;
	}

	public void setAudioFilterGraphForTest(FilterGraph filterGraph) {
		this.audioFilterGraph = filterGraph;
	}
	
	public FilterGraph getVideoFilterGraph() {
		return videoFilterGraph;
	}
	public FilterGraph getAudioFilterGraph() {
		return audioFilterGraph;
	}

}
