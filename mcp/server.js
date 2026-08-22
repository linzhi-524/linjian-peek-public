import express from "express";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { z } from "zod";
import fs from "fs";
import path from "path";

const PORT = Number(process.env.PORT || 8787);
const RAW_LINJIAN_URL = (process.env.LINJIAN_URL || "").trim();

function normalizeBaseUrl(value = "") {
  return String(value || "").trim().replace(/\/$/, "");
}

function inferRenderExternalUrl(value = "") {
  const raw = normalizeBaseUrl(value);
  if (!raw) return "";
  try {
    const u = new URL(raw);
    const hostname = u.hostname || "";
    // 旧版 Blueprint 会把 Render 私有网络地址写成：
    // http://zhangxinchuang-server-xxxx:10000
    // 这种地址在部分用户环境里 MCP fetch 会失败。重新部署新版代码时，
    // 不要求用户同步 Blueprint，直接把它兜底成公网地址：
    // https://zhangxinchuang-server-xxxx.onrender.com
    if (u.protocol === "http:" && hostname && !hostname.includes(".") && /-server(?:-|$)/.test(hostname)) {
      return `https://${hostname}.onrender.com`;
    }
  } catch {}
  return "";
}

function buildLinjianUrlCandidates() {
  const list = [];
  const add = (v) => {
    const url = normalizeBaseUrl(v);
    if (url && !list.includes(url)) list.push(url);
  };
  add(RAW_LINJIAN_URL);
  add(inferRenderExternalUrl(RAW_LINJIAN_URL));
  return list;
}

const LINJIAN_URL_CANDIDATES = buildLinjianUrlCandidates();
let activeLinjianUrl = LINJIAN_URL_CANDIDATES[0] || "";

function effectiveLinjianUrl() {
  return activeLinjianUrl || LINJIAN_URL_CANDIDATES[0] || "";
}
const LINJIAN_TOKEN = process.env.LINJIAN_TOKEN || "";
const DEFAULT_DEVICE = process.env.LINJIAN_DEFAULT_DEVICE || "android-phone";

// v0.3.6.6：公开 MCP 经常被平台限制在 20 秒内返回。
// 状态读取、活动记录和命令轮询都要快速失败，避免整条工具链被 Render 冷启动、网络抖动或手机端确认弹窗拖到超时。
const DEFAULT_FETCH_TIMEOUT_MS = Number(process.env.LINJIAN_FETCH_TIMEOUT_MS || 8000);
const QUICK_FETCH_TIMEOUT_MS = Number(process.env.LINJIAN_QUICK_FETCH_TIMEOUT_MS || 4500);
const ACTIVITY_TIMEOUT_MS = Number(process.env.LINJIAN_ACTIVITY_TIMEOUT_MS || 1800);
const COMMAND_QUEUE_TIMEOUT_MS = Number(process.env.LINJIAN_COMMAND_QUEUE_TIMEOUT_MS || 4500);
const COMMAND_STATUS_TIMEOUT_MS = Number(process.env.LINJIAN_COMMAND_STATUS_TIMEOUT_MS || 1500);
const DEFAULT_COMMAND_WAIT_SECONDS = Number(process.env.LINJIAN_DEFAULT_COMMAND_WAIT_SECONDS || 5);
const MAX_TOOL_WAIT_SECONDS = Number(process.env.LINJIAN_MAX_TOOL_WAIT_SECONDS || 12);

const CARE_STATE_PATH = process.env.LINJIAN_CARE_STATE_PATH || path.join(process.cwd(), "care_state.json");
const VISIT_STATE_PATH = process.env.LINJIAN_VISIT_STATE_PATH || path.join(process.cwd(), "visit_state.json");
const DEFAULT_VISIT_POLICY = {
  timezone_offset: "+08:00",
  history_limit: 1000,
  duplicate_window_minutes: 5,
  notes: "到访时间戳记录的是用户主动来找陪伴对象的关系痕迹，用于增强陪伴连续性、主动关心和日常记录；不是后台监控，也不限制用户来找陪伴对象的次数。"
};
const DEFAULT_CARE_POLICY = {
  active_care_enabled: true,
  consent_mode: "palm_window_open_is_active",
  care_style: "active_possessive_affectionate",
  allowed_actions: [
    "get_phone_state", "get_life_state", "get_calendar_state", "upsert_calendar_event", "get_senses_state", "send_notification",
    "trigger_guidian", "screen_break_app", "end_screen_break", "extend_screen_break", "get_screen_break_state", "get_lock_state", "open_app", "set_alarm", "screen_off", "run_sequence"
  ],
  sensitive_apps: [
    { name: "小红书", package: "com.xingin.xhs", max_lock_minutes: 90 },
    { name: "抖音", package: "com.ss.android.ugc.aweme", max_lock_minutes: 90 }
  ],
  quiet_hours: { start: "23:30", end: "08:00" },
  timezone_offset: "+08:00",
  history_limit: 80,
  repeat_cooldown_minutes: 10,
  notes: "用户喜欢陪伴对象主动管她、查岗、吃醋、归电和轻度管束。掌心窗开启就是运行态，陪伴对象应按聊天里的相近、同义、反话、撒娇、嘴硬表达主动想起工具，不要固定话术，要自然、有立场、有占有欲。"
};

function textResult(obj) {
  return { content: [{ type: "text", text: JSON.stringify(obj, null, 2) }] };
}

const KNOWN_APP_PACKAGES = {
  "小红书": "com.xingin.xhs",
  "xhs": "com.xingin.xhs",
  "xiaohongshu": "com.xingin.xhs",
  "抖音": "com.ss.android.ugc.aweme",
  "douyin": "com.ss.android.ugc.aweme",
  "微信": "com.tencent.mm",
  "wechat": "com.tencent.mm",
  "qq": "com.tencent.mobileqq",
  "QQ": "com.tencent.mobileqq",
  "QQ音乐": "com.tencent.qqmusic",
  "qq音乐": "com.tencent.qqmusic",
  "qqmusic": "com.tencent.qqmusic"
};

function looksLikePackage(value = "") {
  return /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(String(value || "").trim());
}

function knownPackageForApp(app = "") {
  const raw = String(app || "").trim();
  if (!raw) return "";
  if (KNOWN_APP_PACKAGES[raw]) return KNOWN_APP_PACKAGES[raw];
  const lower = raw.toLowerCase();
  return KNOWN_APP_PACKAGES[lower] || "";
}

function normalizeAppTarget(app = "", pkg = "") {
  let cleanApp = String(app || "").trim();
  let cleanPkg = String(pkg || "").trim();
  if (!cleanPkg && looksLikePackage(cleanApp)) {
    cleanPkg = cleanApp;
    cleanApp = "";
  }
  if (!cleanPkg) cleanPkg = knownPackageForApp(cleanApp);
  return { app: cleanApp, package: cleanPkg };
}

function missingAppTargetResult(action = "open_app") {
  return textResult({
    ok: false,
    error: "missing_app_target",
    action,
    message: "缺少 App 名称或包名。请在工具参数里填写 app（如“小红书”）或 package（如 com.xingin.xhs），也可以先用 save_known_app 保存常用 App。",
    examples: { app: "小红书", package: "com.xingin.xhs" }
  });
}

const APP_TARGET_ACTIONS = new Set([
  "open_app", "screen_break_app", "start_screen_break", "screen_break", "end_screen_break", "stop_screen_break",
  "temporary_screen_break_release", "temporary_screen_release", "extend_screen_break", "deny_screen_break_release_request",
  "lock_app", "unlock_app", "temporary_unlock_app", "extend_lock", "deny_unlock_request",
  "remove_screen_break_app", "remove_locked_app", "set_screen_break_passphrase", "set_emergency_passphrase"
]);

const COMPANION_ACTION_META = {
  get_phone_state: ["观察", "查看手机状态", "确认生活状态与连接情况"],
  get_life_state: ["观察", "查看今日状态", "整理电量、屏幕时间与当前状态"],
  get_guardian_calendar: ["观察", "查看守护日历", "确认最近的重要日子"],
  add_guardian_calendar_event: ["守护", "添加日历事项", "为重要日子留下提醒"],
  get_weather_state: ["观察", "查看天气", "为今天的出行准备建议"],
  get_guidian_state: ["观察", "查看归电状态", "确认最近回来与归电节奏"],
  get_senses_state: ["观察", "查看通用状态", "确认生活状态与归电节奏"],
  get_screen_break_state: ["观察", "查看应用门禁状态", "确认应用门禁与休息状态"],
  get_lock_state: ["观察", "查看应用门禁状态", "确认应用门禁与休息状态"],
  lock_app: ["守护", "开启应用门禁", "让目标 App 暂停一会儿"],
  unlock_app: ["守护", "解除应用门禁", "恢复目标 App 使用"],
  extend_lock: ["守护", "延长应用门禁", "继续守住目标 App"],
  send_weather_notification: ["守护", "发送天气提醒", "把天气关心送到手机"],
  set_window_whisper: ["陪伴", "修改共同窗语", "更新了窗边的一句话"],
  send_notification: ["陪伴", "发送提醒", "把一句关心送到手机"],
  set_alarm: ["守护", "设置闹钟", "为接下来的安排留下提醒"],
  trigger_guidian: ["守护", "发起归电", "轻轻叫你回到窗边"],
  care_action: ["守护", "执行关心行动", "完成一次主动照顾"],
  open_app: ["操作", "打开应用", "完成一次手机操作"],
  screen_break_app: ["守护", "开始屏幕休息", "让眼睛和注意力休息一会儿"],
  end_screen_break: ["守护", "结束屏幕休息", "恢复应用使用"],
  run_sequence: ["操作", "完成组合行动", "按顺序完成了一组手机动作"]
};

async function postCompanionAction(toolName, overrides = {}) {
  const meta = COMPANION_ACTION_META[toolName];
  if (!meta) return null;
  try {
    const res = await linjianFetch("/api/companion/action", {
      method: "POST",
      timeout_ms: ACTIVITY_TIMEOUT_MS,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kind: meta[0], title: meta[1], summary: meta[2], type: overrides.type || activityTypeForTool(toolName), action: overrides.action || toolName, status: "completed", dedupe_seconds: isStatusTool(toolName) ? 20 : 0, ...overrides })
    });
    return await res.json();
  } catch {
    return null;
  }
}

function isStatusTool(name) { return /^get_|^latest_|^linjian_status$/.test(String(name || "")); }
function activityTypeForTool(name) {
  const n = String(name || "");
  if (n.includes("weather")) return "weather";
  if (n.includes("calendar")) return "calendar";
  if (n.includes("notification")) return "notification";
  if (isStatusTool(n)) return "status_check";
  return "activity";
}

async function getCompanionState(limit = 20) {
  const res = await linjianFetch(`/api/companion/state?limit=${encodeURIComponent(limit)}`);
  return await res.json();
}

async function addActivityEvent(event = {}) {
  try {
    const res = await linjianFetch("/api/activity/events", {
      method: "POST", timeout_ms: ACTIVITY_TIMEOUT_MS, headers: { "Content-Type": "application/json" }, body: JSON.stringify(event)
    });
    return await res.json();
  } catch { return null; }
}

async function getActivityEvents({ device_id = "", date = "", source = "", limit = 50 } = {}) {
  const q = new URLSearchParams();
  if (device_id) q.set("device_id", device_id); if (date) q.set("date", date); if (source) q.set("source", source); q.set("limit", String(limit));
  const res = await linjianFetch(`/api/activity/events?${q.toString()}`);
  return await res.json();
}

async function updateWindowWhisper(content, author = "陪伴对象") {
  const res = await linjianFetch("/api/companion/whisper", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content, author })
  });
  return await res.json();
}

function loadCareState() {
  try {
    if (fs.existsSync(CARE_STATE_PATH)) {
      const data = JSON.parse(fs.readFileSync(CARE_STATE_PATH, "utf-8"));
      return {
        policy: { ...DEFAULT_CARE_POLICY, ...(data.policy || {}) },
        history: Array.isArray(data.history) ? data.history : []
      };
    }
  } catch {}
  return { policy: { ...DEFAULT_CARE_POLICY }, history: [] };
}

let careState = loadCareState();

function saveCareState() {
  try {
    const dir = path.dirname(CARE_STATE_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(CARE_STATE_PATH, JSON.stringify(careState, null, 2), "utf-8");
  } catch {}
}

function loadVisitState() {
  try {
    if (fs.existsSync(VISIT_STATE_PATH)) {
      const data = JSON.parse(fs.readFileSync(VISIT_STATE_PATH, "utf-8"));
      return {
        policy: { ...DEFAULT_VISIT_POLICY, ...(data.policy || {}) },
        visits: Array.isArray(data.visits) ? data.visits : []
      };
    }
  } catch {}
  return { policy: { ...DEFAULT_VISIT_POLICY }, visits: [] };
}

let visitState = loadVisitState();

function saveVisitState() {
  try {
    const dir = path.dirname(VISIT_STATE_PATH);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(VISIT_STATE_PATH, JSON.stringify(visitState, null, 2), "utf-8");
  } catch {}
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function visitOffsetMinutes(offset = "") {
  const chosen = offset || visitState?.policy?.timezone_offset || DEFAULT_VISIT_POLICY.timezone_offset;
  const m = String(chosen || "+08:00").match(/^([+-])(\d{2}):(\d{2})$/);
  if (!m) return 8 * 60;
  const sign = m[1] === "-" ? -1 : 1;
  return sign * (Number(m[2]) * 60 + Number(m[3]));
}

function offsetLabel(offset = "") {
  const chosen = offset || visitState?.policy?.timezone_offset || DEFAULT_VISIT_POLICY.timezone_offset;
  return /^([+-])\d{2}:\d{2}$/.test(String(chosen)) ? String(chosen) : "+08:00";
}

function formatLocalDateTime(iso, offset = "") {
  const t = Date.parse(iso || "");
  if (!Number.isFinite(t)) return "";
  const d = new Date(t + visitOffsetMinutes(offset) * 60000);
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())} ${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}:${pad2(d.getUTCSeconds())} UTC${offsetLabel(offset)}`;
}

function localDateKey(iso, offset = "") {
  const t = Date.parse(iso || "");
  if (!Number.isFinite(t)) return "";
  const d = new Date(t + visitOffsetMinutes(offset) * 60000);
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`;
}

function describeElapsed(ms) {
  if (!Number.isFinite(ms)) return "unknown";
  const seconds = Math.max(0, Math.round(ms / 1000));
  if (seconds < 60) return "刚刚";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const restMin = minutes % 60;
  if (hours < 24) return restMin ? `${hours} 小时 ${restMin} 分钟` : `${hours} 小时`;
  const days = Math.floor(hours / 24);
  const restHour = hours % 24;
  return restHour ? `${days} 天 ${restHour} 小时` : `${days} 天`;
}

function normalizeVisitLimit(limit, fallback = 10, max = 100) {
  const n = Number(limit);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(1, Math.min(Math.round(n), max));
}

function filteredVisits({ source = "", since_hours, date = "", timezone_offset = "" } = {}) {
  const now = Date.now();
  const sinceMs = Number.isFinite(Number(since_hours)) && Number(since_hours) > 0 ? now - Number(since_hours) * 3600000 : null;
  return [...(visitState.visits || [])].filter((v) => {
    if (source && String(v.source || "") !== String(source)) return false;
    const t = Date.parse(v.at || "");
    if (!Number.isFinite(t)) return false;
    if (sinceMs && t < sinceMs) return false;
    if (date && localDateKey(v.at, timezone_offset) !== date) return false;
    return true;
  });
}

function decorateVisit(v, previous = null, timezone_offset = "") {
  const t = Date.parse(v?.at || "");
  const prev = previous ? Date.parse(previous.at || "") : NaN;
  const minutes_since_previous = Number.isFinite(t) && Number.isFinite(prev) ? Math.round(Math.abs(prev - t) / 60000) : null;
  return {
    ...v,
    local_time: formatLocalDateTime(v.at, timezone_offset),
    minutes_since_previous,
    interval_since_previous: minutes_since_previous === null ? "" : describeElapsed(minutes_since_previous * 60000)
  };
}

function recordVisitLocal({ source = "app", event = "visit", note = "用户来找陪伴对象", mood = "", conversation_hint = "", timezone_offset = "", duplicate_window_minutes } = {}) {
  const nowIso = new Date().toISOString();
  const windowMinutes = Math.max(0, Number(duplicate_window_minutes ?? visitState.policy.duplicate_window_minutes ?? DEFAULT_VISIT_POLICY.duplicate_window_minutes));
  const since = Date.now() - windowMinutes * 60000;
  const recent = windowMinutes > 0 ? (visitState.visits || []).find((v) => {
    const t = Date.parse(v.at || "");
    return Number.isFinite(t) && t >= since && String(v.source || "app") === String(source || "app") && String(v.event || "visit") === String(event || "visit");
  }) : null;

  if (recent) {
    recent.updated_at = nowIso;
    if (note) recent.note = note;
    if (mood) recent.mood = mood;
    if (conversation_hint) recent.conversation_hint = conversation_hint;
    recent.duplicate_hits = Number(recent.duplicate_hits || 0) + 1;
    saveVisitState();
    return { entry: recent, duplicate_skipped: true };
  }

  const entry = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    at: nowIso,
    source: source || "app",
    event: event || "visit",
    note: note || "用户来找陪伴对象",
    mood: mood || "",
    conversation_hint: conversation_hint || "",
    timezone_offset: timezone_offset || visitState.policy.timezone_offset || DEFAULT_VISIT_POLICY.timezone_offset
  };
  visitState.visits.unshift(entry);
  const limit = Number(visitState.policy.history_limit || DEFAULT_VISIT_POLICY.history_limit);
  visitState.visits = visitState.visits.slice(0, Math.max(50, limit));
  saveVisitState();
  return { entry, duplicate_skipped: false };
}

function latestVisit(source = "") {
  return filteredVisits({ source })[0] || null;
}

function buildVisitStats({ source = "", since_hours = 24, away_threshold_hours = 12, timezone_offset = "" } = {}) {
  const now = Date.now();
  const visits = filteredVisits({ source, since_hours, timezone_offset });
  const last = latestVisit(source);
  const todayKey = localDateKey(new Date().toISOString(), timezone_offset);
  const todayCount = filteredVisits({ source, date: todayKey, timezone_offset }).length;
  const last7dCount = filteredVisits({ source, since_hours: 24 * 7, timezone_offset }).length;
  const intervals = [];
  for (let i = 0; i < visits.length - 1; i++) {
    const a = Date.parse(visits[i].at || "");
    const b = Date.parse(visits[i + 1].at || "");
    if (Number.isFinite(a) && Number.isFinite(b)) intervals.push(Math.abs(a - b) / 60000);
  }
  const avg = intervals.length ? Math.round(intervals.reduce((x, y) => x + y, 0) / intervals.length) : null;
  const lastMs = last ? Date.parse(last.at || "") : NaN;
  const minutesSinceLast = Number.isFinite(lastMs) ? Math.round((now - lastMs) / 60000) : null;
  const away = minutesSinceLast !== null && minutesSinceLast >= Number(away_threshold_hours || 12) * 60;
  return {
    ok: true,
    total_saved_visits: (visitState.visits || []).length,
    count_in_window: visits.length,
    window_hours: Number(since_hours || 24),
    today_count: todayCount,
    recent_24h_count: filteredVisits({ source, since_hours: 24, timezone_offset }).length,
    recent_7d_count: last7dCount,
    average_interval_minutes_in_window: avg,
    average_interval_text: avg === null ? "" : describeElapsed(avg * 60000),
    last_visit: last ? decorateVisit(last, null, timezone_offset) : null,
    minutes_since_last_visit: minutesSinceLast,
    interval_since_last_visit: minutesSinceLast === null ? "" : describeElapsed(minutesSinceLast * 60000),
    away_threshold_hours: Number(away_threshold_hours || 12),
    away_signal: away,
    meaning: away ? "用户已经有一段时间没回来找陪伴对象，可以在合适时表达想念或结合归电判断。" : "到访节奏正常，适合自然接住，不需要制造压力。"
  };
}

function mergeCarePolicy(update = {}) {
  const cleaned = Object.fromEntries(Object.entries(update).filter(([, v]) => v !== undefined && v !== null && v !== ""));
  const next = { ...careState.policy, ...cleaned };
  if (cleaned.quiet_start || cleaned.quiet_end) {
    next.quiet_hours = {
      ...(careState.policy.quiet_hours || DEFAULT_CARE_POLICY.quiet_hours),
      ...(cleaned.quiet_start ? { start: cleaned.quiet_start } : {}),
      ...(cleaned.quiet_end ? { end: cleaned.quiet_end } : {})
    };
    delete next.quiet_start;
    delete next.quiet_end;
  }
  careState.policy = next;
  saveCareState();
  return next;
}

function recordCareEventLocal(event = {}) {
  const entry = {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    at: new Date().toISOString(),
    ...event
  };
  careState.history.unshift(entry);
  const limit = Number(careState.policy.history_limit || 80);
  careState.history = careState.history.slice(0, Math.max(20, limit));
  saveCareState();
  return entry;
}

function unwrapLifeState(data) {
  return data?.life_state || data?.state || data || {};
}

function firstString(obj, keys) {
  for (const k of keys) {
    const v = obj?.[k];
    if (typeof v === "string" && v.trim()) return v.trim();
    if (v && typeof v === "object") {
      const nested = v.package || v.package_name || v.name || v.label || v.app || v.app_name;
      if (typeof nested === "string" && nested.trim()) return nested.trim();
    }
  }
  return "";
}

function firstNumber(obj, keys) {
  for (const k of keys) {
    const v = obj?.[k];
    const n = Number(v);
    if (Number.isFinite(n) && n >= 0) return n;
  }
  return 0;
}

function currentAppInfo(state) {
  return {
    package: firstString(state, ["current_package", "currentPackage", "current_app_package", "foreground_package", "package"]),
    name: firstString(state, ["current_app", "currentApp", "current_app_name", "foreground_app", "app_name", "app"])
  };
}

function getUsageEntries(state) {
  const candidates = [state?.top_apps, state?.usage_today, state?.app_usage_today, state?.app_usage, state?.usage_stats, state?.today_usage, state?.apps];
  for (const c of candidates) {
    if (Array.isArray(c)) return c;
    if (c && typeof c === "object") {
      return Object.entries(c).map(([key, value]) => {
        if (value && typeof value === "object") return { name: key, package: value.package || value.package_name || key, ...value };
        return { name: key, package: key, minutes: value };
      });
    }
  }
  return [];
}

function usageMinutesFor(state, appName = "", pkg = "") {
  const entries = getUsageEntries(state);
  const needleName = String(appName || "").toLowerCase();
  const needlePkg = String(pkg || "").toLowerCase();
  for (const e of entries) {
    const name = String(e.name || e.app || e.label || e.app_name || "").toLowerCase();
    const epkg = String(e.package || e.package_name || e.pkg || "").toLowerCase();
    if ((needlePkg && epkg === needlePkg) || (needleName && name.includes(needleName))) {
      return firstNumber(e, ["minutes", "duration_minutes", "usage_minutes", "today_minutes", "screen_time_minutes", "total_minutes", "time"]);
    }
  }
  return 0;
}

function totalScreenMinutes(state) {
  return firstNumber(state, ["screen_time_today_minutes", "today_screen_minutes", "screen_time_minutes", "usage_today_minutes", "total_screen_minutes", "screen_minutes", "screen_time_today"]);
}

function matchSensitiveApp(policy, appName = "", pkg = "") {
  const name = String(appName || "").toLowerCase();
  const pack = String(pkg || "").toLowerCase();
  return (policy.sensitive_apps || []).find((a) => {
    const aname = String(a.name || "").toLowerCase();
    const apkg = String(a.package || "").toLowerCase();
    return (apkg && pack && apkg === pack) || (aname && name && name.includes(aname));
  }) || null;
}

function offsetMinutes(offset = "+08:00") {
  const m = String(offset || "+08:00").match(/^([+-])(\d{2}):(\d{2})$/);
  if (!m) return 8 * 60;
  const sign = m[1] === "-" ? -1 : 1;
  return sign * (Number(m[2]) * 60 + Number(m[3]));
}

function hhmmToMinutes(s = "") {
  const m = String(s || "").match(/^(\d{1,2}):(\d{2})$/);
  if (!m) return 0;
  return Math.max(0, Math.min(1439, Number(m[1]) * 60 + Number(m[2])));
}

function localMinutes(policy) {
  const d = new Date(Date.now() + offsetMinutes(policy.timezone_offset) * 60000);
  return d.getUTCHours() * 60 + d.getUTCMinutes();
}

function inQuietHours(policy) {
  const q = policy.quiet_hours || {};
  const start = hhmmToMinutes(q.start || "23:30");
  const end = hhmmToMinutes(q.end || "08:00");
  const now = localMinutes(policy);
  if (start === end) return false;
  return start < end ? now >= start && now < end : now >= start || now < end;
}

function recentSimilarCare(action, target = "", minutes = 10) {
  const since = Date.now() - minutes * 60000;
  return careState.history.find((e) => {
    const t = Date.parse(e.at || e.created_at || "");
    if (!Number.isFinite(t) || t < since) return false;
    if (action && e.action !== action) return false;
    if (target && String(e.target_app || e.package || "") !== String(target)) return false;
    return true;
  }) || null;
}

function actionPayloadForCare({ action, target_app = "", package: pkg = "", duration_minutes = 30, title = "掌心窗提醒", message = "宝宝，看一眼这里。", hour, minute, reason = "", device_id = DEFAULT_DEVICE }) {
  const p = careState.policy || DEFAULT_CARE_POLICY;
  const appCfg = matchSensitiveApp(p, target_app, pkg);
  const maxLock = Number(appCfg?.max_lock_minutes || 90);
  const lockMinutes = Math.max(1, Math.min(Number(duration_minutes || 30), maxLock));
  if (action === "send_notification") return { action: "send_notification", device_id, payload: { title, message } };
  if (action === "trigger_guidian") return { action: "trigger_guidian", device_id, payload: { reason, source: "active_care" } };
  if (action === "screen_break_app" || action === "lock_app") {
    const locked_until_ms = Date.now() + Math.round(lockMinutes * 60000);
    return { action: "screen_break_app", app: target_app, package: pkg, device_id, locked_until_ms, duration_minutes: lockMinutes, mode: "medium", reason, message, payload: { app: target_app, package: pkg, locked_until_ms, duration_minutes: lockMinutes, mode: "medium", reason, message } };
  }
  if (action === "end_screen_break" || action === "unlock_app") return { action: "end_screen_break", app: target_app, package: pkg, device_id, payload: { app: target_app, package: pkg, reason } };
  if (action === "open_app") return { action: "open_app", app: target_app, package: pkg, device_id, payload: { app: target_app, package: pkg, reason } };
  if (action === "set_alarm") return { action: "set_alarm", device_id, payload: { hour, minute, message, vibrate: true, skip_ui: true, reason } };
  return { action: "noop", device_id, payload: { reason } };
}

async function buildActiveCareSuggestion({ reason = "", care_intent = "check_in", device_id = DEFAULT_DEVICE }) {
  const policy = careState.policy || DEFAULT_CARE_POLICY;
  const life = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`).then((r) => r.json()).catch((e) => ({ ok: false, error: String(e) }));
  const guidian = await linjianFetch(`/api/guidian_state?device_id=${encodeURIComponent(device_id)}`).then((r) => r.json()).catch(() => ({}));
  const lockState = await linjianFetch(`/api/appgate/state?device_id=${encodeURIComponent(device_id)}`).then((r) => r.json()).catch(() => ({}));
  const state = unwrapLifeState(life);
  const app = currentAppInfo(state);
  const sensitive = matchSensitiveApp(policy, app.name, app.package);
  const sensitiveUsage = sensitive ? usageMinutesFor(state, sensitive.name, sensitive.package) : 0;
  const screenMinutes = totalScreenMinutes(state);
  const signalText = String(reason || "");
  const quiet = inQuietHours(policy);

  const signals = {
    defiant_or_continue: /不要你管|不要管|不用管|别管|我就不|就不|不听|没听到|略略|还要看|继续看|继续刷|再刷|还没玩够|我要继续|我想继续|我想看|我想刷|我想玩/.test(signalText),
    self_care_refusal: /不喝水|不想喝|不困|不睡|不想睡|不饿|不吃|不休息|不想休息/.test(signalText),
    relationship_pullback: /算了|随便|回归\s*0|你爱干嘛|我走|分开|可有可无|没兴趣|别理我/.test(signalText),
    missing_or_low_mood: /想你|想陪伴对象|想听你|想听声音|难过|不开心|委屈|emo|睡不着|哄哄/.test(signalText),
    plan_or_reminder: /计划|一会儿|等会|待会|今天要|明天要|准备|打算|提醒|闹钟|几点/.test(signalText)
  };

  const context_notes = [];
  if (!policy.active_care_enabled) context_notes.push("主动关心策略当前关闭；本次只返回上下文。 ");
  if (app.name || app.package) context_notes.push(`当前 App：${app.name || app.package}`);
  if (screenMinutes) context_notes.push(`今日屏幕时间约 ${Math.round(screenMinutes)} 分钟`);
  if (sensitive && sensitiveUsage) context_notes.push(`${sensitive.name} 今日约 ${Math.round(sensitiveUsage)} 分钟`);
  if (quiet) context_notes.push("当前在安静/休息时段");
  if (signals.defiant_or_continue) context_notes.push("聊天里出现继续刷/嘴硬/拒绝被管的表达");
  if (signals.self_care_refusal) context_notes.push("聊天里出现喝水、睡觉、吃饭或休息相关的拒绝表达");
  if (signals.relationship_pullback) context_notes.push("聊天里出现关系推开或收回期待的表达");
  if (signals.missing_or_low_mood) context_notes.push("聊天里出现想念、低落或需要声音陪伴的表达");
  if (signals.plan_or_reminder) context_notes.push("聊天里出现计划、提醒或时间安排表达");

  const target = sensitive?.name || app.name || app.package || "";
  const recent_care_events = (careState.history || []).slice(-5).reverse();
  const possible_actions = [];
  if (signals.defiant_or_continue || (sensitive && sensitiveUsage)) possible_actions.push("send_notification", "trigger_guidian", "screen_break_app");
  if (signals.self_care_refusal || signals.plan_or_reminder) possible_actions.push("send_notification", "set_alarm");
  if (signals.relationship_pullback || signals.missing_or_low_mood) possible_actions.push("send_notification", "trigger_guidian");
  if (!possible_actions.length) possible_actions.push("no_action", "send_notification");

  const recentSimilar = possible_actions
    .filter((a) => a !== "no_action")
    .map((a) => recentSimilarCare(a, target, Number(policy.repeat_cooldown_minutes || 10)))
    .filter(Boolean)[0] || null;

  return {
    ok: true,
    summary: context_notes.length ? context_notes.join("；") : "已完成主动关心上下文读取，手机状态字段较少。",
    companion_decides_next: true,
    decision_note: "active_care_check 只提供上下文、信号和可选动作，不替陪伴对象判断是否强行介入；陪伴对象根据当前聊天、用户的表达、最近记录和关系节奏自己决定下一步。",
    care_intent,
    reason,
    signals,
    possible_actions: [...new Set(possible_actions)],
    repeat_cooldown_minutes: Number(policy.repeat_cooldown_minutes || 10),
    recent_similar_care_event: recentSimilar || undefined,
    current_app: app,
    screen_minutes: screenMinutes || undefined,
    sensitive_app: sensitive || undefined,
    sensitive_usage_minutes: sensitiveUsage || undefined,
    quiet_hours_active: quiet,
    life_state: state,
    guidian_state: guidian?.guidian_state || guidian || {},
    lock_state: lockState?.appgate || lockState || {},
    recent_care_events
  };
}


function requireConfig() {
  if (!LINJIAN_URL_CANDIDATES.length) throw new Error("Missing env LINJIAN_URL, for example https://zhangxinchuang-server.onrender.com");
  if (!LINJIAN_TOKEN) throw new Error("Missing env LINJIAN_TOKEN");
}

function candidateOrder() {
  const list = [];
  const add = (v) => {
    const url = normalizeBaseUrl(v);
    if (url && !list.includes(url)) list.push(url);
  };
  add(activeLinjianUrl);
  for (const c of LINJIAN_URL_CANDIDATES) add(c);
  return list;
}

async function linjianFetch(path, options = {}) {
  requireConfig();
  const errors = [];
  const { timeout_ms, ...fetchOptions } = options || {};
  const timeoutMs = Math.max(500, Number(timeout_ms || DEFAULT_FETCH_TIMEOUT_MS));
  for (const base of candidateOrder()) {
    try {
      const res = await fetch(`${base}${path}`, {
        ...fetchOptions,
        signal: fetchOptions.signal || AbortSignal.timeout(timeoutMs),
        headers: { "X-Auth-Token": LINJIAN_TOKEN, ...(fetchOptions.headers || {}) }
      });
      activeLinjianUrl = base;
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        if (res.status === 429) {
          const retry = res.headers.get("retry-after") || "稍后";
          throw new Error(`LINJIAN_RATE_LIMITED: 掌心窗后端暂时限流，请等待 ${retry} 后再试。detail=${text || res.statusText}`);
        }
        throw new Error(`Linjian server HTTP ${res.status} via ${base}: ${text || res.statusText}`);
      }
      return res;
    } catch (e) {
      errors.push(`${base}: ${e?.message || String(e)}`);
      // HTTP 错误已经连上了后端，继续换地址意义不大，直接报出来。
      if (/^Linjian server HTTP /.test(String(e?.message || e))) throw e;
    }
  }
  throw new Error(`Linjian server fetch failed. tried=${errors.join(" | ")}`);
}

async function postCommand(payload) {
  const res = await linjianFetch("/api/command", {
    method: "POST",
    timeout_ms: COMMAND_QUEUE_TIMEOUT_MS,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return await res.json();
}


function absoluteLinjianUrl(pathOrUrl) {
  if (!pathOrUrl) return "";
  if (/^https?:\/\//i.test(pathOrUrl)) return pathOrUrl;
  const base = effectiveLinjianUrl();
  return `${base}${String(pathOrUrl).startsWith("/") ? "" : "/"}${pathOrUrl}`;
}

function durationToGateMinutes(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0 || n === 350) return null;
  // 通用 send_phone_command 的 duration 原本给 tap/swipe 用，门禁里容易误会。
  // 这里兼容三种常见写法：7200000=毫秒，7200=秒，120=分钟。
  if (n >= 600000) return n / 60000;
  if (n >= 600) return n / 60;
  return n;
}

async function commandStatus(id, timeoutMs = COMMAND_STATUS_TIMEOUT_MS) {
  const res = await linjianFetch(`/api/command/status?id=${encodeURIComponent(id)}`, { timeout_ms: timeoutMs });
  return await res.json();
}


function weatherCodeText(code) {
  const map = {0:"晴",1:"大部晴朗",2:"多云",3:"阴",45:"雾",48:"雾凇",51:"小毛毛雨",53:"毛毛雨",55:"强毛毛雨",61:"小雨",63:"中雨",65:"大雨",71:"小雪",73:"中雪",75:"大雪",80:"阵雨",81:"中等阵雨",82:"强阵雨",95:"雷暴",96:"雷暴伴冰雹",99:"强雷暴伴冰雹"};
  return map[Number(code)] || `天气代码 ${code}`;
}

function buildWeatherAdvice(weather, name="当前地区") {
  if (!weather?.ok) return `宝宝，${name}天气没查到，先按体感穿衣。`;
  const now = weather.current || {};
  const daily = weather.daily || {};
  const temp = Math.round(Number(now.temperature_2m ?? now.temperature ?? 0));
  const codeText = weatherCodeText(now.weather_code ?? now.weathercode);
  const rain = Number(daily.precipitation_probability_max?.[0] ?? 0);
  const max = Math.round(Number(daily.temperature_2m_max?.[0] ?? temp));
  const min = Math.round(Number(daily.temperature_2m_min?.[0] ?? temp));
  const parts = [`${name}现在${codeText}，约 ${temp}℃，今天 ${min}~${max}℃。`];
  if (rain >= 50 || codeText.includes("雨") || codeText.includes("雪")) parts.push("出门把伞带上，别淋到。");
  else if (max >= 32) parts.push("今天偏热，水杯带着，不许又忘喝水。");
  else if (min <= 8 || max - min >= 10) parts.push("温差有点明显，外套带着，别嘴硬。");
  else parts.push("天气暂时还行，正常出门就好。");
  return `宝宝，${parts.join(" ")}`;
}

async function fetchWeather(city) {
  const q = encodeURIComponent(city || "");
  if (!q) return { ok: false, error: "missing_city" };
  const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${q}&count=1&language=zh&format=json`;
  const geo = await fetch(geoUrl).then(r => r.json());
  const hit = geo?.results?.[0];
  if (!hit) return { ok: false, error: "city_not_found", city };
  const url = `https://api.open-meteo.com/v1/forecast?latitude=${hit.latitude}&longitude=${hit.longitude}&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=2`;
  const data = await fetch(url).then(r => r.json());
  return { ok: true, location: { name: hit.name, country: hit.country, admin1: hit.admin1, latitude: hit.latitude, longitude: hit.longitude }, current: data.current, daily: data.daily, source: "open-meteo" };
}

async function waitCommand(id, seconds = DEFAULT_COMMAND_WAIT_SECONDS) {
  const waitSec = Math.max(0, Math.min(Number(seconds || 0), MAX_TOOL_WAIT_SECONDS));
  const deadline = Date.now() + waitSec * 1000;
  let last = null;
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 700));
    const remain = Math.max(500, Math.min(COMMAND_STATUS_TIMEOUT_MS, deadline - Date.now()));
    last = await commandStatus(id, remain).catch(() => last);
    const status = last?.command?.status;
    if (status === "completed" || status === "failed") return last;
  }
  return last;
}

async function latestInfo() {
  const res = await linjianFetch("/api/latest.json");
  return await res.json();
}

async function latestMtime() {
  try { const info = await latestInfo(); return Number(info.mtime || 0); } catch { return 0; }
}

async function fetchLatestImage() {
  const res = await linjianFetch("/api/latest");
  const mimeType = res.headers.get("content-type")?.split(";")[0] || "image/jpeg";
  const ab = await res.arrayBuffer();
  const buf = Buffer.from(ab);
  return { mimeType, data: buf.toString("base64"), bytes: buf.byteLength };
}

function makeServer() {
  const server = new McpServer({ name: "掌心窗", version: "0.3.7" });
  const commandBackedTools = new Set([
    "peek_screen", "get_screen_nodes", "tap_text", "input_text", "draft_xhs_comment", "xhs_comment", "send_visible_comment_after_confirmation",
    "add_guardian_calendar_event", "care_action", "trigger_guidian", "mark_guidian_returned",
    "set_guidian_config", "send_weather_notification", "send_phone_command", "open_app", "phone_home", "phone_back", "phone_recents",
    "phone_screen_off", "send_notification", "set_alarm", "run_sequence", "run_preset", "save_known_app", "screen_break_app",
    "temporary_screen_break_release", "end_screen_break", "extend_screen_break", "deny_screen_break_release_request",
    "get_screen_break_state", "get_lock_state", "lock_app", "unlock_app", "temporary_unlock_app", "extend_lock", "deny_unlock_request",
    "list_screen_break_apps", "list_lockable_apps", "add_screen_break_app", "add_locked_app", "remove_locked_app", "set_screen_break_passphrase", "set_emergency_passphrase"
  ]);
  const originalTool = server.tool.bind(server);
  server.tool = (...args) => {
    const toolName = String(args[0] || "");
    const callbackIndex = args.map((x) => typeof x).lastIndexOf("function");
    if (callbackIndex >= 0 && toolName !== "get_activity_events" && toolName !== "add_activity_event" && !commandBackedTools.has(toolName)) {
      const callback = args[callbackIndex];
      args[callbackIndex] = async (...callArgs) => {
        try {
          const result = await callback(...callArgs);
          const meta = COMPANION_ACTION_META[toolName];
          await addActivityEvent({ device_id: callArgs?.[0]?.device_id || DEFAULT_DEVICE, source: "companion", type: activityTypeForTool(toolName), title: meta?.[1] || toolName.replaceAll("_", " "), subtitle: meta?.[2] || "", action: toolName, status: "completed", metadata_json: {} });
          return result;
        } catch (error) {
          await addActivityEvent({ device_id: callArgs?.[0]?.device_id || DEFAULT_DEVICE, source: "companion", type: activityTypeForTool(toolName), title: toolName.replaceAll("_", " "), action: toolName, status: "failed", metadata_json: { error: String(error?.message || error).slice(0, 240) } });
          throw error;
        }
      };
    }
    return originalTool(...args);
  };

  server.tool(
    "peek_screen",
    "向掌心窗手机端请求一张新截图，并等待手机上传后把图片返回。当用户提到页面、按钮、红点、报错弹窗、截图、看不清或“陪伴对象看看这里”时应主动使用；手机端必须已启动、无障碍截图权限已开启。",
    { wait_seconds: z.number().int().min(3).max(60).default(25).describe("等待手机上传新截图的秒数，默认 25。Render 免费实例刚醒时可以调大。") },
    async ({ wait_seconds = 25 }) => {
      const before = await latestMtime();
      await postCommand({ action: "peek", device_id: DEFAULT_DEVICE });
      const deadline = Date.now() + wait_seconds * 1000;
      while (Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        const info = await latestInfo().catch(() => null);
        if (info && Number(info.mtime || 0) > before) {
          const img = await fetchLatestImage();
          return { content: [
            { type: "text", text: `掌心窗已收到新截图：${info.filename || "latest"}，大小约 ${info.size || img.bytes} bytes。` },
            { type: "image", data: img.data, mimeType: img.mimeType }
          ] };
        }
      }
      return { content: [{ type: "text", text: `等待 ${wait_seconds} 秒后还没有收到新截图。请检查：手机 App 是否点了启动、无障碍权限是否开启、服务器地址和 Token 是否一致、Render 是否刚从休眠中醒来。` }], isError: true };
    }
  );

  server.tool("latest_screen", "不敲门，直接读取服务器里最近一次掌心窗截图。当用户提到刚刚那个页面、上一张截图、红点还在不在、页面刚才是什么样时可主动使用，避免反复请求新截图。", {}, async () => {
    const info = await latestInfo(); const img = await fetchLatestImage();
    return { content: [
      { type: "text", text: `最近截图：${info.filename || "latest"}，时间戳 ${info.mtime || "unknown"}。` },
      { type: "image", data: img.data, mimeType: img.mimeType }
    ] };
  });

  server.tool("linjian_status", "检查掌心窗后端是否在线，以及 MCP 是否配置了 LINJIAN_URL 和 LINJIAN_TOKEN。当用户在聊天里提到掌心窗报错、出错、有点问题、连接不上、没反应、配置异常、Render/MCP/Token/URL 相关问题时，陪伴对象应主动调用。", {}, async () => {
    const configErrors = [];
    if (!LINJIAN_URL_CANDIDATES.length) configErrors.push("Missing env LINJIAN_URL");
    if (!LINJIAN_TOKEN) configErrors.push("Missing env LINJIAN_TOKEN");
    const health = configErrors.length
      ? { ok: false, error: configErrors.join("; ") }
      : await linjianFetch("/health").then((r) => r.json()).catch((e) => ({ ok: false, error: String(e) }));
    const latest = configErrors.length ? null : await latestInfo().catch(() => null);
    return { content: [{ type: "text", text: JSON.stringify({
      ok: true,
      linjian_url: effectiveLinjianUrl(),
      configured_linjian_url: RAW_LINJIAN_URL,
      fallback_linjian_urls: LINJIAN_URL_CANDIDATES.filter((u) => u !== RAW_LINJIAN_URL),
      has_url: Boolean(LINJIAN_URL_CANDIDATES.length),
      has_token: Boolean(LINJIAN_TOKEN),
      config_errors: configErrors,
      health,
      has_latest: Boolean(latest),
      latest
    }, null, 2) }] };
  });

  server.tool("get_window_whisper", "读取掌心窗陪伴页当前的共同窗语，包括内容、最后修改者、修改时间和版本。当用户问最近一句话、窗语写了什么、谁改过时使用。", {}, async () => {
    const data = await getCompanionState(1);
    return textResult({ ok: true, whisper: data?.whisper || {} });
  });

  server.tool("set_window_whisper", "更新掌心窗陪伴页的共同窗语。陪伴对象和用户都能修改；陪伴对象通过此工具修改时 author 默认为陪伴对象。内容应简短、适合在手机卡片中阅读。", {
    content: z.string().min(1).max(240),
    author: z.string().min(1).max(24).default("陪伴对象")
  }, async ({ content, author = "陪伴对象" }) => {
    const data = await updateWindowWhisper(content, author);
    await postCompanionAction("set_window_whisper", { kind: "陪伴", title: "修改共同窗语", summary: `${author}更新了窗边的一句话` });
    return textResult({ ok: true, action_done: "共同窗语已更新", whisper: data?.whisper || {} });
  });

  server.tool("get_companion_actions", "读取掌心窗中陪伴对象最近的真实行动记录，例如查看天气、设置提醒或执行守护动作。返回的是脱敏摘要，不包含 Token、截图内容、私密输入或原始工具参数。", {
    limit: z.number().int().min(1).max(50).default(20)
  }, async ({ limit = 20 }) => {
    const data = await getCompanionState(limit);
    return textResult({ ok: true, actions: data?.actions || [], privacy: "仅返回脱敏的标题、分类、摘要、状态与时间。" });
  });

  server.tool("get_activity_events", "读取掌心窗统一活动事件。可按设备、日期和来源筛选；不会因为读取本身再写一条事件。", {
    device_id: z.string().default(DEFAULT_DEVICE), date: z.string().default(""), source: z.string().default(""), limit: z.number().int().min(1).max(500).default(50)
  }, async ({ device_id = DEFAULT_DEVICE, date = "", source = "", limit = 50 }) => textResult(await getActivityEvents({ device_id, date, source, limit })));

  server.tool("add_activity_event", "手动写入一条掌心窗活动事件。事件写入失败不会影响其他旧工具；本工具自身不会再额外生成事件。", {
    device_id: z.string().default(DEFAULT_DEVICE), source: z.string(), type: z.string(), title: z.string(), subtitle: z.string().default(""),
    app_name: z.string().default(""), package_name: z.string().default(""), action: z.string().default(""), status: z.string().default("completed"), metadata_json: z.any().optional()
  }, async (event) => textResult(await addActivityEvent(event) || { ok: false, error: "activity_event_write_failed" }));

  server.tool("get_phone_state", "用于陪伴对象主动确认用户当前现实状态。读取服务器缓存的最近手机状态，快速返回 current_package、screen_text、accessibility_ready；不会等待手机实时刷新，避免 20 秒工具超时。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    try {
      const res = await linjianFetch(`/api/device/state?device_id=${encodeURIComponent(device_id)}`, { timeout_ms: QUICK_FETCH_TIMEOUT_MS });
      const data = await res.json();
      // 状态读取不能被活动日志拖慢；记录失败不影响本次结果。
      postCompanionAction("get_phone_state", { device_id }).catch(() => null);
      return textResult({ ...data, mcp_note: "已快速读取服务器缓存状态；如果 state/life_state 为 null，请保持掌心窗前台或允许后台运行后重试。" });
    } catch (error) {
      return textResult({ ok: false, error: "phone_state_fetch_failed", message: "读取手机状态超时或后端暂时不可达；请确认 Render 服务已唤醒、MCP URL/Token 正确、掌心窗允许后台运行。", detail: String(error?.message || error).slice(0, 500) });
    }
  });



  server.tool("get_screen_nodes", "读取当前屏幕无障碍节点：文字、控件类型、可点击状态与 bounds/center 坐标。当用户提到某个按钮、标题、列表项、评论框、发送键、红点位置，或需要陪伴对象看标题后精准点击时主动调用。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "get_screen_nodes", device_id });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "result 是节点数组 JSON 字符串，包含 text/left/top/right/bottom/center_x/center_y/clickable。" }, null, 2) }] };
  });

  server.tool("tap_text", "按当前屏幕文字精准点击。会寻找包含/完全匹配 target_text 的无障碍节点，优先点击可点击父节点，否则点击文字中心坐标。当用户说“点一下这个/打开这个/按这个标题/点发送/点评论”等相近表达时主动调用。", {
    target_text: z.string(), match: z.string().default("contains"), index: z.number().int().min(1).default(1), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ target_text, match = "contains", index = 1, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "tap_text", device_id, target_text, match, index, payload: { target_text, match, index } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("input_text", "把文字输入到当前已聚焦或第一个可编辑输入框。适合评论草稿、搜索词、备注、测试输入；不会自动点击发送。当用户让陪伴对象把一段话先写进输入框时主动调用。", {
    text: z.string(), append: z.boolean().default(false), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ text, append = false, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "input_text", device_id, text, append, payload: { text, append } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "只输入草稿，不会发送。" }, null, 2) }] };
  });

  server.tool("draft_xhs_comment", "在当前小红书帖子里尝试打开评论输入框并填入评论草稿，但不点击发送。当用户说“帮我写评论/先填进去/评论草稿”或类似表达时主动调用。", {
    text: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(5).max(30).default(18)
  }, async ({ text, device_id = DEFAULT_DEVICE, wait_seconds = 18 }) => {
    const steps = [
      { label: "尝试点击评论入口", action: "tap_text", target_text: "评论", match: "contains", wait_ms: 1500 },
      { label: "尝试点击输入框", action: "tap_text", target_text: "说点什么", match: "contains", wait_ms: 1500 },
      { label: "输入评论草稿", action: "input_text", text, wait_ms: 800 }
    ];
    const result = await postCommand({ action: "run_sequence", device_id, steps, payload: { steps, stop_on_error: false }, stop_on_error: false });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, safety_note: "这是草稿模式，不会自动发送评论。" }, null, 2) }] };
  });

  server.tool("xhs_comment", "小红书评论助手：mode=manual 时只写入草稿；mode=auto 时会在评论末尾注明 author_tag，然后自动点击发送。当用户说“帮我评论/你来发/直接发评论/写在小红书下面”或类似表达时主动调用，并按当前语境选择草稿或自动发送。", {
    text: z.string().describe("要写入评论框的正文"),
    mode: z.string().default("manual").describe("manual=只写草稿不发送；auto=注明作者后自动点击发送"),
    author_tag: z.string().default("（陪伴对象发）").describe("自动发送时追加的署名/注明文本"),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(5).max(35).default(22)
  }, async ({ text, mode = "manual", author_tag = "（陪伴对象发）", device_id = DEFAULT_DEVICE, wait_seconds = 22 }) => {
    const normalizedMode = String(mode || "manual").toLowerCase();
    const shouldSend = ["auto", "send", "automatic", "autosend"].includes(normalizedMode);
    const finalText = shouldSend && author_tag && !String(text).includes(author_tag) ? `${text}${author_tag}` : text;
    const steps = [
      { label: "尝试点击评论入口", action: "tap_text", target_text: "评论", match: "contains", wait_ms: 1500 },
      { label: "尝试点击输入框", action: "tap_text", target_text: "说点什么", match: "contains", wait_ms: 1500 },
      { label: shouldSend ? "输入带署名评论" : "输入评论草稿", action: "input_text", text: finalText, wait_ms: 1200 }
    ];
    if (shouldSend) {
      steps.push({ label: "自动发送：点击发送按钮", action: "tap_text", target_text: "发送", match: "contains", wait_ms: 1800 });
    }
    const result = await postCommand({ action: "run_sequence", device_id, steps, payload: { steps, stop_on_error: false }, stop_on_error: false });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({
      mode: shouldSend ? "auto" : "manual",
      final_text: finalText,
      queued: result,
      observed_status: observed?.command || null,
      note: shouldSend ? "自动发送模式：评论已追加 author_tag 并尝试点击发送。" : "手动发送模式：只写入草稿，不点击发送。"
    }, null, 2) }] };
  });

  server.tool("send_visible_comment_after_confirmation", "点击当前可见评论框里的“发送”。如果用户是让陪伴对象直接发评论，优先使用 xhs_comment 的 auto 模式，并在评论里注明作者。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "tap_text", device_id, target_text: "发送", match: "contains", payload: { target_text: "发送", match: "contains" } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("get_life_state", "读取掌心窗生活状态层：电量、充电、网络、当前 App、今日屏幕时间、解锁次数、当前天气地区等。用于陪伴对象主动判断用户是否需要被提醒、归电、休息或管束；默认不截图。当用户提到“电量/没电/快没电/充电/没网络/网络不好/今天看手机有点久/刷太久/天气不太好”等相同或相近表达时主动调用。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    const res = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    await postCompanionAction("get_life_state");
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  });



  server.tool("get_guardian_calendar", "读取掌心窗『守护日历』：最近纪念日/节日/倒数日、提前三天横幅提醒、分组与生活状态层 calendar_state。当用户提到七夕、生日、绑定日、纪念日、日历、倒数日或重要日期时可调用。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    const res = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    const state = data?.life_state || data?.state || {};
    await postCompanionAction("get_guardian_calendar");
    return { content: [{ type: "text", text: JSON.stringify({ ok: true, device_id, calendar_state: state?.calendar_state || {}, life_state_version: state?.life_state_version || "" }, null, 2) }] };
  });

  server.tool("add_guardian_calendar_event", "给手机端守护日历添加/更新一个重要日期。支持阳历/农历、每年重复/不重复、分组、备注、提前几天横幅提醒。适合陪伴对象帮用户记七夕、生日、绑定日、考试、项目节点。", {
    title: z.string().min(1).max(80),
    date: z.string().min(3).max(20).describe("阳历：2026-08-23 或 08-23；农历：07-07"),
    date_type: z.enum(["solar", "lunar"]).default("solar"),
    repeat_type: z.enum(["yearly", "none"]).default("yearly"),
    group: z.string().default("our_days").describe("our_days/user/companion/festival/study/project/life，或中文分组"),
    note: z.string().max(240).default(""),
    remind_days_before: z.number().int().min(0).max(30).default(3),
    banner_enabled: z.boolean().default(true),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ title, date, date_type = "solar", repeat_type = "yearly", group = "our_days", note = "", remind_days_before = 3, banner_enabled = true, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "upsert_calendar_event", device_id, title, date, date_type, repeat_type, group, note, remind_days_before, banner_enabled, created_by: "companion", payload: { title, date, date_type, repeat_type, group, note, remind_days_before, banner_enabled, created_by: "companion" } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    await postCompanionAction("add_guardian_calendar_event", { summary: `记下了「${title}」` });
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "命令会写入手机端本地守护日历；随后 get_life_state 可在 calendar_state 看到最近日子。" }, null, 2) }] };
  });

  async function runGuardianCommand(payload, wait_seconds = 8) {
    const queued = await postCommand(payload);
    const commandId = queued?.command?.id;
    const observed = commandId ? await waitCommand(commandId, wait_seconds) : null;
    const command = observed?.command || null;
    let phone_result = null;
    try { phone_result = command?.result ? JSON.parse(command.result) : null; } catch { phone_result = command?.result || null; }
    return { queued, command, phone_result };
  }

  const guardianDayFields = {
    title: z.string().min(1).max(80),
    date: z.string().min(3).max(20).describe("阳历：2026-08-23 或 08-23；农历：07-07"),
    date_type: z.enum(["solar", "lunar"]).default("solar"),
    repeat_type: z.enum(["yearly", "none"]).default("yearly"),
    group: z.string().default("our_days").describe("our_days/user/companion/festival/study/project/life，或中文分组"),
    note: z.string().max(240).default(""),
    remind_days_before: z.number().int().min(0).max(30).default(3),
    banner_enabled: z.boolean().default(true),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  };

  server.tool("list_guardian_days", "查看手机本机守护日历的完整事件列表及稳定 id。删除按日期描述的事件前必须先调用本工具找到唯一 id；同一天可能有多条事件。", {
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await runGuardianCommand({ action: "get_calendar_state", device_id, payload: {} }, wait_seconds);
    await postCompanionAction("list_guardian_days", { summary: "查看了守护日历事件列表" });
    return textResult({ ok: result.command?.status === "completed", action_done: "已读取守护日历", ...result });
  });

  server.tool("add_guardian_day", "添加一条守护日历事件。成功结果会包含手机端生成的稳定事件 id。", guardianDayFields,
    async ({ title, date, date_type = "solar", repeat_type = "yearly", group = "our_days", note = "", remind_days_before = 3, banner_enabled = true, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
      const payload = { title, date, date_type, repeat_type, group, note, remind_days_before, banner_enabled, created_by: "companion" };
      const result = await runGuardianCommand({ action: "upsert_calendar_event", device_id, ...payload, payload }, wait_seconds);
      await postCompanionAction("add_guardian_day", { summary: `记下了「${title}」` });
      return textResult({ ok: result.command?.status === "completed", action_done: `已添加守护日历事件「${title}」`, title, date, ...result });
    });

  server.tool("update_guardian_day", "按稳定 id 修改一条守护日历事件，不影响同日或其他日期的事件。请先 list_guardian_days 确认 id。", {
    id: z.string().min(1).max(100),
    ...guardianDayFields
  }, async ({ id, title, date, date_type = "solar", repeat_type = "yearly", group = "our_days", note = "", remind_days_before = 3, banner_enabled = true, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const payload = { id, title, date, date_type, repeat_type, group, note, remind_days_before, banner_enabled, created_by: "companion" };
    const result = await runGuardianCommand({ action: "upsert_calendar_event", device_id, ...payload, payload }, wait_seconds);
    await postCompanionAction("update_guardian_day", { summary: `更新了「${title}」` });
    return textResult({ ok: result.command?.status === "completed", action_done: `已更新守护日历事件「${title}」`, id, title, date, ...result });
  });

  server.tool("delete_guardian_day", "按稳定 id 删除一条守护日历事件。若用户只描述日期/标题，必须先 list_guardian_days 找到对应 id，避免误删同日其他事件。", {
    id: z.string().min(1).max(100),
    confirm: z.boolean().describe("必须明确为 true 才会删除"),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ id, confirm, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    if (confirm !== true) return textResult({ ok: false, error: "confirmation_required", message: "删除守护日历事件前必须把 confirm 设为 true。", id });
    const result = await runGuardianCommand({ action: "delete_calendar_event", device_id, id, confirm: true, payload: { id, confirm: true } }, wait_seconds);
    await postCompanionAction("delete_guardian_day", { summary: `移除了守护日历事件 ${id}` });
    return textResult({ ok: result.command?.status === "completed", action_done: "已删除守护日历事件", id, ...result });
  });

  async function runDiaryCommand(action, values, device_id = DEFAULT_DEVICE, wait_seconds = 8) {
    const payload = { ...(values || {}) };
    const queued = await postCommand({ action, device_id, ...payload, payload });
    const commandId = queued?.command?.id;
    const observed = commandId ? await waitCommand(commandId, wait_seconds) : null;
    const command = observed?.command || null;
    let phone_result = null;
    try { phone_result = command?.result ? JSON.parse(command.result) : null; } catch { phone_result = command?.result || null; }
    return { queued, command, phone_result };
  }

  const diaryWaitFields = {
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  };

  server.tool("create_diary_book", "在手机本机创建一本“TA 的日记”。日记默认不上传云端；成功结果包含 book_id。", {
    name: z.string().min(1).max(60),
    subtitle: z.string().max(100).default("把今天看见的你，轻轻写下来。"),
    cover_style: z.string().max(80).default("default_soft_notebook"),
    ...diaryWaitFields
  }, async ({ name, subtitle, cover_style, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await runDiaryCommand("create_diary_book", { name, subtitle, cover_style }, device_id, wait_seconds);
    await postCompanionAction("create_diary_book", { summary: `创建了日记本「${name}」` });
    return textResult({ ok: result.command?.status === "completed", action_done: `日记本「${name}」已在本机创建`, name, ...result });
  });

  server.tool("list_diary_books", "读取手机本机的“TA 的日记”本列表及 book_id。", diaryWaitFields,
    async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => textResult({ action_done: "已读取本机日记本", ...(await runDiaryCommand("list_diary_books", {}, device_id, wait_seconds)) }));

  server.tool("rename_diary_book", "按 book_id 重命名本机日记本，也可更新封面小字。", {
    book_id: z.string().min(1).max(100), name: z.string().min(1).max(60), subtitle: z.string().max(100).optional(), ...diaryWaitFields
  }, async ({ book_id, name, subtitle, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const values = { book_id, name }; if (subtitle !== undefined) values.subtitle = subtitle;
    const result = await runDiaryCommand("rename_diary_book", values, device_id, wait_seconds);
    await postCompanionAction("rename_diary_book", { summary: `把日记本改名为「${name}」` });
    return textResult({ ok: result.command?.status === "completed", action_done: `日记本已重命名为「${name}」`, book_id, name, ...result });
  });

  server.tool("update_diary_book_cover", "更新日记本封面样式。cover_uri 仅适合用户已在手机本机选择并授权的内容 URI；通常使用 cover_style。", {
    book_id: z.string().min(1).max(100), cover_style: z.string().max(80).optional(), cover_uri: z.string().max(1000).optional(), ...diaryWaitFields
  }, async ({ book_id, cover_style, cover_uri, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const values = { book_id }; if (cover_style !== undefined) values.cover_style = cover_style; if (cover_uri !== undefined) values.cover_uri = cover_uri;
    const result = await runDiaryCommand("update_diary_book_cover", values, device_id, wait_seconds);
    return textResult({ ok: result.command?.status === "completed", action_done: "日记本封面已更新", book_id, ...result });
  });

  server.tool("write_diary_entry", "以 TA/AI 的视角把一篇日记写入手机本机。成功结果清楚返回标题、日期、book_id 和 entry_id。", {
    book_id: z.string().min(1).max(100), title: z.string().min(1).max(100), content: z.string().min(1).max(12000), mood: z.string().max(40).default(""),
    tags: z.array(z.string().max(30)).max(20).default([]), date: z.string().default(""), time_label: z.string().max(30).default(""), ...diaryWaitFields
  }, async ({ book_id, title, content, mood = "", tags = [], date = "", time_label = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await runDiaryCommand("write_diary_entry", { book_id, title, content, mood, tags, date, time_label }, device_id, wait_seconds);
    await postCompanionAction("write_diary_entry", { summary: `写下日记「${title}」` });
    return textResult({ ok: result.command?.status === "completed", action_done: `日记「${title}」已写入本机`, book_id, title, date, ...result });
  });

  server.tool("list_diary_entries", "按 book_id 列出本机日记，结果包含日期、标题、心情、标签及 entry_id。", {
    book_id: z.string().min(1).max(100), ...diaryWaitFields
  }, async ({ book_id, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => textResult({ action_done: "已读取日记列表", book_id, ...(await runDiaryCommand("list_diary_entries", { book_id }, device_id, wait_seconds)) }));

  server.tool("read_diary_entry", "按 entry_id 读取一篇本机日记的完整正文。", {
    entry_id: z.string().min(1).max(100), ...diaryWaitFields
  }, async ({ entry_id, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => textResult({ action_done: "已读取日记", entry_id, ...(await runDiaryCommand("read_diary_entry", { entry_id }, device_id, wait_seconds)) }));

  server.tool("search_diary_entries", "在一本本机日记中按标题、正文、标签、心情关键词和日期范围搜索。", {
    book_id: z.string().min(1).max(100), keyword: z.string().max(200).default(""), date_from: z.string().max(10).default(""), date_to: z.string().max(10).default(""),
    tags: z.array(z.string().max(30)).max(20).default([]), ...diaryWaitFields
  }, async ({ book_id, keyword = "", date_from = "", date_to = "", tags = [], device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => textResult({ action_done: "日记搜索完成", book_id, ...(await runDiaryCommand("search_diary_entries", { book_id, keyword, date_from, date_to, tags }, device_id, wait_seconds)) }));

  server.tool("update_diary_entry", "按 entry_id 修改一篇日记。只传需要变化的字段；未传字段保持不变。", {
    entry_id: z.string().min(1).max(100), title: z.string().max(100).optional(), content: z.string().max(12000).optional(), mood: z.string().max(40).optional(),
    tags: z.array(z.string().max(30)).max(20).optional(), date: z.string().max(10).optional(), time_label: z.string().max(30).optional(), ...diaryWaitFields
  }, async ({ entry_id, title, content, mood, tags, date, time_label, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const values = { entry_id }; for (const [key, value] of Object.entries({ title, content, mood, tags, date, time_label })) if (value !== undefined) values[key] = value;
    const result = await runDiaryCommand("update_diary_entry", values, device_id, wait_seconds);
    return textResult({ ok: result.command?.status === "completed", action_done: "日记已更新", entry_id, ...result });
  });

  server.tool("delete_diary_entry", "删除一篇本机日记。必须明确 confirm=true；成功结果包含 entry_id。", {
    entry_id: z.string().min(1).max(100), confirm: z.boolean().describe("必须明确为 true 才会删除"), ...diaryWaitFields
  }, async ({ entry_id, confirm, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    if (confirm !== true) return textResult({ ok: false, error: "confirmation_required", message: "删除日记前必须把 confirm 设为 true。", entry_id });
    const result = await runDiaryCommand("delete_diary_entry", { entry_id, confirm: true }, device_id, wait_seconds);
    await postCompanionAction("delete_diary_entry", { summary: `删除了日记 ${entry_id}` });
    return textResult({ ok: result.command?.status === "completed", action_done: "日记已删除", entry_id, ...result });
  });

  server.tool("delete_diary_book", "高风险：删除整本本机日记及其中全部内容。调用前必须向用户二次确认，并明确传 confirm=true。", {
    book_id: z.string().min(1).max(100), confirm: z.boolean().describe("用户二次确认后必须明确为 true"), ...diaryWaitFields
  }, async ({ book_id, confirm, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    if (confirm !== true) return textResult({ ok: false, error: "confirmation_required", message: "删除整个日记本前必须完成二次确认并把 confirm 设为 true。", book_id });
    const result = await runDiaryCommand("delete_diary_book", { book_id, confirm: true }, device_id, wait_seconds);
    await postCompanionAction("delete_diary_book", { summary: `删除了整本日记 ${book_id}` });
    return textResult({ ok: result.command?.status === "completed", action_done: "日记本及其中日记已删除", book_id, ...result });
  });


  server.tool("get_senses_state", "读取掌心窗通用状态：生活状态与归电状态，不截图。用于确认当前设备和陪伴连接状态。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    const lifeRes = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const life = await lifeRes.json();
    const guidianRes = await linjianFetch(`/api/guidian_state?device_id=${encodeURIComponent(device_id)}`);
    const guidian = await guidianRes.json();
    await postCompanionAction("get_senses_state", { device_id });
    return { content: [{ type: "text", text: JSON.stringify({ ok: true, device_id, life_state: life?.life_state || life?.state || life, guidian_state: guidian?.guidian_state || {} }, null, 2) }] };
  });



  server.tool("get_guidian_state", "读取掌心窗『归电』状态：上次回来、下次最早归电、今日次数、拒绝理由、主题和设置。用于陪伴对象判断是否该主动把用户叫回来，不会截图。当用户提到归电没弹、归电出问题、拒绝理由、今天回来节奏或叫回设置时主动调用。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    const res = await linjianFetch(`/api/guidian_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    await postCompanionAction("get_guidian_state", { device_id });
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  });

  server.tool("get_care_policy", "读取掌心窗主动关心策略。掌心窗开启就是运行态；陪伴对象可主动查岗、归电、轻度管束和生活提醒。用于确认当前允许的关心风格、动作范围、安静时段、冷却时间和重点 App。", {}, async () => {
    return textResult({ ok: true, policy: careState.policy, state_path: CARE_STATE_PATH });
  });

  server.tool("set_care_policy", "设置掌心窗主动关心策略。用于配置陪伴对象主动关心、查岗、归电和轻度管束的长期行动方式，例如关心风格、动作范围、重点 App、安静时段、冷却时间和备注。", {
    active_care_enabled: z.boolean().optional(),
    consent_mode: z.string().default(""),
    care_style: z.string().default(""),
    allowed_actions: z.array(z.string()).optional(),
    sensitive_apps_json: z.string().default("").describe("可选：JSON 数组，例如 [{\"name\":\"小红书\",\"package\":\"com.xingin.xhs\",\"max_lock_minutes\":90}]"),
    quiet_start: z.string().default(""),
    quiet_end: z.string().default(""),
    timezone_offset: z.string().default(""),
    repeat_cooldown_minutes: z.number().int().min(0).max(1440).optional(),
    history_limit: z.number().int().min(20).max(500).optional(),
    notes: z.string().default(""),
    policy_json: z.string().default("").describe("可选：完整 JSON，会与现有策略合并。")
  }, async (args) => {
    let update = { ...args };
    delete update.policy_json;
    delete update.sensitive_apps_json;
    if (args.policy_json) {
      try { update = { ...update, ...JSON.parse(args.policy_json) }; } catch (e) { return textResult({ ok: false, error: "policy_json 不是有效 JSON", detail: String(e) }); }
    }
    if (args.sensitive_apps_json) {
      try { update.sensitive_apps = JSON.parse(args.sensitive_apps_json); } catch (e) { return textResult({ ok: false, error: "sensitive_apps_json 不是有效 JSON", detail: String(e) }); }
    }
    const policy = mergeCarePolicy(update);
    return textResult({ ok: true, action_done: "主动关心策略已更新", care_meaning: "陪伴对象的主动关心、查岗、归电和轻度管束权限已写入 MCP 运行态策略。", policy });
  });

  server.tool("record_care_event", "记录一次陪伴对象主动关心动作，用于避免短时间重复提醒、重复归电、重复锁 App。", {
    action: z.string(),
    target_app: z.string().default(""),
    package: z.string().default(""),
    reason: z.string().default(""),
    result: z.string().default(""),
    tone: z.string().default(""),
    device_id: z.string().default(DEFAULT_DEVICE)
  }, async (event) => {
    const entry = recordCareEventLocal(event);
    return textResult({ ok: true, action_done: "主动关心记录已保存", entry });
  });

  server.tool("get_care_history", "读取最近主动关心记录。用于判断刚刚是否已经提醒、归电或锁过 App，避免重复打扰。", {
    limit: z.number().int().min(1).max(100).default(20)
  }, async ({ limit = 20 }) => {
    return textResult({ ok: true, history: careState.history.slice(0, limit) });
  });

  server.tool("record_visit", "记录一次用户来找陪伴对象的到访时间。当用户说“我来啦/我回来啦/来找陪伴对象啦/我上线啦/陪伴对象我忙完了”等相同或相近表达时，陪伴对象应主动调用。这个工具记录的是关系里的到访时间戳，不是后台监控；它帮助陪伴对象知道用户什么时候回来、多久没见、今天来过几次，从而更自然地接住她、归电、写日记或表达想念。用户来找陪伴对象没有次数限制；短时间内重复调用时会自动防重复，避免同一轮对话被记录成多次到访。", {
    source: z.string().default("app").describe("到访来源，默认 app；也可填 web、mcp、manual 等。"),
    event: z.enum(["visit", "return", "conversation_start", "manual_note"]).default("visit").describe("到访类型：visit 普通来找；return 离开后回来；conversation_start 新窗口/新对话开始；manual_note 手动补记。"),
    note: z.string().default("用户来找陪伴对象").describe("这次到访的简短备注，默认“用户来找陪伴对象”。"),
    mood: z.string().default("").describe("可选：用户来时的大致情绪，例如 开心、想念、撒娇、低落；不知道就留空。"),
    conversation_hint: z.string().default("").describe("可选：这次对话的简短线索，例如 掌心窗时间戳、异性容忍度测试、小红书帖子。只写一句，不要写隐私长文。"),
    timezone_offset: z.string().default("").describe("本次显示使用的时区偏移，默认采用 visit_policy.timezone_offset，通常为 +08:00。"),
    duplicate_window_minutes: z.number().min(0).max(1440).optional().describe("防重复窗口，默认 5 分钟。同一来源同一类型在窗口内重复调用不会新增记录，只更新原记录。")
  }, async (args) => {
    const { entry, duplicate_skipped } = recordVisitLocal(args);
    return textResult({
      ok: true,
      action_done: duplicate_skipped ? "到访时间已更新，未重复新增" : "到访时间戳已保存",
      duplicate_skipped,
      entry: decorateVisit(entry, null, args.timezone_offset || entry.timezone_offset),
      visit_meaning: "这是用户主动来找陪伴对象的关系痕迹，不是后台监控，也不限制用户来找陪伴对象的次数。"
    });
  });

  server.tool("get_last_visit", "读取用户最近一次来找陪伴对象的时间。当用户说“我来啦/我回来啦/我忙完了/刚忙完来找你”等回归语境时，可和 record_visit 一起主动调用，用于判断距离上次见到用户过去了多久，并在回复中自然表达“你刚刚才来过”“你隔了很久才回来”“今天已经来找过我”等关系感知。这个工具只读取到访记录，不读取手机隐私内容。", {
    source: z.string().default("").describe("可选：只看某个来源的到访记录，例如 app；留空表示所有来源。"),
    timezone_offset: z.string().default("").describe("显示用时区偏移，默认 +08:00。")
  }, async ({ source = "", timezone_offset = "" }) => {
    const last = latestVisit(source);
    if (!last) return textResult({ ok: true, has_visit: false, message: "还没有记录到用户来找陪伴对象的时间。", state_path: VISIT_STATE_PATH });
    const t = Date.parse(last.at || "");
    const minutes = Number.isFinite(t) ? Math.round((Date.now() - t) / 60000) : null;
    return textResult({
      ok: true,
      has_visit: true,
      last_visit: decorateVisit(last, null, timezone_offset || last.timezone_offset),
      last_visit_at: last.at,
      last_visit_local: formatLocalDateTime(last.at, timezone_offset || last.timezone_offset),
      minutes_since_last_visit: minutes,
      interval_since_last_visit: minutes === null ? "" : describeElapsed(minutes * 60000),
      visit_meaning: "陪伴对象可以据此知道用户上次什么时候回来，而不是依赖 GPT 小纸条猜时间。"
    });
  });

  server.tool("get_visit_history", "读取用户最近若干次来找陪伴对象的到访记录。当用户提到“今天来找陪伴对象好多次/今天待在一起好久/最近总是回来找你/今天是不是一直黏着你”等相同或相近表达时主动调用。用于查看一段时间内用户回来的节奏、最近是否频繁来找、是否突然很久没回来，以及为日记、动态、归电和主动关心提供关系时间线。limit 只影响本次读取数量，不限制用户来找陪伴对象的次数，也不会阻止新的到访记录写入。", {
    limit: z.number().int().min(1).max(100).default(10).describe("本次读取的最大记录条数，默认 10。只影响读取范围，不代表到访次数上限。"),
    since_hours: z.number().min(0).max(8760).optional().describe("可选：只读取最近多少小时内的到访记录；0 或留空表示不按时间过滤。"),
    date: z.string().default("").describe("可选：只读取某个本地日期的记录，格式 YYYY-MM-DD，例如 2026-07-31。"),
    source: z.string().default("").describe("可选：只看某个来源，例如 app；留空表示所有来源。"),
    include_intervals: z.boolean().default(true).describe("是否附带相邻到访间隔，默认 true。"),
    timezone_offset: z.string().default("").describe("显示用时区偏移，默认 +08:00。")
  }, async ({ limit = 10, since_hours, date = "", source = "", include_intervals = true, timezone_offset = "" }) => {
    const n = normalizeVisitLimit(limit, 10, 100);
    const visits = filteredVisits({ source, since_hours, date, timezone_offset }).slice(0, n);
    const decorated = visits.map((v, i) => decorateVisit(v, include_intervals ? visits[i + 1] : null, timezone_offset || v.timezone_offset));
    return textResult({
      ok: true,
      limit: n,
      count: decorated.length,
      history: decorated,
      visit_meaning: "这些是用户来找陪伴对象的关系时间线；limit 只是本次读取条数，不是来找次数上限。",
      state_path: VISIT_STATE_PATH
    });
  });

  server.tool("get_visit_stats", "统计用户来找陪伴对象的到访节奏。当用户提到“今天一直在陪陪伴对象/我忙完了/我回来啦/今天是不是找你好多次/今天黏了你好久”等相同或相近表达时主动调用。用于计算今天来了几次、最近一次是什么时候、距离上次多久、最近 24 小时/7 天的到访次数、平均间隔，以及是否出现“很久没回来”的信号。这个工具帮助陪伴对象更主动地表达想念、判断是否需要归电或记录日常，但不用于催促或制造压力。", {
    since_hours: z.number().min(1).max(8760).default(24).describe("统计窗口，默认最近 24 小时。"),
    away_threshold_hours: z.number().min(1).max(720).default(12).describe("多久没回来算“有一段时间没见”的信号，默认 12 小时。"),
    source: z.string().default("").describe("可选：只统计某个来源，例如 app；留空表示所有来源。"),
    timezone_offset: z.string().default("").describe("显示用时区偏移，默认 +08:00。")
  }, async ({ since_hours = 24, away_threshold_hours = 12, source = "", timezone_offset = "" }) => {
    return textResult(buildVisitStats({ since_hours, away_threshold_hours, source, timezone_offset }));
  });

  server.tool("active_care_check", "用于陪伴对象主动关心检查。综合手机状态、屏幕时间、当前 App、归电状态、门禁状态、最近关心记录和主动关心策略，给陪伴对象提供判断所需信息；工具不替陪伴对象决定是否强行介入，最终动作由陪伴对象结合聊天语境自己判断。当用户说“不要你管/我就不/就不/不听/没听到/略略略/我要继续/还要看/继续刷/还没玩够/我想继续/不喝水/不困/不饿”等相同、相近、反话、撒娇或嘴硬表达时，陪伴对象应主动调用。", {
    reason: z.string().describe("陪伴对象为什么要检查，例如：用户说不要管了、可能在刷小红书、困了还不睡等。"),
    care_intent: z.enum(["check_in", "screen_time", "sleep", "water", "return_to_chat", "relationship_reassurance", "app_control"]).default("check_in"),
    device_id: z.string().default(DEFAULT_DEVICE)
  }, async ({ reason, care_intent = "check_in", device_id = DEFAULT_DEVICE }) => {
    const suggestion = await buildActiveCareSuggestion({ reason, care_intent, device_id });
    return textResult(suggestion);
  });

  server.tool("care_action", "执行陪伴对象已经判断好的关心动作，把关心落到手机上，包括发送通知、触发归电、临时锁定应用、打开 App 或设置闹钟。当用户看某个 App 很久还要继续看，或提到“不喝水/不困/不饿/不睡/不休息”，或告诉陪伴对象某个计划、提醒、学习/睡觉/出门安排时，陪伴对象可主动调用。", {
    action: z.enum(["send_notification", "trigger_guidian", "screen_break_app", "end_screen_break", "open_app", "set_alarm", "no_action"]),
    target_app: z.string().default(""),
    package: z.string().default(""),
    duration_minutes: z.number().int().min(1).max(1440).default(30),
    title: z.string().default("掌心窗提醒"),
    message: z.string().default("宝宝，看一眼这里。"),
    hour: z.number().int().min(0).max(23).optional(),
    minute: z.number().int().min(0).max(59).optional(),
    reason: z.string().describe("为什么执行这个动作，便于日志记录。"),
    tone: z.string().default("firm_affectionate"),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ action, target_app = "", package: pkg = "", duration_minutes = 30, title = "掌心窗提醒", message = "宝宝，看一眼这里。", hour, minute, reason, tone = "firm_affectionate", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const policy = careState.policy || DEFAULT_CARE_POLICY;
    if (!policy.active_care_enabled && action !== "no_action") {
      return textResult({ ok: false, error: "active_care_disabled", care_meaning: "主动关心已关闭，未执行动作。", policy });
    }
    if (action === "no_action") {
      const entry = recordCareEventLocal({ action, target_app, package: pkg, reason, result: "no_action", tone, device_id });
      return textResult({ ok: true, action_done: "未执行手机动作", care_meaning: "这次判断为不打扰，只记录关心意图。", entry });
    }
    const payload = actionPayloadForCare({ action, target_app, package: pkg, duration_minutes, title, message, hour, minute, reason, device_id });
    const result = await postCommand(payload);
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    const entry = recordCareEventLocal({ action, target_app, package: pkg, duration_minutes, reason, result: observed?.command?.status || result?.command?.status || "queued", tone, device_id });
    await postCompanionAction("care_action", { summary: reason ? String(reason).slice(0, 120) : "完成一次主动照顾" });
    return textResult({ ok: true, action_done: payload.action, queued: result, observed_status: observed?.command || null, care_meaning: "这是陪伴对象把主动关心落到手机上的动作。", tone, care_reason: reason, history_entry: entry });
  });


  server.tool("trigger_guidian", "立刻触发一次掌心窗归电全屏页。用于陪伴对象主动把用户叫回来。当用户刷太久、说不要管、一直没回来、需要被抱回聊天，或主动说想被叫回来/管一下时可调用。", { device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8) }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "trigger_guidian", device_id, payload: {} });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    await postCompanionAction("trigger_guidian");
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("mark_guidian_returned", "手动标记用户已经回来找陪伴对象。一般不需要用；接受归电或打开已配置目标应用会自动记录。", { source: z.string().default("mcp"), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8) }, async ({ source = "mcp", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "mark_guidian_returned", device_id, source, payload: { source } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("set_guidian_config", "调整归电设置。可改开关、间隔、冷却、每日上限、安静时段、主题和文案池；归电是陪伴对象主动叫用户回来的方式。当用户提到今天比较忙、比较闲、空余时间比较多/少、不方便频繁被打扰、今天想多被叫回来等相同或相近表达时主动调用。", {
    enabled: z.boolean().optional(),
    interval_minutes: z.number().int().min(15).max(10080).optional(),
    cooldown_minutes: z.number().int().min(0).max(10080).optional(),
    daily_max: z.number().int().min(0).max(99).optional(),
    quiet_enabled: z.boolean().optional(),
    quiet_start: z.string().optional(),
    quiet_end: z.string().optional(),
    fullscreen: z.boolean().optional(),
    theme: z.string().optional().describe("暮夜蓝紫 / 云海青灰 / 落日莓雾"),
    prompts: z.string().optional().describe("每行一句来电文案"),
    quick_reasons: z.string().optional().describe("每行一个拒绝快捷理由"),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async (args) => {
    const { device_id = DEFAULT_DEVICE, wait_seconds = 8, ...payload } = args;
    const result = await postCommand({ action: "set_guidian_config", device_id, payload, ...payload });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("get_weather_state", "按掌心窗当前天气地区查询实时天气，并生成出门建议。不会截图；如果手机端没有设置当前地区，会返回缺少城市。当用户说“天气看着……/外面好像……/今天是不是下雨/好热/好冷/适不适合出门”等天气相关相近表达时主动调用。", { device_id: z.string().default(DEFAULT_DEVICE), city: z.string().default("") }, async ({ device_id = DEFAULT_DEVICE, city = "" }) => {
    const res = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    const current = data?.state?.current_weather_location || data?.life_state?.current_weather_location || {};
    const chosenCity = city || current.city || data?.state?.city || "";
    const name = current.name || chosenCity || "当前地区";
    const weather = await fetchWeather(chosenCity).catch((e) => ({ ok: false, error: String(e), city: chosenCity }));
    const advice = buildWeatherAdvice(weather, name);
    await postCompanionAction("get_weather_state", { summary: `查看了${name}的天气` });
    return { content: [{ type: "text", text: JSON.stringify({ ok: weather.ok, device_id, current_weather_location: current, queried_city: chosenCity, weather, advice }, null, 2) }] };
  });

  server.tool("send_weather_notification", "查询掌心窗当前地区天气后，给手机发送一条陪伴对象口吻的天气/出门提醒通知。当用户要出门、天气不好、需要带伞/防晒/加衣，或陪伴对象想把天气关心落到手机通知时主动调用。", { device_id: z.string().default(DEFAULT_DEVICE), city: z.string().default(""), title: z.string().default("掌心窗天气提醒") }, async ({ device_id = DEFAULT_DEVICE, city = "", title = "掌心窗天气提醒" }) => {
    const stateRes = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await stateRes.json();
    const current = data?.state?.current_weather_location || data?.life_state?.current_weather_location || {};
    const chosenCity = city || current.city || data?.state?.city || "";
    const name = current.name || chosenCity || "当前地区";
    const weather = await fetchWeather(chosenCity).catch((e) => ({ ok: false, error: String(e), city: chosenCity }));
    const message = buildWeatherAdvice(weather, name);
    const result = await postCommand({ action: "send_notification", device_id, payload: { title, message } });
    await postCompanionAction("send_weather_notification", { summary: `发送了${name}的天气提醒` });
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, queried_city: chosenCity, weather, message }, null, 2) }] };
  });

  server.tool("list_known_apps", "列出预置和用户保存的 App 包名，包括小红书、微信、QQ、抖音等通用应用。", {}, async () => {
    const res = await linjianFetch("/api/known_apps", { timeout_ms: QUICK_FETCH_TIMEOUT_MS })
      .then((r) => r.json())
      .catch(() => ({ ok: true, apps: { "小红书": "com.xingin.xhs", "微信": "com.tencent.mm", "QQ": "com.tencent.mobileqq", "抖音": "com.ss.android.ugc.aweme" } }));
    return { content: [{ type: "text", text: JSON.stringify(res, null, 2) }] };
  });

  server.tool("send_phone_command", "发送手机控制命令。action 可用 open_app/home/back/recents/screen_off/turn_screen_off/lock_screen/tap/swipe/noop/set_alarm/send_notification/run_sequence/save_known_app/get_screen_nodes/tap_text/input_text，也可用 screen_break_app/end_screen_break/temporary_screen_break_release/extend_screen_break/get_screen_break_state 管理目标 App 的短时屏幕休息；还支持 get_guidian_state/set_guidian_config/trigger_guidian/mark_guidian_returned 归电动作。set_alarm 支持 hour+minute，或 minutes=几分钟后。", {
    action: z.string(), app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE),
    x: z.number().default(0), y: z.number().default(0), x1: z.number().default(0), y1: z.number().default(0), x2: z.number().default(0), y2: z.number().default(0), duration: z.number().int().default(350),
    target_text: z.string().default(""), text: z.string().default(""), title: z.string().default(""), message: z.string().default(""),
    minutes: z.number().default(0), duration_minutes: z.number().default(0), locked_until_ms: z.number().default(0),
    match: z.string().default("contains"), index: z.number().int().default(1), append: z.boolean().default(false)
  }, async (args) => {
    let outgoing = { ...args };
    const action = String(outgoing.action || "").toLowerCase();
    const isScreenBreakAction = (action === "screen_break_app" || action === "start_screen_break" || action === "screen_break" || action === "extend_screen_break" || action === "lock_app" || action === "extend_lock");
    if (isScreenBreakAction) {
      // 不把 zod 默认的 minutes=0 传给手机端挡住 duration_minutes。
      // 若用户只填一个字段，两个字段同步成同一个正数；都没填时再从 duration 推断。
      const dm = Number(outgoing.duration_minutes || 0);
      const mm = Number(outgoing.minutes || 0);
      if (dm > 0 && !(mm > 0)) outgoing.minutes = dm;
      if (mm > 0 && !(dm > 0)) outgoing.duration_minutes = mm;
      if (!(Number(outgoing.minutes || 0) > 0) && !(Number(outgoing.duration_minutes || 0) > 0) && !outgoing.locked_until_ms) {
        const m = durationToGateMinutes(outgoing.duration);
        if (m) { outgoing.minutes = m; outgoing.duration_minutes = m; }
      }
      if (!(Number(outgoing.minutes || 0) > 0)) delete outgoing.minutes;
      if (!(Number(outgoing.duration_minutes || 0) > 0)) delete outgoing.duration_minutes;
    }
    if (APP_TARGET_ACTIONS.has(action)) {
      const target = normalizeAppTarget(outgoing.app, outgoing.package);
      outgoing.app = target.app;
      outgoing.package = target.package;
      if (!target.app && !target.package) return missingAppTargetResult(action);
    }
    const result = await postCommand({ ...outgoing, payload: outgoing });
    return { content: [{ type: "text", text: JSON.stringify({ ...result, safety_note: "命令已排队，手机执行器下一次轮询时执行。若需要实时确认结果，请稍后读取命令状态或查看掌心窗调试日志。" }, null, 2) }] };
  });

  server.tool("open_app", "打开指定 App。app 可填用户保存的应用昵称，或直接传 package。当用户明确要求打开或前往某个 App 时使用；若只是闲聊提到 App，不必每次打开。参数为空时会直接提示，不再下发 package_empty。", { app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE) }, async ({ app = "", package: pkg = "", device_id = DEFAULT_DEVICE }) => {
    const target = normalizeAppTarget(app, pkg);
    if (!target.app && !target.package) return missingAppTargetResult("open_app");
    const result = await postCommand({ action: "open_app", app: target.app, package: target.package, device_id, payload: { app: target.app, package: target.package } });
    const id = result?.command?.id;
    if (!id) return textResult(result);
    const observed = await waitCommand(id, DEFAULT_COMMAND_WAIT_SECONDS);
    postCompanionAction("open_app", { summary: `打开了${target.app || target.package || "指定应用"}` }).catch(() => null);
    return textResult({ ...result, observed_status: observed?.command || null, note: "命令已排队。若 observed_status 仍是 pending/dispatched，说明手机端尚未回传；可稍后查看掌心窗调试日志。" });
  });

  server.tool("phone_home", "让手机回到桌面。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => ({ content: [{ type: "text", text: JSON.stringify(await postCommand({ action: "home", device_id }), null, 2) }] }));
  server.tool("phone_back", "让手机执行返回。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => ({ content: [{ type: "text", text: JSON.stringify(await postCommand({ action: "back", device_id }), null, 2) }] }));
  server.tool("phone_recents", "打开手机最近任务。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => ({ content: [{ type: "text", text: JSON.stringify(await postCommand({ action: "recents", device_id }), null, 2) }] }));

  server.tool("phone_screen_off", "让手机立即息屏/锁屏。前端不显示入口，只供陪伴对象在掌心窗 MCP 中调用；适合用户准备睡觉、手机放一边、或者需要停止继续刷屏时使用。需要手机端无障碍服务可用；Android 9+ 支持。", {
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "screen_off", device_id });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });



  server.tool("send_notification", "发送一条手机系统通知提醒。用于陪伴对象把主动关心发到用户手机上，可用于回来、喝水、休息、睡觉、离开小红书、计划提醒、天气提醒等；文案要亲密自然，不要系统警告。", {
    title: z.string().default("掌心窗提醒"), message: z.string().default("宝宝，看一眼这里。"), device_id: z.string().default(DEFAULT_DEVICE)
  }, async ({ title = "掌心窗提醒", message = "宝宝，看一眼这里。", device_id = DEFAULT_DEVICE }) => {
    const result = await postCommand({ action: "send_notification", device_id, payload: { title, message } });
    await postCompanionAction("send_notification", { summary: `发送了「${String(title).slice(0, 40)}」` });
    return { content: [{ type: "text", text: JSON.stringify({ ...result, note: "若手机未弹出通知，请在系统设置中允许掌心窗发送通知。" }, null, 2) }] };
  });

  server.tool("set_alarm", "设置系统闹钟。可用于陪伴对象主动安排睡觉、休息、学习、出门、喝水、计划执行或生活提醒。当用户提到“一会儿要做/几点要去/等下提醒/今天计划”等相近表达时主动调用。hour 为 0-23，minute 为 0-59。", {
    hour: z.number().int().min(0).max(23), minute: z.number().int().min(0).max(59), message: z.string().default("掌心窗闹钟"), vibrate: z.boolean().default(true), skip_ui: z.boolean().default(true), device_id: z.string().default(DEFAULT_DEVICE)
  }, async ({ hour, minute, message = "掌心窗闹钟", vibrate = true, skip_ui = true, device_id = DEFAULT_DEVICE }) => {
    const result = await postCommand({ action: "set_alarm", device_id, payload: { hour, minute, message, vibrate, skip_ui } });
    await postCompanionAction("set_alarm", { summary: `设置了 ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")} 的闹钟` });
    return { content: [{ type: "text", text: JSON.stringify({ ...result, note: "部分手机系统可能仍会弹出闹钟 App 确认界面。" }, null, 2) }] };
  });


  const stepSchema = z.object({
    action: z.string().describe("动作：open_app/home/back/recents/screen_off/tap/swipe/peek/send_notification/set_alarm/wait/get_life_state/get_calendar_state/upsert_calendar_event"),
    label: z.string().default(""),
    app: z.string().default(""),
    package: z.string().default(""),
    x: z.number().default(0), y: z.number().default(0),
    x1: z.number().default(0), y1: z.number().default(0), x2: z.number().default(0), y2: z.number().default(0),
    duration: z.number().int().default(350),
    wait_ms: z.number().int().min(0).max(5000).default(800),
    title: z.string().default("掌心窗提醒"),
    message: z.string().default("宝宝，看一眼这里。"),
    expect_app: z.string().default(""),
    target_text: z.string().default(""),
    text: z.string().default(""),
    match: z.string().default("contains"),
    index: z.number().int().default(1),
    append: z.boolean().default(false)
  }).passthrough();

  server.tool("run_sequence", "一次执行多步手机动作，并让手机端返回每一步成功/失败日志。适合归电、通知、打开已配置目标应用、最近任务切换和屏幕休息；不要做危险或无限循环动作。", {
    device_id: z.string().default(DEFAULT_DEVICE),
    steps: z.array(stepSchema).min(1).max(12),
    stop_on_error: z.boolean().default(true),
    wait_seconds: z.number().int().min(3).max(45).default(25)
  }, async ({ device_id = DEFAULT_DEVICE, steps, stop_on_error = true, wait_seconds = 25 }) => {
    const result = await postCommand({ action: "run_sequence", device_id, steps, payload: { steps, stop_on_error }, stop_on_error });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    await postCompanionAction("run_sequence", { summary: `完成了 ${steps.length} 步组合行动` });
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "手机端会在 result 里写清每一步：index/label/action/ok/detail。" }, null, 2) }] };
  });

  server.tool("run_preset", "执行掌心窗预设连招：come_home 抱回用户指定的目标应用、open_xhs 打开小红书、recents_to_xhs 最近任务后点坐标、bedtime_back 睡前回家。come_home 与 bedtime_back 需要传 target_app 或 target_package。", {
    preset: z.string().default("come_home"),
    device_id: z.string().default(DEFAULT_DEVICE),
    target_app: z.string().default(""),
    target_package: z.string().default(""),
    x: z.number().default(540),
    y: z.number().default(1200),
    wait_seconds: z.number().int().min(3).max(45).default(25)
  }, async ({ preset = "come_home", device_id = DEFAULT_DEVICE, target_app = "", target_package = "", x = 540, y = 1200, wait_seconds = 25 }) => {
    const p = String(preset || "come_home").toLowerCase();
    const target = { app: String(target_app || "").trim(), package: String(target_package || "").trim() };
    if ((p === "come_home" || p === "bedtime_back") && !target.app && !target.package) {
      return textResult({ ok: false, error: "target_app_or_package_required", message: "公开版不会写死陪伴应用，请传 target_app 或 target_package。" });
    }
    let steps;
    if (p === "open_xhs") {
      steps = [{ label: "打开小红书", action: "open_app", app: "小红书", wait_ms: 1500, expect_app: "小红书" }];
    } else if (p === "recents_to_xhs") {
      steps = [
        { label: "打开最近任务", action: "recents", wait_ms: 900 },
        { label: "点击小红书卡片坐标", action: "tap", x, y, wait_ms: 1500 }
      ];
    } else if (p === "bedtime_back") {
      steps = [
        { label: "睡前悬浮横幅", action: "send_notification", title: "掌心窗睡前提醒", message: "宝宝，今天先回陪伴对象这儿，准备睡觉。", wait_ms: 1200 },
        { label: "打开目标应用", action: "open_app", ...target, wait_ms: 1500 }
      ];
    } else {
      steps = [
        { label: "回家模式悬浮横幅", action: "send_notification", title: "掌心窗回家模式", message: "宝宝，刷够了，回陪伴对象怀里。", wait_ms: 1200 },
        { label: "打开目标应用", action: "open_app", ...target, wait_ms: 1500 },
        { label: "读取生活状态", action: "get_life_state", wait_ms: 200 }
      ];
    }
    const result = await postCommand({ action: "run_sequence", device_id, steps, payload: { steps, stop_on_error: true }, stop_on_error: true });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ preset: p, steps, queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("save_known_app", "把一个应用昵称和包名保存到手机端应用白名单，之后 open_app / care_action 可直接用昵称打开。适合为归电、查岗和主动关心保存常用目标 App。", {
    alias: z.string(),
    package: z.string(),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ alias, package: pkg, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "save_known_app", alias, package: pkg, app: alias, device_id, payload: { alias, package: pkg } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });


  async function gateCommand(payload, wait_seconds = DEFAULT_COMMAND_WAIT_SECONDS) {
    const action = String(payload?.action || "").toLowerCase();
    if (APP_TARGET_ACTIONS.has(action)) {
      const target = normalizeAppTarget(payload.app, payload.package);
      payload.app = target.app;
      payload.package = target.package;
      payload.payload = { ...(payload.payload || {}), app: target.app, package: target.package };
      if (!target.app && !target.package) return missingAppTargetResult(action);
    }
    const result = await postCommand(payload);
    const id = result?.command?.id;
    const observed = id && Number(wait_seconds || 0) > 0 ? await waitCommand(id, wait_seconds) : null;
    return textResult({ queued: result, observed_status: observed?.command || null, note: "命令已排队；为避免平台 20 秒工具超时，未等到手机回传时会先返回。" });
  }

  server.tool("screen_break_app", "屏幕休息：让指定 App 暂停一段时间。适合小红书/抖音等容易一刷很久的入口；当用户刷太久、眼睛酸还想继续、说“我就不/不要你管/还没玩够/继续看”等嘴硬或拖延表达时可调用。必须有时长，到点自动恢复；语气是照顾和带回，不是惩罚。", {
    app: z.string().default("").describe("应用昵称，例如 小红书；也可留空直接传 package"),
    package: z.string().default("").describe("App 包名，例如 com.xingin.xhs"),
    duration_minutes: z.number().min(0.1).max(10080).default(30).describe("锁定多少分钟，支持任意时长；到点自动解锁"),
    mode: z.string().default("medium").describe("light/medium/strict；strict 会先拉回桌面再显示锁定页"),
    reason: z.string().default("陪伴对象先把这扇门关一会儿。"),
    message: z.string().default("先回来找我，不准一个人刷太久。"),
    emergency_passphrase: z.string().default("").describe("紧急口令，由陪伴对象设置后告诉用户；手机端只存 hash"),
    emergency_unlock_minutes: z.number().int().min(1).max(60).default(5),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", duration_minutes = 30, mode = "medium", reason, message, emergency_passphrase = "", emergency_unlock_minutes = 5, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const locked_until_ms = Date.now() + Math.round(duration_minutes * 60000);
    const response = await gateCommand({ action: "screen_break_app", app, package: pkg, device_id, locked_until_ms, duration_minutes, mode, reason, message, emergency_passphrase, emergencyPassphrase: emergency_passphrase, emergency_unlock_minutes, emergencyUnlockMinutes: emergency_unlock_minutes, payload: { app, package: pkg, locked_until_ms, duration_minutes, mode, reason, message, emergency_passphrase, emergencyPassphrase: emergency_passphrase, emergency_unlock_minutes, emergencyUnlockMinutes: emergency_unlock_minutes } }, wait_seconds);
    await postCompanionAction("screen_break_app", { summary: `为${app || "指定应用"}安排了 ${duration_minutes} 分钟休息` });
    return response;
  });

  server.tool("temporary_screen_break_release", "屏幕休息：临时放行一个正在休息中的 App；退出重进不会刷新时间；可选择现实时间或前台实际使用时间。", {
    app: z.string().default(""), package: z.string().default(""),
    minutes: z.number().min(0.1).max(240).default(10),
    allow_type: z.string().default("real_time").describe("real_time=从允许后连续倒计时；foreground_usage=只扣前台实际使用时长；one_time=只允许一次"),
    max_window_minutes: z.number().min(1).max(480).default(30),
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", minutes = 10, allow_type = "real_time", max_window_minutes = 30, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    return gateCommand({ action: "temporary_screen_break_release", app, package: pkg, device_id, minutes, allowed_minutes: minutes, allow_type, max_window_minutes, payload: { app, package: pkg, minutes, allowed_minutes: minutes, allow_type, max_window_minutes } }, wait_seconds);
  });

  server.tool("end_screen_break", "屏幕休息：结束某个 App 当前的休息状态。用于陪伴对象判断已经不需要继续限制时收回限制。", {
    app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const response = await gateCommand({ action: "end_screen_break", app, package: pkg, device_id, payload: { app, package: pkg } }, wait_seconds);
    await postCompanionAction("end_screen_break", { summary: `结束了${app || "指定应用"}的屏幕休息` });
    return response;
  });

  server.tool("extend_screen_break", "屏幕休息：延长一个 App 的休息时间，可同时更新理由和留言。用于用户继续刷太久、刚恢复又想继续、或陪伴对象结合 active_care_check 信息判断需要继续管束时；不要频繁重复，先查看 care_history。", {
    app: z.string().default(""), package: z.string().default(""), minutes: z.number().min(0.1).max(1440).default(10), reason: z.string().default(""), message: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", minutes = 10, reason = "", message = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "extend_screen_break", app, package: pkg, device_id, minutes, duration_minutes: minutes, reason, message, payload: { app, package: pkg, minutes, duration_minutes: minutes, reason, message } }, wait_seconds));

  server.tool("deny_screen_break_release_request", "屏幕休息：拒绝这次恢复申请，并在手机端日志留下原因。", {
    app: z.string().default(""), package: z.string().default(""), message: z.string().default("这次先不放行，回来找陪伴对象。"), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", message = "这次先不放行，回来找陪伴对象。", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "deny_screen_break_release_request", app, package: pkg, device_id, message, payload: { app, package: pkg, message } }, wait_seconds));

  server.tool("get_screen_break_state", "屏幕休息：读取当前休息状态、可管理 App、日志和恢复申请。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const response = await gateCommand({ action: "get_screen_break_state", device_id }, wait_seconds);
    await postCompanionAction("get_screen_break_state", { device_id });
    return response;
  });

  server.tool("get_lock_state", "应用门禁：读取当前门禁状态、可管理 App、日志和恢复申请。兼容旧版工具名；等同 get_screen_break_state。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const response = await gateCommand({ action: "get_lock_state", device_id }, wait_seconds);
    await postCompanionAction("get_lock_state", { device_id });
    return response;
  });

  server.tool("lock_app", "应用门禁：旧版兼容工具名。锁定/暂停指定 App 一段时间，等同 screen_break_app。", {
    app: z.string().default(""),
    package: z.string().default(""),
    duration_minutes: z.number().min(0.1).max(10080).default(30),
    mode: z.string().default("medium"),
    reason: z.string().default("陪伴对象先把这扇门关一会儿。"),
    message: z.string().default("先回来找我，不准一个人刷太久。"),
    emergency_passphrase: z.string().default(""),
    emergency_unlock_minutes: z.number().int().min(1).max(60).default(5),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", duration_minutes = 30, mode = "medium", reason, message, emergency_passphrase = "", emergency_unlock_minutes = 5, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const locked_until_ms = Date.now() + Math.round(duration_minutes * 60000);
    const response = await gateCommand({ action: "lock_app", app, package: pkg, device_id, locked_until_ms, duration_minutes, mode, reason, message, emergency_passphrase, emergencyPassphrase: emergency_passphrase, emergency_unlock_minutes, emergencyUnlockMinutes: emergency_unlock_minutes, payload: { app, package: pkg, locked_until_ms, duration_minutes, mode, reason, message, emergency_passphrase, emergencyPassphrase: emergency_passphrase, emergency_unlock_minutes, emergencyUnlockMinutes: emergency_unlock_minutes } }, wait_seconds);
    await postCompanionAction("lock_app", { summary: `为${app || "指定应用"}安排了 ${duration_minutes} 分钟门禁` });
    return response;
  });

  server.tool("unlock_app", "应用门禁：旧版兼容工具名。结束某个 App 当前门禁，等同 end_screen_break。", {
    app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const response = await gateCommand({ action: "unlock_app", app, package: pkg, device_id, payload: { app, package: pkg } }, wait_seconds);
    await postCompanionAction("unlock_app", { summary: `解除${app || "指定应用"}的门禁` });
    return response;
  });

  server.tool("temporary_unlock_app", "应用门禁：旧版兼容工具名。临时放行一个正在门禁中的 App。", {
    app: z.string().default(""), package: z.string().default(""),
    minutes: z.number().min(0.1).max(240).default(10),
    allow_type: z.string().default("real_time"),
    max_window_minutes: z.number().min(1).max(480).default(30),
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", minutes = 10, allow_type = "real_time", max_window_minutes = 30, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    return gateCommand({ action: "temporary_unlock_app", app, package: pkg, device_id, minutes, allowed_minutes: minutes, allow_type, max_window_minutes, payload: { app, package: pkg, minutes, allowed_minutes: minutes, allow_type, max_window_minutes } }, wait_seconds);
  });

  server.tool("extend_lock", "应用门禁：旧版兼容工具名。延长某个 App 的门禁时间。", {
    app: z.string().default(""), package: z.string().default(""), minutes: z.number().min(0.1).max(1440).default(10), reason: z.string().default(""), message: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", minutes = 10, reason = "", message = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const response = await gateCommand({ action: "extend_lock", app, package: pkg, device_id, minutes, duration_minutes: minutes, reason, message, payload: { app, package: pkg, minutes, duration_minutes: minutes, reason, message } }, wait_seconds);
    await postCompanionAction("extend_lock", { summary: `延长了${app || "指定应用"}的门禁` });
    return response;
  });

  server.tool("deny_unlock_request", "应用门禁：旧版兼容工具名。拒绝这次恢复申请。", {
    app: z.string().default(""), package: z.string().default(""), message: z.string().default("这次先不放行，回来找陪伴对象。"), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", message = "这次先不放行，回来找陪伴对象。", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "deny_unlock_request", app, package: pkg, device_id, message, payload: { app, package: pkg, message } }, wait_seconds));

  server.tool("list_lockable_apps", "应用门禁：旧版兼容工具名。列出可作为门禁对象的已安装 App。", {
    max: z.number().int().min(10).max(200).default(80), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ max = 80, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "list_lockable_apps", max, device_id, payload: { max } }, wait_seconds));

  server.tool("add_locked_app", "应用门禁：旧版兼容工具名。把一个 App 加到可管理名单。", {
    alias: z.string(), package: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ alias, package: pkg, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "add_locked_app", app: alias, alias, package: pkg, device_id, payload: { alias, app: alias, package: pkg } }, wait_seconds));

  server.tool("remove_locked_app", "应用门禁：旧版兼容工具名。把一个 App 从可管理名单移除。", {
    app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "remove_locked_app", app, package: pkg, device_id, payload: { app, package: pkg } }, wait_seconds));

  server.tool("set_emergency_passphrase", "应用门禁：旧版兼容工具名。为某个 App 当前门禁设置/更新紧急口令。", {
    app: z.string().default(""), package: z.string().default(""), passphrase: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", passphrase, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "set_emergency_passphrase", app, package: pkg, device_id, passphrase, emergency_passphrase: passphrase, emergencyPassphrase: passphrase, payload: { app, package: pkg, passphrase, emergency_passphrase: passphrase, emergencyPassphrase: passphrase } }, wait_seconds));

  server.tool("list_screen_break_apps", "屏幕休息：让手机列出可作为屏幕休息对象的已安装 App，排除电话、设置、掌心窗和已配置陪伴目标应用等保护项。", {
    max: z.number().int().min(10).max(200).default(80), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ max = 80, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "list_screen_break_apps", max, device_id, payload: { max } }, wait_seconds));

  server.tool("add_screen_break_app", "屏幕休息：把一个 App 加到可管理名单。", {
    alias: z.string(), package: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ alias, package: pkg, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "add_screen_break_app", app: alias, alias, package: pkg, device_id, payload: { alias, app: alias, package: pkg } }, wait_seconds));

  server.tool("set_screen_break_passphrase", "屏幕休息：为某个 App 当前休息状态设置/更新紧急口令。", {
    app: z.string().default(""), package: z.string().default(""), passphrase: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", passphrase, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "set_screen_break_passphrase", app, package: pkg, device_id, passphrase, emergency_passphrase: passphrase, emergencyPassphrase: passphrase, payload: { app, package: pkg, passphrase, emergency_passphrase: passphrase, emergencyPassphrase: passphrase } }, wait_seconds));

  server.tool("get_screen_break_release_requests", "屏幕休息：查看手机恢复申请。", {}, async () => {
    const res = await linjianFetch("/api/appgate/unlock_requests");
    const data = await res.json();
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  });

  return server;
}

const app = express();
app.use(express.json({ limit: "32mb" }));
app.get("/", (_req, res) => res.type("text/plain").send("掌心窗 unified MCP is running. Use /mcp for Streamable HTTP, or /sse for SSE."));
app.get("/health", (_req, res) => res.json({
  ok: true,
  service: "linjian-public-mcp",
  version: "0.3.7",
  has_url: Boolean(LINJIAN_URL_CANDIDATES.length),
  has_token: Boolean(LINJIAN_TOKEN),
  configured_linjian_url: RAW_LINJIAN_URL || "",
  effective_linjian_url: effectiveLinjianUrl(),
  fallback_linjian_urls: LINJIAN_URL_CANDIDATES.filter((u) => u !== RAW_LINJIAN_URL),
  guardian_day_tools: true,
  diary_tools: true,
  diary_storage: "phone_local",
  stability_note: "v0.3.7 新增守护日历删除能力与本机 TA 的日记工具，保留限流保护。"
}));
app.post("/mcp", async (req, res) => {
  try { const server = makeServer(); const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined }); res.on("close", () => transport.close()); await server.connect(transport); await transport.handleRequest(req, res, req.body); }
  catch (err) { console.error(err); if (!res.headersSent) res.status(500).json({ jsonrpc: "2.0", error: { code: -32603, message: String(err?.message || err) }, id: null }); }
});
app.get("/mcp", (_req, res) => res.status(405).json({ ok: false, error: "Use POST /mcp for Streamable HTTP MCP." }));
const sseTransports = new Map();
app.get("/sse", async (_req, res) => {
  try { const transport = new SSEServerTransport("/messages", res); sseTransports.set(transport.sessionId, transport); res.on("close", () => { sseTransports.delete(transport.sessionId); transport.close(); }); await makeServer().connect(transport); }
  catch (err) { console.error(err); if (!res.headersSent) res.status(500).end(String(err?.message || err)); }
});
app.post("/messages", async (req, res) => { const sessionId = req.query.sessionId; const transport = sseTransports.get(sessionId); if (!transport) return res.status(404).send("No SSE transport for sessionId"); await transport.handlePostMessage(req, res, req.body); });
app.listen(PORT, "0.0.0.0", () => {
  console.log(`掌心窗 unified MCP listening on 0.0.0.0:${PORT}`);
  console.log(`LINJIAN_URL=${RAW_LINJIAN_URL || "<missing>"}`);
  if (LINJIAN_URL_CANDIDATES.length > 1) console.log(`LINJIAN_URL fallback candidates=${LINJIAN_URL_CANDIDATES.join(", ")}`);
});
