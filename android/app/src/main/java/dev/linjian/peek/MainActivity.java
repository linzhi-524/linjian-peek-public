package dev.linjian.peek;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

public class MainActivity extends Activity {
    private static final String APP_VERSION_NAME = "0.3.4.1";
    private static final int APP_VERSION_CODE = 30401;
    private static final String DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/linzhi-524/linjian-peek-public/main/update.json";
    private int latestVersionCode = APP_VERSION_CODE;
    private String latestVersionName = APP_VERSION_NAME;
    private String latestApkUrl = "";
    private String latestChangelog = "";

    private TextView headerTitle, headerSubtitle, statusText, debugText, lifeStatusText, lifeSummaryText, knownAppsText, homeModeStatusText, gateStatusText;
    private TextView overviewBatteryText, overviewAppText, overviewScreenText, overviewWeatherText, overviewAdviceText, weatherLocationsText, themeText, versionStatusText, updateChangelogText;
    private Button toggleButton, accessibilityButton, usageAccessButton, testButton, openXhsButton, openChatGptButton, homeButton, backButton, recentsButton, alarmTestButton, notifyTestButton, refreshLifeButton;
    private Button addPackageButton, testPackageButton, sequenceTestButton, refreshGateButton, addGateAppButton, addWeatherLocationButton, setCurrentWeatherButton;
    private Button themeCreamButton, themeBlueButton, themePeachButton, themeNightButton, themeMintButton;
    private Button drawerConnectionButton, drawerPermissionButton, drawerControlTestButton, drawerKnownAppsButton, drawerHomeModeButton, drawerGateAddButton, drawerReminderButton, drawerCycleButton, drawerDebugButton, drawerLifeDetailsButton, drawerAppGateButton, drawerWeatherButton, drawerVersionButton, checkUpdateButton, downloadUpdateButton;
    private CheckBox remindersEnabled, batteryRuleEnabled, screenRuleEnabled, waterRuleEnabled, restRuleEnabled, cycleEnabled, foregroundPopupEnabled, homeModeEnabled, homeModeForceEnabled, appGateEnabled;
    private Button tabSettings, tabSee, tabControl, tabLife, tabGate, tabDebug;
    private View sectionSettings, sectionSee, sectionControl, sectionLife, sectionGate, sectionDebug;
    private View drawerConnection, drawerPermission, drawerControlTest, drawerKnownApps, drawerHomeMode, drawerGateAdd, drawerReminder, drawerCycle, drawerDebug, drawerAppGate, drawerWeather, drawerVersion;
    private EditText serverUrl, tokenInput, deviceInput, userNameInput, partnerNameInput, intervalInput, cityInput, weatherInput;
    private EditText weatherAliasInput, weatherCityInput, weatherNoteInput;
    private EditText batteryThresholdInput, screenThresholdInput, waterIntervalInput, restIntervalInput;
    private EditText lastPeriodStartInput, cycleLengthInput, periodLengthInput, cycleRemindBeforeInput;
    private EditText appAliasInput, appPackageInput, homeWatchPackagesInput, homeThresholdInput, homeCooldownInput, homeTargetInput, gateAliasInput, gatePackageInput;
    private boolean serviceRunning = false;
    private String currentTab = "life";
    private boolean weatherFetching = false;
    private long lastWeatherFetchAt = 0L;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable refreshTick = new Runnable() {
        @Override public void run() { serviceRunning = CompanionService.isRunning(); updateUI(); uiHandler.postDelayed(this, 1500); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareWindowForFullContent();
        setContentView(R.layout.activity_main);
        bindViews();
        repairFirstLayoutPass();
        loadSettings();

        DebugState.append(this, "掌心窗 v0.3.4.1-public 显示适配版已打开");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 13);
        serviceRunning = CompanionService.isRunning();
        updateUI();

        if (accessibilityButton != null) accessibilityButton.setOnClickListener(v -> openAccessibilitySettings());
        if (usageAccessButton != null) usageAccessButton.setOnClickListener(v -> openUsageAccessSettings());
        if (toggleButton != null) toggleButton.setOnClickListener(v -> { if (serviceRunning) stopCompanionService(); else startCompanionService(); });
        if (refreshLifeButton != null) refreshLifeButton.setOnClickListener(v -> { saveSettings(); updateUI(); Toast.makeText(this, "已刷新生活总览", Toast.LENGTH_SHORT).show(); });
        if (testButton != null) testButton.setOnClickListener(v -> testScreenshot());
        if (openXhsButton != null) openXhsButton.setOnClickListener(v -> openPackage(AppPrefs.packageForApp(this, "小红书")));
        if (openChatGptButton != null) openChatGptButton.setOnClickListener(v -> openPackage(AppPrefs.packageForApp(this, "ChatGPT")));
        if (homeButton != null) homeButton.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); toast(svc != null && svc.doHome()); });
        if (backButton != null) backButton.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); toast(svc != null && svc.doBack()); });
        if (recentsButton != null) recentsButton.setOnClickListener(v -> { ScreenshotService svc = ScreenshotService.getInstance(); toast(svc != null && svc.doRecents()); });
        if (alarmTestButton != null) alarmTestButton.setOnClickListener(v -> testAlarm());
        if (notifyTestButton != null) notifyTestButton.setOnClickListener(v -> testNotification());
        if (addPackageButton != null) addPackageButton.setOnClickListener(v -> addPackageAlias());
        if (testPackageButton != null) testPackageButton.setOnClickListener(v -> testCustomPackage());
        if (sequenceTestButton != null) sequenceTestButton.setOnClickListener(v -> testLocalSequence());
        if (refreshGateButton != null) refreshGateButton.setOnClickListener(v -> { updateUI(); Toast.makeText(this, "已刷新守护状态", Toast.LENGTH_SHORT).show(); });
        if (addGateAppButton != null) addGateAppButton.setOnClickListener(v -> addGateApp());
        if (addWeatherLocationButton != null) addWeatherLocationButton.setOnClickListener(v -> addWeatherLocation(false));
        if (setCurrentWeatherButton != null) setCurrentWeatherButton.setOnClickListener(v -> addWeatherLocation(true));
        if (checkUpdateButton != null) checkUpdateButton.setOnClickListener(v -> checkForUpdates(true));
        if (downloadUpdateButton != null) downloadUpdateButton.setOnClickListener(v -> downloadLatestApk());

        bindThemeButton(themeCreamButton, "奶油绿"); bindThemeButton(themeBlueButton, "雾蓝白"); bindThemeButton(themePeachButton, "白桃粉"); bindThemeButton(themeNightButton, "夜航黑"); bindThemeButton(themeMintButton, "薄荷透明");

        bindDrawer(drawerLifeDetailsButton, lifeStatusText, "展开详情");
        bindDrawer(drawerAppGateButton, drawerAppGate, "应用门禁");
        bindDrawer(drawerWeatherButton, drawerWeather, "天气地区");
        bindDrawer(drawerConnectionButton, drawerConnection, "连接设置");
        bindDrawer(drawerPermissionButton, drawerPermission, "权限与运行");
        bindDrawer(drawerControlTestButton, drawerControlTest, "本机测试抽屉");
        bindDrawer(drawerKnownAppsButton, drawerKnownApps, "应用包名抽屉");
        bindDrawer(drawerHomeModeButton, drawerHomeMode, "回家模式抽屉");
        bindDrawer(drawerGateAddButton, drawerGateAdd, "添加可锁 App");
        bindDrawer(drawerReminderButton, drawerReminder, "主动提醒规则");
        bindDrawer(drawerCycleButton, drawerCycle, "生理期提醒");
        bindDrawer(drawerDebugButton, drawerDebug, "高级调试日志");
        bindVersionDrawer();

        if (tabSettings != null) tabSettings.setOnClickListener(v -> showTab("settings"));
        if (tabSee != null) tabSee.setOnClickListener(v -> showTab("see"));
        if (tabControl != null) tabControl.setOnClickListener(v -> showTab("settings"));
        if (tabLife != null) tabLife.setOnClickListener(v -> showTab("life"));
        if (tabGate != null) tabGate.setOnClickListener(v -> showTab("gate"));
        if (tabDebug != null) tabDebug.setOnClickListener(v -> showTab("settings"));
        showTab("life");
        uiHandler.postDelayed(() -> checkForUpdates(false), 1200);
    }

    private void bindViews() {
        headerTitle = findViewById(R.id.headerTitle); headerSubtitle = findViewById(R.id.headerSubtitle); statusText = findViewById(R.id.statusText); debugText = findViewById(R.id.debugText); lifeStatusText = findViewById(R.id.lifeStatusText); lifeSummaryText = findViewById(R.id.lifeSummaryText); knownAppsText = findViewById(R.id.knownAppsText); homeModeStatusText = findViewById(R.id.homeModeStatusText); gateStatusText = findViewById(R.id.gateStatusText);
        overviewBatteryText = findViewById(R.id.overviewBatteryText); overviewAppText = findViewById(R.id.overviewAppText); overviewScreenText = findViewById(R.id.overviewScreenText); overviewWeatherText = findViewById(R.id.overviewWeatherText); overviewAdviceText = findViewById(R.id.overviewAdviceText); weatherLocationsText = findViewById(R.id.weatherLocationsText); themeText = findViewById(R.id.themeText); versionStatusText = findViewById(R.id.versionStatusText); updateChangelogText = findViewById(R.id.updateChangelogText);
        toggleButton = findViewById(R.id.toggleButton); accessibilityButton = findViewById(R.id.accessibilityButton); usageAccessButton = findViewById(R.id.usageAccessButton); testButton = findViewById(R.id.testButton); openXhsButton = findViewById(R.id.openXhsButton); openChatGptButton = findViewById(R.id.openChatGptButton); homeButton = findViewById(R.id.homeButton); backButton = findViewById(R.id.backButton); recentsButton = findViewById(R.id.recentsButton); alarmTestButton = findViewById(R.id.alarmTestButton); notifyTestButton = findViewById(R.id.notifyTestButton); refreshLifeButton = findViewById(R.id.refreshLifeButton);
        addPackageButton = findViewById(R.id.addPackageButton); testPackageButton = findViewById(R.id.testPackageButton); sequenceTestButton = findViewById(R.id.sequenceTestButton); refreshGateButton = findViewById(R.id.refreshGateButton); addGateAppButton = findViewById(R.id.addGateAppButton); addWeatherLocationButton = findViewById(R.id.addWeatherLocationButton); setCurrentWeatherButton = findViewById(R.id.setCurrentWeatherButton);
        themeCreamButton = findViewById(R.id.themeCreamButton); themeBlueButton = findViewById(R.id.themeBlueButton); themePeachButton = findViewById(R.id.themePeachButton); themeNightButton = findViewById(R.id.themeNightButton); themeMintButton = findViewById(R.id.themeMintButton);
        drawerConnectionButton = findViewById(R.id.drawerConnectionButton); drawerPermissionButton = findViewById(R.id.drawerPermissionButton); drawerControlTestButton = findViewById(R.id.drawerControlTestButton); drawerKnownAppsButton = findViewById(R.id.drawerKnownAppsButton); drawerHomeModeButton = findViewById(R.id.drawerHomeModeButton); drawerGateAddButton = findViewById(R.id.drawerGateAddButton); drawerReminderButton = findViewById(R.id.drawerReminderButton); drawerCycleButton = findViewById(R.id.drawerCycleButton); drawerDebugButton = findViewById(R.id.drawerDebugButton); drawerLifeDetailsButton = findViewById(R.id.drawerLifeDetailsButton); drawerAppGateButton = findViewById(R.id.drawerAppGateButton); drawerWeatherButton = findViewById(R.id.drawerWeatherButton); drawerVersionButton = findViewById(R.id.drawerVersionButton); checkUpdateButton = findViewById(R.id.checkUpdateButton); downloadUpdateButton = findViewById(R.id.downloadUpdateButton);
        remindersEnabled = findViewById(R.id.remindersEnabled); batteryRuleEnabled = findViewById(R.id.batteryRuleEnabled); screenRuleEnabled = findViewById(R.id.screenRuleEnabled); waterRuleEnabled = findViewById(R.id.waterRuleEnabled); restRuleEnabled = findViewById(R.id.restRuleEnabled); cycleEnabled = findViewById(R.id.cycleEnabled); foregroundPopupEnabled = findViewById(R.id.foregroundPopupEnabled); homeModeEnabled = findViewById(R.id.homeModeEnabled); homeModeForceEnabled = findViewById(R.id.homeModeForceEnabled); appGateEnabled = findViewById(R.id.appGateEnabled);
        tabSettings = findViewById(R.id.tabSettings); tabSee = findViewById(R.id.tabSee); tabControl = findViewById(R.id.tabControl); tabLife = findViewById(R.id.tabLife); tabGate = findViewById(R.id.tabGate); tabDebug = findViewById(R.id.tabDebug);
        sectionSettings = findViewById(R.id.sectionSettings); sectionSee = findViewById(R.id.sectionSee); sectionControl = findViewById(R.id.sectionControl); sectionLife = findViewById(R.id.sectionLife); sectionGate = findViewById(R.id.sectionGate); sectionDebug = findViewById(R.id.sectionDebug);
        drawerConnection = findViewById(R.id.drawerConnection); drawerPermission = findViewById(R.id.drawerPermission); drawerControlTest = findViewById(R.id.drawerControlTest); drawerKnownApps = findViewById(R.id.drawerKnownApps); drawerHomeMode = findViewById(R.id.drawerHomeMode); drawerGateAdd = findViewById(R.id.drawerGateAdd); drawerReminder = findViewById(R.id.drawerReminder); drawerCycle = findViewById(R.id.drawerCycle); drawerDebug = findViewById(R.id.drawerDebug); drawerAppGate = findViewById(R.id.drawerAppGate); drawerWeather = findViewById(R.id.drawerWeather); drawerVersion = findViewById(R.id.drawerVersion);
        serverUrl = findViewById(R.id.serverUrl); tokenInput = findViewById(R.id.tokenInput); deviceInput = findViewById(R.id.deviceInput); userNameInput = findViewById(R.id.userNameInput); partnerNameInput = findViewById(R.id.partnerNameInput); intervalInput = findViewById(R.id.intervalInput); cityInput = findViewById(R.id.cityInput); weatherInput = findViewById(R.id.weatherInput);
        weatherAliasInput = findViewById(R.id.weatherAliasInput); weatherCityInput = findViewById(R.id.weatherCityInput); weatherNoteInput = findViewById(R.id.weatherNoteInput);
        batteryThresholdInput = findViewById(R.id.batteryThresholdInput); screenThresholdInput = findViewById(R.id.screenThresholdInput); waterIntervalInput = findViewById(R.id.waterIntervalInput); restIntervalInput = findViewById(R.id.restIntervalInput);
        lastPeriodStartInput = findViewById(R.id.lastPeriodStartInput); cycleLengthInput = findViewById(R.id.cycleLengthInput); periodLengthInput = findViewById(R.id.periodLengthInput); cycleRemindBeforeInput = findViewById(R.id.cycleRemindBeforeInput);
        appAliasInput = findViewById(R.id.appAliasInput); appPackageInput = findViewById(R.id.appPackageInput); homeWatchPackagesInput = findViewById(R.id.homeWatchPackagesInput); homeThresholdInput = findViewById(R.id.homeThresholdInput); homeCooldownInput = findViewById(R.id.homeCooldownInput); homeTargetInput = findViewById(R.id.homeTargetInput); gateAliasInput = findViewById(R.id.gateAliasInput); gatePackageInput = findViewById(R.id.gatePackageInput);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        if (serverUrl != null) serverUrl.setText(prefs.getString(AppPrefs.KEY_SERVER, ""));
        if (tokenInput != null) tokenInput.setText(prefs.getString(AppPrefs.KEY_TOKEN, ""));
        if (deviceInput != null) deviceInput.setText(prefs.getString(AppPrefs.KEY_DEVICE, "android-phone"));
        if (userNameInput != null) userNameInput.setText(prefs.getString(AppPrefs.KEY_USER_NICKNAME, "宝宝"));
        if (partnerNameInput != null) partnerNameInput.setText(prefs.getString(AppPrefs.KEY_PARTNER_NICKNAME, "老公"));
        if (intervalInput != null) intervalInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_INTERVAL, 1500)));
        if (cityInput != null) cityInput.setText(prefs.getString(AppPrefs.KEY_CITY, ""));
        if (weatherInput != null) weatherInput.setText(prefs.getString(AppPrefs.KEY_WEATHER_NOTE, ""));
        if (weatherAliasInput != null) weatherAliasInput.setText(WeatherState.currentLocation(this).optString("name", "家"));
        if (weatherCityInput != null) weatherCityInput.setText(WeatherState.currentLocation(this).optString("city", ""));
        if (weatherNoteInput != null) weatherNoteInput.setText(WeatherState.currentLocation(this).optString("note", ""));
        if (foregroundPopupEnabled != null) foregroundPopupEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_FOREGROUND_POPUP, true));
        if (remindersEnabled != null) remindersEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_ACTIVE_REMINDERS, true));
        if (batteryRuleEnabled != null) batteryRuleEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_RULE_BATTERY, true));
        if (screenRuleEnabled != null) screenRuleEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_RULE_SCREEN, true));
        if (waterRuleEnabled != null) waterRuleEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_RULE_WATER, false));
        if (restRuleEnabled != null) restRuleEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_RULE_REST, true));
        if (cycleEnabled != null) cycleEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_CYCLE_ENABLED, false));
        if (homeModeEnabled != null) homeModeEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_HOME_MODE_ENABLED, false));
        if (homeModeForceEnabled != null) homeModeForceEnabled.setChecked(prefs.getBoolean(AppPrefs.KEY_HOME_MODE_FORCE, false));
        if (batteryThresholdInput != null) batteryThresholdInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_BATTERY_THRESHOLD, 20)));
        if (screenThresholdInput != null) screenThresholdInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_SCREEN_THRESHOLD_MIN, 240)));
        if (waterIntervalInput != null) waterIntervalInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_WATER_INTERVAL_MIN, 120)));
        if (restIntervalInput != null) restIntervalInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_REST_INTERVAL_MIN, 90)));
        if (lastPeriodStartInput != null) lastPeriodStartInput.setText(prefs.getString(AppPrefs.KEY_LAST_PERIOD_START, ""));
        if (cycleLengthInput != null) cycleLengthInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_CYCLE_LENGTH, 30)));
        if (periodLengthInput != null) periodLengthInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_PERIOD_LENGTH, 6)));
        if (cycleRemindBeforeInput != null) cycleRemindBeforeInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_CYCLE_REMIND_BEFORE, 3)));
        if (homeWatchPackagesInput != null) homeWatchPackagesInput.setText(prefs.getString(AppPrefs.KEY_HOME_WATCH_PACKAGES, "com.ss.android.ugc.aweme,com.xingin.xhs"));
        if (homeThresholdInput != null) homeThresholdInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, 10)));
        if (homeCooldownInput != null) homeCooldownInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, 5)));
        if (homeTargetInput != null) homeTargetInput.setText(prefs.getString(AppPrefs.KEY_HOME_TARGET_PACKAGE, ""));
        if (appGateEnabled != null) appGateEnabled.setChecked(prefs.getBoolean(AppGate.KEY_ENABLED, true));
    }

    private void saveSettings() {
        SharedPreferences.Editor e = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit();
        if (serverUrl != null) e.putString(AppPrefs.KEY_SERVER, serverUrl.getText().toString().trim());
        if (tokenInput != null) e.putString(AppPrefs.KEY_TOKEN, tokenInput.getText().toString().trim());
        if (deviceInput != null) e.putString(AppPrefs.KEY_DEVICE, deviceInput.getText().toString().trim().isEmpty() ? "android-phone" : deviceInput.getText().toString().trim());
        if (userNameInput != null) e.putString(AppPrefs.KEY_USER_NICKNAME, userNameInput.getText().toString().trim().isEmpty() ? "宝宝" : userNameInput.getText().toString().trim());
        if (partnerNameInput != null) e.putString(AppPrefs.KEY_PARTNER_NICKNAME, partnerNameInput.getText().toString().trim().isEmpty() ? "老公" : partnerNameInput.getText().toString().trim());
        if (intervalInput != null) e.putInt(AppPrefs.KEY_INTERVAL, parseInterval(intervalInput.getText().toString().trim()));
        if (cityInput != null) e.putString(AppPrefs.KEY_CITY, cityInput.getText().toString().trim());
        if (weatherInput != null) e.putString(AppPrefs.KEY_WEATHER_NOTE, weatherInput.getText().toString().trim());
        if (foregroundPopupEnabled != null) e.putBoolean(AppPrefs.KEY_FOREGROUND_POPUP, foregroundPopupEnabled.isChecked());
        if (remindersEnabled != null) e.putBoolean(AppPrefs.KEY_ACTIVE_REMINDERS, remindersEnabled.isChecked());
        if (batteryRuleEnabled != null) e.putBoolean(AppPrefs.KEY_RULE_BATTERY, batteryRuleEnabled.isChecked());
        if (batteryThresholdInput != null) e.putInt(AppPrefs.KEY_BATTERY_THRESHOLD, parseInt(batteryThresholdInput.getText().toString().trim(), 20, 5, 80));
        if (screenRuleEnabled != null) e.putBoolean(AppPrefs.KEY_RULE_SCREEN, screenRuleEnabled.isChecked());
        if (screenThresholdInput != null) e.putInt(AppPrefs.KEY_SCREEN_THRESHOLD_MIN, parseInt(screenThresholdInput.getText().toString().trim(), 240, 30, 1440));
        if (waterRuleEnabled != null) e.putBoolean(AppPrefs.KEY_RULE_WATER, waterRuleEnabled.isChecked());
        if (waterIntervalInput != null) e.putInt(AppPrefs.KEY_WATER_INTERVAL_MIN, parseInt(waterIntervalInput.getText().toString().trim(), 120, 30, 720));
        if (restRuleEnabled != null) e.putBoolean(AppPrefs.KEY_RULE_REST, restRuleEnabled.isChecked());
        if (restIntervalInput != null) e.putInt(AppPrefs.KEY_REST_INTERVAL_MIN, parseInt(restIntervalInput.getText().toString().trim(), 90, 30, 720));
        if (cycleEnabled != null) e.putBoolean(AppPrefs.KEY_CYCLE_ENABLED, cycleEnabled.isChecked());
        if (lastPeriodStartInput != null) e.putString(AppPrefs.KEY_LAST_PERIOD_START, lastPeriodStartInput.getText().toString().trim());
        if (cycleLengthInput != null) e.putInt(AppPrefs.KEY_CYCLE_LENGTH, parseInt(cycleLengthInput.getText().toString().trim(), 30, 15, 60));
        if (periodLengthInput != null) e.putInt(AppPrefs.KEY_PERIOD_LENGTH, parseInt(periodLengthInput.getText().toString().trim(), 6, 1, 14));
        if (cycleRemindBeforeInput != null) e.putInt(AppPrefs.KEY_CYCLE_REMIND_BEFORE, parseInt(cycleRemindBeforeInput.getText().toString().trim(), 3, 0, 14));
        if (homeModeEnabled != null) e.putBoolean(AppPrefs.KEY_HOME_MODE_ENABLED, homeModeEnabled.isChecked());
        if (homeModeForceEnabled != null) e.putBoolean(AppPrefs.KEY_HOME_MODE_FORCE, homeModeForceEnabled.isChecked());
        if (homeWatchPackagesInput != null) e.putString(AppPrefs.KEY_HOME_WATCH_PACKAGES, homeWatchPackagesInput.getText().toString().trim());
        if (homeThresholdInput != null) e.putInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, parseInt(homeThresholdInput.getText().toString().trim(), 10, 1, 240));
        if (homeCooldownInput != null) e.putInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, parseInt(homeCooldownInput.getText().toString().trim(), 5, 1, 240));
        if (homeTargetInput != null) e.putString(AppPrefs.KEY_HOME_TARGET_PACKAGE, homeTargetInput.getText().toString().trim());
        if (appGateEnabled != null) e.putBoolean(AppGate.KEY_ENABLED, appGateEnabled.isChecked());
        e.apply();
    }

    private void showTab(String tab) {
        currentTab = tab;
        setVisible(sectionLife, "life".equals(tab)); setVisible(sectionSee, "see".equals(tab)); setVisible(sectionGate, "gate".equals(tab)); setVisible(sectionSettings, "settings".equals(tab));
        setVisible(sectionControl, false); setVisible(sectionDebug, false);
        setTabSelected(tabLife, "life".equals(tab)); setTabSelected(tabSee, "see".equals(tab)); setTabSelected(tabGate, "gate".equals(tab)); setTabSelected(tabSettings, "settings".equals(tab));
        updateHeader(tab);
        applyVisualTheme();
    }
    private void updateHeader(String tab) {
        if (headerTitle == null || headerSubtitle == null) return;
        if ("life".equals(tab)) { headerTitle.setText("掌心窗"); headerSubtitle.setText("v0.3.4.1 · 显示适配与版本更新。"); }
        else if ("see".equals(tab)) { headerTitle.setText("看见"); headerSubtitle.setText("把屏幕递给老公，看见就收在这里。"); }
        else if ("gate".equals(tab)) { headerTitle.setText("守护"); headerSubtitle.setText("门禁、天气和提醒，平时收进抽屉。"); }
        else { headerTitle.setText("设置"); headerSubtitle.setText("主题、权限和调试都放这里。"); }
    }
    private void setVisible(View v, boolean visible) { if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE); }
    private void setTabSelected(Button b, boolean selected) {
        if (b == null) return;
        UITheme t = UITheme.current(this);
        b.setTextColor(selected ? Color.WHITE : t.subtext);
        b.setBackground(t.pill(selected));
        b.setTextSize(10);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setMinHeight(dp(29));
        b.setGravity(Gravity.CENTER);
    }
    private void bindDrawer(Button b, View drawer, String title) {
        if (b == null || drawer == null) return;
        b.setText(title + "  ›");
        b.setOnClickListener(v -> {
            boolean show = drawer.getVisibility() != View.VISIBLE;
            drawer.setVisibility(show ? View.VISIBLE : View.GONE);
            b.setText(title + (show ? "  ˄" : "  ›"));
            applyVisualTheme();
        });
    }
    private void bindThemeButton(Button b, String name) {
        if (b == null) return;
        b.setOnClickListener(v -> {
            AppPrefs.get(this).edit().putString(AppPrefs.KEY_THEME, name).apply();
            applyVisualTheme();
            updateUI();
            Toast.makeText(this, "主题已切换为 " + name, Toast.LENGTH_SHORT).show();
        });
    }


    private void applyVisualTheme() {
        UITheme t = UITheme.current(this);
        View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (root != null) root.setBackground(t.background());
        styleTree(root, t, false);
        setTabSelected(tabLife, "life".equals(currentTab));
        setTabSelected(tabSee, "see".equals(currentTab));
        setTabSelected(tabGate, "gate".equals(currentTab));
        setTabSelected(tabSettings, "settings".equals(currentTab));
        styleThemeButton(themeCreamButton, t, "奶油绿"); styleThemeButton(themeBlueButton, t, "雾蓝白"); styleThemeButton(themePeachButton, t, "白桃粉"); styleThemeButton(themeNightButton, t, "夜航黑"); styleThemeButton(themeMintButton, t, "薄荷透明");
        if (tabLife != null && tabLife.getParent() instanceof View) { ((View) tabLife.getParent()).setBackground(t.card(24, 0.35f)); ((View) tabLife.getParent()).setElevation(dp(4)); }
        styleDrawerButton(drawerLifeDetailsButton, t); styleDrawerButton(drawerAppGateButton, t); styleDrawerButton(drawerWeatherButton, t); styleDrawerButton(drawerConnectionButton, t); styleDrawerButton(drawerPermissionButton, t); styleDrawerButton(drawerControlTestButton, t); styleDrawerButton(drawerKnownAppsButton, t); styleDrawerButton(drawerHomeModeButton, t); styleDrawerButton(drawerGateAddButton, t); styleDrawerButton(drawerReminderButton, t); styleDrawerButton(drawerCycleButton, t); styleDrawerButton(drawerDebugButton, t);
        if (statusText != null) { statusText.setBackground(t.soft(22)); statusText.setPadding(dp(14), dp(10), dp(14), dp(10)); }
        if (themeText != null) themeText.setTextColor(t.subtext);
    }

    private void styleTree(View v, UITheme t, boolean insideCard) {
        if (v == null) return;
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setIncludeFontPadding(false);
            tv.setTypeface(Typeface.create(tv.getTextSize() >= dp(17) ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            if (tv instanceof Button && !(tv instanceof CheckBox)) {
                styleButton((Button) tv, t);
            } else if (tv instanceof EditText) {
                tv.setTextColor(t.text); tv.setHintTextColor(t.subtext); tv.setTextSize(Math.min(13, tv.getTextSize() / getResources().getDisplayMetrics().scaledDensity));
            } else {
                tv.setTextColor(tv.getTextSize() >= dp(16) ? t.text : t.subtext);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            boolean childInsideCard = insideCard;
            if (v instanceof LinearLayout && v.getBackground() != null && v != ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0)) {
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                boolean bottomBar = lp != null && lp.height <= dp(52) && lp.height >= dp(42);
                if (!bottomBar) { v.setBackground(t.card(20, 0.45f)); v.setElevation(dp(1)); childInsideCard = true; }
            }
            for (int i = 0; i < g.getChildCount(); i++) styleTree(g.getChildAt(i), t, childInsideCard);
        }
    }

    private void styleButton(Button b, UITheme t) {
        if (b == null) return;
        b.setAllCaps(false);
        b.setIncludeFontPadding(false);
        b.setTextColor(t.text);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackground(t.chip(false));
        b.setTextSize(12);
        b.setMinHeight(dp(29));
        b.setPadding(dp(10), 0, dp(10), 0);
    }

    private void styleDrawerButton(Button b, UITheme t) {
        if (b == null) return;
        b.setBackground(t.chip(false));
        b.setTextColor(t.text);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setMinHeight(dp(29));
        b.setTextSize(12);
        b.setPadding(dp(14), 0, dp(14), 0);
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        if (lp != null) { lp.height = dp(30); b.setLayoutParams(lp); }
    }

    private void styleThemeButton(Button b, UITheme t, String name) {
        if (b == null) return;
        boolean selected = name.equals(t.name);
        b.setBackground(t.chip(selected));
        b.setTextColor(selected ? t.primary : t.subtext);
        b.setMinHeight(dp(29));
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        if (lp != null) { lp.height = dp(30); b.setLayoutParams(lp); }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onResume() { super.onResume(); serviceRunning = CompanionService.isRunning(); updateUI(); uiHandler.removeCallbacks(refreshTick); uiHandler.post(refreshTick); }
    @Override protected void onPause() { uiHandler.removeCallbacks(refreshTick); super.onPause(); }

    private void addWeatherLocation(boolean makeCurrent) {
        String alias = weatherAliasInput == null ? "" : weatherAliasInput.getText().toString().trim();
        String city = weatherCityInput == null ? "" : weatherCityInput.getText().toString().trim();
        String note = weatherNoteInput == null ? "" : weatherNoteInput.getText().toString().trim();
        if (alias.isEmpty() && city.isEmpty()) { Toast.makeText(this, "先填地区名或城市", Toast.LENGTH_SHORT).show(); return; }
        WeatherState.saveLocation(this, alias, city, note, makeCurrent);
        DebugState.append(this, (makeCurrent ? "已设置当前天气地区：" : "已保存天气地区：") + (alias.isEmpty() ? city : alias));
        Toast.makeText(this, makeCurrent ? "已设为当前地区" : "已保存地区", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    private void startCompanionService() {
        saveSettings();
        String url = serverUrl == null ? "" : serverUrl.getText().toString().trim(); String token = tokenInput == null ? "" : tokenInput.getText().toString().trim();
        if (url.isEmpty() || token.isEmpty()) { Toast.makeText(this, "请填写服务器地址和 Token", Toast.LENGTH_SHORT).show(); return; }
        if (ScreenshotService.getInstance() == null) { DebugState.append(this, "启动失败：无障碍服务未连接"); Toast.makeText(this, "请先开启掌心窗无障碍服务", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putBoolean("user_stopped", false).apply(); requestIgnoreBatteryOptimization();
        Intent intent = new Intent(this, CompanionService.class); intent.putExtra("server_url", url); intent.putExtra("token", token);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
        DebugState.append(this, "已请求启动前台服务：v0.3.4.1 显示适配 / 版本更新已启用"); serviceRunning = true; updateUI();
    }

    private void stopCompanionService() { getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putBoolean("user_stopped", true).apply(); stopService(new Intent(this, CompanionService.class)); DebugState.append(this, "已停止服务"); serviceRunning = false; updateUI(); }

    private void testScreenshot() {
        saveSettings(); String url = serverUrl == null ? "" : serverUrl.getText().toString().trim(); String token = tokenInput == null ? "" : tokenInput.getText().toString().trim(); ScreenshotService ss = ScreenshotService.getInstance();
        if (url.isEmpty() || token.isEmpty()) { Toast.makeText(this, "先填服务器地址和 Token", Toast.LENGTH_SHORT).show(); return; }
        if (ss == null) { DebugState.append(this, "测试失败：无障碍服务未连接"); Toast.makeText(this, "先开启无障碍服务", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        DebugState.append(this, "给老公看一眼：开始截图上传"); ss.doScreenshot(url, token); Toast.makeText(this, "正在给老公看一眼", Toast.LENGTH_SHORT).show(); updateUI();
    }
    private void testAlarm() { Calendar c = Calendar.getInstance(); c.add(Calendar.MINUTE, 1); try { Intent i = new Intent(AlarmClock.ACTION_SET_ALARM); i.putExtra(AlarmClock.EXTRA_HOUR, c.get(Calendar.HOUR_OF_DAY)); i.putExtra(AlarmClock.EXTRA_MINUTES, c.get(Calendar.MINUTE)); i.putExtra(AlarmClock.EXTRA_MESSAGE, "掌心窗测试闹钟：宝宝看到了就说明成功"); i.putExtra(AlarmClock.EXTRA_VIBRATE, true); i.putExtra(AlarmClock.EXTRA_SKIP_UI, true); startActivity(i); DebugState.append(this, "已请求设置一分钟后的测试闹钟"); } catch (Exception e) { DebugState.append(this, "测试闹钟失败：" + e.getClass().getSimpleName()); Toast.makeText(this, "闹钟 App 没接住请求", Toast.LENGTH_SHORT).show(); } }
    private void testNotification() { saveSettings(); boolean ok = CompanionService.showReminderNotification(this, "掌心窗悬浮横幅测试", "宝宝看到了顶部横幅，就说明通知通道正常。"); DebugState.append(this, ok ? "已发送悬浮横幅测试提醒" : "悬浮横幅/通知失败：请允许掌心窗发送通知"); Toast.makeText(this, ok ? "已发送横幅测试" : "请先允许通知权限", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void addPackageAlias() { String alias = appAliasInput == null ? "" : appAliasInput.getText().toString().trim(); String pkg = appPackageInput == null ? "" : appPackageInput.getText().toString().trim(); if (alias.isEmpty()) { Toast.makeText(this, "先填应用名/昵称", Toast.LENGTH_SHORT).show(); return; } if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "包名格式不对，例如 com.xingin.xhs", Toast.LENGTH_LONG).show(); return; } AppPrefs.saveCustomApp(this, alias, pkg); DebugState.append(this, "已保存可打开应用：" + alias + " → " + pkg); Toast.makeText(this, "已添加包名", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void addGateApp() { String alias = gateAliasInput == null ? "" : gateAliasInput.getText().toString().trim(); String pkg = gatePackageInput == null ? "" : gatePackageInput.getText().toString().trim(); if (alias.isEmpty()) { Toast.makeText(this, "先填应用名/昵称", Toast.LENGTH_SHORT).show(); return; } if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "包名格式不对，例如 com.xingin.xhs", Toast.LENGTH_LONG).show(); return; } AppGate.addGateApp(this, alias, pkg); DebugState.append(this, "已保存门禁应用：" + alias + " → " + pkg); Toast.makeText(this, "已添加到应用门禁", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void testCustomPackage() { String pkg = appPackageInput == null ? "" : appPackageInput.getText().toString().trim(); if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "先填正确包名", Toast.LENGTH_SHORT).show(); return; } openPackage(pkg); }
    private void testLocalSequence() { boolean ok1 = CompanionService.showReminderNotification(this, "掌心窗连招测试", "先发悬浮横幅，再回目标 App。日志会写清每一步。"); String result = CompanionService.openPackageResult(this, AppPrefs.packageForApp(this, "ChatGPT")); DebugState.append(this, "本机连招测试：popup=" + ok1 + "；open=" + result); updateUI(); }
    private boolean openPackage(String pkg) { String result = CompanionService.openPackageResult(this, pkg); boolean ok = result.startsWith("opened_"); DebugState.append(this, "本机打开 App：" + result); Toast.makeText(this, ok ? "已尝试打开" : ("打开失败：" + result), Toast.LENGTH_SHORT).show(); updateUI(); return ok; }
    private void toast(boolean ok) { Toast.makeText(this, ok ? "执行成功" : "执行失败，请检查权限/包名", Toast.LENGTH_SHORT).show(); updateUI(); }
    private int parseInterval(String raw) { try { int v = Integer.parseInt(raw); if (v < 700) return 700; if (v > 10000) return 10000; return v; } catch (Exception e) { return 1500; } }
    private int parseInt(String raw, int def, int min, int max) { try { int v = Integer.parseInt(raw); if (v < min) return min; if (v > max) return max; return v; } catch (Exception e) { return def; } }
    private void openAccessibilitySettings() { try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "设置 → 无障碍 → 掌心窗", Toast.LENGTH_LONG).show(); } }
    private void openUsageAccessSettings() { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "设置 → 应用 → 特殊权限 → 使用情况访问", Toast.LENGTH_LONG).show(); } }
    private void requestIgnoreBatteryOptimization() { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; try { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) { Intent bi = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); bi.setData(Uri.parse("package:" + getPackageName())); startActivity(bi); } } catch (Exception ignored) { } }


    private void prepareWindowForFullContent() {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(0xFFF8FCF9);
                getWindow().setNavigationBarColor(0xFFFFFFFF);
            }
        } catch (Exception ignored) { }
    }

    private void repairFirstLayoutPass() {
        final View content = findViewById(android.R.id.content);
        if (content == null) return;
        Runnable repair = () -> {
            try {
                View root = ((ViewGroup) content).getChildAt(0);
                int w = getResources().getDisplayMetrics().widthPixels;
                int h = getResources().getDisplayMetrics().heightPixels;
                if (root != null) {
                    root.setMinimumWidth(w);
                    root.setMinimumHeight(h);
                    ViewGroup.LayoutParams lp = root.getLayoutParams();
                    if (lp != null) { lp.width = ViewGroup.LayoutParams.MATCH_PARENT; lp.height = ViewGroup.LayoutParams.MATCH_PARENT; root.setLayoutParams(lp); }
                    root.requestLayout();
                    root.invalidate();
                }
                content.requestLayout();
                content.invalidate();
            } catch (Exception ignored) { }
        };
        content.post(repair);
        uiHandler.postDelayed(repair, 120);
        uiHandler.postDelayed(repair, 360);
        uiHandler.postDelayed(repair, 900);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) repairFirstLayoutPass();
    }

    private void bindVersionDrawer() {
        if (drawerVersionButton == null || drawerVersion == null) return;
        drawerVersionButton.setOnClickListener(v -> {
            boolean show = drawerVersion.getVisibility() != View.VISIBLE;
            drawerVersion.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putInt("seen_update_code", latestVersionCode).apply();
            }
            updateVersionPanel();
            applyVisualTheme();
        });
    }

    private void updateVersionPanel() {
        boolean hasNew = latestVersionCode > APP_VERSION_CODE;
        int seen = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).getInt("seen_update_code", APP_VERSION_CODE);
        boolean unseen = hasNew && seen < latestVersionCode;
        if (drawerVersionButton != null && (drawerVersion == null || drawerVersion.getVisibility() != View.VISIBLE)) {
            drawerVersionButton.setText("版本与更新" + (unseen ? "  ●" : "") + "  ›");
        } else if (drawerVersionButton != null) {
            drawerVersionButton.setText("版本与更新  ˄");
        }
        if (versionStatusText != null) {
            if (hasNew) versionStatusText.setText("当前版本 v" + APP_VERSION_NAME + "\n发现新版本 v" + latestVersionName + "，建议更新。");
            else versionStatusText.setText("当前版本 v" + APP_VERSION_NAME + "\n已是当前检查到的最新版。");
        }
        if (updateChangelogText != null) {
            String text = latestChangelog == null || latestChangelog.trim().isEmpty()
                ? "点击检查更新，可查看更新日志并下载最新版。"
                : latestChangelog.trim();
            updateChangelogText.setText(text);
        }
        if (downloadUpdateButton != null) downloadUpdateButton.setEnabled(hasNew && latestApkUrl != null && latestApkUrl.trim().length() > 0);
    }

    private void checkForUpdates(boolean manual) {
        String configuredServer = serverUrl == null ? "" : serverUrl.getText().toString().trim();
        String primary = configuredServer.isEmpty() ? DEFAULT_UPDATE_URL : configuredServer.replaceAll("/+$", "") + "/api/update.json";
        new Thread(() -> {
            try {
                JSONObject info;
                try { info = fetchUpdateJson(primary); }
                catch (Exception first) {
                    if (DEFAULT_UPDATE_URL.equals(primary)) throw first;
                    info = fetchUpdateJson(DEFAULT_UPDATE_URL);
                }
                int code = info.optInt("latest_version_code", APP_VERSION_CODE);
                String name = info.optString("latest_version_name", APP_VERSION_NAME);
                String apk = info.optString("apk_url", "");
                JSONArray arr = info.optJSONArray("changelog");
                StringBuilder changes = new StringBuilder();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) changes.append("• ").append(arr.optString(i)).append("\n");
                }
                latestVersionCode = code;
                latestVersionName = name;
                latestApkUrl = apk;
                latestChangelog = changes.toString().trim();
                runOnUiThread(() -> {
                    updateVersionPanel();
                    if (manual) Toast.makeText(this, code > APP_VERSION_CODE ? "发现新版本 v" + name : "已经是最新版", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (manual) Toast.makeText(this, "检查更新失败：" + e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
                    if (updateChangelogText != null && manual) updateChangelogText.setText("检查更新失败，请稍后再试。\n" + e.getClass().getSimpleName());
                    updateVersionPanel();
                });
            }
        }).start();
    }

    private JSONObject fetchUpdateJson(String urlText) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "LinjianPeek/" + APP_VERSION_NAME);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return new JSONObject(sb.toString());
    }

    private void downloadLatestApk() {
        String url = latestApkUrl == null ? "" : latestApkUrl.trim();
        if (url.isEmpty()) { Toast.makeText(this, "先检查更新", Toast.LENGTH_SHORT).show(); return; }
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("掌心窗 v" + latestVersionName);
            req.setDescription("正在下载掌心窗最新版 APK");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Zhangxinchuang-public-v" + latestVersionName + ".apk");
            if (dm != null) {
                dm.enqueue(req);
                Toast.makeText(this, "已开始下载，完成后点通知安装", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ignored) { }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
            Toast.makeText(this, "已打开下载链接", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "打不开下载链接", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        boolean accessibilityOk = ScreenshotService.getInstance() != null; boolean usageOk = LifeState.hasUsagePermission(this);
        if (serviceRunning) { if (statusText != null) { statusText.setText(accessibilityOk ? "已连接 · 看见、控制和守护待命" : "生活小窗已打开 · 无障碍待开启"); statusText.setTextColor(accessibilityOk ? 0xFF2E9D72 : 0xFFFF9800); } if (toggleButton != null) { toggleButton.setText("停止服务"); toggleButton.setBackgroundResource(R.drawable.pill_danger); } }
        else { if (statusText != null) { statusText.setText(accessibilityOk ? "看见已准备 · 服务待启动" : "天气可用 · 无障碍待开启"); statusText.setTextColor(0xFF6A7B76); } if (toggleButton != null) { toggleButton.setText("启动服务"); toggleButton.setBackgroundResource(R.drawable.pill_primary); } }
        if (usageAccessButton != null) usageAccessButton.setText(usageOk ? "使用情况权限：已开启" : "打开使用情况访问权限");
        try {
            JSONObject s = LifeState.collect(this);
            int battery = s.optInt("battery_percent", -1); boolean charging = s.optBoolean("charging", false);
            if (overviewBatteryText != null) overviewBatteryText.setText((battery >= 0 ? battery + "%" : "-") + (charging ? "\n充电中" : "\n未充电"));
            if (overviewAppText != null) overviewAppText.setText(s.optString("current_app", "-") + "\n" + (s.optBoolean("screen_on") ? "屏幕亮" : "屏幕灭"));
            int mins = s.optInt("screen_time_today_minutes", 0);
            if (overviewScreenText != null) overviewScreenText.setText(formatMinutes(mins) + "\n解锁 " + s.optInt("unlock_count_today", 0) + " 次");
            JSONObject w = s.optJSONObject("current_weather_location");
            updateWeatherOverview(w);
            if (overviewAdviceText != null) overviewAdviceText.setText(makeAdvice(s));
        } catch (Exception ignored) { }
        if (lifeSummaryText != null) lifeSummaryText.setText(lifeSummary());
        if (lifeStatusText != null) lifeStatusText.setText(LifeState.pretty(this));
        if (drawerWeatherButton != null && (drawerWeather == null || drawerWeather.getVisibility() != View.VISIBLE)) drawerWeatherButton.setText(WeatherState.summaryLine(this) + "  ›");
        if (weatherLocationsText != null) weatherLocationsText.setText(WeatherState.locationsText(this));
        if (knownAppsText != null) knownAppsText.setText(AppPrefs.knownAppsText(this));
        if (homeModeStatusText != null) homeModeStatusText.setText(HomeMode.pretty(this));
        if (drawerAppGateButton != null && (drawerAppGate == null || drawerAppGate.getVisibility() != View.VISIBLE)) drawerAppGateButton.setText(AppGate.summaryLine(this) + "  ›");
        if (gateStatusText != null) gateStatusText.setText(AppGate.prettyClean(this));
        if (debugText != null) debugText.setText(DebugState.get(this));
        if (themeText != null) themeText.setText("当前主题：" + AppPrefs.get(this).getString(AppPrefs.KEY_THEME, "奶油绿") + "\n点击后即时切换背景、卡片、按钮和底部导航。");
        updateVersionPanel();
        applyVisualTheme();
    }

    private void updateWeatherOverview(JSONObject w) {
        if (overviewWeatherText == null) return;
        String name = w == null ? "当前地区" : w.optString("name", "当前地区");
        String city = w == null ? "" : w.optString("city", "");
        JSONObject live = WeatherLive.cached(this, city);
        if (live != null && live.optBoolean("ok")) overviewWeatherText.setText(WeatherLive.cardText(live, name));
        else overviewWeatherText.setText(name + "\n" + (city.isEmpty() ? "未设城市" : city));
        long now = System.currentTimeMillis();
        if (!city.isEmpty() && !weatherFetching && !WeatherLive.isFresh(this, city, 45L * 60L * 1000L) && now - lastWeatherFetchAt > 45_000L) {
            weatherFetching = true;
            lastWeatherFetchAt = now;
            WeatherLive.refreshAsync(this, city, weather -> runOnUiThread(() -> {
                weatherFetching = false;
                if (weather != null && weather.optBoolean("ok")) {
                    overviewWeatherText.setText(WeatherLive.cardText(weather, name));
                    if (overviewAdviceText != null) {
                        try { overviewAdviceText.setText(makeAdvice(LifeState.collect(this))); } catch (Exception ignored) { }
                    }
                }
            }));
        }
    }

    private String lifeSummary() {
        try {
            JSONObject s = LifeState.collect(this);
            int battery = s.optInt("battery_percent", -1);
            String b = battery >= 0 ? (battery + "%" + (s.optBoolean("charging", false) ? " · 充电中" : " · 未充电")) : "-";
            JSONObject w = s.optJSONObject("current_weather_location");
            String loc = "未设地区";
            if (w != null) { loc = w.optString("name", "当前地区"); String city = w.optString("city", ""); if (city.length() > 0) loc += " · " + city; }
            return "时间：" + s.optString("local_time", "-") + "\n电量：" + b + "\n网络：" + s.optString("network_type", "-") + "\n当前地区：" + loc;
        } catch (Exception e) { return "生活细节加载中…"; }
    }

    private String makeAdvice(JSONObject s) {
        StringBuilder sb = new StringBuilder();
        int battery = s.optInt("battery_percent", -1); boolean charging = s.optBoolean("charging", false);
        if (battery >= 0 && battery <= 40 && !charging) sb.append("电量 ").append(battery).append("%，该充电了。\n");
        int mins = s.optInt("screen_time_today_minutes", 0);
        if (mins >= 480) sb.append("屏幕时间有点长，眼睛歇半分钟。\n");
        JSONObject w = s.optJSONObject("current_weather_location");
        if (w != null) {
            String city = w.optString("city", "");
            JSONObject live = WeatherLive.cached(this, city);
            if (live != null && live.optBoolean("ok")) sb.append(WeatherLive.advice(live, w.optString("name", "当前地区"))).append("\n");
            else sb.append(WeatherState.localAdvice(w.optString("note", ""))).append("\n");
        }
        if (sb.length() == 0) sb.append("状态还好，老公继续看着你。");
        return sb.toString().trim();
    }
    private String formatMinutes(int minutes) { if (minutes < 60) return minutes + " 分钟"; return (minutes / 60) + "h " + (minutes % 60) + "m"; }
}
