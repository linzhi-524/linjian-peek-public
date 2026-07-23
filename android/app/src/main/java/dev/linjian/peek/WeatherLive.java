package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * 真实天气轻量层：
 * 1. App 端按“当前地区城市”查询 Open-Meteo；
 * 2. 结果缓存到本地，生活状态层和首页卡片都能读取；
 * 3. 查询失败不影响掌心窗主功能，只回退到天气备注/地区占位。
 */
public class WeatherLive {
    private static final String KEY_CITY = "weather_live_city";
    private static final String KEY_JSON = "weather_live_json";
    private static final String KEY_AT = "weather_live_at";
    private static final long DEFAULT_FRESH_MS = 45L * 60L * 1000L;

    public interface Callback { void onResult(JSONObject weather); }

    public static JSONObject cached(Context ctx, String city) {
        try {
            city = clean(city);
            if (city.isEmpty()) return null;
            SharedPreferences p = AppPrefs.get(ctx);
            String savedCity = p.getString(KEY_CITY, "");
            String raw = p.getString(KEY_JSON, "");
            if (raw == null || raw.isEmpty() || !city.equals(savedCity)) return null;
            JSONObject o = new JSONObject(raw);
            o.put("cache_age_ms", Math.max(0, System.currentTimeMillis() - p.getLong(KEY_AT, 0)));
            return o;
        } catch (Exception e) { return null; }
    }

    public static boolean isFresh(Context ctx, String city, long freshMs) {
        try {
            city = clean(city);
            if (city.isEmpty()) return false;
            SharedPreferences p = AppPrefs.get(ctx);
            return city.equals(p.getString(KEY_CITY, "")) && (System.currentTimeMillis() - p.getLong(KEY_AT, 0)) <= Math.max(60_000L, freshMs);
        } catch (Exception e) { return false; }
    }

    public static void refreshAsync(Context ctx, String city, Callback callback) {
        final Context app = ctx.getApplicationContext();
        final String chosenCity = clean(city);
        if (chosenCity.isEmpty()) {
            if (callback != null) callback.onResult(error("missing_city", chosenCity));
            return;
        }
        if (isFresh(app, chosenCity, DEFAULT_FRESH_MS)) {
            JSONObject cached = cached(app, chosenCity);
            if (callback != null && cached != null) callback.onResult(cached);
            return;
        }
        new Thread(() -> {
            JSONObject result;
            try {
                result = fetch(chosenCity);
                AppPrefs.get(app).edit()
                        .putString(KEY_CITY, chosenCity)
                        .putString(KEY_JSON, result.toString())
                        .putLong(KEY_AT, System.currentTimeMillis())
                        .apply();
                DebugState.append(app, "天气已刷新：" + chosenCity + " · " + result.optString("condition", ""));
            } catch (Exception e) {
                result = error(ScreenshotService.shortMsg(e), chosenCity);
                DebugState.append(app, "天气刷新失败：" + chosenCity + " · " + ScreenshotService.shortMsg(e));
            }
            if (callback != null) callback.onResult(result);
        }, "peek-weather-live").start();
    }

    private static JSONObject fetch(String city) throws Exception {
        String q = URLEncoder.encode(city, "UTF-8");
        String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" + q + "&count=1&language=zh&format=json";
        JSONObject geo = new JSONObject(get(geoUrl));
        JSONArray results = geo.optJSONArray("results");
        if (results == null || results.length() == 0) return error("city_not_found", city);
        JSONObject hit = results.getJSONObject(0);
        double lat = hit.optDouble("latitude", 0);
        double lon = hit.optDouble("longitude", 0);
        String forecastUrl = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=2",
                lat, lon);
        JSONObject data = new JSONObject(get(forecastUrl));
        JSONObject current = data.optJSONObject("current");
        JSONObject daily = data.optJSONObject("daily");
        int code = current == null ? -999 : current.optInt("weather_code", current.optInt("weathercode", -999));
        double temp = current == null ? 0 : current.optDouble("temperature_2m", current.optDouble("temperature", 0));
        double wind = current == null ? 0 : current.optDouble("wind_speed_10m", 0);
        double max = firstDouble(daily, "temperature_2m_max", temp);
        double min = firstDouble(daily, "temperature_2m_min", temp);
        int rain = (int) Math.round(firstDouble(daily, "precipitation_probability_max", 0));
        JSONObject out = new JSONObject();
        out.put("ok", true);
        out.put("source", "open-meteo");
        out.put("queried_city", city);
        out.put("resolved_city", hit.optString("name", city));
        out.put("admin1", hit.optString("admin1", ""));
        out.put("country", hit.optString("country", ""));
        out.put("latitude", lat);
        out.put("longitude", lon);
        out.put("temperature", Math.round(temp));
        out.put("temperature_raw", temp);
        out.put("weather_code", code);
        out.put("condition", weatherCodeText(code));
        out.put("wind_speed", Math.round(wind));
        out.put("temp_max", Math.round(max));
        out.put("temp_min", Math.round(min));
        out.put("precipitation_probability", rain);
        out.put("updated_at_ms", System.currentTimeMillis());
        out.put("summary", cardText(out, ""));
        out.put("advice", advice(out, "当前地区"));
        return out;
    }

    private static double firstDouble(JSONObject obj, String key, double fallback) {
        try {
            if (obj == null) return fallback;
            JSONArray arr = obj.optJSONArray(key);
            if (arr == null || arr.length() == 0) return fallback;
            return arr.optDouble(0, fallback);
        } catch (Exception e) { return fallback; }
    }

    private static JSONObject error(String error, String city) {
        JSONObject o = new JSONObject();
        try { o.put("ok", false); o.put("error", error); o.put("queried_city", city); } catch (Exception ignored) { }
        return o;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "LinjianPeek/0.3.4.1");
        try {
            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } finally { conn.disconnect(); }
    }

    public static String cardText(JSONObject weather, String name) {
        if (weather == null || !weather.optBoolean("ok")) return "天气\n待刷新";
        String city = weather.optString("resolved_city", weather.optString("queried_city", name));
        String condition = weather.optString("condition", "天气");
        int temp = weather.optInt("temperature", 0);
        return city + " · " + condition + "\n" + temp + "℃";
    }

    public static String advice(JSONObject weather, String name) {
        if (weather == null || !weather.optBoolean("ok")) return "出门建议：实时天气没查到，先按体感穿衣。";
        String n = (name == null || name.trim().isEmpty()) ? "当前地区" : name.trim();
        String condition = weather.optString("condition", "");
        int temp = weather.optInt("temperature", 0);
        int max = weather.optInt("temp_max", temp);
        int min = weather.optInt("temp_min", temp);
        int rain = weather.optInt("precipitation_probability", 0);
        String head = n + "现在" + condition + "，约 " + temp + "℃，今天 " + min + "~" + max + "℃。";
        if (rain >= 50 || condition.contains("雨") || condition.contains("雪")) return "出门建议：" + head + "伞拿上，别淋湿。";
        if (max >= 32) return "出门建议：" + head + "今天偏热，水杯和防晒都带着。";
        if (min <= 8 || Math.abs(max - min) >= 10) return "出门建议：" + head + "温差明显，外套别嘴硬。";
        if (condition.contains("雾") || condition.contains("霾")) return "出门建议：" + head + "能少在外面吹就少吹一会儿。";
        return "出门建议：" + head + "正常出门就好。";
    }

    public static String weatherCodeText(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "大部晴朗";
            case 2: return "多云";
            case 3: return "阴";
            case 45: return "雾";
            case 48: return "雾凇";
            case 51: return "小毛毛雨";
            case 53: return "毛毛雨";
            case 55: return "强毛毛雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 80: return "阵雨";
            case 81: return "中等阵雨";
            case 82: return "强阵雨";
            case 95: return "雷暴";
            case 96: return "雷暴伴冰雹";
            case 99: return "强雷暴伴冰雹";
            default: return "天气代码 " + code;
        }
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
