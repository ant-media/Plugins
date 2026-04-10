import os
import sys

# Layout on server (see copy_files.sh):
#   PythonPluginFiles/python_plugin.py, plugin_base.py, ...  (from app/*.py)
#   PythonPluginFiles/samples/init_plugins.py  (this file)
#   PythonPluginFiles/samples/yolo/yolo_detection_plugin.py, yolo_pose_plugin.py
#   PythonPluginFiles/samples/face/sample_plugin.py
#   PythonPluginFiles/samples/olama/ollama_vision_queue_plugin.py

_here = os.path.dirname(os.path.abspath(__file__))
_parent = os.path.dirname(_here)
for _p in (
    _parent,
    os.path.join(_here, "yolo"),
    os.path.join(_here, "face"),
    os.path.join(_here, "olama"),
    _here,
):
    if _p not in sys.path:
        sys.path.insert(0, _p)

from sample_plugin import FaceDetectionPlugin
from yolo.yolo_detection_plugin import YoloGeneralDetectionPlugin
from yolo_pose_plugin import YoloPoseDetectionPlugin
from ollama_vision_queue_plugin import OllamaVisionQueuePlugin


def init_plugins(register_plugin, callbacks):
    face_plugin = FaceDetectionPlugin()
    face_callback = callbacks.get("face_detections") or callbacks.get("default")
    face_plugin.set_java_callback(face_callback)
    register_plugin(face_plugin)

    yolo_general_plugin = YoloGeneralDetectionPlugin()
    yolo_general_callback = callbacks.get("yolo_general_detections") or callbacks.get("default")
    yolo_general_plugin.set_java_callback(yolo_general_callback)
    register_plugin(yolo_general_plugin)

    yolo_pose_plugin = YoloPoseDetectionPlugin()
    yolo_pose_callback = callbacks.get("pose_detections") or callbacks.get("default")
    yolo_pose_plugin.set_java_callback(yolo_pose_callback)
    register_plugin(yolo_pose_plugin)

    ollama_queue_plugin = OllamaVisionQueuePlugin()
    oq_callback = callbacks.get("ollama_vision_queue") or callbacks.get("default")
    ollama_queue_plugin.set_java_callback(oq_callback)
    register_plugin(ollama_queue_plugin)
