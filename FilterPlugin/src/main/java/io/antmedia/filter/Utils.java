package io.antmedia.filter;

import static org.bytedeco.ffmpeg.global.avutil.av_strerror;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.filter.utils.FilterGraph;


public class Utils {
	
	private static final Logger logger = LoggerFactory.getLogger(Utils.class);

	public static final  AVRational TIME_BASE_FOR_MS;
	static {
		TIME_BASE_FOR_MS = new AVRational();
		TIME_BASE_FOR_MS.num(1);
		TIME_BASE_FOR_MS.den(1000);
	}
	

	public static void save(AVFrame frame, String name) {
		String format = "yuv";
		String fileName = name + "." + format;
		File file = new File(fileName);
		
		byte[] yPlane = new byte[frame.width()*frame.height()];
		frame.data(0).position(0).get(yPlane);
		byte[] uPlane = new byte[frame.width()*frame.height()/4];
		frame.data(1).position(0).get(uPlane);
		byte[] vPlane = new byte[frame.width()*frame.height()/4];
		frame.data(2).position(0).get(vPlane);
		try {
			Files.write(file.toPath(), yPlane, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			Files.write(file.toPath(), uPlane, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			Files.write(file.toPath(), vPlane, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//Typical check if it's ok		
		//ffplay -f rawvideo -pixel_format yuv420p -video_size 1280x720 -i filterInputframe1.yuv 

	

	}
	
	public static String getErrorDefinition(int errorCode) {
		byte[] data = new byte[128];
		av_strerror(errorCode, data, data.length);
		return new String(data, 0, data.length);
	}
}

