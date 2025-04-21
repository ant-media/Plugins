import numpy as np
# import traceback
import importlib
import sys
import subprocess
import cv2
import time
import os
import time
import cython
from libc.stdint cimport (uint8_t, uint16_t, uint32_t, uint64_t,
                          int8_t, int16_t, int32_t, int64_t)

import subprocess
import sys

cdef extern from "libpythonWrapper.h":
    cdef char *PLUGIN_PATH

cdef str plugin_path = PLUGIN_PATH.decode('utf-8')

cdef streamidProcessDict
cdef plugin

cdef extern from "libavutil/frame.h":
    cdef struct AVFrame:
        uint8_t *data[8]
        int width;
        int height;
        int format;
        int quality;

cdef extern from "libavcodec/avcodec.h":
    struct AVPacket:
        pass

cdef public void init_python_plugin_state():
    global streamidProcessDict 
    global plugin
    try:
        streamidProcessDict = {}
        print(sys.path)

        if plugin_path!=None:
            sys.path.append(plugin_path)
            print("\npython append path to \n",plugin_path)
        else:
            print("\n python append path to usr\\local\\antmedia \n")
            sys.path.append("/usr/local/antmedia")

        plugin = importlib.import_module("python_plugin") 

        print("Module Loaded:", plugin)

        plugin.init_python_plugin_state()

    except Exception as e:
        print("Exception occurred in init_python_plugin_state:", e)
        sys.exit(0)
    return


cdef public void init_restream(streamid,int width,int height):
    try:
        global streamidProcessDict
        print("initializing writer for stream"+streamid)
        rtmpUrl = 'rtmp://127.0.0.1/WebRTCAppEE/' + streamid + "frompython"

        fps = 30

        command = ['ffmpeg',
                   '-re',
                   '-y',
                   '-f', 'rawvideo',
                   '-vcodec', 'rawvideo',
                   '-pix_fmt', 'bgr24',
                   '-s', "{}x{}".format(width, height),
                   '-r', str(fps),
                   '-i', '-',
                   '-c:v', 'libx264',
                   '-g', str(fps * 2), 
                   '-pix_fmt', 'yuv420p',
                   '-preset', 'ultrafast',
                   '-f', 'flv',
                   rtmpUrl]

        ffmpegprocess = subprocess.Popen(command, stdin=subprocess.PIPE)
            
        streamidProcessDict[streamid] = ffmpegprocess

    except Exception as e:
        print("Exception occurred in init_restream :", e)
    return

cdef public void uninit_restream(streamid):
    try:
        global streamidProcessDict

        if streamid in streamidProcessDict:
            print("releasing re streaming resources for "+ streamid)
            streamidProcessDict[streamid].terminate()
            streamidProcessDict[streamid].kill()
        else:
            print("failed to release video stream no such stream exist "+streamid)
    except Exception as e:
        print("Exception occurred in uninit_restream :", e)

    return;


cdef public void streamStarted(const char* utfstreamid,int width,int height):
    try:
        if plugin!=None and plugin.mode == "debug":
            importlib.reload(plugin)

        streamid = utfstreamid.decode('utf-8') 
        init_restream(streamid,width,height)
        plugin.streamStarted(streamid,width,height)
    except Exception as e:
        print("Exception occurred in streamStarted:", e)
    return

cdef public void streamFinished(const char* utfstreamid):
    try:
        streamid = utfstreamid.decode('utf-8') 
        uninit_restream(streamid)
        plugin.streamFinished(streamid)
    except Exception as e:
        print("Exception occurred in streamFinished:", e)
    return

cdef public void onVideoFrame(const char* streamid, AVFrame *avframe):

    global streamidProcessDict

    cdef int width = avframe.width
    cdef int height = avframe.height
    cdef int half_width = width // 2
    cdef int half_height = height // 2

    cdef uint8_t[:] y_plane_memview = <uint8_t[:width * height]> avframe.data[0]
    cdef uint8_t[:] u_plane_memview = <uint8_t[:half_width * half_height]> avframe.data[1]
    cdef uint8_t[:] v_plane_memview = <uint8_t[:half_width * half_height]> avframe.data[2]

    y_plane = np.asarray(y_plane_memview).reshape((height, width))
    u_plane = np.asarray(u_plane_memview).reshape((half_height, half_width))
    v_plane = np.asarray(v_plane_memview).reshape((half_height, half_width))

    u_resized = cv2.resize(u_plane, (width, height), interpolation=cv2.INTER_NEAREST)
    v_resized = cv2.resize(v_plane, (width, height), interpolation=cv2.INTER_NEAREST)

    yuv_image = cv2.merge((y_plane, u_resized, v_resized))
    rgb_image = cv2.cvtColor(yuv_image, cv2.COLOR_YUV2BGR)
    # do not touch above code if you don't know what you are doing

    try:
 
        plugin.onVideoFrame(streamid,rgb_image,width,height)

        py_streamid = streamid.decode('UTF-8')
        width, height = avframe.width, avframe.height
        writer = streamidProcessDict.get(py_streamid)

        if not writer:
            return 
        writer.stdin.write(rgb_image.tobytes())

    except Exception as e:
        print("Exception occurred in onVideoFrame:", e)
    return

cdef public void onAudioFrame(const char* streamid, avfame):
    py_streamid = streamid.decode('utf-8') 
    print("on audio frame recieved in python : ", streamid)
    return

cdef public void onVideoPacket(const char* streamid, AVPacket *avpacket):
    py_streamid = streamid.decode('utf-8') 
    print("on video packet recieved in python : ", streamid)
    return

cdef public void onAudioPacket(const char *streamid, AVPacket *avpacket):
    py_streamid = streamid.decode('utf-8') 
    print("on audio packet recieved in python : ", streamid)
    return

cdef public void setVideoStreamInfo(const char* streamid,const void *videoStreamInfo):
    py_streamid = streamid.decode('utf-8') 
    print("on video stream info")
    return

cdef public void setAudioStreamInfo(const char* streamId, const void *audioStreamInfo):
    print("on audio stream info")
    return
