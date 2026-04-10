import json
import os
from stream_reader import StreamReader

mode = "debug"
registered_plugins = []
active_readers = {}
java_callbacks = {}
AMS_DIR = os.getenv("AMS_DIR", "/usr/local/antmedia/")
ANTMEDIA_WEBAPPS_DIR = os.path.join(AMS_DIR, "webapps")


def set_java_callback(name, callback):
    java_callbacks[name] = callback
    print("Java callback '{}' registered".format(name))


def register_plugin(plugin):
    registered_plugins.append(plugin)
    print("Registered plugin: {}".format(type(plugin).__name__))


def init_python_plugin_state():
    from samples.init_plugins import init_plugins
    init_plugins(register_plugin, java_callbacks)
    print("Python plugin initialized with {} plugin(s)".format(len(registered_plugins)))


def streamStarted(streamid, app_name, width, height, hls_url):
    for plugin in registered_plugins:
        try:
            plugin.on_stream_started(streamid, width, height)
        except Exception as e:
            print("Error in {}.on_stream_started: {}".format(type(plugin).__name__, e))

    streams_dir = os.path.join(ANTMEDIA_WEBAPPS_DIR, app_name, "streams")
    reader = StreamReader(
        streamid,
        hls_url,
        app_name,
        registered_plugins,
        width=width,
        height=height,
        streams_dir=streams_dir,
    )
    active_readers[streamid] = reader
    reader.start()


def streamFinished(streamid):
    reader = active_readers.pop(streamid, None)
    if reader is not None:
        reader.stop()

    for plugin in registered_plugins:
        try:
            plugin.on_stream_finished(streamid)
        except Exception as e:
            print("Error in {}.on_stream_finished: {}".format(type(plugin).__name__, e))


def set_monitor_callback(cb):
    for p in registered_plugins:
        fn = getattr(p, "set_monitor_callback", None)
        if fn is not None:
            fn(cb)
            print("Monitor callback attached to {}".format(type(p).__name__))
            return


def set_vision_monitor_config(stream_id, prompts_json, threshold, interval_sec):
    """prompts_json: JSON array of strings. Returns True if applied."""
    for p in registered_plugins:
        fn = getattr(p, "set_monitor_prompts", None)
        if fn is not None:
            return fn(stream_id, prompts_json, threshold, interval_sec)
    return False


def clear_vision_monitor(stream_id):
    for p in registered_plugins:
        fn = getattr(p, "clear_monitor_prompts", None)
        if fn is not None:
            fn(stream_id)
            return True
    return False


def enqueue_ollama_vision_job(stream_id, mode, user_prompt):
    """Enqueue a vision job for OllamaVisionQueuePlugin. Returns True if accepted."""
    for plugin in registered_plugins:
        enqueue = getattr(plugin, "enqueue_job", None)
        if enqueue is None:
            continue
        try:
            if enqueue(stream_id, mode, user_prompt or ""):
                return True
        except Exception as e:
            print("Error in {}.enqueue_job: {}".format(type(plugin).__name__, e))
    return False


def get_latest_frame_b64(streamid):
    for plugin in registered_plugins:
        try:
            getter = getattr(plugin, "get_latest_frame_b64", None)
            if getter is None:
                continue
            frame_b64 = getter(streamid)
            if frame_b64:
                return frame_b64
        except Exception as e:
            print("Error in {}.get_latest_frame_b64: {}".format(type(plugin).__name__, e))
    return None
