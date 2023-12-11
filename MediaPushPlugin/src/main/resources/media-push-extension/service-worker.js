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
        websocketURL: tab.websocketURL,
        streamId: tab.streamId,
    });

    chrome.action.setIcon({ path: '/icons/broadcasting.png' });
});

chrome.contextMenus.create({
    id: "ant-media-publish",
    title: "Ant Media Publish",
    contexts: ["all"]
});

chrome.contextMenus.onClicked.addListener(async function (info, tab) {
    const broadcastInformation = await chrome.storage.session.get('broadcastInformation');

    if (broadcastInformation.broadcastInformation == undefined || 
        broadcastInformation.broadcastInformation == null ||
        broadcastInformation.broadcastInformation.websocketURL == undefined ||
        broadcastInformation.broadcastInformation.streamId == undefined) {
            console.warn("Please set websocketURL and streamId first");
            alert("Please set websocketURL and streamId first");
            return;
    }
    
    if (info.menuItemId == "ant-media-publish") {
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
                streamId: broadcastInformation.broadcastInformation.streamId
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
            websocketURL: broadcastInformation.broadcastInformation.websocketURL,
            streamId: broadcastInformation.broadcastInformation.streamId,
            token: broadcastInformation.broadcastInformation.token,
            width: broadcastInformation.broadcastInformation.width,
            height: broadcastInformation.broadcastInformation.height
        });

        chrome.action.setIcon({ path: '/icons/broadcasting.png' });
    }
});

chrome.runtime.onMessageExternal.addListener(
    async (request, sender, sendResponse) => {
        let broadcastInformation = {broadcastInformation: {websocketURL: request.websocketURL, streamId: request.streamId, token: request.token, width: request.width, height: request.height}};
        await chrome.storage.session.set(broadcastInformation);
    });
