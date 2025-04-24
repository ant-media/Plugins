package io.antmedia.app;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.PythonPlugin;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class PythonWrapFrameListener implements IFrameListener {

  protected static Logger logger = LoggerFactory.getLogger(PythonWrapFrameListener.class);

  @Override
  public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
    // NativeInterface.JNA_RTSP_SERVER.INSTANCE.aquirejil();
    // NativeInterface.JNA_RTSP_SERVER.INSTANCE.onAudioFrame(streamId,
    // audioFrame.address());
    // NativeInterface.JNA_RTSP_SERVER.INSTANCE.releasejil();
    return audioFrame;
  }

  @Override
  public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
    NativeInterface.PY_WRAPPER.INSTANCE.aquirejil();
    NativeInterface.PY_WRAPPER.INSTANCE.onVideoFrame(streamId, videoFrame.address());
    NativeInterface.PY_WRAPPER.INSTANCE.releasejil();
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
    logger.info("PythonFrameListener.start()");
  }

}
