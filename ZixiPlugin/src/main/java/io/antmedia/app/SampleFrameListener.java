package io.antmedia.app;

import org.bytedeco.ffmpeg.avutil.AVFrame;

import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class SampleFrameListener implements IFrameListener{

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
		System.out.println("SampleFrameListener.writeTrailer()");
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		System.out.println("SampleFrameListener.setVideoStreamInfo()");		
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		System.out.println("SampleFrameListener.setAudioStreamInfo()");		
	}

	@Override
	public void start() {
		System.out.println("SampleFrameListener.start()");		
	}

	public String getStats() {
		return "audio frames:"+audioFrameCount+"\t"+"video frames:"+videoFrameCount;
	}


}
