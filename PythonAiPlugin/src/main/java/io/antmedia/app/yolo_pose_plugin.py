import json

import cv2
from plugin_base import PluginBase

try:
    from ultralytics import YOLO
except Exception:
    YOLO = None


class YoloPoseDetectionPlugin(PluginBase):
    def __init__(self):
        super().__init__()
        self.model_name = "yolov8n-pose.pt"
        self.conf_threshold = 0.35
        self.detect_every_n_frames = 20
        self.frame_counter = {}
        self.last_people_by_stream = {}
        self.model = None

        if YOLO is None:
            print("YoloPoseDetectionPlugin: ultralytics is not installed.")
        else:
            try:
                self.model = YOLO(self.model_name)
                print("YoloPoseDetectionPlugin: loaded model {}".format(self.model_name))
            except Exception as e:
                print(
                    "YoloPoseDetectionPlugin: failed to load model {}: {}".format(
                        self.model_name, e
                    )
                )
                self.model = None

    def on_stream_started(self, stream_id, width, height):
        self.frame_counter[stream_id] = 0
        self.last_people_by_stream[stream_id] = []
        print("YoloPoseDetectionPlugin: stream {} started at {}x{}".format(stream_id, width, height))

    def on_stream_finished(self, stream_id):
        self.frame_counter.pop(stream_id, None)
        self.last_people_by_stream.pop(stream_id, None)
        print("YoloPoseDetectionPlugin: stream {} finished".format(stream_id))

    def on_video_frame(self, stream_id, frame, timestamp_ms, stream_feeder):
        frame_count = self.frame_counter.get(stream_id, 0) + 1
        self.frame_counter[stream_id] = frame_count

        people = self.last_people_by_stream.get(stream_id, [])
        run_detection = self.model is not None and (frame_count % self.detect_every_n_frames) == 0

        if run_detection:
            try:
                result = self.model.predict(
                    frame,
                    conf=self.conf_threshold,
                    verbose=False,
                    imgsz=640,
                )[0]
                people = []
                boxes = result.boxes
                keypoints = result.keypoints
                if boxes is not None and keypoints is not None:
                    for idx, box in enumerate(boxes):
                        conf = float(box.conf[0].item())
                        x1, y1, x2, y2 = box.xyxy[0].tolist()
                        kpts_xy = keypoints.xy[idx].tolist()
                        person = {
                            "confidence": round(conf, 3),
                            "x": int(x1),
                            "y": int(y1),
                            "w": int(x2 - x1),
                            "h": int(y2 - y1),
                            "keypoints": [],
                        }
                        for p in kpts_xy:
                            if len(p) >= 2:
                                person["keypoints"].append(
                                    {"x": int(p[0]), "y": int(p[1])}
                                )
                        people.append(person)
                self.last_people_by_stream[stream_id] = people
            except Exception as e:
                print("YoloPoseDetectionPlugin: detection error on {}: {}".format(stream_id, e))

        # Draw cached/latest detections on every frame for smoother restream.
        for person in people:
            x = person["x"]
            y = person["y"]
            w = person["w"]
            h = person["h"]
            cv2.rectangle(frame, (x, y), (x + w, y + h), (128, 255, 0), 2)
            for kp in person["keypoints"]:
                cv2.circle(frame, (kp["x"], kp["y"]), 2, (0, 255, 255), -1)

        if stream_feeder is not None:
            stream_feeder.write(frame)

        if (not run_detection) or (not people):
            return

        timestamp_sec = timestamp_ms / 1000.0
        result_json = {
            "stream_id": stream_id,
            "model": "yolo_pose",
            "timestamp_sec": round(timestamp_sec, 2),
            "timestamp_formatted": "{:02d}:{:02d}:{:05.2f}".format(
                int(timestamp_sec // 3600),
                int((timestamp_sec % 3600) // 60),
                timestamp_sec % 60,
            ),
            "people_detected": len(people),
            "people": people,
        }

        if self.java_callback is not None:
            self.java_callback.onResult(
                stream_id, self.app_name_for(stream_id), json.dumps(result_json)
            )
