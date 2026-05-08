"""YOLO detection + pose samples — registered by samples/init_plugins.py via init_plugin()."""

from yolo.yolo_detection_plugin import YoloGeneralDetectionPlugin
from yolo_pose_plugin import YoloPoseDetectionPlugin


def init_plugin(register_plugin, callbacks):
    yolo_general_plugin = YoloGeneralDetectionPlugin()
    yolo_general_callback = callbacks.get("yolo_general_detections") or callbacks.get("default")
    yolo_general_plugin.set_java_callback(yolo_general_callback)
    register_plugin(yolo_general_plugin)

    yolo_pose_plugin = YoloPoseDetectionPlugin()
    yolo_pose_callback = callbacks.get("pose_detections") or callbacks.get("default")
    yolo_pose_plugin.set_java_callback(yolo_pose_callback)
    register_plugin(yolo_pose_plugin)
