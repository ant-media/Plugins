import json

import cv2
from plugin_base import PluginBase

try:
    from ultralytics import YOLO
except Exception:
    YOLO = None


class _BaseYoloDetectionPlugin(PluginBase):
    def __init__(self, model_name, model_label):
        super().__init__()
        self.model_name = model_name
        self.model_label = model_label
        self.conf_threshold = 0.35
        self.detect_every_n_frames = 20
        self.inference_imgsz = 512
        self.frame_counter = {}
        self.last_objects_by_stream = {}
        self.model = None

        if YOLO is None:
            print("{}: ultralytics is not installed.".format(type(self).__name__))
        else:
            try:
                self.model = YOLO(self.model_name)
                print("{}: loaded model {}".format(type(self).__name__, self.model_name))
            except Exception as e:
                print(
                    "{}: failed to load model {}: {}".format(type(self).__name__, self.model_name, e)
                )
                self.model = None

    def on_stream_started(self, stream_id, width, height):
        self.frame_counter[stream_id] = 0
        self.last_objects_by_stream[stream_id] = []
        print("{}: stream {} started at {}x{}".format(type(self).__name__, stream_id, width, height))

    def on_stream_finished(self, stream_id):
        self.frame_counter.pop(stream_id, None)
        self.last_objects_by_stream.pop(stream_id, None)
        print("{}: stream {} finished".format(type(self).__name__, stream_id))

    def on_video_frame(self, stream_id, frame, timestamp_ms, stream_feeder):
        frame_count = self.frame_counter.get(stream_id, 0) + 1
        self.frame_counter[stream_id] = frame_count

        objects = self.last_objects_by_stream.get(stream_id, [])
        run_detection = self.model is not None and (frame_count % self.detect_every_n_frames) == 0

        if run_detection:
            try:
                result = self.model.predict(
                    frame,
                    conf=self.conf_threshold,
                    verbose=False,
                    imgsz=self.inference_imgsz,
                )[0]
                names = result.names or {}
                objects = []
                if result.boxes is not None:
                    for box in result.boxes:
                        conf = float(box.conf[0].item())
                        cls_id = int(box.cls[0].item())
                        cls_name = str(names.get(cls_id, cls_id))
                        x1, y1, x2, y2 = box.xyxy[0].tolist()
                        objects.append(
                            {
                                "class_id": cls_id,
                                "class_name": cls_name,
                                "confidence": round(conf, 3),
                                "x": int(x1),
                                "y": int(y1),
                                "w": int(x2 - x1),
                                "h": int(y2 - y1),
                            }
                        )
                self.last_objects_by_stream[stream_id] = objects
            except Exception as e:
                print("{}: detection error on {}: {}".format(type(self).__name__, stream_id, e))

        for obj in objects:
            x = obj["x"]
            y = obj["y"]
            w = obj["w"]
            h = obj["h"]
            label = "{} {:.2f}".format(obj["class_name"], obj["confidence"])
            cv2.rectangle(frame, (x, y), (x + w, y + h), (255, 0, 0), 2)
            cv2.putText(
                frame,
                label,
                (x, max(18, y - 8)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (255, 0, 0),
                2,
                cv2.LINE_AA,
            )

        if stream_feeder is not None:
            stream_feeder.write(frame)

        if (not run_detection) or (not objects):
            return

        timestamp_sec = timestamp_ms / 1000.0
        result_json = {
            "stream_id": stream_id,
            "model": self.model_label,
            "timestamp_sec": round(timestamp_sec, 2),
            "timestamp_formatted": "{:02d}:{:02d}:{:05.2f}".format(
                int(timestamp_sec // 3600),
                int((timestamp_sec % 3600) // 60),
                timestamp_sec % 60,
            ),
            "objects_detected": len(objects),
            "object_locations": objects,
        }

        if self.java_callback is not None:
            self.java_callback.onResult(stream_id, json.dumps(result_json))


class YoloGeneralDetectionPlugin(_BaseYoloDetectionPlugin):
    def __init__(self):
        super().__init__(model_name="yolov8n.pt", model_label="yolo_general")
