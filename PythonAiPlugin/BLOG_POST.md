# Integrate Your Custom AI Models into Live Streams with the Python AI Plugin

*A developer-focused plugin for Ant Media Server that lets you run your own Python models on live video—with real-time overlays and a REST API for detection data.*

---

The **Python AI Plugin** is built for developers who want to integrate custom AI models into their live streaming pipeline. Instead of routing video through external services or building your own ingestion pipeline, you implement a small Python class, register it, and your model runs on every live stream that hits Ant Media Server. You get a processed HLS output (with overlays), timestamped detection data via REST API, and a built-in viewer to debug and compare streams.

The plugin ships with **example implementations** you can use as templates or reference when building your own.

---

## Architecture at a Glance

When a stream is published (WebRTC, RTMP, or RTSP):

1. The plugin receives video frames from the live HLS feed.
2. Each registered Python plugin processes frames in its own worker thread.
3. Plugins run inference, optionally draw on the frame, and pass it to `stream_feeder.write(frame)` to publish a processed HLS stream.
4. Plugins can call `java_callback.onResult(stream_id, json_str)` to persist detection data for the REST API.
5. Multiple plugins run in parallel; each publishes its own processed stream (e.g. `streamId_FaceDetectionPlugin`, `streamId_YoloGeneralDetectionPlugin`).

---

## Example Plugins: Reference Implementations

The plugin includes three example implementations in `src/main/java/io/antmedia/app/`. Use them as starting points for your own models.

### 1. `sample_plugin.py` – Face Detection (OpenCV Haar)

A lightweight example using OpenCV’s `CascadeClassifier`. Good as a first template because it keeps the flow simple: load the model once, process every frame, draw, output, and optionally persist results.

**Constructor (`__init__`):**  
Loads `haarcascade_frontalface_default.xml` from the same directory as the plugin and creates a `CascadeClassifier`. This runs once when the plugin is initialized.

**`on_stream_started` / `on_stream_finished`:**  
Only logs stream start/finish. No per-stream state, since the classifier is shared.

**`on_video_frame` – the core loop:**

1. **Grayscale** – Converts the frame to grayscale (`cv2.cvtColor(..., cv2.COLOR_RGB2GRAY)`); Haar cascades expect grayscale input.
2. **Detect** – Calls `detectMultiScale3()` with `scaleFactor=1.1`, `minNeighbors=5`, `minSize=(40, 40)`, and `outputRejectLevels=True` to get confidence weights.
3. **Filter** – Keeps only detections with `weight >= 5`, then draws green rectangles on the frame and collects them into `confident_faces`.
4. **Output video** – Calls `stream_feeder.write(frame)` to publish the annotated frame as HLS.
5. **Output data** – If any faces were found and `java_callback` is set, builds a JSON object with `stream_id`, `timestamp_sec`, `timestamp_formatted`, `faces_detected`, and `face_locations`, then calls `java_callback.onResult(stream_id, json.dumps(result))` so the REST API can serve it.

**Flow:** `Frame in → Grayscale → Haar cascade → Filter by weight → Draw boxes → stream_feeder.write()` + `java_callback.onResult()` when faces exist.

**Patterns to reuse:** Simple per-frame processing (no throttling—Haar is cheap), straightforward `java_callback.onResult()` usage, and a clear JSON result structure.

### 2. `yolo_detection_plugin.py` – General Object Detection (YOLOv8)

Uses Ultralytics YOLOv8 for 80+ object classes:
- Loads the model in `__init__` (`yolov8n.pt`)
- **Throttles inference** with `detect_every_n_frames = 20` – runs detection every 20 frames, caches results for intermediate frames (smoother video, lower GPU load)
- `frame_counter` and `last_objects_by_stream` for per-stream state
- Draws bounding boxes with class labels and confidence
- Uses `_BaseYoloDetectionPlugin` so you can subclass and swap models (e.g. `yolov8s.pt`)

**Patterns to reuse:** Frame throttling, result caching for smooth overlays, base class for multiple YOLO variants.

### 3. `yolo_pose_plugin.py` – Pose Detection (YOLOv8-Pose)

Tracks people and body keypoints:
- Uses `yolov8n-pose.pt`
- Same throttling pattern (`detect_every_n_frames = 20`) and per-stream caches
- Extracts `keypoints` from the YOLO result and draws boxes + keypoint circles
- Emits `people` with `keypoints` arrays for downstream use

**Patterns to reuse:** Keypoint extraction, structured JSON output for pose data, drawing overlays on every frame using cached detections.

---

## Adding Your Own Plugin

### 1. Implement a Python Class Extending `PluginBase`

Create a new file (e.g. `my_model_plugin.py`) in the plugin directory. Your class must implement:

- **`on_stream_started(stream_id, width, height)`** – Load models, init per-stream caches.
- **`on_stream_finished(stream_id)`** – Clean up per-stream state.
- **`on_video_frame(stream_id, frame, timestamp_ms, stream_feeder)`** – Run inference, draw on `frame` (OpenCV BGR numpy array), call `stream_feeder.write(frame)`, and optionally `self.java_callback.onResult(stream_id, json.dumps(...))`.

**Tip:** Copy one of the example plugins and adapt it. For heavy models, use `detect_every_n_frames` and cache results like `yolo_detection_plugin.py` or `yolo_pose_plugin.py`.

### 2. Register in `init_plugins.py`

```python
from my_model_plugin import MyModelPlugin
 
def init_plugins(register_plugin, callbacks):
    # ... existing plugins ...

    my_plugin = MyModelPlugin()
    my_plugin.set_java_callback(callbacks.get("my_model_detections") or callbacks.get("default"))
    register_plugin(my_plugin)
```

### 3. Optional: REST API & Detection Viewer

For REST API and Detection Viewer support:
- Add a `ResultCallback` and `set_java_callback` entry in `JepPythonBridge.java` for your table name.
- Add your model to `web/viewer/index.html` and the mapping in `web/viewer/viewer.js`.

### 4. Redeploy

Run `./redeploy.sh`. Your plugin runs for every live stream.

---

## REST API for Detection Data

```
GET /rest/python-plugin/detections/{model}/{streamId}/seconds/{seconds}
GET /rest/python-plugin/detections/{model}/{streamId}/count/{lastn}
```

Built-in models: `face_detections`, `yolo_general_detections`, `pose_detections`. Custom plugins use the table name you register in `JepPythonBridge`.

---

## Detection Viewer

The viewer (`web/viewer/`) provides a side-by-side comparison of the source and processed stream, plus a timeline of detections. Use it to verify your plugin’s output before integrating the REST API into your app.

---

## Getting Started

1. Install **Ant Media Server** and **Maven**.
2. Clone the plugin repo and run `./redeploy.sh`.
3. Set up Python (OpenCV, Ultralytics, JEP) using `install_dev_dependencies.sh`.
4. Publish a stream and inspect the example plugins’ output in the viewer.
5. Implement your plugin based on `sample_plugin.py`, `yolo_detection_plugin.py`, or `yolo_pose_plugin.py`, register it, and redeploy.

---

## Conclusion

The Python AI Plugin gives you a straightforward way to run custom AI models on live video without building a separate streaming or processing pipeline. Implement a Python class, extend it from `PluginBase`, and register it—the plugin handles frame delivery, HLS output, and optional REST API persistence. Use the bundled examples (face detection, YOLO object detection, pose tracking) as templates, then adapt them for your own models and use cases. For setup details, see the [README](README.md). For more on Ant Media Server plugins, see [Plugins Will Make Ant Media Server More Powerful](https://antmedia.io/plugins-will-make-ant-media-server-more-powerful/).
