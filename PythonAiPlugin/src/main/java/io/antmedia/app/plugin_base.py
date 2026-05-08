from abc import ABC, abstractmethod


class PluginBase(ABC):
    def __init__(self):
        self.java_callback = None

    def set_java_callback(self, callback):
        self.java_callback = callback

    @abstractmethod
    def on_stream_started(self, stream_id, width, height):
        pass

    @abstractmethod
    def on_stream_finished(self, stream_id):
        pass

    @abstractmethod
    def on_video_frame(self, stream_id, app_name, frame, timestamp_ms, stream_feeder):
        pass
