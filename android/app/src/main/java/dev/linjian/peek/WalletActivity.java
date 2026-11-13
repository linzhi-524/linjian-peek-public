package dev.linjian.peek;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/** 掌心窗内的小金库模块：预算、记账、待审批、规则与统计。 */
public class WalletActivity extends Activity {
    private LinearLayout root, content;
    private TextView title, actionText, ruleText;
    private String page = "home";
    private String selectedMonth = WalletState.currentMonth();
    private boolean myApprovalsOpen = true;
    private boolean companionApprovalsOpen = true;
    private UITheme theme;
    private int bg, ink, sub, primary, primarySoft, cardColor, cardSoft, cardStroke;
    private final BroadcastReceiver walletUpdatedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refreshCurrentPage(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadTheme();
        buildRoot();
        showHome();
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            IntentFilter filter = new IntentFilter("dev.linjian.peek.WALLET_UPDATED");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(walletUpdatedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(walletUpdatedReceiver, filter);
        } catch (Exception ignored) { }
        UITheme latest = UITheme.current(this);
        if (theme == null || !theme.name.equals(latest.name)) {
            loadTheme();
            buildRoot();
        }
        if (selectedMonth == null || selectedMonth.length() == 0) selectedMonth = WalletState.currentMonth();
        refreshCurrentPage();
    }

    @Override protected void onPause() {
        try { unregisterReceiver(walletUpdatedReceiver); } catch (Exception ignored) { }
        super.onPause();
    }

    private void refreshCurrentPage() {
        if ("rules".equals(page)) showRules();
        else if ("stats".equals(page)) showStats();
        else if ("pending".equals(page)) showPending();
        else if ("add".equals(page)) showAdd("expense");
        else showHome();
    }

    private void loadTheme() {
        theme = UITheme.current(this);
        bg = theme.bgTop;
        ink = theme.text;
        sub = theme.subtext;
        primary = theme.primary;
        primarySoft = theme.primarySoft;
        cardColor = theme.card;
        cardSoft = theme.cardSoft;
        cardStroke = theme.line;
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(theme.background());
        root.setPadding(dp(16), dp(18), dp(16), dp(10));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(50)));

        Button back = tinyButton("‹");
        back.setTextSize(22);
        back.setOnClickListener(v -> { if ("home".equals(page)) finish(); else showHome(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        title = new TextView(this);
        title.setTextColor(ink);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -1, 1);
        tp.leftMargin = dp(8);
        header.addView(title, tp);

        ruleText = new TextView(this);
        ruleText.setText("⚙");
        ruleText.setTextColor(sub);
        ruleText.setTextSize(16);
        ruleText.setTypeface(Typeface.DEFAULT_BOLD);
        ruleText.setGravity(Gravity.CENTER);
        ruleText.setBackground(round(cardColor, dp(20), cardStroke, 1));
        ruleText.setOnClickListener(v -> showRules());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(42), dp(42));
        rp.rightMargin = dp(8);
        header.addView(ruleText, rp);

        actionText = new TextView(this);
        actionText.setTextColor(ink);
        actionText.setTextSize(17);
        actionText.setTypeface(Typeface.DEFAULT_BOLD);
        actionText.setGravity(Gravity.CENTER);
        actionText.setBackground(round(cardColor, dp(20), cardStroke, 1));
        header.addView(actionText, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ScrollView sv = new ScrollView(this);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(18));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    private void reset(String t, String action, View.OnClickListener listener) {
        title.setText(t);
        title.setOnClickListener(null);
        actionText.setText(action == null ? "" : action);
        actionText.setVisibility(action == null || action.length() == 0 ? View.INVISIBLE : View.VISIBLE);
        actionText.setOnClickListener(listener);
        ruleText.setVisibility("home".equals(page) ? View.VISIBLE : View.INVISIBLE);
        content.removeAllViews();
    }

    private void showHome() {
        page = "home";
        reset("小金库", "+", v -> showAdd("expense"));
        title.setText("小金库 · " + monthShort(selectedMonth) + " ▾");
        title.setOnClickListener(v -> showMonthDrawer());
        try {
            JSONObject s = WalletState.collect(this, selectedMonth);
            LinearLayout hero = card();
            hero.setBackground(theme.hero());

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            hero.addView(topRow, new LinearLayout.LayoutParams(-1, dp(30)));
            TextView tag = small(s.optBoolean("is_current_month", true) ? "本月守钱中" : s.optString("month_label", selectedMonth));
            tag.setTextColor(sub);
            topRow.addView(tag, new LinearLayout.LayoutParams(0, -1, 1));
            TextView walletMark = text("¥", 22, true);
            walletMark.setGravity(Gravity.CENTER);
            walletMark.setTextColor(withAlpha(primary, theme.dark ? 125 : 82));
            walletMark.setBackground(round(primarySoft, dp(16), 0, 0));
            topRow.addView(walletMark, new LinearLayout.LayoutParams(dp(34), dp(34)));

            TextView spent = big("¥ " + WalletState.money(s.optDouble("spent", 0)));
            hero.addView(spent);
            TextView detail = small("预算 ¥" + WalletState.money(s.optDouble("monthly_budget", 0)) + "  ·  剩余 ¥" + WalletState.money(s.optDouble("remaining", 0)) + "  ·  已省 ¥" + WalletState.money(s.optDouble("saved_estimate", 0)));
            detail.setTextColor(sub);
            hero.addView(detail);
            hero.addView(progress(s.optDouble("spent", 0), Math.max(1, s.optDouble("monthly_budget", 1))));
            content.addView(hero);

            LinearLayout quick = new LinearLayout(this);
            quick.setOrientation(LinearLayout.HORIZONTAL);
            quick.setGravity(Gravity.CENTER);
            quick.setPadding(0, dp(4), 0, dp(4));
            content.addView(quick, new LinearLayout.LayoutParams(-1, dp(58)));
            quick.addView(actionButton("✎  记一笔", v -> showAdd("expense")), weightLp(1, 0, 4));
            quick.addView(actionButton("◇  申请花钱", v -> showAdd("approval")), weightLp(1, 4, 4));
            quick.addView(actionButton("▥  统计", v -> showStats()), weightLp(1, 4, 0));

            addSectionTitle("审批列表", "查看全部", v -> showPending());
            JSONArray approvals = s.optJSONArray("approval_records");
            JSONArray pending = s.optJSONArray("pending_records");
            int shownApproval = 0;
            if (approvals != null) for (int i = 0; i < approvals.length() && shownApproval < 2; i++) {
                content.addView(approvalCard(approvals.optJSONObject(i)));
                shownApproval++;
            }
            if (shownApproval == 0 && pending != null && pending.length() > 0) content.addView(recordCard(pending.optJSONObject(0), true));
            if (shownApproval == 0 && (pending == null || pending.length() == 0)) content.addView(emptyCard("暂无审批记录", "◇"));

            addSectionTitle("最近消费", "统计", v -> showStats());
            JSONArray recent = s.optJSONArray("recent_records");
            if (recent == null || recent.length() == 0) content.addView(emptyCard("还没有账单，点右上角 + 记第一笔。", "¥"));
            else for (int i = 0; i < Math.min(6, recent.length()); i++) content.addView(recordCard(recent.optJSONObject(i), false));
        } catch (Exception e) { content.addView(emptyCard("小金库加载失败：" + ScreenshotService.shortMsg(e), "!")); }
    }

    private void showAdd(String mode) {
        page = "add";
        reset("记一笔", "✓", null);
        LinearLayout form = card();
        EditText amount = input("金额，例如 18.00", true); form.addView(amount);
        EditText category = input("分类：饮品 / 饮食 / 购物 / 交通 / 学习 / 娱乐 / 其他", false); form.addView(category);
        EditText note = input("备注或商品名", false); form.addView(note);
        EditText necessity = input("必要程度 1-5（申请花钱时填写）", true); form.addView(necessity);
        EditText impulse = input("冲动程度 1-5（申请花钱时填写）", true); form.addView(impulse);
        content.addView(form);

        LinearLayout modes = new LinearLayout(this); modes.setOrientation(LinearLayout.HORIZONTAL); modes.setPadding(0, dp(8), 0, 0); content.addView(modes, new LinearLayout.LayoutParams(-1, dp(54)));
        final String[] current = new String[]{mode};
        Button bExpense = actionButton("支出", v -> { current[0] = "expense"; toast("支出模式"); });
        Button bIncome = actionButton("收入", v -> { current[0] = "income"; toast("收入模式"); });
        Button bApproval = actionButton("审批", v -> { current[0] = "approval"; toast("审批模式"); });
        modes.addView(bExpense, weightLp(1,0,4)); modes.addView(bIncome, weightLp(1,4,4)); modes.addView(bApproval, weightLp(1,4,0));

        actionText.setOnClickListener(v -> {
            try {
                double a = Double.parseDouble(amount.getText().toString().trim());
                JSONObject cmd = new JSONObject();
                if ("approval".equals(current[0])) {
                    cmd.put("action", "submit_wallet_approval"); cmd.put("amount", a); cmd.put("item", note.getText().toString()); cmd.put("note", note.getText().toString()); cmd.put("category", category.getText().toString());
                    cmd.put("necessity", parseInt(necessity.getText().toString(), 3)); cmd.put("impulse", parseInt(impulse.getText().toString(), 3));
                    WalletState.submitApprovalRequest(this, cmd);
                    toast("已提交，等待处理");
                    showPending();
                } else {
                    cmd.put("action", "add_wallet_record"); cmd.put("amount", a); cmd.put("type", current[0]); cmd.put("category", category.getText().toString()); cmd.put("note", note.getText().toString()); cmd.put("source", "manual");
                    WalletState.addRecord(this, cmd, "confirmed"); toast("已记入小金库"); showHome();
                }
            } catch (Exception ex) { toast("金额要填数字哦"); }
        });
    }

    private void showPending() {
        page = "pending";
        reset("审批列表", "", null);
        JSONObject state = WalletState.collect(this, selectedMonth);
        JSONArray approvals = state.optJSONArray("approval_records");
        JSONArray arr = state.optJSONArray("pending_records");
        int myCount = countApprovals(approvals, "user");
        int companionCount = countApprovals(approvals, "companion");
        boolean any = false;

        addFoldTitle("我的申请", myCount, myApprovalsOpen, v -> { myApprovalsOpen = !myApprovalsOpen; showPending(); });
        if (myApprovalsOpen) {
            int shown = addApprovalCardsForRole(approvals, "user");
            if (shown == 0) content.addView(emptyCard("暂无我的申请。", "◇"));
            any = any || shown > 0;
        }

        addFoldTitle(companionName() + "的申请", companionCount, companionApprovalsOpen, v -> { companionApprovalsOpen = !companionApprovalsOpen; showPending(); });
        if (companionApprovalsOpen) {
            int shown = addApprovalCardsForRole(approvals, "companion");
            if (shown == 0) content.addView(emptyCard("暂无" + companionName() + "的申请。", "◇"));
            any = any || shown > 0;
        }

        if (arr != null && arr.length() > 0) {
            addSectionTitle("待确认账单", "", null);
            for (int i = 0; i < arr.length(); i++) { content.addView(recordCard(arr.optJSONObject(i), true)); any = true; }
        }
        if (!any) content.addView(emptyCard("暂无审批记录。", "◇"));
    }

    private void showRules() {
        page = "rules";
        reset("预算规则", "✓", null);
        JSONObject rules = WalletState.rules(this);
        LinearLayout form = card();
        EditText budget = input("本月预算", true); budget.setText(String.valueOf(rules.optDouble("monthly_budget", 1200))); form.addView(budget);
        EditText threshold = input("单笔审批线", true); threshold.setText(String.valueOf(rules.optDouble("approval_threshold", 50))); form.addView(threshold);
        EditText mode = input("自动识别模式：conservative / normal / strong", false); mode.setText(rules.optString("auto_mode", "conservative")); form.addView(mode);
        EditText limits = input("分类上限，例如 奶茶:80,外卖:300", false); limits.setText(rules.optString("category_limits", "")); form.addView(limits);
        content.addView(form);
        content.addView(actionButton("打开通知读取权限", v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))));
        actionText.setOnClickListener(v -> {
            try { JSONObject c = new JSONObject(); c.put("monthly_budget", Double.parseDouble(budget.getText().toString())); c.put("approval_threshold", Double.parseDouble(threshold.getText().toString())); c.put("auto_mode", mode.getText().toString().trim()); c.put("category_limits", limits.getText().toString()); WalletState.setRules(this, c); toast("规则已保存"); showHome(); }
            catch(Exception e) { toast("预算和审批线要填数字"); }
        });
    }

    private void showStats() {
        page = "stats";
        reset("统计报告", "", null);
        JSONObject s = WalletState.collect(this, selectedMonth);
        LinearLayout hero = card();
        hero.setBackground(theme.hero());
        hero.addView(big("¥ " + WalletState.money(s.optDouble("spent", 0))));
        hero.addView(small(s.optString("month_label", "本月") + "支出 · 剩余 ¥" + WalletState.money(s.optDouble("remaining", 0)) + " · 已省 ¥" + WalletState.money(s.optDouble("saved_estimate", 0))));
        content.addView(hero);
        addSectionTitle("分类排行", "", null);
        JSONObject cats = s.optJSONObject("category_totals");
        if (cats == null || cats.length() == 0) content.addView(emptyCard("本月还没有分类统计。", "▥"));
        else {
            Iterator<String> it = cats.keys();
            while (it.hasNext()) {
                String k = it.next();
                LinearLayout row = cardRow(k, "¥ " + WalletState.money(cats.optDouble(k, 0)), "");
                content.addView(row);
            }
        }

        addSectionTitle("账单明细", "", null);
        JSONArray monthRecords = WalletState.recordsForMonth(this, selectedMonth);
        if (monthRecords == null || monthRecords.length() == 0) {
            content.addView(emptyCard("这个月还没有已入账账单。", "¥"));
        } else {
            TextView tip = small("左滑或长按任一账单，可重新编辑或删除。共 " + monthRecords.length() + " 笔");
            tip.setPadding(dp(2), 0, 0, dp(8));
            content.addView(tip);
            for (int i = 0; i < monthRecords.length(); i++) {
                content.addView(recordCard(monthRecords.optJSONObject(i), false));
            }
        }
    }


    private void showMonthDrawer() {
        try {
            JSONObject state = WalletState.collect(this, selectedMonth);
            JSONArray months = state.optJSONArray("month_summaries");
            if (months == null || months.length() == 0) months = new JSONArray().put(WalletState.collect(this, WalletState.currentMonth()));

            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            wrap.setBackgroundColor(withAlpha(primary, theme.dark ? 60 : 35));

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(18), dp(22), dp(14), dp(18));
            panel.setBackground(round(cardColor, dp(28), cardStroke, 1));
            panel.setOnClickListener(v -> {});
            int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.74f);
            wrap.addView(panel, new LinearLayout.LayoutParams(width, -1));

            TextView h = text("历史月份", 20, true);
            panel.addView(h, new LinearLayout.LayoutParams(-1, dp(34)));
            TextView desc = small("按月份查看预算、支出和明细");
            LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, dp(28));
            dpv.bottomMargin = dp(10);
            panel.addView(desc, dpv);

            ScrollView sv = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            sv.addView(list, new ScrollView.LayoutParams(-1, -2));
            panel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

            final PopupWindow[] holder = new PopupWindow[1];
            for (int i = 0; i < months.length(); i++) {
                JSONObject m = months.optJSONObject(i);
                if (m == null) continue;
                String key = m.optString("month", WalletState.currentMonth());
                LinearLayout item = card();
                item.setPadding(dp(14), dp(12), dp(14), dp(12));
                boolean active = key.equals(selectedMonth);
                item.setBackground(round(active ? primarySoft : cardSoft, dp(20), active ? primary : cardStroke, active ? 2 : 1));
                TextView name = text((m.optBoolean("is_current_month") ? "本月 · " : "") + WalletState.monthLabel(key), 15, true);
                item.addView(name);
                item.addView(small("已花 ¥" + WalletState.money(m.optDouble("spent", 0)) + " · " + m.optInt("count", 0) + " 笔 · 剩余 ¥" + WalletState.money(m.optDouble("remaining", 0))));
                item.setOnClickListener(v -> { selectedMonth = key; if (holder[0] != null) holder[0].dismiss(); showHome(); });
                list.addView(item);
            }

            TextView closeArea = new TextView(this);
            closeArea.setText("");
            wrap.addView(closeArea, new LinearLayout.LayoutParams(0, -1, 1));
            PopupWindow pw = new PopupWindow(wrap, -1, -1, true);
            holder[0] = pw;
            closeArea.setOnClickListener(v -> pw.dismiss());
            wrap.setOnClickListener(v -> pw.dismiss());
            pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            pw.setOutsideTouchable(true);
            pw.showAtLocation(root, Gravity.START | Gravity.TOP, 0, 0);
        } catch (Exception e) { toast("月份抽屉打开失败"); }
    }

    private LinearLayout approvalCard(JSONObject r) {
        if (r == null) return emptyCard("审批读取失败", "!");
        LinearLayout box = card();
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); box.addView(row);
        TextView icon = new TextView(this); icon.setText("审"); icon.setTextSize(16); icon.setTextColor(ink); icon.setTypeface(Typeface.DEFAULT_BOLD); icon.setGravity(Gravity.CENTER); icon.setBackground(round(primarySoft, dp(18), 0, 0)); row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout mid = new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1); mp.leftMargin = dp(10); row.addView(mid, mp);
        TextView name = text(statusLabel(r) + "｜" + r.optString("category", "其他"), 14, true); mid.addView(name);
        TextView note = small(r.optString("item", r.optString("note", "这笔申请"))); note.setMaxLines(1); mid.addView(note);
        TextView money = text("¥" + WalletState.money(r.optDouble("amount",0)), 14, true); row.addView(money);
        TextView msg = small(r.optString("approval_message", defaultApprovalWaitingText(r))); msg.setPadding(dp(52), dp(8), 0, 0); box.addView(msg);
        box.setOnClickListener(v -> showApprovalDetail(r));
        return box;
    }

    private void showApprovalDetail(JSONObject r) {
        if (r == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("金额：¥").append(WalletState.money(r.optDouble("amount", 0))).append("\n");
        sb.append("分类：").append(r.optString("category", "其他")).append("\n");
        sb.append("用途：").append(r.optString("item", r.optString("note", "这笔申请"))).append("\n");
        sb.append("必要程度：").append(r.optInt("necessity", 3)).append("/5\n");
        sb.append("冲动程度：").append(r.optInt("impulse", 3)).append("/5\n");
        sb.append("发起：").append(displayRequesterName(r)).append("\n");
        sb.append("处理：").append(displayApproverName(r)).append("\n");
        sb.append("状态：").append(statusLabel(r)).append("\n\n");
        sb.append("处理备注：\n").append(r.optString("approval_message", defaultApprovalWaitingText(r)));
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("审批详情").setMessage(sb.toString());
        if (canUserHandleApproval(r)) {
            builder.setPositiveButton("通过", (d, w) -> askApprovalReason(r, "ok"));
            builder.setNeutralButton("暂缓", (d, w) -> askApprovalReason(r, "hold"));
            builder.setNegativeButton("驳回", (d, w) -> askApprovalReason(r, "no"));
        } else {
            builder.setPositiveButton("知道了", null);
        }
        builder.show();
    }

    private void askApprovalReason(JSONObject r, String status) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(14), dp(6), dp(14), 0);
        EditText reason = input(reasonHint(status), false);
        reason.setSingleLine(false);
        reason.setMinLines(3);
        form.addView(reason, new LinearLayout.LayoutParams(-1, dp(100)));
        new AlertDialog.Builder(this)
                .setTitle(decisionTitle(status))
                .setMessage("写一句处理理由，会显示在审批列表里，也方便陪伴者通过 MCP 看到你的回复。")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> handleApprovalDecision(r, status, reason.getText().toString().trim()))
                .show();
    }

    private String reasonHint(String status) {
        if ("no".equals(status)) return "写下驳回理由，例如：今天预算不够，先不买。";
        if ("hold".equals(status)) return "写下暂缓理由，例如：晚点再看，先缓一缓。";
        return "写下通过理由，例如：可以，记得别超预算。";
    }

    private String decisionTitle(String status) {
        if ("no".equals(status)) return "驳回理由";
        if ("hold".equals(status)) return "暂缓理由";
        return "通过理由";
    }

    private boolean canUserHandleApproval(JSONObject r) {
        if (r == null) return false;
        boolean pending = "approval_pending".equals(r.optString("status")) || "waiting".equals(r.optString("decision"));
        return pending && "companion".equals(requesterRole(r));
    }

    private void handleApprovalDecision(JSONObject r, String status, String reason) {
        try {
            String note = reason == null ? "" : reason.trim();
            if (note.length() == 0) {
                if ("no".equals(status)) note = "已驳回。";
                else if ("hold".equals(status)) note = "先暂缓，晚点再处理。";
                else note = "已通过。";
            }
            WalletState.decideApproval(this, new JSONObject()
                    .put("id", r.optString("id"))
                    .put("status", status)
                    .put("note", note)
                    .put("user_reason", note)
                    .put("approved_by", AppPrefs.userName(this)));
            toast("已处理");
            showPending();
        } catch (Exception e) { toast("处理失败"); }
    }

    private String statusLabel(JSONObject r) {
        String status = r == null ? "" : r.optString("status", "");
        if ("approval_approved".equals(status)) return "已通过";
        if ("approval_rejected".equals(status)) return "已驳回";
        if ("approval_delayed".equals(status)) return "暂缓";
        return "companion".equals(requesterRole(r)) ? "等待你处理" : "等待" + companionName() + "审批";
    }

    private String requesterRole(JSONObject r) {
        if (r == null) return "user";
        String role = r.optString("requester_role", "");
        if ("companion".equals(role)) return "companion";
        return "user";
    }

    private int countApprovals(JSONArray approvals, String role) {
        if (approvals == null) return 0;
        int n = 0;
        for (int i = 0; i < approvals.length(); i++) if (role.equals(requesterRole(approvals.optJSONObject(i)))) n++;
        return n;
    }

    private int addApprovalCardsForRole(JSONArray approvals, String role) {
        if (approvals == null) return 0;
        int n = 0;
        for (int i = 0; i < approvals.length(); i++) {
            JSONObject r = approvals.optJSONObject(i);
            if (r == null || !role.equals(requesterRole(r))) continue;
            content.addView(approvalCard(r));
            n++;
        }
        return n;
    }

    private void addFoldTitle(String label, int count, boolean open, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(7));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setOnClickListener(listener);
        content.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView l = text(label, 14, true);
        row.addView(l, new LinearLayout.LayoutParams(0, -1, 1));
        TextView r = text(count + " 条  " + (open ? "⌃" : "⌄"), 12, true);
        r.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        r.setTextColor(sub);
        row.addView(r, new LinearLayout.LayoutParams(dp(112), -1));
    }

    private String displayRequesterName(JSONObject r) {
        if ("companion".equals(requesterRole(r))) return r.optString("requester_name", companionName());
        return r == null ? AppPrefs.userName(this) : r.optString("requester_name", AppPrefs.userName(this));
    }

    private String displayApproverName(JSONObject r) {
        if (r == null) return companionName();
        String role = r.optString("approver_role", "companion");
        return "user".equals(role) ? r.optString("approver_name", AppPrefs.userName(this)) : r.optString("approver_name", companionName());
    }

    private String defaultApprovalWaitingText(JSONObject r) {
        return "companion".equals(requesterRole(r)) ? companionName() + "提交给你处理，等待回复。" : "已提交给" + companionName() + "审批，等待回复。";
    }

    private LinearLayout recordCard(JSONObject r, boolean pending) {
        if (r == null) return emptyCard("记录读取失败", "!");
        LinearLayout box = card();
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); box.addView(row);
        TextView icon = new TextView(this); icon.setText(iconFor(r.optString("category"))); icon.setTextSize(18); icon.setTextColor(ink); icon.setGravity(Gravity.CENTER); icon.setBackground(round(primarySoft, dp(18), 0, 0)); row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout mid = new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1); mp.leftMargin = dp(10); row.addView(mid, mp);
        TextView name = text(r.optString("category", "其他") + (r.optString("merchant").isEmpty()?"":" · "+r.optString("merchant")), 14, true); mid.addView(name);
        TextView note = small(r.optString("note", r.optString("created_at_local", ""))); note.setMaxLines(1); mid.addView(note);
        TextView money = text(("income".equals(r.optString("type"))?"+":"-") + "¥" + WalletState.money(r.optDouble("amount",0)), 14, true); row.addView(money);
        if (pending) {
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(0, dp(10), 0, 0); box.addView(actions);
            actions.addView(actionButton("计入", v -> handlePending(r, "confirm")), weightLp(1,0,4));
            actions.addView(actionButton("忽略", v -> handlePending(r, "ignore")), weightLp(1,4,4));
            actions.addView(actionButton("修改", v -> editPending(r)), weightLp(1,4,0));
        } else {
            attachSwipeActions(box, r);
        }
        return box;
    }

    private void attachSwipeActions(LinearLayout box, JSONObject r) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] longPressed = new boolean[1];
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public void onLongPress(MotionEvent e) {
                longPressed[0] = true;
                showRecordSwipeActions(box, r);
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (dx < -dp(44) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    showRecordSwipeActions(box, r);
                    return true;
                }
                return false;
            }
        });
        box.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX[0] = ev.getX();
                downY[0] = ev.getY();
                longPressed[0] = false;
            }
            boolean handled = detector.onTouchEvent(ev);
            if (ev.getActionMasked() == MotionEvent.ACTION_UP && !longPressed[0]) {
                float dx = ev.getX() - downX[0];
                float dy = ev.getY() - downY[0];
                // 不只依赖 fling：慢速左滑也能稳定展开操作。
                if (dx < -dp(36) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    showRecordSwipeActions(box, r);
                    handled = true;
                }
            }
            return handled;
        });
    }

    private void showRecordSwipeActions(LinearLayout box, JSONObject r) {
        if ("actions_open".equals(box.getTag())) return;
        box.setTag("actions_open");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);
        box.addView(actions);
        actions.addView(actionButton("重新编辑", v -> editRecord(r)), weightLp(1,0,4));
        actions.addView(actionButton("删除", v -> confirmDeleteRecord(r)), weightLp(1,4,0));
    }

    private void editRecord(JSONObject r) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(14), dp(6), dp(14), 0);
        EditText amount = input("金额", true); amount.setText(String.valueOf(r.optDouble("amount", 0))); form.addView(amount);
        EditText category = input("分类", false); category.setText(r.optString("category", "其他")); form.addView(category);
        EditText merchant = input("商家", false); merchant.setText(r.optString("merchant", "")); form.addView(merchant);
        EditText note = input("备注", false); note.setText(r.optString("note", "")); form.addView(note);
        new AlertDialog.Builder(this).setTitle("重新编辑账单").setView(form).setNegativeButton("取消", null).setPositiveButton("保存", (d,w)->{
            try {
                WalletState.updateRecord(this, new JSONObject()
                        .put("id", r.optString("id"))
                        .put("amount", Double.parseDouble(amount.getText().toString()))
                        .put("category", category.getText().toString())
                        .put("merchant", merchant.getText().toString())
                        .put("note", note.getText().toString()));
                toast("已保存");
                refreshCurrentPage();
            } catch(Exception e) { toast("保存失败"); }
        }).show();
    }

    private void confirmDeleteRecord(JSONObject r) {
        new AlertDialog.Builder(this)
                .setTitle("删除这笔账单？")
                .setMessage(r.optString("note", r.optString("merchant", "这笔账单")) + " · ¥" + WalletState.money(r.optDouble("amount", 0)))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d,w)->{
                    try { WalletState.deleteRecord(this, r.optString("id")); toast("已删除"); refreshCurrentPage(); }
                    catch(Exception e) { toast("删除失败"); }
                }).show();
    }

    private void handlePending(JSONObject r, String decision) {
        try { WalletState.confirmRecord(this, new JSONObject().put("id", r.optString("id")).put("decision", decision)); toast("已处理"); showPending(); } catch (Exception e) { toast("处理失败"); }
    }

    private void editPending(JSONObject r) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(14), dp(6), dp(14), 0);
        EditText amount = input("金额", true); amount.setText(String.valueOf(r.optDouble("amount", 0))); form.addView(amount);
        EditText category = input("分类", false); category.setText(r.optString("category", "其他")); form.addView(category);
        EditText note = input("备注", false); note.setText(r.optString("note", "")); form.addView(note);
        new AlertDialog.Builder(this).setTitle("修改账单").setView(form).setNegativeButton("取消", null).setPositiveButton("计入", (d,w)->{
            try { WalletState.confirmRecord(this, new JSONObject().put("id", r.optString("id")).put("decision", "confirm").put("amount", Double.parseDouble(amount.getText().toString())).put("category", category.getText().toString()).put("note", note.getText().toString())); showPending(); } catch(Exception e) { toast("修改失败"); }
        }).show();
    }

    private void addSectionTitle(String left, String right, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(14), 0, dp(7)); row.setOrientation(LinearLayout.HORIZONTAL); content.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView l = text(left, 14, true); row.addView(l, new LinearLayout.LayoutParams(0, -1, 1));
        if (right != null && right.length() > 0) { TextView r = text(right + " ›", 12, true); r.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT); r.setTextColor(sub); r.setOnClickListener(listener); row.addView(r, new LinearLayout.LayoutParams(dp(96), -1)); }
    }

    private LinearLayout card() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16), dp(14), dp(16), dp(14)); l.setBackground(round(cardColor, dp(24), cardStroke, 1)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(10); l.setLayoutParams(lp); return l; }
    private LinearLayout emptyCard(String msg, String iconText) { LinearLayout l = card(); l.setGravity(Gravity.CENTER); l.setPadding(dp(16), dp(12), dp(16), dp(12)); TextView icon = text(iconText, 18, true); icon.setTextColor(withAlpha(primary, theme.dark ? 150 : 110)); icon.setGravity(Gravity.CENTER); icon.setBackground(round(primarySoft, dp(17), 0, 0)); LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(34), dp(34)); ip.bottomMargin = dp(4); l.addView(icon, ip); TextView t = small(msg); t.setGravity(Gravity.CENTER); l.addView(t, new LinearLayout.LayoutParams(-1, dp(30))); return l; }
    private LinearLayout cardRow(String name, String right, String subtext) { LinearLayout l = card(); l.addView(text(name + "    " + right, 14, true)); if (subtext.length()>0) l.addView(small(subtext)); return l; }
    private TextView text(String s, int sp, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(ink); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView small(String s) { TextView v = text(s, 11, false); v.setTextColor(sub); v.setLineSpacing(dp(2), 1f); return v; }
    private TextView big(String s) { TextView v = text(s, 28, true); v.setPadding(0, dp(6), 0, dp(4)); return v; }
    private Button tinyButton(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(ink); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round(cardColor, dp(18), cardStroke, 1)); return b; }
    private Button actionButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextSize(12); b.setTextColor(ink); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setBackground(round(cardColor, dp(20), cardStroke, 1)); b.setOnClickListener(l); return b; }
    private EditText input(String hint, boolean number) { EditText e = new EditText(this); e.setHint(hint); e.setTextSize(12); e.setSingleLine(true); e.setTextColor(ink); e.setHintTextColor(withAlpha(sub, 165)); e.setPadding(dp(12), 0, dp(12), 0); e.setBackground(round(cardSoft, dp(16), cardStroke, 1)); if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46)); lp.bottomMargin = dp(8); e.setLayoutParams(lp); return e; }
    private View progress(double used, double total) { LinearLayout outer = new LinearLayout(this); outer.setBackground(round(primarySoft, dp(999), 0, 0)); LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(8)); op.topMargin = dp(12); outer.setLayoutParams(op); View bar = new View(this); bar.setBackground(round(primary, dp(999), 0, 0)); double ratio = Math.max(0, Math.min(1, used / Math.max(1, total))); outer.post(() -> outer.addView(bar, new LinearLayout.LayoutParams((int)(outer.getWidth()*ratio), dp(8)))); return outer; }
    private LinearLayout.LayoutParams weightLp(float w, int left, int right) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w); lp.leftMargin = dp(left); lp.rightMargin = dp(right); return lp; }
    private GradientDrawable round(int color, int radius, int strokeColor, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (stroke > 0) g.setStroke(dp(stroke), strokeColor); return g; }
    private int withAlpha(int color, int alpha) { return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color)); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private int parseInt(String s, int d) { try { return Integer.parseInt(s.trim()); } catch(Exception e) { return d; } }
    private String monthShort(String month) { return WalletState.currentMonth().equals(month) ? "本月" : WalletState.monthLabel(month); }
    private String companionName() { return AppPrefs.companionName(this); }
    private String iconFor(String cat) { if (cat.contains("饮")) return "☕"; if (cat.contains("购物")) return "袋"; if (cat.contains("交通")) return "车"; if (cat.contains("学习")) return "书"; if (cat.contains("医疗")) return "药"; if (cat.contains("娱乐")) return "乐"; return "¥"; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
