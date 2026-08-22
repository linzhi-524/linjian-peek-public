package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AppPrefs {
    public static final String PREFS = "linjian_peek";
    public static final String APP_VERSION_NAME = "0.3.7";
    public static final int APP_VERSION_CODE = 30700;
    public static final String KEY_SERVER = "server_url";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_DEVICE = "device_id";
    public static final String KEY_INTERVAL = "poll_interval_ms";
    public static final int DEFAULT_POLL_INTERVAL_MS = 3000;
    public static final int MIN_POLL_INTERVAL_MS = 2500;
    public static final int MAX_POLL_INTERVAL_MS = 15000;
    public static final int STATE_UPLOAD_INTERVAL_MS = 10000;
    public static final int ACCESSIBILITY_FALLBACK_INTERVAL_MS = 12000;
    public static final String KEY_CITY = "life_city";
    public static final String KEY_WEATHER_NOTE = "life_weather_note";
    public static final String KEY_WEATHER_LOCATIONS = "weather_locations_lines";
    public static final String KEY_THEME = "ui_theme";
    public static final String KEY_ACTIVE_REMINDERS = "active_reminders_enabled";
    public static final String KEY_RULE_BATTERY = "rule_battery_enabled";
    public static final String KEY_BATTERY_THRESHOLD = "rule_battery_threshold";
    public static final String KEY_RULE_SCREEN = "rule_screen_enabled";
    public static final String KEY_SCREEN_THRESHOLD_MIN = "rule_screen_threshold_min";
    public static final String KEY_RULE_WATER = "rule_water_enabled";
    public static final String KEY_WATER_INTERVAL_MIN = "rule_water_interval_min";
    public static final String KEY_RULE_REST = "rule_rest_enabled";
    public static final String KEY_REST_INTERVAL_MIN = "rule_rest_interval_min";
    public static final String KEY_CYCLE_ENABLED = "cycle_enabled";
    public static final String KEY_LAST_PERIOD_START = "cycle_last_period_start";
    public static final String KEY_CYCLE_LENGTH = "cycle_length_days";
    public static final String KEY_PERIOD_LENGTH = "cycle_period_length_days";
    public static final String KEY_CYCLE_REMIND_BEFORE = "cycle_remind_before_days";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_COMPANION_NAME = "companion_name";
    public static final String KEY_TARGET_APPS = "target_apps_lines";
    public static final String KEY_JOURNEY_ENABLED = "today_journey_enabled";
    public static final String KEY_SHOW_COMPANION_ACTIONS = "show_companion_actions";
    public static final String KEY_COMPANION_FIRST_DAY = "companion_first_day_ms";
    public static final String DEFAULT_USER_NAME = "宝宝";
    public static final String DEFAULT_COMPANION_NAME = "陪伴者";
    // 仅用于从旧公开版平滑迁移，新的 UI 和业务逻辑不再写入这两个键。
    public static final String KEY_USER_NICKNAME = "user_nickname";
    public static final String KEY_PARTNER_NICKNAME = "partner_nickname";

    public static final String KEY_FOREGROUND_POPUP = "foreground_popup_enabled";
    public static final String KEY_CUSTOM_APPS = "custom_apps_lines";
    public static final String KEY_HOME_MODE_ENABLED = "home_mode_enabled";
    public static final String KEY_HOME_MODE_FORCE = "home_mode_force";
    public static final String KEY_HOME_WATCH_PACKAGES = "home_mode_watch_packages";
    public static final String KEY_HOME_THRESHOLD_MIN = "home_mode_threshold_min";
    public static final String KEY_HOME_COOLDOWN_MIN = "home_mode_cooldown_min";
    public static final String KEY_HOME_TARGET_PACKAGE = "home_mode_target_package";
    public static final String DEFAULT_HOME_TARGET_PACKAGE = "";

    public static SharedPreferences get(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** User-supplied public deployment address; no built-in private endpoint. */
    public static String cleanServer(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.equalsIgnoreCase("null")) return "";
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static boolean migrateLegacyConfig(Context ctx) { return false; }
    public static String server(Context ctx) { return cleanServer(get(ctx).getString(KEY_SERVER, "")); }
    public static String token(Context ctx) { return get(ctx).getString(KEY_TOKEN, ""); }
    public static String device(Context ctx) { return get(ctx).getString(KEY_DEVICE, "android-phone"); }
    public static int interval(Context ctx) {
        int saved = get(ctx).getInt(KEY_INTERVAL, DEFAULT_POLL_INTERVAL_MS);
        if (saved < MIN_POLL_INTERVAL_MS) return DEFAULT_POLL_INTERVAL_MS;
        if (saved > MAX_POLL_INTERVAL_MS) return MAX_POLL_INTERVAL_MS;
        return saved;
    }

    /** 把旧公开版称呼和回家模式观察列表迁移到通用模板配置。 */
    public static boolean migrateTemplateConfig(Context ctx) {
        SharedPreferences prefs = get(ctx);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_USER_NAME)) {
            editor.putString(KEY_USER_NAME, safeName(prefs.getString(KEY_USER_NICKNAME, ""), DEFAULT_USER_NAME));
            changed = true;
        }
        if (!prefs.contains(KEY_COMPANION_NAME)) {
            editor.putString(KEY_COMPANION_NAME, safeName(prefs.getString(KEY_PARTNER_NICKNAME, ""), DEFAULT_COMPANION_NAME));
            changed = true;
        }
        if (!prefs.contains(KEY_TARGET_APPS)) {
            StringBuilder migrated = new StringBuilder();
            String oldPackages = prefs.getString(KEY_HOME_WATCH_PACKAGES, "");
            for (String raw : oldPackages.split("[,\\n]")) {
                String pkg = raw == null ? "" : raw.trim();
                if (!isPackageLike(pkg)) continue;
                String label = pkg;
                for (Map.Entry<String, String> app : allApps(ctx).entrySet()) {
                    if (pkg.equals(app.getValue())) { label = app.getKey(); break; }
                }
                migrated.append(label).append("|").append(pkg).append("\n");
            }
            editor.putString(KEY_TARGET_APPS, migrated.toString());
            changed = true;
        }
        if (changed) editor.apply();
        return changed;
    }

    public static String userName(Context ctx) {
        SharedPreferences prefs = get(ctx);
        String v = prefs.getString(KEY_USER_NAME, prefs.getString(KEY_USER_NICKNAME, ""));
        return safeName(v, DEFAULT_USER_NAME);
    }
    public static String companionName(Context ctx) {
        SharedPreferences prefs = get(ctx);
        String v = prefs.getString(KEY_COMPANION_NAME, prefs.getString(KEY_PARTNER_NICKNAME, ""));
        return safeName(v, DEFAULT_COMPANION_NAME);
    }
    /** 兼容旧公开版调用；新代码统一使用 companionName。 */
    public static String partnerName(Context ctx) {
        return companionName(ctx);
    }

    private static String safeName(String raw, String fallback) {
        return raw == null || raw.trim().isEmpty() ? fallback : raw.trim();
    }

    public static LinkedHashMap<String, String> targetApps(Context ctx) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        String raw = get(ctx).getString(KEY_TARGET_APPS, "");
        for (String line : raw.split("\\n")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()) continue;
            String label;
            String pkg;
            if (value.contains("|")) {
                String[] parts = value.split("\\|", 2);
                label = parts[0].trim();
                pkg = parts.length > 1 ? parts[1].trim() : "";
            } else {
                label = value;
                pkg = value;
            }
            if (!isPackageLike(pkg)) continue;
            if (label.isEmpty()) label = pkg;
            apps.put(label, pkg);
        }
        return apps;
    }

    public static String targetAppsText(Context ctx) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> app : targetApps(ctx).entrySet()) {
            out.append(app.getKey()).append("|").append(app.getValue()).append("\n");
        }
        return out.toString().trim();
    }

    public static String normalizeTargetApps(String raw) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        String source = raw == null ? "" : raw.replace(',', '\n');
        for (String line : source.split("\\n")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()) continue;
            String[] parts = value.split("\\|", 2);
            String label = parts[0].trim();
            String pkg = parts.length > 1 ? parts[1].trim() : label;
            if (!isPackageLike(pkg)) continue;
            apps.put(label.isEmpty() ? pkg : label, pkg);
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> app : apps.entrySet()) out.append(app.getKey()).append("|").append(app.getValue()).append("\n");
        return out.toString();
    }

    public static boolean isTargetPackage(Context ctx, String packageName) {
        String pkg = packageName == null ? "" : packageName.trim();
        if (pkg.isEmpty()) return false;
        for (String target : targetApps(ctx).values()) if (pkg.equals(target)) return true;
        return false;
    }

    public static String homeTargetPackage(Context ctx) {
        String raw = get(ctx).getString(KEY_HOME_TARGET_PACKAGE, DEFAULT_HOME_TARGET_PACKAGE);
        if (raw == null || raw.trim().isEmpty()) {
            for (String pkg : targetApps(ctx).values()) return pkg;
            return DEFAULT_HOME_TARGET_PACKAGE;
        }
        String resolved = packageForApp(ctx, raw.trim());
        return (resolved == null || resolved.trim().isEmpty()) ? raw.trim() : resolved.trim();
    }

    public static String homeTargetLabel(Context ctx) {
        String target = homeTargetPackage(ctx);
        if (target.isEmpty()) return "未设置";
        for (Map.Entry<String, String> e : targetApps(ctx).entrySet()) {
            if (target.equals(e.getValue())) return e.getKey();
        }
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            if (target.equals(e.getValue())) return e.getKey();
        }
        return target;
    }

    public static String returnButtonText(Context ctx) {
        return "回到" + companionName(ctx) + "这里";
    }

    public static String seeButtonText(Context ctx) {
        return "给" + companionName(ctx) + "看一眼";
    }

    public static String saveHomeTarget(Context ctx, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) return "";
        String pkg = packageForApp(ctx, v);
        if (pkg != null && pkg.trim().length() > 0) return pkg.trim();
        return v;
    }


    public static Map<String, String> defaultApps() {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        apps.put("小红书", "com.xingin.xhs");
        apps.put("微信", "com.tencent.mm");
        apps.put("QQ", "com.tencent.mobileqq");
        apps.put("抖音", "com.ss.android.ugc.aweme");
        apps.put("微博", "com.sina.weibo");
        apps.put("X", "com.twitter.android");
        return apps;
    }

    public static Map<String, String> allApps(Context ctx) {
        LinkedHashMap<String, String> apps = new LinkedHashMap<>(defaultApps());
        String custom = get(ctx).getString(KEY_CUSTOM_APPS, "");
        String[] lines = custom.split("\\n");
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty() || !s.contains("|")) continue;
            String[] parts = s.split("\\|", 2);
            String alias = parts[0].trim();
            String pkg = parts.length > 1 ? parts[1].trim() : "";
            if (!alias.isEmpty() && isPackageLike(pkg)) apps.put(alias, pkg);
        }
        return apps;
    }

    public static void saveCustomApp(Context ctx, String alias, String pkg) {
        alias = alias == null ? "" : alias.trim();
        pkg = pkg == null ? "" : pkg.trim();
        if (alias.isEmpty() || !isPackageLike(pkg)) return;
        LinkedHashMap<String, String> custom = new LinkedHashMap<>();
        String old = get(ctx).getString(KEY_CUSTOM_APPS, "");
        for (String line : old.split("\\n")) {
            if (line == null || !line.contains("|")) continue;
            String[] parts = line.trim().split("\\|", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && isPackageLike(parts[1].trim())) custom.put(parts[0].trim(), parts[1].trim());
        }
        custom.put(alias, pkg);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : custom.entrySet()) sb.append(e.getKey()).append("|").append(e.getValue()).append("\n");
        get(ctx).edit().putString(KEY_CUSTOM_APPS, sb.toString()).putString("pkg_" + alias.toLowerCase(Locale.US), pkg).apply();
    }

    public static String knownAppsText(Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            sb.append(e.getKey()).append("  →  ").append(e.getValue().isEmpty() ? "未设置" : e.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    public static JSONObject knownAppsJson(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String> e : allApps(ctx).entrySet()) o.put(e.getKey(), e.getValue());
        } catch (Exception ignored) { }
        return o;
    }

    public static String packageForApp(Context ctx, String app) {
        String raw = app == null ? "" : app.trim();
        if (isPackageLike(raw)) return raw;
        String key = raw.toLowerCase(Locale.US);
        String def;
        switch (key) {
            case "xiaohongshu": case "xhs": case "小红书": def = "com.xingin.xhs"; break;
            case "wechat": case "微信": def = "com.tencent.mm"; break;
            case "qq": def = "com.tencent.mobileqq"; break;
            case "douyin": case "抖音": def = "com.ss.android.ugc.aweme"; break;
            case "weibo": case "微博": def = "com.sina.weibo"; break;
            case "x": case "twitter": def = "com.twitter.android"; break;
            default: def = "";
        }
        String custom = get(ctx).getString("pkg_" + key, def);
        if (custom != null && custom.trim().length() > 0) return custom.trim();
        for (Map.Entry<String, String> e : allApps(ctx).entrySet()) {
            if (e.getKey().equalsIgnoreCase(raw)) return e.getValue();
        }
        return def;
    }

    public static boolean isPackageLike(String value) {
        if (value == null) return false;
        String s = value.trim();
        return s.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }
}
