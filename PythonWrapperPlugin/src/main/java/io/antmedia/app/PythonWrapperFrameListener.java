package io.antmedia.app;

import org.bytedeco.ffmpeg.avutil.*;

import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder;

import static io.antmedia.app.Utils.convertAVFrameToByteArray;


public class PythonWrapperFrameListener implements IFrameListener{

	private int audioFrameCount = 0;
	private int videoFrameCount = 0;

	private boolean isLocked = false;

	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
		audioFrameCount ++;
		return audioFrame;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		videoFrameCount ++;

		if (!isLocked) {
			isLocked = true;
			processVideoFrame(streamId, videoFrame, videoFrameCount);
			isLocked = false;
		}

		return videoFrame;
	}

	public void processVideoFrame(String streamId, AVFrame avFrame, int videoFrameCount) {
		Process process = null;
		try {
			AVFrame rgbFrame = Utils.toRGB(avFrame);
			byte[] frameData = convertAVFrameToByteArray(rgbFrame);

			String frameOutputName = streamId + "_" + videoFrameCount;

			// Start the Python process and pass the tensor as input
			ProcessBuilder processBuilder = createPythonProcessBuilder(rgbFrame.width(), rgbFrame.height(), frameOutputName);
			process = processBuilder.start();

			// Get the output stream of the Python process
			OutputStream stdin = process.getOutputStream();

			// Write the frame data to the Python process
			stdin.write(frameData);
			stdin.close();

			// Wait for the Python process to complete
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				System.out.println("Python script executed successfully.");
			} else {
				System.out.println("Python script execution failed.");
			}

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			if (process != null) {
				process.destroy();
			}
		}
	}

	public ProcessBuilder createPythonProcessBuilder(int width, int height, String frameOutputName) {
		ProcessBuilder processBuilder = new ProcessBuilder("python3", "/usr/local/antmedia/python_script.py", Integer.toString(width), Integer.toString(height), frameOutputName);
		processBuilder.redirectError(new File("/usr/local/antmedia/logger.log.err"));
		processBuilder.redirectOutput(new File("/usr/local/antmedia/logger.log"));
		return processBuilder;
	}

	@Override
	public void writeTrailer(String streamId) {
		System.out.println("PythonWrapperFrameListener.writeTrailer()");
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
		System.out.println("PythonWrapperFrameListener.setVideoStreamInfo()");
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
		System.out.println("PythonWrapperFrameListener.setAudioStreamInfo()");
	}

	@Override
	public void start() {
		System.out.println("PythonWrapperFrameListener.start()");
	}

	public String getStats() {
		return "audio frames:"+audioFrameCount+"\t"+"video frames:"+videoFrameCount;
	}

}
