package dev.linjian.peek;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Calendar;
import java.util.Locale;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREF_A11Y_SETTINGS_OPENED_AT = "a11y_settings_opened_at";
    private static final long A11Y_CONFIRM_WINDOW_MS = 30000L;
    private static final String DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/linzhi-524/linjian-peek-public/main/update.json";
    private int latestVersionCode = AppPrefs.APP_VERSION_CODE;
    private String latestVersionName = AppPrefs.APP_VERSION_NAME;
    private String latestApkUrl = "";
    private String latestChangelog = "";
    private TextView brandText, headerTitle, headerSubtitle, statusText, debugText, lifeStatusText, lifeSummaryText, knownAppsText, homeModeStatusText, gateStatusText, nowStateText, nowStatePermissionText;
    private TextView heroLabelText, overviewAdviceText, overviewSecondaryText, overviewMetaText, overviewBatteryText, overviewBatteryDetail, overviewAppText, overviewAppDetail, overviewScreenText, overviewScreenDetail, overviewWeatherText, overviewWeatherDetail, weatherLocationsText, themeText, calendarSummaryText, calendarDetailText;
    private TextView overviewBatteryLabel, overviewAppLabel, overviewScreenLabel, overviewWeatherLabel, quickSeeTitle, quickSeeDetail, quickSeeArrow, quickGuardTitle, quickGuardDetail, quickGuardArrow;
    private ImageView quickSeeIcon, quickGuardIcon;
    private TextView guidianSummaryText, guidianDetailText, guidianSettingsStatusText, guidianAvatarText, versionStatusText, updateChangelogText, licenseSummaryText;
    private Button toggleButton, accessibilityButton, usageAccessButton, testButton, openXhsButton, openTargetAppButton, homeButton, backButton, recentsButton, alarmTestButton, notifyTestButton, refreshLifeButton;
    private Button addPackageButton, testPackageButton, sequenceTestButton, refreshGateButton, addGateAppButton, addWeatherLocationButton, setCurrentWeatherButton;
    private Button testGuidianButton, saveGuidianSettingsButton, chooseGuidianAvatarButton, guidianThemeDuskButton, guidianThemeCloudButton, guidianThemeBerryButton;
    private Button themeCreamButton, themeBlueButton, themePeachButton, themeNightButton, themeMintButton, themePurpleButton, drawerThemeButton, drawerNowStateButton, locationPermissionButton, overlayPermissionButton;
    private Button drawerConnectionButton, drawerPermissionButton, drawerControlTestButton, drawerKnownAppsButton, drawerHomeModeButton, drawerGateAddButton, drawerReminderButton, drawerCycleButton, drawerDebugButton, drawerLifeDetailsButton, drawerAppGateButton, drawerWeatherButton, drawerVersionButton, checkUpdateButton, downloadUpdateButton;
    private Button drawerGuidianButton, drawerGuidianSettingsButton, drawerCalendarButton, saveCalendarEventButton;
    private CheckBox remindersEnabled, batteryRuleEnabled, screenRuleEnabled, waterRuleEnabled, restRuleEnabled, cycleEnabled, foregroundPopupEnabled, homeModeEnabled, homeModeForceEnabled, appGateEnabled;
    private CheckBox guidianEnabled, guidianRemoteEnabled, guidianFullscreenEnabled, guidianQuietEnabled, calendarLunarEnabled, calendarRepeatEnabled, calendarBannerEnabled;
    private Button tabSettings, tabSee, tabControl, tabLife, tabGate, tabDebug;
    private View quickSeeButton, quickGuardButton;
    private View sectionSettings, sectionSee, sectionControl, sectionLife, sectionGate, sectionDebug;
    private View heroCard, bottomNav;
    private View drawerTheme, drawerNowState, drawerConnection, drawerPermission, drawerControlTest, drawerKnownApps, drawerHomeMode, drawerGateAdd, drawerReminder, drawerCycle, drawerDebug, drawerAppGate, drawerWeather, drawerVersion;
    private View drawerGuidian, drawerGuidianSettings, drawerCalendar;
    private EditText serverUrl, tokenInput, deviceInput, intervalInput, cityInput, weatherInput, userNameInput, companionNameInput;
    private EditText weatherAliasInput, weatherCityInput, weatherNoteInput, calendarTitleInput, calendarDateInput, calendarGroupInput, calendarNoteInput;
    private EditText batteryThresholdInput, screenThresholdInput, waterIntervalInput, restIntervalInput;
    private EditText lastPeriodStartInput, cycleLengthInput, periodLengthInput, cycleRemindBeforeInput;
    private EditText appAliasInput, appPackageInput, targetAppsInput, homeThresholdInput, homeCooldownInput, homeTargetInput, gateAliasInput, gatePackageInput;
    private EditText guidianIntervalInput, guidianCooldownInput, guidianDailyMaxInput, guidianQuietStartInput, guidianQuietEndInput, guidianTargetPackageInput, guidianPromptInput, guidianReasonInput;
    private boolean serviceRunning = false;
    private String currentTab = "life";
    private boolean weatherFetching = false;
    private long lastWeatherFetchAt = 0L;
    private static final int REQ_GUIDIAN_AVATAR = 230723;
    private static final int REQ_DIARY_COVER = 230724;
    private static final int REQ_DIARY_EXPORT = 230725;
    private static final int REQ_DIARY_IMPORT = 230726;
    private static boolean openingShownForProcess = false;
    private SoftAvatarView companionAvatarView;
    private ImageView companionRestArt;
    private TextView companionPresenceText, sharedWhisperText, sharedWhisperMetaText, companionActionsPreview, todayJourneyText, guardOverviewText;
    private TextView todayNextTitle, todayNextDetail, companionDaysText, companionSinceText, companionAnniversaryText, guardDeviceStatusText, guardRecordText;
    private TextView calendarHeroTitle, calendarHeroDetail, calendarMonthTitle, calendarSelectedTitle, calendarSelectedDetail;
    private LinearLayout calendarGrid, calendarSelectedEventsContainer;
    private Calendar calendarVisibleMonth = Calendar.getInstance();
    private int calendarSelectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
    private boolean guardianCalendarDetailOpen = false;
    private boolean diaryPageOpen = false, diaryContentOpen = false;
    private String diaryBookId = "", diarySelectedDate = "", diaryCurrentEntryId = "";
    private View diaryExpandedPaperView;
    private TextView diaryExpandedContentView, diaryExpandedHintView;
    private FrameLayout diaryDateDrawerOverlay;
    private LinearLayout diaryDateDrawerPanel;
    private GuardianRingView guardianRing;
    private long lastCompanionSyncAt = 0L;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable refreshTick = new Runnable() {
        @Override public void run() { serviceRunning = CompanionService.isRunning(); updateUI(); uiHandler.postDelayed(this, 1500); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        buildMagazinePages();
        loadSettings();
        NowState.start(this);

        DebugState.append(this, "掌心窗公开版 v0.3.7 已打开");
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 13);
        serviceRunning = CompanionService.isRunning();
        updateUI();

        if (accessibilityButton != null) accessibilityButton.setOnClickListener(v -> { if (recentlyOpenedAccessibilitySettings() && !isAccessibilityServiceEnabled()) showAccessibilityHelpDialog(); else openAccessibilitySettings(); });
        if (usageAccessButton != null) usageAccessButton.setOnClickListener(v -> openUsageAccessSettings());
        if (locationPermissionButton != null) locationPermissionButton.setOnClickListener(v -> requestLocationPermission());
        if (overlayPermissionButton != null) overlayPermissionButton.setOnClickListener(v -> openOverlayPermissionSettings());
        if (toggleButton != null) toggleButton.setOnClickListener(v -> { if (serviceRunning) stopCompanionService(); else startCompanionService(); });
        if (refreshLifeButton != null) refreshLifeButton.setOnClickListener(v -> { saveSettings(); updateUI(); Toast.makeText(this, "已刷新生活总览", Toast.LENGTH_SHORT).show(); });
        if (testButton != null) testButton.setOnClickListener(v -> testScreenshot());
        if (openXhsButton != null) openXhsButton.setOnClickListener(v -> openPackage(AppPrefs.packageForApp(this, "小红书")));
        if (openTargetAppButton != null) openTargetAppButton.setOnClickListener(v -> openPackage(AppPrefs.homeTargetPackage(this)));
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
        if (testGuidianButton != null) testGuidianButton.setOnClickListener(v -> { saveSettings(); GuidianState.showPrompt(this, true); CompanionWindowState.recordJourney(this, "回应归电", "回到" + AppPrefs.companionName(this) + "的窗边"); updateUI(); });
        if (saveCalendarEventButton != null) saveCalendarEventButton.setOnClickListener(v -> saveCalendarEvent());
        if (saveGuidianSettingsButton != null) saveGuidianSettingsButton.setOnClickListener(v -> { saveSettings(); Toast.makeText(this, "归电设置已保存", Toast.LENGTH_SHORT).show(); updateUI(); });
        if (chooseGuidianAvatarButton != null) chooseGuidianAvatarButton.setOnClickListener(v -> chooseGuidianAvatar());
        bindGuidianThemeButton(guidianThemeDuskButton, "粉色"); bindGuidianThemeButton(guidianThemeCloudButton, "白色"); bindGuidianThemeButton(guidianThemeBerryButton, "黑色");
        if (userNameInput != null) userNameInput.setOnFocusChangeListener((v, focused) -> { if (!focused) { saveSettings(); buildMagazinePages(); updateUI(); } });
        if (companionNameInput != null) companionNameInput.setOnFocusChangeListener((v, focused) -> { if (!focused) { saveSettings(); buildMagazinePages(); updateUI(); } });
        if (targetAppsInput != null) targetAppsInput.setOnFocusChangeListener((v, focused) -> { if (!focused) { saveSettings(); updateUI(); } });

        bindThemeButton(themeCreamButton, "奶油绿"); bindThemeButton(themeBlueButton, "雾蓝白"); bindThemeButton(themePeachButton, "白桃粉"); bindThemeButton(themeNightButton, "夜航黑"); bindThemeButton(themeMintButton, "薄荷透明"); bindThemeButton(themePurpleButton, "星云紫");

        bindDrawer(drawerLifeDetailsButton, lifeStatusText, "展开详情");
        bindDrawer(drawerThemeButton, drawerTheme, "主题");
        bindDrawer(drawerNowStateButton, drawerNowState, "此刻状态");
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
        bindDrawer(drawerCalendarButton, drawerCalendar, "守护日历");
        bindDrawer(drawerGuidianButton, drawerGuidian, "归电");
        bindDrawer(drawerGuidianSettingsButton, drawerGuidianSettings, "归电设置");
        bindDrawer(drawerVersionButton, drawerVersion, "版本、更新与许可");

        if (tabSettings != null) tabSettings.setOnClickListener(v -> showTab("settings"));
        if (tabSee != null) tabSee.setOnClickListener(v -> showTab("see"));
        if (tabControl != null) tabControl.setOnClickListener(v -> showTab("settings"));
        if (tabLife != null) tabLife.setOnClickListener(v -> showTab("life"));
        if (tabGate != null) tabGate.setOnClickListener(v -> showTab("gate"));
        if (tabDebug != null) tabDebug.setOnClickListener(v -> showTab("settings"));
        if (quickSeeButton != null) quickSeeButton.setOnClickListener(v -> showTab("see"));
        if (quickGuardButton != null) quickGuardButton.setOnClickListener(v -> showTab("gate"));
        CompanionWindowState.recordJourney(this, "打开掌心窗", "回到今天的窗边");
        showTab("life");
        applyBottomNavigationInsets();
        playOpeningWindowAnimation();
        checkForUpdates(false);
    }

    private void bindViews() {
        brandText = findViewById(R.id.brandText); headerTitle = findViewById(R.id.headerTitle); headerSubtitle = findViewById(R.id.headerSubtitle); statusText = findViewById(R.id.statusText); debugText = findViewById(R.id.debugText); lifeStatusText = findViewById(R.id.lifeStatusText); lifeSummaryText = findViewById(R.id.lifeSummaryText); knownAppsText = findViewById(R.id.knownAppsText); homeModeStatusText = findViewById(R.id.homeModeStatusText); gateStatusText = findViewById(R.id.gateStatusText); nowStatePermissionText = findViewById(R.id.nowStatePermissionText);
        heroLabelText = findViewById(R.id.heroLabelText); overviewAdviceText = findViewById(R.id.overviewAdviceText); overviewSecondaryText = findViewById(R.id.overviewSecondaryText); overviewMetaText = findViewById(R.id.overviewMetaText); overviewBatteryText = findViewById(R.id.overviewBatteryText); overviewBatteryDetail = findViewById(R.id.overviewBatteryDetail); overviewAppText = findViewById(R.id.overviewAppText); overviewAppDetail = findViewById(R.id.overviewAppDetail); overviewScreenText = findViewById(R.id.overviewScreenText); overviewScreenDetail = findViewById(R.id.overviewScreenDetail); overviewWeatherText = findViewById(R.id.overviewWeatherText); overviewWeatherDetail = findViewById(R.id.overviewWeatherDetail); weatherLocationsText = findViewById(R.id.weatherLocationsText); themeText = findViewById(R.id.themeText); calendarSummaryText = findViewById(R.id.calendarSummaryText); calendarDetailText = findViewById(R.id.calendarDetailText);
        overviewBatteryLabel = findViewById(R.id.overviewBatteryLabel); overviewAppLabel = findViewById(R.id.overviewAppLabel); overviewScreenLabel = findViewById(R.id.overviewScreenLabel); overviewWeatherLabel = findViewById(R.id.overviewWeatherLabel); quickSeeTitle = findViewById(R.id.quickSeeTitle); quickSeeDetail = findViewById(R.id.quickSeeDetail); quickSeeArrow = findViewById(R.id.quickSeeArrow); quickGuardTitle = findViewById(R.id.quickGuardTitle); quickGuardDetail = findViewById(R.id.quickGuardDetail); quickGuardArrow = findViewById(R.id.quickGuardArrow); quickSeeIcon = findViewById(R.id.quickSeeIcon); quickGuardIcon = findViewById(R.id.quickGuardIcon);
        guidianSummaryText = findViewById(R.id.guidianSummaryText); guidianDetailText = findViewById(R.id.guidianDetailText); guidianSettingsStatusText = findViewById(R.id.guidianSettingsStatusText); guidianAvatarText = findViewById(R.id.guidianAvatarText); versionStatusText = findViewById(R.id.versionStatusText); updateChangelogText = findViewById(R.id.updateChangelogText); licenseSummaryText = findViewById(R.id.licenseSummaryText);
        toggleButton = findViewById(R.id.toggleButton); accessibilityButton = findViewById(R.id.accessibilityButton); usageAccessButton = findViewById(R.id.usageAccessButton); testButton = findViewById(R.id.testButton); openXhsButton = findViewById(R.id.openXhsButton); openTargetAppButton = findViewById(R.id.openTargetAppButton); homeButton = findViewById(R.id.homeButton); backButton = findViewById(R.id.backButton); recentsButton = findViewById(R.id.recentsButton); alarmTestButton = findViewById(R.id.alarmTestButton); notifyTestButton = findViewById(R.id.notifyTestButton); refreshLifeButton = findViewById(R.id.refreshLifeButton);
        addPackageButton = findViewById(R.id.addPackageButton); testPackageButton = findViewById(R.id.testPackageButton); sequenceTestButton = findViewById(R.id.sequenceTestButton); refreshGateButton = findViewById(R.id.refreshGateButton); addGateAppButton = findViewById(R.id.addGateAppButton); addWeatherLocationButton = findViewById(R.id.addWeatherLocationButton); setCurrentWeatherButton = findViewById(R.id.setCurrentWeatherButton);
        testGuidianButton = findViewById(R.id.testGuidianButton); saveGuidianSettingsButton = findViewById(R.id.saveGuidianSettingsButton); chooseGuidianAvatarButton = findViewById(R.id.chooseGuidianAvatarButton); guidianThemeDuskButton = findViewById(R.id.guidianThemeDuskButton); guidianThemeCloudButton = findViewById(R.id.guidianThemeCloudButton); guidianThemeBerryButton = findViewById(R.id.guidianThemeBerryButton);
        themeCreamButton = findViewById(R.id.themeCreamButton); themeBlueButton = findViewById(R.id.themeBlueButton); themePeachButton = findViewById(R.id.themePeachButton); themeNightButton = findViewById(R.id.themeNightButton); themeMintButton = findViewById(R.id.themeMintButton); themePurpleButton = findViewById(R.id.themePurpleButton); drawerThemeButton = findViewById(R.id.drawerThemeButton); drawerNowStateButton = findViewById(R.id.drawerNowStateButton); locationPermissionButton = findViewById(R.id.locationPermissionButton); overlayPermissionButton = findViewById(R.id.overlayPermissionButton);
        drawerConnectionButton = findViewById(R.id.drawerConnectionButton); drawerPermissionButton = findViewById(R.id.drawerPermissionButton); drawerControlTestButton = findViewById(R.id.drawerControlTestButton); drawerKnownAppsButton = findViewById(R.id.drawerKnownAppsButton); drawerHomeModeButton = findViewById(R.id.drawerHomeModeButton); drawerGateAddButton = findViewById(R.id.drawerGateAddButton); drawerReminderButton = findViewById(R.id.drawerReminderButton); drawerCycleButton = findViewById(R.id.drawerCycleButton); drawerDebugButton = findViewById(R.id.drawerDebugButton); drawerLifeDetailsButton = findViewById(R.id.drawerLifeDetailsButton); drawerAppGateButton = findViewById(R.id.drawerAppGateButton); drawerWeatherButton = findViewById(R.id.drawerWeatherButton); drawerVersionButton = findViewById(R.id.drawerVersionButton); checkUpdateButton = findViewById(R.id.checkUpdateButton); downloadUpdateButton = findViewById(R.id.downloadUpdateButton);
        drawerGuidianButton = findViewById(R.id.drawerGuidianButton); drawerGuidianSettingsButton = findViewById(R.id.drawerGuidianSettingsButton); drawerCalendarButton = findViewById(R.id.drawerCalendarButton); saveCalendarEventButton = findViewById(R.id.saveCalendarEventButton);
        remindersEnabled = findViewById(R.id.remindersEnabled); batteryRuleEnabled = findViewById(R.id.batteryRuleEnabled); screenRuleEnabled = findViewById(R.id.screenRuleEnabled); waterRuleEnabled = findViewById(R.id.waterRuleEnabled); restRuleEnabled = findViewById(R.id.restRuleEnabled); cycleEnabled = findViewById(R.id.cycleEnabled); foregroundPopupEnabled = findViewById(R.id.foregroundPopupEnabled); homeModeEnabled = findViewById(R.id.homeModeEnabled); homeModeForceEnabled = findViewById(R.id.homeModeForceEnabled); appGateEnabled = findViewById(R.id.appGateEnabled);
        guidianEnabled = findViewById(R.id.guidianEnabled); guidianRemoteEnabled = findViewById(R.id.guidianRemoteEnabled); guidianFullscreenEnabled = findViewById(R.id.guidianFullscreenEnabled); guidianQuietEnabled = findViewById(R.id.guidianQuietEnabled); calendarLunarEnabled = findViewById(R.id.calendarLunarEnabled); calendarRepeatEnabled = findViewById(R.id.calendarRepeatEnabled); calendarBannerEnabled = findViewById(R.id.calendarBannerEnabled);
        tabSettings = findViewById(R.id.tabSettings); tabSee = findViewById(R.id.tabSee); tabControl = findViewById(R.id.tabControl); tabLife = findViewById(R.id.tabLife); tabGate = findViewById(R.id.tabGate); tabDebug = findViewById(R.id.tabDebug); quickSeeButton = findViewById(R.id.quickSeeButton); quickGuardButton = findViewById(R.id.quickGuardButton);
        sectionSettings = findViewById(R.id.sectionSettings); sectionSee = findViewById(R.id.sectionSee); sectionControl = findViewById(R.id.sectionControl); sectionLife = findViewById(R.id.sectionLife); sectionGate = findViewById(R.id.sectionGate); sectionDebug = findViewById(R.id.sectionDebug);
        heroCard = findViewById(R.id.heroCard); bottomNav = findViewById(R.id.bottomNav);
        drawerTheme = findViewById(R.id.drawerTheme); drawerNowState = findViewById(R.id.drawerNowState); drawerConnection = findViewById(R.id.drawerConnection); drawerPermission = findViewById(R.id.drawerPermission); drawerControlTest = findViewById(R.id.drawerControlTest); drawerKnownApps = findViewById(R.id.drawerKnownApps); drawerHomeMode = findViewById(R.id.drawerHomeMode); drawerGateAdd = findViewById(R.id.drawerGateAdd); drawerReminder = findViewById(R.id.drawerReminder); drawerCycle = findViewById(R.id.drawerCycle); drawerDebug = findViewById(R.id.drawerDebug); drawerAppGate = findViewById(R.id.drawerAppGate); drawerWeather = findViewById(R.id.drawerWeather); drawerVersion = findViewById(R.id.drawerVersion);
        drawerGuidian = findViewById(R.id.drawerGuidian); drawerGuidianSettings = findViewById(R.id.drawerGuidianSettings); drawerCalendar = findViewById(R.id.drawerCalendar);
        serverUrl = findViewById(R.id.serverUrl); tokenInput = findViewById(R.id.tokenInput); deviceInput = findViewById(R.id.deviceInput); intervalInput = findViewById(R.id.intervalInput); cityInput = findViewById(R.id.cityInput); weatherInput = findViewById(R.id.weatherInput); userNameInput = findViewById(R.id.userNameInput); companionNameInput = findViewById(R.id.companionNameInput);
        weatherAliasInput = findViewById(R.id.weatherAliasInput); weatherCityInput = findViewById(R.id.weatherCityInput); weatherNoteInput = findViewById(R.id.weatherNoteInput); calendarTitleInput = findViewById(R.id.calendarTitleInput); calendarDateInput = findViewById(R.id.calendarDateInput); calendarGroupInput = findViewById(R.id.calendarGroupInput); calendarNoteInput = findViewById(R.id.calendarNoteInput);
        batteryThresholdInput = findViewById(R.id.batteryThresholdInput); screenThresholdInput = findViewById(R.id.screenThresholdInput); waterIntervalInput = findViewById(R.id.waterIntervalInput); restIntervalInput = findViewById(R.id.restIntervalInput);
        lastPeriodStartInput = findViewById(R.id.lastPeriodStartInput); cycleLengthInput = findViewById(R.id.cycleLengthInput); periodLengthInput = findViewById(R.id.periodLengthInput); cycleRemindBeforeInput = findViewById(R.id.cycleRemindBeforeInput);
        appAliasInput = findViewById(R.id.appAliasInput); appPackageInput = findViewById(R.id.appPackageInput); targetAppsInput = findViewById(R.id.targetAppsInput); homeThresholdInput = findViewById(R.id.homeThresholdInput); homeCooldownInput = findViewById(R.id.homeCooldownInput); homeTargetInput = findViewById(R.id.homeTargetInput); gateAliasInput = findViewById(R.id.gateAliasInput); gatePackageInput = findViewById(R.id.gatePackageInput);
        guidianIntervalInput = findViewById(R.id.guidianIntervalInput); guidianCooldownInput = findViewById(R.id.guidianCooldownInput); guidianDailyMaxInput = findViewById(R.id.guidianDailyMaxInput); guidianQuietStartInput = findViewById(R.id.guidianQuietStartInput); guidianQuietEndInput = findViewById(R.id.guidianQuietEndInput); guidianTargetPackageInput = findViewById(R.id.guidianTargetPackageInput); guidianPromptInput = findViewById(R.id.guidianPromptInput); guidianReasonInput = findViewById(R.id.guidianReasonInput);
    }

    private void buildMagazinePages() {
        View today = buildTodayMagazine();
        View companion = buildCompanionMagazine();
        View guard = buildGuardMagazine();
        replaceScrollContent(sectionLife, today);
        replaceScrollContent(sectionSee, companion);
        replaceScrollContent(sectionGate, guard);
        if (sectionSee != null) sectionSee.setVisibility(View.GONE);
        if (sectionGate != null) sectionGate.setVisibility(View.GONE);
        if (saveCalendarEventButton != null) saveCalendarEventButton.setOnClickListener(v -> saveCalendarEvent());
        LinearLayout settings = scrollColumn(sectionSettings);
        if (settings != null) {
            for (int i = settings.getChildCount() - 1; i >= 0; i--) {
                Object tag = settings.getChildAt(i).getTag();
                if ("dynamic_privacy".equals(tag) || "dynamic_diary_backup".equals(tag)) settings.removeViewAt(i);
            }
            settings.setPadding(0, 0, 0, dp(42));
            Button privacyButton = actionButton("隐私与记录  ›", false);
            privacyButton.setTag("dynamic_privacy");
            LinearLayout privacy = cardColumn();
            privacy.setTag("dynamic_privacy");
            privacy.setVisibility(View.GONE);
            privacy.addView(title("隐私与记录", 15));
            CheckBox actionToggle = new CheckBox(this);
            actionToggle.setText("在陪伴页显示" + AppPrefs.companionName(this) + "行动记录");
            actionToggle.setTextSize(11);
            actionToggle.setChecked(AppPrefs.get(this).getBoolean(AppPrefs.KEY_SHOW_COMPANION_ACTIONS, true));
            actionToggle.setOnCheckedChangeListener((b, checked) -> {
                AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_SHOW_COMPANION_ACTIONS, checked).apply();
                renderCompanionState(CompanionWindowState.cached(this));
            });
            privacy.addView(actionToggle);
            CheckBox journeyToggle = new CheckBox(this);
            journeyToggle.setText("记录本机今日轨迹");
            journeyToggle.setTextSize(11);
            journeyToggle.setChecked(AppPrefs.get(this).getBoolean(AppPrefs.KEY_JOURNEY_ENABLED, true));
            journeyToggle.setOnCheckedChangeListener((b, checked) -> {
                AppPrefs.get(this).edit().putBoolean(AppPrefs.KEY_JOURNEY_ENABLED, checked).apply();
                updateJourney(new JSONObject());
            });
            privacy.addView(journeyToggle);
            privacy.addView(body("行动记录只展示脱敏摘要；今日轨迹不记录普通滑动、输入内容或屏幕文字。", 9));
            bindDrawer(privacyButton, privacy, "隐私与记录");
            settings.addView(privacyButton, 0, marginBottom(8));
            settings.addView(privacy, 1, marginBottom(8));
            Button diaryBackupButton = actionButton("日记本备份  ›", false);
            diaryBackupButton.setTag("dynamic_diary_backup");
            LinearLayout diaryBackup = cardColumn();
            diaryBackup.setTag("dynamic_diary_backup");
            diaryBackup.setVisibility(View.GONE);
            diaryBackup.addView(title("日记本备份", 15));
            diaryBackup.addView(body("日记只保存在本机。卸载 App 可能导致丢失，建议定期导出备份。", 9), matchWrapTop(6));
            Button importDiary = actionButton("导入日记", false);
            importDiary.setOnClickListener(v -> chooseDiaryImport());
            Button exportDiary = actionButton("导出日记", false);
            exportDiary.setOnClickListener(v -> chooseDiaryExport());
            diaryBackup.addView(importDiary, matchWrapTop(9));
            diaryBackup.addView(exportDiary, matchWrapTop(6));
            bindDrawer(diaryBackupButton, diaryBackup, "日记本备份");
            settings.addView(diaryBackupButton, 2, marginBottom(8));
            settings.addView(diaryBackup, 3, marginBottom(8));
        }
    }

    private View buildTodayMagazine() {
        LinearLayout root = pageColumn();
        root.setClipChildren(false);
        root.setClipToPadding(false);

        FrameLayout heroWrap = new FrameLayout(this);
        heroWrap.setClipChildren(false);
        heroWrap.setClipToPadding(false);

        FrameLayout heroSurface = new FrameLayout(this);
        heroSurface.setBackground(UITheme.current(this).hero());
        heroSurface.setClipToOutline(true);
        heroSurface.setElevation(dp(4));
        FrameLayout.LayoutParams surfaceLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(184), Gravity.TOP);
        surfaceLp.topMargin = dp(16);
        heroWrap.addView(heroSurface, surfaceLp);

        ImageView sideFlower = decorativeImage(R.drawable.decor_rose_hidden_love_side_overlay, ImageView.ScaleType.FIT_CENTER);
        sideFlower.setTag("hero_side_flower_overlay");
        sideFlower.setRotation(-2.5f);
        sideFlower.setElevation(dp(5));
        sideFlower.setAlpha(UITheme.current(this).dark ? .58f : .96f);
        FrameLayout.LayoutParams sideFlowerLp = new FrameLayout.LayoutParams(
                dp(76), dp(164), Gravity.TOP | Gravity.END);
        sideFlowerLp.topMargin = dp(26);
        sideFlowerLp.rightMargin = dp(4);
        heroWrap.addView(sideFlower, sideFlowerLp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(22), dp(24), dp(82), dp(18));
        copy.setElevation(dp(6));
        copy.addView(label("今日窗语", 10));
        overviewAdviceText = title("把今天，\n轻轻收进窗里。", 23);
        overviewAdviceText.setLineSpacing(dp(5), 1f);
        overviewAdviceText.setMaxLines(3);
        copy.addView(overviewAdviceText, matchWrapTop(8));
        overviewSecondaryText = body("晚风会替你放下没有完成的事。", 10.5f);
        overviewSecondaryText.setMaxLines(2);
        copy.addView(overviewSecondaryText, matchWrapTop(7));
        overviewMetaText = body("天气与日历摘要加载中…", 9.5f);
        overviewMetaText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_cloud, 0, 0, 0);
        overviewMetaText.setCompoundDrawablePadding(dp(6));
        copy.addView(overviewMetaText, matchWrapTop(10));
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(184), Gravity.TOP);
        copyLp.topMargin = dp(16);
        heroWrap.addView(copy, copyLp);

        heroCard = heroSurface;
        root.addView(heroWrap, fixedHeight(200, 12));

        LinearLayout mosaic = new LinearLayout(this);
        mosaic.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout focus = editorialCard();
        focus.setPadding(dp(16), dp(13), dp(16), dp(11));
        focus.addView(label("今日专注", 9));
        overviewScreenText = title("-", 20);
        focus.addView(overviewScreenText, matchWrapTop(5));
        overviewScreenDetail = body("解锁次数读取中", 9);
        focus.addView(overviewScreenDetail, matchWrapTop(3));
        FocusProgressView focusProgress = new FocusProgressView(this);
        LinearLayout.LayoutParams focusProgressLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        focusProgressLp.topMargin = dp(14);
        focus.addView(focusProgress, focusProgressLp);
        LinearLayout focusColumn = new LinearLayout(this);
        focusColumn.setOrientation(LinearLayout.VERTICAL);
        focusColumn.addView(focus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(154)));
        mosaic.addView(focusColumn, weighted(1.25f, 0));
        LinearLayout smalls = new LinearLayout(this);
        smalls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout weather = smallStat("窗外", R.drawable.ic_cloud);
        overviewWeatherLabel = (TextView) weather.getChildAt(0);
        overviewWeatherText = (TextView) weather.getChildAt(1);
        overviewWeatherDetail = (TextView) weather.getChildAt(2);
        smalls.addView(weather, weightedVertical(1f, 0));
        LinearLayout next = editorialCard();
        todayNextTitle = label("下一件事", 9);
        todayNextDetail = title("晚间无安排", 13);
        next.addView(todayNextTitle);
        next.addView(todayNextDetail, matchWrapTop(6));
        next.addView(body("把时间留给自己", 9), matchWrapTop(3));
        smalls.addView(next, weightedVertical(1f, 8));
        mosaic.addView(smalls, weighted(1f, 9));
        root.addView(mosaic, fixedHeight(188, 10));

        LinearLayout batteryStrip = editorialCard();
        LinearLayout batteryRow = horizontal();
        overviewBatteryLabel = label("手机电量", 9);
        overviewBatteryLabel.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_battery, 0, 0, 0);
        overviewBatteryLabel.setCompoundDrawablePadding(dp(6));
        overviewBatteryText = title("-", 13);
        overviewBatteryDetail = body("读取中", 9);
        batteryRow.addView(overviewBatteryLabel, weightedWrap(1f, 0));
        batteryRow.addView(overviewBatteryText);
        batteryStrip.addView(batteryRow);
        batteryStrip.addView(overviewBatteryDetail, matchWrapTop(4));
        root.addView(batteryStrip, marginBottom(12));

        LinearLayout nowCard = editorialCard();
        nowCard.addView(title("此刻状态", 14));
        nowStateText = body("姿态、光线和定位正在读取。", 10);
        nowStateText.setLineSpacing(dp(4), 1f);
        nowCard.addView(nowStateText, matchWrapTop(7));
        root.addView(nowCard, marginBottom(12));

        root.addView(sectionRow("今日轨迹", "全部", this::showTodayJourneyDialog), marginBottom(7));
        todayJourneyText = body("今天的轨迹正在整理。", 10);
        todayJourneyText.setLineSpacing(dp(4), 1f);
        todayJourneyText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout journeyCard = editorialCard();
        journeyCard.addView(todayJourneyText);
        root.addView(journeyCard, marginBottom(12));

        LinearLayout links = editorialCard();
        links.addView(title("轻轻打开一扇窗", 14));
        quickSeeButton = featureRow("陪伴", "看" + AppPrefs.companionName(this) + "留下的话与行动", R.drawable.ic_heart_wave, () -> showTab("see"));
        quickGuardButton = featureRow("守护", "重要的人和日子都在这里", R.drawable.ic_shield, () -> showTab("gate"));
        links.addView(quickSeeButton, matchWrapTop(8));
        links.addView(divider());
        links.addView(quickGuardButton);
        root.addView(links, marginBottom(10));

        LinearLayout details = cardColumn();
        details.addView(title("生活细节", 14));
        lifeSummaryText = body("加载中…", 10);
        details.addView(lifeSummaryText, matchWrapTop(7));
        drawerLifeDetailsButton = actionButton("展开详情  ›", false);
        lifeStatusText = body("加载中…", 10);
        lifeStatusText.setVisibility(View.GONE);
        bindDrawer(drawerLifeDetailsButton, lifeStatusText, "展开详情");
        bindDrawer(drawerThemeButton, drawerTheme, "主题");
        bindDrawer(drawerNowStateButton, drawerNowState, "此刻状态");
        details.addView(drawerLifeDetailsButton, matchWrapTop(7));
        details.addView(lifeStatusText, matchWrapTop(7));
        if (refreshLifeButton != null) details.addView(take(refreshLifeButton), matchWrapTop(8));
        root.addView(details);
        return root;
    }

    private View buildCompanionMagazine() {
        LinearLayout root = pageColumn();
        LinearLayout profile = editorialCard();
        profile.setTag("hero_panel");
        profile.setBackground(UITheme.current(this).hero());
        profile.setPadding(dp(17), dp(16), dp(17), dp(14));
        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        companionAvatarView = new SoftAvatarView(this);
        companionAvatarView.setCircle(false);
        companionAvatarView.setCornerDp(23);
        companionAvatarView.setShowDot(true);
        companionAvatarView.setColors(UITheme.current(this).card, UITheme.current(this).line, 0xFF78AE90);
        Drawable companionFallback = getDrawable(R.drawable.ic_heart_wave).mutate();
        companionFallback.setTint(UITheme.current(this).primary);
        companionAvatarView.setFallback(companionFallback);
        companionAvatarView.setOnClickListener(v -> chooseGuidianAvatar());
        profileRow.addView(companionAvatarView, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(label("我的陪伴  ·  " + AppPrefs.companionName(this), 9));
        names.addView(title(AppPrefs.companionName(this) + "在窗边", 18), matchWrapTop(4));
        companionPresenceText = body("正在读取最近行动…", 9);
        names.addView(companionPresenceText, matchWrapTop(4));
        profileRow.addView(names, weightedWrap(1f, 13));
        Button actions = iconButton("➤", "查看" + AppPrefs.companionName(this) + "行动");
        actions.setTextSize(12);
        actions.setTextColor(UITheme.current(this).primary);
        actions.setBackground(UITheme.current(this).soft(15));
        actions.setAlpha(.82f);
        actions.setOnClickListener(v -> showCompanionActionsDialog());
        profileRow.addView(actions, new LinearLayout.LayoutParams(dp(30), dp(30)));
        profile.addView(profileRow);
        LinearLayout presenceStrip = horizontal();
        presenceStrip.setTag("status_strip");
        presenceStrip.setBackground(UITheme.current(this).soft(14));
        presenceStrip.setPadding(dp(10), dp(7), dp(10), dp(7));
        TextView online = label("●  正在陪伴", 8);
        presenceStrip.addView(online, weightedWrap(1f, 0));
        TextView hint = body("轻点头像可更换", 8);
        presenceStrip.addView(hint);
        profile.addView(presenceStrip, matchWrapTop(11));
        root.addView(profile, marginBottom(10));

        FrameLayout whisperFrame = new FrameLayout(this);
        whisperFrame.setBackground(UITheme.current(this).card(22, .45f));
        LinearLayout whisper = new LinearLayout(this);
        whisper.setOrientation(LinearLayout.VERTICAL);
        whisper.setPadding(dp(17), dp(16), dp(17), dp(16));
        LinearLayout whisperHead = horizontal();
        whisperHead.addView(label("最近一句话", 9), weightedWrap(1f, 0));
        Button edit = actionButton("修改", false);
        edit.setTextSize(8);
        edit.setTextColor(UITheme.current(this).primary);
        edit.setBackground(UITheme.current(this).soft(13));
        edit.setPadding(dp(8), 0, dp(8), 0);
        edit.setOnClickListener(v -> showWhisperEditor());
        whisperHead.addView(edit, new LinearLayout.LayoutParams(dp(46), dp(24)));
        whisper.addView(whisperHead);
        sharedWhisperText = title("把今天，轻轻收进窗里。", 15);
        sharedWhisperText.setLineSpacing(dp(3), 1f);
        whisper.addView(sharedWhisperText, matchWrapTop(12));
        sharedWhisperMetaText = body(AppPrefs.companionName(this) + "留下", 9);
        whisper.addView(sharedWhisperMetaText, matchWrapTop(8));
        whisperFrame.addView(whisper, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(whisperFrame, marginBottom(10));

        LinearLayout mosaic = horizontal();
        LinearLayout days = editorialCard();
        days.setPadding(dp(16), dp(12), dp(16), dp(10));
        LinearLayout daysHead = horizontal();
        daysHead.addView(label("和" + AppPrefs.companionName(this) + "一起", 9.5f), weightedWrap(1f, 0));
        TextView editDay = label("修改", 8);
        daysHead.addView(editDay);
        days.addView(daysHead);
        companionDaysText = title("第 1 天", 23);
        days.addView(companionDaysText, matchWrapTop(5));
        companionSinceText = body("从今天开始", 9);
        days.addView(companionSinceText, matchWrapTop(2));
        ImageView daysDecor = decorativeImage(R.drawable.decor_guard_hug, ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams daysDecorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        daysDecorLp.topMargin = dp(3);
        days.addView(daysDecor, daysDecorLp);
        days.setOnClickListener(v -> showCompanionStartDatePicker());
        days.setClickable(true);
        editDay.setOnClickListener(v -> showCompanionStartDatePicker());
        mosaic.addView(days, weighted(1.22f, 0));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout anniversary = editorialCard();
        anniversary.setPadding(dp(15), dp(13), dp(15), dp(12));
        anniversary.addView(label("下个纪念日", 9.5f));
        companionAnniversaryText = title("读取中", 15);
        companionAnniversaryText.setMaxLines(2);
        companionAnniversaryText.setLineSpacing(dp(3), 1f);
        anniversary.addView(companionAnniversaryText, matchWrapTop(7));
        side.addView(anniversary, weightedVertical(1f, 0));
        LinearLayout guidianTile = editorialCard();
        guidianTile.setGravity(Gravity.CENTER);
        guidianTile.setPadding(dp(12), dp(8), dp(12), dp(8));
        ImageView guidianIcon = decorativeImage(R.drawable.decor_guidian_rose, ImageView.ScaleType.CENTER_INSIDE);
        guidianTile.addView(guidianIcon, new LinearLayout.LayoutParams(dp(38), dp(36)));
        TextView guidianName = title("归电", 12);
        guidianName.setGravity(Gravity.CENTER);
        guidianName.setIncludeFontPadding(false);
        guidianTile.addView(guidianName, matchWrapTop(4));
        guidianTile.setOnClickListener(v -> openGuidianFromTile());
        guidianTile.setClickable(true);
        side.addView(guidianTile, weightedVertical(1f, 8));
        mosaic.addView(side, weighted(1f, 9));
        root.addView(mosaic, fixedHeight(178, 10));

        LinearLayout actionPreview = editorialCard();
        LinearLayout actionHead = horizontal();
        actionHead.addView(title(AppPrefs.companionName(this) + "的行动", 14), weightedWrap(1f, 0));
        Button all = actionButton("查看全部", false);
        all.setOnClickListener(v -> showCompanionActionsDialog());
        actionHead.addView(all, new LinearLayout.LayoutParams(dp(82), dp(29)));
        actionPreview.addView(actionHead);
        companionActionsPreview = body("还没有新的行动。", 10);
        companionActionsPreview.setLineSpacing(dp(4), 1f);
        actionPreview.addView(companionActionsPreview, matchWrapTop(8));
        root.addView(actionPreview, marginBottom(10));

        root.addView(sectionHeading("更多陪伴"), marginBottom(8));
        root.addView(actionSettingBlock("TA 的日记", "把今天看见的你，轻轻写下来。", R.drawable.ic_heart_wave, this::showDiaryHomePage), marginBottom(7));
        root.addView(guardSettingBlock("归电", "很久没回来时，" + AppPrefs.companionName(this) + "来敲门", R.drawable.ic_clock, drawerGuidian), marginBottom(7));
        root.addView(actionSettingBlock("看见", "查看屏幕并回应此刻", R.drawable.ic_eye, this::testScreenshot), marginBottom(10));
        return root;
    }

    private void showCompanionHomePage() {
        removeDiaryDateDrawerImmediately();
        diaryPageOpen = false;
        diaryContentOpen = false;
        diarySelectedDate = "";
        diaryCurrentEntryId = "";
        replaceScrollContent(sectionSee, buildCompanionMagazine());
        updateHeader("see");
        updateUI();
    }

    private void showDiaryHomePage() {
        removeDiaryDateDrawerImmediately();
        diaryPageOpen = true;
        diaryContentOpen = false;
        replaceScrollContent(sectionSee, buildDiaryHomePage());
        updateHeader("see");
    }

    private View buildDiaryHomePage() {
        LinearLayout root = pageColumn();
        LinearLayout top = horizontal();
        Button back = iconButton("←", "返回陪伴页");
        back.setOnClickListener(v -> showCompanionHomePage());
        top.addView(back, new LinearLayout.LayoutParams(dp(36), dp(32)));
        top.addView(title("日记本封面", 15), weightedWrap(1f, 10));
        root.addView(top, marginBottom(12));

        JSONArray books = DiaryState.books(this);
        if (books.length() == 0) {
            LinearLayout empty = editorialCard();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(34), dp(20), dp(34));
            empty.addView(title("还没有日记本", 17));
            TextView hint = body("可以先在这里创建一本，也可以让 TA 通过 MCP 为它取名。", 10);
            hint.setGravity(Gravity.CENTER); hint.setLineSpacing(dp(4), 1f);
            empty.addView(hint, matchWrapTop(8));
            Button create = actionButton("创建一本日记", true);
            create.setOnClickListener(v -> {
                JSONObject made = DiaryState.createBook(this, AppPrefs.companionName(this) + "的日记", "把今天轻轻藏起来", DiaryState.DEFAULT_COVER);
                diaryBookId = made.optString("book_id", "");
                showDiaryHomePage();
            });
            empty.addView(create, matchWrapTop(16));
            root.addView(empty, marginBottom(12));
        } else {
            if (DiaryState.bookById(this, diaryBookId) == null) diaryBookId = books.optJSONObject(0).optString("id", "");
            if (books.length() > 1) {
                LinearLayout chooser = horizontal(); chooser.setGravity(Gravity.CENTER);
                for (int i = 0; i < books.length(); i++) {
                    JSONObject choice = books.optJSONObject(i); if (choice == null) continue;
                    TextView chip = label(choice.optString("name", "日记本"), 8);
                    chip.setPadding(dp(9), dp(5), dp(9), dp(5)); chip.setBackground(UITheme.current(this).soft(14));
                    chip.setOnClickListener(v -> { diaryBookId = choice.optString("id", ""); showDiaryHomePage(); });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lp.rightMargin = dp(6); chooser.addView(chip, lp);
                }
                root.addView(chooser, marginBottom(10));
            }
            JSONObject book = DiaryState.bookById(this, diaryBookId);
            FrameLayout cover = buildDiaryCover(book);
            cover.setOnClickListener(v -> playDiaryOpenAnimation(cover));
            cover.setClickable(true); cover.setFocusable(true);
            LinearLayout coverWrap = new LinearLayout(this); coverWrap.setGravity(Gravity.CENTER); coverWrap.addView(cover, new LinearLayout.LayoutParams(dp(244), dp(350)));
            root.addView(coverWrap, marginBottom(14));

            LinearLayout coverActions = horizontal(); coverActions.setGravity(Gravity.CENTER);
            Button rename = actionButton("修改名字", false); rename.setOnClickListener(v -> showRenameDiaryBookDialog());
            Button change = actionButton("更换封面", false); change.setOnClickListener(v -> showDiaryCoverMenu());
            coverActions.addView(rename, new LinearLayout.LayoutParams(dp(104), dp(34)));
            LinearLayout.LayoutParams changeLp = new LinearLayout.LayoutParams(dp(104), dp(34)); changeLp.leftMargin = dp(8); coverActions.addView(change, changeLp);
            root.addView(coverActions, marginBottom(14));
        }

        return root;
    }

    private FrameLayout buildDiaryCover(JSONObject book) {
        FrameLayout cover = new FrameLayout(this);
        GradientDrawable base = new GradientDrawable();
        base.setColors(new int[]{Color.parseColor("#F4CED9"), Color.parseColor("#E8BFCF")});
        base.setOrientation(GradientDrawable.Orientation.TL_BR);
        base.setCornerRadius(dp(18)); base.setStroke(dp(1), Color.parseColor("#DDAFBE"));
        cover.setBackground(base); cover.setElevation(dp(8)); cover.setClipToOutline(true);
        String coverUri = book == null ? "" : book.optString("cover_uri", "");
        if (!coverUri.isEmpty()) {
            try { ImageView image = new ImageView(this); image.setImageURI(Uri.parse(coverUri)); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setAlpha(.72f); cover.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); } catch (Exception ignored) { }
        }
        View spine = new View(this); spine.setBackgroundColor(Color.parseColor("#C98FA4")); spine.setAlpha(.28f);
        FrameLayout.LayoutParams spineLp = new FrameLayout.LayoutParams(dp(14), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START); spineLp.leftMargin = dp(13); cover.addView(spine, spineLp);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setGravity(Gravity.CENTER); copy.setPadding(dp(34), dp(40), dp(24), dp(38));
        TextView tiny = label("PRIVATE NOTEBOOK", 8); tiny.setTextColor(Color.parseColor("#986478")); tiny.setLetterSpacing(.14f); tiny.setGravity(Gravity.CENTER); copy.addView(tiny);
        TextView name = title(book == null ? "TA 的日记" : book.optString("name", "TA 的日记"), 21); name.setTextColor(Color.parseColor("#684451")); name.setGravity(Gravity.CENTER); name.setLineSpacing(dp(4), 1f); copy.addView(name, matchWrapTop(26));
        View rule = new View(this); rule.setBackgroundColor(Color.parseColor("#C18A9E")); LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(dp(84), dp(1)); ruleLp.topMargin = dp(18); ruleLp.gravity = Gravity.CENTER; copy.addView(rule, ruleLp);
        TextView subtitle = body(book == null ? "把今天轻轻藏起来" : book.optString("subtitle", "把今天轻轻藏起来"), 10); subtitle.setTextColor(Color.parseColor("#835E6B")); subtitle.setGravity(Gravity.CENTER); copy.addView(subtitle, matchWrapTop(14));
        TextView open = body("轻触翻开", 8); open.setGravity(Gravity.CENTER); open.setTextColor(Color.parseColor("#9C7482")); copy.addView(open, matchWrapTop(42));
        cover.addView(copy, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return cover;
    }

    private void playDiaryOpenAnimation(View cover) {
        cover.setCameraDistance(dp(1200));
        cover.animate().rotationY(-82f).alpha(.18f).scaleX(.92f).setDuration(260).withEndAction(() -> {
            diaryContentOpen = true; diarySelectedDate = ""; diaryCurrentEntryId = "";
            replaceScrollContent(sectionSee, buildDiaryContentPage(null)); updateHeader("see");
            sectionSee.setRotationY(8f); sectionSee.setAlpha(.25f); sectionSee.animate().rotationY(0f).alpha(1f).setDuration(250).start();
        }).start();
    }

    private View buildDiaryContentPage(JSONArray searchResults) {
        LinearLayout root = pageColumn();
        diaryExpandedPaperView = null;
        diaryExpandedContentView = null;
        diaryExpandedHintView = null;
        JSONObject book = DiaryState.bookById(this, diaryBookId);
        LinearLayout top = horizontal();
        Button back = iconButton("←", "返回日记封面"); back.setOnClickListener(v -> showDiaryHomePage()); top.addView(back, new LinearLayout.LayoutParams(dp(36), dp(32)));
        top.addView(title(book == null ? "TA 的日记" : book.optString("name", "TA 的日记"), 15), weightedWrap(1f, 9));
        Button search = iconButton("⌕", "搜索日记"); search.setOnClickListener(v -> showDiarySearchDialog()); top.addView(search, new LinearLayout.LayoutParams(dp(36), dp(32)));
        Button more = iconButton("⋯", "更多"); more.setOnClickListener(v -> showDiaryMoreMenu()); LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(36), dp(32)); moreLp.leftMargin = dp(5); top.addView(more, moreLp);
        root.addView(top, marginBottom(8));

        LinearLayout timelineBar = horizontal();
        Button dates = actionButton("▤  日期", false); dates.setContentDescription("从左侧打开日期栏"); dates.setOnClickListener(v -> showDiaryDateDrawer());
        timelineBar.addView(dates, new LinearLayout.LayoutParams(dp(76), dp(31)));
        TextView selected = body(diarySelectedDate.isEmpty() ? (searchResults == null ? "全部纸页" : "搜索结果") : diarySelectedDate, 9); timelineBar.addView(selected, weightedWrap(1f, 9));
        if (!diarySelectedDate.isEmpty() || searchResults != null) { TextView clear = label("查看全部", 9); clear.setOnClickListener(v -> showAllDiaryPages()); timelineBar.addView(clear); }
        root.addView(timelineBar, marginBottom(5));

        JSONArray entries = searchResults == null ? DiaryState.listEntries(this, diaryBookId) : searchResults;
        if (!diarySelectedDate.isEmpty() && searchResults == null) root.addView(buildDiaryDatePageHeader(entries), marginBottom(8));
        int shown = 0; String lastDate = "";
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i); if (entry == null) continue;
            String date = entry.optString("date", ""); if (!diarySelectedDate.isEmpty() && !diarySelectedDate.equals(date)) continue;
            if (!date.equals(lastDate) && diarySelectedDate.isEmpty()) { TextView day = label(date, 10); day.setPadding(dp(5), dp(7), 0, dp(3)); root.addView(day); lastDate = date; }
            root.addView(searchResults == null ? buildDiaryEntryPaper(entry) : buildDiarySearchResultCard(entry), marginBottom(10)); shown++;
        }
        if (shown == 0) {
            LinearLayout empty = editorialCard(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(30), dp(20), dp(30));
            String text = searchResults != null ? "没有找到写着这些词的纸页。" : (!diarySelectedDate.isEmpty() ? "这一天还没有留下文字。" : "日记本还是空白的。\nTA 可以通过 MCP 把今天轻轻写下来。");
            TextView emptyText = body(text, 10); emptyText.setGravity(Gravity.CENTER); emptyText.setLineSpacing(dp(4), 1f); empty.addView(emptyText); root.addView(empty);
        }
        return root;
    }

    private View buildDiaryDatePageHeader(JSONArray entries) {
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(15), dp(11), dp(15), dp(11)); header.setBackground(UITheme.current(this).soft(18));
        TextView date = title(diaryDateDisplay(diarySelectedDate), 14); date.setTypeface(Typeface.create("serif", Typeface.BOLD)); header.addView(date);
        int count = 0; for (int i = 0; i < entries.length(); i++) { JSONObject e = entries.optJSONObject(i); if (e != null && diarySelectedDate.equals(e.optString("date", ""))) count++; }
        header.addView(body(count > 0 ? ("这一天留下了 " + count + " 篇日记") : "这一天还没有留下文字。", 8), matchWrapTop(3));
        return header;
    }

    private String diaryDateDisplay(String value) {
        try { Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value); return new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(date); }
        catch (Exception e) { return value; }
    }

    private void showAllDiaryPages() {
        diarySelectedDate = ""; diaryCurrentEntryId = "";
        replaceScrollContent(sectionSee, buildDiaryContentPage(null)); animateDiaryPageSwap();
    }

    private void showDiaryDatePage(String date) {
        showDiaryDatePage(date, "");
    }

    private void showDiaryDatePage(String date, String preferredEntryId) {
        diarySelectedDate = date == null ? "" : date;
        diaryCurrentEntryId = preferredEntryId == null ? "" : preferredEntryId;
        replaceScrollContent(sectionSee, buildDiaryContentPage(null)); animateDiaryPageSwap();
    }

    private void animateDiaryPageSwap() {
        if (sectionSee == null) return;
        sectionSee.scrollTo(0, 0); sectionSee.setAlpha(.3f); sectionSee.setTranslationX(dp(18));
        sectionSee.animate().alpha(1f).translationX(0f).setDuration(220).start();
    }

    private void showDiaryDateDrawer() {
        if (diaryDateDrawerOverlay != null) return;
        ViewGroup content = findViewById(android.R.id.content); if (content == null) return;
        FrameLayout overlay = new FrameLayout(this); diaryDateDrawerOverlay = overlay;
        overlay.setClickable(true); overlay.setFocusable(true); overlay.setBackgroundColor(Color.parseColor("#5C21151C")); overlay.setAlpha(0f);

        LinearLayout panel = new LinearLayout(this); diaryDateDrawerPanel = panel; panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(18), dp(28), dp(14), dp(22)); panel.setElevation(dp(12)); panel.setClickable(true); panel.setOnClickListener(v -> { });
        GradientDrawable paper = new GradientDrawable(); paper.setColor(Color.parseColor("#FFFFFAF7")); float radius = dp(28); paper.setCornerRadii(new float[]{0, 0, radius, radius, radius, radius, 0, 0}); paper.setStroke(dp(1), Color.parseColor("#E9D3DC")); panel.setBackground(paper);

        LinearLayout head = horizontal();
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.addView(label("DATE INDEX", 8)); copy.addView(title("日期纸签", 18), matchWrapTop(3)); copy.addView(body("选择一天，翻开当天的纸页", 8), matchWrapTop(3)); head.addView(copy, weightedWrap(1f, 0));
        Button close = iconButton("×", "收回日期侧边栏"); close.setOnClickListener(v -> closeDiaryDateDrawer(null)); head.addView(close, new LinearLayout.LayoutParams(dp(34), dp(32))); panel.addView(head);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout dates = new LinearLayout(this); dates.setOrientation(LinearLayout.VERTICAL); dates.setPadding(0, dp(12), dp(4), dp(24)); buildDiaryDateDrawerItems(dates); scroll.addView(dates, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f); scrollLp.topMargin = dp(8); panel.addView(scroll, scrollLp);

        int drawerWidth = Math.max(dp(260), (int)(getResources().getDisplayMetrics().widthPixels * .82f));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START); overlay.addView(panel, panelLp);
        overlay.setOnClickListener(v -> closeDiaryDateDrawer(null));
        content.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.setTranslationX(-drawerWidth); overlay.post(() -> { overlay.animate().alpha(1f).setDuration(180).start(); panel.animate().translationX(0f).setDuration(250).start(); });
    }

    private void buildDiaryDateDrawerItems(LinearLayout root) {
        JSONArray all = DiaryState.listEntries(this, diaryBookId);
        if (all.length() == 0) { LinearLayout empty = editorialCard(); TextView text = body("还没有可以翻开的日期。\n先从“更多”里添加一篇日记吧。", 9); text.setGravity(Gravity.CENTER); text.setLineSpacing(dp(4), 1f); empty.addView(text); root.addView(empty, matchWrapTop(12)); return; }
        String lastYear = "", lastMonth = "";
        int i = 0;
        while (i < all.length()) {
            JSONObject first = all.optJSONObject(i); if (first == null) { i++; continue; }
            String date = first.optString("date", ""); int count = 1; int j = i + 1;
            while (j < all.length()) { JSONObject next = all.optJSONObject(j); if (next == null || !date.equals(next.optString("date", ""))) break; count++; j++; }
            String year = date.length() >= 4 ? date.substring(0, 4) : ""; String month = date.length() >= 7 ? date.substring(5, 7) : "";
            if (!year.equals(lastYear)) { TextView yearView = label(year + "年", 9); yearView.setPadding(dp(4), dp(10), 0, dp(4)); root.addView(yearView); lastYear = year; lastMonth = ""; }
            if (!month.equals(lastMonth)) { TextView monthView = title(String.valueOf(parseDiaryNumber(month)) + "月", 13); monthView.setPadding(dp(4), dp(8), 0, dp(5)); root.addView(monthView); lastMonth = month; }
            root.addView(buildDiaryDrawerDateItem(first, date, count), marginBottom(6)); i = j;
        }
    }

    private int parseDiaryNumber(String value) { try { return Integer.parseInt(value); } catch (Exception e) { return 0; } }

    private View buildDiaryDrawerDateItem(JSONObject entry, String date, int count) {
        LinearLayout item = horizontal(); item.setPadding(dp(11), dp(9), dp(10), dp(9));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(diarySelectedDate.equals(date) ? Color.parseColor("#F7E3EB") : Color.parseColor("#FFFFFDFC")); bg.setCornerRadius(dp(17)); bg.setStroke(dp(1), diarySelectedDate.equals(date) ? Color.parseColor("#E3B9C9") : Color.parseColor("#F0E3E7")); item.setBackground(bg);
        String day = date.length() >= 10 ? date.substring(8, 10) : date; TextView dayView = title(String.valueOf(parseDiaryNumber(day)), 19); dayView.setGravity(Gravity.CENTER); dayView.setTextColor(Color.parseColor("#8E6071")); item.addView(dayView, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); String entryTitle = entry.optString("title", "没有标题的一页"); copy.addView(title(entryTitle, 11)); copy.addView(body((count > 1 ? count + " 篇 · " : "") + diaryDateDisplay(date), 8), matchWrapTop(2)); item.addView(copy, weightedWrap(1f, 8)); item.addView(label("›", 17));
        item.setOnClickListener(v -> closeDiaryDateDrawer(() -> showDiaryDatePage(date))); item.setClickable(true); item.setFocusable(true); return item;
    }

    private void closeDiaryDateDrawer(Runnable after) {
        FrameLayout overlay = diaryDateDrawerOverlay; LinearLayout panel = diaryDateDrawerPanel;
        if (overlay == null || panel == null) { if (after != null) after.run(); return; }
        overlay.animate().alpha(0f).setDuration(190).start();
        panel.animate().translationX(-Math.max(panel.getWidth(), dp(280))).setDuration(220).withEndAction(() -> { ViewGroup parent = (ViewGroup)overlay.getParent(); if (parent != null) parent.removeView(overlay); if (diaryDateDrawerOverlay == overlay) { diaryDateDrawerOverlay = null; diaryDateDrawerPanel = null; } if (after != null) after.run(); }).start();
    }

    private void removeDiaryDateDrawerImmediately() {
        if (diaryDateDrawerOverlay != null) { ViewGroup parent = (ViewGroup)diaryDateDrawerOverlay.getParent(); if (parent != null) parent.removeView(diaryDateDrawerOverlay); }
        diaryDateDrawerOverlay = null; diaryDateDrawerPanel = null;
    }

    private View buildDiaryEntryPaper(JSONObject entry) {
        LinearLayout paper = new LinearLayout(this); paper.setTag("diary_paper"); paper.setOrientation(LinearLayout.VERTICAL); paper.setPadding(dp(20), dp(18), dp(20), dp(20)); paper.setBackground(new DiaryPaperDrawable()); paper.setElevation(dp(2));
        String entryId = entry.optString("id", "");
        boolean current = entryId.equals(diaryCurrentEntryId);
        LinearLayout meta = horizontal(); TextView time = label(entry.optString("time_label", entry.optString("created_at", "").length() >= 16 ? entry.optString("created_at").substring(11, 16) : ""), 8); meta.addView(time, weightedWrap(1f, 0)); TextView mood = label(entry.optString("mood", ""), 8); meta.addView(mood); paper.addView(meta);
        TextView heading = title(entry.optString("title", "没有标题的一页"), 16); heading.setTypeface(Typeface.create("serif", Typeface.BOLD)); heading.setTextColor(Color.parseColor("#5A4942")); paper.addView(heading, matchWrapTop(10));
        TextView content = body(entry.optString("content", ""), 11); content.setTypeface(Typeface.create("serif", Typeface.NORMAL)); content.setTextColor(Color.parseColor("#66564E")); content.setLineSpacing(dp(8), 1f); setDiaryEntryExpanded(content, current); paper.addView(content, matchWrapTop(10));
        String tags = diaryTagsText(entry.optJSONArray("tags")); if (!tags.isEmpty()) { TextView tagView = body(tags, 8); tagView.setTextColor(Color.parseColor("#A1767F")); paper.addView(tagView, matchWrapTop(14)); }
        TextView expandHint = label(current ? "收起全文  ↑" : "点击展开全文  ↓", 8); expandHint.setTextColor(Color.parseColor("#A1767F")); paper.addView(expandHint, matchWrapTop(11));
        if (current) { diaryExpandedPaperView = paper; diaryExpandedContentView = content; diaryExpandedHintView = expandHint; }
        paper.setContentDescription(current ? "点击收起这篇日记" : "点击展开这篇日记");
        paper.setOnClickListener(v -> toggleDiaryEntryPaper(paper, content, expandHint, entryId)); paper.setClickable(true); paper.setFocusable(true);
        return paper;
    }

    private void setDiaryEntryExpanded(TextView content, boolean expanded) {
        content.setMaxLines(expanded ? Integer.MAX_VALUE : 4);
        content.setEllipsize(expanded ? null : TextUtils.TruncateAt.END);
    }

    private void toggleDiaryEntryPaper(View paper, TextView content, TextView hint, String entryId) {
        boolean expanding = !entryId.equals(diaryCurrentEntryId);
        if (expanding && diaryExpandedContentView != null && diaryExpandedContentView != content) {
            setDiaryEntryExpanded(diaryExpandedContentView, false);
            if (diaryExpandedHintView != null) diaryExpandedHintView.setText("点击展开全文  ↓");
            if (diaryExpandedPaperView != null) diaryExpandedPaperView.setContentDescription("点击展开这篇日记");
        }
        diaryCurrentEntryId = expanding ? entryId : "";
        setDiaryEntryExpanded(content, expanding);
        hint.setText(expanding ? "收起全文  ↑" : "点击展开全文  ↓");
        paper.setContentDescription(expanding ? "点击收起这篇日记" : "点击展开这篇日记");
        diaryExpandedPaperView = expanding ? paper : null;
        diaryExpandedContentView = expanding ? content : null;
        diaryExpandedHintView = expanding ? hint : null;
        content.setAlpha(.35f); content.animate().alpha(1f).setDuration(180).start();
        paper.requestLayout();
    }

    private View buildDiarySearchResultCard(JSONObject entry) {
        LinearLayout card = editorialCard(); card.setPadding(dp(15), dp(12), dp(15), dp(12));
        LinearLayout head = horizontal(); head.addView(title(entry.optString("title", "没有标题的一页"), 13), weightedWrap(1f, 0)); head.addView(label(entry.optString("mood", ""), 8)); card.addView(head);
        String content = entry.optString("content", ""); if (content.length() > 90) content = content.substring(0, 90) + "…";
        TextView preview = body(content, 9); preview.setMaxLines(3); preview.setLineSpacing(dp(3), 1f); card.addView(preview, matchWrapTop(6));
        String tagText = diaryTagsText(entry.optJSONArray("tags")); if (!tagText.isEmpty()) card.addView(body(tagText, 8), matchWrapTop(7));
        card.setOnClickListener(v -> showDiaryDatePage(entry.optString("date", ""), entry.optString("id", ""))); card.setClickable(true); card.setFocusable(true);
        return card;
    }

    private String diaryTagsText(JSONArray tags) { StringBuilder sb = new StringBuilder(); if (tags != null) for (int i = 0; i < tags.length(); i++) { String tag = tags.optString(i); if (!tag.isEmpty()) { if (sb.length() > 0) sb.append("   "); sb.append("#").append(tag); } } return sb.toString(); }

    private void showDiarySearchDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), 0, dp(8), 0);
        EditText keyword = new EditText(this); keyword.setHint("标题、正文、标签或心情"); box.addView(keyword);
        EditText from = new EditText(this); from.setHint("开始日期 YYYY-MM-DD（可不填）"); box.addView(from);
        EditText to = new EditText(this); to.setHint("结束日期 YYYY-MM-DD（可不填）"); box.addView(to);
        EditText tags = new EditText(this); tags.setHint("标签，用逗号分隔（可不填）"); box.addView(tags);
        new AlertDialog.Builder(this).setTitle("搜索日记").setView(box).setNegativeButton("取消", null).setPositiveButton("搜索", (d, w) -> {
            JSONArray tagArray = new JSONArray(); for (String tag : tags.getText().toString().split("[,，]")) if (!tag.trim().isEmpty()) tagArray.put(tag.trim());
            JSONArray results = DiaryState.search(this, diaryBookId, keyword.getText().toString(), from.getText().toString().trim(), to.getText().toString().trim(), tagArray);
            diarySelectedDate = ""; replaceScrollContent(sectionSee, buildDiaryContentPage(results));
        }).show();
    }

    private void showDiaryMoreMenu() {
        String[] items = new String[]{"添加日记", "重命名日记本", "更换封面", "删除当前日记", "删除整个日记本"};
        new AlertDialog.Builder(this).setTitle("更多").setItems(items, (d, which) -> { if (which == 0) showAddDiaryEntryDialog(); else if (which == 1) showRenameDiaryBookDialog(); else if (which == 2) showDiaryCoverMenu(); else if (which == 3) confirmDeleteDiaryEntry(); else confirmDeleteDiaryBookFirst(); }).show();
    }

    private void showAddDiaryEntryDialog() {
        if (DiaryState.bookById(this, diaryBookId) == null) { Toast.makeText(this, "先创建一本日记本", Toast.LENGTH_SHORT).show(); return; }
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(8), dp(2), dp(8), dp(10));
        EditText titleInput = new EditText(this); titleInput.setHint("标题"); titleInput.setSingleLine(true); fields.addView(titleInput);
        EditText contentInput = new EditText(this); contentInput.setHint("写下今天想留下的内容……"); contentInput.setGravity(Gravity.TOP); contentInput.setMinLines(6); fields.addView(contentInput);
        EditText moodInput = new EditText(this); moodInput.setHint("心情，例如 开心 / 想念 / 安静"); moodInput.setSingleLine(true); fields.addView(moodInput);
        EditText tagsInput = new EditText(this); tagsInput.setHint("标签，用逗号分隔"); tagsInput.setSingleLine(true); fields.addView(tagsInput);
        EditText dateInput = new EditText(this); dateInput.setHint("日期 YYYY-MM-DD"); dateInput.setSingleLine(true); dateInput.setText(diarySelectedDate.isEmpty() ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) : diarySelectedDate); fields.addView(dateInput);
        EditText timeInput = new EditText(this); timeInput.setHint("时间段，例如 晚上"); timeInput.setSingleLine(true); timeInput.setText(diaryDefaultTimeLabel()); fields.addView(timeInput);
        ScrollView scroll = new ScrollView(this); scroll.addView(fields, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("添加一篇日记").setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim(), content = contentInput.getText().toString().trim(), date = dateInput.getText().toString().trim();
            if (title.isEmpty() || content.isEmpty()) { Toast.makeText(this, "请填写标题和正文", Toast.LENGTH_SHORT).show(); return; }
            JSONArray tags = new JSONArray(); for (String tag : tagsInput.getText().toString().split("[,，]")) if (!tag.trim().isEmpty()) tags.put(tag.trim());
            JSONObject saved = DiaryState.writeEntry(this, diaryBookId, title, content, moodInput.getText().toString(), tags, date, timeInput.getText().toString());
            if (!saved.optBoolean("ok", false)) { Toast.makeText(this, "保存失败：" + saved.optString("error", "请检查内容"), Toast.LENGTH_LONG).show(); return; }
            dialog.dismiss(); showDiaryDatePage(saved.optString("date", date), saved.optString("entry_id", "")); Toast.makeText(this, "日记已写进本机", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private String diaryDefaultTimeLabel() { int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return hour < 6 ? "深夜" : (hour < 11 ? "早上" : (hour < 14 ? "中午" : (hour < 18 ? "下午" : "晚上"))); }

    private void showRenameDiaryBookDialog() {
        JSONObject book = DiaryState.bookById(this, diaryBookId); if (book == null) return;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), 0, dp(8), 0);
        EditText name = new EditText(this); name.setHint("日记本名字"); name.setText(book.optString("name", "")); box.addView(name);
        EditText subtitle = new EditText(this); subtitle.setHint("封面小字"); subtitle.setText(book.optString("subtitle", "")); box.addView(subtitle);
        new AlertDialog.Builder(this).setTitle("重命名日记本").setView(box).setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> { DiaryState.renameBook(this, diaryBookId, name.getText().toString(), subtitle.getText().toString()); if (diaryContentOpen) replaceScrollContent(sectionSee, buildDiaryContentPage(null)); else showDiaryHomePage(); }).show();
    }

    private void showDiaryCoverMenu() {
        new AlertDialog.Builder(this).setTitle("更换封面").setItems(new String[]{"掌心窗柔和纸质封面", "从本机选择图片"}, (d, which) -> { if (which == 0) { DiaryState.updateCover(this, diaryBookId, DiaryState.DEFAULT_COVER, ""); showDiaryHomePage(); } else chooseDiaryCover(); }).show();
    }

    private void chooseDiaryCover() { try { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i, REQ_DIARY_COVER); } catch (Exception e) { Toast.makeText(this, "系统相册没有接住封面选择", Toast.LENGTH_SHORT).show(); } }

    private void confirmDeleteDiaryEntry() {
        JSONObject entry = DiaryState.entryById(this, diaryCurrentEntryId); if (entry == null) { Toast.makeText(this, "请先轻点选中一篇日记", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("要删除这篇日记吗？").setMessage("“" + entry.optString("title", "这篇日记") + "”会从本机日记本移除。").setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { DiaryState.deleteEntry(this, diaryCurrentEntryId); diaryCurrentEntryId = ""; replaceScrollContent(sectionSee, buildDiaryContentPage(null)); Toast.makeText(this, "日记已删除", Toast.LENGTH_SHORT).show(); }).show();
    }

    private void confirmDeleteDiaryBookFirst() {
        JSONObject book = DiaryState.bookById(this, diaryBookId); if (book == null) return;
        new AlertDialog.Builder(this).setTitle("要删除整个日记本吗？").setMessage("“" + book.optString("name", "这本日记") + "”里的全部纸页都会被移除。这项操作风险较高。").setNegativeButton("取消", null).setPositiveButton("继续确认", (d, w) -> confirmDeleteDiaryBookSecond(book)).show();
    }

    private void confirmDeleteDiaryBookSecond(JSONObject book) {
        new AlertDialog.Builder(this).setTitle("最后确认一次").setMessage("删除后无法在 App 内撤销。确定删除“" + book.optString("name", "这本日记") + "”吗？").setNegativeButton("取消", null).setPositiveButton("删除日记本", (d, w) -> { DiaryState.deleteBook(this, diaryBookId); diaryBookId = ""; showDiaryHomePage(); Toast.makeText(this, "日记本已删除", Toast.LENGTH_SHORT).show(); }).show();
    }

    private View buildGuardMagazine() {
        LinearLayout root = pageColumn();

        root.addView(sectionHeading("守护功能"), marginBottom(8));
        root.addView(label("日子与地点", 9), marginBottom(6));
        root.addView(actionSettingBlock("守护日历", "纪念日、节日和倒数日", R.drawable.ic_clock, this::showGuardianCalendarDetailPage), marginBottom(7));
        root.addView(guardSettingBlock("天气地区", "家与常用地点", R.drawable.ic_cloud, drawerWeather), marginBottom(11));

        root.addView(label("安心规则", 9), marginBottom(6));
        root.addView(guardSettingBlock("应用门禁", "需要时轻轻守住", R.drawable.ic_shield, drawerAppGate), marginBottom(7));
        root.addView(guardSettingBlock("主动提醒", "电量、休息与喝水", R.drawable.ic_clock, drawerReminder), marginBottom(7));
        root.addView(guardSettingBlock("周期提醒", AppPrefs.userName(this) + "的周期与关怀", R.drawable.ic_heart_wave, drawerCycle), marginBottom(11));

        root.addView(label("回家与设备", 9), marginBottom(6));
        root.addView(guardSettingBlock("回家模式", "回来时打开熟悉的窗", R.drawable.ic_home, drawerHomeMode), marginBottom(10));

        LinearLayout ending = editorialCard();
        ending.setOrientation(LinearLayout.HORIZONTAL);
        ending.setGravity(Gravity.CENTER_VERTICAL);
        ending.setPadding(dp(16), dp(12), dp(16), dp(12));
        ImageView guardCat = new ImageView(this);
        guardCat.setImageResource(R.drawable.decor_sleeping_cat);
        guardCat.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        guardCat.setTag("decorative_art");
        ending.addView(guardCat, new LinearLayout.LayoutParams(dp(36), dp(30)));
        LinearLayout endingCopy = new LinearLayout(this);
        endingCopy.setOrientation(LinearLayout.VERTICAL);
        TextView love = title("Je t’aime.", 15);
        endingCopy.addView(love);
        TextView loveZh = body("掌心窗里的守护，轻一点就够了。", 9);
        endingCopy.addView(loveZh, matchWrapTop(3));
        ending.addView(endingCopy, weightedWrap(1f, 10));
        root.addView(ending);
        return root;
    }


    private void showGuardianCalendarDetailPage() {
        guardianCalendarDetailOpen = true;
        updateHeader(currentTab);
        replaceScrollContent(sectionGate, buildGuardianCalendarDetailPage());
        updateGuardianCalendarView();
    }

    private void showGuardHomePage() {
        guardianCalendarDetailOpen = false;
        replaceScrollContent(sectionGate, buildGuardMagazine());
        updateUI();
    }

    private View buildGuardianCalendarDetailPage() {
        LinearLayout root = pageColumn();

        LinearLayout top = horizontal();
        top.setPadding(dp(2), 0, dp(2), 0);
        Button back = iconButton("←", "返回守护");
        back.setOnClickListener(v -> showGuardHomePage());
        top.addView(back, new LinearLayout.LayoutParams(dp(36), dp(32)));
        View spacer = new View(this);
        top.addView(spacer, weightedWrap(1f, 0));
        Button add = iconButton("＋", "添加日子");
        add.setTextSize(16);
        add.setOnClickListener(v -> showCalendarEventDialog());
        top.addView(add, new LinearLayout.LayoutParams(dp(36), dp(32)));
        root.addView(top, marginBottom(6));
        root.addView(buildGuardianCalendarPage(), marginBottom(12));
        return root;
    }

    private View buildGuardianCalendarPage() {
        LinearLayout root = pageColumn();

        LinearLayout hero = horizontal();
        hero.setPadding(dp(11), dp(7), dp(11), dp(7));
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(UITheme.current(this).soft(18));
        calendarHeroTitle = label("最近", 8);
        calendarHeroTitle.setTextColor(UITheme.current(this).primary);
        calendarHeroDetail = body("日子正在整理。", 9);
        calendarHeroDetail.setSingleLine(true);
        calendarHeroDetail.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(calendarHeroTitle, new LinearLayout.LayoutParams(dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
        hero.addView(calendarHeroDetail, weightedWrap(1f, 6));
        root.addView(hero, marginBottom(8));

        LinearLayout card = editorialCard();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout monthRow = horizontal();
        Button prev = iconButton("←", "上个月");
        prev.setOnClickListener(v -> { calendarVisibleMonth.add(Calendar.MONTH, -1); ensureCalendarDayValid(); updateGuardianCalendarView(); });
        Button next = iconButton("→", "下个月");
        next.setOnClickListener(v -> { calendarVisibleMonth.add(Calendar.MONTH, 1); ensureCalendarDayValid(); updateGuardianCalendarView(); });
        calendarMonthTitle = title("", 15);
        calendarMonthTitle.setGravity(Gravity.CENTER);
        monthRow.addView(prev, new LinearLayout.LayoutParams(dp(34), dp(30)));
        monthRow.addView(calendarMonthTitle, weightedWrap(1f, 8));
        monthRow.addView(next, new LinearLayout.LayoutParams(dp(34), dp(30)));
        card.addView(monthRow);

        calendarGrid = new LinearLayout(this);
        calendarGrid.setOrientation(LinearLayout.VERTICAL);
        card.addView(calendarGrid, matchWrapTop(5));

        LinearLayout legend = horizontal();
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(legendPill("☾ 节日"), weightedWrap(1f, 0));
        legend.addView(legendPill("★ 我们的日子"), weightedWrap(1f, 5));
        legend.addView(legendPill("✿ 生日"), weightedWrap(1f, 5));
        card.addView(legend, matchWrapTop(7));
        root.addView(card, marginBottom(8));

        LinearLayout detail = editorialCard();
        detail.setPadding(dp(14), dp(12), dp(14), dp(12));
        calendarSelectedTitle = title("选中日期", 13);
        calendarSelectedDetail = body("点一个日子看看。", 9);
        calendarSelectedDetail.setLineSpacing(dp(3), 1f);
        detail.addView(calendarSelectedTitle);
        detail.addView(calendarSelectedDetail, matchWrapTop(5));
        calendarSelectedEventsContainer = new LinearLayout(this);
        calendarSelectedEventsContainer.setOrientation(LinearLayout.VERTICAL);
        detail.addView(calendarSelectedEventsContainer, matchWrapTop(5));
        root.addView(detail, marginBottom(12));

        updateGuardianCalendarView();
        return root;
    }


    private TextView legendPill(String text) {
        TextView tv = body(text, 8);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(3), dp(6), dp(3));
        tv.setBackground(UITheme.current(this).soft(14));
        return tv;
    }

    private View calendarSoftDivider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#F1E4EC"));
        return v;
    }

    private void ensureCalendarDayValid() {
        Calendar c = (Calendar) calendarVisibleMonth.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (calendarSelectedDay < 1) calendarSelectedDay = 1;
        if (calendarSelectedDay > max) calendarSelectedDay = max;
    }

    private void updateGuardianCalendarView() {
        if (calendarGrid == null || calendarMonthTitle == null) return;
        ensureCalendarDayValid();
        Calendar month = (Calendar) calendarVisibleMonth.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        int year = month.get(Calendar.YEAR);
        int monthIndex = month.get(Calendar.MONTH);
        calendarMonthTitle.setText(year + "年" + (monthIndex + 1) + "月");
        calendarGrid.removeAllViews();

        try {
            JSONArray upcoming = CalendarState.upcomingOccurrences(this, 80);
            JSONArray monthEvents = CalendarState.occurrencesForMonth(this, year, monthIndex);
            JSONObject nearest = upcoming.length() > 0 ? upcoming.optJSONObject(0) : null;
            if (calendarHeroTitle != null) calendarHeroTitle.setText("最近");
            if (calendarHeroDetail != null) {
                if (nearest != null) {
                    String lunar = nearest.optString("lunar_label", "");
                    calendarHeroDetail.setText(nearest.optString("title", "重要日子") + " · " + nearest.optString("days_text", "") + (lunar.isEmpty() ? "" : " · " + lunar));
                } else calendarHeroDetail.setText("暂时没有临近日子，可以点右上角“＋”添加。");
            }

            LinearLayout week = horizontal();
            String[] ws = new String[]{"一", "二", "三", "四", "五", "六", "日"};
            for (String w : ws) {
                TextView tv = label(w, 9);
                tv.setGravity(Gravity.CENTER);
                week.addView(tv, weightedWrap(1f, 0));
            }
            calendarGrid.addView(week, marginBottom(3));
            calendarGrid.addView(calendarSoftDivider(), fixedHeight(1, 2));

            int firstOffset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            int days = month.getActualMaximum(Calendar.DAY_OF_MONTH);
            int day = 1;
            for (int r = 0; r < 6; r++) {
                LinearLayout row = horizontal();
                row.setGravity(Gravity.CENTER);
                for (int col = 0; col < 7; col++) {
                    if ((r == 0 && col < firstOffset) || day > days) {
                        SpaceCell blank = new SpaceCell(this);
                        row.addView(blank, weighted(1f, 0));
                    } else {
                        final int d = day;
                        row.addView(calendarDayCell(year, monthIndex, d, monthEvents), weighted(1f, 0));
                        day++;
                    }
                }
                calendarGrid.addView(row, fixedHeight(38, 0));
                if (r < 5) calendarGrid.addView(calendarSoftDivider(), fixedHeight(1, 1));
            }
            updateCalendarSelectedDetail(year, monthIndex, calendarSelectedDay, monthEvents);
        } catch (Exception e) {
            calendarGrid.addView(body("守护日历读取失败：" + ScreenshotService.shortMsg(e), 10));
        }
    }

    private View calendarDayCell(int year, int monthIndex, int day, JSONArray upcoming) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(1), dp(1), dp(1), dp(1));
        cell.setTag("calendar_day");
        JSONObject event = firstCalendarEventOn(year, monthIndex, day, upcoming);
        boolean hasEvent = event != null;
        boolean selected = day == calendarSelectedDay;
        Calendar today = Calendar.getInstance();
        boolean isToday = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == monthIndex && today.get(Calendar.DAY_OF_MONTH) == day;
        cell.setBackground(calendarDayBackground(event, selected, isToday));
        TextView num = title(String.valueOf(day), hasEvent || selected ? 11 : 10);
        num.setGravity(Gravity.CENTER);
        cell.addView(num);
        TextView mark = body(hasEvent ? calendarMark(event) : (isToday ? "•" : ""), 7);
        mark.setGravity(Gravity.CENTER);
        cell.addView(mark, matchWrapTop(1));
        cell.setOnClickListener(v -> { calendarSelectedDay = day; updateGuardianCalendarView(); });
        cell.setClickable(true);
        return cell;
    }

    private Drawable calendarDayBackground(JSONObject event, boolean selected, boolean today) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(12));
        int stroke = 0;
        int strokeColor = UITheme.current(this).line;
        if (selected) {
            g.setColor(Color.parseColor("#E8DDF5"));
            stroke = dp(1); strokeColor = UITheme.current(this).primary;
        } else if (event != null) {
            String group = event.optString("group", "");
            if ("festival".equals(group)) g.setColor(Color.parseColor("#FFF0C6"));
            else if ("our_days".equals(group)) g.setColor(Color.parseColor("#F1E8FF"));
            else if ("user".equals(group) || "companion".equals(group)) g.setColor(Color.parseColor("#FFE3EF"));
            else g.setColor(Color.parseColor("#F4EEF8"));
        } else {
            g.setColor(Color.TRANSPARENT);
            if (today) { stroke = dp(1); strokeColor = Color.parseColor("#E6B6CB"); }
        }
        if (stroke > 0) g.setStroke(stroke, strokeColor);
        return g;
    }

    private String calendarMark(JSONObject e) {
        String group = e == null ? "" : e.optString("group", "");
        if ("festival".equals(group)) return "☾";
        if ("our_days".equals(group)) return "★";
        if ("user".equals(group) || "companion".equals(group)) return "✿";
        return "•";
    }

    private JSONObject firstCalendarEventOn(int year, int monthIndex, int day, JSONArray upcoming) {
        String target = String.format(Locale.US, "%04d-%02d-%02d", year, monthIndex + 1, day);
        for (int i = 0; i < upcoming.length(); i++) {
            JSONObject e = upcoming.optJSONObject(i);
            if (e != null && target.equals(e.optString("date", ""))) return e;
        }
        return null;
    }

    private void updateCalendarSelectedDetail(int year, int monthIndex, int day, JSONArray upcoming) {
        if (calendarSelectedTitle == null || calendarSelectedDetail == null || calendarSelectedEventsContainer == null) return;
        String target = String.format(Locale.US, "%04d-%02d-%02d", year, monthIndex + 1, day);
        calendarSelectedTitle.setText((monthIndex + 1) + "月" + day + "日");
        calendarSelectedEventsContainer.removeAllViews();
        int count = 0;
        for (int i = 0; i < upcoming.length(); i++) {
            JSONObject e = upcoming.optJSONObject(i);
            if (e == null || !target.equals(e.optString("date", ""))) continue;
            if (count > 0) calendarSelectedEventsContainer.addView(divider(), matchWrapTop(8));
            count++;
            LinearLayout eventCard = new LinearLayout(this);
            eventCard.setOrientation(LinearLayout.VERTICAL);
            eventCard.setPadding(dp(2), dp(7), dp(2), dp(3));
            LinearLayout head = horizontal();
            TextView eventTitle = title(calendarMark(e) + "  " + e.optString("title", "重要日子"), 12);
            head.addView(eventTitle, weightedWrap(1f, 0));
            String group = e.optString("group_label", "");
            TextView tag = label(group.isEmpty() ? "重要日子" : group, 8);
            tag.setGravity(Gravity.CENTER);
            tag.setPadding(dp(8), dp(3), dp(8), dp(3));
            tag.setBackground(UITheme.current(this).soft(13));
            head.addView(tag);
            eventCard.addView(head);
            String lunar = e.optString("lunar_label", "");
            String days = e.optString("days_text", "");
            String note = e.optString("note", "");
            String meta = (lunar.isEmpty() ? "" : lunar + " · ") + days;
            if (!meta.isEmpty()) eventCard.addView(body(meta, 8), matchWrapTop(5));
            eventCard.addView(body(note.isEmpty() ? "没有备注。" : note, 9), matchWrapTop(4));
            if (!e.optBoolean("builtin", false)) {
                LinearLayout actions = horizontal();
                actions.setGravity(Gravity.END);
                Button edit = actionButton("编辑", false);
                edit.setTextSize(9);
                edit.setOnClickListener(v -> showCalendarEventDialog(e));
                Button delete = actionButton("删除", false);
                delete.setTextSize(9);
                delete.setTextColor(Color.parseColor("#B56F78"));
                GradientDrawable danger = new GradientDrawable();
                danger.setColor(Color.parseColor("#F8ECEE"));
                danger.setCornerRadius(dp(14));
                danger.setStroke(dp(1), Color.parseColor("#E9C9CF"));
                delete.setBackground(danger);
                delete.setOnClickListener(v -> confirmDeleteCalendarEvent(e));
                actions.addView(edit, new LinearLayout.LayoutParams(dp(60), dp(30)));
                LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(60), dp(30));
                deleteLp.leftMargin = dp(7);
                actions.addView(delete, deleteLp);
                eventCard.addView(actions, matchWrapTop(7));
            } else {
                eventCard.addView(body("内置节日", 8), matchWrapTop(5));
            }
            calendarSelectedEventsContainer.addView(eventCard);
        }
        calendarSelectedDetail.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        calendarSelectedDetail.setText("这一天暂时很安静。\n可以点右上角“＋”把它放进窗边。");
    }

    private static class SpaceCell extends View {
        SpaceCell(Context context) { super(context); }
    }

    private final class DiaryPaperDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            paint.setColor(Color.parseColor("#FFF9EC"));
            canvas.drawRoundRect(bounds, dp(16), dp(16), paint);
            paint.setStrokeWidth(dp(.65f)); paint.setColor(Color.parseColor("#E8DDC8")); paint.setAlpha(105);
            for (float y = bounds.top + dp(47); y < bounds.bottom - dp(13); y += dp(26)) canvas.drawLine(bounds.left + dp(13), y, bounds.right - dp(13), y, paint);
            paint.setAlpha(32); paint.setColor(Color.parseColor("#A98F77"));
            for (int i = 0; i < 18; i++) { float x = bounds.left + ((i * 47) % Math.max(1, (int)bounds.width())); float y = bounds.top + ((i * 71) % Math.max(1, (int)bounds.height())); canvas.drawCircle(x, y, dp(.55f), paint); }
            paint.setAlpha(255);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static final class FocusProgressView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);

        FocusProgressView(Context context) {
            super(context);
            track.setStrokeCap(Paint.Cap.ROUND);
            progress.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            UITheme t = UITheme.current(getContext());
            float y = getHeight() / 2f;
            float inset = UITheme.dp(2);
            track.setColor(t.line);
            track.setAlpha(86);
            track.setStrokeWidth(UITheme.dp(3f));
            progress.setColor(t.primary);
            progress.setAlpha(138);
            progress.setStrokeWidth(UITheme.dp(3.2f));
            canvas.drawLine(inset, y, getWidth() - inset, y, track);
            canvas.drawLine(inset, y, inset + (getWidth() - inset * 2) * .58f, y, progress);
        }
    }

    private static final class PhoneOutlineView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PhoneOutlineView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            UITheme t = UITheme.current(getContext());
            paint.setColor(t.primary);
            paint.setAlpha(210);
            paint.setStrokeWidth(UITheme.dp(1.55f));
            float left = UITheme.dp(7), top = UITheme.dp(3);
            float right = getWidth() - UITheme.dp(7), bottom = getHeight() - UITheme.dp(3);
            float radius = UITheme.dp(4.5f);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
            canvas.drawLine(getWidth() * .42f, top + UITheme.dp(3), getWidth() * .58f, top + UITheme.dp(3), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(getWidth() / 2f, bottom - UITheme.dp(3.2f), UITheme.dp(.9f), paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }

    private void replaceScrollContent(View scroll, View content) {
        if (!(scroll instanceof ScrollView)) return;
        ScrollView s = (ScrollView) scroll;
        s.removeAllViews();
        s.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        s.setClipToPadding(false);
    }

    private View take(View view) {
        if (view == null) return new View(this);
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        return view;
    }

    private LinearLayout scrollColumn(View scroll) {
        if (!(scroll instanceof ScrollView) || ((ScrollView) scroll).getChildCount() == 0) return null;
        View child = ((ScrollView) scroll).getChildAt(0);
        return child instanceof LinearLayout ? (LinearLayout) child : null;
    }

    private LinearLayout pageColumn() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0, 0, 0, dp(108)); return l; }
    private LinearLayout cardColumn() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(16), dp(15), dp(16), dp(15)); l.setBackground(UITheme.current(this).card(22, .45f)); return l; }
    private LinearLayout editorialCard() { LinearLayout l = cardColumn(); l.setElevation(dp(.5f)); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView label(String text, float size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(UITheme.current(this).primary); v.setLetterSpacing(.05f); return v; }
    private TextView title(String text, float size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(UITheme.current(this).text); v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return v; }
    private TextView body(String text, float size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(UITheme.current(this).subtext); return v; }
    private TextView sectionHeading(String text) { TextView v = title(text, 14); v.setPadding(dp(3), dp(2), 0, 0); return v; }
    private View sectionRow(String text, String action, Runnable click) { LinearLayout row = horizontal(); row.addView(sectionHeading(text), weightedWrap(1f, 0)); TextView a = label(action, 9); if (click != null) { a.setOnClickListener(v -> click.run()); a.setClickable(true); } row.addView(a); return row; }
    private View divider() { View v = new View(this); v.setBackgroundColor(UITheme.current(this).line); v.setAlpha(.55f); v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))); return v; }
    private ImageView decorativeImage(int drawable, ImageView.ScaleType scaleType) { ImageView art = new ImageView(this); art.setImageResource(drawable); art.setScaleType(scaleType); art.setTag("decorative_art"); art.setAdjustViewBounds(true); return art; }
    private LinearLayout smallStat(String text, int icon) { LinearLayout l = cardColumn(); TextView label = label(text, 9); label.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0); label.setCompoundDrawablePadding(dp(5)); l.addView(label); l.addView(title("-", 17), matchWrapTop(5)); l.addView(body("读取中", 9), matchWrapTop(2)); return l; }
    private View featureRow(String name, String detail, int icon, Runnable click) { LinearLayout row = horizontal(); row.setPadding(dp(3), dp(10), dp(3), dp(10)); ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setColorFilter(UITheme.current(this).primary); row.addView(iv, new LinearLayout.LayoutParams(dp(28), dp(28))); LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.addView(title(name, 13)); text.addView(body(detail, 9), matchWrapTop(2)); row.addView(text, weightedWrap(1f, 10)); TextView arrow = label("›", 18); row.addView(arrow); row.setOnClickListener(v -> click.run()); row.setClickable(true); row.setFocusable(true); return row; }
    private View guardSettingBlock(String name, String detail, int icon, View panel) { LinearLayout card = editorialCard(); card.setPadding(dp(14), dp(12), dp(14), dp(12)); LinearLayout row = horizontal(); ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setColorFilter(UITheme.current(this).primary); iv.setBackground(UITheme.current(this).soft(18)); iv.setPadding(dp(8), dp(8), dp(8), dp(8)); iv.setTag("theme_icon"); row.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38))); LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.addView(title(name, 13)); copy.addView(body(detail, 8), matchWrapTop(2)); row.addView(copy, weightedWrap(1f, 11)); row.addView(label("›", 18)); card.addView(row); card.setOnClickListener(v -> showFeaturePanel(name, panel)); card.setClickable(true); card.setFocusable(true); return card; }
    private View actionSettingBlock(String name, String detail, int icon, Runnable click) { LinearLayout card = editorialCard(); card.setPadding(dp(14), dp(12), dp(14), dp(12)); LinearLayout row = horizontal(); ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setColorFilter(UITheme.current(this).primary); iv.setBackground(UITheme.current(this).soft(18)); iv.setPadding(dp(8), dp(8), dp(8), dp(8)); iv.setTag("theme_icon"); row.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38))); LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.addView(title(name, 13)); copy.addView(body(detail, 8), matchWrapTop(2)); row.addView(copy, weightedWrap(1f, 11)); row.addView(label("›", 18)); card.addView(row); card.setOnClickListener(v -> click.run()); card.setClickable(true); card.setFocusable(true); return card; }
    private View quickAction(String name, String detail, int icon, Runnable click) { LinearLayout card = editorialCard(); card.setGravity(Gravity.CENTER); card.setPadding(dp(8), dp(11), dp(8), dp(9)); ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setColorFilter(UITheme.current(this).primary); card.addView(iv, new LinearLayout.LayoutParams(dp(28), dp(28))); TextView n = title(name, 12); n.setGravity(Gravity.CENTER); card.addView(n, matchWrapTop(5)); TextView d = body(detail, 8); d.setGravity(Gravity.CENTER); card.addView(d, matchWrapTop(2)); card.setOnClickListener(v -> click.run()); card.setClickable(true); return card; }
    private Button actionButton(String text, boolean primary) { Button b = new Button(this); b.setText(text); b.setTextSize(10); b.setAllCaps(false); b.setMinHeight(dp(29)); UITheme t = UITheme.current(this); b.setBackground(primary ? t.pill(true) : t.chip(false)); b.setTextColor(primary ? Color.WHITE : t.text); return b; }
    private Button iconButton(String text, String description) { Button b = actionButton(text, false); b.setContentDescription(description); b.setTextSize(16); b.setPadding(0, 0, 0, 0); return b; }
    private LinearLayout.LayoutParams marginBottom(int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin = dp(bottom); return p; }
    private LinearLayout.LayoutParams matchWrapTop(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin = dp(top); return p; }
    private LinearLayout.LayoutParams weighted(float weight, int left) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight); p.leftMargin = dp(left); return p; }
    private LinearLayout.LayoutParams weightedWrap(float weight, int left) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight); p.leftMargin = dp(left); return p; }
    private LinearLayout.LayoutParams weightedVertical(float weight, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight); p.topMargin = dp(top); return p; }
    private LinearLayout.LayoutParams fixedHeight(int height, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height)); p.bottomMargin = dp(bottom); return p; }

    private void toggleDrawer(Button button, View drawer, String title) {
        if (drawer == null) return;
        boolean show = drawer.getVisibility() != View.VISIBLE;
        drawer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (button != null) button.setText(title + (show ? "  ˄" : "  ›"));
        applyVisualTheme();
        updateGuardianCalendarView();
    }

    private void showWhisperEditor() {
        EditText input = new EditText(this);
        input.setText(CompanionWindowState.whisper(this).optString("content", ""));
        input.setSelection(input.length());
        input.setMaxLines(4);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        new AlertDialog.Builder(this).setTitle("共同窗语").setMessage("你和" + AppPrefs.companionName(this) + "都可以修改这句话。")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
                    CompanionWindowState.updateWhisper(this, input.getText().toString(), "用户", (state, error) -> runOnUiThread(() -> {
                        if (!error.isEmpty()) Toast.makeText(this, "已保存在本机；云端同步失败：" + error, Toast.LENGTH_LONG).show();
                        renderCompanionState(state);
                    }));
                }).show();
    }

    private void showCompanionStartDatePicker() {
        long stored = AppPrefs.get(this).getLong(AppPrefs.KEY_COMPANION_FIRST_DAY, System.currentTimeMillis());
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(stored);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar value = Calendar.getInstance();
            value.set(Calendar.YEAR, year);
            value.set(Calendar.MONTH, month);
            value.set(Calendar.DAY_OF_MONTH, day);
            value.set(Calendar.HOUR_OF_DAY, 0);
            value.set(Calendar.MINUTE, 0);
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            long now = System.currentTimeMillis();
            if (value.getTimeInMillis() > now) {
                Toast.makeText(this, "开始日期不能晚于今天", Toast.LENGTH_SHORT).show();
                return;
            }
            AppPrefs.get(this).edit().putLong(AppPrefs.KEY_COMPANION_FIRST_DAY, value.getTimeInMillis()).apply();
            CompanionWindowState.recordJourney(this, "修改相伴日期", new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(value.getTime()));
            updateCompanionDays();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.setTitle("从哪一天开始一起走过");
        picker.show();
    }

    private void openGuidianFromTile() {
        if (drawerGuidian != null) showFeaturePanel("归电", drawerGuidian);
        else GuidianState.showPrompt(this, true);
        CompanionWindowState.recordJourney(this, "打开归电", "查看" + AppPrefs.companionName(this) + "的归电提醒");
    }

    private void showFeaturePanel(String title, View panel) {
        if (panel == null) return;
        ViewGroup originalParent = panel.getParent() instanceof ViewGroup ? (ViewGroup) panel.getParent() : null;
        int originalIndex = originalParent == null ? -1 : originalParent.indexOfChild(panel);
        ViewGroup.LayoutParams originalParams = panel.getLayoutParams();
        if (originalParent != null) originalParent.removeView(panel);
        panel.setVisibility(View.VISIBLE);
        styleTree(panel, UITheme.current(this), true);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(2), dp(2), dp(2), dp(8));
        scroll.addView(panel, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("完成", null).create();
        dialog.setOnDismissListener(d -> {
            scroll.removeView(panel);
            panel.setVisibility(View.GONE);
            if (originalParent != null) {
                int index = Math.max(0, Math.min(originalIndex, originalParent.getChildCount()));
                if (originalParams != null) originalParent.addView(panel, index, originalParams); else originalParent.addView(panel, index);
            }
            updateUI();
        });
        dialog.show();
    }

    private void showCompanionActionsDialog() {
        if (!AppPrefs.get(this).getBoolean(AppPrefs.KEY_SHOW_COMPANION_ACTIONS, true)) {
            new AlertDialog.Builder(this).setTitle(AppPrefs.companionName(this) + "的行动").setMessage("行动记录已在设置中隐藏。开启后才会在陪伴页展示脱敏摘要。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        JSONArray actions = CompanionWindowState.actions(this);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actions.length(); i++) {
            JSONObject a = actions.optJSONObject(i);
            if (a == null) continue;
            sb.append(CompanionWindowState.elapsed(a.optString("created_at", a.optString("at")))).append(" · ").append(a.optString("type", a.optString("kind", "行动"))).append("\n")
                    .append(a.optString("title", "完成了一次行动"));
            String summary = a.optString("subtitle", a.optString("summary", ""));
            if (!summary.isEmpty()) sb.append("\n").append(summary);
            sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append(AppPrefs.companionName(this)).append("现在安安静静的，暂时没有新的行动。");
        new AlertDialog.Builder(this).setTitle(AppPrefs.companionName(this) + "的行动").setMessage(sb.toString().trim()).setPositiveButton("知道了", null).show();
    }

    private void showTodayJourneyDialog() {
        if (!AppPrefs.get(this).getBoolean(AppPrefs.KEY_JOURNEY_ENABLED, true)) {
            new AlertDialog.Builder(this).setTitle("今日轨迹").setMessage("今日轨迹已在设置中关闭。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        JSONArray items = ActivityEventStore.todayJourney(this, 500);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject e = items.optJSONObject(i); if (e == null) continue;
            sb.append(e.optString("local_time", "--:--")).append(" · ").append(e.optString("title", "今日记录"));
            String subtitle = e.optString("subtitle", ""); if (!subtitle.isEmpty()) sb.append("\n").append(subtitle);
            if (i + 1 < items.length()) sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append("今天还没有留下轨迹。");
        new AlertDialog.Builder(this).setTitle("今日轨迹").setMessage(sb.toString()).setPositiveButton("知道了", null).show();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        if (serverUrl != null) serverUrl.setText(prefs.getString(AppPrefs.KEY_SERVER, ""));
        if (tokenInput != null) tokenInput.setText(prefs.getString(AppPrefs.KEY_TOKEN, ""));
        if (deviceInput != null) deviceInput.setText(prefs.getString(AppPrefs.KEY_DEVICE, "android-phone"));
        if (intervalInput != null) intervalInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_INTERVAL, AppPrefs.DEFAULT_POLL_INTERVAL_MS)));
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
        if (userNameInput != null) userNameInput.setText(AppPrefs.userName(this));
        if (companionNameInput != null) companionNameInput.setText(AppPrefs.companionName(this));
        if (targetAppsInput != null) targetAppsInput.setText(AppPrefs.targetAppsText(this));
        if (homeThresholdInput != null) homeThresholdInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, 10)));
        if (homeCooldownInput != null) homeCooldownInput.setText(String.valueOf(prefs.getInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, 5)));
        if (homeTargetInput != null) homeTargetInput.setText(AppPrefs.homeTargetPackage(this));
        if (appGateEnabled != null) appGateEnabled.setChecked(prefs.getBoolean(AppGate.KEY_ENABLED, true));
        if (guidianEnabled != null) guidianEnabled.setChecked(prefs.getBoolean(GuidianState.KEY_ENABLED, true));
        if (guidianRemoteEnabled != null) guidianRemoteEnabled.setChecked(prefs.getBoolean(GuidianState.KEY_ALLOW_REMOTE, true));
        if (guidianFullscreenEnabled != null) guidianFullscreenEnabled.setChecked(prefs.getBoolean(GuidianState.KEY_FULLSCREEN, true));
        if (guidianQuietEnabled != null) guidianQuietEnabled.setChecked(prefs.getBoolean(GuidianState.KEY_QUIET_ENABLED, true));
        if (guidianIntervalInput != null) guidianIntervalInput.setText(String.valueOf(prefs.getInt(GuidianState.KEY_INTERVAL_MIN, 180)));
        if (guidianCooldownInput != null) guidianCooldownInput.setText(String.valueOf(prefs.getInt(GuidianState.KEY_COOLDOWN_MIN, 60)));
        if (guidianDailyMaxInput != null) guidianDailyMaxInput.setText(String.valueOf(prefs.getInt(GuidianState.KEY_DAILY_MAX, 3)));
        if (guidianQuietStartInput != null) guidianQuietStartInput.setText(prefs.getString(GuidianState.KEY_QUIET_START, "23:30"));
        if (guidianQuietEndInput != null) guidianQuietEndInput.setText(prefs.getString(GuidianState.KEY_QUIET_END, "08:00"));
        if (guidianTargetPackageInput != null) guidianTargetPackageInput.setText(prefs.getString(GuidianState.KEY_TARGET_PACKAGE, AppPrefs.homeTargetPackage(this)));
        if (guidianPromptInput != null) guidianPromptInput.setText(prefs.getString(GuidianState.KEY_PROMPTS, GuidianState.defaultPrompts(this)));
        if (guidianReasonInput != null) guidianReasonInput.setText(prefs.getString(GuidianState.KEY_REASONS, GuidianState.defaultReasons()));
    }

    private void saveSettings() {
        SharedPreferences.Editor e = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit();
        if (serverUrl != null) e.putString(AppPrefs.KEY_SERVER, serverUrl.getText().toString().trim());
        if (tokenInput != null) e.putString(AppPrefs.KEY_TOKEN, tokenInput.getText().toString().trim());
        if (deviceInput != null) e.putString(AppPrefs.KEY_DEVICE, deviceInput.getText().toString().trim().isEmpty() ? "android-phone" : deviceInput.getText().toString().trim());
        if (userNameInput != null) e.putString(AppPrefs.KEY_USER_NAME, userNameInput.getText().toString().trim().isEmpty() ? AppPrefs.DEFAULT_USER_NAME : userNameInput.getText().toString().trim());
        if (companionNameInput != null) e.putString(AppPrefs.KEY_COMPANION_NAME, companionNameInput.getText().toString().trim().isEmpty() ? AppPrefs.DEFAULT_COMPANION_NAME : companionNameInput.getText().toString().trim());
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
        if (targetAppsInput != null) {
            String normalizedTargets = AppPrefs.normalizeTargetApps(targetAppsInput.getText().toString());
            e.putString(AppPrefs.KEY_TARGET_APPS, normalizedTargets);
            StringBuilder packages = new StringBuilder();
            for (String line : normalizedTargets.split("\\n")) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2 && AppPrefs.isPackageLike(parts[1].trim())) {
                    if (packages.length() > 0) packages.append(',');
                    packages.append(parts[1].trim());
                }
            }
            e.putString(AppPrefs.KEY_HOME_WATCH_PACKAGES, packages.toString());
        }
        if (homeThresholdInput != null) e.putInt(AppPrefs.KEY_HOME_THRESHOLD_MIN, parseInt(homeThresholdInput.getText().toString().trim(), 10, 1, 240));
        if (homeCooldownInput != null) e.putInt(AppPrefs.KEY_HOME_COOLDOWN_MIN, parseInt(homeCooldownInput.getText().toString().trim(), 5, 1, 240));
        if (homeTargetInput != null) e.putString(AppPrefs.KEY_HOME_TARGET_PACKAGE, AppPrefs.saveHomeTarget(this, homeTargetInput.getText().toString().trim()));
        if (appGateEnabled != null) e.putBoolean(AppGate.KEY_ENABLED, appGateEnabled.isChecked());
        if (guidianEnabled != null) e.putBoolean(GuidianState.KEY_ENABLED, guidianEnabled.isChecked());
        if (guidianRemoteEnabled != null) e.putBoolean(GuidianState.KEY_ALLOW_REMOTE, guidianRemoteEnabled.isChecked());
        if (guidianFullscreenEnabled != null) e.putBoolean(GuidianState.KEY_FULLSCREEN, guidianFullscreenEnabled.isChecked());
        if (guidianQuietEnabled != null) e.putBoolean(GuidianState.KEY_QUIET_ENABLED, guidianQuietEnabled.isChecked());
        if (guidianIntervalInput != null) e.putInt(GuidianState.KEY_INTERVAL_MIN, parseInt(guidianIntervalInput.getText().toString().trim(), 180, 15, 10080));
        if (guidianCooldownInput != null) e.putInt(GuidianState.KEY_COOLDOWN_MIN, parseInt(guidianCooldownInput.getText().toString().trim(), 60, 0, 10080));
        if (guidianDailyMaxInput != null) e.putInt(GuidianState.KEY_DAILY_MAX, parseInt(guidianDailyMaxInput.getText().toString().trim(), 3, 0, 99));
        if (guidianQuietStartInput != null) e.putString(GuidianState.KEY_QUIET_START, guidianQuietStartInput.getText().toString().trim().isEmpty() ? "23:30" : guidianQuietStartInput.getText().toString().trim());
        if (guidianQuietEndInput != null) e.putString(GuidianState.KEY_QUIET_END, guidianQuietEndInput.getText().toString().trim().isEmpty() ? "08:00" : guidianQuietEndInput.getText().toString().trim());
        if (guidianTargetPackageInput != null) e.putString(GuidianState.KEY_TARGET_PACKAGE, AppPrefs.saveHomeTarget(this, guidianTargetPackageInput.getText().toString().trim()));
        if (guidianPromptInput != null) e.putString(GuidianState.KEY_PROMPTS, guidianPromptInput.getText().toString());
        if (guidianReasonInput != null) e.putString(GuidianState.KEY_REASONS, guidianReasonInput.getText().toString());
        e.apply();
    }

    private void showTab(String tab) {
        currentTab = tab;
        setVisible(sectionLife, "life".equals(tab)); setVisible(sectionSee, "see".equals(tab)); setVisible(sectionGate, "gate".equals(tab)); setVisible(sectionSettings, "settings".equals(tab));
        setVisible(sectionControl, false); setVisible(sectionDebug, false);
        setTabSelected(tabLife, "life".equals(tab)); setTabSelected(tabSee, "see".equals(tab)); setTabSelected(tabGate, "gate".equals(tab)); setTabSelected(tabSettings, "settings".equals(tab));
        updateHeader(tab);
        applyVisualTheme();
        View active = "life".equals(tab) ? sectionLife : ("see".equals(tab) ? sectionSee : ("gate".equals(tab) ? sectionGate : sectionSettings));
        if (active != null) {
            active.setAlpha(0f);
            active.setTranslationY(dp(8));
            active.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
    }
    private void updateHeader(String tab) {
        if (headerTitle == null || headerSubtitle == null) return;
        String date = new SimpleDateFormat("M月d日 · EEEE", Locale.CHINA).format(new Date());
        if ("life".equals(tab)) { headerTitle.setText(greeting() + "，" + AppPrefs.userName(this)); headerSubtitle.setText(date); }
        else if ("see".equals(tab)) {
            if (diaryPageOpen) { headerTitle.setText("TA 的日记"); headerSubtitle.setText(diaryContentOpen ? "翻开纸页，读一读 TA 留下的今天。" : "把今天看见的你，轻轻写下来。"); }
            else { headerTitle.setText("总有人在窗边等你"); headerSubtitle.setText("记录温柔的小事，也看见" + AppPrefs.companionName(this) + "的陪伴。"); }
        }
        else if ("gate".equals(tab)) {
            if (guardianCalendarDetailOpen) { headerTitle.setText("守护日历"); headerSubtitle.setText("把重要日子轻轻放在窗边。"); }
            else { headerTitle.setText("Toujours à tes côtés"); headerSubtitle.setText("一直在你身边 · 守护状态与重要日子"); }
        }
        else { headerTitle.setText("设置这扇窗"); headerSubtitle.setText("调整" + AppPrefs.companionName(this) + "、窗面、提醒与隐私记录。"); }
        if (brandText != null) brandText.setText("掌心窗  ·  " + ("life".equals(tab) ? "今天" : ("see".equals(tab) ? (diaryPageOpen ? "TA 的日记" : "陪伴") : ("gate".equals(tab) ? (guardianCalendarDetailOpen ? "守护日历" : "守护") : "设置"))));
    }
    private String greeting() { int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return h < 5 ? "夜深了" : (h < 11 ? "早上好" : (h < 14 ? "午安" : (h < 18 ? "下午好" : (h < 23 ? "晚上好" : "夜深了")))); }
    private void setVisible(View v, boolean visible) { if (v != null) v.setVisibility(visible ? View.VISIBLE : View.GONE); }
    private void setTabSelected(Button b, boolean selected) {
        if (b == null) return;
        UITheme t = UITheme.current(this);
        b.setTextColor(selected ? t.primary : t.subtext);
        b.setAlpha(selected ? 1f : .56f);
        b.setBackground(navTabBackground(t, selected));
        b.setTextSize(9);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        b.setPadding(dp(6), dp(4), dp(6), dp(3));
        b.setGravity(Gravity.CENTER);
        tintCompoundDrawables(b, selected ? t.primary : t.subtext);
        Drawable top = b.getCompoundDrawables()[1];
        if (top != null) top.setBounds(0, 0, dp(22), dp(22));
        b.setCompoundDrawablePadding(dp(1));
    }

    private Drawable navTabBackground(UITheme t, boolean selected) {
        if (!selected || Build.VERSION.SDK_INT < 23) return colorDrawable(Color.TRANSPARENT);
        Drawable island = t.navIconIsland();
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(island, dp(24), dp(2), dp(24), dp(20));
        return new LayerDrawable(new Drawable[]{inset});
    }

    private GradientDrawable colorDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private void applyBottomNavigationInsets() {
        if (bottomNav == null) return;
        final int baseBottom = dp(6);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            bottomNav.setOnApplyWindowInsetsListener((view, insets) -> {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                int systemBottom = insets.getSystemWindowInsetBottom();
                lp.bottomMargin = baseBottom + Math.max(0, systemBottom);
                view.setLayoutParams(lp);
                return insets;
            });
            bottomNav.requestApplyInsets();
        }
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

    private void bindGuidianThemeButton(Button b, String name) {
        if (b == null) return;
        b.setOnClickListener(v -> {
            AppPrefs.get(this).edit().putString(GuidianState.KEY_THEME, name).apply();
            updateUI();
            Toast.makeText(this, "归电主题已切换为 " + name, Toast.LENGTH_SHORT).show();
        });
    }


    private void applyVisualTheme() {
        UITheme t = UITheme.current(this);
        View root = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (root != null) root.setBackground(t.background());
        getWindow().setStatusBarColor(t.bgTop);
        getWindow().setNavigationBarColor(t.bgBottom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            if (t.dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (t.dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
        styleTree(root, t, false);
        setTabSelected(tabLife, "life".equals(currentTab));
        setTabSelected(tabSee, "see".equals(currentTab));
        setTabSelected(tabGate, "gate".equals(currentTab));
        setTabSelected(tabSettings, "settings".equals(currentTab));
        styleThemeButton(themeCreamButton, t, "奶油绿"); styleThemeButton(themeBlueButton, t, "雾蓝白"); styleThemeButton(themePeachButton, t, "白桃粉"); styleThemeButton(themeNightButton, t, "夜航黑"); styleThemeButton(themeMintButton, t, "薄荷透明"); styleThemeButton(themePurpleButton, t, "星云紫");
        styleGuidianThemeButton(guidianThemeDuskButton, t, "粉色"); styleGuidianThemeButton(guidianThemeCloudButton, t, "白色"); styleGuidianThemeButton(guidianThemeBerryButton, t, "黑色");
        if (bottomNav != null) { bottomNav.setBackground(t.navBar()); bottomNav.setElevation(dp(.8f)); }
        if (heroCard != null) { heroCard.setBackground(t.hero()); heroCard.setElevation(dp(4)); }
        styleQuickCard(quickSeeButton, quickSeeIcon, quickSeeTitle, quickSeeDetail, quickSeeArrow, t);
        styleQuickCard(quickGuardButton, quickGuardIcon, quickGuardTitle, quickGuardDetail, quickGuardArrow, t);
        if (brandText != null) brandText.setTextColor(t.primary);
        if (heroLabelText != null) { heroLabelText.setTextColor(t.primary); heroLabelText.setBackground(t.chip(true)); heroLabelText.setPadding(dp(9), dp(4), dp(9), dp(4)); }
        if (overviewAdviceText != null) overviewAdviceText.setTextColor(t.text);
        if (overviewSecondaryText != null) overviewSecondaryText.setTextColor(t.text);
        if (overviewMetaText != null) overviewMetaText.setTextColor(t.subtext);
        TextView[] statLabels = new TextView[]{overviewBatteryLabel, overviewAppLabel, overviewScreenLabel, overviewWeatherLabel};
        for (TextView label : statLabels) { if (label != null) { label.setTextColor(t.subtext); tintCompoundDrawables(label, t.primary); } }
        TextView[] statValues = new TextView[]{overviewBatteryText, overviewAppText, overviewScreenText, overviewWeatherText};
        for (TextView value : statValues) if (value != null) value.setTextColor(t.text);
        TextView[] statDetails = new TextView[]{overviewBatteryDetail, overviewAppDetail, overviewScreenDetail, overviewWeatherDetail};
        for (TextView detail : statDetails) if (detail != null) detail.setTextColor(t.subtext);
        if (tabSettings != null) { tabSettings.setBackground(t.chip("settings".equals(currentTab))); tabSettings.setPadding(dp(7), dp(7), dp(7), dp(7)); tintCompoundDrawables(tabSettings, t.primary); }
        styleDrawerButton(drawerLifeDetailsButton, t); styleDrawerButton(drawerThemeButton, t); styleDrawerButton(drawerNowStateButton, t); styleDrawerButton(drawerCalendarButton, t); styleDrawerButton(drawerAppGateButton, t); styleDrawerButton(drawerWeatherButton, t); styleDrawerButton(drawerGuidianButton, t); styleDrawerButton(drawerGuidianSettingsButton, t); styleDrawerButton(drawerConnectionButton, t); styleDrawerButton(drawerPermissionButton, t); styleDrawerButton(drawerControlTestButton, t); styleDrawerButton(drawerKnownAppsButton, t); styleDrawerButton(drawerHomeModeButton, t); styleDrawerButton(drawerGateAddButton, t); styleDrawerButton(drawerReminderButton, t); styleDrawerButton(drawerCycleButton, t); styleDrawerButton(drawerDebugButton, t);
        if (statusText != null) { statusText.setBackground(t.soft(18)); statusText.setPadding(dp(12), dp(7), dp(12), dp(7)); }
        if (testGuidianButton != null) { testGuidianButton.setBackground(t.pill(true)); testGuidianButton.setTextColor(Color.WHITE); }
        if (saveGuidianSettingsButton != null) { saveGuidianSettingsButton.setBackground(t.pill(true)); saveGuidianSettingsButton.setTextColor(Color.WHITE); }
        if (toggleButton != null) { toggleButton.setBackground(serviceRunning ? dangerPill(t) : t.pill(true)); toggleButton.setTextColor(Color.WHITE); }
        if (themeText != null) themeText.setTextColor(t.subtext);
        if (statusText != null) statusText.setTextColor(serviceRunning && isAccessibilityServiceEnabled() ? t.primary : t.subtext);
        if (companionAvatarView != null) {
            companionAvatarView.setColors(t.card, t.line, 0xFF78AE90);
        }
        TextView[] companionText = new TextView[]{companionPresenceText, sharedWhisperMetaText, companionActionsPreview, todayJourneyText, guardOverviewText};
        for (TextView v : companionText) if (v != null) v.setTextColor(t.subtext);
        if (sharedWhisperText != null) sharedWhisperText.setTextColor(t.text);
    }

    private GradientDrawable dangerPill(UITheme t) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(t.danger);
        g.setCornerRadius(dp(15));
        return g;
    }

    private static final class GuardianRingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = .82f;

        GuardianRingView(Context context) {
            super(context);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setProgress(float value) { progress = Math.max(.08f, Math.min(1f, value)); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            UITheme t = UITheme.current(getContext());
            float stroke = UITheme.dp(8);
            track.setStrokeWidth(stroke); track.setColor(t.line); track.setAlpha(115);
            progressPaint.setStrokeWidth(stroke); progressPaint.setColor(t.primary);
            float pad = stroke + UITheme.dp(8);
            RectF oval = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
            canvas.drawArc(oval, -90, 360, false, track);
            canvas.drawArc(oval, -90, 360 * progress, false, progressPaint);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float s = UITheme.dp(15);
            iconPaint.setColor(t.primary); iconPaint.setStrokeWidth(UITheme.dp(2.1f));
            android.graphics.Path shield = new android.graphics.Path();
            shield.moveTo(cx, cy - s); shield.lineTo(cx + s * .72f, cy - s * .52f);
            shield.lineTo(cx + s * .62f, cy + s * .52f); shield.lineTo(cx, cy + s);
            shield.lineTo(cx - s * .62f, cy + s * .52f); shield.lineTo(cx - s * .72f, cy - s * .52f);
            shield.close(); canvas.drawPath(shield, iconPaint);
        }
    }

    private void styleQuickCard(View card, ImageView icon, TextView title, TextView detail, TextView arrow, UITheme t) {
        if (card != null) { card.setBackground(t.soft(18)); card.setElevation(dp(1)); }
        if (icon != null) icon.setColorFilter(t.primary);
        if (title != null) title.setTextColor(t.text);
        if (detail != null) detail.setTextColor(t.subtext);
        if (arrow != null) arrow.setTextColor(t.primary);
    }

    private void playOpeningWindowAnimation() {
        if (openingShownForProcess) return;
        openingShownForProcess = true;
        final ViewGroup content = findViewById(android.R.id.content);
        if (content == null) return;
        content.post(() -> {
            if (isFinishing() || content.getWidth() <= 0 || content.getHeight() <= 0) return;
            UITheme t = UITheme.current(this);
            FrameLayout splash = new FrameLayout(this);
            splash.setBackground(t.background());
            ImageView illustration = new ImageView(this);
            illustration.setImageResource(R.drawable.cat_window_splash);
            illustration.setScaleType(ImageView.ScaleType.CENTER_CROP);
            splash.addView(illustration, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView wordmark = new TextView(this);
            wordmark.setText("掌心窗"); wordmark.setTextSize(11); wordmark.setLetterSpacing(0.18f); wordmark.setGravity(Gravity.CENTER); wordmark.setTextColor(t.primary); wordmark.setAlpha(0f);
            FrameLayout.LayoutParams wordmarkLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38), Gravity.BOTTOM); wordmarkLp.bottomMargin = dp(38);
            splash.addView(wordmark, wordmarkLp);
            content.addView(splash, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View page = content.getChildAt(0);
            if (page != null) page.setAlpha(0f);
            illustration.setAlpha(0f); illustration.setScaleX(0.91f); illustration.setScaleY(0.91f); illustration.setTranslationY(dp(18));
            illustration.animate().alpha(1f).scaleX(1.035f).scaleY(1.035f).translationY(0f).setDuration(780).withEndAction(() ->
                    illustration.animate().scaleX(1f).scaleY(1f).translationY(-dp(5)).setDuration(260).start()).start();
            wordmark.animate().alpha(0.76f).setStartDelay(500).setDuration(360).start();
            splash.animate().alpha(0f).setStartDelay(1480).setDuration(420).withEndAction(() -> content.removeView(splash)).start();
            if (page != null) page.animate().alpha(1f).setStartDelay(1420).setDuration(500).start();
        });
    }

    private void tintCompoundDrawables(TextView view, int color) {
        if (view == null) return;
        Drawable[] icons = view.getCompoundDrawables();
        for (Drawable icon : icons) {
            if (icon != null) icon.mutate().setTint(color);
        }
    }

    private void styleTree(View v, UITheme t, boolean insideCard) {
        if (v == null) return;
        if (v instanceof ImageView && "hero_side_flower_overlay".equals(v.getTag())) v.setAlpha(t.dark ? .58f : .96f);
        else if (v instanceof ImageView && "decorative_art".equals(v.getTag())) v.setAlpha(t.dark ? .52f : .92f);
        if (v instanceof ImageView && "theme_icon".equals(v.getTag())) { ((ImageView) v).setColorFilter(t.primary); v.setBackground(t.soft(18)); }
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
            if (v instanceof LinearLayout && v.getBackground() != null && !"calendar_day".equals(v.getTag()) && !"diary_paper".equals(v.getTag()) && v != ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0)) {
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                boolean bottomBar = lp != null && lp.height <= dp(52) && lp.height >= dp(42);
                if (!bottomBar) {
                    if ("hero_panel".equals(v.getTag())) v.setBackground(t.hero());
                    else if ("status_strip".equals(v.getTag())) v.setBackground(t.soft(14));
                    else v.setBackground(t.card(20, 0.45f));
                    v.setElevation(dp(1));
                    childInsideCard = true;
                }
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

    private void styleGuidianThemeButton(Button b, UITheme t, String name) {
        if (b == null) return;
        boolean selected = name.equals(GuidianState.themeName(this));
        b.setBackground(t.chip(selected));
        b.setTextColor(selected ? t.primary : t.subtext);
        b.setMinHeight(dp(29));
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        if (lp != null) { lp.height = dp(30); b.setLayoutParams(lp); }
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onResume() { super.onResume(); serviceRunning = CompanionService.isRunning(); updateUI(); if (recentlyOpenedAccessibilitySettings()) scheduleAccessibilityFollowupChecks(); uiHandler.removeCallbacks(refreshTick); uiHandler.post(refreshTick); }
    @Override protected void onPause() { uiHandler.removeCallbacks(refreshTick); super.onPause(); }

    @Override public void onBackPressed() {
        if (diaryDateDrawerOverlay != null) { closeDiaryDateDrawer(null); return; }
        if (diaryPageOpen) { if (diaryContentOpen) showDiaryHomePage(); else showCompanionHomePage(); return; }
        if (guardianCalendarDetailOpen) { showGuardHomePage(); return; }
        super.onBackPressed();
    }


    private JSONObject saveCalendarEventValues(String title, String date, String group, String note, boolean lunar, boolean repeat, boolean banner) {
        return saveCalendarEventValues("", title, date, group, note, lunar, repeat, banner);
    }

    private JSONObject saveCalendarEventValues(String id, String title, String date, String group, String note, boolean lunar, boolean repeat, boolean banner) {
        title = title == null ? "" : title.trim();
        date = date == null ? "" : date.trim();
        group = group == null ? "" : group.trim();
        note = note == null ? "" : note.trim();
        if (group.isEmpty()) group = "our_days";
        if (title.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "先填标题和日期", Toast.LENGTH_SHORT).show();
            JSONObject out = new JSONObject();
            try { out.put("ok", false).put("error", "title_or_date_required"); } catch (Exception ignored) { }
            return out;
        }
        boolean editing = id != null && !id.trim().isEmpty();
        JSONObject saved = CalendarState.upsertEvent(this, editing ? id : "", title, lunar ? "lunar" : "solar", date, 0, 0, false, repeat ? "yearly" : "none", group, note, 3, banner, "user");
        boolean ok = saved.optBoolean("ok", false);
        if (ok) CompanionWindowState.recordJourney(this, editing ? "编辑守护日历" : "添加守护日历", (editing ? "更新" : "记下") + "「" + title + "」");
        DebugState.append(this, ok ? ("已" + (editing ? "更新" : "保存") + "守护日历：" + title + " · " + date) : ("守护日历保存失败：" + saved.toString()));
        Toast.makeText(this, ok ? (editing ? "已更新这个日子" : "已保存到守护日历") : ("保存失败：" + saved.optString("error", "请检查日期")), Toast.LENGTH_LONG).show();
        updateUI();
        return saved;
    }

    private void saveCalendarEvent() {
        String title = calendarTitleInput == null ? "" : calendarTitleInput.getText().toString().trim();
        String date = calendarDateInput == null ? "" : calendarDateInput.getText().toString().trim();
        String group = calendarGroupInput == null ? "our_days" : calendarGroupInput.getText().toString().trim();
        String note = calendarNoteInput == null ? "" : calendarNoteInput.getText().toString().trim();
        boolean lunar = calendarLunarEnabled != null && calendarLunarEnabled.isChecked();
        boolean repeat = calendarRepeatEnabled == null || calendarRepeatEnabled.isChecked();
        boolean banner = calendarBannerEnabled == null || calendarBannerEnabled.isChecked();
        JSONObject saved = saveCalendarEventValues(title, date, group, note, lunar, repeat, banner);
        if (saved.optBoolean("ok", false)) {
            if (calendarTitleInput != null) calendarTitleInput.setText("");
            if (calendarDateInput != null) calendarDateInput.setText("");
            if (calendarNoteInput != null) calendarNoteInput.setText("");
        }
    }

    private void showCalendarEventDialog() {
        showCalendarEventDialog(null);
    }

    private void showCalendarEventDialog(JSONObject occurrence) {
        String eventId = occurrence == null ? "" : occurrence.optString("id", "");
        JSONObject original = eventId.isEmpty() ? null : CalendarState.eventById(this, eventId);
        boolean editing = original != null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(2), dp(8), 0);
        EditText titleInput = new EditText(this);
        titleInput.setHint("标题，例如 生日 / 纪念日 / 项目节点");
        titleInput.setSingleLine(true);
        EditText dateInput = new EditText(this);
        dateInput.setHint("阳历：2026-08-23 或 08-23；农历：07-07");
        dateInput.setSingleLine(true);
        EditText groupInput = new EditText(this);
        groupInput.setHint("分组：我们的日子 / 用户 / 陪伴对象 / 节日 / 学习 / 项目 / 生活");
        groupInput.setSingleLine(true);
        EditText noteInput = new EditText(this);
        noteInput.setHint("备注，可不填");
        noteInput.setSingleLine(true);
        CheckBox lunarInput = new CheckBox(this);
        lunarInput.setText("农历日期");
        CheckBox repeatInput = new CheckBox(this);
        repeatInput.setText("每年重复"); repeatInput.setChecked(true);
        CheckBox bannerInput = new CheckBox(this);
        bannerInput.setText("提前三天横幅提醒"); bannerInput.setChecked(true);
        if (editing) {
            titleInput.setText(original.optString("title", ""));
            dateInput.setText(original.optString("date", original.optString("solar_date", "")));
            groupInput.setText(original.optString("group", "our_days"));
            noteInput.setText(original.optString("note", ""));
            lunarInput.setChecked("lunar".equals(original.optString("date_type", "solar")));
            repeatInput.setChecked("yearly".equals(original.optString("repeat_type", "yearly")));
            bannerInput.setChecked(original.optBoolean("banner_enabled", true));
        }
        box.addView(titleInput); box.addView(dateInput); box.addView(groupInput); box.addView(noteInput); box.addView(lunarInput); box.addView(repeatInput); box.addView(bannerInput);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "编辑这个日子" : "添加一个日子")
                .setMessage((editing ? "修改" : "保存") + "后会立刻写入本机守护日历，并同步更新日历页和陪伴页的下个纪念日。")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton(editing ? "更新" : "保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            JSONObject saved = saveCalendarEventValues(
                    editing ? eventId : "",
                    titleInput.getText().toString(),
                    dateInput.getText().toString(),
                    groupInput.getText().toString(),
                    noteInput.getText().toString(),
                    lunarInput.isChecked(),
                    repeatInput.isChecked(),
                    bannerInput.isChecked());
            if (saved.optBoolean("ok", false)) dialog.dismiss();
        }));
        dialog.show();
    }

    private void confirmDeleteCalendarEvent(JSONObject event) {
        if (event == null || event.optBoolean("builtin", false)) return;
        String title = event.optString("title", "这个日子");
        String id = event.optString("id", "");
        new AlertDialog.Builder(this)
                .setTitle("要删除这个日子吗？")
                .setMessage("“" + title + "”会从守护日历里移除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean ok = CalendarState.deleteEvent(this, id);
                    if (ok) {
                        CompanionWindowState.recordJourney(this, "删除守护日历", "移除「" + title + "」");
                        Toast.makeText(this, "已从守护日历移除", Toast.LENGTH_SHORT).show();
                        updateGuardianCalendarView();
                        updateUI();
                    } else Toast.makeText(this, "没有找到这个日子", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

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


    private void chooseGuidianAvatar() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, REQ_GUIDIAN_AVATAR);
        } catch (Exception e) { Toast.makeText(this, "系统相册没有接住选择头像", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseDiaryExport() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE, "掌心窗-TA的日记-" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
            startActivityForResult(i, REQ_DIARY_EXPORT);
        } catch (Exception e) { Toast.makeText(this, "系统文件管理器没有接住导出", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseDiaryImport() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_DIARY_IMPORT);
        } catch (Exception e) { Toast.makeText(this, "系统文件管理器没有接住导入", Toast.LENGTH_SHORT).show(); }
    }

    private void exportDiaryTo(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IllegalStateException("output_unavailable");
            output.write(DiaryState.exportBundle(this).toString(2).getBytes(StandardCharsets.UTF_8)); output.flush();
            Toast.makeText(this, "日记备份已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "导出失败：" + ScreenshotService.shortMsg(e), Toast.LENGTH_LONG).show(); }
    }

    private void importDiaryFrom(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("input_unavailable");
            byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) >= 0) { bytes.write(buffer, 0, n); if (bytes.size() > 16 * 1024 * 1024) throw new IllegalStateException("backup_too_large"); }
            String raw = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            new AlertDialog.Builder(this).setTitle("导入日记备份？").setMessage("会把备份中的日记本和纸页合并到本机；相同 id 的内容不会重复导入。").setNegativeButton("取消", null).setPositiveButton("导入", (d, w) -> { JSONObject result = DiaryState.importBundle(this, raw); Toast.makeText(this, result.optBoolean("ok") ? "日记备份已导入" : ("导入失败：" + result.optString("error")), Toast.LENGTH_LONG).show(); }).show();
        } catch (Exception e) { Toast.makeText(this, "导入失败：" + ScreenshotService.shortMsg(e), Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_GUIDIAN_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            AppPrefs.get(this).edit().putString(GuidianState.KEY_AVATAR_URI, uri.toString()).apply();
            Toast.makeText(this, AppPrefs.companionName(this) + "的头像已换好", Toast.LENGTH_SHORT).show();
            updateUI();
        }
        if (requestCode == REQ_DIARY_COVER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            DiaryState.updateCover(this, diaryBookId, "local_image", uri.toString());
            Toast.makeText(this, "日记本封面已换好", Toast.LENGTH_SHORT).show(); showDiaryHomePage();
        } else if (requestCode == REQ_DIARY_EXPORT && resultCode == RESULT_OK && data != null && data.getData() != null) exportDiaryTo(data.getData());
        else if (requestCode == REQ_DIARY_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) importDiaryFrom(data.getData());
    }

    private void startCompanionService() {
        saveSettings();
        String url = serverUrl == null ? "" : serverUrl.getText().toString().trim(); String token = tokenInput == null ? "" : tokenInput.getText().toString().trim();
        if (url.isEmpty() || token.isEmpty()) { Toast.makeText(this, "请填写服务器地址和 Token", Toast.LENGTH_SHORT).show(); return; }
        if (ScreenshotService.getInstance() == null) { DebugState.append(this, "启动失败：无障碍服务未连接"); Toast.makeText(this, "请先开启掌心窗无障碍服务", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putBoolean("user_stopped", false).apply(); requestIgnoreBatteryOptimization();
        Intent intent = new Intent(this, CompanionService.class); intent.putExtra("server_url", url); intent.putExtra("token", token);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
        DebugState.append(this, "已请求启动前台服务：公开版 v0.3.7 右侧 love 线稿花枝已启用"); serviceRunning = true; updateUI();
    }

    private void stopCompanionService() { getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putBoolean("user_stopped", true).apply(); stopService(new Intent(this, CompanionService.class)); DebugState.append(this, "已停止服务"); serviceRunning = false; updateUI(); }

    private void testScreenshot() {
        saveSettings(); String url = serverUrl == null ? "" : serverUrl.getText().toString().trim(); String token = tokenInput == null ? "" : tokenInput.getText().toString().trim(); ScreenshotService ss = ScreenshotService.getInstance();
        if (url.isEmpty() || token.isEmpty()) { Toast.makeText(this, "先填服务器地址和 Token", Toast.LENGTH_SHORT).show(); return; }
        if (ss == null) { DebugState.append(this, "测试失败：无障碍服务未连接"); Toast.makeText(this, "先开启无障碍服务", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        DebugState.append(this, "给" + AppPrefs.companionName(this) + "看一眼：开始截图上传"); ss.doScreenshot(url, token); Toast.makeText(this, "正在上传截图", Toast.LENGTH_SHORT).show(); updateUI();
    }
    private void testAlarm() { Calendar c = Calendar.getInstance(); c.add(Calendar.MINUTE, 1); try { Intent i = new Intent(AlarmClock.ACTION_SET_ALARM); i.putExtra(AlarmClock.EXTRA_HOUR, c.get(Calendar.HOUR_OF_DAY)); i.putExtra(AlarmClock.EXTRA_MINUTES, c.get(Calendar.MINUTE)); i.putExtra(AlarmClock.EXTRA_MESSAGE, "掌心窗测试闹钟：" + AppPrefs.userName(this)); i.putExtra(AlarmClock.EXTRA_VIBRATE, true); i.putExtra(AlarmClock.EXTRA_SKIP_UI, true); startActivity(i); DebugState.append(this, "已请求设置一分钟后的测试闹钟"); } catch (Exception e) { DebugState.append(this, "测试闹钟失败：" + e.getClass().getSimpleName()); Toast.makeText(this, "闹钟 App 没接住请求", Toast.LENGTH_SHORT).show(); } }
    private void testNotification() { saveSettings(); boolean ok = CompanionService.showReminderNotification(this, "掌心窗悬浮横幅测试", AppPrefs.userName(this) + "看到了顶部横幅，就说明通知通道正常。"); DebugState.append(this, ok ? "已发送悬浮横幅测试提醒" : "悬浮横幅/通知失败：请允许掌心窗发送通知"); Toast.makeText(this, ok ? "已发送横幅测试" : "请先允许通知权限", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void addPackageAlias() { String alias = appAliasInput == null ? "" : appAliasInput.getText().toString().trim(); String pkg = appPackageInput == null ? "" : appPackageInput.getText().toString().trim(); if (alias.isEmpty()) { Toast.makeText(this, "先填应用名/昵称", Toast.LENGTH_SHORT).show(); return; } if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "包名格式不对，例如 com.xingin.xhs", Toast.LENGTH_LONG).show(); return; } AppPrefs.saveCustomApp(this, alias, pkg); DebugState.append(this, "已保存可打开应用：" + alias + " → " + pkg); Toast.makeText(this, "已添加包名", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void addGateApp() { String alias = gateAliasInput == null ? "" : gateAliasInput.getText().toString().trim(); String pkg = gatePackageInput == null ? "" : gatePackageInput.getText().toString().trim(); if (alias.isEmpty()) { Toast.makeText(this, "先填应用名/昵称", Toast.LENGTH_SHORT).show(); return; } if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "包名格式不对，例如 com.xingin.xhs", Toast.LENGTH_LONG).show(); return; } AppGate.addGateApp(this, alias, pkg); DebugState.append(this, "已保存门禁应用：" + alias + " → " + pkg); Toast.makeText(this, "已添加到应用门禁", Toast.LENGTH_SHORT).show(); updateUI(); }
    private void testCustomPackage() { String pkg = appPackageInput == null ? "" : appPackageInput.getText().toString().trim(); if (!AppPrefs.isPackageLike(pkg)) { Toast.makeText(this, "先填正确包名", Toast.LENGTH_SHORT).show(); return; } openPackage(pkg); }
    private void testLocalSequence() { boolean ok1 = CompanionService.showReminderNotification(this, "掌心窗连招测试", "先发悬浮横幅，再回目标 APP。日志会写清每一步。"); String result = CompanionService.openPackageResult(this, AppPrefs.homeTargetPackage(this)); DebugState.append(this, "本机连招测试：popup=" + ok1 + "；open=" + result); updateUI(); }
    private boolean openPackage(String pkg) { String result = CompanionService.openPackageResult(this, pkg); boolean ok = result.startsWith("opened_"); DebugState.append(this, "本机打开 App：" + result); Toast.makeText(this, ok ? "已尝试打开" : ("打开失败：" + result), Toast.LENGTH_SHORT).show(); updateUI(); return ok; }
    private void updateVersionUi() {
        boolean hasNew = latestVersionCode > AppPrefs.APP_VERSION_CODE;
        if (versionStatusText != null) {
            versionStatusText.setText("当前版本：" + AppPrefs.APP_VERSION_NAME + "（" + AppPrefs.APP_VERSION_CODE + "）\n" +
                    (hasNew ? "发现新版本：" + latestVersionName + "（" + latestVersionCode + "）" : "已是最新版本"));
        }
        if (updateChangelogText != null) updateChangelogText.setText(latestChangelog.isEmpty() ? "点击检查更新，可查看更新日志并前往下载最新版。" : latestChangelog);
        if (licenseSummaryText != null) licenseSummaryText.setText("本公开版保留原项目许可：可阅读、学习、个人自用部署和本地修改；二次分发、商业用途及移除作者说明须取得许可。详见源码包 LICENSE。");
        if (downloadUpdateButton != null) downloadUpdateButton.setEnabled(hasNew && !latestApkUrl.isEmpty());
    }

    private void checkForUpdates(boolean manual) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(DEFAULT_UPDATE_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "Zhangxinchuang-Public/" + AppPrefs.APP_VERSION_NAME);
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IllegalStateException("HTTP " + connection.getResponseCode());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                }
                JSONObject info = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                latestVersionCode = info.optInt("latest_version_code", AppPrefs.APP_VERSION_CODE);
                latestVersionName = info.optString("latest_version_name", AppPrefs.APP_VERSION_NAME);
                latestApkUrl = info.optString("apk_url", "").trim();
                JSONArray changes = info.optJSONArray("changelog");
                StringBuilder text = new StringBuilder();
                if (changes != null) for (int i = 0; i < changes.length(); i++) text.append("• ").append(changes.optString(i)).append("\n");
                latestChangelog = text.toString().trim();
                runOnUiThread(() -> { updateVersionUi(); if (manual) Toast.makeText(this, latestVersionCode > AppPrefs.APP_VERSION_CODE ? "发现新版本 " + latestVersionName : "当前已是最新版本", Toast.LENGTH_SHORT).show(); });
            } catch (Exception error) {
                runOnUiThread(() -> { updateVersionUi(); if (manual) Toast.makeText(this, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show(); });
            } finally { if (connection != null) connection.disconnect(); }
        }, "public-update-check").start();
    }

    private void downloadLatestApk() {
        if (latestApkUrl.isEmpty()) { Toast.makeText(this, "请先检查更新", Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(latestApkUrl))); }
        catch (Exception e) { Toast.makeText(this, "无法打开下载地址", Toast.LENGTH_SHORT).show(); }
    }

    private void toast(boolean ok) { Toast.makeText(this, ok ? "执行成功" : "执行失败，请检查权限/包名", Toast.LENGTH_SHORT).show(); updateUI(); }
    private int parseInterval(String raw) {
        try {
            int v = Integer.parseInt(raw);
            if (v < AppPrefs.MIN_POLL_INTERVAL_MS) return AppPrefs.DEFAULT_POLL_INTERVAL_MS;
            if (v > AppPrefs.MAX_POLL_INTERVAL_MS) return AppPrefs.MAX_POLL_INTERVAL_MS;
            return v;
        } catch (Exception e) { return AppPrefs.DEFAULT_POLL_INTERVAL_MS; }
    }
    private int parseInt(String raw, int def, int min, int max) { try { int v = Integer.parseInt(raw); if (v < min) return min; if (v > max) return max; return v; } catch (Exception e) { return def; } }
    private void openAccessibilitySettings() {
        try {
            getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().putLong(PREF_A11Y_SETTINGS_OPENED_AT, System.currentTimeMillis()).apply();
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
            Toast.makeText(this, "开启“掌心窗服务”后返回；若返回仍未开启，请先允许受限设置", Toast.LENGTH_LONG).show();
            scheduleAccessibilityFollowupChecks();
        } catch (Exception e) {
            Toast.makeText(this, "设置 → 应用 → 掌心窗 → 允许受限设置；再到无障碍开启掌心窗服务", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppDetailsSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            Toast.makeText(this, "如有右上角菜单，请先点“允许受限设置”，再回无障碍开启掌心窗服务", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "设置 → 应用 → 掌心窗 → 右上角 → 允许受限设置", Toast.LENGTH_LONG).show();
        }
    }

    private void showAccessibilityHelpDialog() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("无障碍还没真正连上")
                    .setMessage("如果你在系统无障碍里打开后，回到掌心窗又变成未开启，通常是系统还没完成绑定，或 Android/部分国产系统拦截了侧载 APK 的无障碍权限。\n\n请先等 5-10 秒；如果仍未开启，到“应用信息 → 掌心窗 → 右上角菜单”允许受限设置，然后再回无障碍开启“掌心窗服务”。")
                    .setPositiveButton("去无障碍设置", (d, w) -> openAccessibilitySettings())
                    .setNegativeButton("去应用信息", (d, w) -> openAppDetailsSettings())
                    .setNeutralButton("我知道了", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "先允许受限设置，再开启掌心窗服务", Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleAccessibilityFollowupChecks() {
        long[] delays = new long[] { 600L, 1500L, 3000L, 6000L, 12000L };
        for (long delay : delays) {
            uiHandler.postDelayed(() -> { serviceRunning = CompanionService.isRunning(); updateUI(); }, delay);
        }
    }

    private boolean recentlyOpenedAccessibilitySettings() {
        try {
            long t = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).getLong(PREF_A11Y_SETTINGS_OPENED_AT, 0L);
            return t > 0 && System.currentTimeMillis() - t < A11Y_CONFIRM_WINDOW_MS;
        } catch (Exception ignored) { return false; }
    }

    private String accessibilityComponentLong() {
        return new ComponentName(this, ScreenshotService.class).flattenToString();
    }

    private String accessibilityComponentShort() {
        return getPackageName() + "/." + ScreenshotService.class.getSimpleName();
    }

    private boolean classMatchesAccessibilityService(String cls) {
        if (cls == null) return false;
        String c = cls.trim();
        return ScreenshotService.class.getName().equals(c)
                || c.endsWith("." + ScreenshotService.class.getSimpleName())
                || ScreenshotService.class.getSimpleName().equals(c);
    }

    private boolean matchesAccessibilityComponent(String raw) {
        if (raw == null) return false;
        String item = raw.trim();
        if (item.length() == 0) return false;
        String expectedLong = accessibilityComponentLong();
        String expectedShort = accessibilityComponentShort();
        if (expectedLong.equalsIgnoreCase(item) || expectedShort.equalsIgnoreCase(item)) return true;
        try {
            ComponentName cn = ComponentName.unflattenFromString(item);
            if (cn != null && getPackageName().equals(cn.getPackageName()) && classMatchesAccessibilityService(cn.getClassName())) return true;
        } catch (Exception ignored) { }
        String lower = item.toLowerCase(Locale.ROOT).replace(" ", "");
        String pkg = getPackageName().toLowerCase(Locale.ROOT);
        String full = ScreenshotService.class.getName().toLowerCase(Locale.ROOT);
        String simple = ScreenshotService.class.getSimpleName().toLowerCase(Locale.ROOT);
        return lower.contains(pkg + "/") && (lower.contains(full) || lower.contains("/.") && lower.contains(simple) || lower.endsWith("/" + simple));
    }

    private String rawEnabledAccessibilityServices() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled == null ? "" : enabled;
        } catch (Exception ignored) { return ""; }
    }

    private boolean isAccessibilityServiceEnabledInSettings() {
        try {
            String enabled = rawEnabledAccessibilityServices();
            if (enabled.length() == 0) return false;
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabled);
            while (splitter.hasNext()) {
                if (matchesAccessibilityComponent(splitter.next())) return true;
            }
            // Some OEM ROMs store accessibility components in a slightly non-standard shape.
            // If the secure setting clearly contains this package and service class, treat it as enabled.
            if (matchesAccessibilityComponent(enabled)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isAccessibilityServiceEnabledByManager() {
        try {
            AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (manager == null) return false;
            List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (services == null) return false;
            for (AccessibilityServiceInfo info : services) {
                if (info == null || info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
                String pkg = info.getResolveInfo().serviceInfo.packageName;
                String cls = info.getResolveInfo().serviceInfo.name;
                if (getPackageName().equals(pkg) && classMatchesAccessibilityService(cls)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isAccessibilityServiceEnabled() {
        boolean enabled = isAccessibilityServiceEnabledInSettings() || isAccessibilityServiceEnabledByManager();
        if (enabled && getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).getLong(PREF_A11Y_SETTINGS_OPENED_AT, 0L) > 0) {
            getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit().remove(PREF_A11Y_SETTINGS_OPENED_AT).apply();
        }
        return enabled;
    }

    private void openUsageAccessSettings() { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "设置 → 应用 → 特殊权限 → 使用情况访问", Toast.LENGTH_LONG).show(); } }
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !NowState.hasLocationPermission(this)) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 24);
        else Toast.makeText(this, "定位权限已开启", Toast.LENGTH_SHORT).show();
        updateUI();
    }
    private void openOverlayPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            else Toast.makeText(this, "当前系统无需单独开启悬浮窗权限", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "设置 → 应用 → 特殊权限 → 悬浮窗", Toast.LENGTH_LONG).show(); }
    }
    private void requestIgnoreBatteryOptimization() { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; try { PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE); if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) { Intent bi = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS); bi.setData(Uri.parse("package:" + getPackageName())); startActivity(bi); } } catch (Exception ignored) { } }

    private void updateUI() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        boolean accessibilityConnected = accessibilityEnabled && ScreenshotService.getInstance() != null;
        boolean accessibilityConfirming = !accessibilityEnabled && recentlyOpenedAccessibilitySettings();
        boolean accessibilityOk = accessibilityEnabled;
        boolean usageOk = LifeState.hasUsagePermission(this);
        UITheme visualTheme = UITheme.current(this);
        updateHeader(currentTab);
        if (serviceRunning) { if (statusText != null) { statusText.setText(accessibilityOk ? "●  窗已打开 · 陪伴和守护都在" : (accessibilityConfirming ? "●  生活小窗已打开 · 正在确认无障碍" : "●  生活小窗已打开 · 无障碍待开启")); statusText.setTextColor(accessibilityOk ? visualTheme.primary : 0xFFCF8A62); } if (toggleButton != null) { toggleButton.setText("停止服务"); toggleButton.setBackgroundResource(R.drawable.pill_danger); } }
        else { if (statusText != null) { statusText.setText(accessibilityOk ? "○  感官已准备 · 服务等待开启" : (accessibilityConfirming ? "○  正在确认无障碍状态" : "○  天气可用 · 无障碍待开启")); statusText.setTextColor(accessibilityConfirming ? 0xFFCF8A62 : visualTheme.subtext); } if (toggleButton != null) { toggleButton.setText("启动服务"); toggleButton.setBackgroundResource(R.drawable.pill_primary); } }
        if (accessibilityButton != null) accessibilityButton.setText(accessibilityOk ? (accessibilityConnected ? "无障碍权限：已开启" : "无障碍权限：系统已开启，等待连接") : (accessibilityConfirming ? "无障碍权限：正在确认，点此查看提示" : "打开无障碍设置"));
        if (usageAccessButton != null) usageAccessButton.setText(usageOk ? "使用情况权限：已开启" : "打开使用情况访问权限");
        if (locationPermissionButton != null) locationPermissionButton.setText(NowState.hasLocationPermission(this) ? "定位权限：已开启" : "打开定位权限");
        if (overlayPermissionButton != null) overlayPermissionButton.setText((Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) ? "悬浮窗权限：已开启" : "打开悬浮窗权限");
        if (nowStatePermissionText != null) nowStatePermissionText.setText("此刻状态：" + (NowState.hasLocationPermission(this) ? "定位已授权" : "定位未授权") + " · " + ((Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) ? "悬浮窗已授权" : "悬浮窗未授权") + "\n用于状态卡片与应用门禁悬浮页。权限均由用户在本机开启。");
        try {
            JSONObject s = LifeState.collect(this);
            int battery = s.optInt("battery_percent", -1); boolean charging = s.optBoolean("charging", false);
            if (overviewBatteryText != null) overviewBatteryText.setText(battery >= 0 ? battery + "%" : "-");
            if (overviewBatteryDetail != null) overviewBatteryDetail.setText(charging ? "正在充电" : "未充电");
            if (overviewAppText != null) overviewAppText.setText(s.optString("current_app", "-").isEmpty() ? "暂未识别" : s.optString("current_app", "-"));
            if (overviewAppDetail != null) overviewAppDetail.setText(s.optBoolean("screen_on") ? "屏幕亮着" : "屏幕已熄灭");
            int mins = s.optInt("screen_time_today_minutes", 0);
            if (overviewScreenText != null) overviewScreenText.setText(formatMinutes(mins));
            if (overviewScreenDetail != null) overviewScreenDetail.setText("解锁 " + s.optInt("unlock_count_today", 0) + " 次");
            JSONObject w = s.optJSONObject("current_weather_location");
            updateWeatherOverview(w);
            updateHeroOverview(s);
            updateJourney(s);
            updateGuardOverview(s);
            if (nowStateText != null) nowStateText.setText(NowState.pretty(this));
        } catch (Exception ignored) { }
        if (lifeSummaryText != null) lifeSummaryText.setText(lifeSummary());
        if (lifeStatusText != null) lifeStatusText.setText(LifeState.pretty(this));
        if (calendarSummaryText != null) calendarSummaryText.setText("把重要日子轻轻放进窗边。");
        if (calendarDetailText != null) calendarDetailText.setText("标题、日期、分组和备注填好后保存。农历生日、七夕和中秋记得勾选农历日期。");
        updateGuardianCalendarView();
        if (drawerCalendarButton != null && (drawerCalendar == null || drawerCalendar.getVisibility() != View.VISIBLE)) drawerCalendarButton.setText(CalendarState.summaryLine(this) + "  ›");
        if (drawerWeatherButton != null && (drawerWeather == null || drawerWeather.getVisibility() != View.VISIBLE)) drawerWeatherButton.setText(WeatherState.summaryLine(this) + "  ›");
        if (weatherLocationsText != null) weatherLocationsText.setText(WeatherState.locationsText(this));
        if (knownAppsText != null) knownAppsText.setText(AppPrefs.knownAppsText(this));
        if (homeModeStatusText != null) homeModeStatusText.setText(HomeMode.pretty(this));
        if (drawerAppGateButton != null && (drawerAppGate == null || drawerAppGate.getVisibility() != View.VISIBLE)) drawerAppGateButton.setText(AppGate.summaryLine(this) + "  ›");
        if (gateStatusText != null) gateStatusText.setText(AppGate.prettyClean(this));
        if (debugText != null) debugText.setText(DebugState.get(this));
        if (themeText != null) themeText.setText("当前主题：" + AppPrefs.get(this).getString(AppPrefs.KEY_THEME, "白桃粉") + "\n点击后即时切换背景、卡片、按钮和底部导航。守护日历主色：#B8A8D8。");
        if (guidianSummaryText != null) guidianSummaryText.setText(GuidianState.summaryText(this));
        if (guidianDetailText != null) guidianDetailText.setText(GuidianState.detailText(this));
        if (guidianSettingsStatusText != null) guidianSettingsStatusText.setText(GuidianState.detailText(this));
        if (guidianAvatarText != null) {
            String avatar = AppPrefs.get(this).getString(GuidianState.KEY_AVATAR_URI, "");
            guidianAvatarText.setText(avatar == null || avatar.length() == 0 ? "当前使用默认头像。" : "已使用你选择的头像。\n" + avatar);
        }
        renderCompanionAvatar();
        updateCompanionDays();
        updateCompanionAnniversary(nearestCalendarEvent(null));
        renderCompanionState(CompanionWindowState.cached(this));
        long now = System.currentTimeMillis();
        if (now - lastCompanionSyncAt > 30_000L && AppPrefs.server(this) != null && !AppPrefs.server(this).trim().isEmpty()) {
            lastCompanionSyncAt = now;
            CompanionWindowState.sync(this, 20, (state, error) -> runOnUiThread(() -> renderCompanionState(state)));
        }
        if (drawerGuidianButton != null && (drawerGuidian == null || drawerGuidian.getVisibility() != View.VISIBLE)) drawerGuidianButton.setText("归电  ›");
        if (drawerGuidianSettingsButton != null && (drawerGuidianSettings == null || drawerGuidianSettings.getVisibility() != View.VISIBLE)) drawerGuidianSettingsButton.setText("归电设置  ›");
        updateVersionUi();
        applyVisualTheme();
        updateGuardianCalendarView();
    }

    private void renderCompanionAvatar() {
        if (companionAvatarView == null) return;
        String raw = AppPrefs.get(this).getString(GuidianState.KEY_AVATAR_URI, "");
        if (raw == null || raw.isEmpty()) {
            companionAvatarView.clearImage();
            Drawable fallback = getDrawable(R.drawable.ic_heart_wave).mutate();
            fallback.setTint(UITheme.current(this).primary);
            companionAvatarView.setFallback(fallback);
            return;
        }
        try {
            companionAvatarView.setImageUri(Uri.parse(raw));
        } catch (Exception ignored) { companionAvatarView.clearImage(); }
    }

    private void renderCompanionState(JSONObject state) {
        if (state == null) state = CompanionWindowState.cached(this);
        JSONObject whisper = state.optJSONObject("whisper");
        if (whisper != null) {
            if (sharedWhisperText != null) sharedWhisperText.setText("“" + whisper.optString("content", "把今天，轻轻收进窗里。") + "”");
            if (sharedWhisperMetaText != null) {
                String at = CompanionWindowState.elapsed(whisper.optString("updated_at", ""));
                sharedWhisperMetaText.setText(whisper.optString("author", AppPrefs.companionName(this)) + "修改" + (at.isEmpty() ? "" : "于 " + at));
            }
        }
        JSONArray actions = CompanionWindowState.actions(this);
        boolean visible = AppPrefs.get(this).getBoolean(AppPrefs.KEY_SHOW_COMPANION_ACTIONS, true);
        if (companionActionsPreview != null) {
            if (!visible) companionActionsPreview.setText("行动记录已在设置中隐藏。");
            else if (actions.length() == 0) companionActionsPreview.setText(AppPrefs.companionName(this) + "现在安安静静的，暂时没有新的行动。");
            else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(5, actions.length()); i++) {
                    JSONObject a = actions.optJSONObject(i);
                    if (a == null) continue;
                    sb.append("●  ").append(a.optString("title", "完成了一次行动")).append("\n    ").append(CompanionWindowState.elapsed(a.optString("created_at", a.optString("at", ""))));
                    if (i + 1 < Math.min(5, actions.length())) sb.append("\n\n");
                }
                companionActionsPreview.setText(sb.toString());
            }
        }
        if (companionRestArt != null) companionRestArt.setVisibility(visible && actions.length() == 0 ? View.VISIBLE : View.GONE);
        if (companionPresenceText != null) {
            if (!visible || actions.length() == 0) companionPresenceText.setText("今天还没有新的行动");
            else {
                JSONObject latest = actions.optJSONObject(0);
                companionPresenceText.setText((latest == null ? "刚刚来过" : CompanionWindowState.elapsed(latest.optString("created_at", latest.optString("at", ""))) + " · " + latest.optString("title", "来过窗边")));
            }
        }
    }

    private void updateJourney(JSONObject state) {
        if (todayJourneyText == null) return;
        JSONArray journey = CompanionWindowState.journey(this);
        StringBuilder sb = new StringBuilder();
        int count = Math.min(5, journey.length());
        for (int i = 0; i < count; i++) {
            JSONObject item = journey.optJSONObject(i);
            if (item == null) continue;
            sb.append(item.optString("local_time", item.optString("time", "--:--"))).append("   ●  ").append(item.optString("title", "今日记录"));
            String detail = item.optString("subtitle", item.optString("detail", ""));
            if (!detail.isEmpty()) sb.append("\n           ").append(detail);
            if (i + 1 < count) sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append("今天还没有留下轨迹。");
        todayJourneyText.setText(sb.toString());
    }

    private void updateCompanionDays() {
        if (companionDaysText == null) return;
        long now = System.currentTimeMillis();
        long first = AppPrefs.get(this).getLong(AppPrefs.KEY_COMPANION_FIRST_DAY, 0L);
        if (first <= 0L) {
            first = now;
            AppPrefs.get(this).edit().putLong(AppPrefs.KEY_COMPANION_FIRST_DAY, first).apply();
        }
        long days = Math.max(1L, (now - first) / 86_400_000L + 1L);
        companionDaysText.setText("第 " + days + " 天");
        if (companionSinceText != null) companionSinceText.setText(new SimpleDateFormat("yyyy年M月d日开始", Locale.CHINA).format(new Date(first)));
    }

    private void updateGuardOverview(JSONObject state) {
        if (guardOverviewText == null) return;
        String online = serviceRunning ? "设备在线" : "服务等待开启";
        String network = state.optString("network_type", "unknown");
        int battery = state.optInt("battery_percent", -1);
        guardOverviewText.setText(online + "，今日没有异常提醒\n" + (battery >= 0 ? "电量 " + battery + "%" : "电量未读取") + "  ·  " + network);
        if (guardDeviceStatusText != null) guardDeviceStatusText.setText((serviceRunning ? "在线" : "等待连接") + " · " + network + (battery >= 0 ? " · 电量 " + battery + "%" : ""));
        if (guardRecordText != null) guardRecordText.setText(
                (serviceRunning ? "✓   设备保持在线" : "○   服务等待开启") + "\n      自动检查持续进行\n\n" +
                "⌂   常用地点状态已同步\n      天气与生活状态保持更新\n\n" +
                "ϟ   " + (battery >= 0 ? "当前电量 " + battery + "%" : "电量提醒规则正在守护"));
        if (guardianRing != null) { guardianRing.setProgress(battery >= 0 ? Math.max(.18f, battery / 100f) : (serviceRunning ? .82f : .28f)); guardianRing.invalidate(); }
        updateCompanionAnniversary(nearestCalendarEvent(state));
    }

    private void updateWeatherOverview(JSONObject w) {
        if (overviewWeatherText == null) return;
        String name = w == null ? "当前地区" : w.optString("name", "当前地区");
        String city = w == null ? "" : w.optString("city", "");
        JSONObject live = WeatherLive.cached(this, city);
        if (live != null && live.optBoolean("ok")) {
            overviewWeatherText.setText(live.optInt("temperature", 0) + "℃");
            if (overviewWeatherDetail != null) overviewWeatherDetail.setText(name + " · " + live.optString("condition", "天气"));
        } else {
            overviewWeatherText.setText(name);
            if (overviewWeatherDetail != null) overviewWeatherDetail.setText(city.isEmpty() ? "未设城市" : city);
        }
        long now = System.currentTimeMillis();
        if (!city.isEmpty() && !weatherFetching && !WeatherLive.isFresh(this, city, 45L * 60L * 1000L) && now - lastWeatherFetchAt > 45_000L) {
            weatherFetching = true;
            lastWeatherFetchAt = now;
            WeatherLive.refreshAsync(this, city, weather -> runOnUiThread(() -> {
                weatherFetching = false;
                if (weather != null && weather.optBoolean("ok")) {
                    overviewWeatherText.setText(weather.optInt("temperature", 0) + "℃");
                    if (overviewWeatherDetail != null) overviewWeatherDetail.setText(name + " · " + weather.optString("condition", "天气"));
                    try { updateHeroOverview(LifeState.collect(this)); } catch (Exception ignored) { }
                }
            }));
        }
    }

    private void updateHeroOverview(JSONObject s) {
        if (s == null) return;
        int battery = s.optInt("battery_percent", -1);
        boolean charging = s.optBoolean("charging", false);
        int screenMinutes = s.optInt("screen_time_today_minutes", 0);
        JSONObject calendar = s.optJSONObject("calendar_state");
        JSONObject nearest = firstCalendarItem(calendar, "active_banners");
        if (nearest == null) nearest = firstCalendarItem(calendar, "nearest");

        String primary = CompanionWindowState.whisper(this).optString("content", "把今天，轻轻收进窗里。");

        String secondary;
        if (battery >= 0 && battery <= 40 && !charging) secondary = "找个顺手的时刻，让手机慢慢充上电。";
        else if (screenMinutes >= 480) secondary = "让眼睛离开屏幕半分钟，看看远一点。";
        else {
            String app = s.optString("current_app", "").trim();
            secondary = app.isEmpty() ? "窗外安安静静，状态都在轻轻更新。" : "此刻在 " + app + "，掌心窗替你看着今天。";
        }

        if (overviewAdviceText != null) overviewAdviceText.setText(formatHeroMessage(primary));
        if (overviewSecondaryText != null) overviewSecondaryText.setText(secondary);
        if (overviewMetaText != null) overviewMetaText.setText(weatherBrief(s) + "   ·   " + calendarBrief(nearest));
        if (todayNextTitle != null) todayNextTitle.setText("下一件事");
        if (todayNextDetail != null) todayNextDetail.setText(nearest == null ? "晚间无安排" : nearest.optString("title", "临近日子"));
    }

    private JSONObject firstCalendarItem(JSONObject calendar, String key) {
        if (calendar == null) return null;
        org.json.JSONArray items = calendar.optJSONArray(key);
        return items == null || items.length() == 0 ? null : items.optJSONObject(0);
    }

    private JSONObject nearestCalendarEvent(JSONObject state) {
        JSONObject calendar = state == null ? null : state.optJSONObject("calendar_state");
        JSONObject nearest = firstCalendarItem(calendar, "nearest");
        if (nearest == null) nearest = firstCalendarItem(calendar, "active_banners");
        if (nearest != null) return nearest;
        try {
            JSONArray upcoming = CalendarState.upcomingOccurrences(this, 1);
            if (upcoming != null && upcoming.length() > 0) return upcoming.optJSONObject(0);
        } catch (Exception ignored) { }
        return null;
    }

    private void updateCompanionAnniversary(JSONObject event) {
        if (companionAnniversaryText == null) return;
        if (event == null) event = nearestCalendarEvent(null);
        companionAnniversaryText.setText(formatCompanionAnniversary(event));
    }

    private String formatCompanionAnniversary(JSONObject event) {
        if (event == null) return "暂无临近日子";
        String title = event.optString("title", "临近日子").trim();
        String days = event.optString("days_text", "").trim();
        if (title.isEmpty()) title = "临近日子";
        if (days.isEmpty()) return title;
        return title + "\n" + days;
    }


    private String formatHeroMessage(String text) {
        if (text == null || text.trim().isEmpty()) return "把今天，\n轻轻收进窗里。";
        String clean = text.trim();
        if (clean.contains("\n") || clean.length() <= 11) return clean;
        int comma = clean.indexOf('，');
        if (comma >= 2 && comma < 10) return clean.substring(0, comma + 1) + "\n" + clean.substring(comma + 1);
        return clean;
    }

    private String calendarBrief(JSONObject event) {
        if (event == null) return "日历 · 暂无临近日子";
        return "日历 · " + event.optString("title", "重要日子") + " " + event.optString("days_text", "");
    }

    private String weatherBrief(JSONObject s) {
        JSONObject location = s.optJSONObject("current_weather_location");
        if (location == null) return "天气 · 未设地区";
        String name = location.optString("name", "当前地区");
        String city = location.optString("city", "");
        JSONObject live = WeatherLive.cached(this, city);
        if (live != null && live.optBoolean("ok")) return name + " · " + live.optInt("temperature", 0) + "℃ " + live.optString("condition", "");
        return name + " · " + (city.isEmpty() ? "待设置" : city);
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
        JSONObject cal = s.optJSONObject("calendar_state");
        if (cal != null) {
            org.json.JSONArray banners = cal.optJSONArray("active_banners");
            if (banners != null && banners.length() > 0) sb.append(banners.optJSONObject(0).optString("banner_text", "")).append("\n");
            else {
                org.json.JSONArray nearest = cal.optJSONArray("nearest");
                if (nearest != null && nearest.length() > 0) {
                    JSONObject n = nearest.optJSONObject(0);
                    if (n != null && n.optInt("days_left", 999) <= 14) sb.append(n.optString("title", "重要日子")).append(" ").append(n.optString("days_text", "")).append("。\n");
                }
            }
        }
        JSONObject w = s.optJSONObject("current_weather_location");
        if (w != null) {
            String city = w.optString("city", "");
            JSONObject live = WeatherLive.cached(this, city);
            if (live != null && live.optBoolean("ok")) sb.append(WeatherLive.advice(live, w.optString("name", "当前地区"))).append("\n");
            else sb.append(WeatherState.localAdvice(w.optString("note", ""))).append("\n");
        }
        if (sb.length() == 0) sb.append("状态还好，").append(AppPrefs.companionName(this)).append("继续陪着你。");
        return sb.toString().trim();
    }
    private String formatMinutes(int minutes) { if (minutes < 60) return minutes + " 分钟"; return (minutes / 60) + "h " + (minutes % 60) + "m"; }
}
