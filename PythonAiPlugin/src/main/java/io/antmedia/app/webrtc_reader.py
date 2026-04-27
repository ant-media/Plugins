import os
import time


class WebRTCReader:
    def __init__(self, stream_id, app_name, is_running, dispatch_frame):
        self.stream_id = stream_id
        self.app_name = app_name
        self._is_running = is_running
        self._dispatch_frame = dispatch_frame

    def _build_webrtc_websocket_url(self):
        base_url = os.getenv("ANTMEDIA_WEBSOCKET_BASE_URL", "ws://localhost:5080").rstrip("/")
        return "{}/{}/websocket".format(base_url, self.app_name)

    def read_loop(self, feeders, workers):
        try:
            from webrtc import WebRTCAdapter, init_webrtc
        except Exception as e:
            print("StreamReader: failed to import WebRTC dependencies: {}".format(e))
            return

        websocket_url = self._build_webrtc_websocket_url()
        fps = 20.0
        init_webrtc()
        webrtc_adapter = WebRTCAdapter(websocket_url)
        print("StreamReader: connecting to WebRTC websocket {}".format(websocket_url))
        webrtc_adapter.connect()

        def on_webrtc_video_frame(frame_rgb, stream_id):
            if not self._is_running():
                return
            if frame_rgb is None or frame_rgb.size == 0:
                return
            timestamp_ms = int(time.time() * 1000)
            self._dispatch_frame(frame_rgb, timestamp_ms, fps, feeders, workers)

        try:
            webrtc_adapter.play(self.stream_id, on_webrtc_video_frame, None)
            while self._is_running():
                time.sleep(0.01)
        finally:
            ws_conn = getattr(webrtc_adapter, "ws_conn", None)
            if ws_conn is not None:
                try:
                    ws_conn.close()
                except Exception as e:
                    print("StreamReader: error closing WebRTC websocket: {}".format(e))
