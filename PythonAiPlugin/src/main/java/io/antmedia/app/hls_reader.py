import cv2
import time


class HLSReader:
    def __init__(self, stream_id, hls_url, is_running, dispatch_frame):
        self.stream_id = stream_id
        self.hls_url = hls_url
        self._is_running = is_running
        self._dispatch_frame = dispatch_frame

    def _open_with_retry(self, max_retries=5, retry_delay=3):
        for attempt in range(1, max_retries + 1):
            if not self._is_running():
                return None
            cap = cv2.VideoCapture(self.hls_url)
            if cap.isOpened():
                print(
                    "StreamReader: opened HLS URL {} on attempt {}".format(
                        self.hls_url, attempt
                    )
                )
                return cap
            cap.release()
            print(
                "StreamReader: failed to open HLS URL {} (attempt {}/{})".format(
                    self.hls_url, attempt, max_retries
                )
            )
            if attempt < max_retries:
                time.sleep(retry_delay)
        return None

    def read_loop(self, feeders, workers):
        if not self.hls_url:
            print("StreamReader: missing HLS URL for stream {}".format(self.stream_id))
            return

        cap = self._open_with_retry()
        if cap is None:
            print(
                "StreamReader: giving up on HLS URL {} after retries".format(
                    self.hls_url
                )
            )
            return

        fps = 20.0
        consecutive_failures = 0
        max_read_failures = 30

        try:
            while self._is_running():
                if not cap.isOpened():
                    cap.release()
                    print(
                        "StreamReader: connection lost for {}, reconnecting...".format(
                            self.stream_id
                        )
                    )
                    cap = self._open_with_retry()
                    if cap is None:
                        break
                    consecutive_failures = 0

                ret, frame = cap.read()
                if not ret:
                    consecutive_failures += 1
                    if consecutive_failures >= max_read_failures:
                        print(
                            "StreamReader: too many read failures for {}, reconnecting...".format(
                                self.stream_id
                            )
                        )
                        cap.release()
                        cap = self._open_with_retry()
                        if cap is None:
                            break
                        consecutive_failures = 0
                    else:
                        time.sleep(0.5)
                    continue

                consecutive_failures = 0
                timestamp_ms = cap.get(cv2.CAP_PROP_POS_MSEC)
                self._dispatch_frame(frame, timestamp_ms, fps, feeders, workers)
        finally:
            cap.release()
