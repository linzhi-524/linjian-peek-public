package dev.linjian.peek;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 外卖小助手：常点外卖卡片、预算、小金库联动与手机端自动点单。永远停在真正付款动作之前。 */
public class TakeoutState {
    private static final String PREF = "linjian_takeout_v1";
    private static final String KEY_CARDS = "cards_json";
    private static final String KEY_MEAL_BUDGET = "meal_budget";
    private static final String KEY_DAY_BUDGET = "day_budget";
    private static final String KEY_TASTE = "taste_note";
    private static final String KEY_LAST_PLAN = "last_plan_json";
    private static final String KEY_CHECKOUT_SESSION = "checkout_session_json";
    private static final int MAX_CARDS = 80;
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://[^\\s\\\"'<>]+|(?:meituan|eleme)://[^\\s\\\"'<>]+)");

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public static JSONObject collect(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            JSONArray cards = cards(ctx);
            double mealBudget = mealBudget(ctx);
            double dayBudget = dayBudget(ctx);
            JSONObject wallet = WalletState.collect(ctx);
            JSONObject checkout = checkoutSession(ctx);
            out.put("ok", true);
            out.put("takeout_version", "0.3.9.1-public-takeout");
            out.put("device_id", AppPrefs.device(ctx));
            out.put("meal_budget", WalletState.round2(mealBudget));
            out.put("day_budget", WalletState.round2(dayBudget));
            out.put("taste_note", tasteNote(ctx));
            out.put("card_count", cards.length());
            out.put("cards", cards);
            out.put("suggestions", suggest(ctx, new JSONObject().put("limit", 3)).optJSONArray("suggestions"));
            out.put("last_plan", lastPlan(ctx));
            out.put("checkout_session", checkout);
            out.put("wallet_summary", wallet.optString("summary", ""));
            String checkoutText = checkout.optString("status", "").length() == 0 ? "" : "，自动点单：" + checkout.optString("status", "");
            out.put("summary", "外卖小助手：单餐预算 ¥" + WalletState.money(mealBudget) + "，今日外卖预算 ¥" + WalletState.money(dayBudget) + "，常点 " + cards.length() + " 个" + checkoutText + "。可由手机端自动点到付款页，最终支付仍由用户本人确认。");
        } catch (Exception e) { try { out.put("ok", false).put("error", ScreenshotService.shortMsg(e)); } catch (Exception ignored) {} }
        return out;
    }

    public static JSONObject handleCommand(Context ctx, JSONObject cmd) {
        String action = cmd.optString("action", "get_takeout_state");
        try {
            if ("get_takeout_state".equals(action) || "list_takeout_cards".equals(action) || "list_takeout_meals".equals(action)) return collect(ctx);
            if ("set_takeout_budget".equals(action) || "set_takeout_preferences".equals(action)) return setPrefs(ctx, cmd);
            if ("add_takeout_card".equals(action) || "save_takeout_card".equals(action) || "update_takeout_card".equals(action)) return saveCard(ctx, cmd);
            if ("remember_takeout_meal".equals(action) || "remember_current_takeout_meal".equals(action)) return rememberMeal(ctx, cmd);
            if ("remove_takeout_card".equals(action) || "delete_takeout_card".equals(action)) return removeCard(ctx, cmd);
            if ("suggest_takeout_options".equals(action)) return suggest(ctx, cmd);
            if ("create_takeout_plan".equals(action)) return createPlan(ctx, cmd);
            if ("open_takeout_link".equals(action) || "open_takeout_plan".equals(action)) return openLink(ctx, cmd);
            if ("copy_takeout_note".equals(action)) return copyNote(ctx, cmd);
            if ("record_takeout_order".equals(action)) return recordOrder(ctx, cmd);
            if ("takeout_wallet_request".equals(action)) return takeoutWalletRequest(ctx, cmd);
            if ("prepare_takeout_checkout".equals(action) || "auto_takeout_checkout".equals(action)) return prepareCheckout(ctx, cmd);
            if ("get_takeout_checkout_status".equals(action)) return getCheckoutStatus(ctx);
            if ("cancel_takeout_checkout".equals(action)) return cancelCheckout(ctx, cmd.optString("reason", "user_cancelled"));
            return new JSONObject().put("ok", false).put("error", "unknown_takeout_action").put("action", action);
        } catch (Exception e) { try { return new JSONObject().put("ok", false).put("error", ScreenshotService.shortMsg(e)).put("action", action); } catch (Exception ignored) { return new JSONObject(); } }
    }

    public static JSONObject setPrefs(Context ctx, JSONObject cmd) throws Exception {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (cmd.has("meal_budget")) e.putFloat(KEY_MEAL_BUDGET, (float)Math.max(0, cmd.optDouble("meal_budget", mealBudget(ctx))));
        if (cmd.has("day_budget")) e.putFloat(KEY_DAY_BUDGET, (float)Math.max(0, cmd.optDouble("day_budget", dayBudget(ctx))));
        if (cmd.has("taste_note")) e.putString(KEY_TASTE, cmd.optString("taste_note", ""));
        e.apply();
        DebugState.append(ctx, "外卖小助手预算已保存");
        return new JSONObject().put("ok", true).put("takeout_state", collect(ctx));
    }

    public static JSONObject saveCard(Context ctx, JSONObject data) throws Exception {
        JSONArray arr = cards(ctx);
        String id = data.optString("id", "");
        JSONObject oldCard = id.length() > 0 ? findCardExact(ctx, id) : null;
        if (id.length() == 0) id = UUID.randomUUID().toString();
        String rawLink = data.has("link") ? data.optString("link", "") : data.optString("url", oldCard == null ? "" : oldCard.optString("link", ""));
        if (rawLink.length() == 0 && oldCard != null) rawLink = oldCard.optString("link", "");
        String normalizedLink = normalizeLink(rawLink);
        JSONObject card = new JSONObject();
        card.put("id", id);
        card.put("title", nonEmpty(value(data, oldCard, "title", "merchant", "常点外卖"), "常点外卖"));
        card.put("platform", value(data, oldCard, "platform", null, guessPlatform(normalizedLink)));
        card.put("link", normalizedLink);
        String rawDirect = value(data, oldCard, "direct_link", "meal_link", "");
        card.put("direct_link", normalizeLink(rawDirect));
        card.put("resolved_link", normalizeLink(value(data, oldCard, "resolved_link", null, "")));
        card.put("jd_openapp", value(data, oldCard, "jd_openapp", null, ""));
        card.put("resolution_status", value(data, oldCard, "resolution_status", null, ""));
        card.put("memory_mode", normalizeLink(rawDirect).length() > 0 ? "direct" : value(data, oldCard, "memory_mode", null, "store_search"));
        if (!rawLink.equals(normalizedLink) && rawLink.trim().length() > 0) card.put("share_text", rawLink);
        else if (oldCard != null && oldCard.has("share_text")) card.put("share_text", oldCard.optString("share_text", ""));
        card.put("items", value(data, oldCard, "items", "item", ""));
        card.put("item_query", nonEmpty(value(data, oldCard, "item_query", null, ""), value(data, oldCard, "items", "item", "")));
        card.put("choices", normalizeChoices(data.has("choices") ? data.opt("choices") : (oldCard == null ? null : oldCard.opt("choices"))));
        card.put("price_min", WalletState.round2(number(data, oldCard, "price_min", number(data, oldCard, "amount", number(data, oldCard, "price", 0)))));
        card.put("price_max", WalletState.round2(number(data, oldCard, "price_max", number(data, oldCard, "amount", number(data, oldCard, "price", 0)))));
        card.put("checkout_max", WalletState.round2(Math.max(0, number(data, oldCard, "checkout_max", 0))));
        card.put("strict_budget", bool(data, oldCard, "strict_budget", false));
        card.put("note", value(data, oldCard, "note", "remark", ""));
        card.put("tags", value(data, oldCard, "tags", null, ""));
        card.put("aliases", normalizeChoices(data.has("aliases") ? data.opt("aliases") : (oldCard == null ? null : oldCard.opt("aliases"))));
        card.put("coupon_mode", value(data, oldCard, "coupon_mode", null, "platform_default"));
        card.put("last_used_at", value(data, oldCard, "last_used_at", null, ""));
        card.put("created_at_ms", oldCard == null ? data.optLong("created_at_ms", System.currentTimeMillis()) : oldCard.optLong("created_at_ms", System.currentTimeMillis()));
        card.put("created_at_local", oldCard == null ? data.optString("created_at_local", formatLocal(System.currentTimeMillis())) : oldCard.optString("created_at_local", formatLocal(System.currentTimeMillis())));
        card.put("updated_at_ms", System.currentTimeMillis());
        card.put("updated_at_local", formatLocal(System.currentTimeMillis()));

        JSONArray next = new JSONArray();
        next.put(card);
        for (int i = 0; i < arr.length() && next.length() < MAX_CARDS; i++) {
            JSONObject old = arr.optJSONObject(i);
            if (old == null || id.equals(old.optString("id"))) continue;
            next.put(old);
        }
        saveCards(ctx, next);
        DebugState.append(ctx, "外卖小助手保存常点：" + card.optString("title") + (normalizedLink.equals(rawLink) ? "" : "（已自动提取纯链接）"));
        return new JSONObject().put("ok", true).put("card", card).put("takeout_state", collect(ctx));
    }

    /**
     * 第一次由用户打开具体菜品并复制“这道饭”的分享链接，掌心窗把它记成可重复使用的饭。
     * link/direct_link 可以显式传入；为空时会尝试读取当前剪贴板。支持保存多道饭，最多 MAX_CARDS 条。
     */
    public static JSONObject rememberMeal(Context ctx, JSONObject cmd) throws Exception {
        JSONObject data = new JSONObject(cmd.toString());
        String direct = normalizeLink(nonEmpty(cmd.optString("direct_link", ""), cmd.optString("meal_link", "")));
        if (direct.length() == 0) direct = normalizeLink(cmd.optString("link", ""));
        if (direct.length() == 0) direct = clipboardLink(ctx);
        if (direct.length() == 0 || !supportedLink(direct)) {
            return new JSONObject().put("ok", false).put("error", "meal_link_required")
                    .put("hint", "先在外卖 App 打开具体菜品，点分享并复制链接，再点“记住这道饭”；也可以直接把菜品分享链接传给 remember_takeout_meal。");
        }
        String title = cmd.optString("title", "").trim();
        if (isJdShortLink(direct) && title.length() == 0) {
            return new JSONObject().put("ok", false).put("error", "jd_meal_name_required")
                    .put("hint", "京东外卖的 3.cn 分享通常只是店铺入口，请给这道饭填一个菜名，例如“土豆片炒肉木桶饭”。掌心窗会解析店铺入口后在店内自动找这道菜。");
        }
        if (title.length() == 0) title = suggestTitleFromShareText(cmd.optString("share_text", ""));
        if (title.length() == 0) title = suggestTitleFromShareText(clipboardText(ctx));
        if (title.length() == 0) title = inferMealTitleFromScreen();
        if (title.length() == 0) title = "记住的外卖";
        data.put("title", title);
        data.put("items", nonEmpty(cmd.optString("items", ""), title));
        data.put("item_query", nonEmpty(cmd.optString("item_query", ""), title));
        // 京东外卖分享目前常给 3.cn 的“店铺分享短链”，并不保证是具体菜品深链。
        // 不再把它伪装成 direct_item：保存为店铺入口，后端先解析真实落地页，再由店内搜索找目标菜。
        if (isJdShortLink(direct)) {
            data.put("link", direct);
            data.put("direct_link", "");
            data.put("memory_mode", "jd_store_resolve_search");
        } else {
            data.put("direct_link", direct);
            if (normalizeLink(data.optString("link", "")).length() == 0) data.put("link", direct);
            data.put("memory_mode", "direct");
        }
        data.put("platform", nonEmpty(cmd.optString("platform", ""), guessPlatform(direct)));
        JSONObject saved = saveCard(ctx, data);
        JSONObject card = saved.optJSONObject("card");
        if (card != null) card.put("remembered_from", "meal_share_link");
        DebugState.append(ctx, "记住这道饭：" + title);
        return new JSONObject().put("ok", true).put("remembered", true).put("card", card).put("takeout_state", collect(ctx))
                .put("note", "已把这道饭保存为直达饭卡；以后可按名字或 id 直接点到付款页。可继续保存多道饭。");
    }

    public static JSONObject removeCard(Context ctx, JSONObject cmd) throws Exception {
        String id = cmd.optString("id", "");
        JSONArray arr = cards(ctx), next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            if (id.length() > 0 && id.equals(r.optString("id"))) { removed = true; continue; }
            next.put(r);
        }
        saveCards(ctx, next);
        return new JSONObject().put("ok", removed).put("removed", removed).put("takeout_state", collect(ctx));
    }

    public static JSONObject suggest(Context ctx, JSONObject cmd) throws Exception {
        JSONArray arr = cards(ctx);
        int limit = Math.max(1, Math.min(8, cmd.optInt("limit", 3)));
        String query = (cmd.optString("query", "") + " " + cmd.optString("taste", "") + " " + tasteNote(ctx)).trim();
        double budget = cmd.has("budget") ? cmd.optDouble("budget", mealBudget(ctx)) : mealBudget(ctx);
        JSONArray out = new JSONArray();
        for (int pass = 0; pass < 3 && out.length() < limit; pass++) {
            for (int i = 0; i < arr.length() && out.length() < limit; i++) {
                JSONObject c = arr.optJSONObject(i);
                if (c == null || containsId(out, c.optString("id"))) continue;
                double max = priceMax(c);
                boolean budgetOk = max <= 0 || budget <= 0 || max <= budget;
                boolean queryOk = query.length() == 0 || matchCard(c, query);
                if ((pass == 0 && budgetOk && queryOk) || (pass == 1 && budgetOk) || pass == 2) {
                    JSONObject s = new JSONObject(c.toString());
                    s.put("estimated_amount", estimate(c, budget));
                    s.put("budget_ok", budgetOk);
                    s.put("reason", reasonFor(c, budgetOk, budget));
                    out.put(s);
                }
            }
        }
        return new JSONObject().put("ok", true).put("meal_budget", WalletState.round2(budget)).put("taste_note", tasteNote(ctx)).put("suggestions", out).put("note", "只从用户保存的常点外卖库里推荐；可以随后调用 prepare_takeout_checkout 让手机本地自动点到付款页。");
    }

    public static JSONObject createPlan(Context ctx, JSONObject cmd) throws Exception {
        String requestedId = nonEmpty(cmd.optString("card_id", ""), cmd.optString("meal_id", ""));
        JSONObject card = findCard(ctx, requestedId, cmd.optString("query", cmd.optString("item", cmd.optString("meal", ""))));
        if (card == null) return new JSONObject().put("ok", false).put("error", "takeout_card_not_found").put("hint", "先在外卖小助手里保存常点店铺或菜品链接。");
        double amount = cmd.optDouble("amount", estimate(card, mealBudget(ctx)));
        JSONObject plan = new JSONObject();
        plan.put("id", UUID.randomUUID().toString());
        plan.put("card_id", card.optString("id"));
        plan.put("title", card.optString("title"));
        plan.put("platform", card.optString("platform"));
        String planLink = normalizeLink(nonEmpty(card.optString("direct_link", ""), card.optString("link", "")));
        plan.put("link", planLink);
        plan.put("direct_link", normalizeLink(card.optString("direct_link", "")));
        plan.put("direct_item", normalizeLink(card.optString("direct_link", "")).length() > 0);
        plan.put("items", cmd.optString("items", card.optString("items")));
        plan.put("item_query", cmd.optString("item_query", card.optString("item_query", card.optString("items"))));
        plan.put("choices", cmd.has("choices") ? normalizeChoices(cmd.opt("choices")) : card.optJSONArray("choices"));
        plan.put("note", cmd.optString("note", card.optString("note")));
        plan.put("estimated_amount", WalletState.round2(amount));
        plan.put("meal_budget", WalletState.round2(mealBudget(ctx)));
        plan.put("budget_ok", amount <= 0 || amount <= mealBudget(ctx));
        plan.put("need_wallet_request", amount > 0 && amount > mealBudget(ctx));
        plan.put("created_at_ms", System.currentTimeMillis());
        plan.put("created_at_local", formatLocal(System.currentTimeMillis()));
        prefs(ctx).edit().putString(KEY_LAST_PLAN, plan.toString()).apply();
        DebugState.append(ctx, "外卖小助手生成计划：" + plan.optString("title") + " ¥" + WalletState.money(amount));
        if (cmd.optBoolean("submit_wallet_request", false)) {
            JSONObject req = new JSONObject();
            req.put("amount", amount);
            req.put("category", "饮食");
            req.put("merchant", card.optString("title"));
            req.put("item", "外卖：" + nonEmpty(plan.optString("items"), card.optString("title")));
            req.put("reason", cmd.optString("reason", "外卖小助手生成的点餐计划"));
            req.put("necessity", cmd.optInt("necessity", 4));
            req.put("impulse", cmd.optInt("impulse", 2));
            req.put("source", "takeout_assistant");
            plan.put("wallet_request", WalletState.submitApprovalRequest(ctx, req).optJSONObject("approval"));
        }
        return new JSONObject().put("ok", true).put("plan", plan).put("card", card).put("note", "可调用 prepare_takeout_checkout 让手机本地自动点到付款页；真正支付仍由用户本人完成。");
    }

    public static JSONObject takeoutWalletRequest(Context ctx, JSONObject cmd) throws Exception {
        JSONObject made = createPlan(ctx, new JSONObject(cmd.toString()).put("submit_wallet_request", false));
        if (!made.optBoolean("ok")) return made;
        JSONObject plan = made.optJSONObject("plan");
        JSONObject req = new JSONObject();
        req.put("amount", plan.optDouble("estimated_amount", cmd.optDouble("amount", 0)));
        req.put("category", "饮食");
        req.put("merchant", plan.optString("title"));
        req.put("item", "外卖：" + nonEmpty(plan.optString("items"), plan.optString("title")));
        req.put("reason", cmd.optString("reason", "想点这份外卖"));
        req.put("necessity", cmd.optInt("necessity", 4));
        req.put("impulse", cmd.optInt("impulse", 2));
        req.put("source", "takeout_assistant");
        JSONObject approval = WalletState.submitApprovalRequest(ctx, req).optJSONObject("approval");
        return new JSONObject().put("ok", true).put("plan", plan).put("approval", approval).put("wallet_state", WalletState.collect(ctx));
    }

    public static JSONObject openLink(Context ctx, JSONObject cmd) throws Exception {
        String jdOpenApp = cmd.optString("jd_openapp", "").trim();
        if (jdOpenApp.length() > 0) return openJdOpenApp(ctx, jdOpenApp);
        String link = cmd.optString("link", "");
        if (link.length() == 0) {
            JSONObject card = findCard(ctx, cmd.optString("card_id", ""), cmd.optString("query", ""));
            if (card != null) link = nonEmpty(card.optString("direct_link", ""), card.optString("link", ""));
        }
        if (link.length() == 0) link = lastPlan(ctx).optString("link", "");
        link = normalizeLink(link);
        if (link.length() == 0) return new JSONObject().put("ok", false).put("error", "link_required");
        if (!supportedLink(link)) return new JSONObject().put("ok", false).put("error", "unsupported_link_scheme").put("link", link);

        // 3.cn 分享短链本身不是稳定的 App 深链。自动点单会先让后端解析真正的京东落地地址，
        // 再通过 openapp.jdmobile 交给京东。只有用户单独点“打开原始链接”时才保留浏览器兜底。
        if (isJdShortLink(link)) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                browser.setPackage("com.android.chrome");
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ctx.startActivity(browser);
                return new JSONObject().put("ok", true).put("opened", true).put("link", link)
                        .put("open_mode", "jd_shortlink_raw_fallback")
                        .put("note", "这是京东分享短链的原始兜底打开方式；自动点单会优先先解析真实落地页，不走这条链路。");
            } catch (Exception ignored) { }
        }
        String preferredPackage = preferredPackage(link, cmd.optString("platform", ""));
        if (preferredPackage.length() > 0) {
            try {
                Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                appIntent.setPackage(preferredPackage);
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ctx.startActivity(appIntent);
                return new JSONObject().put("ok", true).put("opened", true).put("link", link)
                        .put("open_mode", "preferred_app").put("package", preferredPackage)
                        .put("note", "已优先直接交给外卖 App；最终支付仍需用户本人确认。");
            } catch (ActivityNotFoundException ignored) {
                // 回退系统 ACTION_VIEW。
            } catch (Exception ignored) {
                // 某些 ROM 对短链 setPackage 支持不完整，交给系统路由兜底。
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
        return new JSONObject().put("ok", true).put("opened", true).put("link", link)
                .put("open_mode", "system_fallback").put("preferred_package", preferredPackage)
                .put("note", "已打开外卖链接；若先进入浏览器，手机端会自动尝试点击“打开 App”。真正支付仍需用户本人确认。");
    }

    static boolean isJdShortLink(String link) {
        if (link == null) return false;
        try {
            Uri u = Uri.parse(link.trim());
            String h = u.getHost();
            return h != null && ("3.cn".equalsIgnoreCase(h) || h.toLowerCase(Locale.US).endsWith(".3.cn"));
        } catch (Exception ignored) { return false; }
    }

    static JSONObject openJdOpenApp(Context ctx, String scheme) throws Exception {
        if (scheme == null || !scheme.toLowerCase(Locale.US).startsWith("openapp.jdmobile://")) {
            return new JSONObject().put("ok", false).put("error", "jd_openapp_invalid");
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme));
        intent.setPackage("com.jingdong.app.mall");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ctx.startActivity(intent);
        return new JSONObject().put("ok", true).put("opened", true).put("open_mode", "jd_openapp_resolved").put("jd_openapp", scheme)
                .put("note", "已使用京东内部 openApp 协议打开解析后的落地页，不经过 Chrome 登录中转。");
    }

    static JSONObject resolveJdShortLinkViaBackend(Context ctx, String link, String itemQuery) {
        JSONObject last = new JSONObject();
        try {
            if (!isJdShortLink(link)) return new JSONObject().put("ok", true).put("resolved_url", normalizeLink(link)).put("source", "not_shortlink");
            String token = AppPrefs.token(ctx);
            if (token == null || token.trim().length() == 0) return new JSONObject().put("ok", false).put("error", "backend_token_missing");
            String[] servers = AppPrefs.candidateServers(ctx);
            for (String server : servers) {
                if (server == null || server.trim().length() == 0) continue;
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(AppPrefs.cleanUrl(server) + "/api/takeout/resolve_jd_link");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(9000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("X-Auth-Token", token);
                    conn.setDoOutput(true);
                    JSONObject body = new JSONObject().put("url", link).put("item_query", itemQuery == null ? "" : itemQuery);
                    byte[] bytes = body.toString().getBytes("UTF-8");
                    OutputStream os = conn.getOutputStream();
                    os.write(bytes); os.flush(); os.close();
                    int code = conn.getResponseCode();
                    String text = ScreenshotService.readBody(conn, code);
                    if (text != null && text.trim().startsWith("{")) last = new JSONObject(text);
                    else last = new JSONObject().put("ok", false).put("error", "resolver_bad_response").put("http_status", code);
                    last.put("resolver_server", AppPrefs.cleanUrl(server));
                    if (code >= 200 && code < 300 && last.optBoolean("ok", false)) return last;
                    // 404 表示这个备用后端还没升级，继续尝试下一个候选后端。
                    if (code != 404 && code != 405 && code != 501) return last;
                } catch (Exception e) {
                    try { last = new JSONObject().put("ok", false).put("error", "resolver_request_failed").put("detail", ScreenshotService.shortMsg(e)); } catch (Exception ignored) {}
                } finally { if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {} }
            }
        } catch (Exception e) {
            try { last = new JSONObject().put("ok", false).put("error", "resolver_exception").put("detail", ScreenshotService.shortMsg(e)); } catch (Exception ignored) {}
        }
        return last;
    }

    static void cacheResolvedMealLink(Context ctx, String cardId, JSONObject resolved) {
        if (cardId == null || cardId.length() == 0 || resolved == null || !resolved.optBoolean("ok", false)) return;
        try {
            JSONArray arr = cards(ctx), next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                if (c == null) continue;
                if (cardId.equals(c.optString("id", ""))) {
                    c = new JSONObject(c.toString());
                    c.put("resolved_link", normalizeLink(resolved.optString("resolved_url", "")));
                    c.put("jd_openapp", resolved.optString("openapp_url", ""));
                    c.put("resolution_status", "resolved");
                    c.put("resolved_at_ms", System.currentTimeMillis());
                    c.put("resolved_at_local", formatLocal(System.currentTimeMillis()));
                }
                next.put(c);
            }
            saveCards(ctx, next);
        } catch (Exception ignored) { }
    }

    static String preferredPackage(String link, String platform) {
        String s = ((link == null ? "" : link) + " " + (platform == null ? "" : platform)).toLowerCase(Locale.US);
        if (s.contains("3.cn")) return "";
        if (s.contains("jd.com") || s.contains("jingdong") || s.contains("京东")) return "com.jingdong.app.mall";
        if (s.contains("meituan") || s.contains("美团")) return "com.sankuai.meituan";
        if (s.contains("ele.me") || s.contains("eleme") || s.contains("饿了么")) return "me.ele";
        return "";
    }

    public static JSONObject copyNote(Context ctx, JSONObject cmd) throws Exception {
        String note = cmd.optString("note", "");
        if (note.length() == 0) {
            JSONObject card = findCard(ctx, cmd.optString("card_id", ""), cmd.optString("query", ""));
            if (card != null) note = card.optString("note", "");
        }
        if (note.length() == 0) note = lastPlan(ctx).optString("note", "");
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("外卖备注", note));
        return new JSONObject().put("ok", true).put("copied", note).put("note", "已复制外卖备注。");
    }

    public static JSONObject recordOrder(Context ctx, JSONObject cmd) throws Exception {
        double amount = cmd.optDouble("amount", 0);
        if (amount <= 0) return new JSONObject().put("ok", false).put("error", "amount_required");
        JSONObject card = findCard(ctx, cmd.optString("card_id", ""), cmd.optString("query", cmd.optString("merchant", "")));
        JSONObject rec = new JSONObject();
        rec.put("amount", amount);
        rec.put("type", "expense");
        rec.put("category", "饮食");
        rec.put("merchant", cmd.optString("merchant", card == null ? "外卖" : card.optString("title", "外卖")));
        rec.put("note", cmd.optString("note", "外卖" + (card == null ? "" : "：" + nonEmpty(card.optString("items"), card.optString("title")))));
        rec.put("source", "takeout_assistant");
        JSONObject rr = WalletState.addRecord(ctx, rec, "confirmed");
        return new JSONObject().put("ok", true).put("wallet_record", rr.optJSONObject("record")).put("wallet_state", WalletState.collect(ctx));
    }

    public static JSONObject prepareCheckout(Context ctx, JSONObject cmd) throws Exception {
        long expires = cmd.optLong("expires_at_ms", 0);
        if (expires > 0 && System.currentTimeMillis() > expires) return new JSONObject().put("ok", false).put("error", "stale_takeout_command").put("detail", "命令已过期，不执行旧点单任务。");
        if (!ScreenshotService.ready()) return new JSONObject().put("ok", false).put("error", "accessibility_not_ready").put("hint", "请先开启掌心窗无障碍权限。");
        String requestedId = nonEmpty(cmd.optString("card_id", ""), cmd.optString("meal_id", ""));
        JSONObject card = findCard(ctx, requestedId, cmd.optString("query", cmd.optString("item", cmd.optString("meal", ""))));
        if (card == null) return new JSONObject().put("ok", false).put("error", "takeout_card_not_found");
        String cardDirect = normalizeLink(card.optString("direct_link", ""));
        String link = normalizeLink(cmd.optString("link", nonEmpty(cardDirect, card.optString("link", ""))));
        boolean directItem = cardDirect.length() > 0 && link.equals(cardDirect) && !isJdShortLink(cardDirect);
        if (link.length() == 0 || !supportedLink(link)) return new JSONObject().put("ok", false).put("error", "takeout_link_invalid").put("link", link);
        String cachedResolved = normalizeLink(card.optString("resolved_link", ""));
        String cachedOpenApp = card.optString("jd_openapp", "").trim();
        long now = System.currentTimeMillis();
        int timeoutSeconds = Math.max(45, Math.min(240, cmd.optInt("timeout_seconds", 120)));
        JSONObject s = new JSONObject();
        s.put("id", UUID.randomUUID().toString());
        s.put("card_id", card.optString("id"));
        s.put("title", card.optString("title"));
        s.put("platform", nonEmpty(cmd.optString("platform", card.optString("platform", "")), guessPlatform(link)));
        s.put("link", link);
        s.put("resolved_link", cachedResolved);
        s.put("jd_openapp", cachedOpenApp);
        s.put("direct_item", directItem);
        s.put("memory_mode", directItem ? "direct" : "store_search");
        s.put("status", "running");
        s.put("stage", "starting");
        s.put("detail", directItem ? "已找到记住的这道饭，优先直达菜品后继续到付款页。" : "已接收整单任务，接下来由手机本地连续执行，不再逐步等待远程点击。");
        String cmdItem = cmd.optString("item_query", "").trim();
        s.put("item_query", nonEmpty(cmdItem, nonEmpty(card.optString("item_query", ""), card.optString("items", ""))));
        JSONArray cmdChoices = normalizeChoices(cmd.opt("choices"));
        s.put("choices", cmdChoices.length() > 0 ? cmdChoices : normalizeChoices(card.opt("choices")));
        s.put("note", nonEmpty(cmd.optString("note", ""), card.optString("note", "")));
        s.put("coupon_mode", cmd.optString("coupon_mode", card.optString("coupon_mode", "platform_default")));
        double requestedMax = Math.max(0, cmd.optDouble("max_total", 0));
        double maxTotal = requestedMax > 0 ? requestedMax : Math.max(0, card.optDouble("checkout_max", 0));
        s.put("max_total", WalletState.round2(maxTotal));
        s.put("strict_budget", cmd.optBoolean("strict_budget", false) || card.optBoolean("strict_budget", false));
        s.put("submit_order", cmd.has("submit_order") ? cmd.optBoolean("submit_order", true) : true);
        s.put("target", "payment_page");
        s.put("started_at_ms", now);
        s.put("started_at_local", formatLocal(now));
        s.put("updated_at_ms", now);
        s.put("updated_at_local", formatLocal(now));
        s.put("deadline_at_ms", now + timeoutSeconds * 1000L);
        s.put("scroll_count", 0);
        s.put("attempts", 0);
        s.put("note_applied", s.optString("note", "").trim().length() == 0);
        saveCheckoutSession(ctx, s);
        TakeoutCheckoutAutomation.start(ctx, s);
        DebugState.append(ctx, "外卖自动点单启动：" + s.optString("title") + "，目标=付款页");
        return new JSONObject().put("ok", true).put("started", true).put("session", s).put("note", "整单已交给手机本地自动执行；最终支付按钮不会被点击。");
    }

    public static JSONObject getCheckoutStatus(Context ctx) throws Exception {
        JSONObject s = checkoutSession(ctx);
        return new JSONObject().put("ok", true).put("session", s).put("active", "running".equals(s.optString("status")));
    }

    public static JSONObject cancelCheckout(Context ctx, String reason) throws Exception {
        TakeoutCheckoutAutomation.cancel(ctx, reason == null || reason.length() == 0 ? "user_cancelled" : reason);
        return new JSONObject().put("ok", true).put("session", checkoutSession(ctx));
    }

    public static JSONArray cards(Context ctx) { try { return new JSONArray(prefs(ctx).getString(KEY_CARDS, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private static void saveCards(Context ctx, JSONArray arr) { prefs(ctx).edit().putString(KEY_CARDS, arr == null ? "[]" : arr.toString()).apply(); }
    static double mealBudget(Context ctx) { return prefs(ctx).getFloat(KEY_MEAL_BUDGET, 25f); }
    private static double dayBudget(Context ctx) { return prefs(ctx).getFloat(KEY_DAY_BUDGET, 45f); }
    private static String tasteNote(Context ctx) { return prefs(ctx).getString(KEY_TASTE, "少油、别太贵、尽量吃热饭"); }
    private static JSONObject lastPlan(Context ctx) { try { return new JSONObject(prefs(ctx).getString(KEY_LAST_PLAN, "{}")); } catch (Exception e) { return new JSONObject(); } }
    public static JSONObject checkoutSession(Context ctx) { try { return new JSONObject(prefs(ctx).getString(KEY_CHECKOUT_SESSION, "{}")); } catch (Exception e) { return new JSONObject(); } }
    public static void saveCheckoutSession(Context ctx, JSONObject session) { prefs(ctx).edit().putString(KEY_CHECKOUT_SESSION, session == null ? "{}" : session.toString()).apply(); }

    private static JSONObject findCardExact(Context ctx, String id) throws Exception {
        if (id == null || id.length() == 0) return null;
        JSONArray arr = cards(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c != null && id.equals(c.optString("id"))) return c;
        }
        return null;
    }

    static JSONObject findCard(Context ctx, String id, String query) throws Exception {
        JSONArray arr = cards(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c != null && id.length() > 0 && id.equals(c.optString("id"))) return c;
        }
        if (query == null) query = "";
        query = query.trim();
        if (query.length() > 0) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                if (c != null && matchCard(c, query)) return c;
            }
        }
        return arr.length() > 0 ? arr.optJSONObject(0) : null;
    }

    private static boolean containsId(JSONArray arr, String id) {
        if (id == null || id.length() == 0) return false;
        for (int i = 0; i < arr.length(); i++) if (id.equals(arr.optJSONObject(i) == null ? "" : arr.optJSONObject(i).optString("id"))) return true;
        return false;
    }

    private static boolean matchCard(JSONObject c, String query) {
        String s = (c.optString("title") + " " + c.optString("items") + " " + c.optString("item_query") + " " + c.optString("tags") + " " + c.optString("note") + " " + c.optString("platform") + " " + c.optString("aliases") + " " + c.optString("direct_link")).toLowerCase(Locale.US);
        for (String part : query.toLowerCase(Locale.US).split("\\s+")) if (part.length() > 0 && s.contains(part)) return true;
        return query.length() == 0;
    }

    private static double priceMax(JSONObject c) { double max = c.optDouble("price_max", 0); return max > 0 ? max : c.optDouble("price_min", 0); }
    private static double estimate(JSONObject c, double fallback) { double min = c.optDouble("price_min", 0), max = priceMax(c); if (min > 0 && max > 0) return WalletState.round2((min + max) / 2.0); if (max > 0) return WalletState.round2(max); return WalletState.round2(Math.max(0, fallback)); }
    private static String reasonFor(JSONObject c, boolean budgetOk, double budget) { return budgetOk ? "在单餐预算内，适合快速下单。" : "可能超过单餐预算 ¥" + WalletState.money(budget) + "，可先走小金库申请。"; }
    private static String guessPlatform(String link) { String s = link == null ? "" : link.toLowerCase(Locale.US); if (s.contains("3.cn") || s.contains("jd.com") || s.contains("jingdong")) return "京东"; if (s.contains("meituan")) return "美团"; if (s.contains("ele") || s.contains("eleme")) return "饿了么"; return "外卖平台"; }
    public static String normalizeLink(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        Matcher m = URL_PATTERN.matcher(s);
        if (m.find()) {
            String link = m.group(1).trim();
            while (link.endsWith("。") || link.endsWith("，") || link.endsWith(",") || link.endsWith("；") || link.endsWith(";") || link.endsWith(")") || link.endsWith("）") || link.endsWith("]") || link.endsWith("】")) link = link.substring(0, link.length() - 1);
            return link;
        }
        return s;
    }
    private static boolean supportedLink(String link) { return link.startsWith("http://") || link.startsWith("https://") || link.startsWith("meituan://") || link.startsWith("eleme://"); }
    private static JSONArray normalizeChoices(Object value) {
        JSONArray out = new JSONArray();
        try {
            if (value instanceof JSONArray) {
                JSONArray a = (JSONArray)value;
                for (int i = 0; i < a.length(); i++) { String v = a.optString(i, "").trim(); if (v.length() > 0) out.put(v); }
            } else if (value != null && value != JSONObject.NULL) {
                String s = String.valueOf(value).trim();
                if (s.startsWith("[") && s.endsWith("]")) {
                    try { return normalizeChoices(new JSONArray(s)); } catch (Exception ignored) { }
                }
                for (String p : s.split("[|｜;；\\n]+")) { String v = p.trim(); if (v.length() > 0) out.put(v); }
            }
        } catch (Exception ignored) { }
        return out;
    }
    private static String clipboardText(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence t = clip.getItemAt(0).coerceToText(ctx);
            return t == null ? "" : t.toString();
        } catch (Exception e) { return ""; }
    }

    private static String clipboardLink(Context ctx) { return normalizeLink(clipboardText(ctx)); }

    public static String suggestTitleFromShareText(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        Matcher q = Pattern.compile("[「『\"“]([^」』\"”]{3,60})[」』\"”]").matcher(s);
        if (q.find()) {
            String v = q.group(1).trim().replaceFirst("^(?:京东外卖|美团外卖|饿了么)\\s*[|｜:：-]\\s*", "");
            if (v.length() >= 3) return v;
        }
        return "";
    }

    private static String inferMealTitleFromScreen() {
        try {
            ScreenshotService svc = ScreenshotService.getInstance();
            if (svc == null) return "";
            svc.refreshScreenModel();
            String text = ScreenshotService.screenText();
            if (text == null) return "";
            String best = "";
            for (String raw : text.split("\\|")) {
                String v = raw.trim();
                if (v.length() < 4 || v.length() > 46) continue;
                if (v.matches(".*[¥￥0-9]{2,}.*")) continue;
                if (v.contains("返回") || v.contains("分享") || v.contains("购物车") || v.contains("去结算") || v.contains("外卖") || v.contains("配送") || v.contains("商家") || v.contains("点评") || v.contains("已售")) continue;
                if (v.length() > best.length()) best = v;
            }
            return best;
        } catch (Exception e) { return ""; }
    }

    private static String value(JSONObject data, JSONObject old, String key, String alias, String fallback) {
        if (data != null && data.has(key)) return data.optString(key, fallback);
        if (alias != null && data != null && data.has(alias)) return data.optString(alias, fallback);
        if (old != null && old.has(key)) return old.optString(key, fallback);
        if (alias != null && old != null && old.has(alias)) return old.optString(alias, fallback);
        return fallback;
    }
    private static double number(JSONObject data, JSONObject old, String key, double fallback) {
        if (data != null && data.has(key)) return data.optDouble(key, fallback);
        if (old != null && old.has(key)) return old.optDouble(key, fallback);
        return fallback;
    }
    private static boolean bool(JSONObject data, JSONObject old, String key, boolean fallback) {
        if (data != null && data.has(key)) return data.optBoolean(key, fallback);
        if (old != null && old.has(key)) return old.optBoolean(key, fallback);
        return fallback;
    }
    private static String nonEmpty(String s, String fallback) { return s == null || s.trim().length() == 0 ? fallback : s.trim(); }
    static String formatLocal(long ms) { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(ms)); }
}
