package io.antmedia.app;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;

public class PythonWrapPacketListener implements IPacketListener {

  protected static Logger logger = LoggerFactory.getLogger(PythonWrapPacketListener.class);

  @Override
  public void writeTrailer(String streamId) {
    System.out.println("PythonPacketListener.writeTrailer()");
  }

  @Override
  public AVPacket onVideoPacket(String streamId, AVPacket packet) {
    NativeInterface.PY_WRAPPER.INSTANCE.aquirejil();
    NativeInterface.PY_WRAPPER.INSTANCE.onVideoPacket(streamId, packet.address());
    NativeInterface.PY_WRAPPER.INSTANCE.releasejil();
    return packet;
  }

  @Override
  public AVPacket onAudioPacket(String streamId, AVPacket packet) {
    NativeInterface.PY_WRAPPER.INSTANCE.aquirejil();
    NativeInterface.PY_WRAPPER.INSTANCE.onAudioPacket(streamId, packet.address());
    NativeInterface.PY_WRAPPER.INSTANCE.releasejil();

    return packet;
  }

  @Override
  public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
  }

  @Override
  public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
  }

}
