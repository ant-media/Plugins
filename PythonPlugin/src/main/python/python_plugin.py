import cv2
import traceback
import Examples.facedetection as facedetection

# if mode is set to debug it will reload this python script every time when you send a new stream to the server
# during development you can modify the script and send a new stream to the server and your new change will be loaded
# set it to empty string when in release mode=""
mode = "debug"


def init_python_plugin_state():
    # this function will be called only once when plugin loads, initialize your logic here
    # when modifying this function you will need to restart the server because its only called once plugin initialize
    print("initialize all your python code here")


def streamStarted(streamid, width, height):

    print("------------- on stream started in python plugin streamid {} resolution {} x {}  ---------------".format(streamid, width, height))
    pass


def streamFinished(streamid):
    print("------------- on stream finished in python plugin ------------------", streamid)


def onVideoFrame(streamid, rgb_image,  width, height):
    cv2.rectangle(rgb_image, (10, 20), (240, 240), (244, 255, 255), 2)
    # facedetection.onVideoFrame()
