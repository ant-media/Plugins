import cv2
import os
import json
from plugin_base import PluginBase


class FaceDetectionPlugin(PluginBase):
    def __init__(self):
        super().__init__()
        model_path = os.path.dirname(os.path.abspath(__file__))
        model_path = os.path.join(model_path, "haarcascade_frontalface_default.xml")
        self.face_classifier = cv2.CascadeClassifier(model_path)

    def on_stream_started(self, stream_id, width, height):
        print("SamplePlugin: stream {} started at {}x{}".format(stream_id, width, height))

    def on_stream_finished(self, stream_id):
        print("SamplePlugin: stream {} finished".format(stream_id))

    def on_video_frame(self, stream_id, frame, timestamp_ms, stream_feeder):
        gray_image = cv2.cvtColor(frame, cv2.COLOR_RGB2GRAY)

        faces, _, weights = self.face_classifier.detectMultiScale3(
            gray_image, scaleFactor=1.1, minNeighbors=5, minSize=(40, 40),
            outputRejectLevels=True
        )

        confident_faces = []
        for (x, y, w, h), weight in zip(faces, weights):
            if weight >= 5:
                cv2.rectangle(frame, (x, y), (x + w, y + h), (0, 255, 0), 4)
                confident_faces.append({
                    "x": int(x), "y": int(y), "w": int(w), "h": int(h),
                    "confidence": round(float(weight), 2)
                })

        if stream_feeder is not None:
            stream_feeder.write(frame)

        if len(confident_faces) == 0:
            return

        timestamp_sec = timestamp_ms / 1000.0
        result = {
            "stream_id": stream_id,
            "timestamp_sec": round(timestamp_sec, 2),
            "timestamp_formatted": "{:02d}:{:02d}:{:05.2f}".format(
                int(timestamp_sec // 3600),
                int((timestamp_sec % 3600) // 60),
                timestamp_sec % 60),
            "faces_detected": len(confident_faces),
            "face_locations": confident_faces
        }

        if self.java_callback is not None:
            self.java_callback.onResult(stream_id, json.dumps(result))
