package com.tj90.prioritytodo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String UI_PREFS = "priority_todo_ui";
    private static final String SYNC_CREDENTIAL_PREFS = "priority_todo_sync_credentials";
    private static final String LEGACY_NIGHT_MODE = "night_mode";
    private static final String KEY_THEME = "theme";
    private static final String KEY_HAND = "hand";
    private static final String KEY_ONBOARDED = "onboarded_v2";
    private static final String KEY_SYNC_BASE_URL = "sync_base_url";
    private static final String KEY_SYNC_ACCESS_CLIENT_ID = "access_client_id";
    private static final String KEY_SYNC_ACCESS_CLIENT_SECRET = "access_client_secret";
    private static final String KEY_SYNC_ACCESS_BASE_URL = "access_base_url";
    private static final String DEFAULT_SYNC_BASE_URL = "http://10.0.2.2:8787";

    private static final String THEME_DAY = "day";
    private static final String THEME_NIGHT = "night";
    private static final String THEME_SYSTEM = "system";
    private static final String HAND_RIGHT = "right";
    private static final String HAND_CENTER = "center";
    private static final String HAND_LEFT = "left";
    private static final int REQUEST_EXPORT_CSV = 21;
    private static final int REQUEST_IMPORT_CSV = 22;

    private static final RepeatOption[] REPEAT_OPTIONS = {
            new RepeatOption(TodoTask.REPEAT_NONE, "No repeat"),
            new RepeatOption(TodoTask.REPEAT_HOUR, "Hours"),
            new RepeatOption(TodoTask.REPEAT_DAY, "Days"),
            new RepeatOption(TodoTask.REPEAT_WEEK, "Weeks"),
            new RepeatOption(TodoTask.REPEAT_MONTH, "Months"),
            new RepeatOption(TodoTask.REPEAT_YEAR, "Years")
    };

    private static final int[] CONFETTI_COLORS = {
            0xFF008135, 0xFF3D9C5E, 0xFF59AD73, 0xFF60C781, 0xFF9FE4B1, 0xFFFFFFFF
    };

    private final List<TodoTask> tasks = new ArrayList<>();
    private final List<SyncState.TaskTombstone> taskTombstones = new ArrayList<>();
    private final List<SyncState.CategoryState> categoryStates = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedSync = () -> syncNow(false);
    private final Runnable reminderPriorityRefresh = () -> renderAll(true);

    private TaskStore store;
    private PriorityPalette palette;
    private String theme = THEME_DAY;
    private String hand = HAND_RIGHT;
    private String syncBaseUrl = "";
    private String syncAccessClientId = "";
    private String syncAccessClientSecret = "";
    private String syncAccessBaseUrl = "";
    private boolean syncInFlight;
    private boolean syncQueued;
    private boolean queuedManualSync;
    private boolean destroyed;
    private boolean resumed;

    private FrameLayout root;
    private ScrollView mainScroll;
    private TextView headerSubView;
    // TaskViews screen keys: active (incomplete, not Later) and Later.
    private static final String VIEW_ACTIVE = "Focus";
    private static final String VIEW_LATER = "Later";
    private String searchQuery = "";
    private TextView listFilterLabel;
    private View listHeaderView;
    private boolean laterExpanded;
    private LinearLayout laterHeaderView;
    private LinearLayout laterContainer;
    // Rank of each active task across every list, so a task keeps its spectrum
    // color when the user switches lists.
    private final Map<String, Integer> globalRanks = new HashMap<>();
    private int globalRankTotal;
    private final Runnable restoreListDock = () -> setListDockVisible(true);
    private boolean reminderSettingsOpen;
    private LinearLayout reminderSettingsPanel;
    private TextView reminderSummary;
    private HeaderIconButton handPill;
    private HeaderIconButton themePill;
    private View progressFill;
    private TextView progressText;
    private LinearLayout heroContainer;
    private LinearLayout listContainer;
    private View fab;
    private View fabTarget;
    private View reachArc;
    private String expandedTaskKey;

    // ---- FAB drag-to-reposition ----
    private boolean fabDragging;
    private boolean fabMoved;
    private float fabDownRawX;
    private float fabDownRawY;
    private Runnable fabLongPressRunnable;

    private final Map<String, Integer> rowTops = new HashMap<>();

    private ConfettiView confettiView;

    private View toastView;
    private Runnable toastDismiss;
    private Runnable toastUndoAction;

    private View cheerView;
    private Runnable cheerDismiss;

    // ---- sheet draft state ----
    private android.window.OnBackInvokedCallback sheetBackCallback;
    private boolean sheetOpen;
    private String sheetMode = "add";
    private String sheetEditId;
    private String draftName = "";
    private String draftNotes = "";
    private String draftImpact = TodoTask.LOW;
    private String draftEffort = TodoTask.LOW;
    private boolean draftUrgent;
    private boolean draftQuick = true;
    private long draftReminderAt;
    private String draftRepeatUnit = TodoTask.REPEAT_NONE;
    private int draftRepeatEvery = 1;
    private String draftRepeatEveryText = "1";
    private boolean draftRepeatEveryValid = true;
    private boolean detailsExpanded;
    private boolean notesExpanded;

    private FrameLayout sheetOverlay;
    private View sheetScrim;
    private LinearLayout sheetPanel;
    private ScrollView sheetScroll;
    private EditText sheetInput;
    private EditText notesInput;
    private TextView addDetailsRow;
    private TextView listPill;
    private TextView landsPill;
    private TextView detailsSummary;
    private TextView detailsToggle;
    private ReminderClockButton remindChip;
    private View remindClear;
    private LinearLayout reminderInfoRow;
    private View clearTitleButton;
    private ValueAnimator adjustPulse;
    private LinearLayout reminderRepeatRow;
    private LinearLayout chipsContainer;
    private TextView commitButton;
    private boolean detailsAnimating;
    private ViewTreeObserver.OnGlobalLayoutListener sheetKeyboardLayoutListener;

    // ---- lists / categories ----
    private final List<String> categories = new ArrayList<>();
    private String activeCat = "All";
    private String addCatDraft;      // null = not adding a list; "" or text while adding
    private String draftCategory;    // sheet draft: the task's list, null = All / unassigned
    private LinearLayout catStrip;
    private EditText addCatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The add/edit sheet is a bottom overlay that positions itself above the keyboard
        // manually (see sheetKeyboardFrame). Pin ADJUST_NOTHING so the window itself does
        // not also pan/resize for the IME — otherwise the two offsets stack and the sheet
        // floats up with a gap above the keyboard.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        store = new TaskStore(this);
        loadPrefs();
        palette = activePalette();
        setTheme(isNightEffective() ? R.style.AppTheme_Night : R.style.AppTheme);
        replaceSyncState(store.loadSyncState());
        requestNotificationPermission();
        createNotificationChannel();
        scheduleTaskReminders();
        buildChrome();
        renderAll(false);
        maybeShowOnboarding();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        renderAll(false);
        if (!syncBaseUrl.isEmpty()) {
            syncNow(false);
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(reminderPriorityRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        unregisterSheetBack();
        handler.removeCallbacks(debouncedSync);
        handler.removeCallbacks(reminderPriorityRefresh);
        handler.removeCallbacks(restoreListDock);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_CSV) {
            exportCsvTo(uri);
        } else if (requestCode == REQUEST_IMPORT_CSV) {
            importCsvFrom(uri);
        }
    }

    // ===================== prefs / theme =====================

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        if (prefs.contains(KEY_THEME)) {
            theme = prefs.getString(KEY_THEME, THEME_DAY);
        } else if (prefs.contains(LEGACY_NIGHT_MODE)) {
            theme = prefs.getBoolean(LEGACY_NIGHT_MODE, false) ? THEME_NIGHT : THEME_DAY;
        } else {
            theme = THEME_DAY;
        }
        hand = prefs.getString(KEY_HAND, HAND_RIGHT);
        syncBaseUrl = prefs.getString(KEY_SYNC_BASE_URL, "");
        SharedPreferences syncCredentials = getSharedPreferences(
                SYNC_CREDENTIAL_PREFS, MODE_PRIVATE);
        syncAccessClientId = syncCredentials.getString(KEY_SYNC_ACCESS_CLIENT_ID, "");
        syncAccessClientSecret = syncCredentials.getString(KEY_SYNC_ACCESS_CLIENT_SECRET, "");
        syncAccessBaseUrl = syncCredentials.getString(KEY_SYNC_ACCESS_BASE_URL, "");
    }

    private void persistPrefs() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putString(KEY_THEME, theme)
                .putString(KEY_HAND, hand)
                .putString(KEY_SYNC_BASE_URL, syncBaseUrl)
                .apply();
    }

    private void markOnboarded() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ONBOARDED, true)
                .apply();
    }

    private void maybeShowOnboarding() {
        SharedPreferences prefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ONBOARDED, false)) {
            return;
        }
        new AlertDialog.Builder(themedContext())
                .setTitle("Welcome 👋")
                .setMessage("Tap  +  to add a task — it auto-sorts to the top.\n\n"
                        + "Mark it Urgent to jump to #1.\n"
                        + "Swipe a task to finish it.")
                .setPositiveButton("Got it", (dialog, which) -> markOnboarded())
                .setNegativeButton("Import CSV", (dialog, which) -> {
                    markOnboarded();
                    startCsvImport();
                })
                .show();
    }

    private void showHowItWorks() {
        new AlertDialog.Builder(themedContext())
                .setTitle("How it works")
                .setMessage("• Tap  +  to add a task. It sorts itself by priority.\n\n"
                        + "• The card at the top is your #1 across every list. Tap its circle when it's done.\n\n"
                        + "• Tap a task to reveal its full content. Long-press it to edit.\n\n"
                        + "• Tap Adjust to set impact & effort, or mark it Urgent to send it to #1.\n\n"
                        + "• Swipe a task toward your thumb to finish it, the other way to park it in Later at the bottom of the list.\n\n"
                        + "• Switch lists with the strip next to  + . Your progress lives in the ⋮ menu.\n\n"
                        + "• Drag the  +  button (or tap the hand icon) to switch it between left, center and right.")
                .setPositiveButton("Close", null)
                .show();
    }

    private boolean isNightEffective() {
        if (THEME_SYSTEM.equals(theme)) {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }
        return THEME_NIGHT.equals(theme);
    }

    private PriorityPalette activePalette() {
        return isNightEffective() ? PriorityPalette.night() : PriorityPalette.day();
    }

    private Context themedContext() {
        return new ContextThemeWrapper(this,
                isNightEffective() ? R.style.AppTheme_Night : R.style.AppTheme);
    }

    private void cycleTheme() {
        if (THEME_DAY.equals(theme)) {
            theme = THEME_NIGHT;
        } else if (THEME_NIGHT.equals(theme)) {
            theme = THEME_SYSTEM;
        } else {
            theme = THEME_DAY;
        }
        persistPrefs();
        palette = activePalette();
        setTheme(isNightEffective() ? R.style.AppTheme_Night : R.style.AppTheme);
        rebuildEverything();
    }

    private void toggleHand() {
        if (HAND_RIGHT.equals(hand)) {
            hand = HAND_CENTER;
        } else if (HAND_CENTER.equals(hand)) {
            hand = HAND_LEFT;
        } else {
            hand = HAND_RIGHT;
        }
        persistPrefs();
        rebuildEverything();
    }

    private void rebuildEverything() {
        boolean reopenSheet = sheetOpen;
        if (sheetOpen) {
            removeSheetImmediate();
        }
        buildChrome();
        renderAll(false);
        if (reopenSheet) {
            presentSheet();
        }
    }

    // ===================== chrome =====================

    private static final int DOCK_HEIGHT = 52;
    private static final int DOCK_BOTTOM = 16;
    private static final int DOCK_EDGE = 16;
    private static final int DOCK_GAP = 10;
    private static final int FAB_SIZE = 52;

    private void buildChrome() {
        getWindow().setStatusBarColor(palette.bg);
        getWindow().setNavigationBarColor(palette.bg);
        int flags = isNightEffective() ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        handler.removeCallbacks(restoreListDock);
        root = new FrameLayout(this);
        root.setBackgroundColor(palette.bg);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        }

        LinearLayout column = vertical();
        column.setBackgroundColor(palette.bg);
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        column.addView(buildAppBar(), matchWrap(0, 0, 0, 0));
        LinearLayout body = vertical();
        // Clear the floating list dock (and the centered + button above it).
        body.setPadding(0, 0, 0, dp(HAND_CENTER.equals(hand) ? 160 : 104));
        body.addView(buildHeader(), matchWrap(0, 0, 0, 0));
        heroContainer = vertical();
        body.addView(heroContainer, matchWrap(16, 4, 16, 0));
        body.addView(buildListHeader(), matchWrap(0, 0, 0, 0));

        mainScroll = new ScrollView(this);
        ScrollView scroll = mainScroll;
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        listContainer = cardColumn();
        body.addView(listContainer, matchWrap(16, 0, 16, 0));
        laterHeaderView = horizontal();
        laterHeaderView.setPadding(dp(20), dp(16), dp(14), dp(4));
        laterHeaderView.setMinimumHeight(dp(52));
        laterHeaderView.setOnClickListener(v -> {
            laterExpanded = !laterExpanded;
            renderAll(false);
        });
        body.addView(laterHeaderView, matchWrap(0, 0, 0, 0));
        laterContainer = cardColumn();
        body.addView(laterContainer, matchWrap(16, 0, 16, 0));
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            handler.removeCallbacks(restoreListDock);
            if (Math.abs(y - oldY) > dp(2)) {
                setListDockVisible(y < oldY || y <= 0);
            }
            handler.postDelayed(restoreListDock, 380);
        });
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        addListDock();
        addActZone();

        confettiView = new ConfettiView(this);
        FrameLayout.LayoutParams confettiParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(confettiView, confettiParams);
        confettiView.setClickable(false);
        confettiView.setFocusable(false);

        setContentView(root);
        root.requestApplyInsets();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    /** Set a background without letting an InsetDrawable's insets replace the view's padding. */
    private void setBackgroundKeepPadding(View view, Drawable background) {
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setBackground(background);
        view.setPadding(left, top, right, bottom);
    }

    private GradientDrawable cardBackground(int radius) {
        GradientDrawable bg = rounded(palette.surface, radius);
        bg.setStroke(Math.max(1, dp(1)), palette.line);
        return bg;
    }

    /** White rounded card whose rows are clipped to its corners. */
    private LinearLayout cardColumn() {
        LinearLayout card = vertical();
        card.setBackground(cardBackground(18));
        int hairline = Math.max(1, dp(1));
        card.setPadding(hairline, hairline, hairline, hairline);
        card.setClipToOutline(true);
        return card;
    }

    private View dot(int sizeDp, int color, boolean hollow) {
        View dot = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (hollow) {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(Math.max(1, dp(2)), color);
        } else {
            bg.setColor(color);
        }
        dot.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.gravity = Gravity.CENTER_VERTICAL;
        dot.setLayoutParams(params);
        return dot;
    }

    private View glyphView(String kind, int sizeDp, int color) {
        View icon = new View(this);
        icon.setBackground(new Glyph(kind, dp(sizeDp), color));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.gravity = Gravity.CENTER_VERTICAL;
        icon.setLayoutParams(params);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    private TextView countBadge(String value, int bgColor, int fgColor) {
        TextView badge = text(value, 10, 800, fgColor);
        badge.setIncludeFontPadding(false);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(18));
        badge.setPadding(dp(5), dp(2), dp(5), dp(2));
        badge.setBackground(rounded(bgColor, 999));
        return badge;
    }

    private LinearLayout buildAppBar() {
        LinearLayout bar = horizontal();
        bar.setPadding(dp(20), dp(6), dp(10), dp(2));
        bar.setMinimumHeight(dp(56));
        TextView title = text("●  All Tasks", 18, 800, palette.ink);
        android.text.SpannableString appTitle = new android.text.SpannableString("●  All Tasks");
        appTitle.setSpan(new android.text.style.ForegroundColorSpan(PriorityPalette.IMMEDIATE),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        appTitle.setSpan(new android.text.style.RelativeSizeSpan(0.7f),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(appTitle);
        bar.addView(title, weight(1, 0, 0, 8, 0));
        if (!searchQuery.isEmpty()) {
            TextView clear = text("Clear", 13, 700, palette.info);
            clear.setGravity(Gravity.CENTER);
            clear.setMinWidth(dp(44));
            clear.setMinHeight(dp(44));
            clear.setPadding(dp(10), 0, dp(10), 0);
            clear.setContentDescription("Clear search");
            clear.setOnClickListener(v -> { searchQuery = ""; rebuildEverything(); });
            bar.addView(clear, wrap(0, 0, 2, 0));
        }
        HeaderIconButton search = new HeaderIconButton(this, "search");
        search.setContentDescription("Search tasks");
        search.setOnClickListener(v -> showSearch());
        bar.addView(search, wrap(0, 0, 2, 0));
        HeaderIconButton account = new HeaderIconButton(this, "account");
        account.setContentDescription("Sync settings");
        account.setOnClickListener(v -> showSyncSetup());
        bar.addView(account, wrap(0, 0, 0, 0));
        return bar;
    }

    private void showSearch() {
        EditText query = new EditText(themedContext());
        query.setSingleLine(true);
        query.setText(searchQuery);
        query.setHint("Title, details or list");
        query.setContentDescription("Search query");
        query.setTextColor(palette.ink);
        query.setHintTextColor(palette.sub);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout content = vertical();
        content.setPadding(dp(24), dp(4), dp(24), dp(4));
        content.addView(query, matchWrap(0, 0, 0, 0));
        AlertDialog dialog = new AlertDialog.Builder(themedContext())
                .setTitle("Search tasks").setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (d, which) -> {
                    searchQuery = query.getText().toString().trim();
                    expandedTaskKey = null;
                    rebuildEverything();
                }).create();
        query.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        dialog.show();
        query.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    /** "PRIORITY INDEX" kicker plus the current-list badge, which opens the list picker. */
    private View buildListHeader() {
        LinearLayout listHeader = horizontal();
        listHeader.setPadding(dp(20), dp(12), dp(16), dp(0));
        TextView indexLabel = text("PRIORITY INDEX", 11, 800, palette.sub);
        indexLabel.setLetterSpacing(0.08f);
        listHeader.addView(indexLabel, wrap(0, 0, 10, 0));

        LinearLayout badge = horizontal();
        badge.setMinimumHeight(dp(44));
        badge.setPadding(dp(9), 0, dp(5), 0);
        setBackgroundKeepPadding(badge, new InsetDrawable(rounded(palette.chip, 8), 0, dp(10), 0, dp(10)));
        badge.setContentDescription("Choose list");
        badge.setOnClickListener(v -> showNavigationLists());
        View marker = dot(6, palette.info, false);
        ((LinearLayout.LayoutParams) marker.getLayoutParams()).rightMargin = dp(6);
        badge.addView(marker);
        listFilterLabel = text("", 12, 700, palette.ink);
        listFilterLabel.setSingleLine(true);
        listFilterLabel.setEllipsize(TextUtils.TruncateAt.END);
        listFilterLabel.setMaxWidth(dp(170));
        listFilterLabel.setIncludeFontPadding(false);
        badge.addView(listFilterLabel);
        View caret = glyphView(Glyph.EXPAND_MORE, 16, palette.sub);
        ((LinearLayout.LayoutParams) caret.getLayoutParams()).leftMargin = dp(2);
        badge.addView(caret);
        listHeader.addView(badge, wrap(0, 0, 0, 0));
        listHeaderView = listHeader;
        return listHeader;
    }

    private void addListDock() {
        catStrip = horizontal();
        catStrip.setPadding(dp(2), 0, dp(2), 0);
        GradientDrawable bg = rounded(palette.surface, 18);
        bg.setStroke(Math.max(1, dp(1)), palette.line);
        catStrip.setBackground(bg);
        catStrip.setElevation(dp(6));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(DOCK_HEIGHT), Gravity.BOTTOM);
        params.bottomMargin = dp(DOCK_BOTTOM);
        int besideFab = DOCK_EDGE + FAB_SIZE + DOCK_GAP;
        params.leftMargin = dp(HAND_LEFT.equals(hand) ? besideFab : DOCK_EDGE);
        params.rightMargin = dp(HAND_RIGHT.equals(hand) ? besideFab : DOCK_EDGE);
        root.addView(catStrip, params);
    }

    private void setListDockVisible(boolean visible) {
        if (catStrip == null || sheetOpen) return;
        catStrip.animate().cancel();
        if (visible) {
            catStrip.setVisibility(View.VISIBLE);
            catStrip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            catStrip.animate().alpha(1f).translationY(0f).setDuration(180).start();
        } else {
            catStrip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            catStrip.animate().alpha(0f).translationY(dp(18)).setDuration(180)
                    .withEndAction(() -> catStrip.setVisibility(View.INVISIBLE)).start();
        }
    }

    // ---- bottom-sheet style pickers (lists, progress) ----

    private Dialog sheetDialog(String title, View body) {
        Dialog dialog = new Dialog(themedContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = vertical();
        panel.setPadding(dp(8), dp(8), dp(8), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.surface);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        View grip = new View(this);
        grip.setBackground(rounded(palette.line, 2));
        LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        gripParams.gravity = Gravity.CENTER_HORIZONTAL;
        gripParams.topMargin = dp(2);
        gripParams.bottomMargin = dp(12);
        panel.addView(grip, gripParams);
        TextView heading = text(title, 17, 800, palette.ink);
        heading.setPadding(dp(16), 0, dp(16), dp(10));
        panel.addView(heading, matchWrap(0, 0, 0, 0));
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, matchWrap(0, 0, 0, 0));
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
        return dialog;
    }

    private void showSheetDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private View optionRow(String label, int count, boolean selected, View lead, Runnable onTap) {
        LinearLayout row = horizontal();
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(14), 0, dp(14), 0);
        if (selected) {
            row.setBackground(rounded(palette.soft, 14));
        }
        if (lead != null) {
            LinearLayout.LayoutParams leadParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            leadParams.gravity = Gravity.CENTER_VERTICAL;
            leadParams.rightMargin = dp(14);
            FrameLayout leadBox = new FrameLayout(this);
            ViewGroup.LayoutParams own = lead.getLayoutParams();
            int w = own != null && own.width > 0 ? own.width : dp(20);
            int h = own != null && own.height > 0 ? own.height : dp(20);
            leadBox.addView(lead, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
            row.addView(leadBox, leadParams);
        }
        TextView name = text(label, 15, selected ? 700 : 500, palette.ink);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, weight(1, 0, 0, 8, 0));
        if (count > 0) {
            row.addView(countBadge(String.valueOf(count), palette.chip, palette.sub), wrap(0, 0, 0, 0));
        }
        if (selected) {
            View check = glyphView(Glyph.CHECK, 18, palette.info);
            ((LinearLayout.LayoutParams) check.getLayoutParams()).leftMargin = dp(10);
            row.addView(check);
        }
        row.setOnClickListener(v -> onTap.run());
        return row;
    }

    private void showNavigationLists() {
        LinearLayout options = vertical();
        Dialog dialog = sheetDialog("Lists", options);
        for (String name : tabList()) {
            boolean selected = name.equals(activeCat);
            View lead = "All".equals(name)
                    ? glyphView(Glyph.LIST, 20, selected ? palette.info : palette.sub)
                    : dot(9, selected ? palette.info : palette.sub, !selected);
            options.addView(optionRow(name, catCount(name), selected, lead, () -> {
                dialog.dismiss();
                selectNavigationList(name);
            }), matchWrap(0, 0, 0, 2));
        }
        options.addView(optionRow("New list", 0, false, glyphView(Glyph.PLUS, 20, palette.info), () -> {
            dialog.dismiss();
            showCreateListForNavigation();
        }), matchWrap(0, 0, 0, 0));
        showSheetDialog(dialog);
    }

    private void showCreateListForNavigation() {
        EditText input = new EditText(themedContext());
        input.setSingleLine(true);
        input.setHint("New list name");
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.sub);
        AlertDialog dialog = new AlertDialog.Builder(themedContext()).setTitle("New list")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty() || "All".equalsIgnoreCase(name)) {
                input.setError("Enter a list name other than All");
                return;
            }
            for (String existing : categories) {
                if (existing.equalsIgnoreCase(name)) {
                    selectNavigationList(existing);
                    dialog.dismiss();
                    return;
                }
            }
            activateCategory(name, nextMutationTimestamp());
            persistMutation();
            selectNavigationList(name);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void selectNavigationList(String name) {
        activeCat = "All".equals(name) || categories.contains(name) ? name : "All";
        expandedTaskKey = null;
        if (mainScroll != null) mainScroll.scrollTo(0, 0);
        renderAll(false);
    }

    private void stepNavigationList(int direction) {
        List<String> tabs = tabList();
        int index = Math.max(0, tabs.indexOf(activeCat));
        int next = (index + direction + tabs.size()) % tabs.size();
        selectNavigationList(tabs.get(next));
    }

    private void showProgress() {
        int done = 0, active = 0, later = 0, recurring = 0;
        for (TodoTask task : tasks) {
            if (task.completed) {
                done++;
                continue;
            }
            if (task.snoozed) later++; else active++;
            if (task.reminderAt > 0 && !TodoTask.REPEAT_NONE.equals(task.reminderRepeatUnit)) recurring++;
        }
        LinearLayout body = vertical();
        body.setPadding(dp(16), 0, dp(16), dp(4));
        body.addView(text("Every task counts", 14, 600, palette.sub));
        LinearLayout big = horizontal();
        big.setGravity(Gravity.BOTTOM);
        TextView doneCount = text(String.valueOf(done), 40, 800, palette.done);
        doneCount.setIncludeFontPadding(false);
        big.addView(doneCount);
        TextView doneLabel = text("completed", 15, 700, palette.ink);
        big.addView(doneLabel, wrap(8, 0, 0, 4));
        body.addView(big, matchWrap(0, 8, 0, 14));

        LinearLayout track = horizontal();
        track.setBackground(rounded(palette.chip, 4));
        track.setClipToOutline(true);
        View fill = new View(this);
        fill.setBackground(rounded(palette.done, 4));
        int open = active + later;
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(8), done));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8), done + open == 0 ? 1 : open));
        body.addView(track, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout tiles = horizontal();
        tiles.addView(statTile(active, "Active"), weight(1, 0, 0, 8, 0));
        tiles.addView(statTile(later, "Later"), weight(1, 0, 0, 8, 0));
        tiles.addView(statTile(recurring, "Repeating"), weight(1, 0, 0, 0, 0));
        body.addView(tiles, matchWrap(0, 16, 0, 12));
        body.addView(text("Counts every task saved on this device, including synced ones.",
                12, 500, palette.sub));
        showSheetDialog(sheetDialog("Your progress", body));
    }

    private View statTile(int value, String label) {
        LinearLayout tile = vertical();
        tile.setPadding(dp(12), dp(10), dp(12), dp(10));
        tile.setBackground(rounded(palette.soft, 14));
        tile.addView(text(String.valueOf(value), 20, 800, palette.ink));
        tile.addView(text(label, 12, 600, palette.sub));
        return tile;
    }

    private void renderReminderSettings() {
        reminderSettingsPanel.removeAllViews();
        reminderSettingsPanel.setVisibility(reminderSettingsOpen ? View.VISIBLE : View.GONE);
        if (!reminderSettingsOpen) return;
        LinearLayout heading = horizontal();
        View alarm = glyphView(Glyph.CLOCK, 18, palette.info);
        ((LinearLayout.LayoutParams) alarm.getLayoutParams()).rightMargin = dp(8);
        heading.addView(alarm);
        heading.addView(text("Set reminder", 14, 800, palette.ink), weight(1, 0, 0, 4, 0));
        View close = new View(this);
        close.setBackground(new Glyph(Glyph.CLOSE, dp(18), palette.sub, 2.2f));
        close.setContentDescription("Close reminder settings");
        close.setOnClickListener(v -> { reminderSettingsOpen = false; updateSheetDynamic(); });
        heading.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        reminderSettingsPanel.addView(heading);

        LinearLayout shortcuts = horizontal();
        String[] presets = {"Later today", "Tomorrow", "Weekend"};
        for (int i = 0; i < presets.length; i++) {
            String preset = presets[i];
            Calendar target = TaskViews.reminderPreset(Calendar.getInstance(), preset);
            boolean chosen = draftReminderAt == target.getTimeInMillis();
            String presetLabel = preset;
            if ("Later today".equals(preset)
                    && target.get(Calendar.DAY_OF_YEAR) != Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) {
                presetLabel = "Next evening";
            }
            String time = android.text.format.DateFormat.getTimeFormat(this).format(target.getTime());
            if ("Weekend".equals(preset)) {
                time = new java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                        .format(target.getTime()) + " " + time;
            }
            LinearLayout tile = vertical();
            tile.setPadding(dp(10), dp(9), dp(8), dp(9));
            tile.setMinimumHeight(dp(52));
            tile.setBackground(rounded(chosen ? palette.info : palette.soft, 12));
            tile.setContentDescription(preset);
            TextView kicker = text(presetLabel.toUpperCase(java.util.Locale.ROOT), 10, 800,
                    chosen ? PriorityPalette.withAlpha(palette.infoInk, 0xCC) : palette.sub);
            kicker.setLetterSpacing(0.04f);
            kicker.setSingleLine(true);
            kicker.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(kicker);
            TextView when = text(time, 13, 700, chosen ? palette.infoInk : palette.ink);
            when.setSingleLine(true);
            when.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(when, matchWrap(0, 2, 0, 0));
            tile.setOnClickListener(v -> {
                draftReminderAt = TaskViews.reminderPreset(Calendar.getInstance(), preset).getTimeInMillis();
                updateSheetDynamic();
            });
            shortcuts.addView(tile, weight(1, 0, 0, i < presets.length - 1 ? 6 : 0, 0));
        }
        reminderSettingsPanel.addView(shortcuts, matchWrap(0, 2, 6, 8));

        TextView custom = text("Custom date & time", 13, 600, palette.ink);
        custom.setCompoundDrawablesRelativeWithIntrinsicBounds(
                new Glyph(Glyph.CALENDAR, dp(16), palette.info, 2.2f), null,
                new Glyph(Glyph.CHEVRON_RIGHT, dp(16), palette.sub, 2.2f), null);
        custom.setCompoundDrawablePadding(dp(8));
        custom.setMinHeight(dp(44));
        custom.setPadding(dp(12), 0, dp(10), 0);
        custom.setGravity(Gravity.CENTER_VERTICAL);
        custom.setBackground(rounded(palette.soft, 12));
        custom.setContentDescription("Custom date & time");
        custom.setOnClickListener(v -> openReminderPicker());
        reminderSettingsPanel.addView(custom, matchWrap(0, 0, 6, 0));
        if (draftReminderAt > 0) {
            reminderRepeatRow = horizontal();
            buildRepeatControls(reminderRepeatRow);
            reminderSettingsPanel.addView(reminderRepeatRow, matchWrap(0, 8, 6, 0));
        }
    }

    private LinearLayout buildHeader() {
        LinearLayout header = horizontal();
        header.setPadding(dp(20), dp(6), dp(8), dp(8));
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = vertical();
        TextView today = text(searchQuery.isEmpty() ? "Today" : "Search", 26, 800, palette.ink);
        today.setIncludeFontPadding(false);
        today.setLetterSpacing(-0.02f);
        headerSubView = text("", 13, 500, palette.sub);
        titleCol.addView(today);
        titleCol.addView(headerSubView, matchWrap(0, 5, 0, 0));
        header.addView(titleCol, weight(1, 0, 0, 0, 0));

        // Quiet icon-only cluster. Import/Export/Progress live in the overflow menu.
        LinearLayout toggles = horizontal();
        themePill = new HeaderIconButton(this, "theme");
        themePill.setOnClickListener(v -> cycleTheme());
        handPill = new HeaderIconButton(this, "hand");
        handPill.setOnClickListener(v -> toggleHand());
        HeaderIconButton more = new HeaderIconButton(this, "more");
        more.setContentDescription("More options");
        more.setOnClickListener(this::showOverflowMenu);
        toggles.addView(themePill, wrap(0, 0, 0, 0));
        toggles.addView(handPill, wrap(0, 0, 0, 0));
        toggles.addView(more, wrap(0, 0, 0, 0));
        header.addView(toggles, wrap(0, 0, 0, 0));
        return header;
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(themedContext(), anchor);
        menu.getMenu().add(0, 6, 0, "Progress");
        menu.getMenu().add(0, 1, 1, "Import CSV");
        menu.getMenu().add(0, 2, 2, "Export CSV");
        menu.getMenu().add(0, 3, 3, "How it works");
        menu.getMenu().add(0, 4, 4, "Sync with web");
        menu.getMenu().add(0, 5, 5, "Sync now");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 6:
                    showProgress();
                    return true;
                case 1:
                    startCsvImport();
                    return true;
                case 2:
                    startCsvExport();
                    return true;
                case 3:
                    showHowItWorks();
                    return true;
                case 4:
                    showSyncSetup();
                    return true;
                case 5:
                    if (syncBaseUrl.isEmpty()) {
                        showSyncSetup();
                    } else {
                        syncNow(true);
                    }
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void showSyncSetup() {
        LinearLayout fields = vertical();
        fields.setPadding(dp(24), 0, dp(24), 0);

        EditText endpointInput = new EditText(themedContext());
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setHint("Server URL");
        endpointInput.setText(syncBaseUrl.isEmpty() ? DEFAULT_SYNC_BASE_URL : syncBaseUrl);
        endpointInput.setSelectAllOnFocus(true);
        fields.addView(endpointInput, matchWrap(0, 0, 0, 8));

        boolean credentialsMatchServer = !syncBaseUrl.isEmpty()
                && syncBaseUrl.equals(syncAccessBaseUrl);
        EditText clientIdInput = new EditText(themedContext());
        clientIdInput.setSingleLine(true);
        clientIdInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        clientIdInput.setHint("Cloudflare Access Client ID (optional)");
        clientIdInput.setText(credentialsMatchServer ? syncAccessClientId : "");
        clientIdInput.setSelectAllOnFocus(true);
        fields.addView(clientIdInput, matchWrap(0, 0, 0, 8));

        EditText clientSecretInput = new EditText(themedContext());
        clientSecretInput.setSingleLine(true);
        clientSecretInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        clientSecretInput.setHint("Cloudflare Access Client Secret (optional)");
        clientSecretInput.setText(credentialsMatchServer ? syncAccessClientSecret : "");
        clientSecretInput.setSelectAllOnFocus(true);
        fields.addView(clientSecretInput, matchWrap(0, 0, 0, 0));

        final String initialEndpoint = endpointInput.getText().toString().trim();
        endpointInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                if (!value.toString().trim().equals(initialEndpoint)
                        && (!clientIdInput.getText().toString().isEmpty()
                        || !clientSecretInput.getText().toString().isEmpty())) {
                    clientIdInput.setText("");
                    clientSecretInput.setText("");
                }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(themedContext())
                .setTitle("Sync with web")
                .setMessage("Local servers need only the URL. Cloudflare Access also needs its Client ID and Secret.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        String clientId = clientIdInput.getText().toString().trim();
                        String clientSecret = clientSecretInput.getText().toString().trim();
                        if (clientId.isEmpty() != clientSecret.isEmpty()) {
                            if (clientId.isEmpty()) {
                                clientIdInput.setError("Enter the Client ID too.");
                            } else {
                                clientSecretInput.setError("Enter the Client Secret too.");
                            }
                            return;
                        }
                        String normalizedBaseUrl = SyncClient.normalizeBaseUrl(
                                endpointInput.getText().toString());
                        SyncClient.validateAccessCredentials(
                                normalizedBaseUrl, clientId, clientSecret);
                        syncBaseUrl = normalizedBaseUrl;
                        syncAccessClientId = clientId;
                        syncAccessClientSecret = clientSecret;
                        syncAccessBaseUrl = clientId.isEmpty() ? "" : syncBaseUrl;
                        persistPrefs();
                        getSharedPreferences(SYNC_CREDENTIAL_PREFS, MODE_PRIVATE).edit()
                                .putString(KEY_SYNC_ACCESS_CLIENT_ID, syncAccessClientId)
                                .putString(KEY_SYNC_ACCESS_CLIENT_SECRET, syncAccessClientSecret)
                                .putString(KEY_SYNC_ACCESS_BASE_URL, syncAccessBaseUrl)
                                .apply();
                        endpointInput.setError(null);
                        clientIdInput.setError(null);
                        clientSecretInput.setError(null);
                        dialog.dismiss();
                        syncNow(true);
                    } catch (IllegalArgumentException exception) {
                        endpointInput.setError(exception.getMessage());
                    }
                }));
        dialog.show();
    }

    private SyncState currentSyncState() {
        return new SyncState(tasks, taskTombstones, categoryStates);
    }

    private void replaceSyncState(SyncState state) {
        tasks.clear();
        tasks.addAll(state.tasks);
        taskTombstones.clear();
        taskTombstones.addAll(state.taskTombstones);
        categoryStates.clear();
        categoryStates.addAll(state.categories);
        rebuildActiveCategories();
    }

    private void persistMutation() {
        if (destroyed) {
            return;
        }
        store.saveSyncState(currentSyncState());
        if (!syncBaseUrl.isEmpty()) {
            handler.removeCallbacks(debouncedSync);
            handler.postDelayed(debouncedSync, 180);
        }
    }

    private void syncNow(boolean manual) {
        if (destroyed || syncBaseUrl.isEmpty()) {
            return;
        }
        handler.removeCallbacks(debouncedSync);
        if (syncInFlight) {
            syncQueued = true;
            queuedManualSync |= manual;
            return;
        }

        final long requestedRevision = store.revision();
        final String requestJson;
        try {
            requestJson = currentSyncState().toJson().toString();
        } catch (Exception exception) {
            if (manual) {
                showDataError("Sync failed", "Clearflow couldn't prepare the local data.");
            }
            return;
        }
        syncInFlight = true;
        final String baseUrl = syncBaseUrl;
        final String accessClientId = syncAccessClientId;
        final String accessClientSecret = syncAccessClientSecret;
        try {
            SyncClient.validateAccessCredentials(
                    baseUrl, accessClientId, accessClientSecret);
            if (!accessClientId.isEmpty() && !baseUrl.equals(syncAccessBaseUrl)) {
                throw new IllegalArgumentException(
                        "Cloudflare Access credentials belong to a different server. Open Sync with web and paste them again.");
            }
        } catch (IllegalArgumentException exception) {
            syncInFlight = false;
            if (manual) {
                showDataError("Sync credentials invalid", exception.getMessage());
            }
            return;
        }
        new Thread(() -> {
            try {
                SyncState response = SyncClient.sync(
                        baseUrl, accessClientId, accessClientSecret, requestJson);
                handler.post(() -> finishSyncSuccess(
                        response, requestedRevision, baseUrl, manual));
            } catch (Exception exception) {
                handler.post(() -> finishSyncFailure(baseUrl, manual));
            }
        }, "clearflow-sync").start();
    }

    private void finishSyncSuccess(SyncState response, long requestedRevision,
                                   String requestedBaseUrl, boolean manual) {
        if (destroyed || isFinishing() || isDestroyed()) {
            return;
        }
        if (!requestedBaseUrl.equals(syncBaseUrl)) {
            queueFreshEndpointSync();
        } else if (SyncClient.responseMatchesRequest(
                requestedRevision, store.revision(), requestedBaseUrl, syncBaseUrl)) {
            applyAuthoritativeSyncState(response);
            if (manual) {
                showCheer("Synced");
            }
        } else {
            syncQueued = true;
        }
        finishSyncAttempt();
    }

    private void finishSyncFailure(String requestedBaseUrl, boolean manual) {
        if (destroyed || isFinishing() || isDestroyed()) {
            return;
        }
        if (!requestedBaseUrl.equals(syncBaseUrl)) {
            queueFreshEndpointSync();
        } else if (manual) {
            showDataError("Sync unavailable",
                    "Your tasks are still saved on this device. Check the web server address and try again.");
        }
        finishSyncAttempt();
    }

    private void finishSyncAttempt() {
        if (destroyed) {
            return;
        }
        syncInFlight = false;
        if (syncQueued) {
            boolean manual = queuedManualSync;
            syncQueued = false;
            queuedManualSync = false;
            syncNow(manual);
        }
    }

    private void queueFreshEndpointSync() {
        syncQueued = true;
        queuedManualSync = true;
    }

    private long nextMutationTimestamp() {
        return currentSyncState().nextMutationTimestamp(System.currentTimeMillis());
    }

    private void applyAuthoritativeSyncState(SyncState state) {
        if (currentSyncState().hasSameWireState(state)) {
            scheduleTaskReminders();
            return;
        }
        for (TodoTask task : tasks) {
            ReminderScheduler.cancel(this, task);
        }
        replaceSyncState(state);
        if (!"All".equals(activeCat) && !categories.contains(activeCat)) {
            activeCat = "All";
        }
        if (draftCategory != null && !categories.contains(draftCategory)) {
            draftCategory = null;
            if (sheetOpen) {
                styleListPill();
            }
        }
        store.saveSyncState(currentSyncState());
        scheduleTaskReminders();
        renderAll(false);
    }

    private void addActZone() {
        fab = new View(this);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(palette.accent);
        fabBg.setStroke(Math.max(1, dp(1)), PriorityPalette.withAlpha(palette.accentInk, 0x33));
        LayerDrawable fabLayers = new LayerDrawable(new Drawable[]{
                fabBg, new Glyph(Glyph.PLUS, dp(24), palette.accentInk, 2.4f)});
        int plusInset = dp((FAB_SIZE - 24) / 2);
        fabLayers.setLayerInset(1, plusInset, plusInset, plusInset, plusInset);
        fab.setBackground(fabLayers);
        fab.setElevation(dp(8));
        fab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dp(FAB_SIZE), dp(FAB_SIZE));
        fabParams.gravity = Gravity.BOTTOM | horizontalGravityForAddButton();
        // Right/left: the + sits beside the list dock. Center: it floats just above it.
        fabParams.bottomMargin = dp(HAND_CENTER.equals(hand)
                ? DOCK_BOTTOM + DOCK_HEIGHT + 12 : DOCK_BOTTOM);
        if (HAND_RIGHT.equals(hand)) {
            fabParams.rightMargin = dp(DOCK_EDGE);
        } else if (HAND_LEFT.equals(hand)) {
            fabParams.leftMargin = dp(DOCK_EDGE);
        }
        root.addView(fab, fabParams);

        // Transparent touch target above the visual button: tap adds, long-press drags.
        fabTarget = new View(this);
        fabTarget.setContentDescription("Add task");
        fabTarget.setOnClickListener(v -> openAddSheet());
        fabTarget.setOnTouchListener(this::handleFabTouch);
        fabTarget.setElevation(dp(9));
        FrameLayout.LayoutParams targetParams = new FrameLayout.LayoutParams(dp(FAB_SIZE), dp(FAB_SIZE));
        targetParams.gravity = fabParams.gravity;
        targetParams.bottomMargin = fabParams.bottomMargin;
        targetParams.leftMargin = fabParams.leftMargin;
        targetParams.rightMargin = fabParams.rightMargin;
        root.addView(fabTarget, targetParams);
    }

    private int horizontalGravityForAddButton() {
        if (HAND_LEFT.equals(hand)) {
            return Gravity.START;
        }
        if (HAND_CENTER.equals(hand)) {
            return Gravity.CENTER_HORIZONTAL;
        }
        return Gravity.END;
    }

    // ---- drag the + button to reposition it (snaps to left / center / right) ----

    private boolean handleFabTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fabDownRawX = event.getRawX();
                fabDownRawY = event.getRawY();
                fabMoved = false;
                fabDragging = false;
                fabLongPressRunnable = this::beginFabDrag;
                handler.postDelayed(fabLongPressRunnable, 260);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - fabDownRawX;
                float dy = event.getRawY() - fabDownRawY;
                if (!fabDragging) {
                    if (Math.abs(dx) > dp(12) || Math.abs(dy) > dp(12)) {
                        fabMoved = true;
                        beginFabDrag();
                    } else {
                        return true;
                    }
                }
                moveFabBy(dx, dy);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (fabLongPressRunnable != null) {
                    handler.removeCallbacks(fabLongPressRunnable);
                    fabLongPressRunnable = null;
                }
                boolean wasDragging = fabDragging;
                fabDragging = false;
                if (wasDragging) {
                    endFabDrag();
                } else if (!fabMoved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    openAddSheet();
                }
                return true;
            }
            default:
                return false;
        }
    }

    private void beginFabDrag() {
        if (fabDragging) {
            return;
        }
        fabDragging = true;
        if (fabLongPressRunnable != null) {
            handler.removeCallbacks(fabLongPressRunnable);
            fabLongPressRunnable = null;
        }
        if (fab != null) {
            fab.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            fab.animate().cancel();
            fab.animate().scaleX(1.14f).scaleY(1.14f).setDuration(120).start();
            fab.setElevation(dp(16));
        }
        if (reachArc != null) {
            reachArc.animate().alpha(0f).setDuration(120).start();
        }
    }

    private void moveFabBy(float dx, float dy) {
        if (fab != null) {
            fab.setTranslationX(dx);
            fab.setTranslationY(dy);
        }
        if (fabTarget != null) {
            fabTarget.setTranslationX(dx);
            fabTarget.setTranslationY(dy);
        }
    }

    private void endFabDrag() {
        if (fab != null) {
            fab.animate().cancel();
            fab.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            fab.setElevation(dp(8));
        }
        View ref = fab != null ? fab : fabTarget;
        if (ref == null || root == null) {
            snapFabBack();
            return;
        }
        int[] loc = new int[2];
        ref.getLocationOnScreen(loc);
        float centerX = loc[0] + ref.getWidth() / 2f;
        int screenW = root.getWidth() > 0 ? root.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        String newHand;
        if (centerX < screenW / 3f) {
            newHand = HAND_LEFT;
        } else if (centerX > screenW * 2f / 3f) {
            newHand = HAND_RIGHT;
        } else {
            newHand = HAND_CENTER;
        }
        if (!newHand.equals(hand)) {
            hand = newHand;
            persistPrefs();
            showCheer(handMovedLabel(newHand));
            rebuildEverything();
        } else {
            snapFabBack();
        }
    }

    private void snapFabBack() {
        for (View x : new View[]{fab, fabTarget}) {
            if (x != null) {
                x.animate().translationX(0f).translationY(0f)
                        .setDuration(220)
                        .setInterpolator(new OvershootInterpolator(1.2f))
                        .start();
            }
        }
        if (reachArc != null) {
            reachArc.animate().alpha(1f).setDuration(200).start();
        }
    }

    private String handMovedLabel(String value) {
        if (HAND_LEFT.equals(value)) {
            return "Moved to the left";
        }
        if (HAND_CENTER.equals(value)) {
            return "Centered";
        }
        return "Moved to the right";
    }

    // ===================== rendering =====================

    private void renderAll(boolean animateReorder) {
        sortTasks();
        long now = System.currentTimeMillis();
        String listFilter = "All".equals(activeCat) ? null : activeCat;
        boolean searching = !searchQuery.isEmpty();

        // The top zone is global: your #1 across every list and overall progress.
        List<TodoTask> activeEverywhere = TaskViews.select(tasks, null, false, VIEW_ACTIVE, "", now);
        int laterEverywhere = TaskViews.select(tasks, null, false, VIEW_LATER, "", now).size();
        int done = 0;
        for (TodoTask t : tasks) {
            if (t.completed) done++;
        }
        int toGo = activeEverywhere.size();
        int total = toGo + laterEverywhere + done;
        int pct = total == 0 ? 0 : Math.round((done / (float) total) * 100f);
        globalRanks.clear();
        for (int i = 0; i < activeEverywhere.size(); i++) {
            globalRanks.put(activeEverywhere.get(i).id, i);
        }
        globalRankTotal = activeEverywhere.size();
        TodoTask mit = searching || activeEverywhere.isEmpty() ? null : activeEverywhere.get(0);

        // The bottom zone is the selected list, without repeating the hero task.
        List<TodoTask> listed = TaskViews.select(tasks, listFilter, false, VIEW_ACTIVE, searchQuery, now);
        int inList = listed.size();
        if (mit != null) {
            listed.remove(mit);
        }
        List<TodoTask> later = TaskViews.select(tasks, listFilter, false, VIEW_LATER, searchQuery, now);

        if (searching) {
            int matches = inList + later.size();
            headerSubView.setText(matches + (matches == 1 ? " match" : " matches"));
        } else {
            headerSubView.setText(toGo + " to go  ·  " + done + " done"
                    + (laterEverywhere > 0 ? "  ·  " + laterEverywhere + " later" : ""));
        }
        if (handPill != null) {
            handPill.setState(hand, theme);
        }
        if (themePill != null) {
            themePill.setState(hand, theme);
        }
        renderCategoryStrip();
        listFilterLabel.setText(activeCat + " (" + inList + ")");
        renderHero(mit, pct, done, total, laterEverywhere, inList + later.size());
        renderList(listed, inList, animateReorder);
        renderLater(later);
        scheduleReminderPriorityRefresh(now);
    }

    private void scheduleReminderPriorityRefresh(long renderedAt) {
        handler.removeCallbacks(reminderPriorityRefresh);
        if (!resumed) {
            return;
        }
        long nextTransition = Long.MAX_VALUE;
        for (TodoTask task : tasks) {
            if (task.completed || task.snoozed || task.reminderAt <= 0) {
                continue;
            }
            long transition = task.reminderAt - TodoTask.REMINDER_PRIORITY_WINDOW_MS;
            if (transition > renderedAt && transition < nextTransition) {
                nextTransition = transition;
            }
        }
        if (nextTransition != Long.MAX_VALUE) {
            handler.postDelayed(reminderPriorityRefresh,
                    Math.max(1, nextTransition - System.currentTimeMillis()));
        }
    }

    private int rankColor(TodoTask task, int fallbackRank) {
        Integer rank = globalRanks.get(task.id);
        int resolved = rank != null ? rank : fallbackRank;
        return PriorityPalette.spectrumColor(resolved, Math.max(globalRankTotal, resolved + 1),
                isNightEffective());
    }

    private LinearLayout kickerRow(int dotColor, String label, int labelColor, String trailing) {
        LinearLayout row = horizontal();
        View marker = dot(6, dotColor, false);
        ((LinearLayout.LayoutParams) marker.getLayoutParams()).rightMargin = dp(7);
        row.addView(marker);
        TextView kicker = text(label, 11, 800, labelColor);
        kicker.setLetterSpacing(0.1f);
        kicker.setSingleLine(true);
        row.addView(kicker, weight(1, 0, 0, 6, 0));
        if (trailing != null) {
            progressText = text(trailing, 11, 600, palette.heroSub);
            row.addView(progressText, wrap(0, 0, 0, 0));
        }
        return row;
    }

    private void renderHero(TodoTask mit, int pct, int done, int total, int laterCount, int matches) {
        heroContainer.removeAllViews();
        int accent = PriorityPalette.spectrumColor(0, Math.max(1, globalRankTotal), isNightEffective());
        boolean searching = !searchQuery.isEmpty();
        LinearLayout card = vertical();
        card.setPadding(dp(18), dp(16), dp(8), dp(16));
        card.setBackground(cardBackground(20));
        card.setClipToOutline(true);

        if (searching) {
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            card.addView(kickerRow(palette.info, "SEARCH", palette.info, null), matchWrap(0, 0, 0, 8));
            TextView query = text("“" + searchQuery + "”", 20, 800, palette.heroInk);
            query.setMaxLines(2);
            query.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(query);
            card.addView(text(matches == 0 ? "No matching tasks. Try another word or clear the search."
                    : matches + (matches == 1 ? " matching task below." : " matching tasks below."),
                    13, 500, palette.heroSub), matchWrap(0, 6, 0, 0));
            heroContainer.addView(card, matchWrap(0, 0, 0, 0));
            return;
        }

        // 4dp progress rail along the card's top edge.
        LinearLayout track = horizontal();
        track.setBackgroundColor(PriorityPalette.withAlpha(palette.heroInk, 0x12));
        progressFill = new View(this);
        progressFill.setBackground(rounded(accent, 2));
        track.addView(progressFill, new LinearLayout.LayoutParams(0, dp(4), pct));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(4), Math.max(0, 100 - pct)));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        trackParams.setMargins(-dp(18), -dp(16), -dp(8), dp(14));
        card.addView(track, trackParams);
        String progress = done + " of " + total + " done";

        if (mit == null) {
            card.setPadding(dp(18), dp(16), dp(18), dp(18));
            trackParams.setMargins(-dp(18), -dp(16), -dp(18), dp(14));
            card.addView(kickerRow(palette.done, "ALL CLEAR", palette.done, total > 0 ? progress : null),
                    matchWrap(0, 0, 0, 8));
            card.addView(text("Nothing urgent right now", 20, 800, palette.heroInk));
            String hint = laterCount > 0
                    ? laterCount + (laterCount == 1 ? " task is" : " tasks are") + " waiting in Later. Tap + to add more."
                    : "Tap + to add a task. It sorts itself to the top.";
            card.addView(text(hint, 13, 500, palette.heroSub), matchWrap(0, 6, 0, 0));
            heroContainer.addView(card, matchWrap(0, 0, 0, 0));
            return;
        }

        FrameLayout heroWrap = new FrameLayout(this);
        TextView heroReveal = text("", 12, 800, 0xFFFFFFFF);
        heroReveal.setLetterSpacing(0.08f);
        heroReveal.setGravity(Gravity.CENTER_VERTICAL);
        heroReveal.setPadding(dp(22), 0, dp(22), 0);
        heroReveal.setVisibility(View.INVISIBLE);
        heroWrap.addView(heroReveal, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout heroFg = vertical();
        heroFg.setBackgroundColor(palette.surface);
        boolean heroExpanded = isTaskExpanded("hero", mit.id);
        if (heroExpanded) {
            heroFg.setContentDescription("Expanded content for " + mit.title);
        }
        LinearLayout kicker = kickerRow(accent, "YOUR #1 RIGHT NOW", accent, progress);
        heroFg.addView(kicker, matchWrap(0, 0, 10, 6));

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.TOP);
        TextView mitName = text(mit.title, 21, 800, palette.heroInk);
        mitName.setLetterSpacing(-0.01f);
        mitName.setSingleLine(false);
        mitName.setMaxLines(heroExpanded ? Integer.MAX_VALUE : 3);
        mitName.setEllipsize(heroExpanded ? null : TextUtils.TruncateAt.END);
        titleRow.addView(mitName, weight(1, 0, 0, 2, 0));
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        ringParams.topMargin = -dp(10);
        titleRow.addView(completionTarget(mit, accent, 22), ringParams);
        heroFg.addView(titleRow, matchWrap(0, 0, 0, 0));

        if (heroExpanded && !TextUtils.isEmpty(mit.notes)) {
            TextView notes = text(mit.notes, 13, 500, palette.heroSub);
            notes.setMaxLines(6);
            notes.setEllipsize(TextUtils.TruncateAt.END);
            heroFg.addView(notes, matchWrap(0, 4, 10, 0));
        }

        boolean hasReminder = mit.reminderAt > 0;
        if (hasReminder || mit.category != null) {
            LinearLayout meta = horizontal();
            if (hasReminder) {
                TextView remind = text(reminderShort(mit), 12, 700, accent);
                remind.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        new Glyph(Glyph.CLOCK, dp(14), accent), null, null, null);
                remind.setCompoundDrawablePadding(dp(6));
                remind.setIncludeFontPadding(false);
                remind.setPadding(dp(10), dp(6), dp(12), dp(6));
                remind.setBackground(rounded(PriorityPalette.withAlpha(accent, 0x1F), 999));
                meta.addView(remind, wrap(0, 0, 8, 0));
            }
            if (mit.category != null) {
                meta.addView(new TagView(mit.category, palette.sub), wrap(0, 0, 0, 0));
            }
            heroFg.addView(meta, matchWrap(0, heroExpanded ? 12 : 6, 0, 0));
        }
        heroWrap.addView(heroFg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        attachHeroSwipe(heroFg, heroReveal, mit.id);
        card.addView(heroWrap, matchWrap(0, 0, 0, 0));
        heroContainer.addView(card, matchWrap(0, 0, 0, 0));
    }

    /** 48dp tap target around a small rank-colored completion ring. */
    private FrameLayout completionTarget(TodoTask task, int color, int ringDp) {
        CheckBox circle = new CheckBox(this);
        circle.setButtonDrawable(null);
        circle.setText("");
        circle.setMinWidth(0);
        circle.setMinHeight(0);
        circle.setPadding(0, 0, 0, 0);
        circle.setContentDescription("Complete task: " + task.title);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(2), color);
        circle.setBackground(ring);
        circle.setOnClickListener(v -> {
            int[] loc = new int[2];
            int[] rootLoc = new int[2];
            v.getLocationOnScreen(loc);
            root.getLocationOnScreen(rootLoc);
            float cx = loc[0] - rootLoc[0] + v.getWidth() / 2f;
            float cy = loc[1] - rootLoc[1] + v.getHeight() / 2f;
            confettiView.burst(cx, cy, CONFETTI_COLORS);
            completeTask(task.id);
        });
        FrameLayout target = new FrameLayout(this);
        target.setOnClickListener(v -> circle.performClick());
        target.addView(circle, new FrameLayout.LayoutParams(dp(ringDp), dp(ringDp), Gravity.CENTER));
        return target;
    }

    private void renderList(List<TodoTask> listed, int inListCount, boolean animateReorder) {
        rowTops.clear();
        if (animateReorder) {
            for (int i = 0; i < listContainer.getChildCount(); i++) {
                View child = listContainer.getChildAt(i);
                Object tag = child.getTag();
                if (tag != null) {
                    rowTops.put(tag.toString(), child.getTop());
                }
            }
        }
        listContainer.removeAllViews();

        if (listed.isEmpty()) {
            String message;
            if (!searchQuery.isEmpty()) {
                message = "No tasks match this search.";
            } else if (inListCount > 0) {
                message = "Only your #1 is here right now.";
            } else if (!"All".equals(activeCat)) {
                message = "Nothing in " + activeCat + " yet.";
            } else {
                message = "Nothing in the index yet.";
            }
            TextView empty = text(message, 13, 600, palette.sub);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(22), dp(20), dp(22));
            listContainer.addView(empty, matchWrap(0, 0, 0, 0));
            return;
        }
        for (int i = 0; i < listed.size(); i++) {
            listContainer.addView(buildRow(listed.get(i), i, i == listed.size() - 1), matchWrap(0, 0, 0, 0));
        }

        if (animateReorder && !rowTops.isEmpty()) {
            listContainer.post(() -> {
                for (int i = 0; i < listContainer.getChildCount(); i++) {
                    View child = listContainer.getChildAt(i);
                    Object tag = child.getTag();
                    if (tag == null) {
                        continue;
                    }
                    Integer prev = rowTops.get(tag.toString());
                    if (prev == null) {
                        continue;
                    }
                    int dy = prev - child.getTop();
                    if (Math.abs(dy) < 1) {
                        continue;
                    }
                    child.setTranslationY(dy);
                    child.animate().translationY(0f).setDuration(460).start();
                }
            });
        }
    }

    /** Collapsible "Later" section: tasks swiped away from the thumb wait here. */
    private void renderLater(List<TodoTask> later) {
        laterHeaderView.removeAllViews();
        laterContainer.removeAllViews();
        boolean any = !later.isEmpty();
        laterHeaderView.setVisibility(any ? View.VISIBLE : View.GONE);
        laterContainer.setVisibility(any && laterExpanded ? View.VISIBLE : View.GONE);
        if (!any) {
            return;
        }
        View clock = glyphView(Glyph.CLOCK, 14, palette.later);
        ((LinearLayout.LayoutParams) clock.getLayoutParams()).rightMargin = dp(6);
        laterHeaderView.addView(clock);
        TextView label = text("LATER", 11, 800, palette.later);
        label.setLetterSpacing(0.08f);
        laterHeaderView.addView(label, wrap(0, 0, 8, 0));
        laterHeaderView.addView(countBadge(String.valueOf(later.size()),
                PriorityPalette.withAlpha(palette.later, 0x26), palette.later), wrap(0, 0, 0, 0));
        laterHeaderView.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        laterHeaderView.addView(text(laterExpanded ? "Hide" : "Show", 12, 700, palette.sub), wrap(0, 0, 2, 0));
        laterHeaderView.addView(glyphView(laterExpanded ? Glyph.EXPAND_LESS : Glyph.EXPAND_MORE, 18, palette.sub));
        laterHeaderView.setContentDescription((laterExpanded ? "Hide " : "Show ")
                + later.size() + (later.size() == 1 ? " task in Later section" : " tasks in Later section"));
        if (laterExpanded) {
            for (int i = 0; i < later.size(); i++) {
                laterContainer.addView(buildRow(later.get(i), i, i == later.size() - 1), matchWrap(0, 0, 0, 0));
            }
        }
    }

    private FrameLayout buildRow(TodoTask task, int position, boolean last) {
        int tier = task.snoozed ? palette.later : rankColor(task, position);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setTag(task.id);

        TextView reveal = text("", 12, 800, 0xFFFFFFFF);
        reveal.setLetterSpacing(0.08f);
        reveal.setGravity(Gravity.CENTER_VERTICAL);
        reveal.setPadding(dp(22), 0, dp(22), 0);
        reveal.setVisibility(View.INVISIBLE);
        wrap.addView(reveal, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout fg = horizontal();
        fg.setPadding(dp(2), dp(7), dp(14), dp(7));
        fg.setMinimumHeight(dp(60));
        fg.setBackgroundColor(palette.surface);
        boolean rowExpanded = isTaskExpanded("row", task.id);
        LinearLayout.LayoutParams targetParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        targetParams.gravity = rowExpanded ? Gravity.TOP : Gravity.CENTER_VERTICAL;
        fg.addView(completionTarget(task, tier, 20), targetParams);

        LinearLayout copy = vertical();
        if (rowExpanded) {
            copy.setContentDescription("Expanded content for " + task.title);
        }
        LinearLayout titleLine = horizontal();
        titleLine.setGravity(rowExpanded ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        TextView name = text(task.title, 15, position == 0 && !task.snoozed ? 600 : 500,
                task.snoozed ? palette.sub : palette.ink);
        name.setSingleLine(!rowExpanded);
        name.setMaxLines(rowExpanded ? Integer.MAX_VALUE : 1);
        name.setEllipsize(rowExpanded ? null : TextUtils.TruncateAt.END);
        titleLine.addView(name, weight(1, 0, 0, 8, 0));
        if ("All".equals(activeCat) && task.category != null) {
            titleLine.addView(new TagView(task.category, tier), wrap(0, rowExpanded ? 3 : 0, 0, 0));
        }
        copy.addView(titleLine, matchWrap(0, 0, 0, 0));

        boolean hasNotes = !TextUtils.isEmpty(task.notes);
        if (task.reminderAt > 0 || (hasNotes && !rowExpanded)) {
            LinearLayout meta = horizontal();
            if (task.reminderAt > 0) {
                boolean overdue = task.reminderAt < System.currentTimeMillis();
                int whenColor = overdue ? PriorityPalette.IMMEDIATE : palette.sub;
                TextView when = text(reminderShort(task), 11, 600, whenColor);
                when.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        new Glyph(Glyph.CLOCK, dp(12), whenColor), null, null, null);
                when.setCompoundDrawablePadding(dp(4));
                when.setSingleLine(true);
                meta.addView(when, wrap(0, 0, 8, 0));
            }
            if (hasNotes && !rowExpanded) {
                TextView notes = text(task.notes, 12, 500, palette.sub);
                notes.setSingleLine(true);
                notes.setEllipsize(TextUtils.TruncateAt.END);
                meta.addView(notes, weight(1, 0, 0, 0, 0));
            }
            copy.addView(meta, matchWrap(0, 3, 0, 0));
        }
        if (hasNotes && rowExpanded) {
            TextView notes = text(task.notes, 13, 500, palette.sub);
            notes.setMaxLines(6);
            notes.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(notes, matchWrap(0, 6, 0, 0));
        }
        fg.addView(copy, weight(1, 0, 0, 0, 0));
        if (task.snoozed) {
            View restore = new View(this);
            restore.setBackground(new Glyph(Glyph.UNDO, dp(20), palette.later));
            restore.setContentDescription("Restore task: " + task.title);
            restore.setOnClickListener(v -> {
                task.snoozed = false;
                task.updatedAt = nextMutationTimestamp();
                persistMutation();
                renderAll(true);
            });
            fg.addView(restore, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }

        wrap.addView(fg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        if (!last) {
            View divider = new View(this);
            divider.setBackgroundColor(palette.line);
            FrameLayout.LayoutParams divParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
            divParams.gravity = Gravity.BOTTOM;
            divParams.leftMargin = dp(50);
            wrap.addView(divider, divParams);
        }

        attachRowSwipe(fg, reveal, task.id);
        return wrap;
    }

    private boolean isTaskExpanded(String surface, String id) {
        return (surface + ":" + id).equals(expandedTaskKey);
    }

    private void toggleTaskExpansion(String surface, String id) {
        String key = surface + ":" + id;
        expandedTaskKey = key.equals(expandedTaskKey) ? null : key;
        renderAll(false);
    }

    // ===================== gestures =====================

    private int completeDir() {
        return HAND_RIGHT.equals(hand) ? 1 : -1;
    }

    private void attachRowSwipe(View fg, TextView reveal, String id) {
        final float[] startX = new float[1];
        final boolean[] moved = new boolean[1];
        final boolean[] longPressed = new boolean[1];
        final Runnable[] longPress = new Runnable[1];
        fg.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    moved[0] = false;
                    longPressed[0] = false;
                    longPress[0] = () -> {
                        if (!moved[0]) {
                            longPressed[0] = true;
                            openEditSheet(id);
                        }
                    };
                    handler.postDelayed(longPress[0], 480);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - startX[0];
                    if (!moved[0]) {
                        if (Math.abs(dx) > dp(6)) {
                            moved[0] = true;
                            handler.removeCallbacks(longPress[0]);
                        } else {
                            return true;
                        }
                    }
                    v.setTranslationX(dx);
                    paintReveal(reveal, dx);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    handler.removeCallbacks(longPress[0]);
                    float dx = event.getRawX() - startX[0];
                    if (moved[0] && Math.abs(dx) >= dp(72)) {
                        int dir = dx > 0 ? 1 : -1;
                        if (dir == completeDir()) {
                            animateRowOut(v, dir, () -> completeTask(id));
                        } else {
                            animateRowOut(v, dir, () -> laterTask(id));
                        }
                    } else if (!moved[0] && !longPressed[0]
                            && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        v.setTranslationX(0f);
                        reveal.setVisibility(View.INVISIBLE);
                        toggleTaskExpansion("row", id);
                    } else {
                        v.animate().translationX(0f).setDuration(220).start();
                        reveal.setVisibility(View.INVISIBLE);
                    }
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    private void attachHeroSwipe(View fg, TextView reveal, String id) {
        final float[] startX = new float[1];
        final boolean[] moved = new boolean[1];
        final boolean[] longPressed = new boolean[1];
        final Runnable[] longPress = new Runnable[1];
        fg.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    moved[0] = false;
                    longPressed[0] = false;
                    longPress[0] = () -> {
                        if (!moved[0]) {
                            longPressed[0] = true;
                            openEditSheet(id);
                        }
                    };
                    handler.postDelayed(longPress[0], 480);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - startX[0];
                    if (!moved[0]) {
                        if (Math.abs(dx) > dp(6)) {
                            moved[0] = true;
                            handler.removeCallbacks(longPress[0]);
                        } else {
                            return true;
                        }
                    }
                    v.setTranslationX(dx);
                    paintReveal(reveal, dx);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    handler.removeCallbacks(longPress[0]);
                    float dx = event.getRawX() - startX[0];
                    if (moved[0] && Math.abs(dx) >= dp(72)) {
                        int dir = dx > 0 ? 1 : -1;
                        if (dir == completeDir()) {
                            completeTask(id);
                        } else {
                            laterTask(id);
                        }
                    } else if (!moved[0] && !longPressed[0]
                            && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        v.setTranslationX(0f);
                        reveal.setVisibility(View.INVISIBLE);
                        toggleTaskExpansion("hero", id);
                    } else {
                        v.animate().translationX(0f).setDuration(220).start();
                        reveal.setVisibility(View.INVISIBLE);
                    }
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    private void paintReveal(TextView reveal, float dx) {
        if (Math.abs(dx) < dp(12)) {
            reveal.setVisibility(View.INVISIBLE);
            return;
        }
        int dir = dx > 0 ? 1 : -1;
        boolean isComplete = dir == completeDir();
        reveal.setVisibility(View.VISIBLE);
        reveal.setBackgroundColor(isComplete ? PriorityPalette.GREEN_REVEAL : palette.accent);
        reveal.setGravity((dx > 0 ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        reveal.setTextColor(isComplete ? Color.WHITE : palette.accentInk);
        reveal.setText(isComplete ? "DONE" : "LATER");
    }

    private void animateRowOut(View fg, int dir, Runnable after) {
        fg.animate().translationX(dir * fg.getWidth()).setDuration(200)
                .withEndAction(after).start();
    }

    // ===================== task actions =====================

    private void completeTask(String id) {
        TodoTask task = findTask(id);
        if (task == null || task.completed) {
            return;
        }
        long completedAt = System.currentTimeMillis();
        long mutationTimestamp = nextMutationTimestamp();
        TodoTask nextOccurrence = task.completeAndCreateNextOccurrence(completedAt, mutationTimestamp);
        ReminderScheduler.cancel(this, task);
        if (nextOccurrence != null) {
            tasks.add(nextOccurrence);
            ReminderScheduler.schedule(this, nextOccurrence);
        }
        persistMutation();
        renderAll(true);
        String generatedTaskId = nextOccurrence == null ? null : nextOccurrence.id;
        showToast("Completed", () -> undoCompletion(id, generatedTaskId));
    }

    private void laterTask(String id) {
        TodoTask task = findTask(id);
        if (task == null || task.snoozed) {
            // Re-render so a row swiped off-screen inside the Later section snaps back.
            renderAll(false);
            return;
        }
        task.snoozed = true;
        task.updatedAt = nextMutationTimestamp();
        persistMutation();
        renderAll(true);
        showToast("Moved to Later", () -> undoLater(id));
    }

    private void undoCompletion(String taskId, String generatedTaskId) {
        TodoTask task = findTask(taskId);
        if (task == null) {
            return;
        }
        if (generatedTaskId != null) {
            TodoTask generated = findTask(generatedTaskId);
            if (generated != null) {
                ReminderScheduler.cancel(this, generated);
                tasks.remove(generated);
            }
            recordTaskTombstone(generatedTaskId, nextMutationTimestamp());
        }
        task.reopenAfterCompletion(nextMutationTimestamp());
        ReminderScheduler.schedule(this, task);
        persistMutation();
        renderAll(true);
    }

    private void undoLater(String taskId) {
        TodoTask task = findTask(taskId);
        if (task == null) {
            return;
        }
        task.snoozed = false;
        task.updatedAt = nextMutationTimestamp();
        persistMutation();
        renderAll(true);
    }

    // ===================== toast / cheer =====================

    private void showToast(String msg, Runnable undoAction) {
        dismissToast();
        toastUndoAction = undoAction;

        LinearLayout bar = horizontal();
        bar.setPadding(dp(16), dp(11), dp(12), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1D2030);
        bg.setCornerRadius(dp(14));
        bar.setBackground(bg);
        bar.setElevation(dp(8));
        TextView label = text(msg, 13, 600, 0xFFFFFFFF);
        bar.addView(label, wrap(0, 0, 16, 0));
        TextView undo = text("Undo", 13, 800, 0xFFA3A9FF);
        undo.setPadding(dp(4), dp(2), dp(4), dp(2));
        undo.setOnClickListener(v -> {
            Runnable action = toastUndoAction;
            if (action != null) {
                action.run();
            }
            dismissToast();
        });
        bar.addView(undo, wrap(0, 0, 0, 0));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(HAND_CENTER.equals(hand) ? 148 : 82);
        toastView = bar;
        root.addView(toastView, lp);

        toastDismiss = this::dismissToast;
        handler.postDelayed(toastDismiss, 2800);
    }

    private void dismissToast() {
        if (toastDismiss != null) {
            handler.removeCallbacks(toastDismiss);
            toastDismiss = null;
        }
        if (toastView != null) {
            root.removeView(toastView);
            toastView = null;
        }
        toastUndoAction = null;
    }

    private void showCheer(String msg) {
        dismissCheer();
        TextView chip = text(msg, 13, 700, palette.surface);
        chip.setPadding(dp(15), dp(8), dp(15), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.ink);
        bg.setCornerRadius(dp(999));
        chip.setBackground(bg);
        chip.setElevation(dp(8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(HAND_CENTER.equals(hand) ? 152 : 86);
        cheerView = chip;
        root.addView(cheerView, lp);
        cheerDismiss = this::dismissCheer;
        handler.postDelayed(cheerDismiss, 1050);
    }

    private void dismissCheer() {
        if (cheerDismiss != null) {
            handler.removeCallbacks(cheerDismiss);
            cheerDismiss = null;
        }
        if (cheerView != null) {
            root.removeView(cheerView);
            cheerView = null;
        }
    }

    // ===================== lists / categories =====================

    private void rebuildActiveCategories() {
        categories.clear();
        for (SyncState.CategoryState category : categoryStates) {
            if (category.isActive()) {
                categories.add(category.name);
            }
        }
    }

    private void upsertCategoryState(String name, long now) {
        for (SyncState.CategoryState category : categoryStates) {
            if (category.name.equalsIgnoreCase(name)) {
                category.name = name;
                category.updatedAt = now;
                category.deletedAt = 0;
                return;
            }
        }
        categoryStates.add(new SyncState.CategoryState(name, now, 0));
    }

    private void activateCategory(String name, long now) {
        categories.add(name);
        upsertCategoryState(name, now);
    }

    private void deleteCategoryState(String name, long now) {
        for (SyncState.CategoryState category : categoryStates) {
            if (category.name.equalsIgnoreCase(name)) {
                category.deletedAt = Math.max(category.deletedAt, now);
                return;
            }
        }
        categoryStates.add(new SyncState.CategoryState(name, 0, now));
    }

    private void deactivateCategory(String name, long now) {
        categories.remove(name);
        deleteCategoryState(name, now);
    }

    private void recordTaskTombstone(String id, long deletedAt) {
        for (SyncState.TaskTombstone tombstone : taskTombstones) {
            if (tombstone.id.equals(id)) {
                tombstone.deletedAt = Math.max(tombstone.deletedAt, deletedAt);
                return;
            }
        }
        taskTombstones.add(new SyncState.TaskTombstone(id, deletedAt));
    }

    private boolean inActiveCat(TodoTask task) {
        return "All".equals(activeCat) || activeCat.equals(task.category);
    }

    private List<String> tabList() {
        List<String> out = new ArrayList<>();
        out.add("All");
        out.addAll(categories);
        return out;
    }

    /** Active (not completed, not Later) tasks in a list. */
    private int catCount(String name) {
        int count = 0;
        for (TodoTask task : tasks) {
            if (!task.completed && !task.snoozed
                    && ("All".equals(name) || name.equals(task.category))) {
                count++;
            }
        }
        return count;
    }

    private void renderCategoryStrip() {
        if (catStrip == null) {
            return;
        }
        catStrip.removeAllViews();
        catStrip.addView(dockChevron(Glyph.CHEVRON_LEFT, "Previous list", -1),
                new LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.MATCH_PARENT));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(14));
        LinearLayout row = horizontal();
        View selected = null;
        for (String name : tabList()) {
            View tab = buildCatTab(name);
            if (name.equals(activeCat)) {
                selected = tab;
            }
            row.addView(tab, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        }
        scroll.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT));
        catStrip.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        final View selectedTab = selected;
        if (selectedTab != null) {
            scroll.post(() -> scroll.scrollTo(Math.max(0,
                    selectedTab.getLeft() - (scroll.getWidth() - selectedTab.getWidth()) / 2), 0));
        }
        catStrip.addView(dockChevron(Glyph.CHEVRON_RIGHT, "Next list", 1),
                new LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private View dockChevron(String kind, String description, int direction) {
        View chevron = new View(this);
        chevron.setBackground(new Glyph(kind, dp(18), palette.sub, 2.2f));
        chevron.setContentDescription(description);
        chevron.setOnClickListener(v -> stepNavigationList(direction));
        return chevron;
    }

    private View buildCatTab(String name) {
        boolean active = name.equals(activeCat);
        LinearLayout tab = horizontal();
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(active ? 12 : 9), 0, dp(active ? 11 : 9), 0);
        tab.setContentDescription("List: " + name);
        tab.setSelected(active);
        if (active) {
            setBackgroundKeepPadding(tab, new InsetDrawable(rounded(palette.accent, 999), 0, dp(9), 0, dp(9)));
            View marker = dot(6, isNightEffective() ? 0xFF008135 : 0xFF5CDF7B, false);
            ((LinearLayout.LayoutParams) marker.getLayoutParams()).rightMargin = dp(6);
            tab.addView(marker);
        }
        TextView label = text(name, 13, active ? 700 : 600, active ? palette.accentInk : palette.sub);
        label.setIncludeFontPadding(false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setMaxWidth(dp(140));
        tab.addView(label);

        int n = catCount(name);
        if (n > 0) {
            TextView badge = countBadge(String.valueOf(n),
                    active ? PriorityPalette.withAlpha(palette.accentInk, 0x33) : palette.chip,
                    active ? palette.accentInk : palette.sub);
            tab.addView(badge, wrap(6, 0, 0, 0));
        }

        tab.setOnClickListener(v -> selectNavigationList(name));
        if (!"All".equals(name)) {
            tab.setOnLongClickListener(v -> {
                confirmDeleteCat(name);
                return true;
            });
        }
        return tab;
    }

    private View buildAddCatButton() {
        TextView btn = new TextView(this);
        btn.setText("+");
        btn.setTextSize(17);
        applyFont(btn, 700);
        btn.setTextColor(palette.sub);
        btn.setGravity(Gravity.CENTER);
        btn.setIncludeFontPadding(false);
        btn.setContentDescription("Add list");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(dp(2), PriorityPalette.withAlpha(palette.sub, 0x73), dp(4), dp(3));
        btn.setBackground(bg);
        btn.setOnClickListener(v -> openAddCat());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.gravity = Gravity.CENTER_VERTICAL;
        btn.setLayoutParams(lp);
        return btn;
    }

    private View buildAddCatRow() {
        LinearLayout row = horizontal();
        row.setPadding(dp(16), dp(2), dp(16), dp(4));

        LinearLayout field = horizontal();
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(palette.bg);
        fieldBg.setCornerRadius(dp(14));
        fieldBg.setStroke(dp(2), palette.accent);
        field.setBackground(fieldBg);

        final EditText input = new EditText(themedContext());
        input.setHint("New list name");
        input.setText(addCatDraft == null ? "" : addCatDraft);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        input.setTextSize(14);
        applyFont(input, 700);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.sub);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                addCatDraft = s.toString();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            commitAddCat();
            return true;
        });
        addCatInput = input;
        field.addView(input, weight(1, 0, 0, 0, 0));
        row.addView(field, weight(1, 0, 0, 8, 0));

        TextView add = text("Add", 13, 800, palette.accentInk);
        add.setPadding(dp(13), dp(9), dp(13), dp(9));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(palette.accent);
        addBg.setCornerRadius(dp(12));
        add.setBackground(addBg);
        add.setOnClickListener(v -> commitAddCat());
        row.addView(add, wrap(0, 0, 8, 0));

        TextView cancel = text("✕", 15, 700, palette.sub);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(10), dp(9), dp(10), dp(9));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.TRANSPARENT);
        cancelBg.setCornerRadius(dp(12));
        cancelBg.setStroke(dp(2), palette.line);
        cancel.setBackground(cancelBg);
        cancel.setOnClickListener(v -> cancelAddCat());
        row.addView(cancel, wrap(0, 0, 0, 0));

        input.requestFocus();
        input.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        return row;
    }

    private void openAddCat() {
        addCatDraft = "";
        renderCategoryStrip();
    }

    private void cancelAddCat() {
        hideCatKeyboard();
        addCatDraft = null;
        renderCategoryStrip();
    }

    private void commitAddCat() {
        String name = addCatDraft == null ? "" : addCatDraft.trim();
        hideCatKeyboard();
        if (name.isEmpty()) {
            addCatDraft = null;
            renderCategoryStrip();
            return;
        }
        if ("all".equalsIgnoreCase(name)) {
            addCatDraft = null;
            switchCat("All");
            return;
        }
        for (String existing : categories) {
            if (existing.equalsIgnoreCase(name)) {
                addCatDraft = null;
                switchCat(existing);
                return;
            }
        }
        activateCategory(name, nextMutationTimestamp());
        persistMutation();
        addCatDraft = null;
        switchCat(name);
        showCheer("List added");
    }

    private void switchCat(String name) {
        if (name.equals(activeCat) && addCatDraft == null) {
            return;
        }
        List<String> tabs = tabList();
        int fromIndex = tabs.indexOf(activeCat);
        int toIndex = tabs.indexOf(name);
        int dir = (fromIndex >= 0 && toIndex >= 0 && toIndex < fromIndex) ? -1 : 1;
        addCatDraft = null;
        activeCat = name;
        renderAll(false);
        float start = dp(26) * dir;
        for (View zone : new View[]{heroContainer, listContainer}) {
            if (zone == null) {
                continue;
            }
            zone.setTranslationX(start);
            zone.setAlpha(0.35f);
            zone.animate().translationX(0f).alpha(1f)
                    .setDuration(340)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void confirmDeleteCat(String name) {
        new AlertDialog.Builder(themedContext())
                .setTitle("Delete \"" + name + "\"?")
                .setMessage("The list is removed. Its tasks aren't deleted — they move back to All.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteCat(name))
                .show();
    }

    private void deleteCat(String name) {
        long now = nextMutationTimestamp();
        deactivateCategory(name, now);
        for (TodoTask task : tasks) {
            if (name.equals(task.category)) {
                task.category = null;
                task.updatedAt = now;
            }
        }
        if (name.equals(activeCat)) {
            activeCat = "All";
        }
        if (sheetOpen && name.equals(draftCategory)) {
            draftCategory = null;
            styleListPill();
        }
        persistMutation();
        renderAll(false);
    }

    // ===================== csv backup =====================

    private void startCsvExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "clearflow-tasks.csv");
        startActivityForResult(intent, REQUEST_EXPORT_CSV);
    }

    private void startCsvImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_IMPORT_CSV);
    }

    private void exportCsvTo(Uri uri) {
        try (OutputStream stream = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            writer.write(CsvCodec.exportTasks(tasks));
            showCheer("CSV exported");
        } catch (IOException | NullPointerException ex) {
            showDataError("Export failed", "Clearflow couldn't write the CSV file.");
        }
    }

    private void importCsvFrom(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder csv = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                csv.append(line).append('\n');
            }
            int changed = mergeImportedTasks(CsvCodec.importTasks(csv.toString()));
            persistMutation();
            scheduleTaskReminders();
            renderAll(false);
            showCheer(changed + " tasks imported");
        } catch (IOException | NullPointerException ex) {
            showDataError("Import failed", "Clearflow couldn't read that CSV file.");
        }
    }

    private int mergeImportedTasks(List<TodoTask> incoming) {
        Map<String, TodoTask> byId = new HashMap<>();
        for (TodoTask task : tasks) {
            byId.put(task.id, task);
        }
        int changed = 0;
        for (TodoTask imported : incoming) {
            TodoTask existing = byId.get(imported.id);
            TodoTask target;
            if (existing == null) {
                target = imported;
                tasks.add(target);
                byId.put(target.id, target);
            } else {
                target = existing;
                copyTask(imported, target);
            }
            target.updatedAt = imported.updatedAt;
            long now = nextMutationTimestamp();
            if (imported.category != null && !hasCategory(imported.category)) {
                activateCategory(imported.category, now);
            }
            target.updatedAt = now;
            changed++;
        }
        return changed;
    }

    private boolean hasCategory(String name) {
        for (String category : categories) {
            if (category.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void copyTask(TodoTask from, TodoTask to) {
        ReminderScheduler.cancel(this, to);
        to.title = from.title;
        to.notes = from.notes;
        to.impact = from.impact;
        to.effort = from.effort;
        to.dependency = from.dependency;
        to.category = from.category;
        to.urgent = from.urgent;
        to.quickTask = from.quickTask;
        to.snoozed = from.snoozed;
        to.recurringMit = from.recurringMit;
        to.completed = from.completed;
        to.createdAt = from.createdAt;
        to.reminderAt = from.reminderAt;
        to.reminderRepeatUnit = from.reminderRepeatUnit;
        to.reminderRepeatEvery = from.reminderRepeatEvery;
    }

    private void showDataError(String title, String message) {
        new AlertDialog.Builder(themedContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void hideCatKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && addCatInput != null) {
            imm.hideSoftInputFromWindow(addCatInput.getWindowToken(), 0);
        }
    }

    // ===================== sheet =====================

    private void openAddSheet() {
        sheetMode = "add";
        sheetEditId = null;
        draftName = "";
        draftNotes = "";
        draftImpact = TodoTask.LOW;
        draftEffort = TodoTask.LOW;
        draftUrgent = false;
        draftQuick = true;
        draftReminderAt = 0;
        draftRepeatUnit = TodoTask.REPEAT_NONE;
        draftRepeatEvery = 1;
        draftRepeatEveryText = "1";
        draftRepeatEveryValid = true;
        draftCategory = "All".equals(activeCat) ? null : activeCat;
        reminderSettingsOpen = false;
        detailsExpanded = false;
        notesExpanded = false;
        openSheetWithFabTransition();
    }

    private void openEditSheet(String id) {
        TodoTask task = findTask(id);
        if (task == null) {
            return;
        }
        sheetMode = "edit";
        sheetEditId = id;
        draftName = task.title;
        draftNotes = task.notes;
        draftImpact = task.impact;
        draftEffort = task.effort;
        draftUrgent = task.urgent;
        draftQuick = task.quickTask;
        draftReminderAt = task.reminderAt;
        draftRepeatUnit = task.reminderRepeatUnit;
        draftRepeatEvery = Math.max(1, task.reminderRepeatEvery);
        draftRepeatEveryText = String.valueOf(task.reminderRepeatEvery);
        draftRepeatEveryValid = TodoTask.parseRepeatInterval(draftRepeatEveryText) > 0;
        draftCategory = task.category;
        reminderSettingsOpen = false;
        detailsExpanded = false;
        notesExpanded = shouldExpandNotes(sheetMode, draftNotes);
        presentSheet();
    }

    private void openSheetWithFabTransition() {
        if (fab == null) {
            presentSheet();
            return;
        }
        fab.animate().cancel();
        if (fabTarget != null) {
            fabTarget.setVisibility(View.INVISIBLE);
        }
        if (reachArc != null) {
            reachArc.animate().alpha(0f).setDuration(140).start();
        }
        fab.animate()
                .scaleX(0.72f)
                .scaleY(0.72f)
                .alpha(0f)
                .setDuration(130)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    fab.setVisibility(View.INVISIBLE);
                    presentSheet();
                })
                .start();
    }

    private void presentSheet() {
        if (sheetOverlay != null) {
            removeSheetImmediate();
        }
        sheetOpen = true;
        registerSheetBack();
        hideActZoneInstant();

        sheetOverlay = new FrameLayout(this);
        sheetOverlay.setElevation(dp(24));
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

        sheetScrim = new View(this);
        sheetScrim.setBackgroundColor(0x66080A18);
        sheetScrim.setOnClickListener(v -> closeSheet());
        sheetOverlay.addView(sheetScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        sheetPanel = new KeyboardAwareSheetPanel(this);
        sheetPanel.setPadding(dp(18), dp(8), dp(18), dp(18));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(palette.surface);
        panelBg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        sheetPanel.setBackground(panelBg);
        sheetPanel.setElevation(dp(26));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM;

        sheetScroll = new ScrollView(this);
        sheetScroll.setFillViewport(false);
        sheetScroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = vertical();
        buildSheetContent(content);
        sheetScroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        sheetPanel.addView(sheetScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        sheetPanel.addView(buildSheetActions(), matchWrap(0, 12, 0, 0));
        updateSheetDynamic();

        sheetOverlay.addView(sheetPanel, panelParams);
        root.addView(sheetOverlay, overlayParams);
        attachSheetKeyboardHandling();

        sheetScrim.setAlpha(0f);
        sheetScrim.animate().alpha(1f).setDuration(180).start();
        sheetPanel.setAlpha(0f);
        sheetPanel.setScaleX(0.96f);
        sheetPanel.setScaleY(0.96f);
        sheetPanel.post(() -> {
            int h = sheetPanel.getHeight();
            sheetPanel.setPivotX(HAND_RIGHT.equals(hand) ? sheetPanel.getWidth() - dp(48) : dp(48));
            sheetPanel.setPivotY(sheetPanel.getHeight());
            sheetPanel.setTranslationY(h + dp(24));
            sheetPanel.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(320)
                    .setInterpolator(new OvershootInterpolator(0.9f))
                    .start();
        });

        sheetInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(sheetInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void buildSheetContent(LinearLayout content) {
        View grip = new View(this);
        grip.setBackground(rounded(palette.line, 2));
        LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        gripParams.gravity = Gravity.CENTER_HORIZONTAL;
        gripParams.topMargin = dp(2);
        gripParams.bottomMargin = dp(12);
        content.addView(grip, gripParams);

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("edit".equals(sheetMode) ? "Edit task" : "New task", 18, 800, palette.ink);
        title.setLetterSpacing(-0.015f);
        titleRow.addView(title, weight(1, 0, 0, 12, 0));
        listPill = text("", 13, 600, palette.ink);
        listPill.setGravity(Gravity.CENTER_VERTICAL);
        listPill.setSingleLine(true);
        listPill.setMaxWidth(dp(150));
        listPill.setEllipsize(TextUtils.TruncateAt.END);
        listPill.setMinHeight(dp(44));
        listPill.setPadding(dp(12), 0, dp(8), 0);
        listPill.setCompoundDrawablePadding(dp(2));
        listPill.setOnClickListener(v -> showListPicker());
        titleRow.addView(listPill, wrap(0, 0, 0, 0));
        content.addView(titleRow, matchWrap(0, 0, 0, 10));

        sheetInput = new EditText(themedContext());
        sheetInput.setId(View.generateViewId());
        sheetInput.setHint("What needs doing?");
        sheetInput.setText(draftName);
        sheetInput.setSelection(sheetInput.length());
        sheetInput.setSingleLine(true);
        sheetInput.setImeOptions(notesExpanded ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        sheetInput.setTextSize(16);
        applyFont(sheetInput, 600);
        sheetInput.setTextColor(palette.ink);
        sheetInput.setHintTextColor(palette.sub);
        GradientDrawable inputBg = rounded(palette.bg, 14);
        inputBg.setStroke(Math.max(1, dp(1)), palette.line);
        sheetInput.setBackground(inputBg);
        sheetInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                draftName = s.toString();
                if (clearTitleButton != null) {
                    clearTitleButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                updateCommitButtonState();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        sheetInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && notesExpanded && notesInput != null) {
                notesInput.requestFocus();
                keepFocusedSheetFieldVisible();
                return true;
            }
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (draftName.trim().length() > 0) {
                    commitSheet();
                }
                return true;
            }
            return false;
        });
        FrameLayout titleField = new FrameLayout(this);
        // Leave room for the inline reminder clock and clear button.
        sheetInput.setPadding(dp(15), dp(15), dp(86), dp(15));
        titleField.addView(sheetInput, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout inlineActions = horizontal();
        remindChip = new ReminderClockButton();
        remindChip.setOnClickListener(v -> {
            reminderSettingsOpen = !reminderSettingsOpen;
            updateSheetDynamic();
        });
        inlineActions.addView(remindChip);
        clearTitleButton = new View(this);
        GradientDrawable clearDisc = new GradientDrawable();
        clearDisc.setShape(GradientDrawable.OVAL);
        clearDisc.setColor(palette.chip);
        LayerDrawable clearIcon = new LayerDrawable(new Drawable[]{
                clearDisc, new Glyph(Glyph.CLOSE, dp(14), palette.sub, 2.6f)});
        clearIcon.setLayerInset(1, dp(4), dp(4), dp(4), dp(4));
        clearTitleButton.setBackground(new InsetDrawable(clearIcon, dp(7), dp(11), dp(7), dp(11)));
        clearTitleButton.setContentDescription("Clear task title");
        clearTitleButton.setVisibility(draftName.isEmpty() ? View.GONE : View.VISIBLE);
        clearTitleButton.setOnClickListener(v -> { sheetInput.setText(""); sheetInput.requestFocus(); });
        inlineActions.addView(clearTitleButton, new LinearLayout.LayoutParams(dp(36), dp(44)));
        FrameLayout.LayoutParams inlineParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        inlineParams.rightMargin = dp(6);
        titleField.addView(inlineActions, inlineParams);
        content.addView(titleField, matchWrap(0, 0, 0, 0));
        listPill.setAccessibilityTraversalAfter(sheetInput.getId());

        // Chosen reminder, shown as a quiet blue pill with its own clear action.
        reminderInfoRow = horizontal();
        reminderInfoRow.setPadding(dp(12), 0, dp(2), 0);
        reminderInfoRow.setMinimumHeight(dp(44));
        setBackgroundKeepPadding(reminderInfoRow, new InsetDrawable(
                rounded(PriorityPalette.withAlpha(palette.info, 0x17), 12), 0, dp(4), 0, dp(4)));
        reminderInfoRow.setOnClickListener(v -> {
            reminderSettingsOpen = !reminderSettingsOpen;
            updateSheetDynamic();
        });
        View infoClock = glyphView(Glyph.CLOCK, 15, palette.info);
        ((LinearLayout.LayoutParams) infoClock.getLayoutParams()).rightMargin = dp(8);
        reminderInfoRow.addView(infoClock);
        reminderSummary = text("", 13, 700, palette.info);
        reminderSummary.setSingleLine(true);
        reminderSummary.setEllipsize(TextUtils.TruncateAt.END);
        reminderInfoRow.addView(reminderSummary, weight(1, 0, 0, 4, 0));
        remindClear = new View(this);
        remindClear.setBackground(new Glyph(Glyph.CLOSE, dp(16), palette.info, 2.4f));
        remindClear.setContentDescription("Clear reminder");
        remindClear.setOnClickListener(v -> {
            draftReminderAt = 0;
            draftRepeatUnit = TodoTask.REPEAT_NONE;
            draftRepeatEvery = 1;
            draftRepeatEveryText = "1";
            draftRepeatEveryValid = true;
            updateSheetDynamic();
        });
        reminderInfoRow.addView(remindClear, new LinearLayout.LayoutParams(dp(40), dp(40)));
        content.addView(reminderInfoRow, matchWrap(0, 4, 0, 0));

        addDetailsRow = text("Add details", 13, 600, palette.sub);
        addDetailsRow.setCompoundDrawablesRelativeWithIntrinsicBounds(
                new Glyph(Glyph.PLUS, dp(16), palette.sub, 2.4f), null, null, null);
        addDetailsRow.setCompoundDrawablePadding(dp(8));
        addDetailsRow.setGravity(Gravity.CENTER_VERTICAL);
        addDetailsRow.setMinHeight(dp(44));
        addDetailsRow.setPadding(dp(6), 0, dp(12), 0);
        addDetailsRow.setContentDescription("Add task details");
        addDetailsRow.setOnClickListener(v -> expandNotesEditor());
        addDetailsRow.setVisibility(notesExpanded ? View.GONE : View.VISIBLE);
        content.addView(addDetailsRow, wrap(0, 2, 0, 0));

        notesInput = new EditText(themedContext());
        notesInput.setHint("Details, links, steps…");
        notesInput.setText(draftNotes);
        notesInput.setSingleLine(false);
        notesInput.setMinLines(2);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notesInput.setImeOptions(EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        notesInput.setTextSize(14);
        notesInput.setTextColor(palette.ink);
        notesInput.setHintTextColor(palette.sub);
        notesInput.setPadding(dp(15), dp(12), dp(15), dp(12));
        notesInput.setBackground(rounded(palette.bg, 14));
        notesInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                draftNotes = s.toString();
                keepNotesCaretVisible();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        notesInput.setVisibility(notesExpanded ? View.VISIBLE : View.GONE);
        content.addView(notesInput, matchWrap(0, 8, 0, 0));

        reminderSettingsPanel = vertical();
        reminderSettingsPanel.setPadding(dp(12), dp(6), dp(6), dp(12));
        GradientDrawable panelBg = rounded(palette.surface, 16);
        panelBg.setStroke(Math.max(1, dp(1)), palette.line);
        reminderSettingsPanel.setBackground(panelBg);
        reminderSettingsPanel.setElevation(dp(3));
        content.addView(reminderSettingsPanel, matchWrap(1, 8, 1, 2));

        // Pre-computed assumptions with one Adjust affordance (Impact, Effort, flags).
        LinearLayout toggleRow = horizontal();
        toggleRow.setBackground(rounded(palette.soft, 12));
        toggleRow.setPadding(dp(12), dp(4), dp(4), dp(4));
        toggleRow.setMinimumHeight(dp(48));
        toggleRow.setOnClickListener(v -> toggleDetailsAnimated());
        detailsSummary = text("", 12, 600, palette.sub);
        detailsSummary.setMaxLines(2);
        detailsSummary.setEllipsize(TextUtils.TruncateAt.END);
        toggleRow.addView(detailsSummary, weight(1, 0, 0, 8, 0));
        detailsToggle = text("", 13, 800, palette.info);
        detailsToggle.setGravity(Gravity.CENTER);
        detailsToggle.setMinHeight(dp(40));
        detailsToggle.setPadding(dp(12), 0, dp(12), 0);
        toggleRow.addView(detailsToggle, wrap(0, 0, 0, 0));
        content.addView(toggleRow, matchWrap(0, 10, 0, 0));

        chipsContainer = vertical();
        chipsContainer.setPadding(dp(10), dp(10), dp(10), dp(10));
        chipsContainer.setBackground(rounded(palette.bg, 14));
        content.addView(chipsContainer, matchWrap(0, 8, 0, 0));

        if ("edit".equals(sheetMode)) {
            TextView del = text("Delete task", 13, 700, PriorityPalette.IMMEDIATE);
            del.setGravity(Gravity.CENTER);
            del.setMinHeight(dp(44));
            del.setPadding(dp(10), 0, dp(10), 0);
            del.setOnClickListener(v -> confirmDelete(sheetEditId));
            content.addView(del, matchWrap(0, 6, 0, 0));
        }
    }

    private LinearLayout buildSheetActions() {
        LinearLayout actions = horizontal();
        TextView cancel = text("Cancel", 15, 700, palette.sub);
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(dp(50));
        cancel.setPadding(dp(18), 0, dp(18), 0);
        cancel.setOnClickListener(v -> closeSheet());
        actions.addView(cancel, wrap(0, 0, 8, 0));

        commitButton = text("", 15, 800, palette.accentInk);
        commitButton.setGravity(Gravity.CENTER);
        commitButton.setMinHeight(dp(50));
        commitButton.setPadding(dp(15), 0, dp(15), 0);
        commitButton.setBackground(rounded(palette.accent, 14));
        commitButton.setOnClickListener(v -> commitSheet());
        actions.addView(commitButton, weight(1, 0, 0, 0, 0));
        return actions;
    }

    private void expandNotesEditor() {
        if (notesExpanded || notesInput == null) {
            return;
        }
        notesExpanded = true;
        if (addDetailsRow != null) {
            addDetailsRow.setVisibility(View.GONE);
        }
        notesInput.setVisibility(View.VISIBLE);
        sheetInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.restartInput(sheetInput);
        }
        notesInput.requestFocus();
        notesInput.setSelection(notesInput.length());
        notesInput.post(() -> {
            if (imm != null) {
                imm.showSoftInput(notesInput, InputMethodManager.SHOW_IMPLICIT);
            }
            keepFocusedSheetFieldVisible();
        });
    }

    private void showListPicker() {
        if (listPill == null) {
            return;
        }
        LinearLayout options = vertical();
        Dialog dialog = sheetDialog("Move to list", options);
        List<String> choices = new ArrayList<>();
        choices.add(null);
        choices.addAll(categories);
        for (String category : choices) {
            boolean selected = category == null ? draftCategory == null : category.equals(draftCategory);
            String label = listPillLabel(category);
            View lead = category == null
                    ? glyphView(Glyph.CLOSE, 18, palette.sub)
                    : dot(9, selected ? palette.info : palette.sub, !selected);
            options.addView(optionRow(label, category == null ? 0 : catCount(category), selected, lead, () -> {
                dialog.dismiss();
                draftCategory = category;
                styleListPill();
                listPill.announceForAccessibility("Moved to " + label);
            }), matchWrap(0, 0, 0, 2));
        }
        options.addView(optionRow("New list", 0, false, glyphView(Glyph.PLUS, 20, palette.info), () -> {
            dialog.dismiss();
            showCreateListForTask();
        }), matchWrap(0, 0, 0, 0));
        showSheetDialog(dialog);
    }

    private void showCreateListForTask() {
        EditText input = new EditText(themedContext());
        input.setHint("New list name");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.sub);
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new AlertDialog.Builder(themedContext())
                .setTitle("New list")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (ignored, which) -> createAndAssignList(input.getText().toString()))
                .create();
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createAndAssignList(input.getText().toString());
                dialog.dismiss();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            input.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        });
        dialog.show();
    }

    private void createAndAssignList(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || "all".equalsIgnoreCase(name)) {
            return;
        }
        for (String existing : categories) {
            if (existing.equalsIgnoreCase(name)) {
                draftCategory = existing;
                styleListPill();
                return;
            }
        }
        activateCategory(name, nextMutationTimestamp());
        persistMutation();
        draftCategory = name;
        renderCategoryStrip();
        styleListPill();
        listPill.announceForAccessibility("Created and moved to " + name);
    }

    private void styleListPill() {
        if (listPill == null) {
            return;
        }
        String label = listPillLabel(draftCategory);
        boolean assigned = draftCategory != null && !draftCategory.trim().isEmpty();
        listPill.setText(label);
        listPill.setTextColor(assigned ? palette.ink : palette.sub);
        listPill.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                new Glyph(Glyph.EXPAND_MORE, dp(18), palette.sub, 2.2f), null);
        listPill.setContentDescription("List: " + label + ". Double tap to change.");
        GradientDrawable bg = rounded(assigned ? palette.chip : palette.soft, 999);
        setBackgroundKeepPadding(listPill, new InsetDrawable(bg, 0, dp(6), 0, dp(6)));
    }

    private void updateSheetDynamic() {
        List<String> parts = new ArrayList<>();
        parts.add(impactLabel(draftImpact) + " impact");
        parts.add(impactLabel(draftEffort) + " effort");
        if (draftQuick) {
            parts.add("Quick win");
        }
        if (draftUrgent) {
            parts.add("Urgent");
        }
        detailsSummary.setText("Assumed: " + TextUtils.join("  ·  ", parts));
        detailsToggle.setText(detailsExpanded ? "Done" : "Adjust");
        styleAdjustButton();
        styleListPill();

        boolean hasReminder = draftReminderAt > 0;
        remindChip.setContentDescription(hasReminder ? "Reminder settings" : "Set reminder");
        String repeatText = !TodoTask.REPEAT_NONE.equals(draftRepeatUnit) && draftRepeatEveryValid
                ? "  ·  every " + (draftRepeatEvery == 1 ? "" : draftRepeatEvery + " ")
                + repeatOption(draftRepeatUnit).label.toLowerCase(java.util.Locale.ROOT)
                : "";
        reminderSummary.setText(hasReminder ? reminderShortFromMillis(draftReminderAt) + repeatText : "");
        styleReminderChip(hasReminder);
        if (reminderInfoRow != null) {
            reminderInfoRow.setVisibility(hasReminder ? View.VISIBLE : View.GONE);
        }

        renderReminderSettings();

        if (!detailsAnimating) {
            chipsContainer.removeAllViews();
            chipsContainer.setVisibility(detailsExpanded ? View.VISIBLE : View.GONE);
            chipsContainer.setAlpha(1f);
            chipsContainer.setScaleY(1f);
            ViewGroup.LayoutParams params = chipsContainer.getLayoutParams();
            if (params != null) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                chipsContainer.setLayoutParams(params);
            }
            if (detailsExpanded) {
                buildChips(chipsContainer);
            }
        }

        updateCommitButtonState();
    }

    private void updateCommitButtonState() {
        boolean repeats = !TodoTask.REPEAT_NONE.equals(draftRepeatUnit);
        boolean ready = draftName.trim().length() > 0 && (!repeats || draftRepeatEveryValid);
        commitButton.setText("edit".equals(sheetMode) ? "Save" : "Add task");
        commitButton.setAlpha(ready ? 1f : 0.4f);
        commitButton.setEnabled(ready);
    }

    private void styleAdjustButton() {
        if (detailsToggle == null) {
            return;
        }
        stopAdjustPulse();
        detailsToggle.setTextColor(detailsExpanded ? palette.accentInk : palette.info);
        setBackgroundKeepPadding(detailsToggle, detailsExpanded
                ? new InsetDrawable(rounded(palette.accent, 999), 0, dp(4), 0, dp(4))
                : null);
    }

    private void startAdjustPulse() {
        if (detailsToggle == null) {
            return;
        }
        if (adjustPulse != null && adjustPulse.isRunning()) {
            return;
        }
        adjustPulse = ValueAnimator.ofFloat(0f, 1f);
        adjustPulse.setDuration(820);
        adjustPulse.setRepeatCount(ValueAnimator.INFINITE);
        adjustPulse.setRepeatMode(ValueAnimator.REVERSE);
        adjustPulse.addUpdateListener(animation -> {
            if (detailsToggle == null) {
                return;
            }
            float f = (float) animation.getAnimatedValue();
            float scale = 1f + 0.07f * f;
            detailsToggle.setScaleX(scale);
            detailsToggle.setScaleY(scale);
        });
        adjustPulse.start();
    }

    private void stopAdjustPulse() {
        if (adjustPulse != null) {
            adjustPulse.cancel();
            adjustPulse = null;
        }
        if (detailsToggle != null) {
            detailsToggle.setScaleX(1f);
            detailsToggle.setScaleY(1f);
        }
    }

    private void styleReminderChip(boolean active) {
        if (remindChip == null) {
            return;
        }
        remindChip.setActive(active || reminderSettingsOpen);
    }

    private void toggleDetailsAnimated() {
        if (chipsContainer == null || detailsAnimating) {
            return;
        }
        boolean expand = !detailsExpanded;
        detailsExpanded = expand;
        detailsAnimating = true;
        updateSheetDynamic();

        if (expand) {
            chipsContainer.removeAllViews();
            buildChips(chipsContainer);
            chipsContainer.setVisibility(View.VISIBLE);
            chipsContainer.setAlpha(0f);
            chipsContainer.setScaleY(0.96f);
            chipsContainer.setPivotY(0f);
            int width = chipsContainer.getWidth();
            if (width <= 0 && sheetPanel != null) {
                width = sheetPanel.getWidth() - dp(36);
            }
            int widthSpec = View.MeasureSpec.makeMeasureSpec(Math.max(1, width), View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            chipsContainer.measure(widthSpec, heightSpec);
            animateDetailsHeight(0, chipsContainer.getMeasuredHeight(), true);
        } else {
            animateDetailsHeight(Math.max(1, chipsContainer.getHeight()), 0, false);
        }
    }

    private void animateDetailsHeight(int from, int to, boolean expanding) {
        ViewGroup.LayoutParams params = chipsContainer.getLayoutParams();
        if (params == null) {
            detailsAnimating = false;
            updateSheetDynamic();
            return;
        }
        params.height = from;
        chipsContainer.setLayoutParams(params);
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams lp = chipsContainer.getLayoutParams();
            lp.height = (int) animation.getAnimatedValue();
            chipsContainer.setLayoutParams(lp);
        });
        chipsContainer.animate()
                .alpha(expanding ? 1f : 0f)
                .scaleY(expanding ? 1f : 0.96f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams lp = chipsContainer.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                chipsContainer.setLayoutParams(lp);
                if (!expanding) {
                    chipsContainer.removeAllViews();
                    chipsContainer.setVisibility(View.GONE);
                }
                chipsContainer.setAlpha(1f);
                chipsContainer.setScaleY(1f);
                detailsAnimating = false;
                updateSheetDynamic();
            }
        });
        animator.start();
    }

    private void buildChips(LinearLayout container) {
        container.addView(impactEffortRow("impact"), matchWrap(0, 0, 0, 8));
        container.addView(impactEffortRow("effort"), matchWrap(0, 0, 0, 10));
        LinearLayout flags = horizontal();
        TextView quick = flagButton("Quick win", Glyph.BOLT, draftQuick,
                palette.accent, palette.accentInk);
        quick.setOnClickListener(v -> {
            draftQuick = !draftQuick;
            updateSheetDynamic();
        });
        TextView urgent = flagButton("Urgent", Glyph.ALERT, draftUrgent,
                PriorityPalette.withAlpha(PriorityPalette.IMMEDIATE, 0x26), PriorityPalette.IMMEDIATE);
        urgent.setOnClickListener(v -> {
            draftUrgent = !draftUrgent;
            updateSheetDynamic();
        });
        flags.addView(quick, weight(1, 0, 0, 6, 0));
        flags.addView(urgent, weight(1, 0, 0, 0, 0));
        container.addView(flags, matchWrap(0, 0, 0, 0));
    }

    private TextView flagButton(String label, String glyph, boolean active, int activeBg, int activeInk) {
        int ink = active ? activeInk : palette.sub;
        TextView flag = text(label, 13, active ? 800 : 600, ink);
        flag.setGravity(Gravity.CENTER);
        flag.setMinHeight(dp(40));
        flag.setCompoundDrawablesRelativeWithIntrinsicBounds(
                new Glyph(glyph, dp(15), ink, 2.2f), null, null, null);
        flag.setCompoundDrawablePadding(dp(6));
        flag.setPadding(dp(12), 0, dp(12), 0);
        flag.setBackground(rounded(active ? activeBg : palette.surface, 10));
        flag.setContentDescription(label + (active ? ", on" : ", off"));
        return flag;
    }

    private LinearLayout impactEffortRow(String field) {
        LinearLayout row = horizontal();
        TextView label = text("impact".equals(field) ? "IMPACT" : "EFFORT", 11, 800, palette.heroSub);
        label.setLetterSpacing(0.08f);
        label.setMinWidth(dp(62));
        row.addView(label, wrap(2, 0, 4, 0));
        String[][] opts = {{"High", TodoTask.HIGH}, {"Medium", TodoTask.MEDIUM}, {"Low", TodoTask.LOW}};
        for (int i = 0; i < opts.length; i++) {
            String optionLabel = opts[i][0];
            String val = opts[i][1];
            boolean active = "impact".equals(field) ? draftImpact.equals(val) : draftEffort.equals(val);
            TextView chip = text(optionLabel, 13, active ? 700 : 500,
                    active ? palette.accentInk : palette.sub);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(40));
            chip.setSingleLine(true);
            chip.setBackground(rounded(active ? palette.accent : palette.surface, 10));
            if (active) {
                chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        new Glyph(Glyph.CHECK, dp(14), palette.accentInk, 2.6f), null, null, null);
                chip.setCompoundDrawablePadding(dp(4));
                chip.setPadding(dp(8), 0, dp(10), 0);
            }
            chip.setContentDescription(("impact".equals(field) ? "Impact " : "Effort ")
                    + optionLabel + (active ? ", selected" : ""));
            chip.setOnClickListener(v -> {
                if ("impact".equals(field)) {
                    draftImpact = val;
                } else {
                    draftEffort = val;
                }
                updateSheetDynamic();
            });
            row.addView(chip, weight(1, 0, 0, i < opts.length - 1 ? 6 : 0, 0));
        }
        return row;
    }

    private void buildRepeatControls(LinearLayout row) {
        row.setGravity(Gravity.CENTER_VERTICAL);
        boolean repeats = !TodoTask.REPEAT_NONE.equals(draftRepeatUnit);

        TextView label = text("Repeats every", 13, 600, palette.ink);
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                new Glyph(Glyph.REPEAT, dp(16), palette.sub, 2.2f), null, null, null);
        label.setCompoundDrawablePadding(dp(8));
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setContentDescription("Repeats every");
        row.addView(label, weight(1, 0, 0, 8, 0));

        View intervalControl;
        if (repeats) {
            draftRepeatEveryValid = TodoTask.parseRepeatInterval(draftRepeatEveryText) > 0;
            EditText every = new EditText(themedContext());
            every.setText(draftRepeatEveryText);
            every.setInputType(InputType.TYPE_CLASS_NUMBER);
            every.setImeOptions(EditorInfo.IME_ACTION_DONE);
            every.setTextSize(13);
            every.setTextColor(palette.ink);
            every.setGravity(Gravity.CENTER);
            every.setSelectAllOnFocus(true);
            every.setSingleLine(true);
            every.setPadding(dp(8), 0, dp(8), 0);
            every.setBackground(repeatControlBackground());
            every.setError(draftRepeatEveryValid ? null : "Enter 1-999");
            every.setContentDescription(draftRepeatEveryValid
                    ? "Repeat interval: " + draftRepeatEveryText
                    : "Repeat interval: invalid. Enter 1 to 999.");
            every.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    draftRepeatEveryText = s.toString();
                    int parsed = TodoTask.parseRepeatInterval(s.toString());
                    draftRepeatEveryValid = parsed > 0;
                    if (draftRepeatEveryValid) {
                        draftRepeatEvery = parsed;
                    }
                    every.setError(draftRepeatEveryValid ? null : "Enter 1-999");
                    every.setContentDescription(draftRepeatEveryValid
                            ? "Repeat interval: " + parsed
                            : "Repeat interval: invalid. Enter 1 to 999.");
                    updateCommitButtonState();
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            intervalControl = every;
        } else {
            draftRepeatEveryValid = true;
            TextView every = text(draftRepeatEveryText, 13, 700, palette.sub);
            every.setGravity(Gravity.CENTER);
            every.setAlpha(0.55f);
            every.setBackground(repeatControlBackground());
            every.setContentDescription(
                    "Repeat interval: disabled until a repeat unit is selected");
            intervalControl = every;
        }
        LinearLayout.LayoutParams everyParams = new LinearLayout.LayoutParams(dp(48), dp(44));
        everyParams.rightMargin = dp(6);
        row.addView(intervalControl, everyParams);

        RepeatOption selected = repeatOption(draftRepeatUnit);
        TextView unit = text(selected.label, 13, 700, palette.ink);
        unit.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                new Glyph(Glyph.EXPAND_MORE, dp(16), palette.sub, 2.2f), null);
        unit.setCompoundDrawablePadding(dp(2));
        unit.setGravity(Gravity.CENTER);
        unit.setSingleLine(true);
        unit.setEllipsize(TextUtils.TruncateAt.END);
        unit.setMinWidth(dp(64));
        unit.setMinHeight(dp(44));
        unit.setPadding(dp(12), 0, dp(8), 0);
        unit.setBackground(repeatControlBackground());
        unit.setContentDescription("Repeat unit: " + selected.label
                + ". Double tap to change.");
        unit.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(themedContext(), unit);
            for (int index = 0; index < REPEAT_OPTIONS.length; index++) {
                menu.getMenu().add(0, index, index, REPEAT_OPTIONS[index].label);
            }
            menu.setOnMenuItemClickListener(item -> {
                draftRepeatUnit = REPEAT_OPTIONS[item.getItemId()].value;
                draftRepeatEveryValid = TodoTask.parseRepeatInterval(draftRepeatEveryText) > 0;
                if (TodoTask.REPEAT_NONE.equals(draftRepeatUnit)) {
                    draftRepeatEvery = 1;
                    draftRepeatEveryText = "1";
                }
                updateSheetDynamic();
                return true;
            });
            menu.show();
        });
        row.addView(unit, wrap(0, 0, 0, 0));
    }

    private GradientDrawable repeatControlBackground() {
        return rounded(palette.soft, 12);
    }

    private void openReminderPicker() {
        Calendar calendar = Calendar.getInstance();
        if (draftReminderAt > 0) {
            calendar.setTimeInMillis(draftReminderAt);
        } else {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
        }
        DatePickerDialog datePicker = new DatePickerDialog(themedContext(), (view, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog timePicker = new TimePickerDialog(themedContext(), (tv, hour, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                draftReminderAt = calendar.getTimeInMillis();
                updateSheetDynamic();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);
            timePicker.show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePicker.show();
    }

    private void commitSheet() {
        String name = draftName.trim();
        if (name.isEmpty()) {
            return;
        }
        boolean repeats = !TodoTask.REPEAT_NONE.equals(draftRepeatUnit);
        if (repeats && !draftRepeatEveryValid) {
            return;
        }
        if (repeats && draftReminderAt == 0) {
            draftRepeatUnit = TodoTask.REPEAT_NONE;
        }
        if (TodoTask.shouldClearExpiredReminder(
                draftReminderAt, draftRepeatUnit, System.currentTimeMillis())) {
            draftReminderAt = 0;
        }

        TodoTask task;
        boolean isNew = "add".equals(sheetMode);
        if (isNew) {
            task = new TodoTask();
        } else {
            task = findTask(sheetEditId);
            if (task == null) {
                task = new TodoTask();
                isNew = true;
            }
        }
        task.title = name;
        task.notes = normalizeNotes(draftNotes);
        task.impact = draftImpact;
        task.effort = draftEffort;
        task.category = draftCategory;
        task.urgent = draftUrgent;
        task.quickTask = draftQuick;
        task.reminderAt = draftReminderAt;
        task.reminderRepeatUnit = draftReminderAt > 0 ? draftRepeatUnit : TodoTask.REPEAT_NONE;
        task.reminderRepeatEvery = TodoTask.REPEAT_NONE.equals(task.reminderRepeatUnit)
                ? 1 : Math.max(1, draftRepeatEvery);
        task.updatedAt = nextMutationTimestamp();
        if (isNew) {
            tasks.add(task);
        }
        ReminderScheduler.schedule(this, task);
        persistMutation();
        closeSheet();
        renderAll(true);
        if (isNew) {
            if (mainScroll != null) mainScroll.smoothScrollTo(0, 0);
            showCheer("Let's go!");
        }
    }

    private void confirmDelete(String id) {
        TodoTask task = findTask(id);
        if (task == null) {
            return;
        }
        new AlertDialog.Builder(themedContext())
                .setTitle("Delete task?")
                .setMessage("This removes \"" + task.title + "\" from your priority index.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ReminderScheduler.cancel(this, task);
                    recordTaskTombstone(task.id, nextMutationTimestamp());
                    tasks.remove(task);
                    persistMutation();
                    closeSheet();
                    renderAll(true);
                })
                .show();
    }

    private void closeSheet() {
        stopAdjustPulse();
        if (sheetOverlay == null) {
            sheetOpen = false;
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && sheetInput != null) {
            imm.hideSoftInputFromWindow(sheetInput.getWindowToken(), 0);
        }
        final FrameLayout overlay = sheetOverlay;
        detachSheetKeyboardHandling();
        sheetScrim.animate().alpha(0f).setDuration(160).start();
        sheetPanel.animate().translationY(sheetPanel.getHeight()).setDuration(220)
                .withEndAction(() -> {
                    root.removeView(overlay);
                    restoreActZone();
                }).start();
        sheetOverlay = null;
        unregisterSheetBack();
        sheetOpen = false;
    }

    private void removeSheetImmediate() {
        unregisterSheetBack();
        stopAdjustPulse();
        if (sheetOverlay != null) {
            detachSheetKeyboardHandling();
            root.removeView(sheetOverlay);
            sheetOverlay = null;
        }
        sheetOpen = false;
        restoreActZone();
    }

    private void hideActZoneInstant() {
        if (fab != null) {
            fab.animate().cancel();
            fab.setVisibility(View.INVISIBLE);
        }
        if (fabTarget != null) {
            fabTarget.setVisibility(View.INVISIBLE);
        }
        if (catStrip != null) catStrip.setVisibility(View.INVISIBLE);
        if (reachArc != null) {
            reachArc.animate().cancel();
            reachArc.setAlpha(0f);
        }
    }

    private void restoreActZone() {
        setListDockVisible(true);
        if (fab != null) {
            fab.animate().cancel();
            fab.setVisibility(View.VISIBLE);
            fab.setAlpha(0f);
            fab.setScaleX(0.9f);
            fab.setScaleY(0.9f);
            fab.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(new OvershootInterpolator(1.6f))
                    .start();
        }
        if (fabTarget != null) {
            fabTarget.setVisibility(View.VISIBLE);
        }
        if (reachArc != null) {
            reachArc.animate().cancel();
            reachArc.setAlpha(0f);
            reachArc.animate().alpha(1f).setDuration(180).start();
        }
    }

    private void registerSheetBack() {
        if (Build.VERSION.SDK_INT >= 33 && sheetBackCallback == null) {
            sheetBackCallback = this::closeSheet;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, sheetBackCallback);
        }
    }

    private void unregisterSheetBack() {
        if (Build.VERSION.SDK_INT >= 33 && sheetBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(sheetBackCallback);
            sheetBackCallback = null;
        }
    }

    // API 33+ uses the dispatcher while the sheet is open; older devices use this callback.
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        if (sheetOpen) {
            closeSheet();
            return;
        }
        super.onBackPressed();
    }

    // ===================== sheet helpers =====================

    private void attachSheetKeyboardHandling() {
        if (sheetOverlay == null) {
            return;
        }
        sheetOverlay.setOnApplyWindowInsetsListener((view, insets) -> {
            updateSheetForKeyboardInset(visibleKeyboardInset(view, insets));
            return insets;
        });
        sheetKeyboardLayoutListener = () -> updateSheetForKeyboardInset(visibleKeyboardInset(sheetOverlay, null));
        sheetOverlay.getViewTreeObserver().addOnGlobalLayoutListener(sheetKeyboardLayoutListener);
        sheetOverlay.requestApplyInsets();
        sheetOverlay.post(() -> {
            updateSheetForKeyboardInset(visibleKeyboardInset(sheetOverlay, null));
        });
    }

    private void detachSheetKeyboardHandling() {
        if (sheetOverlay == null || sheetKeyboardLayoutListener == null) {
            sheetKeyboardLayoutListener = null;
            return;
        }
        sheetOverlay.getViewTreeObserver().removeOnGlobalLayoutListener(sheetKeyboardLayoutListener);
        sheetOverlay.setOnApplyWindowInsetsListener(null);
        sheetKeyboardLayoutListener = null;
    }

    private int visibleKeyboardInset(View view, WindowInsets appliedInsets) {
        if (view == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = appliedInsets != null ? appliedInsets : view.getRootWindowInsets();
            if (insets != null) {
                int imeInset = insets.getInsets(WindowInsets.Type.ime()).bottom;
                if (imeInset >= dp(120)) {
                    return imeInset;
                }
            }
        }
        Rect visibleFrame = new Rect();
        view.getWindowVisibleDisplayFrame(visibleFrame);
        int screenHeight = Math.max(view.getRootView().getHeight(),
                getResources().getDisplayMetrics().heightPixels);
        int inset = Math.max(0, screenHeight - visibleFrame.bottom);
        return inset >= dp(120) ? inset : 0;
    }

    private void updateSheetForKeyboardInset(int keyboardInsetPx) {
        if (sheetPanel == null || root == null) {
            return;
        }
        int contentHeight = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int contentKeyboardInset = Math.max(0, keyboardInsetPx - root.getPaddingBottom());
        SheetKeyboardFrame frame = sheetKeyboardFrame(
                contentHeight, contentKeyboardInset, dp(8), dp(12), dp(180));
        ViewGroup.LayoutParams rawParams = sheetPanel.getLayoutParams();
        if (!(rawParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
        if (params.bottomMargin != frame.bottomMarginPx) {
            params.bottomMargin = frame.bottomMarginPx;
            sheetPanel.setLayoutParams(params);
        }
        if (sheetPanel instanceof KeyboardAwareSheetPanel) {
            ((KeyboardAwareSheetPanel) sheetPanel).setMaxHeightPx(frame.maxHeightPx);
        }
        keepFocusedSheetFieldVisible();
    }

    private void keepFocusedSheetFieldVisible() {
        if (sheetScroll == null || sheetInput == null) {
            return;
        }
        View focused = sheetPanel == null ? null : sheetPanel.findFocus();
        if (focused == notesInput) {
            keepNotesCaretVisible();
            return;
        }
        if (focused != null) {
            focused.post(() -> focused.requestRectangleOnScreen(
                    new Rect(0, 0, focused.getWidth(), focused.getHeight()), false));
        }
    }

    private void keepNotesCaretVisible() {
        if (notesInput == null || !notesInput.hasFocus()) {
            return;
        }
        notesInput.post(() -> {
            android.text.Layout layout = notesInput.getLayout();
            int offset = notesInput.getSelectionStart();
            if (layout == null || offset < 0) {
                return;
            }
            int line = layout.getLineForOffset(offset);
            int paddingTop = notesInput.getTotalPaddingTop();
            Rect caret = new Rect(0, layout.getLineTop(line) + paddingTop, notesInput.getWidth(),
                    layout.getLineBottom(line) + paddingTop);
            notesInput.requestRectangleOnScreen(caret, false);
        });
    }

    static SheetKeyboardFrame sheetKeyboardFrame(
            int rootHeightPx,
            int keyboardInsetPx,
            int keyboardGapPx,
            int topGapPx,
            int fallbackHeightPx) {
        int inset = Math.max(0, keyboardInsetPx);
        int bottomMargin = inset > 0 ? inset + Math.max(0, keyboardGapPx) : 0;
        int maxHeight;
        if (rootHeightPx > 0) {
            maxHeight = Math.max(0, rootHeightPx - bottomMargin - Math.max(0, topGapPx));
        } else {
            maxHeight = Math.max(0, fallbackHeightPx);
        }
        return new SheetKeyboardFrame(bottomMargin, maxHeight);
    }

    static String normalizeNotes(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean shouldExpandNotes(String mode, String value) {
        return "edit".equals(mode) && !normalizeNotes(value).isEmpty();
    }

    static String listPillLabel(String category) {
        return category == null || category.trim().isEmpty() ? "No list" : category;
    }

    static final class SheetKeyboardFrame {
        final int bottomMarginPx;
        final int maxHeightPx;

        SheetKeyboardFrame(int bottomMarginPx, int maxHeightPx) {
            this.bottomMarginPx = bottomMarginPx;
            this.maxHeightPx = maxHeightPx;
        }
    }

    private String predictBucket() {
        TodoTask probe = new TodoTask();
        probe.impact = draftImpact;
        probe.effort = draftEffort;
        probe.urgent = draftUrgent;
        return PriorityPalette.bucket(probe.score());
    }

    private int valueColor(String value) {
        if (TodoTask.HIGH.equals(value)) {
            return PriorityPalette.IMMEDIATE;
        }
        if (TodoTask.MEDIUM.equals(value)) {
            return PriorityPalette.NEXT_WEEK;
        }
        return PriorityPalette.SOMEDAY;
    }

    private int valueInk(String value) {
        return TodoTask.MEDIUM.equals(value) ? 0xFF1B1F2E : 0xFFFFFFFF;
    }

    private String impactLabel(String value) {
        if (TodoTask.HIGH.equals(value)) {
            return "High";
        }
        if (TodoTask.MEDIUM.equals(value)) {
            return "Medium";
        }
        return "Low";
    }

    private RepeatOption repeatOption(String unit) {
        for (RepeatOption option : REPEAT_OPTIONS) {
            if (option.value.equals(unit)) {
                return option;
            }
        }
        return REPEAT_OPTIONS[0];
    }

    private TextView chipGroupLabel(String value) {
        TextView label = text(value, 11, 800, palette.sub);
        label.setLetterSpacing(0.1f);
        return label;
    }

    private TextView chipButton(String label, boolean active, int activeColor, int activeInk) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        applyFont(chip, active ? 700 : 600);
        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(999));
        if (active) {
            bg.setColor(activeColor);
            bg.setStroke(dp(2), activeColor);
            chip.setTextColor(activeInk);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(2), palette.line);
            chip.setTextColor(palette.sub);
        }
        chip.setBackground(bg);
        return chip;
    }

    private TextView categoryPill(String bucket) {
        TextView pill = new TextView(this);
        styleCategoryPill(pill, bucket);
        return pill;
    }

    private void styleCategoryPill(TextView pill, String bucket) {
        pill.setText(bucket.toUpperCase());
        pill.setTextSize(10);
        applyFont(pill, 800);
        pill.setLetterSpacing(0.06f);
        pill.setTextColor(0xFFFFFFFF);
        pill.setPadding(dp(9), dp(3), dp(9), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PriorityPalette.catColor(bucket));
        bg.setCornerRadius(dp(999));
        pill.setBackground(bg);
    }

    private TextView chipText(String value, int fg, int bgColor) {
        TextView chip = new TextView(this);
        chip.setText(value);
        chip.setTextSize(11);
        applyFont(chip, 700);
        chip.setTextColor(fg);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(999));
        chip.setBackground(bg);
        return chip;
    }

    // ===================== model helpers =====================

    private void sortTasks() {
        Collections.sort(tasks, new Comparator<TodoTask>() {
            @Override
            public int compare(TodoTask first, TodoTask second) {
                int byScore = Double.compare(second.score(), first.score());
                if (byScore != 0) {
                    return byScore;
                }
                return Long.compare(first.createdAt, second.createdAt);
            }
        });
    }

    private TodoTask findTask(String id) {
        for (TodoTask task : tasks) {
            if (task.id.equals(id)) {
                return task;
            }
        }
        return null;
    }

    private String reminderShort(TodoTask task) {
        return reminderShortFromMillis(task.reminderAt);
    }

    private String reminderShortFromMillis(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(millis));
    }

    // ===================== reminders bootstrap (unchanged behavior) =====================

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            ReminderReceiver.createChannel(this, manager);
        }
    }

    private void scheduleTaskReminders() {
        for (TodoTask task : tasks) {
            ReminderScheduler.schedule(this, task);
        }
    }

    private static final class RepeatOption {
        final String value;
        final String label;

        RepeatOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /** Inline clock inside the title field: grey when unset, blue once a reminder exists. */
    private final class ReminderClockButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean active;

        ReminderClockButton() {
            super(MainActivity.this);
            setClickable(true);
            setFocusable(true);
        }

        void setActive(boolean value) {
            active = value;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(40), dp(44));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(active ? palette.info : palette.chip);
            canvas.drawCircle(cx, cy, dp(15), paint);
            float density = getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(1.8f * density);
            paint.setColor(active ? palette.infoInk : palette.sub);
            canvas.drawCircle(cx, cy, 7.5f * density, paint);
            canvas.drawLine(cx, cy, cx, cy - 4.2f * density, paint);
            canvas.drawLine(cx, cy, cx + 3f * density, cy + 2f * density, paint);
        }
    }

    private final class HeaderIconButton extends View {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Path path = new Path();
        private final Path bite = new Path();
        private String handState = HAND_RIGHT;
        private String themeState = THEME_DAY;

        HeaderIconButton(Context context, String kind) {
            super(context);
            this.kind = kind;
            setClickable(true);
            setFocusable(true);
            setState(hand, theme);
        }

        void setState(String handState, String themeState) {
            this.handState = handState;
            this.themeState = themeState;
            if ("more".equals(kind) || "search".equals(kind) || "account".equals(kind)) {
                invalidate();
                return;
            }
            if ("hand".equals(kind)) {
                if (HAND_RIGHT.equals(handState)) {
                    setContentDescription("Add button on right");
                } else if (HAND_CENTER.equals(handState)) {
                    setContentDescription("Add button centered");
                } else {
                    setContentDescription("Add button on left");
                }
            } else if (THEME_DAY.equals(themeState)) {
                setContentDescription("Day theme");
            } else if (THEME_NIGHT.equals(themeState)) {
                setContentDescription("Night theme");
            } else {
                setContentDescription("Auto theme");
            }
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = dp(44);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            if ("account".equals(kind)) {
                drawAccount(canvas, cx, cy);
                return;
            }
            // Quiet 20dp line icons on a 24-unit grid, centered in a 44dp target.
            float u = dp(20) / 24f;
            canvas.save();
            canvas.translate(cx - 12f * u, cy - 12f * u);
            canvas.scale(u, u);
            paint.setColor(palette.sub);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(2f);
            if ("search".equals(kind)) {
                canvas.drawCircle(10.5f, 10.5f, 6.5f, paint);
                canvas.drawLine(15.5f, 15.5f, 20.5f, 20.5f, paint);
            } else if ("hand".equals(kind)) {
                drawHand(canvas);
            } else if ("more".equals(kind)) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12f, 5f, 2f, paint);
                canvas.drawCircle(12f, 12f, 2f, paint);
                canvas.drawCircle(12f, 19f, 2f, paint);
            } else if (THEME_NIGHT.equals(themeState)) {
                drawMoon(canvas);
            } else if (THEME_SYSTEM.equals(themeState)) {
                drawAuto(canvas);
            } else {
                drawSun(canvas);
            }
            canvas.restore();
        }

        private void drawAccount(Canvas canvas, float cx, float cy) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.accent);
            canvas.drawCircle(cx, cy, dp(16), paint);
            float u = dp(18) / 24f;
            canvas.save();
            canvas.translate(cx - 12f * u, cy - 12f * u);
            canvas.scale(u, u);
            paint.setColor(palette.accentInk);
            canvas.drawCircle(12f, 8.5f, 3.8f, paint);
            oval.set(4.5f, 14f, 19.5f, 27f);
            canvas.drawArc(oval, 180f, 180f, true, paint);
            canvas.restore();
        }

        private void drawHand(Canvas canvas) {
            canvas.save();
            if (HAND_LEFT.equals(handState)) {
                canvas.scale(-1f, 1f, 12f, 12f);
            }
            canvas.translate(0.5f, -1.5f);
            paint.setStrokeWidth(1.8f);
            canvas.drawLine(6f, 15f, 6f, 8f, paint);
            canvas.drawLine(10f, 14f, 10f, 5f, paint);
            canvas.drawLine(14f, 14f, 14f, 4.5f, paint);
            canvas.drawLine(18f, 15f, 18f, 8f, paint);
            path.reset();
            path.moveTo(6f, 15f);
            path.cubicTo(4.7f, 13.5f, 3.2f, 13.1f, 2.3f, 14f);
            path.cubicTo(1.5f, 14.9f, 1.8f, 16.1f, 2.9f, 17.2f);
            path.lineTo(6.1f, 20.2f);
            path.cubicTo(8f, 22f, 10.3f, 23f, 13.7f, 23f);
            path.cubicTo(18.6f, 23f, 21f, 20f, 21f, 15.6f);
            path.lineTo(21f, 10f);
            path.cubicTo(21f, 8.7f, 20.1f, 7.8f, 19f, 7.8f);
            path.cubicTo(18.4f, 7.8f, 18f, 8.2f, 18f, 8.9f);
            canvas.drawPath(path, paint);
            canvas.restore();
            if (HAND_CENTER.equals(handState)) {
                // Small mark under the palm: the + button is centered.
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12f, 24.5f, 1.3f, paint);
            }
        }

        private void drawSun(Canvas canvas) {
            canvas.drawCircle(12f, 12f, 4.2f, paint);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4d;
                canvas.drawLine(12f + (float) Math.cos(angle) * 7.6f, 12f + (float) Math.sin(angle) * 7.6f,
                        12f + (float) Math.cos(angle) * 10f, 12f + (float) Math.sin(angle) * 10f, paint);
            }
        }

        private void drawMoon(Canvas canvas) {
            path.reset();
            path.addCircle(11.5f, 12.5f, 8.5f, Path.Direction.CW);
            bite.reset();
            bite.addCircle(16.5f, 7.5f, 7f, Path.Direction.CW);
            path.op(bite, Path.Op.DIFFERENCE);
            canvas.drawPath(path, paint);
        }

        private void drawAuto(Canvas canvas) {
            canvas.drawCircle(12f, 12f, 8.5f, paint);
            paint.setStyle(Paint.Style.FILL);
            oval.set(3.5f, 3.5f, 20.5f, 20.5f);
            canvas.drawArc(oval, 90f, 180f, true, paint);
        }
    }

    /**
     * Small uppercase list tag drawn on canvas. It is not a TextView on purpose:
     * its label stays out of view text so it never shadows the real list controls.
     */
    private final class TagView extends View {
        private final String label;
        private final android.text.TextPaint textPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final int padX = dp(6);
        private final int padY = dp(3);

        TagView(String name, int color) {
            super(MainActivity.this);
            label = name.toUpperCase(java.util.Locale.ROOT);
            textPaint.setColor(color);
            textPaint.setTextSize(android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
            textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            textPaint.setLetterSpacing(0.04f);
            fillPaint.setColor(PriorityPalette.withAlpha(color, 0x1F));
            setContentDescription("In list " + name);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            int width = Math.min(Math.round(textPaint.measureText(label)) + padX * 2, dp(96));
            int height = Math.round(metrics.descent - metrics.ascent) + padY * 2;
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            box.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(box, dp(5), dp(5), fillPaint);
            CharSequence shown = TextUtils.ellipsize(label, textPaint,
                    getWidth() - padX * 2f, TextUtils.TruncateAt.END);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(shown, 0, shown.length(), padX, baseline, textPaint);
        }
    }

    private static final class KeyboardAwareSheetPanel extends LinearLayout {
        private int maxHeightPx = Integer.MAX_VALUE;

        KeyboardAwareSheetPanel(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
        }

        void setMaxHeightPx(int maxHeightPx) {
            int resolved = maxHeightPx > 0 ? maxHeightPx : Integer.MAX_VALUE;
            if (this.maxHeightPx == resolved) {
                return;
            }
            this.maxHeightPx = resolved;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int constrainedHeightSpec = heightMeasureSpec;
            if (maxHeightPx < Integer.MAX_VALUE) {
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                if (mode == MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                    constrainedHeightSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST);
                }
            }
            super.onMeasure(widthMeasureSpec, constrainedHeightSpec);
        }
    }

    // ===================== view factory helpers =====================

    private TextView pill(String label) {
        TextView pill = new TextView(this);
        pill.setText(label);
        pill.setTextSize(11);
        applyFont(pill, 700);
        pill.setTextColor(palette.sub);
        pill.setPadding(dp(11), dp(7), dp(11), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.surface);
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), palette.line);
        pill.setBackground(bg);
        return pill;
    }

    private TextView text(String value, int sizeSp, int weight, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        applyFont(view, weight);
        view.setIncludeFontPadding(true);
        return view;
    }

    private void applyFont(TextView view, int weight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setTypeface(Typeface.create(Typeface.SANS_SERIF, weight, false));
        } else {
            view.setTypeface(Typeface.SANS_SERIF, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams wrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weight(float weight, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
