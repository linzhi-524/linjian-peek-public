#!/usr/bin/env python3
"""掌心窗 v0.3.4.1 unified server.

零依赖标准库版，负责：
1. 给手机端下发 peek / open_app / back / home / recents / tap / swipe / set_alarm / send_notification 命令；
2. 接收手机端上传的截图；
3. 保存手机端最近状态；
4. 提供 /api/latest 与 /api/latest.json 给 MCP 读取。
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock
from urllib.parse import parse_qs, urlparse

DEFAULT_PORT = 8513
DEFAULT_KEEP = 3
MAX_UPLOAD_BYTES = 24 * 1024 * 1024
VERSION = "0.3.4.1-public"
DEFAULT_DEVICE = os.environ.get("LINJIAN_DEFAULT_DEVICE", "android-phone")

ERR_BAD_TOKEN = "LINJIAN_ERR_BAD_TOKEN"
ERR_NO_IMAGE = "LINJIAN_ERR_NO_IMAGE"
ERR_TOO_LARGE = "LINJIAN_ERR_TOO_LARGE"
ERR_NOT_FOUND = "LINJIAN_ERR_NOT_FOUND"
ERR_BAD_METHOD = "LINJIAN_ERR_BAD_METHOD"

KNOWN_APPS = {
    "小红书": "com.xingin.xhs", "xhs": "com.xingin.xhs", "xiaohongshu": "com.xingin.xhs",
    "微信": "com.tencent.mm", "wechat": "com.tencent.mm",
    "QQ": "com.tencent.mobileqq", "qq": "com.tencent.mobileqq",
    "抖音": "com.ss.android.ugc.aweme", "douyin": "com.ss.android.ugc.aweme",
    "ChatGPT": "com.openai.chatgpt", "chatgpt": "com.openai.chatgpt",
    "Gemini": "com.google.android.apps.bard", "gemini": "com.google.android.apps.bard", "bard": "com.google.android.apps.bard",
    "Claude": "com.anthropic.claude", "claude": "com.anthropic.claude",
    "微博": "com.sina.weibo", "weibo": "com.sina.weibo",
    "X": "com.twitter.android", "x": "com.twitter.android", "Twitter": "com.twitter.android", "twitter": "com.twitter.android",
    "Speedcat": "", "speedcat": "",
}
SENSITIVE_PACKAGES = {"com.eg.android.AlipayGphone", "com.tencent.mm.plugin.wallet"}
ALLOWED_ACTIONS = {"noop", "peek", "open_app", "home", "back", "recents", "tap", "swipe", "set_alarm", "send_notification", "run_sequence", "save_known_app", "get_screen_nodes", "tap_text", "input_text", "lock_app", "unlock_app", "temporary_unlock_app", "extend_lock", "deny_unlock_request", "get_lock_state", "set_emergency_passphrase", "add_locked_app", "remove_locked_app", "list_lockable_apps"}


def load_dotenv(path: Path) -> None:
    if not path.exists(): return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line: continue
        key, _, val = line.partition("=")
        os.environ.setdefault(key.strip(), val.strip().strip('"').strip("'"))



def load_update_info() -> dict:
    here = Path(__file__).resolve().parent
    candidates = [here.parent / "update.json", here / "update.json"]
    for path in candidates:
        if path.exists():
            try:
                return json.loads(path.read_text(encoding="utf-8"))
            except Exception:
                pass
    return {
        "latest_version_name": "0.3.4.1",
        "latest_version_code": 30401,
        "apk_url": "",
        "sha256": "",
        "required": False,
        "changelog": [
            "修复部分 vivo / OriginOS 机型首次打开只显示左上角的问题",
            "优化全屏铺满与启动重测量",
            "新增 设置 - 版本与更新，支持检查最新版和查看更新日志"
        ]
    }

def now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


class State:
    def __init__(self) -> None:
        here = Path(__file__).resolve().parent
        load_dotenv(here / ".env")
        self.token = os.environ.get("LINJIAN_TOKEN", "").strip()
        self.port = int(os.environ.get("PORT", os.environ.get("LINJIAN_PORT", DEFAULT_PORT)))
        self.host = os.environ.get("LINJIAN_HOST", "0.0.0.0")
        self.keep = int(os.environ.get("LINJIAN_KEEP", DEFAULT_KEEP))
        self.hook = os.environ.get("LINJIAN_HOOK", "").strip()
        data_dir = os.environ.get("LINJIAN_DATA_DIR", str(here / "data"))
        self.data_dir = Path(data_dir).resolve()
        self.shots_dir = self.data_dir / "screenshots"
        self.shots_dir.mkdir(parents=True, exist_ok=True)
        self.commands: list[dict] = []
        self.command_history: dict[str, dict] = {}
        self.commands_lock = Lock()
        self.device_states: dict[str, dict] = {}
        self.unlock_requests: list[dict] = []

    def latest_shot(self) -> Path | None:
        shots = sorted(self.shots_dir.glob("peek_*"), key=lambda p: p.stat().st_mtime)
        return shots[-1] if shots else None


def package_for(app_name: str, package: str = "") -> str:
    pkg = (package or "").strip()
    if pkg: return pkg
    key = (app_name or "").strip()
    return KNOWN_APPS.get(key, KNOWN_APPS.get(key.lower(), ""))


def make_command(device_id: str, action: str, app: str = "", package: str = "", payload: dict | None = None) -> dict:
    action = (action or "noop").strip().lower()
    if action not in ALLOWED_ACTIONS: action = "noop"
    package = package_for(app, package)
    payload = dict(payload or {})
    if package in SENSITIVE_PACKAGES:
        action = "noop"
        payload["blocked_reason"] = "sensitive_package"
    cmd = {
        "id": str(uuid.uuid4()),
        "device_id": device_id or DEFAULT_DEVICE,
        "action": action,
        "app": app or "",
        "package": package or "",
        "payload": payload,
        "status": "pending",
        "created_at": now_iso(),
        "dispatched_at": None,
        "completed_at": None,
        "result": "",
    }
    cmd.update(payload)
    return cmd


class Handler(BaseHTTPRequestHandler):
    state: State

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("[linjian-unified] %s - %s\n" % (self.address_string(), fmt % args))

    def _send_bytes(self, code: int, body: bytes, content_type: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code: int, payload: dict) -> None:
        self._send_bytes(code, json.dumps(payload, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

    def _token_ok(self) -> bool:
        qs = parse_qs(urlparse(self.path).query)
        supplied = self.headers.get("X-Auth-Token", "") or qs.get("token", [""])[0]
        return bool(self.state.token) and supplied == self.state.token

    def _require_token(self) -> bool:
        if self._token_ok(): return True
        self._json(403, {"ok": False, "error": ERR_BAD_TOKEN}); return False

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", 0) or 0)
        if length <= 0: return {}
        raw = self.rfile.read(length)
        try: return json.loads(raw.decode("utf-8"))
        except Exception: return {}

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)
        if path in ("/", "/health"):
            self._json(200, {"ok": True, "service": "linjian-unified", "name": "掌心窗", "version": VERSION, "tools": sorted(ALLOWED_ACTIONS)})
            return
        if path in ("/api/update.json", "/update.json"):
            payload = load_update_info()
            payload.setdefault("ok", True)
            self._json(200, payload)
            return
        if path == "/api/poll":
            if not self._require_token(): return
            device_id = qs.get("device_id", [DEFAULT_DEVICE])[0] or DEFAULT_DEVICE
            with self.state.commands_lock:
                idx = next((i for i, c in enumerate(self.state.commands) if c.get("device_id") == device_id and c.get("status") == "pending"), None)
                if idx is None:
                    self._json(200, {"ok": True, "command": None}); return
                cmd = self.state.commands.pop(idx)
                cmd["status"] = "dispatched"; cmd["dispatched_at"] = now_iso()
                self.state.command_history[cmd.get("id", "")] = dict(cmd)
            self._json(200, {"ok": True, "command": cmd})
            return
        if path == "/api/latest.json":
            if not self._require_token(): return
            shot = self.state.latest_shot()
            if not shot: self._json(404, {"ok": False, "error": ERR_NOT_FOUND}); return
            st = shot.stat(); self._json(200, {"ok": True, "filename": shot.name, "size": st.st_size, "mtime": st.st_mtime, "url": "/api/latest"}); return
        if path == "/api/latest":
            if not self._require_token(): return
            shot = self.state.latest_shot()
            if not shot: self._json(404, {"ok": False, "error": ERR_NOT_FOUND}); return
            ctype = "image/png" if shot.suffix.lower() == ".png" else "image/jpeg"
            self._send_bytes(200, shot.read_bytes(), ctype); return
        if path in ("/api/device/state", "/api/life_state"):
            if not self._require_token(): return
            device_id = qs.get("device_id", [DEFAULT_DEVICE])[0] or DEFAULT_DEVICE
            state = self.state.device_states.get(device_id)
            self._json(200, {"ok": True, "device_id": device_id, "state": state, "life_state": state}); return
        if path == "/api/command/status":
            if not self._require_token(): return
            cid = qs.get("id", [""])[0]
            with self.state.commands_lock:
                found = self.state.command_history.get(cid) or next((c for c in self.state.commands if c.get("id") == cid), None)
            self._json(200, {"ok": bool(found), "command": found}); return
        if path == "/api/appgate/unlock_requests":
            if not self._require_token(): return
            self._json(200, {"ok": True, "requests": self.state.unlock_requests[-50:]}); return
        if path == "/api/known_apps":
            self._json(200, {"ok": True, "apps": KNOWN_APPS}); return
        self._json(404, {"ok": False, "error": ERR_BAD_METHOD})

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        if path == "/api/peek":
            if not self._require_token(): return
            self._queue(make_command(DEFAULT_DEVICE, "peek")); self._json(200, {"ok": True, "queued": True}); return
        if path == "/api/command":
            if not self._require_token(): return
            data = self._read_json()
            cmd = make_command(data.get("device_id") or DEFAULT_DEVICE, data.get("action") or "noop", data.get("app") or "", data.get("package") or "", data.get("payload") or data)
            self._queue(cmd)
            with self.state.commands_lock:
                self.state.command_history[cmd.get("id", "")] = dict(cmd)
            self._json(200, {"ok": True, "command": cmd}); return
        if path == "/api/device/state":
            if not self._require_token(): return
            data = self._read_json(); device_id = data.get("device_id") or DEFAULT_DEVICE
            data["updated_at"] = now_iso(); self.state.device_states[device_id] = data
            self._json(200, {"ok": True, "device_id": device_id}); return
        if path == "/api/device/report":
            if not self._require_token(): return
            data = self._read_json()
            cid = data.get("command_id") or data.get("id") or ""
            with self.state.commands_lock:
                cmd = self.state.command_history.get(cid)
                if cmd is not None:
                    cmd["status"] = "completed" if data.get("ok") else "failed"
                    cmd["completed_at"] = now_iso()
                    cmd["result"] = data.get("result", "")
                    cmd["report"] = data
            self._json(200, {"ok": True, "report": data, "command": self.state.command_history.get(cid)}); return
        if path == "/api/appgate/unlock_request":
            if not self._require_token(): return
            data = self._read_json(); data.setdefault("created_at", now_iso())
            self.state.unlock_requests.append(data)
            self.state.unlock_requests = self.state.unlock_requests[-50:]
            self._json(200, {"ok": True, "request": data, "count": len(self.state.unlock_requests)}); return
        if path == "/api/screenshot":
            if not self._require_token(): return
            self._handle_screenshot(); return
        self._json(404, {"ok": False, "error": ERR_BAD_METHOD})

    def _queue(self, cmd: dict) -> None:
        with self.state.commands_lock:
            # 防止短时间狂点堆很多同类命令；控制命令保留顺序，peek 最多保留 3 个。
            if cmd.get("action") == "peek" and sum(1 for c in self.state.commands if c.get("action") == "peek") >= 3:
                return
            self.state.commands.append(cmd)

    def _handle_screenshot(self) -> None:
        length = int(self.headers.get("Content-Length", 0))
        if length <= 0: self._json(400, {"ok": False, "error": ERR_NO_IMAGE}); return
        if length > MAX_UPLOAD_BYTES: self._json(413, {"ok": False, "error": ERR_TOO_LARGE}); return
        data = self.rfile.read(length)
        if len(data) < 100: self._json(400, {"ok": False, "error": ERR_NO_IMAGE}); return
        ext = ".png" if data[:8] == b"\x89PNG\r\n\x1a\n" else ".jpg"
        dest = self.state.shots_dir / f"peek_{int(time.time() * 1000)}{ext}"
        dest.write_bytes(data)
        shots = sorted(self.state.shots_dir.glob("peek_*"), key=lambda p: p.stat().st_mtime)
        old_list = shots[:-self.state.keep] if self.state.keep > 0 else shots[:-1]
        for old in old_list:
            try: old.unlink()
            except OSError: pass
        if self.state.hook:
            try: subprocess.Popen([*self.state.hook.split(), str(dest.resolve())], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except Exception as exc: self.log_message("hook failed: %s", exc)
        self._json(200, {"ok": True, "filename": dest.name, "size": len(data)})


def main() -> None:
    state = State()
    if not state.token or state.token == "please-change-me-to-a-long-random-token":
        sys.stderr.write("拒绝启动：请先设置 LINJIAN_TOKEN 为长随机密钥。\n")
        sys.exit(1)
    Handler.state = state
    httpd = ThreadingHTTPServer((state.host, state.port), Handler)
    print("=" * 56)
    print(f"  掌心窗 unified v{VERSION}")
    print(f"  listening: http://{state.host}:{state.port}")
    print(f"  screenshots: {state.shots_dir}  keep={state.keep}")
    print("=" * 56)
    try: httpd.serve_forever()
    except KeyboardInterrupt: print("\nbye.")

if __name__ == "__main__": main()
