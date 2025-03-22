import cv2
import torch


def init_python_plugin_state():
    # this function will be called before any other you can inintialize your logic here

    print("initialize all your python code here")


def streamStarted(streamid, width, height):

    print("------------- on stream started in python plugin streamid {} resolution {} x {}  ---------------".format(streamid, width, height))
    pass


def streamFinished(streamid):
    print("------------- on stream finished in python plugin ------------------", streamid)


def onVideoFrame(streamid, rgb_image,  width, height):
    cv2.rectangle(rgb_image, (10, 20), (240, 240), (233, 222, 255), 2)
