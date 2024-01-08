
import {WebRTCAdaptor} from "@antmedia/webrtc_adaptor";

var webRTCAdaptor;

var publishStarted = false;

async function startBroadcasting(message) {
	
	console.log("startBroadcasting is called websocket url:" + message.websocketURL + " streamId: " + message.streamId);
    if (typeof webRTCAdaptor !== 'undefined' ) {
        throw new Error('Called startBroadcasting while recording is in progress.');
    }

    let token = "";
    let width = 1280;
    let height = 720;

    if(message.token !== undefined) {
        token = message.token;
    }
    if (message.width !== undefined && message.width > 0) {
        width = message.width;
    }
    if (message.height !== undefined && message.height > 0) {
        height = message.height;
    }


	const stream = await navigator.mediaDevices.getDisplayMedia(
		{
			//min is not allowed in getDisplayMedia
			video: true, 
			audio:true, 
			preferCurrentTab:true
		})
	
	
 	const track = stream.getVideoTracks()[0];

	
	const constra = {
        width: { min: 640, ideal: width },
        height: { min: 360, ideal: height },
        advanced: [{ width: width, height: height }, { aspectRatio: width/height }],
        resizeMode: 'crop-and-scale'
      };

    track.applyConstraints(constra);

	//width and height may not perfectly match with the created video, there may be some improvement opportunity there
    //@mekya 

    let pc_config = {
        'iceServers' : [ {
            'urls' : 'stun:stun1.l.google.com:19302'
        } ]
    };

   

    webRTCAdaptor = new WebRTCAdaptor({
        websocket_url : message.websocketURL,
        peerconnection_config : pc_config,
        localStream: stream,
        callback : (info, obj) => {
            if (info == "initialized") {
	            console.log("WebRTC adaptor initialized");
                webRTCAdaptor.publish(message.streamId, token, "", "", "", "");
            } else if (info == "publish_started") {
                console.log("mediapush_publish_started");
                publishStarted = true;
            }
            else if (info == "publish_finished") {
				publishStarted = false;
			}
            console.log(info);
        },
        callbackError : function(error, message) {
            var errorMessage = JSON.stringify(error);
            if (typeof message != "undefined") {
                errorMessage = message;
            } else {
                errorMessage = JSON.stringify(error);
            }

            if (error.indexOf("WebSocketNotConnected") != -1) {
                errorMessage = "WebSocket is disconnected.";
            } else if (error.indexOf("not_initialized_yet") != -1) {
                errorMessage = "Server is getting initialized.";
            } else if (error.indexOf("data_store_not_available") != -1) {
                errorMessage = "Data store is not available. It's likely that server is initialized or getting closed";
            } else {
                if (error.indexOf("NotFoundError") != -1) {
                    errorMessage = "Camera or Mic are not found or not allowed in your device";
                } else if (error.indexOf("NotReadableError") != -1 || error.indexOf("TrackStartError") != -1) {
                    errorMessage = "Camera or Mic are already in use and they cannot be opened. Choose another video/audio source if you have on the page below ";

                } else if (error.indexOf("OverconstrainedError") != -1 || error.indexOf("ConstraintNotSatisfiedError") != -1) {
                    errorMessage = "There is no device found that fits your video and audio constraints. You may change video and audio constraints"
                } else if (error.indexOf("NotAllowedError") != -1 || error.indexOf("PermissionDeniedError") != -1) {
                    errorMessage = "You are not allowed to access camera and mic.";
                } else if (error.indexOf("TypeError") != -1) {
                    errorMessage = "Video/Audio is required";
                } else if (error.indexOf("getUserMediaIsNotAllowed") != -1) {
                    errorMessage = "You are not allowed to reach devices from an insecure origin, please enable ssl";
                } else if (error.indexOf("ScreenSharePermissionDenied") != -1) {
                    errorMessage = "You are not allowed to access screen share";
                    $(".video-source").first().prop("checked", true);
                } else if (error.indexOf("UnsecureContext") != -1) {
                    errorMessage = "Please Install SSL(https). Camera and mic cannot be opened because of unsecure context. ";
                }
                else if (error.indexOf('no_stream_exist') != -1) {
                    errorMessage = 'There is no active live stream with this id to play';
                } else {
                    errorMessage = error
                }
            }
            if (message !== undefined) {
                console.log("error callback: " + error + " message: " + errorMessage);
            }

        }
    });

    window.webRTCAdaptor = webRTCAdaptor;

}

function isConnected(streamId) {
	
	var connected = false;
	if (publishStarted) {
		var state = webRTCAdaptor.signallingState(streamId);
		if (state != null && state != "closed") 
		{
			var iceState = webRTCAdaptor.iceConnectionState(streamId);
			if (iceState != null && iceState != "failed" && iceState != "disconnected") {
				connected = true;		
			}
		}
	}			
	return connected;
}

function stopBroadcasting(message) {
	webRTCAdaptor.stop(message.streamId);
	webRTCAdaptor.closeWebSocket();
}



window.startBroadcasting = startBroadcasting
window.stopBroadcasting = stopBroadcasting
window.isConnected = isConnected