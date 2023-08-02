import {WebRTCAdaptor} from "../webpage-recording-extension-manifest-v2/js/webrtc_adaptor";

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
    if (typeof webRTCAdaptor === 'undefined' ) {
        throw new Error('Called startBroadcasting while recording is in progress.');
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
                chromeMediaSourceId: message.data
            }
        }
    });

    const stream = await getVideoAudioStream(mediaConstraints);

    let pc_config = {
        'iceServers' : [ {
            'urls' : 'stun:stun1.l.google.com:19302'
        } ]
    };

    let sdpConstraints = {
        OfferToReceiveAudio : false,
        OfferToReceiveVideo : false
    };

    webRTCAdaptor = new WebRTCAdaptor({
        websocket_url : message.websocketURL,
        mediaConstraints : mediaConstraints,
        peerconnection_config : pc_config,
        sdp_constraints : sdpConstraints,
        isPlayMode : true,
        localVideoId : "localVideo",
        callback : (info, obj) => {
            if (info == "initialized") {
                webRTCAdaptor.publish(message.streamId, "", "", "", "", "");
            }
            console.log(info);
        },
        callbackError : function(error, message) {}
    });
    webRTCAdaptor.setLocalStream(stream);

    window.location.hash = 'broadcasting';
}

async function stopBroadcasting(streamId) {
    webRTCAdaptor.stop(streamId);
    webRTCAdaptor = undefined;

    // Update current state in URL
    window.location.hash = '';
}
