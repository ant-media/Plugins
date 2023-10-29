import {WebRTCAdaptor} from "./js/webrtc_adaptor.js";

chrome.runtime.onMessage.addListener(async (message) => {
    if (message.target === 'offscreen') {
        switch (message.type) {
            case 'start-broadcasting':
                startBroadcasting(message);
                break;
            case 'stop-broadcasting':
                stopBroadcasting(message.streamId);
                break;
            default:
                throw new Error('Unrecognized message:', message.type);
        }
    }
});

let webRTCAdaptor;
let data = [];

async function startBroadcasting(message) {
    if (typeof webRTCAdaptor !== 'undefined' ) {
        throw new Error('Called startBroadcasting while recording is in progress.');
    }

    let token = "";

    if(message.token != undefined) {
        token = message.token;
    }

    let mediaConstraints = {
        video : true,
        audio : true,
        videoConstraints : {
            mandatory : {
                chromeMediaSource : 'tab',
                minFrameRate: 4,
                maxFrameRate: 20
            }
        }
    };

    if (message.width != undefined && message.height != undefined && message.width > 0 && message.height > 0) {
        mediaConstraints = {
            video : true,
            audio : true,
            videoConstraints : {
                mandatory : {
                    chromeMediaSource : 'tab',
                    minFrameRate: 4,
                    maxFrameRate: 20,
                    maxWidth : message.width,
                    maxHeight : message.height,
                    minWidth : message.width,
                    minHeight : message.height
                }
            }
        };
    }

    const media = await navigator.mediaDevices.getUserMedia({
        audio: {
            mandatory: {
                chromeMediaSource: 'tab',
                chromeMediaSourceId: message.data
            }
        },
        video: {
            mandatory: {
                chromeMediaSource: 'tab',
                chromeMediaSourceId: message.data,
                maxWidth: 1920,
                maxHeight: 1280,
                minWidth: 640,
                minHeight: 480
            }
        }
    });

    let pc_config = {
        'iceServers' : [ {
            'urls' : 'stun:stun1.l.google.com:19302'
        } ]
    };

    let sdpConstraints = {
        OfferToReceiveAudio : false,
        OfferToReceiveVideo : false
    };

    const track = media.getVideoTracks()[0];

    const constra = {
        width: { min: 640, ideal: 1280 },
        height: { min: 480, ideal: 720 },
        advanced: [{ width: 1920, height: 1280 }, { aspectRatio: 1.333 }],
        resizeMode: 'crop-and-scale'
      };

    track.applyConstraints(constra);

    webRTCAdaptor = new WebRTCAdaptor({
        websocket_url : message.websocketURL,
        peerconnection_config : pc_config,
        sdp_constraints : sdpConstraints,
        localStream: media,
        callback : (info, obj) => {
            if (info == "initialized") {
                webRTCAdaptor.publish(message.streamId, token, "", "", "", "");
            }
            console.log(info);
        },
        callbackError : function(error, message) {
            console.log("error callback: " + error + " message: " + message);
        }
    });

    window.location.hash = 'broadcasting';
}

async function stopBroadcasting(streamId) {
    webRTCAdaptor.stop(streamId);
    webRTCAdaptor.mediaManager.localStream.getTracks().forEach(track => track.stop());
    webRTCAdaptor = undefined;

    // Update current state in URL
    window.location.hash = '';
}
