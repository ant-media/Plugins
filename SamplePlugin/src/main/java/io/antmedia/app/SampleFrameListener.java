package io.antmedia.app;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.SamplePlugin;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class SampleFrameListener implements IFrameListener{
	
	protected static Logger logger = LoggerFactory.getLogger(SampleFrameListener.class);

	private int audioFrameCount = 0;
	private int videoFrameCount = 0;

	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
		audioFrameCount ++;
		return audioFrame;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		videoFrameCount ++;
		return videoFrame;
	}

	@Override
	public void writeTrailer(String streamId) {
		logger.info("SampleFrameListener.writeTrailer() for streamId:{}", streamId);
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		logger.info("SampleFrameListener.setVideoStreamInfo() for streamId:{}", streamId);		
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		logger.info("SampleFrameListener.setAudioStreamInfo() for streamId:{}", streamId);		
	}

	@Override
	public void start() {
		logger.info("SampleFrameListener.start()");		
	}

	public String getStats() {
		return "audio frames:"+audioFrameCount+"\t"+"video frames:"+videoFrameCount;
	}


}
