package dev.linjian.peek;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/**
 * 专注模式：全机托管锁。
 * 应用门禁负责锁单个 App；专注模式只负责全机专注：
 * 解锁后仍回到全屏页，只留“留言给他”和很窄的应急放行。
 */
public class FocusMode {
    public static final String KEY_STATE = "focus_mode_state_v1";
    private static final String SELF_PACKAGE = "dev.linjian.peek";
    private static volatile long lastLockAt = 0L;
    private static volatile boolean lockActivityVisible = false;
    private static volatile long lockActivityVisibleAt = 0L;

    private static JSONObject defaultState() {
        JSONObject s = new JSONObject();
        try {
            s.put("focus_version", "0.3.9.1-public-diary-annotations");
            s.put("enabled", false);
            s.put("active", false);
            s.put("mode", "strict");
            s.put("scope", "full_phone");
            s.put("managed_by_ai", true);
            s.put("goal", "先离开手机");
            s.put("message", "你提前把这段时间交给我了，我会帮你守住。");
            s.put("message_source", "default");
            s.put("started_at_ms", 0L);
            s.put("until_ms", 0L);
            s.put("temporary_until_ms", 0L);
            s.put("emergency_total", 1);
            s.put("emergency_used", 0);
            s.put("emergency_minutes", 1);
            s.put("requests", new JSONArray());
            s.put("messages", new JSONArray());
            s.put("logs", new JSONArray());
        } catch (Exception ignored) { }
        return s;
    }

    private static JSONObject state(Context ctx) {
        try {
            String raw = AppPrefs.get(ctx).getString(KEY_STATE, "");
            if (raw != null && raw.trim().length() > 0) {
                JSONObject s = new JSONObject(raw);
                if (!s.has("requests")) s.put("requests", new JSONArray());
                if (!s.has("messages")) s.put("messages", new JSONArray());
                if (!s.has("logs")) s.put("logs", new JSONArray());
                if (!s.has("scope")) s.put("scope", "full_phone");
                if (!s.has("managed_by_ai")) s.put("managed_by_ai", true);
                if (!s.has("message_source")) s.put("message_source", "default");
                s.put("focus_version", "0.3.9.1-public-diary-annotations");
                return s;
            }
        } catch (Exception ignored) { }
        return defaultState();
    }

    private static void save(Context ctx, JSONObject s) { AppPrefs.get(ctx).edit().putString(KEY_STATE, s.toString()).apply(); }

    public static JSONObject handleCommand(Context ctx, JSONObject cmd) {
        String action = cmd == null ? "" : cmd.optString("action", "");
        try {
            if ("get_focus_status".equals(action)) return put(new JSONObject(), true, config(ctx).toString());
            if ("start_focus_mode".equals(action) || "enable_focus_mode".equals(action)) return start(ctx, cmd);
            if ("end_focus_mode".equals(action) || "disable_focus_mode".equals(action)) return end(ctx, cmd.optString("reason", "remote_end"));
            if ("set_focus_plan".equals(action)) return setPlan(ctx, cmd);
            if ("request_focus_unlock".equals(action) || "create_focus_request".equals(action)) return createRequest(ctx, cmd.optString("reason", cmd.optString("message", "")));
            if ("reply_focus_request".equals(action) || "focus_reply".equals(action)) return reply(ctx, cmd.optString("message", cmd.optString("text", "我在。")), false);
            if ("approve_focus_unlock".equals(action) || "temporary_focus_unlock".equals(action)) return approve(ctx, cmd);
            if ("deny_focus_unlock".equals(action)) return deny(ctx, cmd.optString("message", cmd.optString("reason", "这次先不放行，我陪你把这段时间守住。")));
        } catch (Exception e) { return put(new JSONObject(), false, ScreenshotService.shortMsg(e)); }
        return put(new JSONObject(), false, "unknown_focus_action:" + action);
    }

    public static boolean isFocusAction(String action) {
        if (action == null) return false;
        return "get_focus_status".equals(action) || "start_focus_mode".equals(action) || "enable_focus_mode".equals(action)
                || "end_focus_mode".equals(action) || "disable_focus_mode".equals(action) || "set_focus_plan".equals(action)
                || "request_focus_unlock".equals(action) || "create_focus_request".equals(action) || "reply_focus_request".equals(action)
                || "focus_reply".equals(action) || "approve_focus_unlock".equals(action) || "temporary_focus_unlock".equals(action)
                || "deny_focus_unlock".equals(action);
    }

    private static JSONObject start(Context ctx, JSONObject cmd) throws Exception {
        JSONObject s = state(ctx);
        long now = System.currentTimeMillis();
        double minutes = positive(cmd.optDouble("duration_minutes", 0), positive(cmd.optDouble("minutes", 0), 30));
        long until = cmd.optLong("until_ms", cmd.optLong("locked_until_ms", 0));
        if (until <= now) until = now + Math.round(minutes * 60000.0);
        s.put("enabled", true);
        s.put("active", true);
        s.put("started_at_ms", now);
        s.put("started_at_local", formatLocal(now));
        s.put("until_ms", until);
        s.put("until_local", formatLocal(until));
        s.put("temporary_until_ms", 0L);
        s.put("mode", "strict");
        s.put("scope", "full_phone");
        s.put("managed_by_ai", cmd.optBoolean("managed_by_ai", true));
        String goal = firstNonEmpty(cmd.optString("goal", ""), cmd.optString("target", ""), cmd.optString("title", ""), s.optString("goal", "先离开手机"));
        String msg = firstNonEmpty(cmd.optString("message", ""), cmd.optString("guard_message", ""), cmd.optString("says", ""), cmd.optString("ai_message", ""), s.optString("message", "你提前把这段时间交给我了，我会帮你守住。"));
        s.put("goal", goal);
        s.put("reason", cmd.optString("reason", s.optString("reason", "专注模式已开启")));
        s.put("message", msg);
        s.put("message_source", cmd.optBoolean("managed_by_ai", true) ? "ai" : "manual");
        s.put("emergency_total", Math.max(0, cmd.optInt("emergency_total", cmd.optInt("emergency_times", s.optInt("emergency_total", 1)))));
        s.put("emergency_minutes", Math.max(1, cmd.optInt("emergency_minutes", s.optInt("emergency_minutes", 1))));
        s.put("emergency_used", 0);
        log(s, "开启专注模式到 " + formatLocal(until));
        appendMessage(s, "ai", s.optString("message"), false);
        save(ctx, s);
        forceShowLockActivity(ctx);
        ScreenshotService svc = ScreenshotService.getInstance();
        if (cmd.optBoolean("screen_off", false) && svc != null) svc.doLockScreen();
        return put(new JSONObject(), true, "focus_started until " + s.optString("until_local"));
    }

    private static JSONObject setPlan(Context ctx, JSONObject cmd) throws Exception {
        JSONObject s = state(ctx);
        s.put("enabled", cmd.optBoolean("enabled", true));
        s.put("mode", "strict");
        s.put("scope", "full_phone");
        s.put("managed_by_ai", cmd.optBoolean("managed_by_ai", s.optBoolean("managed_by_ai", true)));
        String goal = firstNonEmpty(cmd.optString("goal", ""), cmd.optString("target", ""), cmd.optString("title", ""));
        String msg = firstNonEmpty(cmd.optString("message", ""), cmd.optString("guard_message", ""), cmd.optString("says", ""), cmd.optString("ai_message", ""));
        if (goal.length() > 0) s.put("goal", goal);
        if (msg.length() > 0) { s.put("message", msg); s.put("message_source", cmd.optBoolean("managed_by_ai", true) ? "ai" : "manual"); }
        if (cmd.has("emergency_total") || cmd.has("emergency_times")) s.put("emergency_total", Math.max(0, cmd.optInt("emergency_total", cmd.optInt("emergency_times", 1))));
        if (cmd.has("emergency_minutes")) s.put("emergency_minutes", Math.max(1, cmd.optInt("emergency_minutes", 1)));
        log(s, "更新专注模式计划");
        save(ctx, s);
        return put(new JSONObject(), true, "focus_plan_saved");
    }

    private static JSONObject end(Context ctx, String reason) throws Exception {
        JSONObject s = state(ctx);
        s.put("active", false);
        s.put("temporary_until_ms", 0L);
        s.put("ended_at_ms", System.currentTimeMillis());
        s.put("ended_at_local", formatLocal(System.currentTimeMillis()));
        log(s, "结束专注模式：" + reason);
        save(ctx, s);
        return put(new JSONObject(), true, "focus_ended");
    }

    private static JSONObject createRequest(Context ctx, String reason) throws Exception {
        JSONObject s = state(ctx);
        String text = reason == null ? "" : reason.trim();
        if (text.length() == 0) text = "我想申请应急解锁。";
        JSONObject req = new JSONObject();
        req.put("id", String.valueOf(System.currentTimeMillis()));
        req.put("device_id", AppPrefs.device(ctx));
        req.put("reason", text);
        req.put("created_at_ms", System.currentTimeMillis());
        req.put("created_at_local", formatLocal(System.currentTimeMillis()));
        req.put("status", "pending");
        JSONArray arr = s.optJSONArray("requests"); if (arr == null) arr = new JSONArray();
        arr.put(req); while (arr.length() > 20) arr.remove(0); s.put("requests", arr);
        appendMessage(s, "user", text, true);
        log(s, "提交专注求助：" + text);
        save(ctx, s);
        return put(new JSONObject(), true, "focus_request_created:" + req.optString("id"));
    }

    private static JSONObject reply(Context ctx, String message, boolean approved) throws Exception {
        JSONObject s = state(ctx);
        appendMessage(s, "ai", message == null || message.trim().length() == 0 ? "我在。" : message.trim(), approved);
        log(s, "专注小窗回复：" + message);
        save(ctx, s);
        return put(new JSONObject(), true, "focus_reply_saved");
    }

    private static JSONObject deny(Context ctx, String message) throws Exception {
        JSONObject s = state(ctx);
        markLatestRequest(s, "denied");
        appendMessage(s, "ai", message == null || message.trim().length() == 0 ? "这次先不放行，我陪你把这段时间守住。" : message.trim(), false);
        log(s, "拒绝专注解锁");
        save(ctx, s);
        return put(new JSONObject(), true, "focus_unlock_denied");
    }

    private static JSONObject approve(Context ctx, JSONObject cmd) throws Exception {
        JSONObject s = state(ctx);
        int minutes = Math.max(1, cmd.optInt("minutes", cmd.optInt("allowed_minutes", s.optInt("emergency_minutes", 1))));
        s.put("temporary_until_ms", System.currentTimeMillis() + minutes * 60000L);
        s.put("emergency_used", s.optInt("emergency_used", 0) + 1);
        markLatestRequest(s, "approved");
        String message = cmd.optString("message", "应急放行已批准，" + minutes + " 分钟后我会重新守住你。");
        appendMessage(s, "ai", message, true);
        log(s, "批准专注应急放行 " + minutes + " 分钟");
        save(ctx, s);
        ScreenshotService svc = ScreenshotService.getInstance();
        if (svc != null) svc.doHome();
        return put(new JSONObject(), true, "focus_temporary_unlocked:" + minutes + "min");
    }

    public static boolean offlineEmergencyUnlock(Context ctx, String reason) {
        try {
            JSONObject s = state(ctx);
            if (!isActiveRaw(s, System.currentTimeMillis())) return true;
            int total = Math.max(0, s.optInt("emergency_total", 1));
            int used = Math.max(0, s.optInt("emergency_used", 0));
            if (used >= total) return false;
            int minutes = Math.max(1, s.optInt("emergency_minutes", 1));
            s.put("temporary_until_ms", System.currentTimeMillis() + minutes * 60000L);
            s.put("emergency_used", used + 1);
            appendMessage(s, "user", reason == null || reason.trim().length() == 0 ? "离线应急解锁" : reason.trim(), true);
            appendMessage(s, "ai", "离线应急已放行 " + minutes + " 分钟，时间到我会继续守住。", true);
            log(s, "离线应急放行 " + minutes + " 分钟");
            save(ctx, s);
            return true;
        } catch (Exception e) { return false; }
    }

    public static void submitContactMessage(Context ctx, String text) { try { createRequest(ctx, text); } catch (Exception ignored) { } }

    public static boolean isActive(Context ctx) {
        try {
            JSONObject s = state(ctx); long now = System.currentTimeMillis();
            if (!isActiveRaw(s, now)) return false;
            if (now < s.optLong("temporary_until_ms", 0L)) return false;
            return true;
        } catch (Exception e) { return false; }
    }

    private static boolean isActiveRaw(JSONObject s, long now) throws Exception {
        if (s == null || !s.optBoolean("active", false)) return false;
        long until = s.optLong("until_ms", 0L);
        if (until > 0 && now >= until) { s.put("active", false); return false; }
        return true;
    }

    public static void setLockActivityVisible(boolean visible) {
        lockActivityVisible = visible;
        lockActivityVisibleAt = visible ? System.currentTimeMillis() : 0L;
    }

    public static boolean isLockActivityVisible() {
        return lockActivityVisible && (System.currentTimeMillis() - lockActivityVisibleAt < 30000L);
    }

    public static void onForegroundPackage(Context ctx, String pkg) {
        try {
            if (pkg == null || pkg.trim().length() == 0) return;
            String p = pkg.trim();
            long now = System.currentTimeMillis();
            JSONObject s = state(ctx);
            if (!isActiveRaw(s, now)) { save(ctx, s); return; }
            if (now < s.optLong("temporary_until_ms", 0L)) return;
            if (isLockActivityVisible()) return;
            if (SELF_PACKAGE.equals(p) || isProtectedPackage(ctx, p)) return;
            if (now - lastLockAt < 1800) return;
            lastLockAt = now;
            // 不再先执行 HOME：HOME 和 Activity 拉起会在部分系统上互相打架，表现为专注页闪退回桌面。
            // 直接把专注锁定页拉到前台，由 Activity 覆盖当前界面。
            startLockActivity(ctx);
        } catch (Exception e) { DebugState.append(ctx, "专注模式前台检查异常：" + ScreenshotService.shortMsg(e)); }
    }

    /**
     * 远程/MCP 开专注时，命令通常在 CompanionService 的后台轮询线程里执行。
     * Android 10+ / targetSdk 34 可能会拦截后台 Service 直接拉起 Activity，
     * 所以这里同时走应用 Context + 无障碍 Service Context，并延迟补拉一次。
     */
    public static void forceShowLockActivity(Context ctx) {
        try {
            final Context appCtx = ctx == null ? null : ctx.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            if (appCtx != null) {
                main.post(() -> startLockActivity(appCtx));
            }
            final ScreenshotService svc = ScreenshotService.getInstance();
            if (svc != null) {
                main.postDelayed(() -> {
                    try { startLockActivity(svc); }
                    catch (Exception e) { DebugState.append(svc, "专注锁定页无障碍拉起异常：" + ScreenshotService.shortMsg(e)); }
                }, 350);
                main.postDelayed(() -> {
                    try {
                        String pkg = ScreenshotService.currentPackage();
                        if (pkg != null && pkg.trim().length() > 0) onForegroundPackage(svc, pkg);
                    } catch (Exception e) {
                        DebugState.append(svc, "专注模式二次前台检查异常：" + ScreenshotService.shortMsg(e));
                    }
                }, 900);
            }
        } catch (Exception e) {
            try { if (ctx != null) DebugState.append(ctx, "专注锁定页强制拉起失败：" + ScreenshotService.shortMsg(e)); } catch (Exception ignored) { }
        }
    }

    public static void startLockActivity(Context ctx) {
        try {
            Intent i = new Intent(ctx, FocusLockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            ctx.startActivity(i);
        } catch (Exception e) { DebugState.append(ctx, "专注锁定页拉起失败：" + ScreenshotService.shortMsg(e)); }
    }

    public static JSONObject config(Context ctx) {
        JSONObject s = state(ctx);
        try {
            long now = System.currentTimeMillis();
            if (s.optBoolean("active", false) && s.optLong("until_ms", 0L) > 0 && now >= s.optLong("until_ms", 0L)) {
                s.put("active", false); save(ctx, s);
            }
            s.put("remaining_ms", Math.max(0L, s.optLong("until_ms", 0L) - now));
            s.put("temporary_active", now < s.optLong("temporary_until_ms", 0L));
            s.put("temporary_remaining_ms", Math.max(0L, s.optLong("temporary_until_ms", 0L) - now));
            s.put("emergency_remaining", Math.max(0, s.optInt("emergency_total", 1) - s.optInt("emergency_used", 0)));
        } catch (Exception ignored) { }
        return s;
    }

    public static String summaryLine(Context ctx) {
        try {
            JSONObject s = config(ctx);
            if (!s.optBoolean("active", false)) return "专注模式 · 未开启";
            if (s.optBoolean("temporary_active", false)) return "专注模式 · 应急放行中";
            return "专注模式 · " + s.optString("goal", "守住手机") + " · 剩余 " + remainText(s.optLong("remaining_ms", 0));
        } catch (Exception e) { return "专注模式 · 读取中"; }
    }

    public static String pretty(Context ctx) {
        try {
            JSONObject s = config(ctx);
            StringBuilder sb = new StringBuilder();
            sb.append(summaryLine(ctx)).append("\n");
            sb.append("范围：全机专注\n");
            sb.append("目标：").append(s.optString("goal", "-")).append("\n");
            sb.append("托管：").append(s.optBoolean("managed_by_ai", true) ? "由机填写目标/时间/他说" : "手动规则").append("\n");
            if (s.optBoolean("active", false)) sb.append("结束：").append(s.optString("until_local", "-")).append("\n");
            sb.append("应急：").append(s.optInt("emergency_used", 0)).append("/").append(s.optInt("emergency_total", 1)).append("，每次 ").append(s.optInt("emergency_minutes", 1)).append(" 分钟\n");
            JSONArray req = s.optJSONArray("requests"); if (req != null && req.length() > 0) sb.append("最近求助：").append(req.length()).append(" 条\n");
            return sb.toString().trim();
        } catch (Exception e) { return "专注模式读取失败：" + ScreenshotService.shortMsg(e); }
    }

    private static void appendMessage(JSONObject s, String role, String text, boolean important) throws Exception {
        JSONArray arr = s.optJSONArray("messages"); if (arr == null) arr = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", role == null ? "ai" : role);
        msg.put("text", text == null ? "" : text);
        msg.put("important", important);
        msg.put("time_ms", System.currentTimeMillis());
        msg.put("time", formatLocal(System.currentTimeMillis()));
        arr.put(msg); while (arr.length() > 30) arr.remove(0); s.put("messages", arr);
    }

    private static void markLatestRequest(JSONObject s, String status) throws Exception {
        JSONArray arr = s.optJSONArray("requests");
        if (arr == null || arr.length() == 0) return;
        JSONObject last = arr.optJSONObject(arr.length() - 1);
        if (last != null) last.put("status", status == null ? "done" : status);
    }

    private static void log(JSONObject s, String text) throws Exception {
        JSONArray arr = s.optJSONArray("logs"); if (arr == null) arr = new JSONArray();
        JSONObject o = new JSONObject(); o.put("time_ms", System.currentTimeMillis()); o.put("time", formatLocal(System.currentTimeMillis())); o.put("message", text == null ? "" : text);
        arr.put(o); while (arr.length() > 60) arr.remove(0); s.put("logs", arr);
    }

    private static boolean isProtectedPackage(Context ctx, String pkg) {
        if (pkg == null || pkg.trim().length() == 0) return true;
        String p = pkg.trim();
        if (SELF_PACKAGE.equals(p)) return true;
        String[] protectedPkgs = {"com.android.settings", "com.android.phone", "com.google.android.dialer", "com.android.contacts", "com.android.mms", "com.android.deskclock", "com.coloros.alarmclock", "com.eg.android.AlipayGphone"};
        for (String one : protectedPkgs) if (p.equals(one)) return true;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(p, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            String name = label == null ? "" : label.toString();
            return name.contains("电话") || name.contains("联系人") || name.contains("闹钟") || name.contains("设置");
        } catch (Exception ignored) { }
        return false;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && v.trim().length() > 0) return v.trim();
        }
        return "";
    }
    private static String normalizeMode(String m) { return "strict"; }
    private static double positive(double v, double fallback) { return v > 0 ? v : fallback; }
    private static JSONObject put(JSONObject out, boolean ok, String result) { try { out.put("ok", ok); out.put("result", result); } catch (Exception ignored) { } return out; }
    private static String formatLocal(long ms) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(ms)); }
    public static String remainText(long ms) {
        long sec = Math.max(0, ms / 1000); long h = sec / 3600; long m = (sec % 3600) / 60; long s = sec % 60;
        if (h > 0) return h + " 小时 " + m + " 分钟";
        if (m > 0) return m + " 分钟 " + s + " 秒";
        return s + " 秒";
    }
}
