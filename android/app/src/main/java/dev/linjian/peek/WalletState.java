package dev.linjian.peek;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 小金库：本地预算、花钱审批、通知识别待确认账单。默认本地保存，不读取支付密码。 */
public class WalletState {
    private static final String PREF = "linjian_wallet_v1";
    private static final String KEY_RECORDS = "records_json";
    private static final String KEY_BUDGET = "monthly_budget";
    private static final String KEY_APPROVAL = "approval_threshold";
    private static final String KEY_MODE = "auto_mode";
    private static final String KEY_DEEP_NIGHT = "deep_night";
    private static final String KEY_CATEGORY_LIMITS = "category_limits";
    private static final int MAX_RECORDS = 520;

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static JSONObject collect(Context ctx) { return collect(ctx, currentMonth()); }

    public static JSONObject collect(Context ctx, String targetMonth) {
        JSONObject out = new JSONObject();
        try {
            JSONArray all = records(ctx);
            long now = System.currentTimeMillis();
            String currentMonth = monthKey(now);
            String month = (targetMonth == null || targetMonth.trim().length() == 0) ? currentMonth : targetMonth.trim();
            double spent = 0, income = 0, saved = 0;
            int pending = 0, confirmed = 0, approvals = 0, approvalPending = 0;
            LinkedHashMap<String, Double> cat = new LinkedHashMap<>();
            JSONArray recent = new JSONArray();
            JSONArray pendingArr = new JSONArray();
            JSONArray approvalArr = new JSONArray();
            for (int i = 0; i < all.length(); i++) {
                JSONObject r = all.optJSONObject(i);
                if (r == null) continue;
                String status = r.optString("status", "confirmed");
                String type = r.optString("type", "expense");
                boolean isApproval = "approval_request".equals(type) || status.startsWith("approval_");
                if ("pending".equals(status)) {
                    pending++;
                    if (pendingArr.length() < 12) pendingArr.put(r);
                    continue;
                }
                if (isApproval) {
                    if (!month.equals(monthKey(r.optLong("created_at_ms", now)))) continue;
                    approvals++;
                    if ("approval_pending".equals(status) || "waiting".equals(r.optString("decision", ""))) approvalPending++;
                    if (approvalArr.length() < 40) approvalArr.put(r);
                    if ("approval_rejected".equals(status) || "approval_delayed".equals(status)) saved += Math.max(0, r.optDouble("amount", 0));
                    continue;
                }
                if ("ignored".equals(status)) continue;
                if (!month.equals(monthKey(r.optLong("created_at_ms", now)))) continue;
                if (recent.length() < 80) recent.put(r);
                double amount = Math.max(0, r.optDouble("amount", 0));
                if ("income".equals(type)) income += amount;
                else if ("saved".equals(type) || "rejected".equals(type) || "delayed".equals(type)) saved += amount;
                else {
                    spent += amount; confirmed++;
                    String c = r.optString("category", "其他");
                    cat.put(c, cat.containsKey(c) ? cat.get(c) + amount : amount);
                }
            }
            double budget = monthlyBudget(ctx);
            double remaining = Math.max(0, budget - spent);
            JSONObject cats = new JSONObject();
            for (Map.Entry<String, Double> e : cat.entrySet()) cats.put(e.getKey(), round2(e.getValue()));
            out.put("ok", true);
            out.put("device_id", AppPrefs.device(ctx));
            out.put("wallet_version", "0.3.9.0-public-wallet-auto-record");
            out.put("month", month);
            out.put("current_month", currentMonth);
            out.put("is_current_month", currentMonth.equals(month));
            out.put("month_label", monthLabel(month));
            out.put("monthly_budget", round2(budget));
            out.put("spent", round2(spent));
            out.put("income", round2(income));
            out.put("remaining", round2(remaining));
            out.put("saved_estimate", round2(saved));
            out.put("pending_count", pending);
            out.put("approval_count", approvals);
            out.put("approval_record_count", approvals);
            out.put("approval_pending_count", approvalPending);
            out.put("todo_count", approvalPending + pending);
            out.put("confirmed_count_this_month", confirmed);
            out.put("category_totals", cats);
            out.put("recent_records", recent);
            out.put("pending_records", pendingArr);
            out.put("approval_records", approvalArr);
            out.put("month_summaries", monthSummaries(ctx));
            out.put("rules", rules(ctx));
            out.put("summary", "小金库：" + monthLabel(month) + "已花 ¥" + money(spent) + "，预算 ¥" + money(budget) + "，剩余 ¥" + money(remaining) + "；待处理 " + (approvalPending + pending) + " 项，审批记录 " + approvals + " 条。");
        } catch (Exception e) { try { out.put("ok", false).put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) {} }
        return out;
    }

    /** 返回指定月份全部已入账账单，供小金库本地明细页编辑/删除；不把审批和待确认项混入。 */
    public static JSONArray recordsForMonth(Context ctx, String targetMonth) {
        JSONArray out = new JSONArray();
        try {
            JSONArray all = records(ctx);
            String month = (targetMonth == null || targetMonth.trim().length() == 0) ? currentMonth() : targetMonth.trim();
            long now = System.currentTimeMillis();
            for (int i = 0; i < all.length(); i++) {
                JSONObject r = all.optJSONObject(i);
                if (r == null) continue;
                String status = r.optString("status", "confirmed");
                String type = r.optString("type", "expense");
                boolean isApproval = "approval_request".equals(type) || status.startsWith("approval_");
                if ("pending".equals(status) || "ignored".equals(status) || isApproval) continue;
                if (!month.equals(monthKey(r.optLong("created_at_ms", now)))) continue;
                out.put(r);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static JSONArray monthSummaries(Context ctx) {
        JSONArray out = new JSONArray();
        try {
            JSONArray all = records(ctx);
            String current = currentMonth();
            LinkedHashMap<String, JSONObject> map = new LinkedHashMap<>();
            ensureMonth(map, current);
            for (int i = 0; i < all.length(); i++) {
                JSONObject r = all.optJSONObject(i);
                if (r == null) continue;
                String status = r.optString("status", "confirmed");
                String type = r.optString("type", "expense");
                if ("pending".equals(status) || "ignored".equals(status) || "approval_request".equals(type) || status.startsWith("approval_")) continue;
                String m = monthKey(r.optLong("created_at_ms", System.currentTimeMillis()));
                JSONObject s = ensureMonth(map, m);
                double amount = Math.max(0, r.optDouble("amount", 0));
                if ("income".equals(type)) s.put("income", round2(s.optDouble("income", 0) + amount));
                else if ("saved".equals(type) || "rejected".equals(type) || "delayed".equals(type)) s.put("saved_estimate", round2(s.optDouble("saved_estimate", 0) + amount));
                else {
                    s.put("spent", round2(s.optDouble("spent", 0) + amount));
                    s.put("count", s.optInt("count", 0) + 1);
                }
            }
            double budget = monthlyBudget(ctx);
            for (String m : map.keySet()) {
                JSONObject s = map.get(m);
                double spent = s.optDouble("spent", 0);
                s.put("monthly_budget", round2(budget));
                s.put("remaining", round2(Math.max(0, budget - spent)));
                s.put("label", monthLabel(m));
                s.put("is_current_month", current.equals(m));
                out.put(s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static JSONObject ensureMonth(LinkedHashMap<String, JSONObject> map, String month) throws Exception {
        JSONObject o = map.get(month);
        if (o == null) {
            o = new JSONObject();
            o.put("month", month);
            o.put("spent", 0);
            o.put("income", 0);
            o.put("saved_estimate", 0);
            o.put("count", 0);
            map.put(month, o);
        }
        return o;
    }

    public static JSONObject rules(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            SharedPreferences p = prefs(ctx);
            o.put("monthly_budget", round2(monthlyBudget(ctx)));
            o.put("approval_threshold", round2(approvalThreshold(ctx)));
            o.put("auto_mode", p.getString(KEY_MODE, "conservative"));
            o.put("deep_night_reminder", p.getBoolean(KEY_DEEP_NIGHT, true));
            o.put("category_limits", p.getString(KEY_CATEGORY_LIMITS, "奶茶:80,外卖:300,购物:500"));
            o.put("note", "小金库默认本地保存；通知识别只生成待确认账单。用户确认后才进入正式账本。");
        } catch (Exception ignored) {}
        return o;
    }

    private static double monthlyBudget(Context ctx) { return prefs(ctx).getFloat(KEY_BUDGET, 1200f); }
    private static double approvalThreshold(Context ctx) { return prefs(ctx).getFloat(KEY_APPROVAL, 50f); }

    public static JSONObject handleCommand(Context ctx, JSONObject cmd) {
        String action = cmd.optString("action", "get_wallet_state");
        try {
            if ("get_wallet_state".equals(action)) return collect(ctx, cmd.optString("month", currentMonth()));
            if ("get_wallet_month_state".equals(action)) return collect(ctx, cmd.optString("month", currentMonth()));
            if ("list_wallet_months".equals(action)) return new JSONObject().put("ok", true).put("month_summaries", monthSummaries(ctx));
            if ("list_wallet_pending".equals(action) || "list_wallet_approvals".equals(action)) {
                JSONObject s = collect(ctx, cmd.optString("month", currentMonth()));
                return new JSONObject().put("ok", true)
                        .put("pending_count", s.optInt("pending_count"))
                        .put("approval_count", s.optInt("approval_count"))
                        .put("approval_pending_count", s.optInt("approval_pending_count"))
                        .put("todo_count", s.optInt("todo_count"))
                        .put("pending_records", s.optJSONArray("pending_records"))
                        .put("approval_records", s.optJSONArray("approval_records"));
            }
            if ("list_companion_wallet_requests".equals(action)) {
                return listRequests(ctx, cmd, "companion");
            }
            if ("list_wallet_request_results".equals(action)) {
                return listRequests(ctx, cmd, cmd.optString("requester_role", "all"));
            }
            if ("get_wallet_rules".equals(action)) return new JSONObject().put("ok", true).put("rules", rules(ctx));
            if ("set_wallet_rules".equals(action)) return setRules(ctx, cmd);
            if ("add_wallet_record".equals(action)) return addRecord(ctx, cmd, cmd.optBoolean("require_confirm", false) ? "pending" : cmd.optString("status", "confirmed"));
            if ("edit_wallet_record".equals(action) || "update_wallet_record".equals(action)) return updateRecord(ctx, cmd);
            if ("delete_wallet_record".equals(action) || "remove_wallet_record".equals(action)) return deleteRecord(ctx, cmd.optString("id", ""));
            if ("submit_companion_wallet_request".equals(action)) { cmd.put("requester_role", "companion"); cmd.put("source", "mcp"); cmd.put("created_by", "companion"); return submitApprovalRequest(ctx, cmd); }
            if ("submit_wallet_approval".equals(action) || "submit_wallet_request".equals(action)) return submitApprovalRequest(ctx, cmd);
            if ("confirm_wallet_record".equals(action)) return confirmRecord(ctx, cmd);
            if ("decide_wallet_approval".equals(action) || "save_wallet_request_result".equals(action) || "update_wallet_request_result".equals(action) || "save_user_wallet_request_result".equals(action)) return decideApproval(ctx, cmd);
            if ("wallet_approval_request".equals(action)) return approvalRequest(ctx, cmd);
            if ("open_wallet".equals(action)) return new JSONObject().put("ok", true).put("result", "open_wallet_supported_by_activity");
            return new JSONObject().put("ok", false).put("error", "unknown_wallet_action").put("action", action);
        } catch (Exception e) { try { return new JSONObject().put("ok", false).put("error", ScreenshotService.shortMsg(e)).put("action", action); } catch (Exception ignored) { return new JSONObject(); } }
    }

    public static JSONObject listRequests(Context ctx, JSONObject cmd, String roleFilter) throws Exception {
        JSONObject s = collect(ctx, cmd.optString("month", currentMonth()));
        JSONArray approvals = s.optJSONArray("approval_records");
        JSONArray filtered = new JSONArray();
        String role = roleFilter == null ? "all" : roleFilter.trim().toLowerCase(Locale.US);
        String statusFilter = cmd.optString("status", "all").trim().toLowerCase(Locale.US);
        int waiting = 0, handled = 0;
        if (approvals != null) {
            for (int i = 0; i < approvals.length(); i++) {
                JSONObject r = approvals.optJSONObject(i);
                if (r == null) continue;
                String requester = requesterRoleOf(r);
                if (!"all".equals(role) && role.length() > 0 && !role.equals(requester)) continue;
                boolean isWaiting = "approval_pending".equals(r.optString("status")) || "waiting".equals(r.optString("decision"));
                if (("waiting".equals(statusFilter) || "pending".equals(statusFilter)) && !isWaiting) continue;
                if (("handled".equals(statusFilter) || "done".equals(statusFilter)) && isWaiting) continue;
                filtered.put(r);
                if (isWaiting) waiting++; else handled++;
            }
        }
        return new JSONObject().put("ok", true)
                .put("month", s.optString("month"))
                .put("requester_role", role.length() == 0 ? "all" : role)
                .put("status_filter", statusFilter.length() == 0 ? "all" : statusFilter)
                .put("approval_count", filtered.length())
                .put("pending_count", waiting)
                .put("handled_count", handled)
                .put("approval_records", filtered);
    }

    public static JSONObject setRules(Context ctx, JSONObject cmd) throws Exception {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (cmd.has("monthly_budget")) e.putFloat(KEY_BUDGET, (float)Math.max(0, cmd.optDouble("monthly_budget", monthlyBudget(ctx))));
        if (cmd.has("approval_threshold")) e.putFloat(KEY_APPROVAL, (float)Math.max(0, cmd.optDouble("approval_threshold", approvalThreshold(ctx))));
        if (cmd.has("auto_mode")) e.putString(KEY_MODE, cmd.optString("auto_mode", "conservative"));
        if (cmd.has("deep_night_reminder")) e.putBoolean(KEY_DEEP_NIGHT, cmd.optBoolean("deep_night_reminder", true));
        if (cmd.has("category_limits")) e.putString(KEY_CATEGORY_LIMITS, cmd.optString("category_limits", ""));
        e.apply();
        DebugState.append(ctx, "小金库规则已保存");
        return new JSONObject().put("ok", true).put("rules", rules(ctx));
    }

    public static JSONObject addRecord(Context ctx, JSONObject data, String status) throws Exception {
        double amount = optMoney(data, 0, "amount", "amount_yuan", "price", "cost", "estimated_amount", "total");
        if (amount <= 0) return new JSONObject().put("ok", false).put("error", "amount_required").put("hint", "请传入 amount/price/cost/estimated_amount，金额不能默认为 0。倒贴或无金额申请请走小金库申请。");
        JSONObject r = new JSONObject();
        long now = data.optLong("created_at_ms", System.currentTimeMillis());
        String title = firstText(data, "", "note", "title", "item", "purpose", "content", "name", "reason");
        r.put("id", data.optString("id", UUID.randomUUID().toString()));
        r.put("amount", round2(amount));
        r.put("type", data.optString("type", "expense"));
        r.put("category", data.optString("category", guessCategory(data.optString("merchant", title))));
        r.put("merchant", data.optString("merchant", ""));
        r.put("note", title);
        r.put("source", data.optString("source", "manual"));
        r.put("source_app", data.optString("source_app", ""));
        r.put("source_package", data.optString("source_package", ""));
        r.put("source_key", data.optString("source_key", ""));
        if (data.has("approval_id")) r.put("approval_id", data.optString("approval_id", ""));
        r.put("status", status == null || status.length() == 0 ? "confirmed" : status);
        r.put("created_at_ms", now);
        r.put("created_at_local", formatLocal(now));
        JSONArray arr = records(ctx);
        if (!r.optString("source_key", "").isEmpty() && hasSourceKey(arr, r.optString("source_key"))) return new JSONObject().put("ok", true).put("duplicate", true).put("record", r);
        JSONArray next = new JSONArray();
        next.put(r);
        for (int i = 0; i < Math.min(MAX_RECORDS - 1, arr.length()); i++) next.put(arr.optJSONObject(i));
        saveRecords(ctx, next);
        DebugState.append(ctx, "小金库新增" + ("pending".equals(r.optString("status")) ? "待确认" : "账单") + "：¥" + money(amount) + " " + r.optString("category"));
        return new JSONObject().put("ok", true).put("record", r).put("wallet_state", collect(ctx));
    }

    public static JSONObject submitApprovalRequest(Context ctx, JSONObject data) throws Exception {
        double amount = optMoney(data, 0, "amount", "amount_yuan", "price", "cost", "estimated_amount", "total");
        if (amount < 0) return new JSONObject().put("ok", false).put("error", "amount_invalid");
        String item = firstText(data, "", "item", "title", "purpose", "content", "name", "note", "reason");
        if (item.length() == 0) item = "这笔申请";
        String requester = normalizeApprovalRole(data.optString("requester_role", data.optString("requester", "")));
        if (requester.length() == 0) requester = "mcp".equals(data.optString("source", "")) || "companion".equals(data.optString("created_by", "")) ? "companion" : "user";
        String approver = "companion".equals(requester) ? "user" : "companion";
        JSONObject r = new JSONObject();
        long now = data.optLong("created_at_ms", System.currentTimeMillis());
        r.put("id", data.optString("id", UUID.randomUUID().toString()));
        r.put("amount", round2(amount));
        r.put("type", "approval_request");
        r.put("category", data.optString("category", guessCategory(item)));
        r.put("merchant", data.optString("merchant", ""));
        r.put("item", item);
        r.put("reason", data.optString("reason", ""));
        r.put("note", firstText(data, item, "note", "item", "title", "purpose", "content", "name"));
        r.put("necessity", data.optInt("necessity", 3));
        r.put("impulse", data.optInt("impulse", 3));
        r.put("source", data.optString("source", "manual"));
        r.put("requester_role", requester);
        r.put("approver_role", approver);
        r.put("requester_name", "companion".equals(requester) ? AppPrefs.companionName(ctx) : AppPrefs.userName(ctx));
        r.put("approver_name", "companion".equals(approver) ? AppPrefs.companionName(ctx) : AppPrefs.userName(ctx));
        r.put("status", "approval_pending");
        r.put("decision", "waiting");
        String defaultMsg = "companion".equals(requester)
                ? AppPrefs.companionName(ctx) + "提交给你处理，等待回复。"
                : "已提交给" + AppPrefs.companionName(ctx) + "审批，等待回复。";
        r.put("approval_message", data.optString("approval_message", defaultMsg));
        r.put("created_at_ms", now);
        r.put("created_at_local", formatLocal(now));
        JSONArray arr = records(ctx);
        JSONArray next = new JSONArray();
        next.put(r);
        for (int i = 0; i < Math.min(MAX_RECORDS - 1, arr.length()); i++) next.put(arr.optJSONObject(i));
        saveRecords(ctx, next);
        DebugState.append(ctx, "小金库新增待审批：¥" + money(amount) + " " + r.optString("item"));
        return new JSONObject().put("ok", true).put("approval", r).put("wallet_state", collect(ctx));
    }

    public static JSONObject decideApproval(Context ctx, JSONObject cmd) throws Exception {
        String id = cmd.optString("id", "");
        String decision = cmd.optString("decision", cmd.optString("status", "approved"));
        String status = "approval_approved";
        if ("reject".equals(decision) || "rejected".equals(decision) || "deny".equals(decision) || "no".equals(decision)) { decision = "rejected"; status = "approval_rejected"; }
        else if ("delay".equals(decision) || "delayed".equals(decision) || "hold".equals(decision) || "later".equals(decision)) { decision = "delayed"; status = "approval_delayed"; }
        else decision = "approved";
        String message = cmd.optString("message", cmd.optString("approval_message", cmd.optString("note", defaultApprovalMessage(decision))));
        JSONArray arr = records(ctx);
        JSONArray next = new JSONArray();
        JSONObject found = null;
        JSONObject autoRecord = null;
        boolean shouldAutoRecord = false;
        String recordId = "";
        String sourceKey = id.length() == 0 ? "" : ("approval:" + id);
        long decidedAt = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            if (id.length() > 0 && id.equals(r.optString("id"))) {
                found = new JSONObject(r.toString());
                found.put("status", status);
                found.put("decision", decision);
                found.put("approval_message", message);
                found.put("decision_note", message);
                if (cmd.has("user_reason")) found.put("user_reason", cmd.optString("user_reason", message));
                String approverRole = found.optString("approver_role", "companion");
                String defaultApprover = "user".equals(approverRole) ? AppPrefs.userName(ctx) : AppPrefs.companionName(ctx);
                found.put("approved_by", cmd.optString("approved_by", cmd.optString("handler_name", defaultApprover)));
                found.put("decided_at_ms", decidedAt);
                found.put("decided_at_local", formatLocal(decidedAt));
                double amount = Math.max(0, found.optDouble("amount", 0));
                shouldAutoRecord = "approved".equals(decision) && amount > 0
                        && found.optString("wallet_record_id", found.optString("linked_wallet_record_id", "")).length() == 0
                        && !hasSourceKey(arr, sourceKey);
                if (shouldAutoRecord) {
                    recordId = UUID.randomUUID().toString();
                    found.put("wallet_record_id", recordId);
                    found.put("linked_wallet_record_id", recordId);
                    found.put("auto_recorded", true);
                    found.put("auto_record_source_key", sourceKey);
                    if (message.length() == 0 || defaultApprovalMessage("approved").equals(message)) found.put("approval_message", defaultApprovalMessage("approved"));
                }
                next.put(found);
            } else next.put(r);
        }
        if (found == null) return new JSONObject().put("ok", false).put("error", "approval_not_found").put("id", id);
        saveRecords(ctx, next);
        if (shouldAutoRecord) {
            JSONObject rec = new JSONObject();
            rec.put("id", recordId);
            rec.put("amount", round2(found.optDouble("amount", 0)));
            rec.put("type", "expense");
            rec.put("category", found.optString("category", "其他"));
            rec.put("merchant", found.optString("merchant", ""));
            rec.put("note", firstText(found, found.optString("item", "审批通过支出"), "item", "note", "title", "purpose", "content", "name", "reason"));
            rec.put("source", "wallet_approval");
            rec.put("source_key", sourceKey);
            rec.put("approval_id", id);
            rec.put("created_at_ms", decidedAt);
            JSONObject added = addRecord(ctx, rec, "confirmed");
            autoRecord = added.optJSONObject("record");
        }
        DebugState.append(ctx, "小金库审批已处理：" + found.optString("decision") + " ¥" + money(found.optDouble("amount", 0)) + (shouldAutoRecord ? "，已自动入账" : ""));
        JSONObject out = new JSONObject().put("ok", true).put("approval", found).put("wallet_state", collect(ctx));
        if (autoRecord != null) out.put("auto_record", autoRecord);
        return out;
    }

    public static JSONObject updateRecord(Context ctx, JSONObject cmd) throws Exception {
        String id = cmd.optString("id", "");
        if (id.length() == 0) return new JSONObject().put("ok", false).put("error", "id_required");
        JSONArray arr = records(ctx);
        JSONArray next = new JSONArray();
        JSONObject found = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            if (id.equals(r.optString("id"))) {
                found = new JSONObject(r.toString());
                if (cmd.has("amount") || cmd.has("amount_yuan") || cmd.has("price") || cmd.has("cost") || cmd.has("estimated_amount") || cmd.has("total")) {
                    double amount = optMoney(cmd, found.optDouble("amount", 0), "amount", "amount_yuan", "price", "cost", "estimated_amount", "total");
                    if (amount <= 0) return new JSONObject().put("ok", false).put("error", "amount_required");
                    found.put("amount", round2(amount));
                }
                if (cmd.has("type")) found.put("type", cmd.optString("type", found.optString("type", "expense")));
                if (cmd.has("category")) found.put("category", cmd.optString("category", found.optString("category", "其他")));
                if (cmd.has("merchant")) found.put("merchant", cmd.optString("merchant", found.optString("merchant", "")));
                if (cmd.has("note") || cmd.has("title") || cmd.has("item") || cmd.has("purpose") || cmd.has("content") || cmd.has("name")) found.put("note", firstText(cmd, found.optString("note", ""), "note", "title", "item", "purpose", "content", "name"));
                found.put("updated_at_ms", System.currentTimeMillis());
                found.put("updated_at_local", formatLocal(System.currentTimeMillis()));
                next.put(found);
            } else next.put(r);
        }
        if (found == null) return new JSONObject().put("ok", false).put("error", "record_not_found").put("id", id);
        saveRecords(ctx, next);
        DebugState.append(ctx, "小金库账单已编辑：¥" + money(found.optDouble("amount", 0)) + " " + found.optString("category", ""));
        return new JSONObject().put("ok", true).put("record", found).put("wallet_state", collect(ctx));
    }

    public static JSONObject deleteRecord(Context ctx, String id) throws Exception {
        if (id == null || id.length() == 0) return new JSONObject().put("ok", false).put("error", "id_required");
        JSONArray arr = records(ctx);
        JSONArray next = new JSONArray();
        JSONObject removed = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            if (id.equals(r.optString("id"))) removed = r;
            else next.put(r);
        }
        if (removed == null) return new JSONObject().put("ok", false).put("error", "record_not_found").put("id", id);
        saveRecords(ctx, next);
        DebugState.append(ctx, "小金库账单已删除：¥" + money(removed.optDouble("amount", 0)) + " " + removed.optString("category", ""));
        return new JSONObject().put("ok", true).put("deleted_record", removed).put("wallet_state", collect(ctx));
    }

    private static String requesterRoleOf(JSONObject r) {
        if (r == null) return "user";
        String role = r.optString("requester_role", "").trim().toLowerCase(Locale.US);
        return "companion".equals(role) ? "companion" : "user";
    }

    private static String normalizeApprovalRole(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if ("ai".equals(v) || "bot".equals(v) || "companion".equals(v) || "partner".equals(v) || "ta".equals(v)) return "companion";
        if ("user".equals(v) || "me".equals(v) || "human".equals(v) || "owner".equals(v)) return "user";
        return "";
    }

    private static String defaultApprovalMessage(String decision) {
        if ("rejected".equals(decision)) return "这笔先不批，先冷静一下。";
        if ("delayed".equals(decision)) return "先等 20 分钟，真的还想买再回来申请。";
        return "通过，已自动记入小金库支出。";
    }

    public static JSONObject confirmRecord(Context ctx, JSONObject cmd) throws Exception {
        String id = cmd.optString("id", "");
        String decision = cmd.optString("decision", "confirm");
        JSONArray arr = records(ctx);
        JSONArray next = new JSONArray();
        JSONObject found = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            if (id.length() > 0 && id.equals(r.optString("id"))) {
                found = new JSONObject(r.toString());
                if ("ignore".equals(decision) || "ignored".equals(decision) || "not_expense".equals(decision)) found.put("status", "ignored");
                else found.put("status", "confirmed");
                if (cmd.has("amount")) found.put("amount", round2(cmd.optDouble("amount", found.optDouble("amount"))));
                if (cmd.has("category")) found.put("category", cmd.optString("category", found.optString("category")));
                if (cmd.has("note")) found.put("note", cmd.optString("note", found.optString("note")));
                next.put(found);
            } else next.put(r);
        }
        if (found == null) return new JSONObject().put("ok", false).put("error", "record_not_found").put("id", id);
        saveRecords(ctx, next);
        DebugState.append(ctx, "小金库处理待确认账单：" + found.optString("status") + " ¥" + money(found.optDouble("amount", 0)));
        return new JSONObject().put("ok", true).put("record", found).put("wallet_state", collect(ctx));
    }

    public static JSONObject approvalRequest(Context ctx, JSONObject cmd) throws Exception {
        // MCP 侧即时审批：优先处理已有申请；没有 id 时创建一条带审批结果的记录。
        if (cmd.optString("id", "").length() > 0) return decideApproval(ctx, cmd);
        double amount = cmd.optDouble("amount", 0);
        String item = cmd.optString("item", cmd.optString("note", "这笔消费"));
        int necessity = cmd.optInt("necessity", cmd.optInt("need", 3));
        int impulse = cmd.optInt("impulse", 3);
        String decision = cmd.optString("decision", "");
        String message = cmd.optString("message", "");
        if (decision.length() == 0) {
            decision = "approved";
            message = "通过。已自动记入小金库支出。";
            if (amount >= approvalThreshold(ctx) && impulse >= 4 && necessity <= 3) { decision = "delayed"; message = "先冷静 20 分钟。真的还想要再回来申请。"; }
            if (amount > monthlyBudget(ctx) * 0.35 && necessity <= 2) { decision = "rejected"; message = "这笔太重了，今天先不买。"; }
        }
        JSONObject r = new JSONObject(cmd.toString());
        r.put("item", item);
        r.put("note", item);
        r.put("source", "mcp");
        r.put("requester_role", cmd.optString("requester_role", "user"));
        r.put("approval_message", message);
        JSONObject created = submitApprovalRequest(ctx, r).optJSONObject("approval");
        if (created == null) return new JSONObject().put("ok", false).put("error", "approval_create_failed");
        JSONObject decided = decideApproval(ctx, new JSONObject().put("id", created.optString("id")).put("decision", decision).put("message", message)).optJSONObject("approval");
        return new JSONObject().put("ok", true).put("decision", decision).put("message", message).put("amount", amount).put("item", item).put("approval", decided).put("saved_estimate", ("rejected".equals(decision) || "delayed".equals(decision)) ? round2(amount) : 0).put("rules", rules(ctx)).put("wallet_state", collect(ctx));
    }

    public static void captureNotification(Context ctx, StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(ctx.getPackageName())) return;
            Notification n = sbn.getNotification();
            Bundle e = n.extras;
            String title = e == null ? "" : text(e.getCharSequence(Notification.EXTRA_TITLE));
            String body = e == null ? "" : text(e.getCharSequence(Notification.EXTRA_TEXT));
            StringBuilder all = new StringBuilder();
            all.append(title).append(" ").append(body);
            if (e != null) {
                CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (lines != null) for (CharSequence line : lines) all.append(" ").append(text(line));
            }
            JSONObject parsed = parsePayment(ctx, pkg, appLabel(ctx, pkg), all.toString(), sbn.getPostTime());
            if (parsed == null) return;
            String mode = prefs(ctx).getString(KEY_MODE, "conservative");
            boolean high = parsed.optDouble("confidence", 0) >= 0.82;
            String status = ("strong".equals(mode) || ("normal".equals(mode) && high)) ? "confirmed" : "pending";
            addRecord(ctx, parsed, status);
        } catch (Exception err) { DebugState.append(ctx, "小金库通知识别失败：" + ScreenshotService.shortMsg(err)); }
    }

    private static JSONObject parsePayment(Context ctx, String pkg, String app, String text, long postTime) throws Exception {
        String s = text == null ? "" : text.replace('\n', ' ').trim();
        if (s.length() == 0) return null;
        String lower = s.toLowerCase(Locale.US);
        boolean sourceLike = pkg.contains("alipay") || pkg.contains("tencent.mm") || pkg.contains("bank") || pkg.contains("unionpay") || app.contains("支付宝") || app.contains("微信") || app.contains("银行") || app.contains("云闪付") || app.contains("美团") || app.contains("淘宝") || app.contains("京东") || app.contains("拼多多") || app.contains("饿了么");
        boolean payWord = s.contains("支付") || s.contains("付款") || s.contains("消费") || s.contains("扣款") || s.contains("交易") || s.contains("支出") || s.contains("订单") || s.contains("已付") || s.contains("付款给");
        boolean negative = s.contains("退款") || s.contains("退回") || s.contains("收入") || s.contains("收款") || s.contains("到账") || s.contains("还款到账") || s.contains("转入");
        if (!sourceLike && !payWord) return null;
        if (negative && !s.contains("支出")) return null;
        Double amount = extractAmount(s);
        if (amount == null || amount <= 0 || amount > 200000) return null;
        String merchant = guessMerchant(s);
        JSONObject o = new JSONObject();
        o.put("amount", round2(amount));
        o.put("type", "expense");
        o.put("category", guessCategory(s));
        o.put("merchant", merchant);
        o.put("note", clip(s, 90));
        o.put("source", "notification");
        o.put("source_app", app);
        o.put("source_package", pkg);
        o.put("source_key", pkg + ":" + postTime + ":" + money(amount));
        o.put("created_at_ms", postTime > 0 ? postTime : System.currentTimeMillis());
        o.put("confidence", (sourceLike ? 0.45 : 0) + (payWord ? 0.35 : 0) + (merchant.length() > 0 ? 0.12 : 0) + 0.1);
        return o;
    }

    private static Double extractAmount(String s) {
        Pattern[] ps = new Pattern[]{
                Pattern.compile("(?:¥|￥|RMB|CNY|人民币)\\s*([0-9]+(?:\\.[0-9]{1,2})?)"),
                Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元")
        };
        for (Pattern p : ps) {
            Matcher m = p.matcher(s);
            double best = -1;
            while (m.find()) {
                try { double v = Double.parseDouble(m.group(1)); if (v > best) best = v; } catch (Exception ignored) {}
            }
            if (best > 0) return best;
        }
        return null;
    }

    public static JSONArray records(Context ctx) {
        try { return new JSONArray(prefs(ctx).getString(KEY_RECORDS, "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private static void saveRecords(Context ctx, JSONArray arr) { prefs(ctx).edit().putString(KEY_RECORDS, arr == null ? "[]" : arr.toString()).apply(); }

    private static boolean hasSourceKey(JSONArray arr, String key) {
        if (key == null || key.length() == 0) return false;
        for (int i = 0; i < arr.length(); i++) if (key.equals(arr.optJSONObject(i) == null ? "" : arr.optJSONObject(i).optString("source_key"))) return true;
        return false;
    }

    private static double optMoney(JSONObject data, double def, String... keys) {
        if (data == null || keys == null) return def;
        for (String k : keys) {
            if (k == null || !data.has(k) || data.isNull(k)) continue;
            Object v = data.opt(k);
            if (v instanceof Number) return ((Number)v).doubleValue();
            try {
                String raw = String.valueOf(v).replace("￥", "").replace("¥", "").replace("元", "").trim();
                if (raw.length() > 0) return Double.parseDouble(raw);
            } catch (Exception ignored) {}
        }
        return def;
    }

    private static String firstText(JSONObject data, String def, String... keys) {
        if (data == null || keys == null) return def == null ? "" : def;
        for (String k : keys) {
            if (k == null || !data.has(k) || data.isNull(k)) continue;
            String v = data.optString(k, "").trim();
            if (v.length() > 0) return v;
        }
        return def == null ? "" : def;
    }

    public static String guessCategory(String text) {
        String s = text == null ? "" : text;
        if (s.contains("奶茶") || s.contains("咖啡") || s.contains("饮") || s.contains("茶") || s.contains("瑞幸") || s.contains("蜜雪") || s.contains("喜茶")) return "饮品";
        if (s.contains("餐") || s.contains("饭") || s.contains("外卖") || s.contains("美团") || s.contains("饿了么") || s.contains("食") || s.contains("超市")) return "饮食";
        if (s.contains("淘宝") || s.contains("京东") || s.contains("拼多多") || s.contains("购物") || s.contains("商城") || s.contains("订单")) return "购物";
        if (s.contains("地铁") || s.contains("公交") || s.contains("打车") || s.contains("滴滴") || s.contains("交通")) return "交通";
        if (s.contains("书") || s.contains("课") || s.contains("学习") || s.contains("考试")) return "学习";
        if (s.contains("医院") || s.contains("药") || s.contains("医疗")) return "医疗";
        if (s.contains("电影") || s.contains("游戏") || s.contains("会员") || s.contains("娱乐")) return "娱乐";
        return "其他";
    }

    private static String guessMerchant(String s) {
        String[] keys = new String[]{"付款给", "商户", "收款方", "订单", "消费", "支付给"};
        for (String k : keys) {
            int idx = s.indexOf(k);
            if (idx >= 0) return clip(s.substring(idx + k.length()).replaceAll("[：:，,。].*$", "").trim(), 24);
        }
        return "";
    }

    public static String currentMonth() { return monthKey(System.currentTimeMillis()); }
    public static String monthLabel(String month) {
        try {
            String[] p = (month == null ? "" : month).split("-");
            if (p.length >= 2) return Integer.parseInt(p[0]) + "年" + Integer.parseInt(p[1]) + "月";
        } catch (Exception ignored) {}
        return month == null || month.length() == 0 ? "本月" : month;
    }
    private static String monthKey(long ms) { return new SimpleDateFormat("yyyy-MM", Locale.CHINA).format(new Date(ms)); }
    private static String formatLocal(long ms) { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(ms)); }
    public static String todayDate() { return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date()); }
    public static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    public static String money(double v) { return String.format(Locale.CHINA, "%.2f", v); }
    private static String text(CharSequence c) { return c == null ? "" : c.toString(); }
    private static String clip(String s, int n) { if (s == null) return ""; return s.length() <= n ? s : s.substring(0, n); }
    private static String appLabel(Context ctx, String pkg) {
        try { android.content.pm.PackageManager pm = ctx.getPackageManager(); android.content.pm.ApplicationInfo info = pm.getApplicationInfo(pkg, 0); CharSequence label = pm.getApplicationLabel(info); return label == null ? pkg : label.toString(); } catch (Exception e) { return pkg == null ? "" : pkg; }
    }
}
