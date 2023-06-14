package io.antmedia.app;

import static org.bytedeco.ffmpeg.global.avutil.av_frame_unref;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.bytedeco.ffmpeg.avutil.AVFrame;

/*
 * This class is for management of queued AVFrames
 * Creation of a native AVFrame is expensive process
 * So we want to use pre-created AVFrames from pool instead of creating new ones
 * This pool should be used when multiple AVFrames are queued as in StreamAdaptor or WebRTCEncoderAdaptor
 */

public class AVFramePool {

    private static AVFramePool instance = new AVFramePool();

    private ConcurrentLinkedQueue<AVFrame> availableFrames = new ConcurrentLinkedQueue<>();

    public AVFrame getAVFrame() {
        if (!availableFrames.isEmpty()) {
            return availableFrames.poll();
        }
        return new AVFrame();
    }

    public void addFrame2Pool(AVFrame frame) {
        av_frame_unref(frame);
        availableFrames.offer(frame);
    }

    public void releaseFrames() {
        for (AVFrame avFrame : availableFrames) {
            av_frame_unref(avFrame);
            avFrame.close();
        }

        availableFrames = null;
    }

    public static AVFramePool getInstance() {
        return instance;
    }

}