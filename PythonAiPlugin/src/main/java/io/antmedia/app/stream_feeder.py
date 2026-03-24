import subprocess
import threading
import os
import cv2


class StreamFeeder:
    def __init__(
        self,
        input_stream_id,
        output_stream_id,
        width,
        height,
        fps=25.0,
        streams_dir=None,
    ):
        self.input_stream_id = input_stream_id
        self.output_stream_id = output_stream_id
        self.width = int(width)
        self.height = int(height)
        self.fps = float(fps) if fps and fps > 0 else 25.0
        if streams_dir is None:
            default_ams_dir = os.getenv("AMS_DIR", "/usr/local/antmedia/")
            streams_dir = os.path.join(default_ams_dir, "webapps", "LiveApp", "streams")
        self.streams_dir = streams_dir
        self.playlist_path = os.path.join(self.streams_dir, "{}.m3u8".format(self.output_stream_id))
        self.segment_pattern = os.path.join(self.streams_dir, "{}%09d.ts".format(self.output_stream_id))

        self._process = None
        self._lock = threading.Lock()
        self._restart_count = 0
        self._max_restarts = 3
        self._closed = False

    def _start_process(self):
        gop_size = 25
        os.makedirs(self.streams_dir, exist_ok=True)
        cmd = [
            "ffmpeg",
            "-loglevel",
            "error",
            "-y",
            "-f",
            "rawvideo",
            "-pix_fmt",
            "bgr24",
            "-s",
            "{}x{}".format(self.width, self.height),
            "-framerate",
            "{:.2f}".format(self.fps),
            "-i",
            "-",
            "-an",
            "-c:v",
            "libx264",
            "-g",
            str(gop_size),
            "-keyint_min",
            str(gop_size),
            "-preset",
            "ultrafast",
            "-tune",
            "zerolatency",
            "-r",
            "{:.2f}".format(self.fps),
            "-vsync",
            "1",
            "-pix_fmt",
            "yuv420p",
            "-f",
            "hls",
            "-hls_time",
            "2",
            "-hls_list_size",
            "6",
            "-hls_flags",
            "delete_segments+append_list+omit_endlist",
            "-hls_segment_filename",
            self.segment_pattern,
            self.playlist_path,
        ]
        self._process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        print("StreamFeeder started: {} -> {}".format(self.input_stream_id, self.playlist_path))

    def _ensure_running(self):
        if self._closed:
            return False
        if self._process is None:
            self._start_process()
            return True
        if self._process.poll() is not None:
            if self._restart_count >= self._max_restarts:
                print("StreamFeeder restart limit reached for {}".format(self.output_stream_id))
                return False
            self._restart_count += 1
            print(
                "StreamFeeder restarting {} ({}/{})".format(
                    self.output_stream_id, self._restart_count, self._max_restarts
                )
            )
            self._start_process()
        return True

    def write(self, frame):
        with self._lock:
            if not self._ensure_running():
                return False
            if frame is None:
                return False
            frame_h, frame_w = frame.shape[:2]
            if frame_w != self.width or frame_h != self.height:
                frame = cv2.resize(frame, (self.width, self.height), interpolation=cv2.INTER_LINEAR)
            try:
                self._process.stdin.write(frame.tobytes())
                return True
            except Exception:
                # Retry once after restart on broken pipe/process crash.
                try:
                    if self._process is not None:
                        self._process.kill()
                except Exception:
                    pass
                self._process = None
                if not self._ensure_running():
                    return False
                try:
                    self._process.stdin.write(frame.tobytes())
                    return True
                except Exception:
                    return False

    def stop(self):
        with self._lock:
            self._closed = True
            if self._process is not None:
                try:
                    if self._process.stdin:
                        self._process.stdin.close()
                except Exception:
                    pass
                try:
                    self._process.terminate()
                    self._process.wait(timeout=3)
                except Exception:
                    try:
                        self._process.kill()
                    except Exception:
                        pass
                self._process = None
            print("StreamFeeder stopped: {}".format(self.output_stream_id))
