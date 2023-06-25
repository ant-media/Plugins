package io.antmedia.app;

import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;




public class Utils {
    private static BytePointer rgbFrameBuffer;


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


        sws_freeContext(sws_ctx);
        sws_ctx.close();

        return outFrame;
    }


    static byte[] convertAVFrameToByteArray(AVFrame rgbFrame) {
        byte[] RGBAdata = new byte[rgbFrame.width()*rgbFrame.height()*4];
        rgbFrame.data(0).get(RGBAdata);
        return RGBAdata;
    }

}
