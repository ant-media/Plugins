package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_ref;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_ROUND_NEAR_INF;
import static org.bytedeco.ffmpeg.global.avutil.AV_ROUND_PASS_MINMAX;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_clone;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q_rnd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.global.avutil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.FilterGraph;
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

	List<String> currentInStreams = new ArrayList<>();
	Map<String, IFrameListener> currentOutStreams = new LinkedHashMap<>();

	// stream Id to video/audio stream params map
	private Map<String, StreamParametersInfo> videoStreamParamsMap = new LinkedHashMap<>();
	private Map<String, StreamParametersInfo> audioStreamParamsMap = new LinkedHashMap<>();
	private FilterConfiguration filterConfiguration;
	private Vertx vertx;
	private static final Logger logger = LoggerFactory.getLogger(FilterAdaptor.class);

	private Map<String, VideoDecoder> videoDecodersMap = new LinkedHashMap<>();
	private Map<String, OpusDecoder> audioDecodersMap = new LinkedHashMap<>();

	private boolean selfDecodeStreams = true;
	/*
	 * In case decoding in the plugin, video frames are generated later than audio.
	 * So this parameters is used to provide synchronization of video and audio frames.
	 */
	private long audioVideoOffset; 
	private boolean firstVideoReceived = false;
	private AVChannelLayout channelLayout;
	private String filterId;

	public FilterAdaptor(String filterId, boolean selfDecodeVideo) {
		this.selfDecodeStreams  = selfDecodeVideo;
		this.filterId = filterId;
	}

	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {

		if(audioFilterGraph == null || !audioFilterGraph.isInitiated() || audioFilterGraph.getListener() == null) {
			//logger.warn("AudioFilter graph is not initialized correctly so returning frame for stream:{} and filter:{}", streamId, filterId);
			
			return audioFrame;
		}
		AVFrame filterInputframe;
		AVFrame filterOutputFrame = null;
		if(filterConfiguration.getType().equals(FilterConfiguration.ASYNCHRONOUS)) {
			//copy the input frame then return it immediately
			filterInputframe = av_frame_clone(audioFrame);

			vertx.executeBlocking(a->{
				audioFilterGraph.doFilter(streamId, filterInputframe, false);
				av_frame_free(filterInputframe);
			},b->{});
			filterOutputFrame = audioFrame;
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.LASTPOINT)) 
		{
			filterInputframe = audioFrame;
			vertx.executeBlocking(a->{
				audioFilterGraph.doFilter(streamId, filterInputframe, false);
			},b->{});

			filterOutputFrame = null; //lastpoint
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.SYNCHRONOUS))
		{
			filterInputframe = audioFrame;
			long orgPts = filterInputframe.pts();
			filterOutputFrame = audioFilterGraph.doFilter(streamId, filterInputframe, true);
			if(filterOutputFrame != null) 
			{
				
				filterOutputFrame.pts(orgPts);
				
			}
		}
		return filterOutputFrame;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		if(videoFilterGraph == null || !videoFilterGraph.isInitiated() || videoFilterGraph.getListener() == null) {
			logger.warn("Videofilter graph is not initialized correctly so returning frame for stream:{} and filter:{}", streamId, filterId);
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

		if(filterConfiguration.getType().equals(FilterConfiguration.ASYNCHRONOUS)) 
		{
			//copy the input frame then refilteredVideoFramesturn it immediately
			filterInputframe = av_frame_clone(videoFrame);
			rescaleFramePtsToMs(filterInputframe, videoStreamParams.getTimeBase());

			vertx.executeBlocking(a->{
				videoFilterGraph.doFilter(streamId, filterInputframe, false);
				av_frame_free(filterInputframe);
			},b->{});
			filterOutputFrame = videoFrame;
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.LASTPOINT)) 
		{
			filterInputframe = av_frame_clone(videoFrame);
			rescaleFramePtsToMs(filterInputframe, videoStreamParams.getTimeBase());

			filterOutputFrame = null; //lastpoint

			vertx.executeBlocking(a->{
				videoFilterGraph.doFilter(streamId, filterInputframe, false);
				av_frame_free(filterInputframe);
			}, b->{});
		}
		else if(filterConfiguration.getType().equals(FilterConfiguration.SYNCHRONOUS))
		{
			filterInputframe = videoFrame;
			long orgPts = filterInputframe.pts();

			rescaleFramePtsToMs(filterInputframe, videoStreamParams.getTimeBase());

			filterOutputFrame = videoFilterGraph.doFilter(streamId, filterInputframe, true);

			if(filterOutputFrame != null) 
			{
				if (filterOutputFrame.width() == 0) 
				{
					filterOutputFrame = null;
					logger.warn("Sync filter output has not created frame for streamId:{} and filterId:{}", streamId, filterId);
				}
				else {
					filterOutputFrame.pts(orgPts);
				}
			}

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
	public synchronized void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
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
		Map<String, Filter> videoSourceFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> videoSinkFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> audioSourceFiltersMap = new LinkedHashMap<>();
		Map<String, Filter> audioSinkFiltersMap = new LinkedHashMap<>();
		int i = 0;

		Result result = new Result(false);
		//prepare buffer for video and audio frames to feed the filter graph
		for (String streamId : currentInStreams) 
		{
			StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);
			if(videoStreamParams.isEnabled()) {

				String videoFilterArgs = "video_size="+videoStreamParams.getCodecParameters().width()+"x"+videoStreamParams.getCodecParameters().height()+":"
						+ "pix_fmt="+videoStreamParams.getCodecParameters().format()+":"
						+ "time_base="+Utils.TIME_BASE_FOR_MS.num()+"/"+Utils.TIME_BASE_FOR_MS.den()+":"
						+ "pixel_aspect=1/1";

				logger.info("Input video arguments is \"{}\" for filter:{}", videoFilterArgs, filterId);

				if(filterConfiguration.getVideoFilter().contains("["+"in"+i+"]")) {
					videoSourceFiltersMap.put(streamId, new Filter("buffer", videoFilterArgs, "in"+i));
				}
			}

			StreamParametersInfo audioStreamParams = audioStreamParamsMap.get(streamId);

			if(audioStreamParams.isEnabled()) {
				byte[] channelLayoutData = new byte[64];

				int length = avutil.av_channel_layout_describe(audioStreamParams.getCodecParameters().ch_layout(), channelLayoutData, channelLayoutData.length);

				String channelLayoutName = new String(channelLayoutData, 0, length);

				String audioFilterArgs = "channel_layout="+ channelLayoutName +":"
						+ "sample_fmt="+audioStreamParams.getCodecParameters().format()+":"
						+ "time_base="+audioStreamParams.getTimeBase().num()+"/"+audioStreamParams.getTimeBase().den()+":"
						+ "sample_rate="+audioStreamParams.getCodecParameters().sample_rate();

				logger.info("Input audio arguments is \"{}\" for filter:{}", audioFilterArgs, filterId);

				if(filterConfiguration.getAudioFilter().contains("["+"in"+i+"]")) {
					audioSourceFiltersMap.put(streamId, new Filter("abuffer", audioFilterArgs, "in"+i));
				}
			}

			i++;
		}
		FilterGraph prevVideoFilterGraph = videoFilterGraph;


		/*
		 * Buffersinks for video and audio to get the output of the filter graph
		 */
		i = 0;
		for (String streamId : currentOutStreams.keySet()) {
			Filter videoSink = new Filter("buffersink", null, "out"+i);

			videoSink.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);

			videoSinkFiltersMap.put(streamId, videoSink);

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
				logger.error("Video filter graph can not be initiated: {}", filterConfiguration.getVideoFilter());
				result.setMessage("Video filter graph can not be initiated:" + filterConfiguration.getVideoFilter());
				return result;
			}
			videoFilterGraph.setCurrentPts(currentVideoPts);

			/*
			 * Set the listener of video filter graph. FilterGrapah calls the listener for the filtered output frame
			 */
			videoFilterGraph.setListener((streamId, frame)->{
				if(frame != null && currentOutStreams.containsKey(streamId)) {
					IFrameListener frameListener = currentOutStreams.get(streamId);
					if(frameListener != null) { 
						if(!firstVideoReceived) {
							firstVideoReceived = true;
						}
						//rescale the pts if the filter timebase is different
						frame.pts(av_rescale_q_rnd(frame.pts(), videoSinkFiltersMap.get(streamId).getFilterContext().inputs(0).time_base(), Utils.TIME_BASE_FOR_MS, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
						//framelistener is a custombroadcast
						frameListener.onVideoFrame(streamId, frame);
					}
				}
			});

			if(prevVideoFilterGraph != null) {
				prevVideoFilterGraph.close();
			}
		}

		if(filterConfiguration.isAudioEnabled()) {
			FilterGraph prevAudioFilterGraph = audioFilterGraph;
			long currentAudioPts = 0;


			if(audioFilterGraph != null) {
				currentAudioPts = audioFilterGraph.getCurrentPts();
			}

			audioFilterGraph = new FilterGraph(filterConfiguration.getAudioFilter(), audioSourceFiltersMap , audioSinkFiltersMap);
			if(!audioFilterGraph.isInitiated()) {
				logger.error("Audio filter graph can not be initiated:{}", filterConfiguration.getAudioFilter());
				result.setMessage("Audio filter graph can not be initiated:" + filterConfiguration.getAudioFilter());
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

			if(prevAudioFilterGraph != null) {
				prevAudioFilterGraph.close();
			}
		}
		result.setSuccess(true);
		result.setDataId(filterId);
		return result;
	}

	/*
	 * This method is used for the creation and also for the update of the filter
	 * For example new inputs mat be added as an update
	 */

	public synchronized Result createOrUpdateFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter app) {
		if(vertx == null) {
			vertx = app.getVertx();
		}

		this.filterConfiguration = filterConfiguration;

		/*
		 * create custom broadcast for each output and add them to the map
		 *  if an outputstream is same with an input stream no need the create custombroadcast 
		 * 		instead we feed input stream with filtered frame
		 */
		for (String streamId : filterConfiguration.getOutputStreams()) 
		{
			if(!currentOutStreams.containsKey(streamId)) 
			{
				if(filterConfiguration.getInputStreams().contains(streamId)) 
				{
					currentOutStreams.put(streamId, null);
					logger.info("Output stream:{} is same as input stream so no new customBroadcast will be created for filterId:{}", streamId, filterId);
				}
				else {
					IFrameListener broadcast = app.createCustomBroadcast(streamId, filterConfiguration.getVideoOutputHeight(), filterConfiguration.getVideoOutputBitrate());
					startBroadcast(streamId, broadcast, filterConfiguration.isVideoEnabled(), filterConfiguration.isAudioEnabled());
					currentOutStreams.put(streamId, broadcast);
					logger.info("Output stream:{} will be created for filterId:{}", streamId, filterId);
				}

			}		
		}


		// check the inserted or removed streams to the filter as an update
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
				app.removePacketListener(streamId, this);
				currentInStreams.remove(streamId);
				logger.info("StreamId:{} is being removed from the filter:{}", streamId, filterId);
			}


			// register plugin for the streams inserted to the filter
			for (String streamId : inserted) {

				if(selfDecodeStreams) {
					app.addFrameListener(streamId, this); //to get decoded audioframes
					app.addPacketListener(streamId, this); //to get video packets
				}
				else {
					app.addFrameListener(streamId, this);
				}
				currentInStreams.add(streamId);
				logger.info("StreamId:{} is being added as input to the filter:{}", streamId, filterId);
			}

			filterConfiguration.setInputStreams(currentInStreams);
		}

		//we need to update the filter graph to make the configuration changes will be effective
		return update();
	}

	private void startBroadcast(String streamId, IFrameListener broadcast, boolean videoEnabled, boolean audioEnabled) {
		AVCodecParameters videoCodecParameters = new AVCodecParameters();
		videoCodecParameters.height(filterConfiguration.getVideoOutputHeight());
		videoCodecParameters.width((int) (filterConfiguration.getVideoOutputHeight()*1.5f)); //non 0, it will be overriden by AMS
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

		channelLayout = new AVChannelLayout();
		av_channel_layout_default(channelLayout, 1);

		audioCodecParameters.ch_layout(channelLayout);

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

	public synchronized void close(AntMediaApplicationAdapter app) {
		for(String streamId : currentInStreams) {
			app.removeFrameListener(streamId, this);
			app.removePacketListener(streamId, this);
		}
		for (String streamId : filterConfiguration.getOutputStreams()) {
			app.stopCustomBroadcast(streamId);
		}

		if(videoFilterGraph != null) {
			videoFilterGraph.close();
			videoFilterGraph = null;
		}
		if (audioFilterGraph != null){
			audioFilterGraph.close();
			audioFilterGraph = null;
		}		
	}

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket packet) {
		StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);
		if(selfDecodeStreams || (videoStreamParams != null && videoStreamParams.isHostedInOtherNode())) 
		{
			if(videoDecodersMap.containsKey(streamId)) 
			{
				AVPacket tempPacket = new AVPacket();
				av_packet_ref(tempPacket, packet);
				vertx.executeBlocking(l->{
					AVFrame frame = videoDecodersMap.get(streamId).decodeVideoPacket(tempPacket);
					av_packet_unref(tempPacket);
					tempPacket.close();
					if(frame != null) {
						onVideoFrame(streamId, frame);
					}
					else {
						logger.warn("video decoder does not generate video frame for streamId:{}", streamId);
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

	public void setFilterConfiguration(FilterConfiguration filterConfiguration) {
		this.filterConfiguration = filterConfiguration;
	}

}
