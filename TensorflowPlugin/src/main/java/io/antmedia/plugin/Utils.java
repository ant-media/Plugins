package io.antmedia.plugin;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC;
import static org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;




public class Utils {
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


		AVFrame outFrame = av_frame_alloc();
		int size = av_image_get_buffer_size(format, inFrame.width(), inFrame.height(), 32);
		BytePointer picture_bufptr = new BytePointer(av_malloc(size)).capacity(size);

		av_image_fill_arrays(outFrame.data(), outFrame.linesize(), picture_bufptr, format, inFrame.width(), inFrame.height(), 32);
		outFrame.format(format);
		outFrame.width(inFrame.width());
		outFrame.height(inFrame.height());

		sws_scale(sws_ctx, inFrame.data(), inFrame.linesize(),
				0, inFrame.height(), outFrame.data(), outFrame.linesize());

		outFrame.pts(inFrame.pts());

		return outFrame;
	}
	
}

