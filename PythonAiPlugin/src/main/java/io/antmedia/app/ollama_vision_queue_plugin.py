"""
Queued vision jobs against local Ollama (same HTTP surface as ollama-vision-mcp-style tools).

User prompts are enqueued per stream. Each job is processed with the current encoded JPEG only
(one image per Ollama /api/chat call).

Modes (aligned with ollama-vision-mcp tool names):
- analyze_image: custom analysis (user_prompt should describe what to look for)
- describe_image: detailed scene description
- identify_objects: list identifiable objects
- read_text: OCR / visible text
- custom: use user_prompt as the full instruction

Results are sent via java_callback (SQLite table ollama_vision_queue_results).

Continuous **monitor**: multiple yes/no prompts per stream; positive matches go to table monitor_alerts
via monitor_callback. Set OLLAMA_VISION_MONITOR_MATCH_DIR to write the matched frame JPEG only on yes.

Set OLLAMA_VISION_MONITOR_DEBUG=1 to log each monitor tick: Ollama errors, det/conf vs threshold, parse failures,
and when a repeat yes is suppressed.

Ollama URL: OLLAMA_VISION_OLLAMA_URL (or LOCAL_VLM_BASE_URL), default http://127.0.0.1:11434 . If Ollama is in Docker
but AMS is not (or the reverse), see connection error messages — localhost inside a container is not the host.
"""

import base64
import errno
import json
import os
import queue
import threading
import time
import urllib.error
import urllib.request
from collections import deque

import cv2

from frame_dump import dump_jpeg_b64_if_enabled, save_monitor_match_if_enabled
from plugin_base import PluginBase
from WebHookNotifier import notify_webhook_plugin_result_json


def _env_float(name, default):
    try:
        return float(os.environ.get(name, str(default)))
    except ValueError:
        return float(default)


def _env_int(name, default):
    try:
        return int(os.environ.get(name, str(default)))
    except ValueError:
        return int(default)


def _env_bool(name, default=True):
    v = os.environ.get(name, "")
    if not v.strip():
        return default
    return v.strip().lower() not in ("0", "false", "no", "off")


def _ollama_connection_error_message(base_url, exc):
    if not isinstance(exc, urllib.error.URLError):
        return str(exc)
    r = exc.reason
    if isinstance(r, OSError):
        ec = getattr(r, "errno", None)
        if ec in (errno.ECONNREFUSED, getattr(errno, "WSAECONNREFUSED", -1)):
            return (
                "Connection refused to {} — nothing is listening there from this process. "
            ).format(base_url)
        if ec in (errno.ETIMEDOUT, errno.EHOSTUNREACH, errno.ENETUNREACH):
            return "Network error reaching {}: {} (errno {})".format(base_url, r, ec)
    return "Cannot reach Ollama at {}: {}".format(base_url, r)


def _format_ollama_http_error(http_error, model_name):
    err_body = http_error.read().decode("utf-8", errors="replace")[:800]
    msg = "HTTP {}: {}".format(http_error.code, err_body)
    if http_error.code in (400, 404) and model_name and "not found" in err_body.lower():
        msg += (
            " Hint: on the Ollama host run: ollama pull {} "
            "(or set OLLAMA_VISION_DEFAULT_MODEL to a name from `ollama list`)."
        ).format(model_name)
    return msg


_MODE_PRESETS = {
    "describe_image": (
        "Describe this image in detail: setting, people, actions, objects, lighting, "
        "and anything safety-relevant. Be specific and concise."
    ),
    "identify_objects": (
        "List all clearly identifiable objects in the image. "
        "Use a bullet list; group small background clutter as one line if needed."
    ),
    "read_text": (
        "Extract visible printed or handwritten text only. If there is no text, reply exactly: No text. "
        "Keep the answer short; do not describe the scene or objects."
    ),
}

_VALID_MODES = frozenset(
    list(_MODE_PRESETS.keys()) + ["analyze_image", "custom"]
)


class OllamaVisionQueuePlugin(PluginBase):
    def __init__(self):
        super().__init__()
        self.base_url = os.environ.get(
            "OLLAMA_VISION_OLLAMA_URL",
            os.environ.get("LOCAL_VLM_BASE_URL", "http://127.0.0.1:11434"),
        ).rstrip("/")
        self.model = os.environ.get(
            "OLLAMA_VISION_DEFAULT_MODEL",
            os.environ.get("LOCAL_VLM_MODEL", "llava:13b"),
        )
        self.max_side = _env_int("OLLAMA_VISION_QUEUE_MAX_SIDE", 512)
        self.jpeg_quality = _env_int("OLLAMA_VISION_QUEUE_JPEG_QUALITY", 80)
        self.http_timeout_sec = _env_int("OLLAMA_VISION_TIMEOUT", 120)
        self.min_interval_sec = _env_float("OLLAMA_VISION_QUEUE_INTERVAL_SEC", 1.0)
        self.num_predict = _env_int("OLLAMA_VISION_NUM_PREDICT", 256)
        self.num_predict_read_text = _env_int("OLLAMA_VISION_NUM_PREDICT_READ_TEXT", 128)
        self._lock = threading.Lock()
        self._queues = {}
        self._inflight = set()
        self._completed = queue.Queue()
        self._work_queue = queue.Queue()
        self.latest_frame_b64_by_stream = {}
        self.last_run_ts = {}
        self.monitor_callback = None
        self._monitor_prompts = {}
        self._monitor_config = {}
        self._last_monitor_ts = {}
        self._monitor_inflight = set()
        self._monitor_queue = queue.Queue()
        self.monitor_num_predict = _env_int("OLLAMA_VISION_MONITOR_NUM_PREDICT", 96)
        self.monitor_temperature = _env_float("OLLAMA_VISION_MONITOR_TEMPERATURE", 0.1)
        self.monitor_suppress_repeat_alerts = _env_bool(
            "OLLAMA_VISION_MONITOR_SUPPRESS_REPEAT_ALERTS", False
        )
        self.monitor_debug = _env_bool("OLLAMA_VISION_MONITOR_DEBUG", False)
        self._monitor_was_yes = {}
        self._start_ollama_worker()
        self._start_monitor_worker()

    def set_monitor_callback(self, cb):
        self.monitor_callback = cb

    def set_monitor_prompts(self, stream_id, prompts_json, threshold, interval_sec):
        try:
            arr = json.loads(prompts_json)
        except Exception:
            return False
        prompts = []
        for p in arr:
            s = str(p).strip()
            if s:
                prompts.append(s)
        prompts = prompts[:20]
        if not prompts:
            return False
        with self._lock:
            self._monitor_prompts[stream_id] = prompts
            self._monitor_config[stream_id] = {
                "threshold": float(threshold),
                "interval_sec": max(0.5, float(interval_sec)),
            }
            self._monitor_was_yes.pop(stream_id, None)
        print(
            "OllamaVisionQueuePlugin: monitor {} prompt(s) for stream {}".format(
                len(prompts), stream_id
            )
        )
        return True

    def clear_monitor_prompts(self, stream_id):
        with self._lock:
            self._monitor_prompts.pop(stream_id, None)
            self._monitor_config.pop(stream_id, None)
            self._monitor_was_yes.pop(stream_id, None)
        self._last_monitor_ts.pop(stream_id, None)
        self._monitor_inflight.discard(stream_id)
        print("OllamaVisionQueuePlugin: cleared monitor for {}".format(stream_id))
        return True

    def on_stream_started(self, stream_id, width, height):
        self.last_run_ts[stream_id] = 0.0
        self._last_monitor_ts[stream_id] = 0.0
        self.latest_frame_b64_by_stream.pop(stream_id, None)
        with self._lock:
            self._queues.pop(stream_id, None)
        print(
            "OllamaVisionQueuePlugin: stream {} started {}x{} model={} max_side={}".format(
                stream_id, width, height, self.model, self.max_side
            )
        )

    def on_stream_finished(self, stream_id):
        self.latest_frame_b64_by_stream.pop(stream_id, None)
        self.last_run_ts.pop(stream_id, None)
        self._last_monitor_ts.pop(stream_id, None)
        with self._lock:
            self._queues.pop(stream_id, None)
            self._monitor_prompts.pop(stream_id, None)
            self._monitor_config.pop(stream_id, None)
            self._monitor_was_yes.pop(stream_id, None)
            self._inflight.discard(stream_id)
            self._monitor_inflight.discard(stream_id)
        print("OllamaVisionQueuePlugin: stream {} finished".format(stream_id))

    def _start_monitor_worker(self):
        def loop():
            while True:
                item = self._monitor_queue.get()
                if item is None:
                    return
                (
                    stream_id,
                    app_name,
                    prompts,
                    images,
                    timestamp_sec,
                    threshold,
                ) = item
                if self.monitor_debug:
                    print(
                        "OllamaVisionQueuePlugin: monitor tick stream={} prompts={} threshold={}".format(
                            stream_id, len(prompts), threshold
                        )
                    )

                try:
                    for prompt in prompts:
                        text, err = self._call_ollama_yes_no(prompt, images)
                        if err:
                            if self.monitor_debug:
                                print(
                                    "OllamaVisionQueuePlugin: monitor Ollama error: {}".format(
                                        err
                                    )
                                )
                            continue
                        parsed = self._parse_detection_json(text)
                        if not parsed:
                            if self.monitor_debug:
                                print(
                                    "OllamaVisionQueuePlugin: monitor unparseable model response: {!r}".format(
                                        (text or "")[:200]
                                    )
                                )
                            continue
                        det = parsed.get("detected")
                        if isinstance(det, str):
                            det = det.strip().lower() in ("true", "yes", "1")
                        else:
                            det = bool(det)
                        try:
                            conf = float(parsed.get("confidence") or 0.0)
                        except (TypeError, ValueError):
                            conf = 0.0
                        if self.monitor_debug:
                            print(
                                "OllamaVisionQueuePlugin: monitor det={} conf={} need_conf>={} match={}".format(
                                    det,
                                    conf,
                                    threshold,
                                    bool(det and conf >= threshold),
                                )
                            )
                        if det and conf >= threshold:
                            with self._lock:
                                prev_map = self._monitor_was_yes.get(stream_id, {})
                                was_yes = prev_map.get(prompt, False)
                            if self.monitor_suppress_repeat_alerts and was_yes:
                                if self.monitor_debug:
                                    print(
                                        "OllamaVisionQueuePlugin: monitor skip duplicate yes "
                                        "(OLLAMA_VISION_MONITOR_SUPPRESS_REPEAT_ALERTS)"
                                    )
                                continue
                            cur_b64 = images[0] if images else None

                            self._emit_monitor(
                                stream_id,
                                app_name,
                                prompt,
                                conf,
                                str(parsed.get("reason") or ""),
                                timestamp_sec,
                                cur_b64,
                            )
                            with self._lock:
                                self._monitor_was_yes.setdefault(stream_id, {})[
                                    prompt
                                ] = True
                        else:
                            with self._lock:
                                self._monitor_was_yes.setdefault(stream_id, {})[
                                    prompt
                                ] = False
                finally:
                    with self._lock:
                        self._monitor_inflight.discard(stream_id)

        t = threading.Thread(target=loop, daemon=True, name="ollama-vision-monitor")
        t.start()

    def _start_ollama_worker(self):
        def loop():
            while True:
                item = self._work_queue.get()
                if item is None:
                    return
                (
                    app_name,
                    stream_id,
                    job,
                    instruction,
                    images,
                    timestamp_sec,
                ) = item
                t0 = time.time()
                text, call_err = None, None
                try:
                    text, call_err = self._call_ollama(instruction, images, job)
                except Exception as e:
                    text, call_err = None, str(e) or repr(e)
                finally:
                    latency_ms = int((time.time() - t0) * 1000)
                    with self._lock:
                        self._inflight.discard(stream_id)
                    self._completed.put(
                        (
                            app_name,
                            stream_id,
                            job,
                            text,
                            call_err,
                            latency_ms,
                            timestamp_sec,
                        )
                    )

        t = threading.Thread(target=loop, daemon=True, name="ollama-vision-queue")
        t.start()

    def _drain_completed(self):
        while True:
            try:
                (
                    app_name,
                    stream_id,
                    job,
                    text,
                    call_err,
                    latency_ms,
                    timestamp_sec,
                ) = self._completed.get_nowait()
            except queue.Empty:
                break
            self._emit(
                app_name,
                stream_id,
                job,
                text,
                call_err,
                latency_ms,
                timestamp_sec,
            )

    def enqueue_job(self, stream_id, mode, user_prompt):
        if not stream_id or not str(stream_id).strip():
            return False
        mode = (mode or "custom").strip().lower()
        if mode not in _VALID_MODES:
            return False
        user_prompt = (user_prompt or "").strip()
        if mode == "analyze_image" and not user_prompt:
            return False
        job = {
            "mode": mode,
            "prompt": user_prompt,
            "enqueued_ms": int(time.time() * 1000),
        }
        with self._lock:
            q = self._queues.setdefault(stream_id, deque(maxlen=100))
            q.append(job)
        print(
            "OllamaVisionQueuePlugin: enqueued mode={} for stream={} (queue_len={})".format(
                mode, stream_id, len(q)
            )
        )
        return True

    def get_latest_frame_b64(self, stream_id):
        return self.latest_frame_b64_by_stream.get(stream_id)

    def _encode_jpeg_b64(self, frame):
        h, w = frame.shape[:2]
        scale = min(1.0, float(self.max_side) / max(h, w, 1))
        if scale < 1.0:
            nw = max(1, int(w * scale))
            nh = max(1, int(h * scale))
            small = cv2.resize(frame, (nw, nh), interpolation=cv2.INTER_AREA)
        else:
            small = frame
        ok, buf = cv2.imencode(
            ".jpg", small, [int(cv2.IMWRITE_JPEG_QUALITY), self.jpeg_quality]
        )
        if not ok:
            return None
        return base64.b64encode(buf.tobytes()).decode("ascii")

    def on_video_frame(self, stream_id, app_name, frame, timestamp_ms, stream_feeder):
        self._drain_completed()

        now = time.time()
        do_encode = now - self.last_run_ts.get(stream_id, 0.0) >= self.min_interval_sec

        if do_encode:
            self.last_run_ts[stream_id] = now
            b64 = self._encode_jpeg_b64(frame)
            if not b64:
                return
            dump_jpeg_b64_if_enabled(stream_id, "queue", b64)
            self.latest_frame_b64_by_stream[stream_id] = b64
        else:
            b64 = self.latest_frame_b64_by_stream.get(stream_id)
            if not b64:
                return

        self._maybe_schedule_monitor(stream_id, app_name, b64, timestamp_ms, now)

        job = None
        # Do not block queue jobs while monitor runs; monitor uses _monitor_inflight only.
        # Blocking here caused REST-enqueued jobs to never run when monitor was active.
        with self._lock:
            if stream_id in self._inflight:
                job = None
            else:
                q = self._queues.get(stream_id)
                if q:
                    job = q.popleft()

        if job is None:
            return

        instruction, err = self._build_instruction(job)
        if err:
            self._emit(
                app_name,
                stream_id,
                job,
                None,
                err,
                0,
                timestamp_ms / 1000.0,
            )
            return

        images = [b64]

        with self._lock:
            self._inflight.add(stream_id)
        try:
            self._work_queue.put(
                (
                    app_name,
                    stream_id,
                    job,
                    instruction,
                    images,
                    timestamp_ms / 1000.0,
                )
            )
        except Exception:
            with self._lock:
                self._inflight.discard(stream_id)
            raise

    def _maybe_schedule_monitor(self, stream_id, app_name, b64, timestamp_ms, now):
        cfg = self._monitor_config.get(stream_id)
        prompts = self._monitor_prompts.get(stream_id) or []
        if not cfg or not prompts:
            return
        if stream_id in self._inflight or stream_id in self._monitor_inflight:
            return
        if now - self._last_monitor_ts.get(stream_id, 0.0) < cfg["interval_sec"]:
            return
        self._last_monitor_ts[stream_id] = now
        images = [b64]
        with self._lock:
            self._monitor_inflight.add(stream_id)
        try:
            self._monitor_queue.put(
                (
                    stream_id,
                    app_name,
                    list(prompts),
                    images,
                    timestamp_ms / 1000.0,
                    cfg["threshold"],
                )
            )
        except Exception:
            with self._lock:
                self._monitor_inflight.discard(stream_id)
            raise

    def _build_instruction(self, job):
        mode = job["mode"]
        user = (job.get("prompt") or "").strip()
        if mode == "custom":
            if not user:
                return None, "custom mode requires a non-empty prompt"
            base = user
        elif mode == "analyze_image":
            if not user:
                return None, "analyze_image requires a non-empty prompt"
            base = user
        else:
            base = _MODE_PRESETS.get(mode, "")
            if user:
                base += "\n\nAdditional instructions: " + user

        base += "\n\nOne image is provided (current video frame)."
        return base, None

    def _call_ollama(self, instruction, images_b64_list, job=None):
        np = self.num_predict
        if job is not None and (job.get("mode") or "") == "read_text":
            np = self.num_predict_read_text
        url = self.base_url + "/api/chat"
        body = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": instruction,
                    "images": images_b64_list,
                }
            ],
            "stream": False,
            "options": {"num_predict": np},
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.http_timeout_sec) as resp:
                raw = resp.read().decode("utf-8")
            data = json.loads(raw)
            message = data.get("message") or {}
            content = (message.get("content") or "").strip()
            if not content:
                return None, "empty response from Ollama"
            return content, None
        except urllib.error.HTTPError as e:
            return None, _format_ollama_http_error(e, self.model)
        except urllib.error.URLError as e:
            return None, _ollama_connection_error_message(self.base_url, e)
        except Exception as e:
            return None, str(e)

    _MONITOR_SYSTEM = (
        "You verify a single live camera frame. Be strict: wrong 'yes' is worse than 'no'.\n"
        "\n"
            "Confidence: use low values when unsure. Output ONLY one JSON object, no markdown.\n"
    )

    def _call_ollama_yes_no(self, user_prompt, images_b64_list):
        user_block = (
            "Answer for the main subject described above.\n"
            "Question: "
            + json.dumps(user_prompt)
            + '\n\nReturn ONLY: {"detected": boolean, "confidence": number from 0 to 1, '
            '"reason": "one short sentence"}'
        )
        url = self.base_url + "/api/chat"
        body = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": self._MONITOR_SYSTEM},
                {
                    "role": "user",
                    "content": user_block,
                    "images": images_b64_list,
                },
            ],
            "stream": False,
            "options": {
                "num_predict": self.monitor_num_predict,
                "temperature": self.monitor_temperature,
            },
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.http_timeout_sec) as resp:
                raw = resp.read().decode("utf-8")
            data = json.loads(raw)
            message = data.get("message") or {}
            content = (message.get("content") or "").strip()
            if not content:
                return None, "empty response from Ollama"
            return content, None
        except urllib.error.HTTPError as e:
            return None, _format_ollama_http_error(e, self.model)
        except urllib.error.URLError as e:
            return None, _ollama_connection_error_message(self.base_url, e)
        except Exception as e:
            return None, str(e)

    def _parse_detection_json(self, content):
        if not content:
            return None
        text = content.strip()
        if text.startswith("```"):
            first = text.find("{")
            last = text.rfind("}")
            if first >= 0 and last > first:
                text = text[first : last + 1]
        try:
            return json.loads(text)
        except Exception:
            return None

    def _emit_monitor(
        self,
        stream_id,
        app_name,
        prompt,
        confidence,
        reason,
        timestamp_sec,
        jpeg_b64_current=None,
    ):
        saved_path = save_monitor_match_if_enabled(
            stream_id, prompt, jpeg_b64_current
        )
        payload = {
            "app_name": app_name or "",
            "stream_id": stream_id,
            "kind": "monitor_match",
            "prompt": prompt,
            "detected": True,
            "confidence": confidence,
            "reason": reason,
            "timestamp_sec": round(timestamp_sec, 2),
            "model": self.model,
        }
        if saved_path:
            payload["saved_image_path"] = saved_path
        if self.monitor_callback is not None:
            try:
                self.monitor_callback.onResult(app_name or "", stream_id, json.dumps(payload))
            except Exception as e:
                print("OllamaVisionQueuePlugin: monitor onResult error: {}".format(e))

        notify_webhook_plugin_result_json(payload)

    def _emit(
        self,
        app_name,
        stream_id,
        job,
        result_text,
        error_text,
        latency_ms,
        timestamp_sec,
    ):
        payload = {
            "app_name": app_name or "",
            "stream_id": stream_id,
            "mode": job["mode"],
            "user_prompt": job.get("prompt") or "",
            "result": result_text,
            "error": error_text,
            "enqueued_ms": job.get("enqueued_ms"),
            "timestamp_sec": round(timestamp_sec, 2),
            "model": self.model,
            "latency_ms": latency_ms,
        }
        if self.java_callback is not None:
            try:
                self.java_callback.onResult(app_name or "", stream_id, json.dumps(payload))
            except Exception as e:
                print("OllamaVisionQueuePlugin: onResult error: {}".format(e))
        notify_webhook_plugin_result_json(payload)