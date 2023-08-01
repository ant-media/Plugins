const port = chrome.runtime.connect(chrome.runtime.id);

window.addEventListener('message', (ev) => {
    console.log('GOT MESSAGE', ev);
    port.postMessage(ev.data);
});
