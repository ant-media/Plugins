import threading
import queue
import os
from stream_feeder import StreamFeeder
from hls_reader import HLSReader
from webrtc_reader import WebRTCReader


class _PluginWorker:
    def __init__(self, plugin, stream_id, app_name, feeder):
        self.plugin = plugin
        self.stream_id = stream_id
        self.app_name = app_name or ""
        self.feeder = feeder
        self._queue = queue.Queue(maxsize=60)
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
                    self.app_name,
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
    def __init__(
        self,
        stream_id,
        app_name,
        plugins,
        source_type=None,
        width=None,
        height=None,
        streams_dir=None,
    ):
        self.stream_id = stream_id
        self.plugins = plugins
        self.app_name = app_name or ""
        self.source_type = self._resolve_source_type(source_type)
        self.width = width
        self.height = height
        default_ams_dir = os.getenv("AMS_DIR", "/usr/local/antmedia/")
        self.streams_dir = streams_dir or os.path.join(default_ams_dir, "webapps", "LiveApp", "streams")
        self.hls_url = self._build_hls_url()
        self.running = False
        self.thread = None

    def _resolve_source_type(self, source_type):
        selected = source_type
        if selected is None:
            selected = os.getenv("STREAM_READER_SOURCE", "webrtc")
        selected = str(selected).strip().lower()
        if selected in ("hls", "webrtc"):
            return selected
        print("Invalid STREAM_READER_SOURCE '{}', defaulting to hls".format(selected))
        return "hls"

    def _build_hls_url(self):
        base_url = os.getenv("ANTMEDIA_HTTP_BASE_URL", "http://localhost:5080").rstrip("/")
        return "{}/{}/streams/{}.m3u8".format(base_url, self.app_name, self.stream_id)

    def _is_running(self):
        return self.running

    def _create_source_reader(self):
        if self.source_type == "webrtc":
            return WebRTCReader(
                stream_id=self.stream_id,
                app_name=self.app_name,
                is_running=self._is_running,
                dispatch_frame=self._dispatch_frame,
            )
        return HLSReader(
            stream_id=self.stream_id,
            hls_url=self.hls_url,
            is_running=self._is_running,
            dispatch_frame=self._dispatch_frame,
        )

    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._read_loop, daemon=True)
        self.thread.start()
        print(
            "StreamReader started for stream {} with source {}".format(
                self.stream_id, self.source_type
            )
        )

    def stop(self):
        self.running = False
        if self.thread is not None:
            self.thread.join(timeout=5)
        print("StreamReader stopped for stream {}".format(self.stream_id))

    def _initialize_outputs(self, fps, feeders, workers):
        if self.width is None or self.height is None:
            print(
                "StreamReader: width/height are required to initialize outputs for stream {}".format(
                    self.stream_id
                )
            )
            return False
        frame_width = int(self.width)
        frame_height = int(self.height)
        if frame_width <= 0 or frame_height <= 0:
            print(
                "StreamReader: invalid output dimensions {}x{} for stream {}".format(
                    frame_width, frame_height, self.stream_id
                )
            )
            return False
        for plugin in self.plugins:
            output_stream_id = "{}_{}".format(self.stream_id, type(plugin).__name__)
            feeder = StreamFeeder(
                self.stream_id,
                self.app_name,
                output_stream_id,
                frame_width,
                frame_height,
                fps=fps,
                streams_dir=self.streams_dir,
            )
            feeders[plugin] = feeder
            worker = _PluginWorker(plugin, self.stream_id, self.app_name, feeder)
            worker.start()
            workers[plugin] = worker
        return True

    def _dispatch_frame(self, frame, timestamp_ms, fps, feeders, workers):
        for plugin in self.plugins:
            worker = workers.get(plugin)
            if worker is not None:
                worker.submit(frame.copy(), timestamp_ms)

    def _stop_outputs(self, feeders, workers):
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

    def _read_loop(self):
        feeders = {}
        workers = {}
        try:
            if not self._initialize_outputs(fps=20.0, feeders=feeders, workers=workers):
                return
            source_reader = self._create_source_reader()
            source_reader.read_loop(feeders, workers)
        finally:
            self._stop_outputs(feeders, workers)
