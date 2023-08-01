"use strict";

import {WebRTCAdaptor} from "./js/webrtc_adaptor.js"

function getVideoAudioStream(video, audio) {
    let options = {
        audio,
        video
    };
    return new Promise((resolve, reject) => {
        chrome.tabCapture.capture(options, stream => {
            resolve(stream);
        });
    });
}

chrome.runtime.onConnect.addListener(port => {
    let webRTCAdaptor;

    let mediaConstraints;

    port.onMessage.addListener(async (message) => {
        console.log('GOT MESSAGE', message);

        mediaConstraints = {
            video : true,
            audio : true,
            videoConstraints : {
                mandatory : {
                    chromeMediaSource : 'tab',
                    minFrameRate: 4,
                    maxFrameRate: 30,
                    maxWidth : message.width,
                    maxHeight : message.height,
                    minWidth : message.width,
                    minHeight : message.height
                }
            }
        };
        
        if (message.command === 'WR_START_BROADCASTING') {
            const stream = await getVideoAudioStream(mediaConstraints.video, mediaConstraints.audio);

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
        }
        else if (message.command === 'WR_STOP_BROADCASTING') {
            webRTCAdaptor.stop(message.streamId);
        }
    });
});



/*
{
	"manifest_version": 3,
	"name": "Ant Media Webpage Recorder Extension",
	"version": "1.1.0",
	"background": {
		"service_worker": "background.js"
	},
	"content_scripts": [
		{
			"matches": [
				"<all_urls>"
			],
			"js": [
				"content_script.js"
			],
			"run_at": "document_start"
		}
	],
	"host_permissions": [
		"downloads",
		"tabCapture"
	],
	"permissions": [
		"activeTab",
		"tabs"
	],
	"action": {
		"default_popup": "popup.html"
	},
	"web_accessible_resources": [
		{
			"resources": [
				"background.js"
			],
			"matches": [
				"<all_urls>"
			]
		}
	]
}
*/