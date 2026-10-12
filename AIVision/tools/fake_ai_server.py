#!/usr/bin/env python3
"""Dependency-free fake OpenAI-compatible vision server for local testing."""

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class FakeAIState:
    def __init__(self):
        self.request_count = 0
        self.lock = threading.Lock()

    def next_request_number(self):
        with self.lock:
            self.request_count += 1
            return self.request_count


STATE = FakeAIState()


def extract_prompt(payload):
    messages = payload.get("messages", [])
    if not messages:
        return ""

    content = messages[-1].get("content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(
            item.get("text", "")
            for item in content
            if isinstance(item, dict) and item.get("type") == "text"
        )
    return ""


def is_yes_no_prompt(prompt):
    first_line = prompt.splitlines()[0].strip().lower() if prompt else ""
    question_prefixes = (
        "is ", "are ", "am ", "was ", "were ", "do ", "does ", "did ",
        "can ", "could ", "will ", "would ", "has ", "have ", "had ",
        "should ", "is there", "are there",
    )
    return first_line.endswith("?") and first_line.startswith(question_prefixes)


class FakeAIHandler(BaseHTTPRequestHandler):
    server_version = "AIVisionFakeAI/1.0"

    def do_GET(self):
        if self.path in ("/", "/health"):
            self.send_json(200, {"status": "ok", "requests": STATE.request_count})
            return
        self.send_json(404, {"error": "not found"})

    def do_POST(self):
        if self.path.rstrip("/") != "/v1/chat/completions":
            self.send_json(404, {"error": "not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(content_length) or b"{}")
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(400, {"error": f"invalid JSON: {error}"})
            return

        request_number = STATE.next_request_number()
        prompt = extract_prompt(payload)
        model = payload.get("model", "unknown")
        yes_no = is_yes_no_prompt(prompt)

        print(
            f"\n[{request_number}] Request received | model={model} | yes/no={yes_no}\n"
            f"Prompt: {prompt}\n",
            flush=True,
        )

        if yes_no and request_number % 5 == 0:
            answer = "Yes.\nALERT:{Fake AI returns yes on every fifth request}"
        elif yes_no:
            answer = f"No. Fake AI received request #{request_number}."
        else:
            answer = f"Fake AI received request #{request_number}: {prompt.splitlines()[0] if prompt else '(empty prompt)'}"

        response = {
            "id": f"fake-chatcmpl-{request_number}",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": model,
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": answer},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
        }
        self.send_json(200, response)

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, message_format, *args):
        return


def main():
    parser = argparse.ArgumentParser(description="Run a fake OpenAI-compatible AI Vision endpoint")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=11434)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), FakeAIHandler)
    print(f"Fake AI listening at http://{args.host}:{args.port}/v1", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping fake AI", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
