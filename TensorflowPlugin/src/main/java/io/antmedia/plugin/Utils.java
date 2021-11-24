package io.antmedia.plugin;

import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC;
import static org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;




public class Utils {
	private static BytePointer targetFrameBuffer;
	private static BytePointer rgbFrameBuffer;


	public static void saveRGB(BufferedImage image, String fileName) {
		try {
			ImageIO.write(image, "jpeg", new File(fileName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public static AVFrame toRGB(AVFrame inFrame) {
		//int format = AV_PIX_FMT_YUV420P;
		int format = AV_PIX_FMT_RGBA;
		SwsContext sws_ctx = null;
		sws_ctx = sws_getCachedContext(sws_ctx, inFrame.width(), inFrame.height(), inFrame.format(),
				inFrame.width(), inFrame.height(), format,
				SWS_BICUBIC, null, null, (DoublePointer)null);


		AVFrame outFrame = AVFramePool.getInstance().getAVFrame();
		int size = av_image_get_buffer_size(format, inFrame.width(), inFrame.height(), 32);
		if(rgbFrameBuffer == null) {
			rgbFrameBuffer = new BytePointer(av_malloc(size)).capacity(size);
		}
		
		av_image_fill_arrays(outFrame.data(), outFrame.linesize(), rgbFrameBuffer, format, inFrame.width(), inFrame.height(), 32);
		outFrame.format(format);
		outFrame.width(inFrame.width());
		outFrame.height(inFrame.height());

		sws_scale(sws_ctx, inFrame.data(), inFrame.linesize(),
				0, inFrame.height(), outFrame.data(), outFrame.linesize());

		outFrame.pts(inFrame.pts());
		
		return outFrame;
	}


	public static byte[] getRGBData(BufferedImage image) {
		
		int width = image.getWidth();
		int height = image.getHeight();
		
		byte data[] = new byte[width*height*4];
		int k = 0;
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				Color c = new Color(image.getRGB(x, y));
				data[k++] = (byte)c.getRed();
				data[k++] = (byte)c.getGreen();
				data[k++] = (byte)c.getBlue();
				data[k++] = (byte)c.getAlpha();
			}
		}
		
		return data;
	}


	public static AVFrame toTargetFormat(AVFrame inFrame, int format) {
		SwsContext sws_ctx = null;
		sws_ctx = sws_getCachedContext(sws_ctx, inFrame.width(), inFrame.height(), inFrame.format(),
				inFrame.width(), inFrame.height(), format,
				SWS_BICUBIC, null, null, (DoublePointer)null);


		AVFrame outFrame = AVFramePool.getInstance().getAVFrame();
		int size = av_image_get_buffer_size(format, inFrame.width(), inFrame.height(), 32);
		if(targetFrameBuffer == null) {
			targetFrameBuffer = new BytePointer(av_malloc(size)).capacity(size);
		}
		
		av_image_fill_arrays(outFrame.data(), outFrame.linesize(), targetFrameBuffer, format, inFrame.width(), inFrame.height(), 32);
		outFrame.format(format);
		outFrame.width(inFrame.width());
		outFrame.height(inFrame.height());

		sws_scale(sws_ctx, inFrame.data(), inFrame.linesize(),
				0, inFrame.height(), outFrame.data(), outFrame.linesize());

		outFrame.pts(inFrame.pts());
		
		return outFrame;
	}
	
}

