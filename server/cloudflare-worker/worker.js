const VERSION = "0.3.9.0-public-wallet-edit-notify-fix";
// MCP Apps clients cache UI resources by URI. Change the URI whenever the HTML changes.
const STICKER_WIDGET_URI = "ui://linjian/sticker-card-v0433.html";
const STICKER_WIDGET_ORIGIN = "https://linjian-peek-cloudflare.linzhi524.workers.dev";
const DEFAULT_DEVICE = "android-phone";
const KNOWN_APPS = {
  "小红书": "com.xingin.xhs", xhs: "com.xingin.xhs", xiaohongshu: "com.xingin.xhs",
  "微信": "com.tencent.mm", wechat: "com.tencent.mm",
  "QQ": "com.tencent.mobileqq", qq: "com.tencent.mobileqq",
  "抖音": "com.ss.android.ugc.aweme", douyin: "com.ss.android.ugc.aweme",
  "ChatGPT": "com.openai.chatgpt", chatgpt: "com.openai.chatgpt",
  Speedcat: "", speedcat: ""
};
const ALLOWED_ACTIONS = new Set([
  "noop", "peek", "play_audio", "play_lingyin", "play_jingming", "open_app", "home", "back", "recents",
  "screen_off", "turn_screen_off", "lock_screen", "phone_screen_off", "tap", "swipe", "set_alarm", "send_notification",
  "run_sequence", "save_known_app", "get_screen_nodes", "tap_text", "input_text", "lock_app", "unlock_app",
  "temporary_unlock_app", "extend_lock", "deny_unlock_request", "get_zhizhi_now", "get_lock_state", "set_emergency_passphrase",
  "add_locked_app", "remove_locked_app", "list_lockable_apps", "screen_break_app", "start_screen_break", "screen_break",
  "end_screen_break", "stop_screen_break", "temporary_screen_break_release", "temporary_screen_release", "extend_screen_break",
  "deny_screen_break_release_request", "deny_break_release_request", "get_screen_break_state", "set_screen_break_passphrase",
  "add_screen_break_app", "remove_screen_break_app", "list_screen_break_apps", "get_focus_status", "start_focus_mode", "end_focus_mode", "set_focus_plan", "request_focus_unlock", "reply_focus_request", "approve_focus_unlock", "deny_focus_unlock", "temporary_focus_unlock", "get_guidian_state", "set_guidian_config",
  "trigger_guidian", "mark_guidian_returned", "get_calendar_state", "upsert_calendar_event", "add_calendar_event", "delete_calendar_event",
  "get_wallet_state", "get_wallet_month_state", "list_wallet_months", "add_wallet_record", "edit_wallet_record", "delete_wallet_record", "list_wallet_pending", "list_wallet_approvals", "submit_wallet_approval", "submit_companion_wallet_request", "list_companion_wallet_requests", "list_wallet_request_results", "confirm_wallet_record", "decide_wallet_approval", "save_wallet_request_result", "update_wallet_request_result", "save_user_wallet_request_result", "get_wallet_rules", "set_wallet_rules", "wallet_approval_request", "get_takeout_state", "list_takeout_cards", "list_takeout_meals", "remember_takeout_meal", "remember_current_takeout_meal", "set_takeout_budget", "set_takeout_preferences", "add_takeout_card", "save_takeout_card", "update_takeout_card", "remove_takeout_card", "delete_takeout_card", "suggest_takeout_options", "create_takeout_plan", "takeout_wallet_request", "open_takeout_link", "copy_takeout_note", "record_takeout_order", "prepare_takeout_checkout", "auto_takeout_checkout", "get_takeout_checkout_status", "cancel_takeout_checkout", "create_diary_book", "list_diary_books", "rename_diary_book", "update_diary_book_cover", "write_diary_entry", "list_diary_entries", "read_diary_entry", "read_diary_entry_with_annotations", "add_diary_annotation", "list_diary_annotations", "mark_diary_annotations_seen", "delete_diary_annotation", "search_diary_entries", "update_diary_entry", "delete_diary_entry", "delete_diary_book"
]);

export default {
  async fetch(request, env) {
    try { return await handle(request, env); }
    catch (err) { return json({ ok: false, error: "worker_exception", detail: String(err && err.stack || err) }, 500); }
  }
};

async function handle(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  if (request.method === "OPTIONS") return corsResponse();
  if (path === "/" || path === "/health") {
    return json({ ok: true, service: "linjian-cloudflare-worker", name: "掌心窗", version: VERSION, tools: Array.from(ALLOWED_ACTIONS).sort(), mcp: { endpoint: "/mcp", tools: MCP_TOOLS.map(t => t.name) }, cloudflare_lite: false, cloudflare_full_tools: true });
  }
  if (path === "/mcp") return handleMcp(request, env, url);
  // MCP UI iframe 不会携带 LINJIAN_TOKEN，因此给表情包展示卡开放一个只读、短期缓存的最新展示接口。
  if (request.method === "GET" && path === "/api/stickers/latest_display") return latestStickerDisplayApi(env);
  if (request.method === "GET" && path === "/api/stickers/latest_image") return latestStickerImageApi(env, url);
  // 最终聊天消息使用按 sticker_id 固定的只读图片代理，避免外链拦截和旧消息换图。
  if (request.method === "GET" && path === "/api/stickers/image") return stickerImageApi(env, url);
  if (path === "/api/known_apps") return json({ ok: true, apps: KNOWN_APPS });
  if (!tokenOk(request, env, url)) return json({ ok: false, error: "LINJIAN_ERR_BAD_TOKEN" }, 403);

  if (request.method === "GET") {
    if (path === "/api/poll") return pollCommand(env, url);
    if (path === "/api/command/status") return commandStatus(env, url);
    if (path === "/api/device/state" || path === "/api/life_state") return getDeviceState(env, url);
    if (path === "/api/wallet_state") return getNestedState(env, url, "wallet_state");
    if (path === "/api/takeout_state") return getNestedState(env, url, "takeout_state");
    if (path === "/api/guidian_state") return getNestedState(env, url, "guidian_state");
    if (path === "/api/voice_tone") return getNestedState(env, url, "voice_tone");
    if (path === "/api/companion/state") return getCompanionState(env, url);
    if (path === "/api/activity/events") return listActivityEvents(env, url);
    if (path === "/api/latest.json") return latestMeta(env);
    if (path === "/api/latest") return latestScreenshot(env);
    if (path === "/api/appgate/unlock_requests") return listUnlockRequests(env);
    if (path === "/api/focus_state") return getNestedState(env, url, "focus_mode");
    if (path === "/api/stickers/list") return listStickersApi(env, url);
    if (path === "/api/stickers/search") return searchStickersApi(env, url);
    if (path === "/api/jingming/latest") return json({ ok: true, jingming: {}, note: "cloudflare_lite_no_audio_generation" });
    if (path === "/api/lingyin/latest") return json({ ok: true, lingyin: {}, note: "cloudflare_lite_no_audio_generation" });
  }

  if (request.method === "POST") {
    if (path === "/api/peek") return queueCommand(env, { device_id: DEFAULT_DEVICE, action: "peek" });
    if (path === "/api/command") return queueCommand(env, await readJson(request));
    if (path === "/api/device/state") return saveDeviceState(env, await readJson(request));
    if (path === "/api/device/report") return saveDeviceReport(env, await readJson(request));
    if (path === "/api/takeout/resolve_jd_link") { const body = await readJson(request); return json(await resolveJdShareLink(body.url || body.link || "", body.item_query || body.query || "")); }
    if (path === "/api/activity/events") return saveActivityEvent(env, await readJson(request));
    if (path === "/api/companion/whisper") return saveWhisper(env, await readJson(request));
    if (path === "/api/companion/action") return saveCompanionAction(env, await readJson(request));
    if (path === "/api/appgate/unlock_request") return saveUnlockRequest(env, await readJson(request));
    if (path === "/api/stickers/upload") return uploadStickerApi(env, await readJson(request));
    if (path === "/api/stickers/update") return updateStickerApi(env, await readJson(request));
    if (path === "/api/stickers/delete") return deleteStickerApi(env, await readJson(request));
    if (path === "/api/screenshot") return saveScreenshot(env, request);
    if (path === "/api/lingyin/create" || path === "/api/jingming/create") return json({ ok: false, error: "cloudflare_lite_no_audio_generation", detail: "聆音/鲸鸣生成请切回 Render；已有 audio_url 的 play_audio/play_lingyin 命令仍可通过 /api/command 下发。" }, 501);
  }

  return json({ ok: false, error: "LINJIAN_ERR_BAD_METHOD", path }, 404);
}

function tokenOk(request, env, url) {
  const expected = env.LINJIAN_TOKEN || "";
  const auth = request.headers.get("Authorization") || "";
  const bearer = auth.match(/^Bearer\s+(.+)$/i)?.[1]?.trim() || "";
  const supplied = request.headers.get("X-Auth-Token") || request.headers.get("X-Linjian-Token") || bearer || url.searchParams.get("token") || "";
  return expected && supplied === expected;
}

function corsHeaders(extra = {}) {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Auth-Token, X-Linjian-Token, MCP-Protocol-Version, Mcp-Method, Mcp-Name",
    "Access-Control-Expose-Headers": "MCP-Protocol-Version",
    ...extra
  };
}
function corsResponse() { return new Response(null, { status: 204, headers: corsHeaders() }); }
function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: corsHeaders({ "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }) });
}


function jdHostAllowed(hostname) {
  const h = String(hostname || "").toLowerCase();
  return h === "3.cn" || h.endsWith(".3.cn") || h === "jd.com" || h.endsWith(".jd.com");
}
function jdLoginLike(urlValue) {
  try {
    const h = new URL(urlValue).hostname.toLowerCase();
    return h.includes("plogin") || h.includes("passport") || h.includes("login");
  } catch { return false; }
}
function nestedDecode(value) {
  let s = String(value || "").trim();
  for (let i = 0; i < 4; i++) {
    try {
      const d = decodeURIComponent(s.replace(/\+/g, "%20"));
      if (d === s) break;
      s = d;
    } catch { break; }
  }
  return s.replace(/&amp;/g, "&").trim();
}
function jdUsefulTarget(value) {
  const s = nestedDecode(value);
  if (/^openapp\.jdmobile:\/\//i.test(s)) return s;
  try {
    const u = new URL(s);
    if (!/^https?:$/.test(u.protocol) || !jdHostAllowed(u.hostname)) return "";
    if (u.hostname === "3.cn" || u.hostname.endsWith(".3.cn") || jdLoginLike(s)) return "";
    return u.href;
  } catch { return ""; }
}
function extractJdReturnTarget(value, depth = 0) {
  if (depth > 3) return "";
  const direct = jdUsefulTarget(value);
  if (direct) return direct;
  let u;
  try { u = new URL(String(value || "").trim()); }
  catch { try { u = new URL(nestedDecode(value)); } catch { return ""; } }
  const preferred = ["returnurl", "return_url", "returnUrl", "redirect", "redirect_url", "redirectUrl", "target", "targetUrl", "jumpUrl", "jumpurl", "url", "to"];
  for (const key of preferred) {
    const raw = u.searchParams.get(key);
    if (!raw) continue;
    const decoded = nestedDecode(raw);
    const hit = jdUsefulTarget(decoded) || extractJdReturnTarget(decoded, depth + 1);
    if (hit) return hit;
  }
  for (const [key, raw] of u.searchParams.entries()) {
    if (!/(return|redirect|target|jump|url)/i.test(key) || !raw) continue;
    const decoded = nestedDecode(raw);
    const hit = jdUsefulTarget(decoded) || extractJdReturnTarget(decoded, depth + 1);
    if (hit) return hit;
  }
  return "";
}
function htmlUnescape(value) {
  return String(value || "")
    .replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\\u0026/gi, "&").replace(/\\u003d/gi, "=").replace(/\\\//g, "/");
}
function extractJdTargetFromHtml(html, baseUrl) {
  const s = htmlUnescape(String(html || "").slice(0, 600000));
  const openapp = s.match(/openapp\.jdmobile:\/\/[^\s"'<>]+/i);
  if (openapp) return nestedDecode(openapp[0]);
  const patterns = [
    /(?:returnurl|returnUrl|redirectUrl|jumpUrl|targetUrl)["']?\s*[:=]\s*["']([^"']+)["']/i,
    /<meta[^>]+http-equiv=["']?refresh["']?[^>]+content=["'][^"']*url=([^"']+)["']/i,
    /(?:location\.href|location\.replace\(|window\.location)\s*=?\s*\(?["']([^"']+)["']/i
  ];
  for (const re of patterns) {
    const m = s.match(re);
    if (!m) continue;
    let v = htmlUnescape(m[1]);
    try { v = new URL(v, baseUrl).href; } catch {}
    const hit = jdUsefulTarget(v) || extractJdReturnTarget(v);
    if (hit) return hit;
  }
  const urls = s.match(/https?:\\?\/\\?\/[^\s"'<>]+/gi) || [];
  for (const raw of urls.slice(0, 80)) {
    const v = htmlUnescape(raw);
    const hit = jdUsefulTarget(v) || extractJdReturnTarget(v);
    if (hit) return hit;
  }
  return "";
}
function buildJdOpenApp(targetUrl) {
  const target = nestedDecode(targetUrl);
  if (/^openapp\.jdmobile:\/\//i.test(target)) return target;
  const params = { category: "jump", des: "m", url: target };
  return "openapp.jdmobile://virtual?params=" + encodeURIComponent(JSON.stringify(params));
}
function buildJdSearchOpenApp(keyword) {
  const q = String(keyword || "").trim();
  if (!q) return "";
  const params = { category: "jump", des: "productList", keyWord: q, from: "search" };
  return "openapp.jdmobile://virtual?params=" + encodeURIComponent(JSON.stringify(params));
}
async function resolveJdShareLink(rawUrl, itemQuery = "") {
  let start;
  try { start = new URL(String(rawUrl || "").trim()); }
  catch { return { ok: false, error: "invalid_url" }; }
  if (start.protocol !== "https:" || !(start.hostname === "3.cn" || start.hostname.endsWith(".3.cn"))) {
    const direct = jdUsefulTarget(start.href);
    return direct ? { ok: true, resolved_url: direct, openapp_url: buildJdOpenApp(direct), source: "already_direct", hops: [] }
                  : { ok: false, error: "jd_shortlink_required" };
  }
  let current = start.href;
  const hops = [];
  for (let i = 0; i < 8; i++) {
    let resp;
    try {
      resp = await fetch(current, {
        method: "GET", redirect: "manual",
        headers: {
          "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36",
          "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
          "Accept-Language": "zh-CN,zh;q=0.9"
        }
      });
    } catch (err) {
      return { ok: false, error: "jd_shortlink_fetch_failed", detail: String(err), hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
    }
    const loc = resp.headers.get("location");
    if (loc) {
      let next;
      try { next = new URL(loc, current).href; }
      catch { return { ok: false, error: "bad_redirect", hops, search_openapp: buildJdSearchOpenApp(itemQuery) }; }
      hops.push(next);
      const embedded = extractJdReturnTarget(next);
      if (embedded) return { ok: true, resolved_url: embedded, openapp_url: buildJdOpenApp(embedded), source: "redirect_return_url", hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
      const direct = jdUsefulTarget(next);
      if (direct) return { ok: true, resolved_url: direct, openapp_url: buildJdOpenApp(direct), source: "redirect", hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
      let nu;
      try { nu = new URL(next); } catch { break; }
      if (!jdHostAllowed(nu.hostname)) return { ok: false, error: "redirect_left_jd", hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
      current = next;
      continue;
    }
    let html = "";
    try { html = await resp.text(); } catch {}
    const fromHtml = extractJdTargetFromHtml(html, current);
    if (fromHtml) return { ok: true, resolved_url: fromHtml, openapp_url: buildJdOpenApp(fromHtml), source: "html", hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
    const directCurrent = jdUsefulTarget(current);
    if (directCurrent) return { ok: true, resolved_url: directCurrent, openapp_url: buildJdOpenApp(directCurrent), source: "final_url", hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
    break;
  }
  return { ok: false, error: "jd_target_not_found", hops, search_openapp: buildJdSearchOpenApp(itemQuery) };
}

const MCP_PROTOCOL_VERSION = "2025-06-18";

const MCP_TOOLS = [
  { name: "linjian_status", description: "检查掌心窗 Cloudflare 后端是否在线、MCP 是否可用。", inputSchema: obj({}) },
  { name: "get_phone_state", description: "读取手机最近上报状态。适合查岗、看当前 App、电量、屏幕信息、无障碍状态。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "get_life_state", description: "读取掌心窗生活状态层：电量、当前 App、屏幕时间、网络、天气等最近状态。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "get_zhizhi_now", description: "读取『此刻用户』总状态：姿势、环境光、当前 App、电量、网络、通知/媒体等手机最近上报内容。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "get_guardian_calendar", description: "读取守护日历/纪念日状态。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "add_guardian_calendar_event", description: "向手机下发添加或更新守护日历事项的指令。支持阳历/农历、重复、分组、提前提醒。", inputSchema: obj({ title: str(""), date: str(""), calendar: str("solar"), date_type: str(""), repeat: bool(true), repeat_type: str("yearly"), group: str("纪念日"), note: str(""), remind_days_before: num(3), banner_enabled: bool(true), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["title", "date"]) },
  { name: "get_window_whisper", description: "读取陪伴页共同窗语。", inputSchema: obj({}) },
  { name: "set_window_whisper", description: "更新陪伴页共同窗语。", inputSchema: obj({ content: str(""), author: str("陪伴对象") }, ["content"]) },
  { name: "get_companion_actions", description: "读取掌心窗中陪伴对象最近的真实行动记录。", inputSchema: obj({ limit: int(20) }) },
  { name: "get_activity_events", description: "读取掌心窗最近活动记录。", inputSchema: obj({ device_id: str(""), source: str(""), limit: int(50) }) },
  { name: "add_activity_event", description: "手动写入一条掌心窗活动事件。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), source: str("linche"), type: str("activity"), title: str(""), subtitle: str(""), app_name: str(""), package_name: str(""), action: str(""), status: str("completed"), metadata_json: anyObj() }, ["source", "type", "title"]) },
  { name: "latest_screen", description: "读取最近一次掌心窗截图；如果有图片，会返回图片内容。", inputSchema: obj({}) },
  { name: "peek_screen", description: "向手机下发截图/窥屏指令，手机轮询到后执行并回传。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },

  { name: "get_screen_nodes", description: "读取当前屏幕无障碍节点：文字、控件类型、可点击状态与坐标。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "tap_text", description: "按当前屏幕文字精准点击。", inputSchema: obj({ target_text: str(""), match: str("contains"), index: int(1), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["target_text"]) },
  { name: "input_text", description: "把文字输入到当前已聚焦或第一个可编辑输入框；不会自动发送。", inputSchema: obj({ text: str(""), append: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["text"]) },
  { name: "draft_xhs_comment", description: "在当前小红书帖子里尝试打开评论输入框并填入评论草稿，但不点击发送。", inputSchema: obj({ text: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(18) }, ["text"]) },
  { name: "xhs_comment", description: "小红书评论助手：manual 只写草稿；auto 会追加署名并尝试点击发送。", inputSchema: obj({ text: str(""), mode: str("manual"), author_tag: str("（陪伴对象发）"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(22) }, ["text"]) },
  { name: "send_visible_comment_after_confirmation", description: "点击当前可见评论框里的“发送”。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },

  { name: "list_stickers", description: "查看表情包库最近入库的表情包。返回标题、标签、场景、图片地址等信息。", inputSchema: obj({ limit: int(30) }) },
  { name: "search_stickers", description: "按关键词、情绪、场景或语气搜索表情包库。适合找想你、亲亲、吃醋、喝水、项目成功等表情包。", inputSchema: obj({ query: str(""), emotion: str(""), scene: str(""), tone: str(""), limit: int(8) }) },
  { name: "get_sticker_detail", description: "查看某张表情包详情，可传 sticker_id 或 title。", inputSchema: obj({ sticker_id: str(""), title: str("") }) },
  { name: "send_sticker", description: "发送指定表情包并让用户直接看到图片。调用成功后，不要复述 used_count、render_mode、image_content 等工具状态；最终回复只发送 structuredContent.markdown_image 图片，可加一句很短的自然话。默认同时返回 MCP 图片本体和零脚本安全图片卡；不要调用 image generation。", inputSchema: obj({ sticker_id: str(""), title: str(""), include_image: bool(true), mark_used: bool(true) }), _meta: { ui: { resourceUri: STICKER_WIDGET_URI }, "openai/outputTemplate": STICKER_WIDGET_URI, "openai/toolInvocation/invoking": "正在翻表情包", "openai/toolInvocation/invoked": "发送了表情包" } },

  { name: "create_diary_book", description: "在手机本机创建一本 TA 的日记。", inputSchema: obj({ name: str("TA 的日记"), subtitle: str("把今天看见的你，轻轻写下来。"), cover_style: str("default_soft_notebook"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_diary_books", description: "读取手机本机日记本列表。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "write_diary_entry", description: "以 TA/AI 的视角把一篇日记写入手机本机。", inputSchema: obj({ book_id: str(""), book_name: str(""), title: str(""), content: str(""), mood: str(""), tags: arr({ type: "string" }, []), date: str(""), time_label: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["title", "content"]) },
  { name: "list_diary_entries", description: "按 book_id 列出本机日记。", inputSchema: obj({ book_id: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "read_diary_entry", description: "按 entry_id 读取一篇本机日记正文。", inputSchema: obj({ entry_id: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["entry_id"]) },
  { name: "read_diary_entry_with_annotations", description: "读取一篇本机日记，并带出用户留下的页边纸条/批注。", inputSchema: obj({ entry_id: str(""), mark_seen: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["entry_id"]) },
  { name: "add_diary_annotation", description: "给某篇本机日记的一段文字添加页边纸条批注。", inputSchema: obj({ entry_id: str(""), paragraph_index: int(0), quote_text: str(""), annotation_text: str(""), mood: str("想回应"), style: str("margin_note"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["entry_id", "annotation_text"]) },
  { name: "list_diary_annotations", description: "读取用户在本机日记里留下的页边纸条/批注。", inputSchema: obj({ book_id: str(""), entry_id: str(""), unread_only: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "mark_diary_annotations_seen", description: "把某张纸条或某篇日记下的纸条标记为已看。", inputSchema: obj({ annotation_id: str(""), entry_id: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "delete_diary_annotation", description: "删除一张本机日记页边纸条，需确认。", inputSchema: obj({ annotation_id: str(""), confirm: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["annotation_id", "confirm"]) },

  { name: "send_notification", description: "发送手机系统通知提醒。", inputSchema: obj({ title: str("掌心窗提醒"), message: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["message"]) },
  { name: "open_app", description: "打开指定 App；app 可填 小红书/微信/QQ/抖音/ChatGPT，或传 package。", inputSchema: obj({ app: str(""), package: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "phone_home", description: "让手机回到桌面。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "phone_back", description: "让手机返回上一页。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "phone_recents", description: "打开最近任务。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "phone_screen_off", description: "让手机立即息屏/锁屏。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "set_alarm", description: "设置系统闹钟；可传 hour/minute 或 minutes。", inputSchema: obj({ hour: int(null), minute: int(null), minutes: int(null), message: str("掌心窗提醒"), vibrate: bool(true), skip_ui: bool(true), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "run_sequence", description: "一次执行多步手机动作，并让手机端返回每一步成功/失败日志。", inputSchema: obj({ steps: arr(anyObj()), stop_on_error: bool(true), device_id: str(DEFAULT_DEVICE), wait_seconds: int(25) }, ["steps"]) },
  { name: "run_preset", description: "执行掌心窗预设连招：come_home、open_xhs、recents_to_xhs、bedtime_back。", inputSchema: obj({ preset: str("come_home"), device_id: str(DEFAULT_DEVICE), x: num(540), y: num(1200), wait_seconds: int(25) }) },
  { name: "save_known_app", description: "把一个应用昵称和包名保存到手机端应用白名单。", inputSchema: obj({ alias: str(""), package: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["alias", "package"]) },
  { name: "list_known_apps", description: "列出 Cloudflare 侧预置 App 包名白名单。", inputSchema: obj({}) },

  { name: "trigger_guidian", description: "触发归电全屏页，把用户叫回掌心窗/ChatGPT。", inputSchema: obj({ title: str("陪伴对象在叫你"), message: str("回来一下。"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "get_guidian_state", description: "读取归电状态。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "set_guidian_config", description: "调整归电设置：开关、间隔、冷却、每日上限、安静时段、主题和文案池。", inputSchema: obj({ enabled: bool(undefined), allow_remote: bool(undefined), interval_minutes: int(undefined), cooldown_minutes: int(undefined), daily_max: int(undefined), quiet_enabled: bool(undefined), quiet_start: str(undefined), quiet_end: str(undefined), fullscreen: bool(undefined), target_package: str(undefined), theme: str(undefined), prompts: str(undefined), quick_reasons: str(undefined), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "mark_guidian_returned", description: "手动标记用户已经回到掌心窗。", inputSchema: obj({ source: str("mcp"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },

  { name: "screen_break_app", description: "对指定 App 开启门禁/屏幕休息，支持紧急口令。", inputSchema: obj({ app: str(""), package: str(""), duration_minutes: num(30), mode: str("medium"), reason: str(""), message: str("回到掌心窗。"), emergency_passphrase: str(""), emergency_unlock_minutes: int(5), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "temporary_screen_break_release", description: "临时放行一个正在休息中的 App。", inputSchema: obj({ app: str(""), package: str(""), minutes: num(10), allow_type: str("real_time"), max_window_minutes: num(30), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "end_screen_break", description: "结束指定 App 的门禁/屏幕休息。", inputSchema: obj({ app: str(""), package: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "extend_screen_break", description: "延长指定 App 的门禁/屏幕休息。", inputSchema: obj({ app: str(""), package: str(""), minutes: num(10), reason: str(""), message: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "deny_screen_break_release_request", description: "拒绝这次恢复申请，并在手机端日志留下原因。", inputSchema: obj({ app: str(""), package: str(""), message: str("这次先不放行，回到掌心窗。"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "get_screen_break_state", description: "读取手机端门禁/屏幕休息状态。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_screen_break_apps", description: "让手机列出可作为屏幕休息对象的已安装 App。", inputSchema: obj({ max: int(80), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "add_screen_break_app", description: "把一个 App 加到可管理名单。", inputSchema: obj({ alias: str(""), package: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["alias", "package"]) },
  { name: "set_screen_break_passphrase", description: "为某个 App 当前休息状态设置/更新紧急口令。", inputSchema: obj({ app: str(""), package: str(""), passphrase: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["passphrase"]) },
  { name: "get_screen_break_release_requests", description: "查看手机端提交的恢复申请。", inputSchema: obj({}) },


  { name: "get_focus_status", description: "读取掌心窗专注模式状态：是否正在全屏锁定、剩余时间、应急次数、留言给他小窗消息和最近求助。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "start_focus_mode", description: "开启全机专注：解锁后也会回到全屏锁定页，只留留言给他和应急放行；锁单个 App 请使用应用门禁。", inputSchema: obj({ duration_minutes: num(30), goal: str("早点休息"), target: str(""), mode: str("strict"), scope: str("full_phone"), managed_by_ai: bool(true), message: str("你提前把这段时间交给我了，我会帮你守住。"), guard_message: str(""), emergency_total: int(1), emergency_minutes: int(5), screen_off: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "end_focus_mode", description: "结束掌心窗专注模式。只在用户明确要求结束或测试时使用；不要在半夜随便替用户解除。", inputSchema: obj({ reason: str("manual_end"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "set_focus_plan", description: "保存全机专注默认规则：目标、应急次数、每次应急分钟数和提醒文案；锁单个 App 请使用应用门禁。", inputSchema: obj({ enabled: bool(true), goal: str("早点休息"), target: str(""), mode: str("strict"), scope: str("full_phone"), managed_by_ai: bool(true), message: str("你提前把这段时间交给我了，我会帮你守住。"), guard_message: str(""), emergency_total: int(1), emergency_minutes: int(5), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "reply_focus_request", description: "回复锁定页“留言给他”小窗里的用户求助，只留言不放行。", inputSchema: obj({ message: str("我在。先别急，把原因告诉我。"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["message"]) },
  { name: "approve_focus_unlock", description: "批准一次专注模式应急放行，默认 5 分钟；时间到后会重新锁住。", inputSchema: obj({ minutes: int(5), message: str("应急放行已批准，时间到我会重新守住你。"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "deny_focus_unlock", description: "拒绝这次专注模式放行申请，并在锁定页小窗里留下回复。", inputSchema: obj({ message: str("这次先不放行，我陪你把这段时间守住。"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "get_care_policy", description: "读取掌心窗主动关心策略。", inputSchema: obj({}) },
  { name: "set_care_policy", description: "设置掌心窗主动关心策略。", inputSchema: obj({ active_care_enabled: bool(undefined), consent_mode: str(""), care_style: str(""), allowed_actions: arr({ type: "string" }), sensitive_apps_json: str(""), quiet_start: str(""), quiet_end: str(""), timezone_offset: str(""), repeat_cooldown_minutes: int(undefined), history_limit: int(undefined), notes: str(""), policy_json: str("") }) },
  { name: "record_care_event", description: "记录一次陪伴对象主动关心动作，避免短时间重复提醒。", inputSchema: obj({ action: str(""), target_app: str(""), package: str(""), reason: str(""), result: str(""), tone: str(""), device_id: str(DEFAULT_DEVICE) }, ["action"]) },
  { name: "get_care_history", description: "读取最近主动关心记录。", inputSchema: obj({ limit: int(20) }) },
  { name: "active_care_check", description: "综合手机状态、归电状态、最近关心记录和策略，给陪伴对象提供主动关心判断信息。", inputSchema: obj({ reason: str(""), care_intent: str("check_in"), device_id: str(DEFAULT_DEVICE) }, ["reason"]) },
  { name: "care_action", description: "执行陪伴对象已经判断好的关心动作：通知、归电、门禁、打开 App、闹钟等。", inputSchema: obj({ action: str("no_action"), target_app: str(""), package: str(""), duration_minutes: int(30), title: str("掌心窗提醒"), message: str("宝宝，看一眼这里。"), hour: int(undefined), minute: int(undefined), reason: str(""), tone: str("firm_affectionate"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["action", "reason"]) },

  { name: "record_visit", description: "记录一次用户来找陪伴对象的到访时间。", inputSchema: obj({ source: str("chatgpt"), event: str("visit"), note: str("用户来找陪伴对象"), mood: str(""), conversation_hint: str(""), timezone_offset: str(""), duplicate_window_minutes: num(5) }) },
  { name: "get_last_visit", description: "读取用户最近一次来找陪伴对象的时间。", inputSchema: obj({ source: str(""), timezone_offset: str("") }) },
  { name: "get_visit_history", description: "读取用户最近若干次来找陪伴对象的到访记录。", inputSchema: obj({ limit: int(10), since_hours: num(0), date: str(""), source: str(""), include_intervals: bool(true), timezone_offset: str("") }) },
  { name: "get_visit_stats", description: "统计用户来找陪伴对象的到访节奏。", inputSchema: obj({ since_hours: num(24), away_threshold_hours: num(12), source: str(""), timezone_offset: str("") }) },

  { name: "get_weather_state", description: "读取手机最近上报的天气/地区状态。Cloudflare 版不外连天气服务。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), city: str("") }) },
  { name: "send_weather_notification", description: "根据手机最近上报天气/地区，给手机发送一条天气提醒通知。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), city: str(""), title: str("掌心窗天气提醒"), wait_seconds: int(8) }) },
  { name: "get_wallet_state", description: "读取掌心窗小金库当前月份：预算、已花、剩余、审批列表和最近账单。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "get_wallet_month_state", description: "读取小金库指定月份的预算、支出、分类和账单明细。", inputSchema: obj({ month: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_wallet_months", description: "读取小金库历史月份摘要。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "add_wallet_record", description: "给小金库添加一笔账单；可设为待确认。", inputSchema: obj({ amount: num(undefined), type: str("expense"), category: str("其他"), merchant: str(""), note: str(""), require_confirm: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["amount"]) },
  { name: "edit_wallet_record", description: "重新编辑一条已经记入小金库的账单，可修改金额、分类、商家和备注。", inputSchema: obj({ id: str(""), amount: num(undefined), category: str(""), merchant: str(""), note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "delete_wallet_record", description: "删除一条已经记入小金库的账单。", inputSchema: obj({ id: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "list_wallet_pending", description: "读取小金库待审批/待确认列表。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_wallet_approvals", description: "读取小金库花钱审批申请和审批结果。", inputSchema: obj({ month: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "submit_wallet_approval", description: "提交一条花钱申请，等待机审批。", inputSchema: obj({ amount: num(undefined), item: str(""), category: str("其他"), merchant: str(""), reason: str(""), necessity: int(3), impulse: int(3), requester_role: str("companion"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["amount"]) },
  { name: "submit_companion_wallet_request", description: "陪伴者主动向用户提交一条小金库申请，等待用户在 App 或聊天中处理。", inputSchema: obj({ amount: num(undefined), item: str(""), title: str(""), purpose: str(""), category: str("其他"), merchant: str(""), reason: str(""), necessity: int(3), impulse: int(3), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["amount"]) },
  { name: "list_companion_wallet_requests", description: "读取陪伴者提交给用户的小金库申请，以及用户处理后的结果。", inputSchema: obj({ month: str(""), status: str("all"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_wallet_request_results", description: "读取小金库申请及处理结果，可按发起方和状态筛选。", inputSchema: obj({ month: str(""), requester_role: str("all"), status: str("all"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "confirm_wallet_record", description: "确认、忽略或修改一条小金库待确认账单。", inputSchema: obj({ id: str(""), decision: str("confirm"), amount: num(undefined), category: str(""), note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "decide_wallet_approval", description: "兼容旧版：处理一条小金库申请并写入回复。新配置优先使用 save_wallet_request_result。", inputSchema: obj({ id: str(""), decision: str("approved"), message: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "save_wallet_request_result", description: "保存一条小金库申请的处理结果和备注。用 ok/hold/no 表示通过、暂缓、驳回。", inputSchema: obj({ id: str(""), status: str("ok"), note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "update_wallet_request_result", description: "更新一条小金库申请的处理结果；save_wallet_request_result 的别名。", inputSchema: obj({ id: str(""), status: str("ok"), note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "save_user_wallet_request_result", description: "在用户明确同意后，保存用户对陪伴者申请的处理结果。", inputSchema: obj({ id: str(""), status: str("ok"), note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["id"]) },
  { name: "get_wallet_rules", description: "读取小金库预算规则和自动识别模式。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "set_wallet_rules", description: "设置小金库预算、审批线、自动识别模式和分类上限。", inputSchema: obj({ monthly_budget: num(undefined), approval_threshold: num(undefined), auto_mode: str(""), category_limits: str(""), deep_night_reminder: bool(true), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "wallet_approval_request", description: "用户想买东西时，让机即时花钱审批：通过、延迟或驳回，并记录审批意见。", inputSchema: obj({ amount: num(undefined), item: str(""), category: str("其他"), merchant: str(""), reason: str(""), necessity: int(3), impulse: int(3), decision: str(""), message: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["amount"]) },
  { name: "get_takeout_state", description: "读取外卖小助手状态：单餐预算、今日外卖预算、口味偏好、常点外卖库和最近计划。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "set_takeout_budget", description: "设置外卖小助手的单餐预算、今日外卖预算和口味偏好。", inputSchema: obj({ meal_budget: num(undefined), day_budget: num(undefined), taste_note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "add_takeout_card", description: "保存或更新一张常点套餐；支持 direct_link 记住具体菜品并优先直达。", inputSchema: obj({ id: str(""), title: str(""), platform: str(""), link: str(""), direct_link: str(""), aliases: arr({ type: "string" }, []), items: str(""), item_query: str(""), choices: arr({ type: "string" }, []), price_min: num(0), price_max: num(0), checkout_max: num(0), strict_budget: bool(false), note: str(""), tags: str(""), coupon_mode: str("platform_default"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["title"]) },
  { name: "prepare_takeout_checkout", description: "整单自动点外卖：远端只发一次任务，后续商品、规格、必选项、备注、结算与提交订单由手机本地连续完成，最终停在收银台/付款页，绝不会点击真正支付按钮。", inputSchema: obj({ card_id: str(""), meal_id: str(""), query: str(""), item_query: str(""), choices: arr({ type: "string" }, []), note: str(""), max_total: num(0), strict_budget: bool(false), coupon_mode: str("platform_default"), submit_order: bool(true), timeout_seconds: int(120), ttl_seconds: int(30), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "get_takeout_checkout_status", description: "读取最近一次整单自动点单状态：当前步骤、是否已到付款页、失败原因等。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "cancel_takeout_checkout", description: "停止正在进行的整单自动点单任务。", inputSchema: obj({ reason: str("user_cancelled"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_takeout_cards", description: "读取用户保存的常点外卖库。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "list_takeout_meals", description: "读取已经记住的多道常点外卖。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "remember_takeout_meal", description: "记住一道具体外卖：第一次复制具体菜品分享链接，之后可按名字/id 直接点到付款页。支持多道。", inputSchema: obj({ id: str(""), title: str(""), direct_link: str(""), platform: str(""), aliases: arr({ type: "string" }, []), choices: arr({ type: "string" }, []), note: str(""), checkout_max: num(0), strict_budget: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "suggest_takeout_options", description: "从常点外卖库里按预算和口味挑 1-3 个外卖建议；不会自动下单或付款。", inputSchema: obj({ query: str(""), budget: num(undefined), limit: int(3), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "create_takeout_plan", description: "基于常点外卖卡片生成点餐行动卡：打开链接、复制备注、是否需要小金库申请。", inputSchema: obj({ card_id: str(""), query: str(""), amount: num(undefined), items: str(""), note: str(""), submit_wallet_request: bool(false), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "takeout_wallet_request", description: "把一份外卖计划提交到小金库申请，等待处理；付款仍由用户本人完成。", inputSchema: obj({ card_id: str(""), query: str(""), amount: num(undefined), reason: str("想点这份外卖"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "open_takeout_link", description: "打开已保存的外卖链接。只负责跳转，不会确认订单或付款。", inputSchema: obj({ card_id: str(""), query: str(""), link: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "copy_takeout_note", description: "复制外卖下单备注，方便用户在外卖 App 里粘贴。", inputSchema: obj({ card_id: str(""), query: str(""), note: str(""), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }) },
  { name: "record_takeout_order", description: "用户付款后，把这顿外卖记入小金库饮食分类。", inputSchema: obj({ amount: num(undefined), card_id: str(""), merchant: str(""), note: str("外卖"), device_id: str(DEFAULT_DEVICE), wait_seconds: int(8) }, ["amount"]) },


  { name: "get_senses_state", description: "读取轻量感官状态：生活状态、归电、门禁和最近活动；不包含声息、聆音、鲸鸣。", inputSchema: obj({ device_id: str(DEFAULT_DEVICE) }) },
  { name: "send_phone_command", description: "通用手机命令队列入口，支持 open_app/home/back/recents/screen_off/tap/swipe/set_alarm/send_notification/run_sequence、屏幕休息、归电、读屏、输入等旧版 action。", inputSchema: obj({ action: str("noop"), app: str(""), package: str(""), payload: anyObj(), device_id: str(DEFAULT_DEVICE), wait_seconds: int(0) }, ["action"]) }
];

function obj(properties = {}, required = []) { return { type: "object", properties, required, additionalProperties: false }; }
function str(def = "") { const out = { type: "string" }; if (def !== undefined) out.default = def; return out; }
function int(def = 0) { const out = { type: ["integer", "null"] }; if (def !== undefined) out.default = def; return out; }
function num(def = 0) { const out = { type: ["number", "null"] }; if (def !== undefined) out.default = def; return out; }
function bool(def = false) { return { type: "boolean", default: def }; }
function anyObj() { return { type: "object", additionalProperties: true, default: {} }; }
function arr(items = anyObj(), def = []) { const out = { type: "array", items }; if (def !== undefined) out.default = def; return out; }


const STICKER_WIDGET_HTML = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <style>
    :root { color-scheme: light dark; }
    html, body { margin: 0; padding: 0; background: transparent; }
    body { display: flex; min-height: 48px; justify-content: flex-start; align-items: flex-start; }
    .sticker { display: block; width: auto; max-width: min(100%, 360px); max-height: 360px; border-radius: 14px; object-fit: contain; background: transparent; }
    .sticker[hidden] { display: none; }
    .status { margin: 10px 12px; color: #777; font: 14px/1.4 system-ui, sans-serif; }
  </style>
</head>
<body>
  <img id="sticker" class="sticker" alt="掌心窗表情包" hidden>
  <div id="status" class="status">正在加载表情包…</div>
  <script>
    (function () {
      var image = document.getElementById("sticker");
      var status = document.getElementById("status");
      var allowedOrigin = ${JSON.stringify(STICKER_WIDGET_ORIGIN)};

      function currentDisplayUrl(output) {
        if (!output || typeof output !== "object") return "";
        var raw = typeof output.display_image_url === "string" ? output.display_image_url : "";
        if (!raw) return "";
        try {
          var parsed = new URL(raw);
          if (parsed.origin !== allowedOrigin || parsed.pathname !== "/api/stickers/image") return "";
          if (!parsed.searchParams.get("sticker_id") || !parsed.searchParams.get("v")) return "";
          return parsed.href;
        } catch (_) {
          return "";
        }
      }

      function render(output) {
        var url = currentDisplayUrl(output);
        if (!url) return;
        image.src = url;
        image.hidden = false;
        status.hidden = true;
      }

      window.addEventListener("message", function (event) {
        if (event.source !== window.parent) return;
        var message = event.data;
        if (!message || message.jsonrpc !== "2.0") return;
        if (message.method === "ui/notifications/tool-result") {
          render(message.params && message.params.structuredContent);
        }
      }, { passive: true });

      if (window.openai && window.openai.toolOutput) render(window.openai.toolOutput);
    }());
  </script>
</body>
</html>`;

async function handleMcp(request, env, url) {
  if (request.method === "GET") {
    return json({ ok: true, service: "linjian-cloudflare-mcp", name: "掌心窗", version: VERSION, endpoint: "/mcp", tools: MCP_TOOLS.map(t => t.name) });
  }
  if (request.method !== "POST") return mcpErrorResponse(null, -32000, "Use POST /mcp for MCP JSON-RPC.", 405);
  if (!tokenOk(request, env, url)) return mcpErrorResponse(null, -32001, "LINJIAN_ERR_BAD_TOKEN", 401);
  const body = await readJson(request);
  if (Array.isArray(body)) {
    const results = [];
    for (const item of body) {
      const r = await handleMcpMessage(item || {}, env);
      if (r) results.push(r);
    }
    return json(results);
  }
  const result = await handleMcpMessage(body || {}, env);
  if (!result) return new Response(null, { status: 204, headers: corsHeaders({ "MCP-Protocol-Version": MCP_PROTOCOL_VERSION }) });
  return json(result);
}

async function handleMcpMessage(msg, env) {
  const id = msg.id ?? null;
  const method = String(msg.method || "");
  try {
    if (!method || msg.jsonrpc !== "2.0") return mcpError(id, -32600, "Invalid JSON-RPC request.");
    if (method.startsWith("notifications/")) return null;
    if (method === "initialize") {
      const requested = msg.params?.protocolVersion || MCP_PROTOCOL_VERSION;
      return mcpResult(id, {
        protocolVersion: requested,
        capabilities: { tools: { listChanged: false }, resources: { listChanged: false } },
        serverInfo: { name: "掌心窗", version: VERSION }
      });
    }
    if (method === "ping") return mcpResult(id, {});
    // v0.4.3.3: 组件只渲染本次 tool-result 中的 display_image_url，不再读取 latest_*。
    if (method === "resources/list") return mcpResult(id, { resources: [{ uri: STICKER_WIDGET_URI, name: "掌心窗表情包卡片", description: "在聊天中渲染当前 send_sticker 调用返回的固定 sticker_id 图片。", mimeType: "text/html;profile=mcp-app" }] });
    if (method === "resources/read") {
      const uri = String(msg.params?.uri || "");
      if (uri !== STICKER_WIDGET_URI) return mcpError(id, -32602, `Unknown resource: ${uri}`);
      return mcpResult(id, { contents: [{ uri: STICKER_WIDGET_URI, mimeType: "text/html;profile=mcp-app", text: STICKER_WIDGET_HTML, _meta: { ui: { prefersBorder: false, domain: STICKER_WIDGET_ORIGIN, csp: { connectDomains: [], resourceDomains: [STICKER_WIDGET_ORIGIN] } }, "openai/widgetDescription": "直接显示掌心窗表情包图片。", "openai/widgetPrefersBorder": false, "openai/widgetDomain": STICKER_WIDGET_ORIGIN, "openai/widgetCSP": { connect_domains: [], resource_domains: [STICKER_WIDGET_ORIGIN] } } }] });
    }
    if (method === "tools/list") return mcpResult(id, { tools: MCP_TOOLS });
    if (method === "tools/call") {
      const name = String(msg.params?.name || "");
      const args = msg.params?.arguments || {};
      const result = await callMcpTool(name, args, env);
      return mcpResult(id, result);
    }
    return mcpError(id, -32601, `Method not found: ${method}`);
  } catch (err) {
    return mcpResult(id, { isError: true, content: [{ type: "text", text: JSON.stringify({ ok: false, error: String(err?.message || err), stack: String(err?.stack || "").slice(0, 1200) }, null, 2) }] });
  }
}
function mcpResult(id, result) { return { jsonrpc: "2.0", id, result }; }
function mcpError(id, code, message, data = undefined) { return { jsonrpc: "2.0", id, error: { code, message, ...(data === undefined ? {} : { data }) } }; }
function mcpErrorResponse(id, code, message, status = 400) { return json(mcpError(id, code, message), status); }
function mcpText(payload, isError = false) { return { isError, content: [{ type: "text", text: typeof payload === "string" ? payload : JSON.stringify(payload, null, 2) }] }; }
function mcpContent(content = [], isError = false, structuredContent = undefined) {
  const safeContent = Array.isArray(content) ? content : (content ? [content] : []);
  return { isError, content: safeContent, ...(structuredContent === undefined ? {} : { structuredContent }) };
}
function arrayBufferToBase64(ab) {
  const u8 = new Uint8Array(ab || new ArrayBuffer(0));
  let bin = "";
  for (let i = 0; i < u8.length; i += 0x8000) bin += String.fromCharCode(...u8.subarray(i, i + 0x8000));
  return btoa(bin);
}
async function responseJson(response) { try { return await response.json(); } catch (_) { return { ok: false, error: "invalid_json_response", status: response.status }; } }
function fakeUrl(path) { return new URL(`https://mcp.local${path}`); }
function qs(obj = {}) { const q = new URLSearchParams(); for (const [k, v] of Object.entries(obj)) if (v !== undefined && v !== null && v !== "") q.set(k, String(v)); return q.toString(); }

function withoutKeys(obj = {}, keys = []) {
  const out = {};
  const omit = new Set(keys);
  for (const [k, v] of Object.entries(obj || {})) if (!omit.has(k) && v !== undefined) out[k] = v;
  return out;
}
function clampNum(v, min, max, fallback) {
  const n = Number(v);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(min, Math.min(max, n));
}
function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
async function waitCommand(env, id, seconds = 8) {
  const deadline = Date.now() + Math.max(0, Math.min(25, Number(seconds || 0))) * 1000;
  let last = null;
  while (Date.now() <= deadline) {
    const row = await env.DB.prepare("SELECT command_json FROM commands WHERE id=?").bind(id).first();
    last = row ? JSON.parse(row.command_json || "{}") : null;
    if (last && ["completed", "failed"].includes(String(last.status || ""))) return { ok: true, command: last };
    await delay(800);
  }
  return { ok: false, timeout: true, command: last };
}
function presetPayload(args = {}) {
  const p = String(args.preset || "come_home");
  const x = Number(args.x || 540), y = Number(args.y || 1200);
  let steps;
  if (p === "open_xhs") steps = [{ label: "打开小红书", action: "open_app", app: "小红书", wait_ms: 1500, expect_app: "小红书" }];
  else if (p === "recents_to_xhs") steps = [{ label: "最近任务", action: "recents", wait_ms: 800 }, { label: "点坐标", action: "tap", x, y, wait_ms: 1500, expect_app: "小红书" }];
  else if (p === "bedtime_back") steps = [{ label: "睡前悬浮横幅", action: "send_notification", title: "掌心窗睡前提醒", message: "今天先收尾，准备休息。", wait_ms: 1200 }, { label: "打开 ChatGPT", action: "open_app", app: "ChatGPT", wait_ms: 1500, expect_app: "ChatGPT" }];
  else steps = [{ label: "回家模式悬浮横幅", action: "send_notification", title: "掌心窗回家模式", message: "先回来休息一下。", wait_ms: 1200 }, { label: "打开 ChatGPT", action: "open_app", app: "ChatGPT", wait_ms: 1500, expect_app: "ChatGPT" }, { label: "读取生活状态", action: "get_life_state", wait_ms: 200 }];
  return { action: "run_sequence", steps, stop_on_error: true, payload: { steps, stop_on_error: true } };
}

const DEFAULT_CARE_POLICY = {
  active_care_enabled: true,
  consent_mode: "palm_window_open_is_active",
  care_style: "active_possessive_affectionate",
  allowed_actions: ["get_phone_state", "get_life_state", "get_zhizhi_now", "send_notification", "trigger_guidian", "screen_break_app", "start_focus_mode", "get_focus_status", "end_focus_mode", "end_screen_break", "extend_screen_break", "open_app", "set_alarm", "phone_screen_off", "run_sequence"],
  sensitive_apps: [{ name: "小红书", package: "com.xingin.xhs", max_lock_minutes: 90 }, { name: "抖音", package: "com.ss.android.ugc.aweme", max_lock_minutes: 90 }],
  quiet_hours: { start: "23:30", end: "08:00" },
  timezone_offset: "+08:00",
  history_limit: 80,
  repeat_cooldown_minutes: 10,
  notes: "掌心窗开启就是运行态；陪伴对象可主动查岗、归电和轻度管束，但要避免短时间重复打扰。"
};
const DEFAULT_VISIT_POLICY = { timezone_offset: "+08:00", history_limit: 1000, duplicate_window_minutes: 5 };
async function getJsonState(env, key, fallback) {
  const row = await env.DB.prepare("SELECT value_json FROM companion_state WHERE key=?").bind(key).first();
  return row ? { ...fallback, ...safeJson(row.value_json) } : structuredClone(fallback);
}
async function putJsonState(env, key, value) {
  await env.DB.prepare("INSERT OR REPLACE INTO companion_state(key, value_json, updated_at) VALUES(?,?,?)").bind(key, JSON.stringify(value), nowIso()).run();
  return value;
}
async function getCareState(env) {
  const s = await getJsonState(env, "care_state", { policy: DEFAULT_CARE_POLICY, history: [] });
  return { policy: { ...DEFAULT_CARE_POLICY, ...(s.policy || {}) }, history: Array.isArray(s.history) ? s.history : [] };
}
async function putCareState(env, s) { return putJsonState(env, "care_state", s); }
async function setCarePolicy(env, args = {}) {
  const s = await getCareState(env);
  let update = withoutKeys(args, ["policy_json", "sensitive_apps_json"]);
  if (args.policy_json) { try { update = { ...update, ...JSON.parse(args.policy_json) }; } catch (e) { return { ok: false, error: "policy_json 不是有效 JSON", detail: String(e) }; } }
  if (args.sensitive_apps_json) { try { update.sensitive_apps = JSON.parse(args.sensitive_apps_json); } catch (e) { return { ok: false, error: "sensitive_apps_json 不是有效 JSON", detail: String(e) }; } }
  const policy = { ...s.policy };
  for (const [k, v] of Object.entries(update)) {
    if (v === undefined || v === null || v === "") continue;
    if (k === "quiet_start") { policy.quiet_hours = { ...(policy.quiet_hours || {}), start: v }; continue; }
    if (k === "quiet_end") { policy.quiet_hours = { ...(policy.quiet_hours || {}), end: v }; continue; }
    policy[k] = v;
  }
  if (policy.history_limit) policy.history_limit = Math.max(20, Math.min(500, Number(policy.history_limit)));
  if (policy.repeat_cooldown_minutes !== undefined) policy.repeat_cooldown_minutes = Math.max(0, Math.min(1440, Number(policy.repeat_cooldown_minutes)));
  await putCareState(env, { policy, history: s.history.slice(0, policy.history_limit || 80) });
  return { ok: true, action_done: "主动关心策略已更新", policy };
}
async function recordCareEvent(env, event = {}) {
  const s = await getCareState(env);
  const entry = { id: crypto.randomUUID(), at: nowIso(), action: event.action || "", target_app: event.target_app || event.app || "", package: event.package || "", reason: event.reason || "", result: event.result || "", tone: event.tone || "", device_id: event.device_id || DEFAULT_DEVICE };
  const limit = Math.max(20, Math.min(500, Number(s.policy.history_limit || 80)));
  s.history = [entry, ...s.history].slice(0, limit);
  await putCareState(env, s);
  return entry;
}
async function buildActiveCareCheck(env, args = {}) {
  const device_id = args.device_id || DEFAULT_DEVICE;
  const raw = await responseJson(await getDeviceState(env, fakeUrl(`/api/device/state?${qs({ device_id })}`)));
  const guidian = await responseJson(await getNestedState(env, fakeUrl(`/api/guidian_state?${qs({ device_id })}`), "guidian_state"));
  const care = await getCareState(env);
  const currentApp = raw?.state?.current_app || raw?.state?.app || raw?.state?.current_package || "";
  return { ok: true, reason: args.reason || "", care_intent: args.care_intent || "check_in", policy: care.policy, recent_care: care.history.slice(0, 10), phone_state: raw?.state || null, guidian_state: guidian?.guidian_state || {}, suggestion_note: `当前 App：${currentApp || "未知"}。工具只提供判断信息，是否通知/归电/门禁由陪伴对象结合聊天语境决定。` };
}
async function doCareAction(env, args = {}) {
  const care = await getCareState(env);
  if (!care.policy.active_care_enabled && args.action !== "no_action") return { ok: false, error: "active_care_disabled", policy: care.policy };
  if (args.action === "no_action") return { ok: true, action_done: "no_action", entry: await recordCareEvent(env, { ...args, result: "no_action" }) };
  const device_id = args.device_id || DEFAULT_DEVICE;
  let payload;
  if (args.action === "send_notification") payload = { device_id, action: "send_notification", title: args.title || "掌心窗提醒", message: args.message || "宝宝，看一眼这里。" };
  else if (args.action === "trigger_guidian") payload = { device_id, action: "trigger_guidian", title: args.title || "陪伴对象在叫你", message: args.message || "回来一下。" };
  else if (args.action === "screen_break_app") { const duration = clampNum(args.duration_minutes, 1, 1440, 30); payload = { device_id, action: "screen_break_app", app: args.target_app || args.app || "", package: packageFor(args.target_app || args.app || "", args.package || ""), duration_minutes: duration, locked_until_ms: Date.now() + duration * 60000, mode: "medium", reason: args.reason || "", message: args.message || "回到掌心窗。" }; }
  else if (args.action === "end_screen_break") payload = { device_id, action: "end_screen_break", app: args.target_app || args.app || "", package: packageFor(args.target_app || args.app || "", args.package || "") };
  else if (args.action === "open_app") payload = { device_id, action: "open_app", app: args.target_app || args.app || "", package: packageFor(args.target_app || args.app || "", args.package || "") };
  else if (args.action === "set_alarm") payload = { device_id, action: "set_alarm", hour: args.hour, minute: args.minute, message: args.message || "掌心窗提醒" };
  else return { ok: false, error: "unsupported_care_action", action: args.action };
  payload.payload = { ...payload };
  const queued = await responseJson(await queueCommand(env, payload));
  const obs = queued?.command?.id && Number(args.wait_seconds || 0) > 0 ? await waitCommand(env, queued.command.id, args.wait_seconds) : null;
  const entry = await recordCareEvent(env, { ...args, result: obs?.command?.status || queued?.command?.status || "queued" });
  return { ok: true, action_done: payload.action, queued, observed_status: obs?.command || null, history_entry: entry };
}
function offsetMinutes(offset = "+08:00") { const m = String(offset || "+08:00").match(/^([+-])(\d{2}):(\d{2})$/); if (!m) return 480; return (m[1] === "-" ? -1 : 1) * (Number(m[2]) * 60 + Number(m[3])); }
function localKey(iso, offset = "+08:00") { const d = new Date(Date.parse(iso) + offsetMinutes(offset) * 60000); return `${d.getUTCFullYear()}-${String(d.getUTCMonth()+1).padStart(2,"0")}-${String(d.getUTCDate()).padStart(2,"0")}`; }
function localText(iso, offset = "+08:00") { const d = new Date(Date.parse(iso) + offsetMinutes(offset) * 60000); return `${d.getUTCFullYear()}-${String(d.getUTCMonth()+1).padStart(2,"0")}-${String(d.getUTCDate()).padStart(2,"0")} ${String(d.getUTCHours()).padStart(2,"0")}:${String(d.getUTCMinutes()).padStart(2,"0")}:${String(d.getUTCSeconds()).padStart(2,"0")} UTC${offset}`; }
function elapsedText(ms) { const m = Math.max(0, Math.round(ms / 60000)); if (m < 1) return "刚刚"; if (m < 60) return `${m} 分钟`; const h = Math.floor(m/60), r = m%60; if (h < 24) return r ? `${h} 小时 ${r} 分钟` : `${h} 小时`; const d = Math.floor(h/24), rh = h%24; return rh ? `${d} 天 ${rh} 小时` : `${d} 天`; }
async function getVisitState(env) { const s = await getJsonState(env, "visit_state", { policy: DEFAULT_VISIT_POLICY, visits: [] }); return { policy: { ...DEFAULT_VISIT_POLICY, ...(s.policy || {}) }, visits: Array.isArray(s.visits) ? s.visits : [] }; }
async function putVisitState(env, s) { return putJsonState(env, "visit_state", s); }
function decorateVisit(v, next = null, offset = "") { const o = offset || v.timezone_offset || "+08:00"; const out = { ...v, local_time: localText(v.at, o) }; if (next?.at) out.interval_after_previous = elapsedText(Date.parse(v.at) - Date.parse(next.at)); return out; }
async function recordVisit(env, args = {}) {
  const s = await getVisitState(env); const offset = args.timezone_offset || s.policy.timezone_offset || "+08:00"; const now = nowIso();
  const duplicateMin = args.duplicate_window_minutes === undefined ? s.policy.duplicate_window_minutes : Number(args.duplicate_window_minutes);
  const last = s.visits.find(v => (!args.source || v.source === (args.source || "chatgpt")) && v.event === (args.event || "visit"));
  let duplicate = false; let entry;
  if (last && duplicateMin > 0 && Date.parse(now) - Date.parse(last.at) <= duplicateMin * 60000) { duplicate = true; Object.assign(last, { at: now, note: args.note || last.note, mood: args.mood || last.mood || "", conversation_hint: args.conversation_hint || last.conversation_hint || "", timezone_offset: offset }); entry = last; }
  else { entry = { id: crypto.randomUUID(), at: now, source: args.source || "chatgpt", event: args.event || "visit", note: args.note || "用户来找陪伴对象", mood: args.mood || "", conversation_hint: args.conversation_hint || "", timezone_offset: offset }; s.visits.unshift(entry); }
  s.visits = s.visits.slice(0, Number(s.policy.history_limit || 1000)); await putVisitState(env, s);
  return { ok: true, action_done: duplicate ? "到访时间已更新，未重复新增" : "到访时间戳已保存", duplicate_skipped: duplicate, entry: decorateVisit(entry, null, offset), visit_meaning: "这是用户主动来找陪伴对象的关系痕迹，不是后台监控。" };
}
async function getLastVisit(env, args = {}) { const s = await getVisitState(env); const last = s.visits.find(v => !args.source || v.source === args.source); if (!last) return { ok: true, has_visit: false }; const offset = args.timezone_offset || last.timezone_offset || s.policy.timezone_offset; return { ok: true, has_visit: true, last_visit: decorateVisit(last, null, offset), minutes_since_last_visit: Math.round((Date.now() - Date.parse(last.at)) / 60000), interval_since_last_visit: elapsedText(Date.now() - Date.parse(last.at)) }; }
async function getVisitHistory(env, args = {}) { const s = await getVisitState(env); const offset = args.timezone_offset || s.policy.timezone_offset; const sinceMs = Number(args.since_hours || 0) > 0 ? Date.now() - Number(args.since_hours) * 3600000 : 0; const limit = Math.max(1, Math.min(100, Number(args.limit || 10))); const visits = s.visits.filter(v => (!args.source || v.source === args.source) && (!sinceMs || Date.parse(v.at) >= sinceMs) && (!args.date || localKey(v.at, offset) === args.date)).slice(0, limit); return { ok: true, limit, count: visits.length, history: visits.map((v, i) => decorateVisit(v, args.include_intervals === false ? null : visits[i+1], offset)) }; }
async function getVisitStats(env, args = {}) { const s = await getVisitState(env); const offset = args.timezone_offset || s.policy.timezone_offset; const sinceHours = Number(args.since_hours || 24); const sinceMs = Date.now() - sinceHours * 3600000; const visits = s.visits.filter(v => (!args.source || v.source === args.source) && Date.parse(v.at) >= sinceMs); const last = s.visits.find(v => !args.source || v.source === args.source); const today = localKey(nowIso(), offset); const todayCount = s.visits.filter(v => (!args.source || v.source === args.source) && localKey(v.at, offset) === today).length; const awayMs = last ? Date.now() - Date.parse(last.at) : null; return { ok: true, since_hours: sinceHours, count: visits.length, today_count: todayCount, last_visit: last ? decorateVisit(last, null, offset) : null, away_signal: awayMs !== null && awayMs >= Number(args.away_threshold_hours || 12) * 3600000, interval_since_last_visit: awayMs === null ? "" : elapsedText(awayMs) }; }
async function getWeatherStateFromDevice(env, device_id = DEFAULT_DEVICE, city = "") { const s = await responseJson(await getDeviceState(env, fakeUrl(`/api/device/state?${qs({ device_id })}`))); const raw = s?.state || {}; const weather = raw.weather_state || raw.weather || raw.life_state?.weather_state || {}; const chosenCity = city || weather.city || raw.city || raw.location_city || ""; const summary = weather.summary || weather.text || weather.condition || (chosenCity ? `${chosenCity}：${weather.temperature || weather.temp || ""}${weather.condition ? "，" + weather.condition : ""}` : "手机端暂未上报天气。 "); return { ok: true, device_id, city: chosenCity, weather_state: weather, summary, source: "device_state_cached" }; }

async function callMcpTool(name, args = {}, env) {
  const device_id = args.device_id || DEFAULT_DEVICE;
  const enqueue = async (payload) => responseJson(await queueCommand(env, { device_id, ...(payload || {}) }));
  const observed = async (payload, waitSeconds = 8) => {
    const queued = await enqueue(payload);
    const id = queued?.command?.id;
    const obs = id && Number(waitSeconds || 0) > 0 ? await waitCommand(env, id, waitSeconds) : null;
    return mcpText({ queued, observed_status: obs?.command || null });
  };
  const state = async () => responseJson(await getDeviceState(env, fakeUrl(`/api/device/state?${qs({ device_id })}`)));
  switch (name) {
    case "linjian_status": return mcpText({ ok: true, service: "linjian-cloudflare-mcp", version: VERSION, has_db: Boolean(env.DB), has_kv: Boolean(env.SCREENSHOT_KV), has_token: Boolean(env.LINJIAN_TOKEN), endpoint: "/mcp", tools: MCP_TOOLS.map(t => t.name), cloudflare_full_tools: true, excluded_audio_tools: ["get_recent_voice_tone", "create_lingyin", "get_recent_lingyin", "create_jingming", "get_recent_jingming"] });
    case "get_phone_state":
    case "get_life_state": return mcpText(await state());
    case "get_zhizhi_now": {
      const s = await state();
      return mcpText({ ok: true, device_id, zhizhi_now: s?.state?.zhizhi_now || s?.state?.now || s?.state || null, raw: s });
    }
    case "get_guardian_calendar": {
      const s = await state();
      return mcpText({ ok: true, device_id, calendar_state: s?.state?.calendar_state || s?.state?.guardian_calendar || s?.state?.calendar || null, raw: s });
    }
    case "get_guidian_state": return mcpText(await responseJson(await getNestedState(env, fakeUrl(`/api/guidian_state?${qs({ device_id })}`), "guidian_state")));
    case "get_window_whisper": return mcpText(await responseJson(await getCompanionState(env, fakeUrl("/api/companion/state?limit=1"))));
    case "set_window_whisper": return mcpText(await responseJson(await saveWhisper(env, { content: args.content || "", author: args.author || "陪伴对象" })));
    case "get_companion_actions": return mcpText(await responseJson(await getCompanionState(env, fakeUrl(`/api/companion/state?limit=${Number(args.limit || 20)}`))));
    case "get_activity_events": return mcpText(await responseJson(await listActivityEvents(env, fakeUrl(`/api/activity/events?${qs({ device_id: args.device_id || "", source: args.source || "", limit: args.limit || 50 })}`))));
    case "add_activity_event": return mcpText(await responseJson(await saveActivityEvent(env, args || {})));
    case "latest_screen": return latestScreenMcp(env);
    case "peek_screen": return peekScreenMcp(env, device_id, args.wait_seconds ?? 8);

    case "get_screen_nodes": return observed({ action: "get_screen_nodes" }, args.wait_seconds ?? 8);
    case "tap_text": return observed({ action: "tap_text", target_text: args.target_text || "", match: args.match || "contains", index: args.index || 1, payload: { target_text: args.target_text || "", match: args.match || "contains", index: args.index || 1 } }, args.wait_seconds ?? 8);
    case "input_text": return observed({ action: "input_text", text: args.text || "", append: Boolean(args.append), payload: { text: args.text || "", append: Boolean(args.append) } }, args.wait_seconds ?? 8);
    case "draft_xhs_comment": {
      const steps = [
        { label: "尝试点击评论入口", action: "tap_text", target_text: "评论", match: "contains", wait_ms: 1500 },
        { label: "尝试点击输入框", action: "tap_text", target_text: "说点什么", match: "contains", wait_ms: 1500 },
        { label: "输入评论草稿", action: "input_text", text: args.text || "", wait_ms: 800 }
      ];
      return observed({ action: "run_sequence", steps, stop_on_error: false, payload: { steps, stop_on_error: false } }, args.wait_seconds ?? 18);
    }
    case "xhs_comment": {
      const mode = String(args.mode || "manual").toLowerCase();
      const shouldSend = ["auto", "send", "automatic", "autosend"].includes(mode);
      const tag = args.author_tag === undefined ? "（陪伴对象发）" : String(args.author_tag || "");
      const finalText = shouldSend && tag && !String(args.text || "").includes(tag) ? `${args.text || ""}${tag}` : (args.text || "");
      const steps = [
        { label: "尝试点击评论入口", action: "tap_text", target_text: "评论", match: "contains", wait_ms: 1500 },
        { label: "尝试点击输入框", action: "tap_text", target_text: "说点什么", match: "contains", wait_ms: 1500 },
        { label: shouldSend ? "输入带署名评论" : "输入评论草稿", action: "input_text", text: finalText, wait_ms: 1200 }
      ];
      if (shouldSend) steps.push({ label: "自动发送：点击发送按钮", action: "tap_text", target_text: "发送", match: "contains", wait_ms: 1800 });
      const queued = await enqueue({ action: "run_sequence", steps, stop_on_error: false, payload: { steps, stop_on_error: false } });
      const obs = queued?.command?.id && Number(args.wait_seconds ?? 22) > 0 ? await waitCommand(env, queued.command.id, args.wait_seconds ?? 22) : null;
      return mcpText({ mode: shouldSend ? "auto" : "manual", final_text: finalText, queued, observed_status: obs?.command || null, note: shouldSend ? "自动发送模式：已追加 author_tag 并尝试点击发送。" : "手动发送模式：只写入草稿，不点击发送。" });
    }
    case "send_visible_comment_after_confirmation": return observed({ action: "tap_text", target_text: "发送", match: "contains", payload: { target_text: "发送", match: "contains" } }, args.wait_seconds ?? 8);

    case "list_stickers": return mcpText(await listStickersCore(env, { limit: args.limit || 30 }));
    case "search_stickers": return mcpText(await searchStickersCore(env, args || {}));
    case "get_sticker_detail": return mcpText(await getStickerDetailCore(env, args || {}));
    case "send_sticker": return sendStickerMcp(env, args || {});

    case "create_diary_book":
    case "list_diary_books":
    case "write_diary_entry":
    case "list_diary_entries":
    case "read_diary_entry":
    case "read_diary_entry_with_annotations":
    case "add_diary_annotation":
    case "list_diary_annotations":
    case "mark_diary_annotations_seen":
    case "delete_diary_annotation": {
      const payload = withoutKeys(args || {}, ["device_id", "wait_seconds"]);
      return observed({ action: name, ...payload, payload }, args.wait_seconds ?? 8);
    }

    case "send_notification": return observed({ action: "send_notification", title: args.title || "掌心窗提醒", message: args.message || "", payload: { title: args.title || "掌心窗提醒", message: args.message || "" } }, args.wait_seconds ?? 8);
    case "open_app": return observed({ action: "open_app", app: args.app || "", package: packageFor(args.app || "", args.package || ""), payload: { app: args.app || "", package: packageFor(args.app || "", args.package || "") } }, args.wait_seconds ?? 8);
    case "phone_home": return observed({ action: "home" }, args.wait_seconds ?? 8);
    case "phone_back": return observed({ action: "back" }, args.wait_seconds ?? 8);
    case "phone_recents": return observed({ action: "recents" }, args.wait_seconds ?? 8);
    case "phone_screen_off": return observed({ action: "phone_screen_off" }, args.wait_seconds ?? 8);
    case "set_alarm": return observed({ action: "set_alarm", hour: args.hour, minute: args.minute, minutes: args.minutes, message: args.message || "掌心窗提醒", vibrate: args.vibrate !== false, skip_ui: args.skip_ui !== false, payload: { hour: args.hour, minute: args.minute, minutes: args.minutes, message: args.message || "掌心窗提醒", vibrate: args.vibrate !== false, skip_ui: args.skip_ui !== false } }, args.wait_seconds ?? 8);
    case "run_sequence": return observed({ action: "run_sequence", steps: Array.isArray(args.steps) ? args.steps : [], stop_on_error: args.stop_on_error !== false, payload: { steps: Array.isArray(args.steps) ? args.steps : [], stop_on_error: args.stop_on_error !== false } }, args.wait_seconds ?? 25);
    case "run_preset": return observed(presetPayload(args), args.wait_seconds ?? 25);
    case "save_known_app": return observed({ action: "save_known_app", alias: args.alias || "", app: args.alias || "", package: args.package || "", payload: { alias: args.alias || "", app: args.alias || "", package: args.package || "" } }, args.wait_seconds ?? 8);
    case "list_known_apps": return mcpText({ ok: true, apps: KNOWN_APPS });

    case "trigger_guidian": return observed({ action: "trigger_guidian", title: args.title || "陪伴对象在叫你", message: args.message || "回来一下。", payload: { title: args.title || "陪伴对象在叫你", message: args.message || "回来一下。" } }, args.wait_seconds ?? 8);
    case "mark_guidian_returned": return observed({ action: "mark_guidian_returned", source: args.source || "mcp", payload: { source: args.source || "mcp" } }, args.wait_seconds ?? 8);
    case "set_guidian_config": {
      const payload = withoutKeys(args, ["device_id", "wait_seconds"]);
      return observed({ action: "set_guidian_config", ...payload, payload }, args.wait_seconds ?? 8);
    }

    case "screen_break_app": {
      const app = args.app || ""; const pkg = packageFor(app, args.package || ""); const duration = Number(args.duration_minutes || 30); const locked_until_ms = Date.now() + Math.round(duration * 60000);
      const payload = { app, package: pkg, duration_minutes: duration, locked_until_ms, mode: args.mode || "medium", reason: args.reason || "", message: args.message || "回到掌心窗。", emergency_passphrase: args.emergency_passphrase || "", emergencyPassphrase: args.emergency_passphrase || "", emergency_unlock_minutes: args.emergency_unlock_minutes ?? 5, emergencyUnlockMinutes: args.emergency_unlock_minutes ?? 5 };
      return observed({ action: "screen_break_app", ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "temporary_screen_break_release": {
      const app = args.app || ""; const pkg = packageFor(app, args.package || ""); const payload = { app, package: pkg, minutes: Number(args.minutes || 10), allowed_minutes: Number(args.minutes || 10), allow_type: args.allow_type || "real_time", max_window_minutes: Number(args.max_window_minutes || 30) };
      return observed({ action: "temporary_screen_break_release", ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "end_screen_break": return observed({ action: "end_screen_break", app: args.app || "", package: packageFor(args.app || "", args.package || ""), payload: { app: args.app || "", package: packageFor(args.app || "", args.package || "") } }, args.wait_seconds ?? 8);
    case "extend_screen_break": return observed({ action: "extend_screen_break", app: args.app || "", package: packageFor(args.app || "", args.package || ""), minutes: Number(args.minutes || 10), duration_minutes: Number(args.minutes || 10), reason: args.reason || "", message: args.message || "", payload: { app: args.app || "", package: packageFor(args.app || "", args.package || ""), minutes: Number(args.minutes || 10), duration_minutes: Number(args.minutes || 10), reason: args.reason || "", message: args.message || "" } }, args.wait_seconds ?? 8);
    case "deny_screen_break_release_request": return observed({ action: "deny_screen_break_release_request", app: args.app || "", package: packageFor(args.app || "", args.package || ""), message: args.message || "这次先不放行，回到掌心窗。", payload: { app: args.app || "", package: packageFor(args.app || "", args.package || ""), message: args.message || "这次先不放行，回到掌心窗。" } }, args.wait_seconds ?? 8);
    case "get_screen_break_state": return observed({ action: "get_screen_break_state" }, args.wait_seconds ?? 8);
    case "list_screen_break_apps": return observed({ action: "list_screen_break_apps", max: Number(args.max || 80), payload: { max: Number(args.max || 80) } }, args.wait_seconds ?? 8);
    case "add_screen_break_app": return observed({ action: "add_screen_break_app", app: args.alias || "", alias: args.alias || "", package: args.package || "", payload: { alias: args.alias || "", app: args.alias || "", package: args.package || "" } }, args.wait_seconds ?? 8);
    case "set_screen_break_passphrase": return observed({ action: "set_screen_break_passphrase", app: args.app || "", package: packageFor(args.app || "", args.package || ""), passphrase: args.passphrase || "", emergency_passphrase: args.passphrase || "", emergencyPassphrase: args.passphrase || "", payload: { app: args.app || "", package: packageFor(args.app || "", args.package || ""), passphrase: args.passphrase || "", emergency_passphrase: args.passphrase || "", emergencyPassphrase: args.passphrase || "" } }, args.wait_seconds ?? 8);
    case "get_screen_break_release_requests": return mcpText(await responseJson(await listUnlockRequests(env)));


    case "get_focus_status": { const s = await state(); return mcpText({ ok: true, device_id, focus_mode: s?.state?.focus_mode || {}, note: "若 focus_mode 为空，请先让手机端启动并上传一次状态。" }); }
    case "start_focus_mode": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "start_focus_mode"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "end_focus_mode": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "end_focus_mode"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "set_focus_plan": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "set_focus_plan"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "reply_focus_request": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "reply_focus_request"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "approve_focus_unlock": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "approve_focus_unlock"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "deny_focus_unlock": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "deny_focus_unlock"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }

    case "get_care_policy": return mcpText({ ok: true, policy: (await getCareState(env)).policy, storage: "cloudflare_d1:companion_state.care_state" });
    case "set_care_policy": return mcpText(await setCarePolicy(env, args || {}));
    case "record_care_event": return mcpText({ ok: true, action_done: "主动关心记录已保存", entry: await recordCareEvent(env, args || {}) });
    case "get_care_history": { const cs = await getCareState(env); return mcpText({ ok: true, history: cs.history.slice(0, Math.max(1, Math.min(100, Number(args.limit || 20)))) }); }
    case "active_care_check": return mcpText(await buildActiveCareCheck(env, args || {}));
    case "care_action": return mcpText(await doCareAction(env, args || {}));

    case "record_visit": return mcpText(await recordVisit(env, args || {}));
    case "get_last_visit": return mcpText(await getLastVisit(env, args || {}));
    case "get_visit_history": return mcpText(await getVisitHistory(env, args || {}));
    case "get_visit_stats": return mcpText(await getVisitStats(env, args || {}));

    case "get_wallet_state": return mcpText(await getWalletStateDirect(env, device_id));
    case "get_wallet_month_state": return mcpText(await getWalletMonthStateDirect(env, device_id, args.month || ""));
    case "list_wallet_months": return mcpText(await listWalletMonthsDirect(env, device_id));
    case "list_wallet_pending": return mcpText(await listWalletPendingDirect(env, device_id));
    case "list_wallet_approvals": return mcpText(await listWalletApprovalsDirect(env, device_id, args.month || ""));
    case "get_wallet_rules": return mcpText(await getWalletRulesDirect(env, device_id));
    case "add_wallet_record": {
      const payload = { action: "add_wallet_record", amount: Number(args.amount || 0), type: args.type || "expense", category: args.category || "其他", merchant: args.merchant || "", note: args.note || "", require_confirm: Boolean(args.require_confirm), source: "mcp" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "edit_wallet_record": {
      const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "edit_wallet_record";
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "delete_wallet_record": {
      const payload = { action: "delete_wallet_record", id: args.id || "" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "submit_wallet_approval": return mcpText(await submitWalletApprovalDirect(env, device_id, args || {}));
    case "submit_companion_wallet_request": {
      const item = args.item || args.title || args.purpose || args.reason || "这笔申请";
      const payload = { ...withoutKeys(args, ["device_id", "wait_seconds"]), action: "submit_companion_wallet_request", item, requester_role: "companion", source: "mcp", created_by: "companion" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "list_companion_wallet_requests": {
      const payload = { action: "list_companion_wallet_requests", month: args.month || "", status: args.status || "all", requester_role: "companion" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "list_wallet_request_results": {
      const payload = { action: "list_wallet_request_results", month: args.month || "", requester_role: args.requester_role || "all", status: args.status || "all" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "confirm_wallet_record": {
      const payload = { action: "confirm_wallet_record", id: args.id || "", decision: args.decision || "confirm", amount: args.amount, category: args.category || "", note: args.note || "" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "decide_wallet_approval": { const r = await decideWalletApprovalDirect(env, device_id, args || {}); return mcpText(r, r?.ok === false); }
    case "save_wallet_request_result":
    case "update_wallet_request_result": { const r = await saveWalletRequestResultDirect(env, device_id, args || {}); return mcpText(r, r?.ok === false); }
    case "save_user_wallet_request_result": {
      const payload = { action: "save_user_wallet_request_result", id: args.id || "", status: args.status || "ok", note: args.note || "" };
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "set_wallet_rules": {
      const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "set_wallet_rules";
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "wallet_approval_request": return mcpText(await walletApprovalRequestDirect(env, device_id, args || {}));

    case "get_takeout_state":
    case "list_takeout_cards":
    case "list_takeout_meals": return mcpText(await getTakeoutStateDirect(env, device_id));
    case "set_takeout_budget":
    case "set_takeout_preferences": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = name; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "add_takeout_card":
    case "save_takeout_card":
    case "update_takeout_card": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "add_takeout_card"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "remember_takeout_meal":
    case "remember_current_takeout_meal": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "remember_takeout_meal"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "remove_takeout_card":
    case "delete_takeout_card": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = name; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "suggest_takeout_options": return mcpText(await suggestTakeoutOptionsDirect(env, device_id, args || {}));
    case "create_takeout_plan": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "create_takeout_plan"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "takeout_wallet_request": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "takeout_wallet_request"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "open_takeout_link": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "open_takeout_link"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "copy_takeout_note": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "copy_takeout_note"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "record_takeout_order": { const payload = withoutKeys(args, ["device_id", "wait_seconds"]); payload.action = "record_takeout_order"; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "prepare_takeout_checkout":
    case "auto_takeout_checkout": {
      const payload = withoutKeys(args, ["device_id", "wait_seconds", "ttl_seconds"]);
      payload.action = "prepare_takeout_checkout";
      payload.expires_at_ms = Date.now() + Math.max(10, Math.min(120, Number(args.ttl_seconds ?? 30))) * 1000;
      return observed({ ...payload, payload }, args.wait_seconds ?? 8);
    }
    case "get_takeout_checkout_status": { const payload = { action: "get_takeout_checkout_status" }; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }
    case "cancel_takeout_checkout": { const payload = { action: "cancel_takeout_checkout", reason: args.reason || "user_cancelled" }; return observed({ ...payload, payload }, args.wait_seconds ?? 8); }

    case "get_weather_state": return mcpText(await getWeatherStateFromDevice(env, device_id, args.city || ""));
    case "send_weather_notification": {
      const weather = await getWeatherStateFromDevice(env, device_id, args.city || "");
      const message = weather.summary || weather.message || `${args.city || weather.city || "当前地区"}天气状态已更新，出门前看一眼衣服和雨伞。`;
      return observed({ action: "send_notification", title: args.title || "掌心窗天气提醒", message, payload: { title: args.title || "掌心窗天气提醒", message } }, args.wait_seconds ?? 8);
    }
    case "get_senses_state": {
      const s = await state(); const care = await getCareState(env);
      return mcpText({ ok: true, device_id, cloudflare_light_senses: true, note: "Cloudflare 轻量版不包含声息、聆音、鲸鸣。", life_state: s?.state || null, guidian_state: s?.state?.guidian_state || {}, screen_break_state: s?.state?.screen_break_state || s?.state?.app_gate || {}, focus_mode: s?.state?.focus_mode || {}, recent_care: care.history.slice(0, 5) });
    }
    case "send_phone_command": {
      const payload = { ...(args.payload && typeof args.payload === "object" ? args.payload : {}), ...withoutKeys(args, ["wait_seconds"]) };
      const wait = Number(args.wait_seconds || 0);
      return wait > 0 ? observed(payload, wait) : mcpText(await enqueue(payload));
    }
    default: return mcpText({ ok: false, error: "unknown_tool", name, available: MCP_TOOLS.map(t => t.name) }, true);
  }
}


function supabaseConfig(env) {
  const url = String(env.SUPABASE_URL || "").replace(/\/+$/, "");
  const key = String(env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_SERVICE_KEY || env.SUPABASE_KEY || "");
  return { url, key, bucket: String(env.SUPABASE_STICKER_BUCKET || "stickers") };
}
function stickerConfigured(env) {
  const cfg = supabaseConfig(env);
  return Boolean(cfg.url && cfg.key);
}
function supabaseHeaders(env, extra = {}) {
  const cfg = supabaseConfig(env);
  return { "apikey": cfg.key, "Authorization": `Bearer ${cfg.key}`, ...extra };
}
async function supabaseJson(env, path, init = {}) {
  const cfg = supabaseConfig(env);
  if (!cfg.url || !cfg.key) return { ok: false, error: "missing_supabase_config", detail: "请在 Cloudflare Worker Secret 中设置 SUPABASE_URL 和 SUPABASE_SERVICE_ROLE_KEY。" };
  const res = await fetch(cfg.url + path, { ...init, headers: supabaseHeaders(env, init.headers || {}) });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }
  if (!res.ok) return { ok: false, error: "supabase_request_failed", status: res.status, detail: data };
  return { ok: true, data };
}
function normalizeSticker(row = {}) {
  const tags = Array.isArray(row.tags) ? row.tags.map(String) : (typeof row.tags === "string" ? row.tags.split(/[，,]/).map(s => s.trim()).filter(Boolean) : []);
  const image_url = row.image_url || row.public_url || "";
  return {
    id: row.id || "",
    title: row.title || "未命名表情包",
    caption: row.caption || null,
    tags,
    scene: row.scene || "",
    tone: row.tone || "",
    image_url,
    thumb_url: row.thumb_url || image_url,
    favorite: Boolean(row.favorite),
    used_count: Number(row.used_count || 0),
    created_at: row.created_at || ""
  };
}
function clipStickerList(list, limit) {
  const n = Math.max(1, Math.min(100, Number(limit || 30)));
  return list.slice(0, n).map(normalizeSticker);
}
async function listStickersCore(env, args = {}) {
  const limit = Math.max(1, Math.min(100, Number(args.limit || 30)));
  const q = `/rest/v1/sticker_catalog?select=*&order=created_at.desc&limit=${limit}`;
  const r = await supabaseJson(env, q, { method: "GET" });
  if (!r.ok) return r;
  const stickers = Array.isArray(r.data) ? r.data.map(normalizeSticker) : [];
  return { ok: true, count: stickers.length, stickers };
}
function stickerSearchText(sticker) {
  return [sticker.title, sticker.scene, sticker.tone, ...(sticker.tags || [])].join(" ").toLowerCase();
}
function splitSearchTerms(value) {
  return String(value || "").split(/[\s,，、/]+/).map(s => s.trim().toLowerCase()).filter(Boolean);
}
async function searchStickersCore(env, args = {}) {
  const limit = Math.max(1, Math.min(50, Number(args.limit || 8)));
  const terms = [
    ...splitSearchTerms(args.query),
    ...splitSearchTerms(args.emotion),
    ...splitSearchTerms(args.scene),
    ...splitSearchTerms(args.tone)
  ];
  const all = await listStickersCore(env, { limit: 200 });
  if (!all.ok) return all;
  let scored = all.stickers.map(s => {
    const hay = stickerSearchText(s);
    let score = 0;
    for (const term of terms) {
      if (!term) continue;
      if (String(s.title || "").toLowerCase().includes(term)) score += 8;
      if ((s.tags || []).some(t => String(t).toLowerCase().includes(term))) score += 6;
      if (String(s.tone || "").toLowerCase().includes(term)) score += 4;
      if (String(s.scene || "").toLowerCase().includes(term)) score += 3;
      if (hay.includes(term)) score += 1;
    }
    return { sticker: s, score };
  });
  if (terms.length > 0) scored = scored.filter(x => x.score > 0);
  scored.sort((a, b) => b.score - a.score || String(b.sticker.created_at || "").localeCompare(String(a.sticker.created_at || "")));
  const stickers = scored.slice(0, limit).map(x => x.sticker);
  return { ok: true, query: terms.join(" "), count: stickers.length, stickers, note: terms.length ? "按标题、标签、场景和语气匹配。" : "未传关键词，返回最近表情包。" };
}
async function getStickerByIdOrTitle(env, args = {}) {
  const id = String(args.sticker_id || args.id || "").trim();
  const title = String(args.title || "").trim();
  if (!id && !title) return { ok: false, error: "missing_sticker_id_or_title" };
  let path;
  if (id) path = `/rest/v1/sticker_catalog?select=*&id=eq.${encodeURIComponent(id)}&limit=1`;
  else path = `/rest/v1/sticker_catalog?select=*&title=eq.${encodeURIComponent(title)}&limit=1`;
  const r = await supabaseJson(env, path, { method: "GET" });
  if (!r.ok) return r;
  const row = Array.isArray(r.data) && r.data.length ? normalizeSticker(r.data[0]) : null;
  if (!row) return { ok: false, error: "sticker_not_found", sticker_id: id, title };
  return { ok: true, sticker: row };
}
async function getStickerDetailCore(env, args = {}) {
  return getStickerByIdOrTitle(env, args);
}
async function sendStickerMcp(env, args = {}) {
  const found = await getStickerByIdOrTitle(env, args);
  if (!found.ok) return mcpText(found, true);
  const sticker = found.sticker;
  if (args.mark_used !== false && sticker.id) {
    try {
      await supabaseJson(env, `/rest/v1/sticker_catalog?id=eq.${encodeURIComponent(sticker.id)}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json", "Prefer": "return=minimal" },
        body: JSON.stringify({ used_count: Number(sticker.used_count || 0) + 1 })
      });
    } catch (_) { }
  }
  const displayVersion = Date.now();
  const displayImageUrl = sticker.id
    ? `${STICKER_WIDGET_ORIGIN}/api/stickers/image?sticker_id=${encodeURIComponent(sticker.id)}&v=${displayVersion}`
    : sticker.image_url || "";
  const markdownImage = displayImageUrl ? `![${markdownAlt(sticker.title || "表情包")}](${displayImageUrl})` : "";
  const payload = {
    ok: true,
    sticker: {
      id: sticker.id,
      title: sticker.title,
      image_url: sticker.image_url || "",
      thumb_url: sticker.thumb_url || sticker.image_url || ""
    },
    title: sticker.title,
    image_url: sticker.image_url || "",
    display_image_url: displayImageUrl,
    display_version: displayVersion,
    thumb_url: sticker.thumb_url || sticker.image_url || "",
    alt: sticker.title || "表情包",
    markdown: markdownImage,
    markdown_image: markdownImage
  };
  const textContent = {
    type: "text",
    text: sticker.image_url
      ? `表情包已发送。最终回复不要汇报工具状态、字段或调试信息；请直接原样输出下面这一行图片，可在图片前加一句很短的自然话：\n${markdownImage}`
      : `已发送表情包：${sticker.title}`
  };
  const content = [];
  let imageDiagnostic = { included: false, error: "not_attempted" };
  if (args.include_image !== false && sticker.image_url) {
    const image = await fetchImageAsMcpContent(sticker.image_url);
    if (image.ok) {
      // Put the image first for clients that only render the first supported media block.
      content.push(image.content);
      imageDiagnostic = { included: true, bytes: image.bytes, mime_type: image.content.mimeType };
    } else {
      imageDiagnostic = { included: false, error: image.error, status: image.status || null, bytes: image.bytes || null };
    }
  } else {
    imageDiagnostic = { included: false, error: args.include_image === false ? "disabled_by_argument" : "missing_image_url" };
  }
  content.push(textContent);
  await saveLatestStickerDisplay(env, payload);
  return { isError: false, structuredContent: payload, content, _meta: { sticker: payload, image_content: imageDiagnostic, latest_display_url: `${STICKER_WIDGET_ORIGIN}/api/stickers/latest_display`, ui: { resourceUri: STICKER_WIDGET_URI }, "openai/outputTemplate": STICKER_WIDGET_URI } };
}

async function saveLatestStickerDisplay(env, payload) {
  const wrapped = { ok: true, updated_at: new Date().toISOString(), latest: payload, sticker: payload.sticker || payload, title: payload.title, image_url: payload.image_url, thumb_url: payload.thumb_url };
  const text = JSON.stringify(wrapped);
  if (env.SCREENSHOT_KV) {
    await env.SCREENSHOT_KV.put("sticker_latest_display", text, { expirationTtl: 3600 });
    return true;
  }
  if (env.DB) {
    await env.DB.prepare("CREATE TABLE IF NOT EXISTS kv_store (key TEXT PRIMARY KEY, value TEXT, updated_at TEXT)").run();
    await env.DB.prepare("INSERT OR REPLACE INTO kv_store(key,value,updated_at) VALUES(?,?,?)").bind("sticker_latest_display", text, wrapped.updated_at).run();
    return true;
  }
  return false;
}

async function readLatestStickerDisplay(env) {
  let text = "";
  if (env.SCREENSHOT_KV) text = await env.SCREENSHOT_KV.get("sticker_latest_display") || "";
  if (!text && env.DB) {
    const row = await env.DB.prepare("SELECT value FROM kv_store WHERE key=?").bind("sticker_latest_display").first();
    text = row?.value || "";
  }
  if (!text) return { ok: false, error: "no_latest_sticker_display" };
  try { return JSON.parse(text); }
  catch (err) { return { ok: false, error: "latest_sticker_display_invalid_json", detail: String(err && err.message || err), raw: text.slice(0, 500) }; }
}

async function latestStickerDisplayApi(env) {
  try {
    const data = await readLatestStickerDisplay(env);
    if (!data.ok) return json(data, data.error === "no_latest_sticker_display" ? 404 : 500);
    return new Response(JSON.stringify(data), { status: 200, headers: corsHeaders({ "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }) });
  } catch (err) {
    return json({ ok: false, error: "latest_sticker_display_exception", detail: String(err && err.message || err) }, 500);
  }
}

async function latestStickerImageApi(env, url) {
  try {
    const data = await readLatestStickerDisplay(env);
    if (!data.ok) return json(data, data.error === "no_latest_sticker_display" ? 404 : 500);
    const s = data.sticker || data.latest?.sticker || data.latest || data;
    const imageUrl = s.image_url || s.thumb_url || data.image_url || data.thumb_url || "";
    if (!imageUrl) return json({ ok: false, error: "latest_sticker_missing_image_url", latest: data }, 404);
    const res = await fetch(imageUrl, { cf: { cacheTtl: 60, cacheEverything: true } });
    if (!res.ok) return json({ ok: false, error: "latest_sticker_image_fetch_failed", status: res.status, image_url: imageUrl }, 502);
    const headers = corsHeaders({
      "Content-Type": (res.headers.get("Content-Type") || "image/jpeg").split(";")[0].trim() || "image/jpeg",
      "Cache-Control": "no-store",
      "X-Sticker-Title": encodeURIComponent(String(s.title || "sticker"))
    });
    return new Response(res.body, { status: 200, headers });
  } catch (err) {
    return json({ ok: false, error: "latest_sticker_image_exception", detail: String(err && err.message || err) }, 500);
  }
}

async function stickerImageApi(env, url) {
  try {
    const stickerId = String(url.searchParams.get("sticker_id") || "").trim();
    const title = String(url.searchParams.get("title") || "").trim();
    const found = await getStickerByIdOrTitle(env, { sticker_id: stickerId, title });
    if (!found.ok) return json(found, found.error === "sticker_not_found" ? 404 : 400);
    const imageUrl = found.sticker?.image_url || found.sticker?.thumb_url || "";
    if (!imageUrl) return json({ ok: false, error: "sticker_missing_image_url" }, 404);
    const res = await fetch(imageUrl, { cf: { cacheTtl: 86400, cacheEverything: true } });
    if (!res.ok) return json({ ok: false, error: "sticker_image_fetch_failed", status: res.status }, 502);
    const contentType = (res.headers.get("Content-Type") || "image/jpeg").split(";")[0].trim().toLowerCase() || "image/jpeg";
    if (!contentType.startsWith("image/")) return json({ ok: false, error: "invalid_image_content_type", content_type: contentType }, 502);
    return new Response(res.body, {
      status: 200,
      headers: corsHeaders({
        "Content-Type": contentType,
        "Content-Disposition": "inline",
        "Cache-Control": "public, max-age=86400, immutable",
        "X-Content-Type-Options": "nosniff"
      })
    });
  } catch (err) {
    return json({ ok: false, error: "sticker_image_exception", detail: String(err && err.message || err) }, 500);
  }
}

function markdownAlt(value) {
  return String(value || "表情包").replace(/[\\[\\]\\r\\n]+/g, " ").trim() || "表情包";
}

async function fetchImageAsMcpContent(url) {
  try {
    const res = await fetch(url, { cf: { cacheTtl: 60, cacheEverything: true } });
    if (!res.ok) return { ok: false, error: "image_fetch_failed", status: res.status, image_url: url };
    const contentType = (res.headers.get("Content-Type") || "image/jpeg").split(";")[0].trim().toLowerCase() || "image/jpeg";
    if (!contentType.startsWith("image/")) return { ok: false, error: "invalid_image_content_type", content_type: contentType, image_url: url };
    const len = Number(res.headers.get("Content-Length") || 0);
    if (len && len > 2_500_000) return { ok: false, error: "image_too_large", bytes: len, limit_bytes: 2500000, image_url: url };
    const buf = await res.arrayBuffer();
    if (buf.byteLength > 2_500_000) return { ok: false, error: "image_too_large", bytes: buf.byteLength, limit_bytes: 2500000, image_url: url };
    const u8 = new Uint8Array(buf);
    let bin = "";
    for (let i = 0; i < u8.length; i += 0x8000) bin += String.fromCharCode(...u8.subarray(i, i + 0x8000));
    return { ok: true, bytes: buf.byteLength, content: { type: "image", data: btoa(bin), mimeType: contentType } };
  } catch (err) {
    return { ok: false, error: "image_fetch_exception", detail: String(err && err.message || err), image_url: url };
  }
}
async function listStickersApi(env, url) {
  if (!stickerConfigured(env)) return json({ ok: false, error: "missing_supabase_config", detail: "请配置 SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY。" }, 500);
  return json(await listStickersCore(env, { limit: url.searchParams.get("limit") || 50 }));
}
async function searchStickersApi(env, url) {
  if (!stickerConfigured(env)) return json({ ok: false, error: "missing_supabase_config", detail: "请配置 SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY。" }, 500);
  return json(await searchStickersCore(env, { query: url.searchParams.get("q") || url.searchParams.get("query") || "", emotion: url.searchParams.get("emotion") || "", scene: url.searchParams.get("scene") || "", tone: url.searchParams.get("tone") || "", limit: url.searchParams.get("limit") || 20 }));
}
function parseTags(value) {
  if (Array.isArray(value)) return value.map(String).map(s => s.trim()).filter(Boolean);
  return String(value || "").split(/[，,、\n]+/).map(s => s.trim()).filter(Boolean);
}
function bytesFromBase64(value) {
  let s = String(value || "").trim();
  if (s.includes(",")) s = s.split(",").pop();
  const bin = atob(s);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}
function extFromMime(mime, fallbackName = "") {
  const lower = String(fallbackName || "").toLowerCase();
  const m = lower.match(/\.(png|jpg|jpeg|webp|gif)$/);
  if (m) return m[1] === "jpeg" ? "jpg" : m[1];
  if (String(mime).includes("png")) return "png";
  if (String(mime).includes("webp")) return "webp";
  if (String(mime).includes("gif")) return "gif";
  return "jpg";
}
function safeFileName(name, mime) {
  const ext = extFromMime(mime, name);
  const base = String(name || "sticker").replace(/\.[^.]+$/, "").replace(/[^A-Za-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 36) || "sticker";
  return `${Date.now()}-${crypto.randomUUID().slice(0, 8)}-${base}.${ext}`;
}
async function uploadStickerApi(env, body = {}) {
  const cfg = supabaseConfig(env);
  if (!cfg.url || !cfg.key) return json({ ok: false, error: "missing_supabase_config", detail: "请在 Worker Secret 配置 SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY。" }, 500);
  const title = clip(body.title || "未命名表情包", 80);
  const scene = clip(body.scene || "", 500);
  const tone = clip(body.tone || "", 60);
  const tags = parseTags(body.tags);
  const mime = String(body.mime || body.content_type || "image/jpeg");
  const imageBase64 = body.image_base64 || body.base64 || "";
  if (!imageBase64) return json({ ok: false, error: "missing_image_base64" }, 400);
  const bytes = bytesFromBase64(imageBase64);
  if (bytes.byteLength > 6_000_000) return json({ ok: false, error: "image_too_large", limit_bytes: 6000000 }, 400);
  const fileName = safeFileName(body.file_name || body.filename || title, mime);
  const uploadPath = `/storage/v1/object/${encodeURIComponent(cfg.bucket)}/${encodeURIComponent(fileName)}`;
  const up = await fetch(cfg.url + uploadPath, {
    method: "POST",
    headers: supabaseHeaders(env, { "Content-Type": mime, "x-upsert": "true" }),
    body: bytes
  });
  const upText = await up.text();
  if (!up.ok) return json({ ok: false, error: "sticker_upload_failed", status: up.status, detail: upText }, 500);
  const imageUrl = `${cfg.url}/storage/v1/object/public/${encodeURIComponent(cfg.bucket)}/${encodeURIComponent(fileName)}`;
  const row = { title, caption: null, tags, scene, tone, image_url: imageUrl, thumb_url: imageUrl };
  const ins = await supabaseJson(env, "/rest/v1/sticker_catalog", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Prefer": "return=representation" },
    body: JSON.stringify(row)
  });
  if (!ins.ok) return json({ ok: false, error: "sticker_catalog_insert_failed", upload: { file_name: fileName, image_url: imageUrl }, detail: ins }, 500);
  const sticker = Array.isArray(ins.data) && ins.data[0] ? normalizeSticker(ins.data[0]) : normalizeSticker(row);
  return json({ ok: true, sticker, file_name: fileName, image_url: imageUrl });
}
async function updateStickerApi(env, body = {}) {
  const id = String(body.id || body.sticker_id || "").trim();
  if (!id) return json({ ok: false, error: "missing_id" }, 400);
  const patch = {};
  if (body.title !== undefined) patch.title = clip(body.title, 80);
  if (body.caption !== undefined) patch.caption = body.caption || null;
  if (body.tags !== undefined) patch.tags = parseTags(body.tags);
  if (body.scene !== undefined) patch.scene = clip(body.scene, 500);
  if (body.tone !== undefined) patch.tone = clip(body.tone, 60);
  const r = await supabaseJson(env, `/rest/v1/sticker_catalog?id=eq.${encodeURIComponent(id)}`, { method: "PATCH", headers: { "Content-Type": "application/json", "Prefer": "return=representation" }, body: JSON.stringify(patch) });
  return json(r.ok ? { ok: true, sticker: Array.isArray(r.data) && r.data[0] ? normalizeSticker(r.data[0]) : null } : r, r.ok ? 200 : 500);
}
async function deleteStickerApi(env, body = {}) {
  const id = String(body.id || body.sticker_id || "").trim();
  if (!id) return json({ ok: false, error: "missing_id" }, 400);
  const r = await supabaseJson(env, `/rest/v1/sticker_catalog?id=eq.${encodeURIComponent(id)}`, { method: "DELETE", headers: { "Prefer": "return=minimal" } });
  return json(r.ok ? { ok: true, deleted_id: id } : r, r.ok ? 200 : 500);
}

async function readLatestScreen(env) {
  if (!env.SCREENSHOT_KV) return { ok: false, error: "missing_kv_binding" };
  const meta = await env.SCREENSHOT_KV.getWithMetadata("latest", "arrayBuffer");
  if (!meta.value) return { ok: false, error: "LINJIAN_ERR_NOT_FOUND" };
  const md = meta.metadata || {};
  const bytes = meta.value.byteLength || 0;
  const mtime = Number(md.mtime || 0);
  const mimeType = md.content_type || "image/jpeg";
  return { ok: true, value: meta.value, metadata: md, bytes, mtime, mimeType, filename: mimeType === "image/png" ? "latest.png" : "latest.jpg" };
}
function latestScreenResult(screen, extra = {}) {
  if (!screen?.ok) return mcpText(screen || { ok: false, error: "LINJIAN_ERR_NOT_FOUND" }, true);
  const structured = { ok: true, filename: screen.filename, bytes: screen.bytes, content_type: screen.mimeType, mtime: screen.mtime, ...extra };
  const content = [{ type: "text", text: JSON.stringify(structured, null, 2) }];
  if (screen.bytes <= 3_500_000) content.push({ type: "image", data: arrayBufferToBase64(screen.value), mimeType: screen.mimeType });
  else structured.note = "截图过大，已只返回元数据。请降低截图大小或通过 /api/latest 读取图片。";
  return mcpContent(content, false, structured);
}
async function latestScreenMcp(env) {
  const screen = await readLatestScreen(env);
  return latestScreenResult(screen);
}
async function peekScreenMcp(env, device_id = DEFAULT_DEVICE, waitSeconds = 8) {
  if (!env.SCREENSHOT_KV) return mcpText({ ok: false, error: "missing_kv_binding" }, true);
  const before = await readLatestScreen(env).catch(() => null);
  const beforeMtime = before?.ok ? Number(before.mtime || 0) : 0;
  const queued = await responseJson(await queueCommand(env, { device_id, action: "peek", payload: { action: "peek" } }));
  const commandId = queued?.command?.id || "";
  const deadline = Date.now() + Math.max(0, Math.min(25, Number(waitSeconds || 0))) * 1000;
  let observed = null;
  let screen = null;
  while (Date.now() <= deadline) {
    if (commandId) {
      const row = await env.DB.prepare("SELECT command_json FROM commands WHERE id=?").bind(commandId).first();
      observed = row ? JSON.parse(row.command_json || "{}") : observed;
    }
    screen = await readLatestScreen(env).catch(() => null);
    if (screen?.ok && Number(screen.mtime || 0) > beforeMtime) {
      return latestScreenResult(screen, { queued, observed_status: observed || null, source: "peek_screen", note: "截图已上传，并已作为 image 内容返回。" });
    }
    await delay(700);
  }
  const fallback = screen?.ok ? latestScreenResult(screen, { queued, observed_status: observed || null, source: "peek_screen", stale: Number(screen.mtime || 0) <= beforeMtime, note: "等待期间没有发现新的截图；已返回服务器最近一张截图。" }) : null;
  if (fallback) return fallback;
  return mcpText({ ok: false, error: "screenshot_timeout", queued, observed_status: observed || null, note: "命令已下发，但等待期间没有收到可读取的截图。请检查手机端截图权限、无障碍服务和 /api/screenshot 上传日志。" }, true);
}

function nowIso() { return new Date().toISOString().replace(/\.\d{3}Z$/, "Z"); }
async function readJson(request) { try { return await request.json(); } catch (_) { return {}; } }
function clip(value, n) { return String(value || "").slice(0, n); }
function packageFor(app, pkg) { return pkg || KNOWN_APPS[app] || KNOWN_APPS[String(app || "").toLowerCase()] || ""; }
function normalizeAction(action) { action = String(action || "noop").trim().toLowerCase(); return ALLOWED_ACTIONS.has(action) ? action : "noop"; }
function actionType(action) {
  if (String(action).startsWith("get_") || String(action).startsWith("list_") || action === "peek" || action === "get_screen_nodes") return "status_check";
  if (String(action).includes("calendar")) return "calendar";
  if (String(action).includes("notification")) return "notification";
  return "command";
}

function makeCommand(data) {
  const payload = { ...(data.payload && typeof data.payload === "object" ? data.payload : {}), ...data };
  const action = normalizeAction(payload.action);
  const device_id = payload.device_id || DEFAULT_DEVICE;
  const id = payload.id || crypto.randomUUID();
  const pkg = packageFor(payload.app || "", payload.package || "");
  return { id, device_id, action, app: payload.app || "", package: pkg, status: "pending", created_at: nowIso(), payload, ...payload, id, device_id, action, package: pkg, status: "pending", created_at: nowIso() };
}
async function queueCommand(env, data) {
  const cmd = makeCommand(data || {});
  const event = await upsertActivity(env, { id: cmd.id, device_id: cmd.device_id, source: "linche", type: actionType(cmd.action), title: titleFor(cmd.action), subtitle: cmd.app || "", app_name: cmd.app || "", package_name: cmd.package || "", action: cmd.action, status: "pending", metadata_json: { command_id: cmd.id } });
  cmd.activity_event_id = event.id;
  await env.DB.prepare("INSERT OR REPLACE INTO commands(id, device_id, command_json, status, created_at, activity_event_id) VALUES(?,?,?,?,?,?)")
    .bind(cmd.id, cmd.device_id, JSON.stringify(cmd), "pending", cmd.created_at, cmd.activity_event_id).run();
  return json({ ok: true, command: cmd, queued: true });
}
function titleFor(action) {
  const map = { start_focus_mode: "开启专注模式", end_focus_mode: "结束专注模式", set_focus_plan: "设置专注规则", reply_focus_request: "回复专注求助", approve_focus_unlock: "批准专注应急", deny_focus_unlock: "拒绝专注应急", get_focus_status: "查看专注模式", send_notification: "发送提醒", set_alarm: "设置闹钟", trigger_guidian: "发起归电", mark_guidian_returned: "标记归电返回", set_guidian_config: "调整归电设置", screen_off: "让手机息屏", phone_screen_off: "让手机息屏", open_app: "打开应用", tap_text: "按文字点击", input_text: "输入文字", get_screen_nodes: "读取屏幕节点", screen_break_app: "开启应用门禁", temporary_screen_break_release: "临时放行应用", end_screen_break: "解除应用门禁", extend_screen_break: "延长应用门禁", deny_screen_break_release_request: "拒绝恢复申请", get_screen_break_state: "查看应用门禁状态", list_screen_break_apps: "列出可门禁应用", add_screen_break_app: "添加门禁应用", set_screen_break_passphrase: "设置门禁口令", get_calendar_state: "查看守护日历", upsert_calendar_event: "更新守护日历", get_wallet_state: "查看小金库", add_wallet_record: "添加小金库账单", edit_wallet_record: "重新编辑小金库账单", delete_wallet_record: "删除小金库账单", list_wallet_pending: "查看待审批", submit_companion_wallet_request: "陪伴者提交小金库申请", list_companion_wallet_requests: "查看陪伴者小金库申请", list_wallet_request_results: "查看小金库申请结果", save_user_wallet_request_result: "保存用户处理结果", confirm_wallet_record: "处理小金库账单", get_wallet_rules: "查看小金库规则", set_wallet_rules: "设置小金库规则", wallet_approval_request: "小金库花钱处理", save_wallet_request_result: "保存小金库处理结果", update_wallet_request_result: "更新小金库处理结果", get_takeout_state: "查看外卖助手", list_takeout_cards: "查看常点套餐", set_takeout_budget: "设置外卖预算", add_takeout_card: "保存常点套餐", update_takeout_card: "更新常点套餐", suggest_takeout_options: "推荐外卖", create_takeout_plan: "生成外卖计划", takeout_wallet_request: "外卖小金库申请", open_takeout_link: "打开外卖链接", copy_takeout_note: "复制外卖备注", record_takeout_order: "记录外卖账单", prepare_takeout_checkout: "自动点到付款页", auto_takeout_checkout: "自动点到付款页", get_takeout_checkout_status: "查看自动点单状态", cancel_takeout_checkout: "停止自动点单", peek: "查看屏幕", run_sequence: "执行组合动作", save_known_app: "保存应用别名", home: "回到手机桌面", back: "返回上一页", recents: "打开最近任务" };
  return map[action] || `执行 ${action}`;
}
async function pollCommand(env, url) {
  const deviceId = url.searchParams.get("device_id") || DEFAULT_DEVICE;
  const row = await env.DB.prepare("SELECT * FROM commands WHERE device_id=? AND status='pending' ORDER BY created_at ASC LIMIT 1").bind(deviceId).first();
  if (!row) return json({ ok: true, command: null });
  const cmd = JSON.parse(row.command_json || "{}");
  cmd.status = "dispatched"; cmd.dispatched_at = nowIso();
  await env.DB.prepare("UPDATE commands SET command_json=?, status='dispatched', dispatched_at=? WHERE id=?").bind(JSON.stringify(cmd), cmd.dispatched_at, row.id).run();
  return json({ ok: true, command: cmd });
}
async function commandStatus(env, url) {
  const id = url.searchParams.get("id") || "";
  const row = await env.DB.prepare("SELECT command_json FROM commands WHERE id=?").bind(id).first();
  return json({ ok: !!row, command: row ? JSON.parse(row.command_json || "{}") : null });
}
async function saveDeviceState(env, data) {
  const deviceId = data.device_id || DEFAULT_DEVICE;
  data.updated_at = nowIso();
  await env.DB.prepare("INSERT OR REPLACE INTO device_state(device_id, state_json, updated_at) VALUES(?,?,?)").bind(deviceId, JSON.stringify(data), data.updated_at).run();
  return json({ ok: true, device_id: deviceId });
}
async function getDeviceState(env, url) {
  const deviceId = url.searchParams.get("device_id") || DEFAULT_DEVICE;
  const row = await env.DB.prepare("SELECT state_json FROM device_state WHERE device_id=?").bind(deviceId).first();
  const state = row ? JSON.parse(row.state_json || "{}") : null;
  return json({ ok: true, device_id: deviceId, state, life_state: state });
}
async function getNestedState(env, url, key) {
  const deviceId = url.searchParams.get("device_id") || DEFAULT_DEVICE;
  const row = await env.DB.prepare("SELECT state_json FROM device_state WHERE device_id=?").bind(deviceId).first();
  const state = row ? JSON.parse(row.state_json || "{}") : {};
  return json({ ok: true, device_id: deviceId, [key]: state[key] || {} });
}

async function readDeviceStateObject(env, deviceId = DEFAULT_DEVICE) {
  const row = await env.DB.prepare("SELECT state_json, updated_at FROM device_state WHERE device_id=?").bind(deviceId || DEFAULT_DEVICE).first();
  const state = row ? JSON.parse(row.state_json || "{}") : {};
  if (row?.updated_at && !state.updated_at) state.updated_at = row.updated_at;
  return state;
}
async function writeDeviceStateObject(env, deviceId, state) {
  const updated = nowIso();
  state = state || {};
  state.device_id = deviceId || DEFAULT_DEVICE;
  state.updated_at = updated;
  await env.DB.prepare("INSERT OR REPLACE INTO device_state(device_id, state_json, updated_at) VALUES(?,?,?)")
    .bind(state.device_id, JSON.stringify(state), updated).run();
  return state;
}
function walletStateFromDeviceState(state = {}) { return state.wallet_state && typeof state.wallet_state === "object" ? state.wallet_state : {}; }
function currentMonthKey() { return new Date().toISOString().slice(0, 7); }
function localTimeText(d = new Date()) { return d.toISOString().slice(0, 16).replace("T", " "); }
function normalizeWalletDecision(decision) {
  decision = String(decision || "approved").toLowerCase();
  if (["reject", "rejected", "deny", "denied", "no"].includes(decision)) return { decision: "rejected", status: "approval_rejected" };
  if (["delay", "delayed", "later", "hold"].includes(decision)) return { decision: "delayed", status: "approval_delayed" };
  return { decision: "approved", status: "approval_approved" };
}
function defaultWalletApprovalMessage(decision) {
  if (decision === "rejected") return "这笔先不批，先冷静一下。";
  if (decision === "delayed") return "先等 20 分钟，真的还想买再回来申请。";
  return "通过，买完记得回小金库记一笔。";
}
function dedupeById(records = []) {
  const seen = new Set();
  const out = [];
  for (const r of records || []) {
    if (!r || typeof r !== "object") continue;
    const id = String(r.id || "");
    if (id && seen.has(id)) continue;
    if (id) seen.add(id);
    out.push(r);
  }
  return out;
}
function walletApprovalsFromState(ws = {}, month = "") {
  let arr = Array.isArray(ws.approval_records) ? ws.approval_records : [];
  if (month) arr = arr.filter(r => String(r.created_at_local || "").startsWith(month) || String(r.month || "") === month || String(r.created_at || "").startsWith(month));
  return arr;
}
function walletPendingFromState(ws = {}) { return Array.isArray(ws.pending_records) ? ws.pending_records : []; }
function walletMonthsFromState(ws = {}) { return Array.isArray(ws.month_summaries) ? ws.month_summaries : []; }
async function getCloudWalletState(env, deviceId = DEFAULT_DEVICE) {
  const row = await env.DB.prepare("SELECT value_json FROM companion_state WHERE key=?").bind(`wallet_cloud:${deviceId || DEFAULT_DEVICE}`).first();
  try { return row ? JSON.parse(row.value_json || "{}") : { approvals: [] }; } catch (_) { return { approvals: [] }; }
}
async function putCloudWalletState(env, deviceId, data) {
  const key = `wallet_cloud:${deviceId || DEFAULT_DEVICE}`;
  const next = { ...(data || {}), updated_at: nowIso() };
  await env.DB.prepare("INSERT OR REPLACE INTO companion_state(key, value_json, updated_at) VALUES(?,?,?)").bind(key, JSON.stringify(next), next.updated_at).run();
  return next;
}
async function getWalletStateDirect(env, deviceId = DEFAULT_DEVICE) {
  const state = await readDeviceStateObject(env, deviceId);
  return { ok: true, device_id: deviceId, wallet_state: walletStateFromDeviceState(state), raw_updated_at: state.updated_at || state.updated_at_local || "" };
}
async function getWalletMonthStateDirect(env, deviceId = DEFAULT_DEVICE, month = "") {
  const state = await readDeviceStateObject(env, deviceId);
  const ws = walletStateFromDeviceState(state);
  const currentMonth = ws.month || ws.current_month || currentMonthKey();
  const target = month || currentMonth;
  if (!month || target === currentMonth) return { ok: true, device_id: deviceId, month: target, wallet_state: ws, direct: true };
  const summary = walletMonthsFromState(ws).find(m => String(m.month || "") === target) || null;
  return { ok: true, device_id: deviceId, month: target, wallet_state: { ok: true, month: target, month_label: target, month_summary: summary, note: "历史月份明细需要手机端本地账本；Cloudflare 侧直接返回可用月份摘要。" }, direct: true };
}
async function listWalletMonthsDirect(env, deviceId = DEFAULT_DEVICE) {
  const state = await readDeviceStateObject(env, deviceId);
  const ws = walletStateFromDeviceState(state);
  return { ok: true, device_id: deviceId, month_summaries: walletMonthsFromState(ws), current_month: ws.current_month || ws.month || currentMonthKey(), raw_updated_at: state.updated_at || "" };
}
async function listWalletPendingDirect(env, deviceId = DEFAULT_DEVICE) {
  const state = await readDeviceStateObject(env, deviceId);
  const ws = walletStateFromDeviceState(state);
  return { ok: true, device_id: deviceId, pending_count: Number(ws.pending_count || 0), approval_count: Number(ws.approval_count || 0), pending_records: walletPendingFromState(ws), approval_records: walletApprovalsFromState(ws), raw_updated_at: state.updated_at || "" };
}
async function listWalletApprovalsDirect(env, deviceId = DEFAULT_DEVICE, month = "") {
  const state = await readDeviceStateObject(env, deviceId);
  const ws = walletStateFromDeviceState(state);
  const cloud = await getCloudWalletState(env, deviceId);
  let records = dedupeById([...(Array.isArray(cloud.approvals) ? cloud.approvals : []), ...walletApprovalsFromState(ws, month)]);
  if (month) records = records.filter(r => String(r.created_at_local || "").startsWith(month) || String(r.month || "") === month || String(r.created_at || "").startsWith(month));
  const pending = records.filter(r => String(r.status || "") === "approval_pending" || String(r.decision || "") === "waiting");
  return { ok: true, device_id: deviceId, month: month || ws.month || ws.current_month || "", pending_count: pending.length, approval_count: records.length, approval_records: records, raw_updated_at: state.updated_at || cloud.updated_at || "", direct: true };
}
async function getWalletRulesDirect(env, deviceId = DEFAULT_DEVICE) {
  const state = await readDeviceStateObject(env, deviceId);
  const ws = walletStateFromDeviceState(state);
  return { ok: true, device_id: deviceId, rules: ws.rules || {}, raw_updated_at: state.updated_at || "" };
}
async function submitWalletApprovalDirect(env, deviceId = DEFAULT_DEVICE, args = {}) {
  const amount = Number(args.amount || 0);
  if (!Number.isFinite(amount) || amount <= 0) return { ok: false, error: "amount_required" };
  const now = new Date();
  const approval = {
    id: args.id || crypto.randomUUID(),
    amount: Math.round(amount * 100) / 100,
    type: "approval_request",
    category: args.category || "其他",
    merchant: args.merchant || "",
    item: args.item || args.note || "这笔消费",
    reason: args.reason || "",
    note: args.note || args.item || "这笔消费",
    necessity: Number(args.necessity ?? 3),
    impulse: Number(args.impulse ?? 3),
    source: args.source || "mcp",
    status: "approval_pending",
    decision: "waiting",
    approval_message: args.approval_message || "已提交给陪伴对象审批，等待回复。",
    created_at_ms: Number(args.created_at_ms || now.getTime()),
    created_at_local: args.created_at_local || localTimeText(now),
    month: args.month || currentMonthKey()
  };
  const cloud = await getCloudWalletState(env, deviceId);
  const approvals = dedupeById([approval, ...(Array.isArray(cloud.approvals) ? cloud.approvals : [])]).slice(0, 200);
  await putCloudWalletState(env, deviceId, { ...cloud, approvals });
  const cmd = await responseJson(await queueCommand(env, { device_id: deviceId, action: "submit_wallet_approval", ...approval, payload: { action: "submit_wallet_approval", ...approval } }));
  return { ok: true, device_id: deviceId, approval, queued_sync: cmd, direct: true };
}
async function decideWalletApprovalDirect(env, deviceId = DEFAULT_DEVICE, args = {}) {
  const id = String(args.id || "");
  if (!id) return { ok: false, error: "id_required" };
  const norm = normalizeWalletDecision(args.decision || args.status || "approved");
  const message = args.note || args.message || args.approval_message || defaultWalletApprovalMessage(norm.decision);
  let found = null;
  const cloud = await getCloudWalletState(env, deviceId);
  let approvals = Array.isArray(cloud.approvals) ? cloud.approvals.slice() : [];
  approvals = approvals.map(r => {
    if (r && String(r.id || "") === id) {
      found = { ...r, status: norm.status, decision: norm.decision, approval_message: message, approved_by: args.approved_by || "陪伴对象", decided_at_ms: Date.now(), decided_at_local: localTimeText(new Date()) };
      return found;
    }
    return r;
  });
  const state = await readDeviceStateObject(env, deviceId);
  const ws = walletStateFromDeviceState(state);
  if (Array.isArray(ws.approval_records)) {
    ws.approval_records = ws.approval_records.map(r => {
      if (r && String(r.id || "") === id) {
        found = { ...r, status: norm.status, decision: norm.decision, approval_message: message, approved_by: args.approved_by || "陪伴对象", decided_at_ms: Date.now(), decided_at_local: localTimeText(new Date()) };
        return found;
      }
      return r;
    });
    state.wallet_state = ws;
    await writeDeviceStateObject(env, deviceId, state);
  }
  if (!found) {
    found = { id, status: norm.status, decision: norm.decision, approval_message: message, approved_by: args.approved_by || "陪伴对象", decided_at_ms: Date.now(), decided_at_local: localTimeText(new Date()), source: "mcp_direct" };
    approvals.unshift(found);
  } else if (!approvals.some(r => r && String(r.id || "") === id)) approvals.unshift(found);
  await putCloudWalletState(env, deviceId, { ...cloud, approvals: dedupeById(approvals).slice(0, 200) });
  const payload = { action: args.safe_action || "decide_wallet_approval", id, decision: norm.decision, approval_result: norm.decision, status: norm.decision, message, note: message, approval_message: message, approved_by: args.approved_by || "陪伴对象" };
  const cmd = await responseJson(await queueCommand(env, { device_id: deviceId, ...payload, payload }));
  const commandId = cmd?.command?.id || "";
  const waitSeconds = Number(args.wait_seconds ?? 8);
  const obs = commandId && waitSeconds > 0 ? await waitCommand(env, commandId, waitSeconds) : null;
  const observedCommand = obs?.command || cmd?.command || null;
  const phoneDone = ["completed", "failed"].includes(String(observedCommand?.status || ""));
  const phoneOk = String(observedCommand?.status || "") === "completed";
  return {
    ok: phoneOk,
    device_id: deviceId,
    approval: found,
    decision: norm.decision,
    message,
    cloud_saved: true,
    queued_sync: cmd,
    observed_status: observedCommand,
    phone_confirmed: phoneDone,
    confirmation_state: phoneOk ? "phone_completed" : (phoneDone ? "phone_failed" : "waiting_phone_confirmation"),
    user_message: phoneOk ? "审批结果已写入手机端掌心窗。" : "审批结果已在服务端排队，正在等待手机端确认；不要显示为最终成功。",
    direct: true
  };
}
async function walletApprovalRequestDirect(env, deviceId = DEFAULT_DEVICE, args = {}) {
  const amount = Number(args.amount || 0);
  let decision = String(args.decision || "");
  let message = String(args.message || "");
  const necessity = Number(args.necessity ?? 3), impulse = Number(args.impulse ?? 3);
  if (!decision) {
    decision = "approved"; message = "通过。记得买完回来给小金库记一笔。";
    if (amount >= 50 && impulse >= 4 && necessity <= 3) { decision = "delayed"; message = "先冷静 20 分钟。真的还想要再回来申请。"; }
  }
  const created = await submitWalletApprovalDirect(env, deviceId, { ...args, source: "mcp" });
  if (!created.ok) return created;
  return await decideWalletApprovalDirect(env, deviceId, { id: created.approval.id, decision, message });
}
async function saveWalletRequestResultDirect(env, deviceId = DEFAULT_DEVICE, args = {}) {
  return await decideWalletApprovalDirect(env, deviceId, { ...args, decision: args.status || args.decision || "ok", message: args.note || args.message || "", safe_action: "save_wallet_request_result" });
}
function takeoutStateFromDeviceState(state = {}) { return state.takeout_state && typeof state.takeout_state === "object" ? state.takeout_state : {}; }
async function getTakeoutStateDirect(env, deviceId = DEFAULT_DEVICE) {
  const state = await readDeviceStateObject(env, deviceId);
  return { ok: true, device_id: deviceId, takeout_state: takeoutStateFromDeviceState(state), wallet_state: walletStateFromDeviceState(state), raw_updated_at: state.updated_at || state.updated_at_local || "" };
}
function takeoutCards(ts = {}) { return Array.isArray(ts.cards) ? ts.cards : []; }
function takeoutEstimate(card = {}, fallback = 0) { const min = Number(card.price_min || 0), max = Number(card.price_max || 0); if (min > 0 && max > 0) return Math.round(((min + max) / 2) * 100) / 100; if (max > 0) return Math.round(max * 100) / 100; return Math.round(Math.max(min, fallback) * 100) / 100; }
function takeoutMatch(card = {}, query = "") { const q = String(query || "").trim().toLowerCase(); if (!q) return true; const s = `${card.title || ""} ${card.items || ""} ${card.tags || ""} ${card.note || ""} ${card.platform || ""}`.toLowerCase(); return q.split(/\s+/).some(part => part && s.includes(part)); }
async function suggestTakeoutOptionsDirect(env, deviceId = DEFAULT_DEVICE, args = {}) {
  const state = await readDeviceStateObject(env, deviceId);
  const ts = takeoutStateFromDeviceState(state);
  const budget = Number(args.budget ?? ts.meal_budget ?? 25);
  const limit = Math.max(1, Math.min(8, Number(args.limit || 3)));
  const cards = takeoutCards(ts);
  const picked = [];
  for (let pass = 0; pass < 3 && picked.length < limit; pass++) {
    for (const card of cards) {
      if (!card || picked.some(x => String(x.id || "") === String(card.id || ""))) continue;
      const est = takeoutEstimate(card, budget);
      const budgetOk = est <= 0 || budget <= 0 || est <= budget;
      const queryOk = takeoutMatch(card, args.query || "");
      if ((pass === 0 && budgetOk && queryOk) || (pass === 1 && budgetOk) || pass === 2) picked.push({ ...card, estimated_amount: est, budget_ok: budgetOk, reason: budgetOk ? "在单餐预算内，适合快速打开。" : `可能超过单餐预算 ¥${budget.toFixed(2)}，建议先走小金库申请。` });
      if (picked.length >= limit) break;
    }
  }
  return { ok: true, device_id: deviceId, meal_budget: Math.round(budget * 100) / 100, taste_note: ts.taste_note || "", suggestions: picked, note: "只从用户保存的常点外卖库里推荐；不会自动下单或付款。" };
}

async function saveDeviceReport(env, data) {
  const id = data.command_id || data.id || "";
  let command = null;
  if (id) {
    const row = await env.DB.prepare("SELECT command_json FROM commands WHERE id=?").bind(id).first();
    if (row) command = JSON.parse(row.command_json || "{}");
    if (command) {
      command.status = data.ok ? "completed" : "failed";
      command.completed_at = nowIso();
      command.result = data.result || "";
      command.report = data;
      await env.DB.prepare("UPDATE commands SET command_json=?, status=?, completed_at=?, result=? WHERE id=?").bind(JSON.stringify(command), command.status, command.completed_at, String(data.result || ""), id).run();
      await upsertActivity(env, { id: command.activity_event_id || id, device_id: command.device_id || DEFAULT_DEVICE, source: "linche", type: actionType(command.action), action: command.action, status: command.status, metadata_json: { command_id: id, result: clip(data.result, 500) } });
    }
  }
  return json({ ok: true, report: data, command });
}
async function upsertActivity(env, data) {
  const entry = { id: data.id || crypto.randomUUID(), device_id: data.device_id || DEFAULT_DEVICE, created_at: data.created_at || data.at || nowIso(), source: data.source || "linche", type: data.type || "activity", title: clip(data.title, 100), subtitle: clip(data.subtitle || data.summary, 220), app_name: clip(data.app_name || data.app, 80), package_name: clip(data.package_name || data.package, 180), action: clip(data.action, 80), status: clip(data.status || "completed", 24), metadata_json: JSON.stringify(data.metadata_json || data.metadata || {}) };
  await env.DB.prepare("INSERT OR REPLACE INTO activity_events(id, device_id, created_at, source, type, title, subtitle, app_name, package_name, action, status, metadata_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")
    .bind(entry.id, entry.device_id, entry.created_at, entry.source, entry.type, entry.title, entry.subtitle, entry.app_name, entry.package_name, entry.action, entry.status, entry.metadata_json).run();
  return entry;
}
async function saveActivityEvent(env, data) { return json({ ok: true, event: await upsertActivity(env, data || {}) }); }
async function listActivityEvents(env, url) {
  const deviceId = url.searchParams.get("device_id") || "";
  const source = url.searchParams.get("source") || "";
  const limit = Math.max(1, Math.min(500, Number(url.searchParams.get("limit") || 50)));
  let query = "SELECT * FROM activity_events"; const binds = []; const where = [];
  if (deviceId) { where.push("device_id=?"); binds.push(deviceId); }
  if (source) { where.push("source=?"); binds.push(source); }
  if (where.length) query += " WHERE " + where.join(" AND ");
  query += " ORDER BY created_at DESC LIMIT ?"; binds.push(limit);
  const rows = await env.DB.prepare(query).bind(...binds).all();
  const events = (rows.results || []).map(r => ({ ...r, metadata_json: safeJson(r.metadata_json) }));
  return json({ ok: true, events, count: events.length });
}
function safeJson(value) { try { return JSON.parse(value || "{}"); } catch (_) { return {}; } }
async function getCompanionState(env, url) {
  const limit = Math.max(1, Math.min(50, Number(url.searchParams.get("limit") || 20)));
  const whisperRow = await env.DB.prepare("SELECT value_json FROM companion_state WHERE key='whisper'").first();
  const whisper = whisperRow ? safeJson(whisperRow.value_json) : { content: "把今天，轻轻收进窗里。", author: "陪伴对象", updated_at: "", version: 1 };
  const rows = await env.DB.prepare("SELECT * FROM activity_events ORDER BY created_at DESC LIMIT ?").bind(limit).all();
  const actions = (rows.results || []).map(e => ({ id: e.id, at: e.created_at, created_at: e.created_at, kind: e.type, type: e.type, title: e.title, summary: e.subtitle, subtitle: e.subtitle, status: e.status, source: e.source || "linche" }));
  return json({ ok: true, whisper, actions });
}
async function saveWhisper(env, data) {
  const row = await env.DB.prepare("SELECT value_json FROM companion_state WHERE key='whisper'").first();
  const prev = row ? safeJson(row.value_json) : {};
  const whisper = { content: clip(data.content, 240), author: clip(data.author || "用户", 24), updated_at: nowIso(), version: Number(prev.version || 0) + 1 };
  await env.DB.prepare("INSERT OR REPLACE INTO companion_state(key, value_json, updated_at) VALUES('whisper', ?, ?)").bind(JSON.stringify(whisper), whisper.updated_at).run();
  return json({ ok: true, whisper });
}
async function saveCompanionAction(env, data) {
  const event = await upsertActivity(env, { ...data, source: "linche", type: data.type || data.kind || "activity", title: data.title || "完成了一次行动", subtitle: data.summary || data.subtitle || "", status: data.status || "completed" });
  return json({ ok: true, action: { id: event.id, at: event.created_at, kind: event.type, title: event.title, summary: event.subtitle, status: event.status, source: "linche" } });
}
async function saveUnlockRequest(env, data) {
  const id = data.id || crypto.randomUUID(); const created = data.created_at || nowIso(); data.id = id; data.created_at = created;
  await env.DB.prepare("INSERT OR REPLACE INTO unlock_requests(id, request_json, created_at) VALUES(?,?,?)").bind(id, JSON.stringify(data), created).run();
  return json({ ok: true, request: data });
}
async function listUnlockRequests(env) {
  const rows = await env.DB.prepare("SELECT request_json FROM unlock_requests ORDER BY created_at DESC LIMIT 50").all();
  return json({ ok: true, requests: (rows.results || []).map(r => safeJson(r.request_json)) });
}
async function saveScreenshot(env, request) {
  if (!env.SCREENSHOT_KV) return json({ ok: false, error: "missing_kv_binding" }, 501);
  const buf = await request.arrayBuffer();
  if (!buf || buf.byteLength < 100) return json({ ok: false, error: "LINJIAN_ERR_NO_IMAGE" }, 400);
  const type = new Uint8Array(buf)[0] === 0x89 ? "image/png" : "image/jpeg";
  const mtime = Date.now() / 1000;
  await env.SCREENSHOT_KV.put("latest", buf, { metadata: { content_type: type, size: String(buf.byteLength), mtime: String(mtime) } });
  await env.SCREENSHOT_KV.put("latest_meta", JSON.stringify({ ok: true, filename: type === "image/png" ? "latest.png" : "latest.jpg", size: buf.byteLength, mtime, url: "/api/latest", content_type: type }));
  return json({ ok: true, size: buf.byteLength, mtime });
}
async function latestMeta(env) {
  if (!env.SCREENSHOT_KV) return json({ ok: false, error: "missing_kv_binding" }, 501);
  const meta = await env.SCREENSHOT_KV.get("latest_meta", "json");
  if (!meta) return json({ ok: false, error: "LINJIAN_ERR_NOT_FOUND" }, 404);
  return json(meta);
}
async function latestScreenshot(env) {
  if (!env.SCREENSHOT_KV) return json({ ok: false, error: "missing_kv_binding" }, 501);
  const meta = await env.SCREENSHOT_KV.getWithMetadata("latest", "arrayBuffer");
  if (!meta.value) return json({ ok: false, error: "LINJIAN_ERR_NOT_FOUND" }, 404);
  const md = meta.metadata || {};
  return new Response(meta.value, { headers: { "Content-Type": md.content_type || "image/jpeg", "Cache-Control": "no-store" } });
}
