package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_CH_LAYOUT_MONO;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC;
import static org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.vertx.core.Vertx;

public class FilterAdaptor implements IFrameListener{
	private FilterGraph videoFilterGraph = null;
	private FilterGraph audioFilterGraph = null;

	List<String> currentInStreams = new ArrayList<String>();
	Map<String, IFrameListener> currentOutStreams = new LinkedHashMap<String, IFrameListener>();

	// stream Id to video/audio stream params map
	private Map<String, StreamParametersInfo> videoStreamParamsMap = new LinkedHashMap<String, StreamParametersInfo>();
	private Map<String, StreamParametersInfo> audioStreamParamsMap = new LinkedHashMap<String, StreamParametersInfo>();
	private FilterConfiguration filterConfiguration;
	private Vertx vertx;
	private SwsContext sws_ctx;
	private AVFrame picture;
	private BytePointer picture_bufptr;
	private static final Logger logger = LoggerFactory.getLogger(FilterAdaptor.class);
	
	int width = 480;
	int height = 360;


	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
		if(audioFilterGraph != null) {
			audioFilterGraph.doFilter(streamId, audioFrame);
		}
		
		return null;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		if(videoFilterGraph != null) {
			//AVFrame frame = isScalingRequired(videoFrame) ? scale(videoFrame) : videoFrame;
			AVFrame frame = videoFrame;
			//Utils.save(frame, "onvideo");
			videoFilterGraph.doFilter(streamId, frame);
			//av_frame_unref(videoFrame);  //FIXME: STREAM ADAPTORDEN GELENLEDE GEREKLİ ??
		}
		return null;
	}
	
	public boolean isScalingRequired(AVFrame frame) {
    	return frame != null && 
				(frame.width() != width || frame.height() != height 
				|| frame.format() != AV_PIX_FMT_YUV420P);
    }

	AVFrame scale(AVFrame frame) {
		sws_ctx = sws_getCachedContext(sws_ctx, frame.width(), frame.height(), frame.format(),
				width, height, AV_PIX_FMT_YUV420P,
				SWS_BICUBIC, null, null, (DoublePointer)null);
		
		sws_scale(sws_ctx, frame.data(), frame.linesize(),
				0, frame.height(), picture.data(), picture.linesize());

		picture.pts(frame.pts());
		
		return picture;
	}
	
	public void prepareFrame() {
		picture = av_frame_alloc();
		int size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height, 32);
		picture_bufptr = new BytePointer(av_malloc(size)).capacity(size);

		// Assign appropriate parts of buffer to image planes in picture_rgb
		// Note that picture_rgb is an AVFrame, but AVFrame is a superset of AVPicture
		av_image_fill_arrays(picture.data(), picture.linesize(), picture_bufptr, AV_PIX_FMT_YUV420P, width, height, 32);
		picture.format(AV_PIX_FMT_YUV420P);
		picture.width(width);
		picture.height(height);
	}
	
	
	@Override
	public void writeTrailer() {
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		videoStreamParamsMap.put(streamId, videoStreamInfo);
		
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
	public void update() {
		Map<String, Filter> videoSourceFiltersMap = new LinkedHashMap<String, Filter>();
		Map<String, Filter> videoSinkFiltersMap = new LinkedHashMap<String, Filter>();
		Map<String, Filter> audioSourceFiltersMap = new LinkedHashMap<String, Filter>();
		Map<String, Filter> audioSinkFiltersMap = new LinkedHashMap<String, Filter>();
		int i = 0;
		for (String streamId : currentInStreams) {
			StreamParametersInfo videoStreamParams = videoStreamParamsMap.get(streamId);

			String videoFilterArgs = "video_size="+videoStreamParams.codecParameters.width()+"x"+videoStreamParams.codecParameters.height()+":"
					+ "pix_fmt="+videoStreamParams.codecParameters.format()+":"
					+ "time_base="+videoStreamParams.timeBase.num()+"/"+videoStreamParams.timeBase.den()+":"
					+ "pixel_aspect=1/1";
			
			if(!videoStreamParams.enabled) {
				videoFilterArgs = "video_size=360x360:pix_fmt=0:time_base=1/20:pixel_aspect=1/1";
			}
			
			System.out.println("video:"+videoFilterArgs);
			
			videoSourceFiltersMap.put(streamId, new Filter("buffer", videoFilterArgs, "in"+i));


			StreamParametersInfo audioStreamParams = audioStreamParamsMap.get(streamId);
			/* FIXME: Check according to the createAudioFrame in WebRTCEncoderAdaptor
			String audioFilterArgs = "channel_layout="+audioStreamParams.codecParameters.channel_layout()+":"
					+ "sample_fmt="+audioStreamParams.codecParameters.format()+":"
					+ "time_base="+1+"/"+audioStreamParams.timeBase.num()+"/"+audioStreamParams.timeBase.den()+":"
					+ "sample_rate="+audioStreamParams.codecParameters.sample_rate();
			*/	
			String audioFilterArgs = "channel_layout=4:" //MONO
					+ "sample_fmt=1:" //AV_SAMPLE_FMT_S16
					+ "sample_rate=16000";
			
			System.out.println("audio:"+audioFilterArgs);

			audioSourceFiltersMap.put(streamId, new Filter("abuffer", audioFilterArgs, "in"+i));
			i++;
		}
		FilterGraph prevVideoFilterGraph = videoFilterGraph;

		
		i = 0;
		for (String streamId : currentOutStreams.keySet()) {
			videoSinkFiltersMap.put(streamId, new Filter("buffersink", null, "out"+i));
			audioSinkFiltersMap.put(streamId, new Filter("abuffersink", null, "out"+i));
		}
		
		long currentVideoPts = 0;
		if(videoFilterGraph != null) {
			currentVideoPts = videoFilterGraph.getCurrentPts();
		}
		
		videoFilterGraph = new FilterGraph(filterConfiguration.getVideoFilter(), videoSourceFiltersMap , videoSinkFiltersMap);
		videoFilterGraph.setCurrentPts(currentVideoPts);
		videoFilterGraph.setListener((streamId, frame)->{
			if(frame != null) {
				currentOutStreams.get(streamId).onVideoFrame(streamId, frame);
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
		audioFilterGraph.setCurrentPts(currentAudioPts);
		audioFilterGraph.setListener((streamId, frame)->{
			if(frame != null) {
				currentOutStreams.get(streamId).onAudioFrame(streamId, frame);
			}
		});
		
		if(prevAudioFilterGraph != null) {
			prevAudioFilterGraph.close();
		}
	}
	
	/*
	 * This method is used for the creation and also for the update of the filter
	 * For example new inputs mat be added as an update
	 */
	
	public void createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter app) {
		if(vertx == null) {
			vertx = app.getVertx();
			prepareFrame();
		}
		
		this.filterConfiguration = filterConfiguration;
		
		//create custom broadcast for each output and add them to the map
		for (String streamId : filterConfiguration.getOutputStreams()) {
			if(!currentOutStreams.containsKey(streamId)) {
				IFrameListener broadcast = app.createCustomBroadcast(streamId);
				currentOutStreams.put(streamId, broadcast);
				startBroadcast(streamId, broadcast, filterConfiguration.videoEnabled, filterConfiguration.audioEnabled);
			}		
		}

		
		// check the inseted or removed streams to the filter as an update
		List<String> inserted = filterConfiguration.getInputStreams();
		List<String> removed = new ArrayList<String>();
		for (String streamId : currentInStreams) {
			if(filterConfiguration.getInputStreams().contains(streamId)) {
				inserted.remove(streamId);
			}
			else {
				removed.add(streamId);
			}
		}
		
		boolean noChange = inserted.isEmpty() && removed.isEmpty();
		
		if(noChange) {
			return;
		}
		
		// deregister plugin for the streams removed from filter
		for (String streamId : removed) {
			app.removeFrameListener(streamId, this);
			currentInStreams.remove(streamId);
		}
		
		// register plugin for the streams inserted to the filter
		for (String streamId : inserted) {
			app.addFrameListener(streamId, this);
			currentInStreams.add(streamId);
		}
		
		filterConfiguration.setInputStreams(currentInStreams);
		
		update();
	}
	
	private void startBroadcast(String streamId, IFrameListener broadcast, boolean videoEnabled, boolean audioEnabled) {
		AVCodecParameters videoCodecParameters = new AVCodecParameters();
		videoCodecParameters.width(width);
		videoCodecParameters.height(height);
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
		}
		for (String streamId : filterConfiguration.getOutputStreams()) {
			app.stopCustomBroadcast(streamId);
		}
		videoFilterGraph.close();
		audioFilterGraph.close();

		return true;
		
	}
}
