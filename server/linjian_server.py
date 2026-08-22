#!/usr/bin/env python3
"""掌心窗公开版 v0.3.7 unified server.

零依赖标准库版，负责：
1. 给手机端下发 peek / open_app / back / home / recents / tap / swipe / set_alarm / send_notification 命令；
2. 接收手机端上传的截图；
3. 保存手机端最近状态；
4. 提供 /api/latest 与 /api/latest.json 给 MCP 读取。
"""
from __future__ import annotations

import calendar
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
VERSION = "0.3.7"
DEFAULT_DEVICE = os.environ.get("LINJIAN_DEFAULT_DEVICE", "android-phone")
ACTIVITY_EVENT_LIMIT = 500

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
    "Speedcat": "", "speedcat": "",
}
SENSITIVE_PACKAGES = {"com.eg.android.AlipayGphone", "com.tencent.mm.plugin.wallet"}
ALLOWED_ACTIONS = {"noop", "peek", "open_app", "home", "back", "recents", "screen_off", "turn_screen_off", "lock_screen", "phone_screen_off", "tap", "swipe", "set_alarm", "send_notification", "run_sequence", "save_known_app", "get_screen_nodes", "tap_text", "input_text", "lock_app", "unlock_app", "temporary_unlock_app", "extend_lock", "deny_unlock_request", "get_lock_state", "set_emergency_passphrase", "add_locked_app", "remove_locked_app", "list_lockable_apps", "screen_break_app", "start_screen_break", "screen_break", "end_screen_break", "stop_screen_break", "temporary_screen_break_release", "temporary_screen_release", "extend_screen_break", "deny_screen_break_release_request", "deny_break_release_request", "get_screen_break_state", "set_screen_break_passphrase", "add_screen_break_app", "remove_screen_break_app", "list_screen_break_apps", "get_guidian_state", "set_guidian_config", "trigger_guidian", "mark_guidian_returned", "get_calendar_state", "upsert_calendar_event", "add_calendar_event", "delete_calendar_event", "create_diary_book", "list_diary_books", "rename_diary_book", "update_diary_book_cover", "write_diary_entry", "list_diary_entries", "read_diary_entry", "search_diary_entries", "update_diary_entry", "delete_diary_entry", "delete_diary_book"}


def load_dotenv(path: Path) -> None:
    if not path.exists(): return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line: continue
        key, _, val = line.partition("=")
        os.environ.setdefault(key.strip(), val.strip().strip('"').strip("'"))


def now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def parse_iso_seconds(value: str) -> float:
    try: return calendar.timegm(time.strptime(str(value or ""), "%Y-%m-%dT%H:%M:%SZ"))
    except Exception: return 0.0


def activity_type_for_action(action: str) -> str:
    value = str(action or "")
    if value.startswith("get_") or value.startswith("list_") or value in ("peek", "get_screen_nodes"):
        return "status_check"
    if "calendar" in value: return "calendar"
    if "weather" in value: return "weather"
    if "notification" in value: return "notification"
    return "command"


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
        self.companion_path = self.data_dir / "companion_state.json"
        self.companion_lock = Lock()
        self.companion_state = self._load_companion_state()
        self.activity_path = self.data_dir / "activity_events.json"
        self.activity_lock = Lock()
        self.activity_events = self._load_activity_events()

    def _load_activity_events(self) -> list[dict]:
        try:
            if self.activity_path.exists():
                loaded = json.loads(self.activity_path.read_text(encoding="utf-8"))
                if isinstance(loaded, list): return loaded[:ACTIVITY_EVENT_LIMIT]
        except Exception:
            pass
        return []

    def save_activity_events(self) -> None:
        self.activity_path.parent.mkdir(parents=True, exist_ok=True)
        temp = self.activity_path.with_suffix(".tmp")
        temp.write_text(json.dumps(self.activity_events[:ACTIVITY_EVENT_LIMIT], ensure_ascii=False, indent=2), encoding="utf-8")
        temp.replace(self.activity_path)

    def add_activity_event(self, data: dict, dedupe_seconds: int = 0) -> dict:
        with self.activity_lock:
            event_id = clip_text(str(data.get("id") or ""), 100) or str(uuid.uuid4())
            previous = next((e for e in self.activity_events if e.get("id") == event_id), None)
            metadata = data.get("metadata_json", data.get("metadata", {}))
            if isinstance(metadata, str):
                try: metadata = json.loads(metadata)
                except Exception: metadata = {"value": clip_text(metadata, 1000)}
            if not isinstance(metadata, (dict, list)): metadata = {}
            entry = {
                "id": event_id,
                "device_id": clip_text(str(data.get("device_id") or DEFAULT_DEVICE), 80),
                "created_at": clip_text(str(data.get("created_at") or data.get("at") or now_iso()), 40),
                "source": clip_text(str(data.get("source") or "companion"), 24),
                "type": clip_text(str(data.get("type") or "activity"), 40),
                "title": clip_text(str(data.get("title") or ""), 100),
                "subtitle": clip_text(str(data.get("subtitle") or data.get("summary") or ""), 220),
                "app_name": clip_text(str(data.get("app_name") or data.get("app") or ""), 80),
                "package_name": clip_text(str(data.get("package_name") or data.get("package") or ""), 180),
                "action": clip_text(str(data.get("action") or ""), 80),
                "status": clip_text(str(data.get("status") or "completed"), 24),
                "metadata_json": metadata,
            }
            if previous is not None:
                if not data.get("created_at") and not data.get("at"):
                    entry["created_at"] = previous.get("created_at") or entry["created_at"]
                previous.update({k: v for k, v in entry.items() if v not in ("", None) or k in ("status", "metadata_json")})
                self.save_activity_events()
                return dict(previous)
            if dedupe_seconds > 0:
                now = time.time()
                duplicate = next((e for e in self.activity_events[:40]
                    if e.get("device_id") == entry["device_id"] and e.get("source") == entry["source"]
                    and e.get("type") == entry["type"] and e.get("action") == entry["action"]
                    and e.get("package_name") == entry["package_name"]
                    and now - parse_iso_seconds(e.get("created_at")) <= dedupe_seconds), None)
                if duplicate is not None: return dict(duplicate)
            self.activity_events.insert(0, entry)
            del self.activity_events[ACTIVITY_EVENT_LIMIT:]
            self.save_activity_events()
            return dict(entry)

    def list_activity_events(self, device_id: str = "", date: str = "", source: str = "", limit: int = 50) -> list[dict]:
        with self.activity_lock:
            items = list(self.activity_events)
        if device_id: items = [e for e in items if e.get("device_id") == device_id]
        if date: items = [e for e in items if str(e.get("created_at") or "").startswith(date)]
        if source: items = [e for e in items if e.get("source") == source]
        return items[:max(1, min(500, limit))]

    def _load_companion_state(self) -> dict:
        fallback = {
            "whisper": {
                "content": "把今天，轻轻收进窗里。",
                "author": "陪伴对象",
                "updated_at": now_iso(),
                "version": 1,
            },
            "actions": [],
        }
        try:
            if self.companion_path.exists():
                loaded = json.loads(self.companion_path.read_text(encoding="utf-8"))
                if isinstance(loaded, dict):
                    fallback.update(loaded)
        except Exception:
            pass
        return fallback

    def save_companion_state(self) -> None:
        self.companion_path.parent.mkdir(parents=True, exist_ok=True)
        temp = self.companion_path.with_suffix(".tmp")
        temp.write_text(json.dumps(self.companion_state, ensure_ascii=False, indent=2), encoding="utf-8")
        temp.replace(self.companion_path)

    def update_whisper(self, content: str, author: str) -> dict:
        with self.companion_lock:
            previous = self.companion_state.get("whisper") or {}
            whisper = {
                "content": clip_text(content, 240),
                "author": clip_text(author, 24) or "用户",
                "updated_at": now_iso(),
                "version": int(previous.get("version") or 0) + 1,
            }
            self.companion_state["whisper"] = whisper
            self.save_companion_state()
            return whisper

    def record_companion_action(self, data: dict) -> dict:
        with self.companion_lock:
            entry = {
                "id": str(uuid.uuid4()),
                "at": now_iso(),
                "kind": clip_text(str(data.get("kind") or "陪伴"), 24),
                "title": clip_text(str(data.get("title") or "完成了一次行动"), 80),
                "summary": clip_text(str(data.get("summary") or ""), 160),
                "status": clip_text(str(data.get("status") or "completed"), 24),
                "source": "companion",
            }
            actions = self.companion_state.setdefault("actions", [])
            actions.insert(0, entry)
            del actions[120:]
            self.save_companion_state()
        if data.get("write_activity"):
            self.add_activity_event({
                "device_id": data.get("device_id") or DEFAULT_DEVICE, "source": "companion",
                "type": data.get("type") or "activity", "title": entry["title"],
                "subtitle": entry["summary"], "action": data.get("action") or "",
                "status": entry["status"], "metadata_json": data.get("metadata_json") or {}
            }, int(data.get("dedupe_seconds") or 0))
        return entry

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


def clip_text(value: str, limit: int = 1200) -> str:
    value = (value or "").strip()
    if len(value) <= limit:
        return value
    return value[:limit].rstrip() + "…"


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


    def _public_base(self) -> str:
        configured = os.environ.get("LINJIAN_PUBLIC_URL", "").strip().rstrip("/")
        if configured:
            return configured
        proto = self.headers.get("X-Forwarded-Proto", "https") or "https"
        host = self.headers.get("X-Forwarded-Host") or self.headers.get("Host") or f"localhost:{self.state.port}"
        return f"{proto}://{host}".rstrip("/")

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)
        if path in ("/", "/health"):
            self._json(200, {"ok": True, "service": "linjian-public", "name": "掌心窗", "version": VERSION, "tools": sorted(ALLOWED_ACTIONS), "guidian": True, "calendar": True, "diary": True, "diary_storage": "phone_local", "app_gate": True})
            return
        if path == "/api/companion/state":
            if not self._require_token(): return
            limit = max(1, min(50, int(qs.get("limit", ["20"])[0] or 20)))
            with self.state.companion_lock:
                whisper = dict(self.state.companion_state.get("whisper") or {})
                actions = list(self.state.companion_state.get("actions") or [])[:limit]
            unified = [e for e in self.state.list_activity_events("", "", "", ACTIVITY_EVENT_LIMIT)
                       if e.get("source") in ("companion", "assistant") or e.get("type") in ("command", "notification", "weather", "calendar", "status_check")][:limit]
            if unified:
                actions = [{"id": e.get("id"), "at": e.get("created_at"), "created_at": e.get("created_at"), "kind": e.get("type"), "type": e.get("type"), "title": e.get("title"), "summary": e.get("subtitle"), "subtitle": e.get("subtitle"), "status": e.get("status"), "source": "companion"} for e in unified]
            self._json(200, {"ok": True, "whisper": whisper, "actions": actions}); return
        if path == "/api/activity/events":
            if not self._require_token(): return
            device_id = qs.get("device_id", [""])[0]
            date = qs.get("date", [""])[0]
            source = qs.get("source", [""])[0]
            limit = max(1, min(500, int(qs.get("limit", ["50"])[0] or 50)))
            events = self.state.list_activity_events(device_id, date, source, limit)
            self._json(200, {"ok": True, "events": events, "count": len(events)}); return
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
        if path == "/api/guidian_state":
            if not self._require_token(): return
            device_id = qs.get("device_id", [DEFAULT_DEVICE])[0] or DEFAULT_DEVICE
            st = self.state.device_states.get(device_id) or {}
            guidian = st.get("guidian_state") or {}
            self._json(200, {"ok": True, "device_id": device_id, "guidian_state": guidian}); return
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
        if path == "/api/companion/whisper":
            if not self._require_token(): return
            data = self._read_json()
            content = (data.get("content") or "").strip()
            if not content:
                self._json(400, {"ok": False, "error": "content_required"}); return
            whisper = self.state.update_whisper(content, data.get("author") or "用户")
            self._json(200, {"ok": True, "whisper": whisper}); return
        if path == "/api/companion/action":
            if not self._require_token(): return
            entry = self.state.record_companion_action(self._read_json())
            self._json(200, {"ok": True, "action": entry}); return
        if path == "/api/activity/events":
            if not self._require_token(): return
            data = self._read_json()
            event = self.state.add_activity_event(data, max(0, min(300, int(data.get("dedupe_seconds") or 0))))
            self._json(200, {"ok": True, "event": event}); return
        if path == "/api/peek":
            if not self._require_token(): return
            self._queue(make_command(DEFAULT_DEVICE, "peek")); self._json(200, {"ok": True, "queued": True}); return
        if path == "/api/command":
            if not self._require_token(): return
            data = self._read_json()
            cmd = make_command(data.get("device_id") or DEFAULT_DEVICE, data.get("action") or "noop", data.get("app") or "", data.get("package") or "", data.get("payload") or data)
            action = cmd.get("action") or "noop"
            app_name = cmd.get("app") or (cmd.get("payload") or {}).get("app") or ""
            title_map = {
                "send_notification": "发送提醒", "set_alarm": "设置闹钟", "trigger_guidian": "发起归电",
                "mark_guidian_returned": "记录归电回应", "get_guidian_state": "查看归电状态", "set_guidian_config": "调整归电设置",
                "screen_off": "让手机息屏", "phone_screen_off": "让手机息屏", "open_app": "打开应用",
                "screen_break_app": "开启应用门禁", "lock_app": "开启应用门禁", "end_screen_break": "解除应用门禁", "unlock_app": "解除应用门禁", "extend_screen_break": "延长应用门禁", "extend_lock": "延长应用门禁",
                "temporary_screen_break_release": "临时解除应用门禁", "temporary_unlock_app": "临时解除应用门禁", "deny_screen_break_release_request": "拒绝门禁解除", "deny_unlock_request": "拒绝门禁解除",
                "get_screen_break_state": "查看应用门禁状态", "get_lock_state": "查看应用门禁状态", "list_screen_break_apps": "查看可管理应用", "list_lockable_apps": "查看可管理应用",
                "add_screen_break_app": "加入门禁管理", "add_locked_app": "加入门禁管理", "remove_locked_app": "移除门禁应用", "set_screen_break_passphrase": "设置门禁口令", "set_emergency_passphrase": "设置门禁口令",
                "get_calendar_state": "查看守护日历", "upsert_calendar_event": "更新守护日历",
                "get_screen_nodes": "查看当前页面",
                "peek": "查看屏幕", "run_sequence": "执行组合动作", "home": "回到手机桌面", "back": "返回上一页", "recents": "打开最近任务"
            }
            event = self.state.add_activity_event({
                "id": cmd.get("id"), "device_id": cmd.get("device_id"), "source": "companion", "type": activity_type_for_action(action),
                "title": title_map.get(action, "执行 " + action), "subtitle": app_name,
                "app_name": app_name, "package_name": cmd.get("package") or "", "action": action,
                "status": "pending", "metadata_json": {"command_id": cmd.get("id")}
            })
            cmd["activity_event_id"] = event.get("id")
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
            completed_cmd = None
            with self.state.commands_lock:
                cmd = self.state.command_history.get(cid)
                if cmd is not None:
                    cmd["status"] = "completed" if data.get("ok") else "failed"
                    cmd["completed_at"] = now_iso()
                    cmd["result"] = data.get("result", "")
                    cmd["report"] = data
                    completed_cmd = dict(cmd)
            if completed_cmd is not None:
                try:
                    self.state.add_activity_event({
                        "id": completed_cmd.get("activity_event_id") or cid, "device_id": completed_cmd.get("device_id") or DEFAULT_DEVICE,
                        "source": "companion", "type": activity_type_for_action(completed_cmd.get("action")), "action": completed_cmd.get("action") or "",
                        "status": completed_cmd["status"], "metadata_json": {"command_id": cid, "result": clip_text(str(data.get("result") or ""), 500)}
                    })
                except Exception: pass
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
