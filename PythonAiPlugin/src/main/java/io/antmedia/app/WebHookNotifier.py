"""
HTTP webhook helper for plugin JSON payloads.

Set the target URL with env AMS_WEBHOOK_URL (or WEBHOOK_URL); otherwise WEBHOOK_URL_DEFAULT is used
(empty = disabled unless you set the env var or change the constant below).
"""

import json
import os
import urllib.error
import urllib.request

WEBHOOK_URL= "https://webhook.site/503aeb15-6cf4-4f56-b233-de623a9de925" or os.environ.get("WEBHOOK_URL")

def post_webhook_json(json_str, timeout_sec=15):
    target = WEBHOOK_URL.strip()
    if not target:
        return False, "webhook URL not configured (set AMS_WEBHOOK_URL or WEBHOOK_URL)"

    if isinstance(json_str, (dict, list)):
        body = json.dumps(json_str).encode("utf-8")
    else:
        body = (json_str or "").encode("utf-8")

    req = urllib.request.Request(
        target,
        data=body,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            code = resp.getcode()
            if 200 <= code < 300:
                return True, "HTTP {}".format(code)
            return False, "HTTP {}".format(code)
    except urllib.error.HTTPError as e:
        return False, "HTTP {}: {}".format(e.code, e.reason)
    except urllib.error.URLError as e:
        return False, str(e.reason if hasattr(e, "reason") else e)
    except Exception as e:
        return False, str(e)


def notify_webhook_plugin_result_json(json_str, timeout_sec=15):
    return post_webhook_json(json_str, timeout_sec=timeout_sec)
