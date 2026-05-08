"""Face detection sample — registered by samples/init_plugins.py via init_plugin()."""

from sample_plugin import FaceDetectionPlugin


def init_plugin(register_plugin, callbacks):
    face_plugin = FaceDetectionPlugin()
    face_callback = callbacks.get("face_detections") or callbacks.get("default")
    face_plugin.set_java_callback(face_callback)
    register_plugin(face_plugin)
