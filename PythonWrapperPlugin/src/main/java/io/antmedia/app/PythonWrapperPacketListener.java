package io.antmedia.app;

import org.bytedeco.ffmpeg.avcodec.AVPacket;

import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class PythonWrapperPacketListener implements IPacketListener{

	private int packetCount = 0;

	@Override
	public void writeTrailer(String streamId) {
		System.out.println("PythonWrapperPacketListener.writeTrailer()");
		
	}

	@Override
	public AVPacket onVideoPacket(String streamId, AVPacket packet) {
		packetCount++;
		return packet;
	}
	
	@Override
	public AVPacket onAudioPacket(String streamId, AVPacket packet) {
		packetCount++;
		return packet;
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		System.out.println("PythonWrapperPacketListener.setVideoStreamInfo()");
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		System.out.println("PythonWrapperPacketListener.setAudioStreamInfo()");
	}

	public String getStats() {
		return "packets:"+packetCount;
	}

}
