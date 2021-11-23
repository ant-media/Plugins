package io.antmedia.plugin;

import static org.bytedeco.ffmpeg.global.avutil.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.antmedia.plugin.tensorflow.detection.TensorFlowDetector;
import io.vertx.core.Vertx;

public class TensorflowFrameListener implements IFrameListener{

	private static final long DETECTION_CALL_PERIOD = 0;
	public static final String DATE_TIME_PATTERN = "yyyy-MM-dd_HH-mm-ss.SSS";

	private TensorFlowDetector detector;
	private Vertx vertx = null;
	private long lastCallTime;
	private File detectionFolder;
	private boolean realTime = true;
	private AVFrame yuvFrame;

	public TensorflowFrameListener(Vertx vertx, TensorFlowDetector tensorFlowDetector, AntMediaApplicationAdapter app, boolean realTime) {
		this.vertx  = vertx;
		this.detector = tensorFlowDetector;
		this.realTime = realTime;
		detectionFolder = new File("webapps/"+app.getName()+"/previews");
		if(!detectionFolder.exists()) {
			detectionFolder.mkdirs();
		}
	}

	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
		return audioFrame;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		if(realTime) {
			return processRealTime(streamId, videoFrame);
		}
		else {
			return processOffline(streamId, videoFrame);
		}
	}
	
	public AVFrame processRealTime(String streamId, AVFrame videoFrame) {
		// since we return yuvFrame, we need to release it in the next call 
		// TODO: find better way
		if(yuvFrame != null) {
			AVFramePool.getInstance().addFrame2Pool(yuvFrame);
		}
		
		int format = videoFrame.format();
		
		AVFrame cloneframe = AVFramePool.getInstance().getAVFrame();
		av_frame_ref(cloneframe, videoFrame);
		
		AVFrame rgbFrame = Utils.toRGB(cloneframe);
		AVFramePool.getInstance().addFrame2Pool(cloneframe);
		
		byte[] RGBAdata = new byte[rgbFrame.width()*rgbFrame.height()*4];
		rgbFrame.data(0).get(RGBAdata);

		try {
			BufferedImage image = detector.process(rgbFrame.width(), rgbFrame.height(), RGBAdata,false);
			if(image != null) {
				byte[] data = Utils.getRGBData(image);
				rgbFrame.data(0, new BytePointer(data));
				yuvFrame = Utils.toTargetFormat(rgbFrame, format);
				AVFramePool.getInstance().addFrame2Pool(rgbFrame);
				return yuvFrame;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	
	public AVFrame processOffline(String streamId, AVFrame videoFrame) {
		long now = System.currentTimeMillis();
		if(now - lastCallTime > DETECTION_CALL_PERIOD) {
			lastCallTime = now;
			AVFrame cloneframe = av_frame_clone(videoFrame);
			vertx.executeBlocking(a->{
				LocalDateTime ldt =  LocalDateTime.now();
				String fileName = streamId + "-" + ldt.format(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN));
				AVFrame rgbFrame = Utils.toRGB(cloneframe);
				byte[] RGBAdata = new byte[rgbFrame.width()*rgbFrame.height()*4];
				rgbFrame.data(0).get(RGBAdata);

				try {
					BufferedImage image = detector.process(rgbFrame.width(), rgbFrame.height(), RGBAdata, true);
					if(image != null) {
						Utils.saveRGB(image, detectionFolder.getAbsolutePath()+"/"+fileName);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				av_frame_free(cloneframe);
			},b->{});
		}
		
		return videoFrame;
	}

	@Override
	public void writeTrailer(String streamId) {
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
	}

	@Override
	public void start() {
	}
}
