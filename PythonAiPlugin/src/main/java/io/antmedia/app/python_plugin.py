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
    from init_plugins import init_plugins
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
