package dev.linjian.peek;

import android.content.Context;
import android.content.SharedPreferences;

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
import java.util.HashSet;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** 守护日历：纪念日、节日、倒数日、提前三天横幅提醒，并接入生活状态层。 */
public class CalendarState {
    public static final String VERSION = "0.3.7";
    public static final String KEY_EVENTS = "guard_calendar_events_json";
    public static final String THEME_COLOR = "#B8A8D8";
    public static final int DEFAULT_REMIND_DAYS = 3;

    private static final String TYPE_SOLAR = "solar";
    private static final String TYPE_LUNAR = "lunar";
    private static final String REPEAT_YEARLY = "yearly";
    private static final String REPEAT_NONE = "none";

    // 农历数据：1900-2100。算法只用于把农历节日/纪念日换算成当年阳历日期。
    private static final int[] LUNAR_INFO = new int[]{
            0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
            0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
            0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
            0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
            0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
            0x06ca0,0x0b550,0x15355,0x04da0,0x0a5d0,0x14573,0x052d0,0x0a9a8,0x0e950,0x06aa0,
            0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
            0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,
            0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
            0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0,
            0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
            0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
            0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
            0x05aa0,0x076a3,0x096d0,0x04bd7,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
            0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0,
            0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06b20,0x1a6c4,0x0aae0,
            0x0a2e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,
            0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,
            0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160,
            0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252,
            0x0d520
    };

    public static JSONObject collect(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            List<Occurrence> occ = occurrences(ctx, false);
            JSONArray nearest = new JSONArray();
            JSONArray upcoming = new JSONArray();
            JSONArray banners = new JSONArray();
            for (Occurrence x : occ) {
                JSONObject item = x.toJson();
                if (nearest.length() < 3) nearest.put(item);
                if (upcoming.length() < 12) upcoming.put(item);
                if (x.bannerEnabled && x.daysLeft >= 0 && x.daysLeft <= Math.max(0, x.remindDays)) banners.put(item);
            }
            JSONArray customEvents = events(ctx);
            o.put("enabled", true);
            o.put("version", VERSION);
            o.put("theme_color", THEME_COLOR);
            o.put("default_remind_days_before", DEFAULT_REMIND_DAYS);
            o.put("event_count", customEvents.length());
            o.put("custom_event_count", customEvents.length());
            o.put("nearest", nearest);
            o.put("upcoming", upcoming);
            o.put("active_banners", banners);
            o.put("custom_events", customEvents);
            o.put("events", customEvents);
            o.put("summary", summaryLine(ctx));
        } catch (Exception e) {
            try { o.put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { }
        }
        return o;
    }


    public static JSONArray upcomingOccurrences(Context ctx, int limit) {
        JSONArray arr = new JSONArray();
        try {
            List<Occurrence> list = occurrences(ctx, false);
            int max = limit <= 0 ? list.size() : Math.min(limit, list.size());
            for (int i = 0; i < max; i++) arr.put(list.get(i).toJson());
        } catch (Exception ignored) { }
        return arr;
    }

    /** 返回指定月份真正应显示的事件，包含已过去的日期，供月历标记和详情使用。 */
    public static JSONArray occurrencesForMonth(Context ctx, int year, int monthIndex) {
        JSONArray out = new JSONArray();
        try {
            ArrayList<JSONObject> source = new ArrayList<>();
            JSONArray custom = events(ctx);
            for (int i = 0; i < custom.length(); i++) { JSONObject e = custom.optJSONObject(i); if (e != null) source.add(e); }
            source.addAll(builtinEvents());
            Calendar today = midnight(Calendar.getInstance());
            for (JSONObject e : source) {
                boolean builtin = e.optString("id", "").startsWith("builtin_");
                Calendar date = dateInYear(e, year);
                if (date == null || date.get(Calendar.MONTH) != monthIndex) continue;
                Occurrence o = occurrenceFrom(e, date, daysBetween(today, date), builtin);
                if (o != null) out.put(o.toJson());
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static String summaryLine(Context ctx) {
        try {
            List<Occurrence> list = occurrences(ctx, false);
            if (list.isEmpty()) return "守护日历 · 暂无临近日子";
            Occurrence x = list.get(0);
            return "守护日历 · " + x.title + " · " + daysText(x.daysLeft);
        } catch (Exception e) { return "守护日历 · 读取中"; }
    }

    public static String detailText(Context ctx) {
        try {
            StringBuilder sb = new StringBuilder();
            JSONObject state = collect(ctx);
            JSONArray banners = state.optJSONArray("active_banners");
            if (banners != null && banners.length() > 0) {
                sb.append("横幅提醒：\n");
                for (int i = 0; i < banners.length(); i++) sb.append("· ").append(banners.optJSONObject(i).optString("banner_text", "")).append("\n");
                sb.append("\n");
            }
            List<Occurrence> list = occurrences(ctx, false);
            if (list.isEmpty()) sb.append("还没有守护日子。可以添加七夕、生日、绑定日、考试或项目节点。\n");
            else {
                sb.append("最近日子：\n");
                for (int i = 0; i < Math.min(5, list.size()); i++) {
                    Occurrence x = list.get(i);
                    sb.append("· ").append(x.title).append("  ").append(formatDate(x.date)).append("  ").append(daysText(x.daysLeft));
                    if (x.lunarLabel.length() > 0) sb.append(" · ").append(x.lunarLabel);
                    if (x.group.length() > 0) sb.append(" · ").append(groupLabel(x.group));
                    sb.append("\n");
                }
            }
            sb.append("\n提醒规则：默认提前 3 天到当天显示横幅；无特殊日子时不显示。\n");
            sb.append("输入提示：阳历可填 2026-08-23 或 08-23；农历可填 07-07。勾选重复后每年滚动。\n");
            return sb.toString().trim();
        } catch (Exception e) { return "守护日历读取失败：" + ScreenshotService.shortMsg(e); }
    }

    public static String pretty(Context ctx) {
        try {
            JSONObject state = collect(ctx);
            StringBuilder sb = new StringBuilder();
            JSONArray banners = state.optJSONArray("active_banners");
            if (banners != null && banners.length() > 0) {
                sb.append("守护日历提醒：");
                sb.append(banners.optJSONObject(0).optString("banner_text", ""));
            } else {
                sb.append(summaryLine(ctx));
            }
            JSONArray nearest = state.optJSONArray("nearest");
            if (nearest != null && nearest.length() > 0) {
                sb.append("\n最近日子：");
                for (int i = 0; i < Math.min(3, nearest.length()); i++) {
                    JSONObject n = nearest.optJSONObject(i);
                    if (n == null) continue;
                    sb.append("\n· ").append(n.optString("title", ""));
                    String date = n.optString("date", "");
                    if (date.length() > 0) sb.append("  ").append(date);
                    String days = n.optString("days_text", "");
                    if (days.length() > 0) sb.append("  ").append(days);
                    String lunar = n.optString("lunar_label", "");
                    if (lunar.length() > 0) sb.append(" · ").append(lunar);
                }
            }
            return sb.toString().trim();
        } catch (Exception e) { return "守护日历读取失败：" + ScreenshotService.shortMsg(e); }
    }

    public static JSONArray dueReminders(Context ctx) {
        JSONArray arr = new JSONArray();
        try {
            for (Occurrence x : occurrences(ctx, false)) {
                if (x.bannerEnabled && x.daysLeft >= 0 && x.daysLeft <= Math.max(0, x.remindDays)) arr.put(x.toJson());
            }
        } catch (Exception ignored) { }
        return arr;
    }

    public static JSONObject handleCommand(Context ctx, JSONObject cmd) {
        JSONObject out = new JSONObject();
        try {
            String action = cmd.optString("action", "");
            if ("get_calendar_state".equals(action)) {
                out.put("ok", true).put("result", collect(ctx).toString()).put("calendar_state", collect(ctx));
            } else if ("upsert_calendar_event".equals(action) || "add_calendar_event".equals(action)) {
                JSONObject saved = upsertEvent(ctx,
                        cmd.optString("id", ""),
                        cmd.optString("title", ""),
                        normalizeDateType(cmd.optString("date_type", cmd.optBoolean("lunar", false) ? TYPE_LUNAR : TYPE_SOLAR)),
                        firstNonEmpty(cmd.optString("date", ""), cmd.optString("solar_date", ""), cmd.optString("date_text", "")),
                        firstPositive(cmd.optInt("lunar_month", 0), cmd.optInt("month", 0)),
                        firstPositive(cmd.optInt("lunar_day", 0), cmd.optInt("day", 0)),
                        cmd.optBoolean("lunar_is_leap", cmd.optBoolean("leap", false)),
                        normalizeRepeatType(cmd.optString("repeat_type", cmd.optBoolean("repeat", true) ? REPEAT_YEARLY : REPEAT_NONE)),
                        cmd.optString("group", "our_days"),
                        cmd.optString("note", ""),
                        cmd.optInt("remind_days_before", DEFAULT_REMIND_DAYS),
                        cmd.optBoolean("banner_enabled", true),
                        cmd.optString("created_by", "companion"));
                out.put("ok", saved.optBoolean("ok", false)).put("result", saved.toString()).put("saved", saved);
            } else if ("delete_calendar_event".equals(action)) {
                String id = cmd.optString("id", "");
                boolean ok = deleteEvent(ctx, id);
                out.put("ok", ok).put("result", ok ? "deleted:" + id : "not_found:" + id);
            } else {
                out.put("ok", false).put("result", "unknown_calendar_action");
            }
        } catch (Exception e) {
            try { out.put("ok", false).put("result", ScreenshotService.shortMsg(e)); } catch (Exception ignored) { }
        }
        return out;
    }

    public static JSONObject upsertEvent(Context ctx, String id, String title, String dateType, String date, int lunarMonth, int lunarDay, boolean lunarLeap, String repeatType, String group, String note, int remindDays, boolean bannerEnabled, String createdBy) {
        JSONObject out = new JSONObject();
        try {
            title = safe(title);
            if (title.length() == 0) return out.put("ok", false).put("error", "title_required");
            dateType = normalizeDateType(dateType);
            repeatType = normalizeRepeatType(repeatType);
            date = safe(date);
            group = normalizeGroup(group);
            JSONObject e = new JSONObject();
            if (id == null || id.trim().isEmpty()) id = "cal_" + System.currentTimeMillis();
            e.put("id", id);
            e.put("title", title);
            e.put("date_type", dateType);
            e.put("repeat_type", repeatType);
            e.put("group", group);
            e.put("note", safe(note));
            e.put("remind_days_before", clamp(remindDays, 0, 30));
            e.put("banner_enabled", bannerEnabled);
            e.put("created_by", safe(createdBy).length() == 0 ? "user" : safe(createdBy));
            e.put("updated_at", formatDateTime(System.currentTimeMillis()));
            if (TYPE_LUNAR.equals(dateType)) {
                int[] md = parseMonthDay(date);
                if (lunarMonth <= 0 && md[0] > 0) lunarMonth = md[0];
                if (lunarDay <= 0 && md[1] > 0) lunarDay = md[1];
                if (lunarMonth < 1 || lunarMonth > 12 || lunarDay < 1 || lunarDay > 30) return out.put("ok", false).put("error", "bad_lunar_date");
                e.put("lunar_month", lunarMonth);
                e.put("lunar_day", lunarDay);
                e.put("lunar_is_leap", lunarLeap);
                e.put("date", pad2(lunarMonth) + "-" + pad2(lunarDay));
            } else {
                String normalized = normalizeSolarDate(date, repeatType);
                if (normalized.length() == 0) return out.put("ok", false).put("error", "bad_solar_date");
                e.put("solar_date", normalized);
                e.put("date", normalized);
            }
            JSONArray arr = events(ctx);
            boolean replaced = false;
            String newKey = eventIdentityKey(e);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject old = arr.optJSONObject(i);
                if (old == null) continue;
                boolean sameId = id.equals(old.optString("id", ""));
                boolean sameEvent = !sameId && newKey.length() > 0 && newKey.equals(eventIdentityKey(old));
                if (sameId || sameEvent) { arr.put(i, e); replaced = true; break; }
            }
            if (!replaced) arr.put(e);
            if (!saveEvents(ctx, arr)) return out.put("ok", false).put("error", "save_failed");
            out.put("ok", true).put("event", e).put("calendar_state", collect(ctx));
        } catch (Exception ex) {
            try { out.put("ok", false).put("error", ScreenshotService.shortMsg(ex)); } catch (Exception ignored) { }
        }
        return out;
    }

    public static boolean deleteEvent(Context ctx, String id) {
        try {
            JSONArray arr = events(ctx);
            JSONArray next = new JSONArray();
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e != null && id != null && id.equals(e.optString("id", ""))) { found = true; continue; }
                if (e != null) next.put(e);
            }
            if (found) return saveEvents(ctx, next);
            return false;
        } catch (Exception e) { return false; }
    }

    public static JSONArray events(Context ctx) {
        try {
            String raw = AppPrefs.get(ctx).getString(KEY_EVENTS, "[]");
            JSONArray arr = new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
            // 旧版本的日历事件可能没有 id。读取时一次性补上可重复得到、且不会
            // 随应用重启变化的 UUID，后续编辑/删除始终只针对这一条事件。
            boolean migrated = false;
            HashSet<String> used = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                String id = safe(e.optString("id", ""));
                if (id.length() == 0 || used.contains(id)) {
                    String seed = "guardian-day|" + eventIdentityKey(e) + "|" + i + "|" + e.optString("updated_at", "");
                    id = "cal_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
                    while (used.contains(id)) id = "cal_" + UUID.randomUUID().toString();
                    e.put("id", id);
                    migrated = true;
                }
                used.add(id);
            }
            if (migrated) saveEvents(ctx, arr);
            return arr;
        } catch (Exception e) { return new JSONArray(); }
    }

    public static JSONObject eventById(Context ctx, String id) {
        if (id == null || id.trim().isEmpty()) return null;
        JSONArray arr = events(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e != null && id.equals(e.optString("id", ""))) return e;
        }
        return null;
    }

    private static boolean saveEvents(Context ctx, JSONArray arr) {
        return AppPrefs.get(ctx).edit().putString(KEY_EVENTS, arr.toString()).commit();
    }

    private static List<Occurrence> occurrences(Context ctx, boolean includeExpired) {
        ArrayList<Occurrence> list = new ArrayList<>();
        Calendar today = midnight(Calendar.getInstance());
        JSONArray arr = events(ctx);
        for (int i = 0; i < arr.length(); i++) addOccurrence(list, arr.optJSONObject(i), today, false);
        for (JSONObject e : builtinEvents()) addOccurrence(list, e, today, true);
        Collections.sort(list, new Comparator<Occurrence>() { @Override public int compare(Occurrence a, Occurrence b) { return a.daysLeft - b.daysLeft; } });
        return list;
    }

    private static void addOccurrence(ArrayList<Occurrence> list, JSONObject e, Calendar today, boolean builtin) {
        if (e == null || e.optBoolean("archived", false)) return;
        try {
            Calendar date = nextDateFor(e, today);
            if (date == null) return;
            int days = daysBetween(today, date);
            if (days < 0) return;
            Occurrence o = occurrenceFrom(e, date, days, builtin);
            if (o != null) list.add(o);
        } catch (Exception ignored) { }
    }

    private static Occurrence occurrenceFrom(JSONObject e, Calendar date, int days, boolean builtin) {
        if (e == null || date == null) return null;
        Occurrence o = new Occurrence();
        o.id = e.optString("id", builtin ? ("builtin_" + e.optString("title", "")) : "");
        o.title = e.optString("title", ""); o.group = e.optString("group", builtin ? "festival" : "our_days"); o.note = e.optString("note", "");
        o.date = date; o.daysLeft = days; o.dateType = e.optString("date_type", TYPE_SOLAR); o.repeatType = e.optString("repeat_type", REPEAT_YEARLY);
        o.remindDays = clamp(e.optInt("remind_days_before", DEFAULT_REMIND_DAYS), 0, 30); o.bannerEnabled = e.optBoolean("banner_enabled", true);
        o.createdBy = e.optString("created_by", builtin ? "system" : "user"); o.builtin = builtin;
        if (TYPE_LUNAR.equals(o.dateType)) o.lunarLabel = "农历" + e.optInt("lunar_month", 0) + "月" + e.optInt("lunar_day", 0);
        return o;
    }

    private static Calendar dateInYear(JSONObject e, int year) {
        try {
            String type = e.optString("date_type", TYPE_SOLAR);
            if (TYPE_LUNAR.equals(type)) return lunarToSolar(year, e.optInt("lunar_month", 0), e.optInt("lunar_day", 0), e.optBoolean("lunar_is_leap", false));
            String raw = e.optString("solar_date", e.optString("date", ""));
            Calendar parsed = parseSolarCalendar(raw, year); if (parsed == null) return null;
            if (REPEAT_YEARLY.equals(e.optString("repeat_type", REPEAT_YEARLY))) parsed.set(Calendar.YEAR, year);
            else if (parsed.get(Calendar.YEAR) != year) return null;
            return midnight(parsed);
        } catch (Exception ex) { return null; }
    }

    private static Calendar nextDateFor(JSONObject e, Calendar today) {
        String dateType = e.optString("date_type", TYPE_SOLAR);
        String repeat = e.optString("repeat_type", REPEAT_YEARLY);
        Calendar candidate = null;
        if (TYPE_LUNAR.equals(dateType)) {
            int m = e.optInt("lunar_month", 0), d = e.optInt("lunar_day", 0);
            boolean leap = e.optBoolean("lunar_is_leap", false);
            if (m < 1 || d < 1) return null;
            int y = today.get(Calendar.YEAR);
            candidate = lunarToSolar(y, m, d, leap);
            if (candidate != null && daysBetween(today, candidate) < 0 && REPEAT_YEARLY.equals(repeat)) candidate = lunarToSolar(y + 1, m, d, leap);
        } else {
            String ds = e.optString("solar_date", e.optString("date", ""));
            candidate = parseSolarCalendar(ds, today.get(Calendar.YEAR));
            if (candidate != null && daysBetween(today, candidate) < 0 && REPEAT_YEARLY.equals(repeat)) {
                candidate.set(Calendar.YEAR, today.get(Calendar.YEAR) + 1);
            }
        }
        if (candidate == null) return null;
        if (daysBetween(today, candidate) < 0 && !REPEAT_YEARLY.equals(repeat)) return null;
        return midnight(candidate);
    }

    private static List<JSONObject> builtinEvents() {
        ArrayList<JSONObject> list = new ArrayList<>();
        try {
            list.add(builtinLunar("七夕", 7, 7, "festival", "农历七月初七", 3));
            list.add(builtinLunar("中秋", 8, 15, "festival", "农历八月十五", 3));
            list.add(builtinLunar("春节", 1, 1, "festival", "农历正月初一", 3));
            list.add(builtinLunar("元宵", 1, 15, "festival", "农历正月十五", 3));
            list.add(builtinSolar("元旦", "01-01", "festival", "新年第一天", 3));
        } catch (Exception ignored) { }
        return list;
    }

    private static JSONObject builtinLunar(String title, int m, int d, String group, String note, int remind) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", "builtin_lunar_" + m + "_" + d).put("title", title).put("date_type", TYPE_LUNAR).put("lunar_month", m).put("lunar_day", d).put("lunar_is_leap", false).put("repeat_type", REPEAT_YEARLY).put("group", group).put("note", note).put("remind_days_before", remind).put("banner_enabled", true).put("created_by", "system");
        return o;
    }
    private static JSONObject builtinSolar(String title, String date, String group, String note, int remind) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", "builtin_solar_" + date).put("title", title).put("date_type", TYPE_SOLAR).put("solar_date", date).put("repeat_type", REPEAT_YEARLY).put("group", group).put("note", note).put("remind_days_before", remind).put("banner_enabled", true).put("created_by", "system");
        return o;
    }

    private static Calendar parseSolarCalendar(String raw, int currentYear) {
        try {
            raw = safe(raw).replace('.', '-').replace('/', '-');
            String[] parts = raw.split("-");
            int y, m, d;
            if (parts.length == 3) { y = Integer.parseInt(parts[0]); m = Integer.parseInt(parts[1]); d = Integer.parseInt(parts[2]); }
            else if (parts.length == 2) { y = currentYear; m = Integer.parseInt(parts[0]); d = Integer.parseInt(parts[1]); }
            else return null;
            Calendar c = Calendar.getInstance(); c.setLenient(false); c.set(Calendar.YEAR, y); c.set(Calendar.MONTH, m - 1); c.set(Calendar.DAY_OF_MONTH, d); return midnight(c);
        } catch (Exception e) { return null; }
    }

    private static String normalizeSolarDate(String raw, String repeatType) {
        Calendar c = parseSolarCalendar(raw, Calendar.getInstance().get(Calendar.YEAR));
        if (c == null) return "";
        if (REPEAT_YEARLY.equals(repeatType) && safe(raw).split("[-./]").length == 2) return pad2(c.get(Calendar.MONTH) + 1) + "-" + pad2(c.get(Calendar.DAY_OF_MONTH));
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private static int[] parseMonthDay(String raw) {
        try {
            raw = safe(raw).replace('.', '-').replace('/', '-');
            String[] parts = raw.split("-");
            if (parts.length == 3) return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
            if (parts.length == 2) return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (Exception ignored) { }
        return new int[]{0, 0};
    }

    private static int daysBetween(Calendar a, Calendar b) {
        long x = midnight((Calendar) a.clone()).getTimeInMillis();
        long y = midnight((Calendar) b.clone()).getTimeInMillis();
        return (int) Math.round((y - x) / 86400000.0);
    }

    private static Calendar midnight(Calendar c) { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c; }
    private static String daysText(int d) { if (d < 0) return "已经过去 " + Math.abs(d) + " 天"; if (d == 0) return "今天"; if (d == 1) return "明天"; return "还有 " + d + " 天"; }
    private static String bannerText(String title, int d) { if (d == 0) return "今天是" + title + "，记得回来看看。"; if (d == 1) return "明天是" + title + "，已经帮你记着。"; return "还有 " + d + " 天是" + title + "，别忘了这份小仪式。"; }
    private static String formatDate(Calendar c) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime()); }
    private static String formatDateTime(long ms) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(ms)); }
    private static String pad2(int n) { return n < 10 ? "0" + n : String.valueOf(n); }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) { String s = safe(v); if (s.length() > 0) return s; }
        return "";
    }
    private static int firstPositive(int... values) {
        if (values == null) return 0;
        for (int v : values) if (v > 0) return v;
        return 0;
    }
    private static String normalizeDateType(String value) {
        String v = safe(value).toLowerCase(Locale.US);
        if (TYPE_LUNAR.equals(v) || "农历".equals(value) || "阴历".equals(value) || "lunar_date".equals(v)) return TYPE_LUNAR;
        return TYPE_SOLAR;
    }
    private static String normalizeRepeatType(String value) {
        String v = safe(value).toLowerCase(Locale.US);
        if (REPEAT_NONE.equals(v) || "once".equals(v) || "no".equals(v) || "false".equals(v) || "不重复".equals(value) || "仅一次".equals(value) || "一次".equals(value)) return REPEAT_NONE;
        return REPEAT_YEARLY;
    }
    private static String eventIdentityKey(JSONObject e) {
        if (e == null) return "";
        String title = safe(e.optString("title", ""));
        String type = normalizeDateType(e.optString("date_type", TYPE_SOLAR));
        String date = TYPE_LUNAR.equals(type) ? (pad2(e.optInt("lunar_month", 0)) + "-" + pad2(e.optInt("lunar_day", 0))) : safe(e.optString("solar_date", e.optString("date", "")));
        if (title.length() == 0 || date.length() == 0) return "";
        return title + "|" + type + "|" + date;
    }
    private static String normalizeGroup(String g) {
        g = safe(g);
        if (g.length() == 0) return "our_days";
        if ("我们的日子".equals(g) || "我们".equals(g) || "纪念日".equals(g)) return "our_days"; if ("我的日子".equals(g) || "用户".equals(g) || "我".equals(g) || "自己".equals(g)) return "user"; if ("陪伴对象".equals(g) || "AI".equalsIgnoreCase(g) || "伴侣".equals(g)) return "companion"; if ("节日".equals(g)) return "festival"; if ("学习".equals(g) || "考试".equals(g)) return "study"; if ("项目".equals(g)) return "project"; if ("生活".equals(g)) return "life";
        return g.matches("[A-Za-z0-9_]+") ? g : "our_days";
    }
    private static String groupLabel(String g) {
        if ("our_days".equals(g)) return "我们的日子"; if ("user".equals(g)) return "我的日子"; if ("companion".equals(g)) return "陪伴对象"; if ("festival".equals(g)) return "节日"; if ("study".equals(g)) return "考试学习"; if ("project".equals(g)) return "项目"; if ("life".equals(g)) return "生活"; return g;
    }

    private static int leapMonth(int y) { if (y < 1900 || y > 2100) return 0; return LUNAR_INFO[y - 1900] & 0xf; }
    private static int leapDays(int y) { int lm = leapMonth(y); return lm == 0 ? 0 : ((LUNAR_INFO[y - 1900] & 0x10000) != 0 ? 30 : 29); }
    private static int monthDays(int y, int m) { return ((LUNAR_INFO[y - 1900] & (0x10000 >> m)) == 0) ? 29 : 30; }
    private static int yearDays(int y) { int sum = 348; for (int i = 0x8000; i > 0x8; i >>= 1) if ((LUNAR_INFO[y - 1900] & i) != 0) sum++; return sum + leapDays(y); }

    private static Calendar lunarToSolar(int year, int month, int day, boolean leap) {
        try {
            if (year < 1900 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 30) return null;
            int offset = 0;
            for (int y = 1900; y < year; y++) offset += yearDays(y);
            int leapMonth = leapMonth(year);
            for (int m = 1; m < month; m++) {
                offset += monthDays(year, m);
                if (m == leapMonth) offset += leapDays(year);
            }
            if (leap && leapMonth == month) offset += monthDays(year, month);
            offset += day - 1;
            Calendar base = Calendar.getInstance();
            base.set(1900, Calendar.JANUARY, 31, 0, 0, 0); base.set(Calendar.MILLISECOND, 0);
            base.add(Calendar.DAY_OF_MONTH, offset);
            return midnight(base);
        } catch (Exception e) { return null; }
    }

    private static class Occurrence {
        String id = ""; String title = ""; String group = ""; String note = ""; String dateType = TYPE_SOLAR; String repeatType = REPEAT_YEARLY; String lunarLabel = ""; String createdBy = "";
        Calendar date; int daysLeft; int remindDays = DEFAULT_REMIND_DAYS; boolean bannerEnabled = true; boolean builtin = false;
        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id); o.put("title", title); o.put("group", group); o.put("group_label", groupLabel(group)); o.put("note", note);
                o.put("date", formatDate(date)); o.put("days_left", daysLeft); o.put("days_text", daysText(daysLeft)); o.put("date_type", dateType); o.put("repeat_type", repeatType);
                o.put("lunar_label", lunarLabel); o.put("remind_days_before", remindDays); o.put("banner_enabled", bannerEnabled); o.put("banner_text", bannerText(title, daysLeft)); o.put("created_by", createdBy); o.put("builtin", builtin);
            } catch (Exception ignored) { }
            return o;
        }
    }
}
