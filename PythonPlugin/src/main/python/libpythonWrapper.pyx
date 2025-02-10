#add your imports here <
import numpy as np
import subprocess
import cv2
import time
import time
from cypes import *
import cython
from libc.stdint cimport (uint8_t, uint16_t, uint32_t, uint64_t,
                          int8_t, int16_t, int32_t, int64_t)

import sys
cdef streamidProcessDict

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
    # initialize all global variables and state of the program in this functions it will be called once when program is initialized
    global streamidProcessDict
    try:
        streamidProcessDict = {}
    except Exception as e:
        print("Exception occurred in init_python_plugin_state:", e)
    return


cdef public void init_restream(streamid,int width,int height):
    try:
        global streamidProcessDict
        print("initializing writer for stream"+streamid)
        rtmpUrl = 'rtmp://127.0.0.1/WebRTCAppEE/' + streamid + "frompython"
        send_gst = " appsrc !  videoconvert ! video/x-raw,format=I420  ! x264enc key-int-max=2 tune=zerolatency speed-preset=veryfast  ! video/x-h264,stream-format=byte-stream,alignment=au ! h264parse ! queue !  flvmux ! rtmpsink location=" + rtmpUrl

        adaptive_resolution = (width, height) 

        out_send = cv2.VideoWriter(send_gst, 0, 30, adaptive_resolution)

        if not out_send.isOpened():
            print("Failed to initialize VideoWriter")
        else:
            print("VideoWriter initialized successfully streamid: ",streamid )
            
            
        streamidProcessDict[streamid] = out_send
    except Exception as e:
        print("Exception occurred in init_restream :", e)
    return

cdef public void uninit_restream(streamid):
    try:
        global streamidProcessDict

        if streamid in streamidProcessDict:
            print("releasing re streaming resources for "+ streamid)
            streamidProcessDict[streamid].release()
        else:
            print("failed to release video stream no such stream exist "+streamid)
    except Exception as e:
        print("Exception occurred in uninit_restream :", e)

    return;


cdef public void streamStarted(const char* utfstreamid,int width,int height):
    try:
        streamid = utfstreamid.decode('utf-8') 
        init_restream(streamid,width,height)
        print("------------- on stream started in python plugin streamid {} resolution {} x {}  ---------------".format(streamid,width,height))
    except Exception as e:
        print("Exception occurred in streamStarted:", e)
    return

cdef public void streamFinished(const char* utfstreamid):
    try:
        streamid = utfstreamid.decode('utf-8') 
        uninit_restream(streamid)
        print("------------- on stream finished in python plugin ------------------",streamid)
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
        cv2.rectangle(rgb_image, (20, 20), (240, 240), (255, 0, 0), 2)
 
        #start writing code from here


        # your code for modifying frame


        #end writing code 

        py_streamid = streamid.decode('UTF-8')
        width, height = avframe.width, avframe.height
        writer = streamidProcessDict.get(py_streamid)

        if not writer:
            return 
        writer.write(rgb_image)    

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

cdef public void setVideoStreamInfo(const char* streamid,const void *audioStreamInfo):
    py_streamid = streamid.decode('utf-8') 
    print("on video stream info")
    return

cdef public void setAudioStreamInfo(const char* streamId, const void *audioStreamInfo):
    print("on audio stream info")
    return
