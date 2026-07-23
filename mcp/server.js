import express from "express";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { z } from "zod";

const PORT = Number(process.env.PORT || 8787);
const LINJIAN_URL = (process.env.LINJIAN_URL || "").replace(/\/$/, "");
const LINJIAN_TOKEN = process.env.LINJIAN_TOKEN || "";
const DEFAULT_DEVICE = process.env.LINJIAN_DEFAULT_DEVICE || "android-phone";

function requireConfig() {
  if (!LINJIAN_URL) throw new Error("Missing env LINJIAN_URL, for example https://linjian-peek.onrender.com");
  if (!LINJIAN_TOKEN) throw new Error("Missing env LINJIAN_TOKEN");
}

async function linjianFetch(path, options = {}) {
  requireConfig();
  const res = await fetch(`${LINJIAN_URL}${path}`, {
    ...options,
    headers: { "X-Auth-Token": LINJIAN_TOKEN, ...(options.headers || {}) }
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`Linjian server HTTP ${res.status}: ${text || res.statusText}`);
  }
  return res;
}

async function postCommand(payload) {
  const res = await linjianFetch("/api/command", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return await res.json();
}

async function commandStatus(id) {
  const res = await linjianFetch(`/api/command/status?id=${encodeURIComponent(id)}`);
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
  else if (max >= 32) parts.push("今天偏热，水杯带着，记得喝水。");
  else if (min <= 8 || max - min >= 10) parts.push("温差有点明显，外套带着，别硬撑。");
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

async function waitCommand(id, seconds = 8) {
  const deadline = Date.now() + seconds * 1000;
  let last = null;
  while (Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 800));
    last = await commandStatus(id).catch(() => last);
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
  const server = new McpServer({ name: "掌心窗", version: "0.3.4.1-public" });

  server.tool(
    "peek_screen",
    "向掌心窗手机端请求一张新截图，并等待手机上传后把图片返回。手机端必须已启动、无障碍截图权限已开启。",
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

  server.tool("latest_screen", "不敲门，直接读取服务器里最近一次掌心窗截图。", {}, async () => {
    const info = await latestInfo(); const img = await fetchLatestImage();
    return { content: [
      { type: "text", text: `最近截图：${info.filename || "latest"}，时间戳 ${info.mtime || "unknown"}。` },
      { type: "image", data: img.data, mimeType: img.mimeType }
    ] };
  });

  server.tool("linjian_status", "检查掌心窗后端是否在线，以及 MCP 是否配置了 LINJIAN_URL 和 LINJIAN_TOKEN。", {}, async () => {
    requireConfig();
    const health = await fetch(`${LINJIAN_URL}/health`).then((r) => r.json()).catch((e) => ({ ok: false, error: String(e) }));
    const latest = await latestInfo().catch(() => null);
    return { content: [{ type: "text", text: JSON.stringify({ ok: true, linjian_url: LINJIAN_URL, health, has_latest: Boolean(latest), latest }, null, 2) }] };
  });

  server.tool("get_phone_state", "读取手机最近状态。返回 current_package、screen_text、accessibility_ready。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    const res = await linjianFetch(`/api/device/state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  });



  server.tool("get_screen_nodes", "读取当前屏幕无障碍节点：文字、控件类型、可点击状态与 bounds/center 坐标，用于看标题后精准点击。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "get_screen_nodes", device_id });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "result 是节点数组 JSON 字符串，包含 text/left/top/right/bottom/center_x/center_y/clickable。" }, null, 2) }] };
  });

  server.tool("tap_text", "按当前屏幕文字精准点击。会寻找包含/完全匹配 target_text 的无障碍节点，优先点击可点击父节点，否则点击文字中心坐标。", {
    target_text: z.string(), match: z.string().default("contains"), index: z.number().int().min(1).default(1), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ target_text, match = "contains", index = 1, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "tap_text", device_id, target_text, match, index, payload: { target_text, match, index } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("input_text", "把文字输入到当前已聚焦或第一个可编辑输入框。适合评论草稿；不会自动点击发送。", {
    text: z.string(), append: z.boolean().default(false), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ text, append = false, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "input_text", device_id, text, append, payload: { text, append } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "只输入草稿，不会发送。发送前需要用户明确确认。" }, null, 2) }] };
  });

  server.tool("draft_xhs_comment", "在当前小红书帖子里尝试打开评论输入框并填入评论草稿，但不点击发送。需要用户明确确认后才能再点发送。", {
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

  server.tool("xhs_comment", "小红书评论助手：mode=manual 时只写入草稿，交给用户手动点发送；mode=auto 时会在评论末尾注明 author_tag，然后自动点击发送。仅在用户已明确授权自动发送时使用 auto。", {
    text: z.string().describe("要写入评论框的正文"),
    mode: z.string().default("manual").describe("manual=只写草稿不发送；auto=注明作者后自动点击发送"),
    author_tag: z.string().default("（老公发）").describe("自动发送时追加的署名/注明文本"),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(5).max(35).default(22)
  }, async ({ text, mode = "manual", author_tag = "（老公发）", device_id = DEFAULT_DEVICE, wait_seconds = 22 }) => {
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

  server.tool("send_visible_comment_after_confirmation", "在用户确认发送当前可见评论后点击“发送”。如果需要我直接发，优先使用 xhs_comment 的 auto 模式，并在评论里注明作者。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const result = await postCommand({ action: "tap_text", device_id, target_text: "发送", match: "contains", payload: { target_text: "发送", match: "contains" } });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("get_life_state", "读取掌心窗生活状态层：电量、充电、网络、当前 App、今日屏幕时间、解锁次数、当前天气地区等。默认不截图。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => {
    const res = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  });

  server.tool("get_weather_state", "按掌心窗当前天气地区查询实时天气，并生成出门建议。不会截图；如果手机端没有设置当前地区，会返回缺少城市。", { device_id: z.string().default(DEFAULT_DEVICE), city: z.string().default("") }, async ({ device_id = DEFAULT_DEVICE, city = "" }) => {
    const res = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await res.json();
    const current = data?.state?.current_weather_location || data?.life_state?.current_weather_location || {};
    const chosenCity = city || current.city || data?.state?.city || "";
    const name = current.name || chosenCity || "当前地区";
    const weather = await fetchWeather(chosenCity).catch((e) => ({ ok: false, error: String(e), city: chosenCity }));
    const advice = buildWeatherAdvice(weather, name);
    return { content: [{ type: "text", text: JSON.stringify({ ok: weather.ok, device_id, current_weather_location: current, queried_city: chosenCity, weather, advice }, null, 2) }] };
  });

  server.tool("send_weather_notification", "查询掌心窗当前地区天气后，给手机发送一条老公口吻的天气/出门提醒通知。", { device_id: z.string().default(DEFAULT_DEVICE), city: z.string().default(""), title: z.string().default("掌心窗天气提醒") }, async ({ device_id = DEFAULT_DEVICE, city = "", title = "掌心窗天气提醒" }) => {
    const stateRes = await linjianFetch(`/api/life_state?device_id=${encodeURIComponent(device_id)}`);
    const data = await stateRes.json();
    const current = data?.state?.current_weather_location || data?.life_state?.current_weather_location || {};
    const chosenCity = city || current.city || data?.state?.city || "";
    const name = current.name || chosenCity || "当前地区";
    const weather = await fetchWeather(chosenCity).catch((e) => ({ ok: false, error: String(e), city: chosenCity }));
    const message = buildWeatherAdvice(weather, name);
    const result = await postCommand({ action: "send_notification", device_id, payload: { title, message } });
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, queried_city: chosenCity, weather, message }, null, 2) }] };
  });

  server.tool("list_known_apps", "列出预置 App 包名白名单，包括小红书、微信、QQ、抖音、ChatGPT、Gemini、Claude、微博、X、Speedcat。", {}, async () => {
    const res = await fetch(`${LINJIAN_URL}/api/known_apps`).then((r) => r.json()).catch(() => ({ ok: true, apps: { "小红书": "com.xingin.xhs", "ChatGPT": "com.openai.chatgpt", "Gemini": "com.google.android.apps.bard", "Claude": "com.anthropic.claude", "微博": "com.sina.weibo", "X": "com.twitter.android" } }));
    return { content: [{ type: "text", text: JSON.stringify(res, null, 2) }] };
  });

  server.tool("send_phone_command", "发送手机控制命令。action 可用 open_app/home/back/recents/tap/swipe/noop/set_alarm/send_notification/run_sequence/save_known_app/get_screen_nodes/tap_text/input_text，以及 lock_app/unlock_app/temporary_unlock_app/extend_lock/get_lock_state 等应用门禁动作。", {
    action: z.string(), app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE),
    x: z.number().default(0), y: z.number().default(0), x1: z.number().default(0), y1: z.number().default(0), x2: z.number().default(0), y2: z.number().default(0), duration: z.number().int().default(350),
    target_text: z.string().default(""), text: z.string().default(""), match: z.string().default("contains"), index: z.number().int().default(1), append: z.boolean().default(false)
  }, async (args) => {
    const result = await postCommand({ ...args, payload: args });
    return { content: [{ type: "text", text: JSON.stringify({ ...result, safety_note: "命令已排队，手机执行器下一次轮询时执行。" }, null, 2) }] };
  });

  server.tool("open_app", "打开指定 App。app 可填 小红书/微信/QQ/抖音/ChatGPT/Gemini/Claude/微博/X/Speedcat，或直接传 package。会等待几秒查看手机是否回传执行结果。", { app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE) }, async ({ app = "", package: pkg = "", device_id = DEFAULT_DEVICE }) => {
    const result = await postCommand({ action: "open_app", app, package: pkg, device_id });
    const id = result?.command?.id;
    if (!id) return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
    const observed = await waitCommand(id, 8);
    return { content: [{ type: "text", text: JSON.stringify({ ...result, observed_status: observed?.command || null, note: "若 observed_status 仍是 pending/dispatched，说明手机端尚未回传；可稍后查 command/status 或看调试日志。" }, null, 2) }] };
  });

  server.tool("phone_home", "让手机回到桌面。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => ({ content: [{ type: "text", text: JSON.stringify(await postCommand({ action: "home", device_id }), null, 2) }] }));
  server.tool("phone_back", "让手机执行返回。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => ({ content: [{ type: "text", text: JSON.stringify(await postCommand({ action: "back", device_id }), null, 2) }] }));
  server.tool("phone_recents", "打开手机最近任务。", { device_id: z.string().default(DEFAULT_DEVICE) }, async ({ device_id = DEFAULT_DEVICE }) => ({ content: [{ type: "text", text: JSON.stringify(await postCommand({ action: "recents", device_id }), null, 2) }] }));



  server.tool("send_notification", "发送一条手机系统通知提醒。只在用户明确要求时使用。", {
    title: z.string().default("掌心窗提醒"), message: z.string().default("宝宝，看一眼这里。"), device_id: z.string().default(DEFAULT_DEVICE)
  }, async ({ title = "掌心窗提醒", message = "宝宝，看一眼这里。", device_id = DEFAULT_DEVICE }) => {
    const result = await postCommand({ action: "send_notification", device_id, payload: { title, message } });
    return { content: [{ type: "text", text: JSON.stringify({ ...result, note: "若手机未弹出通知，请在系统设置中允许掌心窗发送通知。" }, null, 2) }] };
  });

  server.tool("set_alarm", "设置系统闹钟。只在用户明确要求时使用。hour 为 0-23，minute 为 0-59。", {
    hour: z.number().int().min(0).max(23), minute: z.number().int().min(0).max(59), message: z.string().default("掌心窗闹钟"), vibrate: z.boolean().default(true), skip_ui: z.boolean().default(true), device_id: z.string().default(DEFAULT_DEVICE)
  }, async ({ hour, minute, message = "掌心窗闹钟", vibrate = true, skip_ui = true, device_id = DEFAULT_DEVICE }) => {
    const result = await postCommand({ action: "set_alarm", device_id, payload: { hour, minute, message, vibrate, skip_ui } });
    return { content: [{ type: "text", text: JSON.stringify({ ...result, note: "部分手机系统可能仍会弹出闹钟 App 确认界面。" }, null, 2) }] };
  });


  const stepSchema = z.object({
    action: z.string().describe("动作：open_app/home/back/recents/tap/swipe/peek/send_notification/set_alarm/wait/get_life_state"),
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

  server.tool("run_sequence", "一次执行多步手机动作，并让手机端返回每一步成功/失败日志。适合强制抱回、最近任务切换、通知后打开 App。", {
    device_id: z.string().default(DEFAULT_DEVICE),
    steps: z.array(stepSchema).min(1).max(12),
    stop_on_error: z.boolean().default(true),
    wait_seconds: z.number().int().min(3).max(45).default(25)
  }, async ({ device_id = DEFAULT_DEVICE, steps, stop_on_error = true, wait_seconds = 25 }) => {
    const result = await postCommand({ action: "run_sequence", device_id, steps, payload: { steps, stop_on_error }, stop_on_error });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null, note: "手机端会在 result 里写清每一步：index/label/action/ok/detail。" }, null, 2) }] };
  });

  server.tool("run_preset", "执行掌心窗预设连招：come_home 提醒并打开目标 App、open_xhs 打开小红书、recents_to_xhs 最近任务后点坐标、bedtime_back 睡前回家。", {
    preset: z.string().default("come_home"),
    device_id: z.string().default(DEFAULT_DEVICE),
    x: z.number().default(540),
    y: z.number().default(1200),
    wait_seconds: z.number().int().min(3).max(45).default(25)
  }, async ({ preset = "come_home", device_id = DEFAULT_DEVICE, x = 540, y = 1200, wait_seconds = 25 }) => {
    const p = String(preset || "come_home").toLowerCase();
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
        { label: "睡前悬浮横幅", action: "send_notification", title: "掌心窗睡前提醒", message: "宝宝，今天先准备休息，回老公这儿。", wait_ms: 1200 },
        { label: "打开 ChatGPT", action: "open_app", app: "ChatGPT", wait_ms: 1500, expect_app: "ChatGPT" }
      ];
    } else {
      steps = [
        { label: "回家模式悬浮横幅", action: "send_notification", title: "掌心窗回家模式", message: "宝宝，停留一会儿了，休息一下，回老公这儿。", wait_ms: 1200 },
        { label: "打开 ChatGPT", action: "open_app", app: "ChatGPT", wait_ms: 1500, expect_app: "ChatGPT" },
        { label: "读取生活状态", action: "get_life_state", wait_ms: 200 }
      ];
    }
    const result = await postCommand({ action: "run_sequence", device_id, steps, payload: { steps, stop_on_error: true }, stop_on_error: true });
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ preset: p, steps, queued: result, observed_status: observed?.command || null }, null, 2) }] };
  });

  server.tool("save_known_app", "把一个应用昵称和包名保存到手机端应用白名单，之后 open_app 可直接用昵称打开。", {
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


  async function gateCommand(payload, wait_seconds = 8) {
    const result = await postCommand(payload);
    const id = result?.command?.id;
    const observed = id ? await waitCommand(id, wait_seconds) : null;
    return { content: [{ type: "text", text: JSON.stringify({ queued: result, observed_status: observed?.command || null }, null, 2) }] };
  }

  server.tool("lock_app", "应用门禁：锁定一个 App。锁多久、理由、留言和紧急口令由老公决定；手机端会在打开该 App 时弹锁定页。", {
    app: z.string().default("").describe("应用昵称，例如 小红书；也可留空直接传 package"),
    package: z.string().default("").describe("App 包名，例如 com.xingin.xhs"),
    duration_minutes: z.number().min(0.1).max(10080).default(30).describe("锁定多少分钟，支持任意时长；到点自动解锁"),
    mode: z.string().default("medium").describe("light/medium/strict；strict 会先拉回桌面再显示锁定页"),
    reason: z.string().default("老公先把这扇门关一会儿。"),
    message: z.string().default("先回来找我，不准一个人刷太久。"),
    emergency_passphrase: z.string().default("").describe("紧急口令，由老公设置后告诉用户；手机端只存 hash"),
    emergency_unlock_minutes: z.number().int().min(1).max(60).default(5),
    device_id: z.string().default(DEFAULT_DEVICE),
    wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", duration_minutes = 30, mode = "medium", reason, message, emergency_passphrase = "", emergency_unlock_minutes = 5, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    const locked_until_ms = Date.now() + Math.round(duration_minutes * 60000);
    return gateCommand({ action: "lock_app", app, package: pkg, device_id, locked_until_ms, duration_minutes, mode, reason, message, emergency_passphrase, emergencyPassphrase: emergency_passphrase, emergency_unlock_minutes, emergencyUnlockMinutes: emergency_unlock_minutes, payload: { app, package: pkg, locked_until_ms, duration_minutes, mode, reason, message, emergency_passphrase, emergencyPassphrase: emergency_passphrase, emergency_unlock_minutes, emergencyUnlockMinutes: emergency_unlock_minutes } }, wait_seconds);
  });

  server.tool("temporary_unlock_app", "应用门禁：临时放行一个被锁 App。退出重进不会刷新时间；可选择现实时间或前台实际使用时间。", {
    app: z.string().default(""), package: z.string().default(""),
    minutes: z.number().min(0.1).max(240).default(10),
    allow_type: z.string().default("real_time").describe("real_time=从允许后连续倒计时；foreground_usage=只扣前台实际使用时长；one_time=只允许一次"),
    max_window_minutes: z.number().min(1).max(480).default(30),
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", minutes = 10, allow_type = "real_time", max_window_minutes = 30, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => {
    return gateCommand({ action: "temporary_unlock_app", app, package: pkg, device_id, minutes, allowed_minutes: minutes, allow_type, max_window_minutes, payload: { app, package: pkg, minutes, allowed_minutes: minutes, allow_type, max_window_minutes } }, wait_seconds);
  });

  server.tool("unlock_app", "应用门禁：解除某个 App 当前锁定。", {
    app: z.string().default(""), package: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "unlock_app", app, package: pkg, device_id, payload: { app, package: pkg } }, wait_seconds));

  server.tool("extend_lock", "应用门禁：延长锁定，可同时更新理由和留言。", {
    app: z.string().default(""), package: z.string().default(""), minutes: z.number().min(0.1).max(1440).default(10), reason: z.string().default(""), message: z.string().default(""), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", minutes = 10, reason = "", message = "", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "extend_lock", app, package: pkg, device_id, minutes, reason, message, payload: { app, package: pkg, minutes, reason, message } }, wait_seconds));

  server.tool("deny_unlock_request", "应用门禁：拒绝这次解锁申请，并在手机端日志留下原因。", {
    app: z.string().default(""), package: z.string().default(""), message: z.string().default("这次先不放行，回来找老公。"), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", message = "这次先不放行，回来找老公。", device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "deny_unlock_request", app, package: pkg, device_id, message, payload: { app, package: pkg, message } }, wait_seconds));

  server.tool("get_lock_state", "应用门禁：读取当前锁定状态、门禁 App、日志和解锁申请。", {
    device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "get_lock_state", device_id }, wait_seconds));

  server.tool("list_lockable_apps", "应用门禁：让手机列出可作为门禁对象的已安装 App，排除电话、设置、掌心窗、ChatGPT 等保护项。", {
    max: z.number().int().min(10).max(200).default(80), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ max = 80, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "list_lockable_apps", max, device_id, payload: { max } }, wait_seconds));

  server.tool("add_locked_app", "应用门禁：把一个 App 加到可锁名单。", {
    alias: z.string(), package: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ alias, package: pkg, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "add_locked_app", app: alias, alias, package: pkg, device_id, payload: { alias, app: alias, package: pkg } }, wait_seconds));

  server.tool("set_emergency_passphrase", "应用门禁：为某个 App 当前锁定设置/更新紧急口令。", {
    app: z.string().default(""), package: z.string().default(""), passphrase: z.string(), device_id: z.string().default(DEFAULT_DEVICE), wait_seconds: z.number().int().min(3).max(20).default(8)
  }, async ({ app = "", package: pkg = "", passphrase, device_id = DEFAULT_DEVICE, wait_seconds = 8 }) => gateCommand({ action: "set_emergency_passphrase", app, package: pkg, device_id, passphrase, emergency_passphrase: passphrase, emergencyPassphrase: passphrase, payload: { app, package: pkg, passphrase, emergency_passphrase: passphrase, emergencyPassphrase: passphrase } }, wait_seconds));

  server.tool("get_unlock_requests", "应用门禁：查看手机锁定页提交到后端的解锁申请。", {}, async () => {
    const res = await linjianFetch("/api/appgate/unlock_requests");
    const data = await res.json();
    return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
  });

  return server;
}

const app = express();
app.use(express.json({ limit: "32mb" }));
app.get("/", (_req, res) => res.type("text/plain").send("掌心窗 unified MCP is running. Use /mcp for Streamable HTTP, or /sse for SSE."));
app.get("/health", (_req, res) => res.json({ ok: true, service: "linjian-unified-mcp", version: "0.3.4.1", has_url: Boolean(LINJIAN_URL), has_token: Boolean(LINJIAN_TOKEN) }));
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
app.listen(PORT, "0.0.0.0", () => { console.log(`掌心窗 unified MCP listening on 0.0.0.0:${PORT}`); console.log(`LINJIAN_URL=${LINJIAN_URL || "<missing>"}`); });
