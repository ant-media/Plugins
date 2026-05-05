"""
Optional JPEG dump to disk for debugging vision inputs.

Set env VISION_FRAME_DUMP_DIR to an absolute path; each encoded frame sent toward
Ollama is written as streamId_timestamp_label.jpg

Monitor matches only: set OLLAMA_VISION_MONITOR_MATCH_DIR to save the **current** frame
JPEG when a monitor prompt returns yes (not every monitor tick). Filenames include a slug
derived from the prompt plus stream id and timestamp.
"""

import base64
import hashlib
import os
import re
import time


def _prompt_filename_slug(prompt, max_len=72):
    """ASCII-ish slug from prompt for use in filenames; safe on common filesystems."""
    s = str(prompt or "").strip().lower()
    s = re.sub(r"[\s/\\:*?\"<>|]+", "_", s)
    s = "".join(c if c.isalnum() or c in "-_" else "_" for c in s)
    s = re.sub(r"_+", "_", s).strip("_")
    if not s:
        return "prompt_" + hashlib.sha256(str(prompt).encode("utf-8")).hexdigest()[:10]
    if len(s) > max_len:
        h = hashlib.sha256(str(prompt).encode("utf-8")).hexdigest()[:8]
        s = s[: max_len - 9] + "_" + h
    return s


def dump_jpeg_if_enabled(stream_id, label, jpeg_bytes):
    if not jpeg_bytes:
        return
    root = os.environ.get("VISION_FRAME_DUMP_DIR", "").strip()
    if not root:
        return
    try:
        os.makedirs(root, exist_ok=True)
        safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in str(stream_id))[:80]
        lab = str(label).replace(" ", "_").replace("/", "_")[:40]
        name = "{}_{}_{}.jpg".format(safe, int(time.time() * 1000), lab)
        path = os.path.join(root, name)
        with open(path, "wb") as f:
            f.write(jpeg_bytes)
    except Exception as e:
        print("frame_dump: {}".format(e))


def dump_jpeg_b64_if_enabled(stream_id, label, b64_ascii):
    if not b64_ascii:
        return
    try:
        dump_jpeg_if_enabled(stream_id, label, base64.b64decode(b64_ascii))
    except Exception as e:
        print("frame_dump b64: {}".format(e))


def save_monitor_match_if_enabled(stream_id, prompt, jpeg_b64_ascii):
    """
    Write the matched monitor frame (current JPEG) when OLLAMA_VISION_MONITOR_MATCH_DIR is set.
    Returns absolute path written, or None.
    """
    root = os.environ.get("OLLAMA_VISION_MONITOR_MATCH_DIR", "").strip()
    if not root or not jpeg_b64_ascii:
        return None
    try:
        os.makedirs(root, exist_ok=True)
        safe = "".join(
            c if c.isalnum() or c in "-_" else "_" for c in str(stream_id)
        )[:80]
        slug = _prompt_filename_slug(prompt)
        name = "{}_{}_{}.jpg".format(safe, slug, int(time.time() * 1000))
        path = os.path.join(root, name)
        raw = base64.b64decode(jpeg_b64_ascii)
        with open(path, "wb") as f:
            f.write(raw)
        return path
    except Exception as e:
        print("frame_dump monitor_match: {}".format(e))
        return None
