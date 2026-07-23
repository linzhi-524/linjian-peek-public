package dev.linjian.peek;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 轻量生活状态层：不截图，不读聊天内容，只上传设备状态。 */
public class LifeState {
    public static JSONObject collect(Context ctx) {
        JSONObject state = new JSONObject();
        try {
            long now = System.currentTimeMillis();
            Intent battery = ctx.registerReceiver((BroadcastReceiver) null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int batteryPercent = -1;
            boolean charging = false;
            String chargingType = "unknown";
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) batteryPercent = Math.round(level * 100f / scale);
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                chargingType = pluggedToString(plugged);
                state.put("battery_status", batteryStatusToString(status));
            }

            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            boolean screenOn = pm != null && (Build.VERSION.SDK_INT >= 20 ? pm.isInteractive() : pm.isScreenOn());
            String currentPackage = ScreenshotService.currentPackage();
            String currentApp = appLabel(ctx, currentPackage);
            boolean usageReady = hasUsagePermission(ctx);
            UsageSummary usage = usageReady ? readUsage(ctx, now) : new UsageSummary();
            SharedPreferencesCompat prefs = new SharedPreferencesCompat(ctx);

            state.put("device_id", AppPrefs.device(ctx));
            state.put("life_state_version", "0.3.4.1");
            state.put("local_time", formatLocal(now, "HH:mm"));
            state.put("local_date", formatLocal(now, "yyyy-MM-dd"));
            state.put("timezone", TimeZone.getDefault().getID());
            state.put("updated_at_ms", now);
            state.put("updated_at_local", formatLocal(now, "yyyy-MM-dd HH:mm:ss"));
            state.put("battery_percent", batteryPercent);
            state.put("charging", charging);
            state.put("charging_type", chargingType);
            state.put("network_type", networkType(ctx));
            state.put("screen_on", screenOn);
            state.put("current_package", currentPackage);
            state.put("current_app", currentApp);
            state.put("accessibility_ready", ScreenshotService.ready());
            state.put("usage_permission_ready", usageReady);
            state.put("screen_time_today_minutes", usage.screenTimeMinutes);
            state.put("unlock_count_today", usage.unlockCount);
            state.put("last_unlock_at", usage.lastUnlockAt <= 0 ? "" : formatIsoLocal(usage.lastUnlockAt));
            state.put("top_apps_today", usage.topApps);
            state.put("city", prefs.city());
            state.put("weather_note", prefs.weatherNote());
            JSONObject weatherState = WeatherState.collect(ctx);
            state.put("weather_state", weatherState);
            state.put("weather_locations", weatherState.optJSONArray("locations"));
            state.put("current_weather_location", weatherState.optJSONObject("current"));
            state.put("screen_text", ScreenshotService.screenText());
            state.put("active_reminders", ActiveReminder.config(ctx));
            state.put("home_mode", HomeMode.config(ctx));
            state.put("known_apps", AppPrefs.knownAppsJson(ctx));
            state.put("app_gate", AppGate.config(ctx));
            state.put("cycle_state", CycleState.collect(ctx));
            state.put("summary", makeSummary(batteryPercent, charging, currentApp, usage.screenTimeMinutes, usage.unlockCount, usageReady));
        } catch (Exception e) {
            try { state.put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { }
        }
        return state;
    }

    public static String pretty(Context ctx) {
        try {
            JSONObject s = collect(ctx);
            StringBuilder sb = new StringBuilder();
            sb.append("生活状态层 v0.3.4.1\n");
            sb.append("时间：").append(s.optString("local_time", "-")).append("  ").append(s.optString("local_date", "-")).append("\n");
            sb.append("电量：").append(s.optInt("battery_percent", -1)).append("%  ").append(s.optBoolean("charging") ? "充电中" : "未充电").append("\n");
            sb.append("网络：").append(s.optString("network_type", "-")).append("  屏幕：").append(s.optBoolean("screen_on") ? "亮" : "灭").append("\n");
            sb.append("当前：").append(s.optString("current_app", "-")).append("\n");
            sb.append("屏幕时间：").append(s.optInt("screen_time_today_minutes", 0)).append(" 分钟  解锁：").append(s.optInt("unlock_count_today", 0)).append(" 次\n");
            sb.append("使用权限：").append(s.optBoolean("usage_permission_ready") ? "已开启" : "未开启，屏幕时间/解锁次数会为空").append("\n");
            String city = s.optString("city", "");
            String weather = s.optString("weather_note", "");
            if (!city.isEmpty() || !weather.isEmpty()) sb.append("城市/天气：").append(city).append(city.isEmpty() || weather.isEmpty() ? "" : " · ").append(weather).append("\n");
            sb.append("\n").append(WeatherState.pretty(ctx));
            sb.append("\n").append(s.optString("summary", ""));
            sb.append("\n\n").append(ActiveReminder.pretty(ctx));
            sb.append("\n\n").append(HomeMode.pretty(ctx));
            sb.append("\n\n").append(AppGate.pretty(ctx));
            sb.append("\n\n可打开 App：\n").append(AppPrefs.knownAppsText(ctx));
            sb.append("\n\n").append(CycleState.pretty(ctx));
            return sb.toString();
        } catch (Exception e) { return "生活状态读取失败：" + ScreenshotService.shortMsg(e); }
    }

    private static String makeSummary(int battery, boolean charging, String app, int screenMinutes, int unlocks, boolean usageReady) {
        StringBuilder sb = new StringBuilder();
        sb.append("轻量查岗：");
        if (app != null && app.length() > 0) sb.append("当前在 ").append(app).append("；");
        if (battery >= 0) sb.append("电量 ").append(battery).append("%").append(charging ? "，正在充电；" : "，未充电；");
        if (usageReady) sb.append("今日屏幕约 ").append(screenMinutes).append(" 分钟，解锁 ").append(unlocks).append(" 次。");
        else sb.append("使用情况权限未开，暂时看不到今日屏幕时间。");
        return sb.toString();
    }

    private static String pluggedToString(int plugged) {
        if (plugged == BatteryManager.BATTERY_PLUGGED_USB) return "usb";
        if (plugged == BatteryManager.BATTERY_PLUGGED_AC) return "ac";
        if (Build.VERSION.SDK_INT >= 17 && plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) return "wireless";
        return "none";
    }

    private static String batteryStatusToString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "not_charging";
            default: return "unknown";
        }
    }

    private static String networkType(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "unknown";
            if (Build.VERSION.SDK_INT >= 23) {
                Network n = cm.getActiveNetwork();
                if (n == null) return "none";
                NetworkCapabilities cap = cm.getNetworkCapabilities(n);
                if (cap == null) return "unknown";
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
                return "other";
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected()) return "none";
                return info.getTypeName().toLowerCase(Locale.US);
            }
        } catch (Exception e) { return "unknown"; }
    }

    public static boolean hasUsagePermission(Context ctx) {
        try {
            AppOpsManager appOps = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    private static UsageSummary readUsage(Context ctx, long now) {
        UsageSummary summary = new UsageSummary();
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return summary;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            long start = cal.getTimeInMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now);
            long total = 0;
            List<AppUse> apps = new ArrayList<>();
            if (stats != null) {
                for (UsageStats st : stats) {
                    long fg = st.getTotalTimeInForeground();
                    if (fg <= 0) continue;
                    total += fg;
                    apps.add(new AppUse(appLabel(ctx, st.getPackageName()), st.getPackageName(), fg));
                }
            }
            Collections.sort(apps, new Comparator<AppUse>() { @Override public int compare(AppUse a, AppUse b) { return Long.compare(b.ms, a.ms); } });
            JSONArray arr = new JSONArray();
            for (int i = 0; i < Math.min(5, apps.size()); i++) {
                AppUse u = apps.get(i);
                JSONObject o = new JSONObject();
                o.put("app", u.label); o.put("package", u.pkg); o.put("minutes", Math.round(u.ms / 60000.0));
                arr.put(o);
            }
            summary.screenTimeMinutes = (int) Math.round(total / 60000.0);
            summary.topApps = arr;

            UsageEvents events = usm.queryEvents(start, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                boolean isUnlock = false;
                if (Build.VERSION.SDK_INT >= 28) isUnlock = type == UsageEvents.Event.KEYGUARD_HIDDEN || type == UsageEvents.Event.SCREEN_INTERACTIVE;
                else isUnlock = type == UsageEvents.Event.USER_INTERACTION;
                if (isUnlock) { summary.unlockCount++; summary.lastUnlockAt = event.getTimeStamp(); }
            }
        } catch (Exception ignored) { }
        return summary;
    }

    private static String appLabel(Context ctx, String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return "";
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? pkg : label.toString();
        } catch (Exception e) { return pkg; }
    }

    private static String formatLocal(long ms, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(ms));
    }

    private static String formatIsoLocal(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(ms));
    }

    private static class UsageSummary {
        int screenTimeMinutes = 0;
        int unlockCount = 0;
        long lastUnlockAt = 0;
        JSONArray topApps = new JSONArray();
    }

    private static class AppUse {
        final String label; final String pkg; final long ms;
        AppUse(String label, String pkg, long ms) { this.label = label; this.pkg = pkg; this.ms = ms; }
    }

    private static class SharedPreferencesCompat {
        private final Context ctx;
        SharedPreferencesCompat(Context ctx) { this.ctx = ctx; }
        String city() { return AppPrefs.get(ctx).getString(AppPrefs.KEY_CITY, ""); }
        String weatherNote() { return AppPrefs.get(ctx).getString(AppPrefs.KEY_WEATHER_NOTE, ""); }
    }
}
