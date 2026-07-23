package dev.linjian.peek;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ScreenshotService extends AccessibilityService {
    private static volatile ScreenshotService instance;
    private static volatile String currentPackage = "";
    private static volatile String screenText = "";
    private static volatile String screenNodesJson = "[]";
    private final Executor executor = Executors.newSingleThreadExecutor();
    private Handler watchdog;
    private HandlerThread backgroundPollThread;
    private Handler backgroundPollHandler;

    public static ScreenshotService getInstance() { return instance; }
    public static boolean ready() { return instance != null; }
    public static String currentPackage() { return currentPackage == null ? "" : currentPackage; }
    public static String screenText() { return screenText == null ? "" : screenText; }
    public static String screenNodesJson() { return screenNodesJson == null ? "[]" : screenNodesJson; }

    private final Runnable watchdogTick = new Runnable() {
        @Override public void run() {
            try {
                SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
                String url = prefs.getString(AppPrefs.KEY_SERVER, "");
                String tk = prefs.getString(AppPrefs.KEY_TOKEN, "");
                boolean userStopped = prefs.getBoolean("user_stopped", false);
                if (!CompanionService.isRunning() && !userStopped && !url.isEmpty() && !tk.isEmpty()) {
                    DebugState.append(ScreenshotService.this, "看门狗：尝试重启前台服务");
                    Intent i = new Intent(ScreenshotService.this, CompanionService.class);
                    i.putExtra("server_url", url);
                    i.putExtra("token", tk);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
                }
            } catch (Exception e) {
                DebugState.append(ScreenshotService.this, "看门狗异常：" + shortMsg(e));
            }
            if (watchdog != null) watchdog.postDelayed(this, 60000);
        }
    };

    private final Runnable backgroundPollTick = new Runnable() {
        @Override public void run() {
            try {
                SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
                String url = normalizeUrl(prefs.getString(AppPrefs.KEY_SERVER, ""));
                String tk = prefs.getString(AppPrefs.KEY_TOKEN, "");
                boolean userStopped = prefs.getBoolean("user_stopped", true);
                if (!userStopped && !url.isEmpty() && !tk.isEmpty()) {
                    String body = pollServerFromAccessibility(url, tk);
                    if (body != null && body.length() > 0) CompanionService.handleCommandBody(ScreenshotService.this, body, url, tk);
                }
            } catch (Exception e) {
                DebugState.append(ScreenshotService.this, "无障碍后台轮询异常：" + shortMsg(e));
            }
            if (backgroundPollHandler != null) backgroundPollHandler.postDelayed(this, Math.max(700, AppPrefs.interval(ScreenshotService.this)));
        }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        DebugState.append(this, "无障碍服务已连接：截图/读屏/节点坐标/应用门禁可用 v0.3.4.1");
        watchdog = new Handler(Looper.getMainLooper());
        watchdog.postDelayed(watchdogTick, 15000);
        startBackgroundPolling();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg != null) currentPackage = pkg.toString();
        int t = event.getEventType();
        if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || t == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || t == AccessibilityEvent.TYPE_VIEW_SCROLLED) updateScreenText();
        if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) AppGate.onForegroundPackage(this, pkg.toString());
    }
    @Override public void onInterrupt() { DebugState.append(this, "无障碍服务被中断"); }

    @Override public void onDestroy() {
        DebugState.append(this, "无障碍服务已断开");
        instance = null;
        if (watchdog != null) { watchdog.removeCallbacksAndMessages(null); watchdog = null; }
        if (backgroundPollHandler != null) { backgroundPollHandler.removeCallbacksAndMessages(null); backgroundPollHandler = null; }
        if (backgroundPollThread != null) { backgroundPollThread.quitSafely(); backgroundPollThread = null; }
        super.onDestroy();
    }

    private void startBackgroundPolling() {
        if (backgroundPollThread != null) return;
        backgroundPollThread = new HandlerThread("LinjianAccessibilityPoll");
        backgroundPollThread.start();
        backgroundPollHandler = new Handler(backgroundPollThread.getLooper());
        DebugState.append(this, "无障碍后台轮询已启动 v0.3.4.1");
        backgroundPollHandler.postDelayed(backgroundPollTick, 1000);
    }

    private String pollServerFromAccessibility(String serverUrl, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(serverUrl + "/api/poll?device_id=" + java.net.URLEncoder.encode(AppPrefs.device(this), "UTF-8")).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Auth-Token", token);
        try {
            int code = conn.getResponseCode();
            String body = readBody(conn, code);
            if (code == 200) {
                if (body.contains("\"command\": null") || body.contains("\"command\":null")) return "";
                DebugState.append(this, "无障碍后台轮询：收到命令包");
                return body;
            } else {
                DebugState.append(this, "无障碍后台轮询失败：HTTP " + code + " " + clip(body));
            }
            return "";
        } finally { conn.disconnect(); }
    }

    public void refreshScreenModel() { updateScreenText(); }

    private void updateScreenText() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            StringBuilder sb = new StringBuilder();
            JSONArray nodes = new JSONArray();
            collect(root, sb, nodes, 0, 0);
            screenText = sb.length() > 2400 ? sb.substring(0, 2400) : sb.toString();
            screenNodesJson = nodes.toString();
            if (root != null) root.recycle();
        } catch (Exception ignored) { }
    }

    private int collect(AccessibilityNodeInfo node, StringBuilder sb, JSONArray nodes, int depth, int count) {
        if (node == null || count > 140 || depth > 14) return count;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String value = text != null && text.length() > 0 ? text.toString() : (desc != null && desc.length() > 0 ? desc.toString() : "");
        if (value.length() > 0) {
            if (sb.length() < 2600) sb.append(value).append(" | ");
            try {
                Rect r = new Rect(); node.getBoundsInScreen(r);
                JSONObject o = new JSONObject();
                o.put("index", nodes.length() + 1);
                o.put("text", value.length() > 160 ? value.substring(0, 160) : value);
                o.put("class", String.valueOf(node.getClassName()));
                o.put("clickable", node.isClickable());
                o.put("editable", node.isEditable());
                o.put("enabled", node.isEnabled());
                o.put("focused", node.isFocused());
                o.put("left", r.left); o.put("top", r.top); o.put("right", r.right); o.put("bottom", r.bottom);
                o.put("center_x", (r.left + r.right) / 2); o.put("center_y", (r.top + r.bottom) / 2);
                nodes.put(o);
            } catch (Exception ignored) { }
            count++;
        }
        for (int i = 0; i < node.getChildCount(); i++) count = collect(node.getChild(i), sb, nodes, depth + 1, count);
        return count;
    }

    public String getScreenNodesJsonNow() { refreshScreenModel(); return screenNodesJson(); }

    public JSONObject tapText(String query, String match, int index) {
        JSONObject out = new JSONObject();
        try {
            if (query == null || query.trim().isEmpty()) { out.put("ok", false); out.put("result", "target_text_empty"); return out; }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            TextHit hit = new TextHit(); hit.targetIndex = Math.max(1, index); hit.match = (match == null || match.length() == 0) ? "contains" : match; hit.query = query.trim();
            findTextNode(root, hit);
            if (hit.node == null) { out.put("ok", false); out.put("result", "text_not_found:" + query); out.put("nodes", getScreenNodesJsonNow()); if (root != null) root.recycle(); return out; }
            Rect r = new Rect(); hit.node.getBoundsInScreen(r);
            AccessibilityNodeInfo clickable = findClickableSelfOrParent(hit.node);
            boolean clicked = false;
            String mode = "tap_center";
            if (clickable != null) { clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK); mode = "accessibility_click"; }
            if (!clicked) clicked = doTap((r.left + r.right) / 2f, (r.top + r.bottom) / 2f);
            out.put("ok", clicked);
            out.put("result", clicked ? ("tap_text:" + hit.text) : "tap_text_failed");
            out.put("matched_text", hit.text);
            out.put("mode", mode);
            out.put("left", r.left); out.put("top", r.top); out.put("right", r.right); out.put("bottom", r.bottom);
            out.put("center_x", (r.left + r.right) / 2); out.put("center_y", (r.top + r.bottom) / 2);
            if (root != null) root.recycle();
        } catch (Exception e) { try { out.put("ok", false); out.put("result", shortMsg(e)); } catch (Exception ignored) { } }
        return out;
    }

    private static class TextHit { String query=""; String match="contains"; int targetIndex=1; int seen=0; AccessibilityNodeInfo node; String text=""; }

    private void findTextNode(AccessibilityNodeInfo node, TextHit hit) {
        if (node == null || hit.node != null) return;
        String value = nodeText(node);
        if (value.length() > 0 && textMatches(value, hit.query, hit.match)) {
            hit.seen++;
            if (hit.seen == hit.targetIndex) { hit.node = node; hit.text = value; return; }
        }
        for (int i = 0; i < node.getChildCount(); i++) findTextNode(node.getChild(i), hit);
    }

    private String nodeText(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) return text.toString();
        if (desc != null && desc.length() > 0) return desc.toString();
        return "";
    }

    private boolean textMatches(String value, String query, String match) {
        String v = value == null ? "" : value;
        String q = query == null ? "" : query;
        String m = match == null ? "contains" : match.toLowerCase();
        if ("exact".equals(m)) return v.equals(q);
        if ("starts".equals(m) || "prefix".equals(m)) return v.startsWith(q);
        return v.contains(q);
    }

    private AccessibilityNodeInfo findClickableSelfOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; cur != null && i < 6; i++) {
            if (cur.isClickable() && cur.isEnabled()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    public JSONObject inputText(String text, boolean append) {
        JSONObject out = new JSONObject();
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            AccessibilityNodeInfo target = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null) target = findEditable(root);
            if (target == null) { out.put("ok", false); out.put("result", "editable_node_not_found"); if (root != null) root.recycle(); return out; }
            String value = text == null ? "" : text;
            if (append) {
                CharSequence existing = target.getText();
                value = (existing == null ? "" : existing.toString()) + value;
            }
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            String mode = "set_text";
            if (!ok) {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null) {
                    cb.setPrimaryClip(ClipData.newPlainText("掌心窗输入", value));
                    ok = target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                    mode = "clipboard_paste";
                }
            }
            out.put("ok", ok);
            out.put("result", ok ? ("input_text:" + mode) : "input_text_failed");
            out.put("length", value.length());
            if (root != null) root.recycle();
        } catch (Exception e) { try { out.put("ok", false); out.put("result", shortMsg(e)); } catch (Exception ignored) { } }
        return out;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public boolean doBack() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean doHome() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean doRecents() { return performGlobalAction(GLOBAL_ACTION_RECENTS); }

    public boolean doTap(float x, float y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 80);
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public boolean doSwipe(float x1, float y1, float x2, float y2, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, Math.max(80, durationMs));
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public void doScreenshot(String serverUrl, String token) {
        if (Build.VERSION.SDK_INT < 30) { DebugState.append(this, "截图失败：Android 版本低于 11"); return; }
        final String finalUrl = normalizeUrl(serverUrl);
        DebugState.append(this, "开始调用系统截图 API");
        takeScreenshot(Display.DEFAULT_DISPLAY, executor, new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                try {
                    DebugState.append(ScreenshotService.this, "系统截图成功，开始编码");
                    Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    if (hardwareBitmap == null) { DebugState.append(ScreenshotService.this, "截图失败：Bitmap 为空"); return; }
                    Bitmap bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    hardwareBitmap.recycle(); result.getHardwareBuffer().close();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); bitmap.recycle();
                    byte[] data = out.toByteArray();
                    DebugState.append(ScreenshotService.this, "截图编码完成：" + data.length + " bytes");
                    if (data.length > 100) uploadScreenshot(data, finalUrl, token); else DebugState.append(ScreenshotService.this, "上传取消：截图数据太小");
                } catch (Exception e) { DebugState.append(ScreenshotService.this, "截图处理异常：" + shortMsg(e)); }
            }
            @Override public void onFailure(int errorCode) { DebugState.append(ScreenshotService.this, "系统截图失败：errorCode=" + errorCode + "（可尝试关闭再开启无障碍）"); }
        });
    }

    private void uploadScreenshot(byte[] data, String serverUrl, String token) {
        try {
            DebugState.append(this, "开始上传截图到 /api/screenshot");
            HttpURLConnection conn = (HttpURLConnection) new URL(serverUrl + "/api/screenshot").openConnection();
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("X-Auth-Token", token);
            conn.setRequestProperty("Content-Type", "image/jpeg");
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
            OutputStream os = conn.getOutputStream(); os.write(data); os.flush(); os.close();
            int code = conn.getResponseCode(); String body = readBody(conn, code);
            if (code >= 200 && code < 300) DebugState.append(this, "上传成功：HTTP " + code + " " + clip(body));
            else DebugState.append(this, "上传失败：HTTP " + code + " " + clip(body));
            conn.disconnect();
        } catch (Exception e) { DebugState.append(this, "上传异常：" + shortMsg(e)); }
    }

    public static String normalizeUrl(String url) { if (url == null) return ""; url = url.trim(); while (url.endsWith("/")) url = url.substring(0, url.length() - 1); return url; }
    static String readBody(HttpURLConnection conn, int code) { try { InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream(); if (is == null) return ""; ByteArrayOutputStream bos = new ByteArrayOutputStream(); byte[] buf = new byte[1024]; int n; while ((n = is.read(buf)) > 0) bos.write(buf, 0, n); return new String(bos.toByteArray(), "UTF-8"); } catch (Exception e) { return ""; } }
    static String clip(String s) { if (s == null) return ""; s = s.replace('\n', ' ').replace('\r', ' '); return s.length() > 90 ? s.substring(0, 90) + "…" : s; }
    static String shortMsg(Exception e) { String msg = e.getClass().getSimpleName(); if (e.getMessage() != null) msg += ": " + e.getMessage(); return clip(msg); }
}
