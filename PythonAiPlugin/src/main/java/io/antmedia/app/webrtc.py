# ulimit -n 65536 — raise open-file limit if you hit socket limits
import asyncio
import json
import threading
import time
import websocket

from aiortc import RTCPeerConnection, RTCSessionDescription
from aiortc.contrib.media import MediaPlayer
from aiortc.sdp import candidate_from_sdp

_async_loop = None
_async_loop_lock = threading.Lock()


def _ensure_async_loop():
    global _async_loop
    with _async_loop_lock:
        if _async_loop is not None:
            return _async_loop
        loop = asyncio.new_event_loop()

        def _run():
            asyncio.set_event_loop(loop)
            loop.run_forever()

        t = threading.Thread(target=_run, name="aiortc-async", daemon=True)
        t.start()

        while not loop.is_running():
            time.sleep(0.001)
        _async_loop = loop
        return _async_loop


def _run_coroutine(coro, timeout=120):
    loop = _ensure_async_loop()
    return asyncio.run_coroutine_threadsafe(coro, loop).result(timeout=timeout)


def init_webrtc():
    """Verify aiortc is available; call once before WebRTC use."""
    _ensure_async_loop()
    import aiortc  # noqa: F401


class WebRTCAdapter:
    webrtc_clients = {}

    def __init__(self, URL):
        self.server = URL
        self.client_id = ""
        self._ping_timer = None

    def on_message(self, ws, message):
        data = json.loads(message)

        print("Message: " + data["command"], data)

        if data["command"] == "start":
            self.start_publishing(data["streamId"])
        elif data["command"] == "takeCandidate":
            self.take_candidate(data)
        elif data["command"] == "takeConfiguration":
            self.take_configuration(data)
        elif data["command"] == "notification":
            self.notification(data)
        elif data["command"] == "error":
            print("Message: " + data["definition"])

    def on_error(self, ws, error):
        print("Client {} error: {}".format(self.client_id, error))

    def on_close(self, ws, close_status_code, close_msg):
        print(
            "Client {} closed: {}, {}".format(
                self.client_id, close_status_code, close_msg
            )
        )

    def on_open(self, ws):
        self.isopen.set()
        print(self.ws_conn)
        self._schedule_ping()

    def _schedule_ping(self):
        if self._ping_timer is not None:
            self._ping_timer.cancel()
        self._ping_timer = threading.Timer(5.0, self.send_ping)
        self._ping_timer.daemon = True
        self._ping_timer.start()

    def get_websocket(self):
        return self.ws_conn

    def socket_listner_thread(self):
        ws = websocket.WebSocketApp(
            self.server,
            on_message=self.on_message,
            on_error=self.on_error,
            on_close=self.on_close,
            on_open=self.on_open,
        )

        self.ws_conn = ws
        ws.run_forever()

    def send_ping(self):
        if getattr(self, "ws_conn", None) is None:
            return
        try:
            self.ws_conn.send('{"command": "ping"}')
        except Exception:
            return
        self._schedule_ping()

    def connect(self):
        self.callback = "test"
        self.isopen = threading.Event()
        thread = threading.Thread(target=self.socket_listner_thread, args=())
        thread.daemon = True
        thread.start()
        self.isopen.wait()

    def play(self, id, on_video_callback=None, on_audio_callback=None):
        print("play request sent for id", id)
        wrtc_client_id = id
        if self.wrtc_client_exist(id):
            return
        play_client = WebRTCClient(
            id, "play", self.ws_conn, on_video_callback, on_audio_callback
        )
        WebRTCAdapter.webrtc_clients[wrtc_client_id] = play_client
        play_client.play()

    def start_publishing(self, id):
        if publish_client := self.get_webrtc_client(id):
            publish_client.start_pipeline("publish")
        else:
            print("no client found")

    def publish(self, id):
        wrtc_client_id = id
        if wrtc_client_id in WebRTCAdapter.webrtc_clients:
            return
        publish_client = WebRTCClient(id, "publish", self.ws_conn)
        WebRTCAdapter.webrtc_clients[wrtc_client_id] = publish_client
        publish_client.send_publish_request()

    def wrtc_client_exist(self, id):
        return id in WebRTCAdapter.webrtc_clients

    def get_webrtc_client(self, id):
        if id in WebRTCAdapter.webrtc_clients:
            return WebRTCAdapter.webrtc_clients[id]
        return None

    def take_candidate(self, candidate):
        stream_id = candidate["streamId"]
        webrtc_client = self.get_webrtc_client(stream_id)
        if webrtc_client:
            webrtc_client.take_candidate(candidate)
        else:
            print("no webrtc client exist for this request", stream_id)

    def take_configuration(self, config):
        wrtc_client_id = config["streamId"]

        wrtc_client = self.get_webrtc_client(wrtc_client_id)
        print(wrtc_client, wrtc_client_id, WebRTCAdapter.webrtc_clients)
        if wrtc_client:
            wrtc_client.take_configuration(config)
        else:
            print("no webrtc client exist for this request", wrtc_client_id)

    def notification(self, data):
        if data["definition"] == "publish_started":
            print("Publish Started")
        else:
            print(data["definition"])


class WebRTCClient:
    def __init__(
        self, id, mode, ws_client, on_video_callback=None, on_audio_callback=None
    ):
        self.id = id
        self.pc = None
        self.peer_id = None
        self.mode = mode
        self.websocket_client = ws_client
        self.on_video_callback = on_video_callback
        self.on_audio_callback = on_audio_callback
        self._pending_remote_ice = []
        self._video_consume_tasks = []
        self._player = None

    def send_publish_request(self):
        self.websocket_client.send(
            '{"command":"publish","streamId":"'
            + self.id
            + '", "token":"null","video":true,"audio":true}'
        )

    def play(self):
        self.websocket_client.send(
            '{"command":"play","streamId":"'
            + self.id
            + '", "token":"null"}'
        )

    def _send_sdp(self, sdp_type, sdp_text):
        print("Send SDP " + sdp_type, self.id)
        msg = {
            "command": "takeConfiguration",
            "streamId": self.id,
            "type": sdp_type,
            "sdp": sdp_text,
        }
        self.websocket_client.send(json.dumps(msg))

    async def _wait_ice_complete(self, pc):
        deadline = time.monotonic() + 30.0
        while pc.iceGatheringState != "complete":
            if time.monotonic() > deadline:
                break
            await asyncio.sleep(0.05)

    async def _consume_video(self, track):
        while True:
            try:
                frame = await track.recv()
            except Exception:
                break
            if self.on_video_callback is None:
                continue
            try:
                img = frame.to_ndarray(format="bgr24")
                self.on_video_callback(img, self.id)
            except Exception as e:
                print("video frame callback error: {}".format(e))

    async def _consume_audio(self, track):
        while True:
            try:
                await track.recv()
            except Exception:
                break

    async def _handle_play_offer(self, sdp_text):
        if self.pc is not None:
            try:
                await self._close_async()
            except Exception:
                self.pc = None
                self._video_consume_tasks.clear()
        self.pc = RTCPeerConnection()

        @self.pc.on("track")
        def on_track(track):
            loop = asyncio.get_running_loop()
            if track.kind == "video":
                self._video_consume_tasks.append(
                    loop.create_task(self._consume_video(track))
                )
            elif track.kind == "audio" and self.on_audio_callback:
                self._video_consume_tasks.append(
                    loop.create_task(self._consume_audio(track))
                )

        await self.pc.setRemoteDescription(
            RTCSessionDescription(sdp=sdp_text, type="offer")
        )
        await self._flush_pending_ice()
        answer = await self.pc.createAnswer()
        await self.pc.setLocalDescription(answer)
        await self._wait_ice_complete(self.pc)
        local = self.pc.localDescription
        self._send_sdp(local.type, local.sdp)

    async def _flush_pending_ice(self):
        if self.pc is None:
            return
        pending = self._pending_remote_ice
        self._pending_remote_ice = []
        for c in pending:
            if c is None:
                await self.pc.addIceCandidate(None)
            else:
                await self.pc.addIceCandidate(c)

    def _parse_remote_candidate(self, data):
        raw = data.get("candidate")
        if raw is None or (isinstance(raw, str) and not raw.strip()):
            return None
        s = raw.strip()
        if s.startswith("candidate:"):
            s = s[len("candidate:") :].strip()
        try:
            c = candidate_from_sdp(s)
        except Exception as e:
            print("bad remote candidate: {}".format(e))
            return None
        c.sdpMLineIndex = int(data["label"])
        mid = data.get("id")
        if mid is not None and str(mid).strip() != "":
            c.sdpMid = str(mid)
        return c

    async def _add_remote_candidate_async(self, data):
        raw = data.get("candidate")
        if raw is None or (isinstance(raw, str) and not raw.strip()):
            cand = None
        else:
            cand = self._parse_remote_candidate(data)
            if cand is None:
                return
        if self.pc is None:
            self._pending_remote_ice.append(cand)
            return
        if self.pc.remoteDescription is None:
            self._pending_remote_ice.append(cand)
            return
        if cand is None:
            await self.pc.addIceCandidate(None)
        else:
            await self.pc.addIceCandidate(cand)

    def take_candidate(self, data):
        try:
            _run_coroutine(self._add_remote_candidate_async(data), timeout=30)
        except Exception as e:
            print("take_candidate failed: {}".format(e))

    async def _start_publish(self):
        self.pc = RTCPeerConnection()
        try:
            self._player = MediaPlayer(
                "testsrc=size=640x480:rate=30",
                format="lavfi",
                options={"f": "lavfi"},
            )
        except Exception as e:
            print("MediaPlayer (lavfi) failed: {}".format(e))
            self._player = None
            await self.pc.close()
            self.pc = None
            return

        if self._player and self._player.video:
            self.pc.addTrack(self._player.video)
        else:
            print("no video track from MediaPlayer; publish aborted")
            await self.pc.close()
            self.pc = None
            return

        offer = await self.pc.createOffer()
        await self.pc.setLocalDescription(offer)
        await self._wait_ice_complete(self.pc)
        local = self.pc.localDescription
        self._send_sdp(local.type, local.sdp)

    async def _set_remote_answer(self, sdp_text):
        if self.pc is None:
            return
        await self.pc.setRemoteDescription(
            RTCSessionDescription(sdp=sdp_text, type="answer")
        )
        await self._flush_pending_ice()

    def start_pipeline(self, mode):
        print("Creating WebRTC connection", mode, self.id)
        if mode == "publish":
            try:
                _run_coroutine(self._start_publish(), timeout=120)
            except Exception as e:
                print("start_pipeline publish failed: {}".format(e))
        elif mode == "play":
            print("play pipeline is driven by remote offer (aiortc)")

    def take_configuration(self, data):
        if data["type"] == "answer":
            if not self.pc:
                print("take_configuration answer but no peer connection")
                return
            try:
                _run_coroutine(self._set_remote_answer(data["sdp"]), timeout=60)
            except Exception as e:
                print("setRemoteDescription (answer) failed: {}".format(e))

        elif data["type"] == "offer":
            try:
                _run_coroutine(self._handle_play_offer(data["sdp"]), timeout=120)
            except Exception as e:
                print("handle play offer failed: {}".format(e))

    async def _close_async(self):
        for t in self._video_consume_tasks:
            t.cancel()
        if self._video_consume_tasks:
            await asyncio.gather(*self._video_consume_tasks, return_exceptions=True)
        self._video_consume_tasks.clear()
        if self.pc:
            await self.pc.close()
            self.pc = None
        self._player = None

    def close_pipeline(self):
        print("Close Pipeline")
        try:
            _run_coroutine(self._close_async(), timeout=30)
        except Exception as e:
            print("close_pipeline: {}".format(e))
        self._pending_remote_ice.clear()

    def stop(self):
        if getattr(self, "websocket_client", None) is not None:
            try:
                self.websocket_client.close()
            except Exception:
                pass
