package io.antmedia.filter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;

public class Utils {
	
	public static final  AVRational TIME_BASE_FOR_MS;
	static {
		TIME_BASE_FOR_MS = new AVRational();
		TIME_BASE_FOR_MS.num(1);
		TIME_BASE_FOR_MS.den(1000);
	}

	public static void save(AVFrame frame, String name) {
		String format = "jpeg";
		String fileName = name+"." + format;

		int width = frame.width();
		int height = frame.height();
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		byte[] Ydata = new byte[width*height];

		frame.data(0).get(Ydata);

		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				image.setRGB(x, y, Ydata[y*width+x]);
			}
		}

		try {
			ImageIO.write(image, format, new File("/home/burak/temp/"+fileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

