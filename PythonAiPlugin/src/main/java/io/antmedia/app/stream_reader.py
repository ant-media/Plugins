import cv2
import time
import threading
import queue
import os
from stream_feeder import StreamFeeder


class _PluginWorker:
    def __init__(self, plugin, stream_id, feeder):
        self.plugin = plugin
        self.stream_id = stream_id
        self.feeder = feeder
        self._queue = queue.Queue(maxsize=15)
        self._running = False
        self._thread = None

    def start(self):
        self._running = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def submit(self, frame, timestamp_ms):
        if not self._running:
            return
        try:
            self._queue.put_nowait((frame, timestamp_ms))
        except queue.Full:
            try:
                self._queue.get_nowait()
            except queue.Empty:
                pass
            try:
                self._queue.put_nowait((frame, timestamp_ms))
            except queue.Full:
                pass

    def _run(self):
        while self._running:
            try:
                frame, timestamp_ms = self._queue.get(timeout=0.5)
            except queue.Empty:
                continue
            try:
                self.plugin.on_video_frame(
                    self.stream_id,
                    frame,
                    timestamp_ms,
                    self.feeder,
                )
            except Exception as e:
                print("Error in {}.on_video_frame: {}".format(type(self.plugin).__name__, e))

    def stop(self):
        self._running = False
        if self._thread is not None:
            self._thread.join(timeout=3)


class StreamReader:
    def __init__(self, stream_id, hls_url, plugins, width=None, height=None, streams_dir=None):
        self.stream_id = stream_id
        self.hls_url = hls_url
        self.plugins = plugins
        self.width = width
        self.height = height
        default_ams_dir = os.getenv("AMS_DIR", "/usr/local/antmedia/")
        self.streams_dir = streams_dir or os.path.join(default_ams_dir, "webapps", "LiveApp", "streams")
        self.running = False
        self.thread = None

    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._read_loop, daemon=True)
        self.thread.start()
        print("StreamReader started for stream {} at {}".format(self.stream_id, self.hls_url))

    def stop(self):
        self.running = False
        if self.thread is not None:
            self.thread.join(timeout=5)
        print("StreamReader stopped for stream {}".format(self.stream_id))

    def _open_with_retry(self, max_retries=5, retry_delay=3):
        for attempt in range(1, max_retries + 1):
            if not self.running:
                return None
            cap = cv2.VideoCapture(self.hls_url)
            if cap.isOpened():
                print("StreamReader: opened HLS URL {} on attempt {}".format(self.hls_url, attempt))
                return cap
            cap.release()
            print("StreamReader: failed to open HLS URL {} (attempt {}/{})".format(
                self.hls_url, attempt, max_retries))
            if attempt < max_retries:
                time.sleep(retry_delay)
        return None

    def _read_loop(self):
        cap = self._open_with_retry()
        if cap is None:
            print("StreamReader: giving up on HLS URL {} after retries".format(self.hls_url))
            return

        fps = 20.0
      
        feeders = {}
        workers = {}

        consecutive_failures = 0
        max_read_failures = 30

        try:
            while self.running:
                if not cap.isOpened():
                    cap.release()
                    print("StreamReader: connection lost for {}, reconnecting...".format(self.stream_id))
                    cap = self._open_with_retry()
                    if cap is None:
                        break
                    consecutive_failures = 0

                ret, frame = cap.read()
                if not ret:
                    consecutive_failures += 1
                    if consecutive_failures >= max_read_failures:
                        print("StreamReader: too many read failures for {}, reconnecting...".format(self.stream_id))
                        cap.release()
                        cap = self._open_with_retry()
                        if cap is None:
                            break
                        consecutive_failures = 0
                    else:
                        time.sleep(0.5)
                    continue

                consecutive_failures = 0
                if not feeders:
                    frame_height, frame_width = frame.shape[:2]
                    for plugin in self.plugins:
                        output_stream_id = "{}_{}".format(self.stream_id, type(plugin).__name__)
                        feeder = StreamFeeder(
                            input_stream_id=self.stream_id,
                            output_stream_id=output_stream_id,
                            width=frame_width,
                            height=frame_height,
                            fps=fps,
                            streams_dir=self.streams_dir,
                        )
                        feeders[plugin] = feeder
                        worker = _PluginWorker(plugin, self.stream_id, feeder)
                        worker.start()
                        workers[plugin] = worker
                timestamp_ms = cap.get(cv2.CAP_PROP_POS_MSEC)
                for plugin in self.plugins:
                    worker = workers.get(plugin)
                    if worker is not None:
                        worker.submit(frame.copy(), timestamp_ms)
        finally:
            cap.release()
            for worker in workers.values():
                try:
                    worker.stop()
                except Exception as e:
                    print("Error stopping plugin worker: {}".format(e))
            for feeder in feeders.values():
                try:
                    feeder.stop()
                except Exception as e:
                    print("Error stopping stream feeder: {}".format(e))
