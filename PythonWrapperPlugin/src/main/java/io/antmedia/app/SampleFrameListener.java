package io.antmedia.app;

import org.bytedeco.ffmpeg.avutil.*;

import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.tensorflow.Tensor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.lang.ProcessBuilder;

import static org.bytedeco.ffmpeg.global.avutil.av_frame_ref;

public class SampleFrameListener implements IFrameListener{

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

		//processVideoFrame(videoFrame);
		//Thread t1= new Thread(() -> processVideoFrame(videoFrame));
		//t1.start();

		return videoFrame;
	}

	public void processVideoFrame(String streamId, AVFrame avFrame, int videoFrameCount) {
		Process process = null;
		try {
			AVFrame rgbFrame = Utils.toRGB(avFrame);
			byte[] frameData = convertAVFrameToByteArray(rgbFrame);

			String frameOutputName = streamId + "_" + videoFrameCount;

			// Start the Python process and pass the tensor as input
			ProcessBuilder processBuilder = new ProcessBuilder("python3", "/usr/local/antmedia/python_script.py", Integer.toString(rgbFrame.width()), Integer.toString(rgbFrame.height()), frameOutputName);
			processBuilder.redirectError(new File("/usr/local/antmedia/logger.log.err"));
			processBuilder.redirectOutput(new File("/usr/local/antmedia/logger.log"));
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

	private static byte[] convertTensorToByteArray(Tensor<Float> tensor) {
		// Convert the tensor data to a byte array
		long dataSize = tensor.numElements() * Float.BYTES;
		ByteBuffer byteBuffer = ByteBuffer.allocate((int) dataSize);
		tensor.writeTo(byteBuffer);
		return byteBuffer.array();
	}

	private static byte[] convertAVFrameToByteArray(AVFrame rgbFrame) {
		byte[] RGBAdata = new byte[rgbFrame.width()*rgbFrame.height()*4];
		rgbFrame.data(0).get(RGBAdata);
		return RGBAdata;
	}

	private static float[][][] convertAVFrameToData(AVFrame avFrame) {
		int height = avFrame.height();
		int width = avFrame.width();
		int channels = avFrame.channels();

		// Allocate memory for the data
		float[][][] frameData = new float[height][width][channels];

		// Iterate over each row and column of the frame
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// Get the pointer to the pixel data for the current position
				BytePointer data = avFrame.data(0).position(y * avFrame.linesize(0) + x * channels);

				// Read the pixel values for each channel
				for (int c = 0; c < channels; c++) {
					// Convert the byte value to float
					float value = (float) (data.get(c) & 0xFF) / 255.0f;

					// Store the pixel value in the frame data array
					frameData[y][x][c] = value;
				}
			}
		}

		return frameData;
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
