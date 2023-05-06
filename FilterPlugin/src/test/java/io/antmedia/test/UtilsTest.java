package io.antmedia.test;


import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.junit.Test;

import io.antmedia.filter.Utils;

public class UtilsTest {

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
	
	@Test
	public void testErrorDefinition() {
		String errorDefinition = Utils.getErrorDefinition(-22);
		//invalid argument error
		assertTrue(errorDefinition.contains("Invalid"));
	}
	
	
}

