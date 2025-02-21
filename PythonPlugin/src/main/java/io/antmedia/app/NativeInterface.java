package io.antmedia.app;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Callback;

public class NativeInterface {

  public static interface PY_WRAPPER extends Library {

    PY_WRAPPER INSTANCE = Native.load("./lib/native/libpythonWrapper.so",
        PY_WRAPPER.class);

    public boolean Py_IsInitialized();

    void init_py_and_wrapperlib();

    void init_python_plugin_state();

    void PyImport_ImportModule(String moduletoimport);

    void pyimport_wrapperlib();

    void aquirejil();

    void releasejil();

    void streamStarted(String streamid,int width,int height);

    void streamFinished(String streamid);

    void joinedTheRoom(String roomId, String streamId);

    void leftTheRoom(String roomId, String streamId);

    void onVideoFrame(String streamId, long pktPointer);

    void onAudioFrame(String streamId, long pktPointer);

    void onVideoPacket(String streamId, long pktPointer);

    void onAudioPacket(String streamId, long pktPointer);

    void setStreamInfo(String streamId, long codecPar, long rational, int isEnabled, int streamType); // pkt codecType
                                                                                                      // 0=video

    interface receiveDataCallback extends Callback {
      void C_Callback(String streamId, String roomId, String data);
    }

    void registerCallback(receiveDataCallback callback);

  }
}
