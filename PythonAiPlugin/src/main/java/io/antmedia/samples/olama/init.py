"""Ollama vision queue sample — registered by samples/init_plugins.py via init_plugin()."""

from ollama_vision_queue_plugin import OllamaVisionQueuePlugin


def init_plugin(register_plugin, callbacks):
    ollama_queue_plugin = OllamaVisionQueuePlugin()
    oq_callback = callbacks.get("ollama_vision_queue") or callbacks.get("default")
    ollama_queue_plugin.set_java_callback(oq_callback)
    register_plugin(ollama_queue_plugin)
