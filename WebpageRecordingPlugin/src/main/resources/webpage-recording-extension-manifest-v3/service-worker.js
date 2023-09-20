chrome.action.onClicked.addListener(async (tab) => {
    const existingContexts = await chrome.runtime.getContexts({});
    let broadcasting = false;

    const offscreenDocument = existingContexts.find(
        (c) => c.contextType === 'OFFSCREEN_DOCUMENT'
    );

    // If an offscreen document is not already open, create one.
    if (!offscreenDocument) {
        // Create an offscreen document.
        await chrome.offscreen.createDocument({
            url: 'offscreen.html',
            reasons: ['USER_MEDIA'],
            justification: 'Broadcasting from chrome.tabCapture API'
        });
    } else {
        broadcasting = offscreenDocument.documentUrl.endsWith('#broadcasting');
    }

    if (broadcasting) {
        chrome.runtime.sendMessage({
            type: 'stop-broadcasting',
            target: 'offscreen',
            streamId: 'stream1'
        });
        chrome.action.setIcon({ path: 'icons/not-broadcasting.png' });
        return;
    }

    // Get a MediaStream for the active tab.
    const streamId = await chrome.tabCapture.getMediaStreamId({
        targetTabId: tab.id
    });

    // Send the stream ID to the offscreen document to start broadcasting.
    chrome.runtime.sendMessage({
        type: 'start-broadcasting',
        target: 'offscreen',
        data: streamId,
        websocketURL: 'wss://ovh36.antmedia.io:5443/WebRTCAppEE/websocket',
        streamId: 'stream1',
    });

    chrome.action.setIcon({ path: '/icons/broadcasting.png' });
});
