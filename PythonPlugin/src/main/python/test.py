import cv2
streamid= "test"
rtmpUrl = 'rtmp://127.0.0.1/WebRTCAppEE/' + streamid + "frompython"
send_gst = " appsrc !  videoconvert ! video/x-raw,format=I420  ! x264enc key-int-max=2 tune=zerolatency speed-preset=veryfast  ! video/x-h264,stream-format=byte-stream,alignment=au ! h264parse ! queue !  flvmux ! rtmpsink location=" + rtmpUrl

# adaptive_resolution = (640, 480) 
adaptive_resolution = (1280, 720) 
# adaptive_resolution = 1920 , 1080)

out_send = cv2.VideoWriter(send_gst, 0, 30, adaptive_resolution)    

