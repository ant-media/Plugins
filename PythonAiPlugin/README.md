# PythonPlugin
This is a Python plugin project for Ant Media Server. You can use this project to interface with python program to modify Audio/Video data, apply AI
With this plugin you can find:
- Accessing the Ant Media Server ie. AntMediaApplicationAdaptor class
- Registration of the plugin as the PacketListener and/or FrameListener 
- Consuming packets and/or frames
- REST interface implementation

# Prerequests
- Install Ant Media Server
- Install Maven 

# Quick Start

- Clone the repository and go the Sample Plugin Directory
  ```sh
  git clone https://github.com/ant-media/Plugins.git
  cd Plugins/pythonAIPlugin/
  ```
- Build the Sample Plugin
  ```sh
  sudo ./redeploy.sh
  ```
- Publish/unPublish a Live Stream to Ant Media Server with WebRTC/RTMP/RTSP

For more information about the plugins, [visit this post](https://antmedia.io/plugins-will-make-ant-media-server-more-powerful/)

## Ollama (Docker)
optionally if you want to try out the olama vision model you can install olama with docker 
Run Ollama for the vision queue / monitor (see `docker-compose.yml` in this folder), or:

```sh
docker run -d --name ollama-local -p 127.0.0.1:11434:11434 \
  -v pythonaiplugin_ollama_data:/root/.ollama --restart unless-stopped ollama/ollama:latest
docker exec -it ollama-local ollama pull llava:13b
```

Optional: `VISION_FRAME_DUMP_DIR` — if set, JPEGs sent to Ollama from the vision queue can be dumped for debugging (`queue` / `queue_prev` labels). Can fill disk if left enabled.

**Sample:** `web/samples/publish_webrtc_vlm.html` — WebRTC publish (copy to `webapps/LiveApp/samples/`).

## Ollama vision queue (ollama-vision-mcp style, in-process)

The separate project [ollama-vision-mcp](https://github.com/xkiranj/ollama-vision-mcp) is an MCP server that calls **local Ollama** with image tools. This plugin embeds the same *idea* inside Ant Media: a Python plugin [`ollama_vision_queue_plugin.py`](src/main/java/io/antmedia/app/ollama_vision_queue_plugin.py) queues user jobs per stream, and on each sampled frame sends JPEG(s) (base64) to Ollama `/api/chat`. Results are written to SQLite table **`ollama_vision_queue_results`** (via `ResultCallback`).

**Why not run the MCP binary inside the plugin?** The video path runs under JEP in the AMS process; spawning an MCP stdio client per frame is impractical. Behavior matches those tools: modes `analyze_image`, `describe_image`, `identify_objects`, `read_text`, and `custom`.

**REST**

- `POST .../rest/python-plugin/vision-queue/enqueue` — body `{ "streamId", "mode", "prompt" }`.  
  `analyze_image` and `custom` require a non-empty `prompt`; other modes may use `prompt` as extra instructions.
- `GET .../rest/python-plugin/detections/ollama_vision_queue_results/{streamId}/seconds/{seconds}` — persisted JSON rows.

**Env (optional)**

- `OLLAMA_VISION_OLLAMA_URL` — default: `LOCAL_VLM_BASE_URL` or `http://127.0.0.1:11434`
- `OLLAMA_VISION_DEFAULT_MODEL` — default: `LOCAL_VLM_MODEL` or `llava:13b`
- `OLLAMA_VISION_TIMEOUT` — HTTP timeout seconds (default `120`)
- `OLLAMA_VISION_QUEUE_INTERVAL_SEC` — min seconds between **new JPEG encodes** (default `1`). While waiting, the plugin can still dequeue the next job using the **last cached** frame so backlog clears without waiting for another encode.
- `OLLAMA_VISION_NUM_PREDICT` — max tokens for most modes (default `256`; was higher for slower runs).
- `OLLAMA_VISION_NUM_PREDICT_READ_TEXT` — max tokens for `read_text` jobs (default `128`).
- `OLLAMA_VISION_QUEUE_MAX_SIDE` — default `512` (smaller = faster vision; raise to `768` if quality suffers).
- `OLLAMA_VISION_QUEUE_JPEG_QUALITY` — default `80` for API calls.

Inference runs on a **background thread** so the video worker is not blocked for the full Ollama HTTP duration. Jobs are still processed **one at a time** per stream; `queue_len=3` means roughly **three times** the average model latency before the last result appears—enqueue one job at a time for interactive use, or use a smaller/faster vision model on CPU.

**UI sample:** copy [`web/samples/ollama_vision_queue_ui.html`](web/samples/ollama_vision_queue_ui.html) to `webapps/LiveApp/samples/` and open from the browser.

Continuous **monitor** prompts (yes/no per line, DB persistence) are configured via `POST /rest/python-plugin/vision-monitor/config` — see comments in `PythonPluginRestService` and `ollama_vision_queue_plugin.py`.
