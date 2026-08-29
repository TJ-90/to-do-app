package com.tj90.prioritytodo;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private static final String LEGACY_NIGHT_MODE = "night_mode";
    private static final String KEY_THEME = "theme";
    private static final String KEY_HAND = "hand";
    private static final String KEY_ONBOARDED = "onboarded_v2";

    private static final String THEME_DAY = "day";
    private static final String THEME_NIGHT = "night";
    private static final String THEME_SYSTEM = "system";
    private static final String HAND_RIGHT = "right";
    private static final String HAND_CENTER = "center";
    private static final String HAND_LEFT = "left";
    private static final int REQUEST_EXPORT_CSV = 21;
    private static final int REQUEST_IMPORT_CSV = 22;

    private static final String[] DEPENDENCIES = {"None", "Sequential", "Reciprocal", "Pooled"};
    private static final String[] REPEAT_UNITS = {"No repeat", "Day", "Week", "Month"};

    private static final int[] CONFETTI_COLORS = {
            0xFF008135, 0xFF3D9C5E, 0xFF59AD73, 0xFF60C781, 0xFF9FE4B1, 0xFFFFFFFF
    };

    private final List<TodoTask> tasks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TaskStore store;
    private PriorityPalette palette;
    private String theme = THEME_DAY;
    private String hand = HAND_RIGHT;

    private FrameLayout root;
    private TextView headerSubView;
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
    private String toastTaskId;
    private String toastActionType;

    private View cheerView;
    private Runnable cheerDismiss;

    // ---- sheet draft state ----
    private boolean sheetOpen;
    private String sheetMode = "add";
    private String sheetEditId;
    private String draftName = "";
    private String draftNotes = "";
    private String draftImpact = TodoTask.LOW;
    private String draftEffort = TodoTask.LOW;
    private String draftDep = "None";
    private boolean draftUrgent;
    private boolean draftQuick = true;
    private long draftReminderAt;
    private String draftRepeatUnit = TodoTask.REPEAT_NONE;
    private int draftRepeatEvery = 1;
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
    private TextView remindChip;
    private TextView remindClear;
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
        tasks.addAll(store.load());
        categories.addAll(store.loadCategories());
        requestNotificationPermission();
        createNotificationChannel();
        rescheduleFutureReminders();
        buildChrome();
        renderAll(false);
        maybeShowOnboarding();
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
    }

    private void persistPrefs() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit()
                .putString(KEY_THEME, theme)
                .putString(KEY_HAND, hand)
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
        new AlertDialog.Builder(this)
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
        new AlertDialog.Builder(this)
                .setTitle("How it works")
                .setMessage("• Tap  +  to add a task. It sorts itself by priority.\n\n"
                        + "• Tap a task to reveal its full content. Long-press it to edit.\n\n"
                        + "• Tap Adjust to set impact & effort, or mark it Urgent to send it to #1.\n\n"
                        + "• Swipe a task toward your thumb to finish it, the other way for Later.\n\n"
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

    private void buildChrome() {
        getWindow().setStatusBarColor(palette.bg);
        getWindow().setNavigationBarColor(palette.bg);
        int flags = isNightEffective() ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        root = new FrameLayout(this);
        root.setBackgroundColor(palette.surface);

        LinearLayout column = vertical();
        column.setBackgroundColor(palette.surface);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(column, columnParams);

        column.addView(buildHeader(), matchWrap(0, 0, 0, 0));
        catStrip = vertical();
        column.addView(catStrip, matchWrap(0, 4, 0, 0));
        heroContainer = vertical();
        column.addView(heroContainer, matchWrap(16, 8, 16, 4));

        LinearLayout listHeader = horizontal();
        listHeader.setPadding(dp(20), dp(6), dp(20), dp(6));
        TextView indexLabel = text("PRIORITY INDEX", 11, 800, palette.sub);
        indexLabel.setLetterSpacing(0.14f);
        TextView autoSorted = text("auto-sorted", 11, 600, palette.sub);
        autoSorted.setAlpha(0.8f);
        listHeader.addView(indexLabel, weight(1, 0, 0, 0, 0));
        listHeader.addView(autoSorted, wrap(0, 0, 0, 0));
        column.addView(listHeader, matchWrap(0, 4, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        listContainer = vertical();
        listContainer.setPadding(dp(20), dp(2), dp(20), dp(150));
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        addActZone();

        confettiView = new ConfettiView(this);
        FrameLayout.LayoutParams confettiParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(confettiView, confettiParams);
        confettiView.setClickable(false);
        confettiView.setFocusable(false);

        setContentView(root);
    }

    private LinearLayout buildHeader() {
        LinearLayout header = horizontal();
        header.setPadding(dp(24), dp(20), dp(16), dp(4));
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = vertical();
        TextView today = text("Today", 24, 800, palette.ink);
        today.setIncludeFontPadding(false);
        headerSubView = text("", 13, 600, palette.sub);
        titleCol.addView(today);
        titleCol.addView(headerSubView, matchWrap(0, 4, 0, 0));
        header.addView(titleCol, weight(1, 0, 0, 0, 0));

        // Minimal icon-only cluster. Import/Export live in the overflow menu so the
        // header stays uncluttered.
        LinearLayout toggles = horizontal();
        themePill = new HeaderIconButton(this, "theme");
        themePill.setOnClickListener(v -> cycleTheme());
        handPill = new HeaderIconButton(this, "hand");
        handPill.setOnClickListener(v -> toggleHand());
        HeaderIconButton more = new HeaderIconButton(this, "more");
        more.setContentDescription("More options");
        more.setOnClickListener(this::showOverflowMenu);
        toggles.addView(themePill, wrap(0, 0, 12, 0));
        toggles.addView(handPill, wrap(0, 0, 12, 0));
        toggles.addView(more, wrap(0, 0, 0, 0));
        header.addView(toggles, wrap(0, 0, 0, 0));
        return header;
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Import CSV");
        menu.getMenu().add(0, 2, 1, "Export CSV");
        menu.getMenu().add(0, 3, 2, "How it works");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startCsvImport();
                    return true;
                case 2:
                    startCsvExport();
                    return true;
                case 3:
                    showHowItWorks();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void addActZone() {
        reachArc = new View(this);
        GradientDrawable arc = new GradientDrawable();
        arc.setShape(GradientDrawable.OVAL);
        arc.setColor(Color.TRANSPARENT);
        arc.setStroke(dp(2), PriorityPalette.withAlpha(palette.accent, 0x52), dp(8), dp(8));
        reachArc.setBackground(arc);
        FrameLayout.LayoutParams arcParams = new FrameLayout.LayoutParams(dp(280), dp(280));
        arcParams.gravity = Gravity.BOTTOM | horizontalGravityForAddButton();
        arcParams.bottomMargin = dp(-118);
        if (HAND_RIGHT.equals(hand)) {
            arcParams.rightMargin = dp(-118);
        } else if (HAND_LEFT.equals(hand)) {
            arcParams.leftMargin = dp(-118);
        }
        root.addView(reachArc, arcParams);

        fab = new TextView(this);
        TextView fabText = (TextView) fab;
        fabText.setText("+");
        fabText.setTextSize(28);
        fabText.setTypeface(Typeface.DEFAULT_BOLD);
        fabText.setTextColor(palette.accentInk);
        fabText.setGravity(Gravity.CENTER);
        fabText.setIncludeFontPadding(false);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setColor(palette.accent);
        fabBg.setCornerRadius(dp(22));
        fab.setBackground(fabBg);
        fab.setElevation(dp(8));
        fab.setOnClickListener(v -> openAddSheet());
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dp(60), dp(60));
        fabParams.gravity = Gravity.BOTTOM | horizontalGravityForAddButton();
        fabParams.bottomMargin = dp(24);
        if (HAND_RIGHT.equals(hand)) {
            fabParams.rightMargin = dp(20);
        } else if (HAND_LEFT.equals(hand)) {
            fabParams.leftMargin = dp(20);
        }
        root.addView(fab, fabParams);

        fabTarget = new View(this);
        fabTarget.setContentDescription("Add task");
        fabTarget.setOnClickListener(v -> openAddSheet());
        fabTarget.setOnTouchListener(this::handleFabTouch);
        FrameLayout.LayoutParams targetParams = new FrameLayout.LayoutParams(dp(60), dp(60));
        targetParams.gravity = fabParams.gravity;
        targetParams.bottomMargin = fabParams.bottomMargin;
        targetParams.leftMargin = fabParams.leftMargin;
        targetParams.rightMargin = fabParams.rightMargin;
        root.addView(fabTarget, targetParams);

        View homeBar = new View(this);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(palette.ink);
        hb.setCornerRadius(dp(2));
        homeBar.setBackground(hb);
        homeBar.setAlpha(0.16f);
        FrameLayout.LayoutParams hbParams = new FrameLayout.LayoutParams(dp(120), dp(4));
        hbParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hbParams.bottomMargin = dp(8);
        root.addView(homeBar, hbParams);
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
        List<TodoTask> active = activeTasks();
        int total = 0;
        for (TodoTask t : tasks) {
            if (inActiveCat(t)) {
                total++;
            }
        }
        int remaining = active.size();
        int done = total - remaining;
        int pct = total == 0 ? 0 : Math.round((done / (float) total) * 100f);

        TodoTask mit = active.isEmpty() ? null : active.get(0);
        int heroAccent = PriorityPalette.spectrumColor(0, active.size(), isNightEffective());

        headerSubView.setText(remaining + " to go  ·  " + done + " done");
        if (handPill != null) {
            handPill.setState(hand, theme);
        }
        if (themePill != null) {
            themePill.setState(hand, theme);
        }
        renderCategoryStrip();

        renderHero(mit, heroAccent, pct, done, total);
        renderList(active, animateReorder);
    }

    private void renderHero(TodoTask mit, int accent, int pct, int done, int total) {
        heroContainer.removeAllViews();
        LinearLayout card = vertical();
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.surface);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(2), PriorityPalette.withAlpha(accent, 0xB3));
        card.setBackground(bg);

        LinearLayout progressRow = horizontal();
        FrameLayout track = new FrameLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(PriorityPalette.withAlpha(palette.heroInk, 0x17));
        trackBg.setCornerRadius(dp(3));
        track.setBackground(trackBg);
        progressFill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(accent);
        fillBg.setCornerRadius(dp(3));
        progressFill.setBackground(fillBg);
        track.addView(progressFill, new FrameLayout.LayoutParams(0, dp(5)));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(0, dp(5), 1f);
        trackParams.rightMargin = dp(10);
        trackParams.gravity = Gravity.CENTER_VERTICAL;
        progressRow.addView(track, trackParams);
        progressText = text(done + " of " + total + " done", 11, 700, palette.heroSub);
        progressRow.addView(progressText, wrap(0, 0, 0, 0));
        card.addView(progressRow, matchWrap(0, 0, 0, 14));
        final int pctFinal = pct;
        final FrameLayout trackRef = track;
        track.post(() -> {
            int w = Math.round(trackRef.getWidth() * (pctFinal / 100f));
            ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
            lp.width = w;
            progressFill.setLayoutParams(lp);
        });

        if (mit == null) {
            TextView clear = text("All clear for today", 21, 800, palette.heroInk);
            TextView hint = text("Add a task and it sorts itself to the top.", 13, 600, palette.heroSub);
            card.addView(clear);
            card.addView(hint, matchWrap(0, 6, 0, 0));
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
        boolean heroExpanded = isTaskExpanded("hero", mit.id);
        if (heroExpanded) {
            heroFg.setContentDescription("Expanded content for " + mit.title);
        }
        TextView kicker = text("YOUR #1 RIGHT NOW", 11, 800, accent);
        kicker.setLetterSpacing(0.1f);
        heroFg.addView(kicker, matchWrap(0, 0, 0, 9));

        TextView mitName = text(mit.title, 23, 800, palette.heroInk);
        mitName.setSingleLine(false);
        mitName.setMaxLines(heroExpanded ? Integer.MAX_VALUE : 3);
        mitName.setEllipsize(heroExpanded ? null : TextUtils.TruncateAt.END);
        heroFg.addView(mitName);

        if (heroExpanded && !TextUtils.isEmpty(mit.notes)) {
            TextView notes = text(mit.notes, 13, 500, palette.heroSub);
            notes.setMaxLines(6);
            notes.setEllipsize(TextUtils.TruncateAt.END);
            heroFg.addView(notes, matchWrap(0, 8, 0, 0));
        }

        if (mit.reminderAt > 0) {
            TextView remind = chipText(reminderShort(mit), accent,
                    PriorityPalette.withAlpha(accent, 0x26));
            heroFg.addView(remind, matchWrap(0, 11, 0, 0));
        }
        heroWrap.addView(heroFg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        attachHeroSwipe(heroFg, heroReveal, mit.id);
        card.addView(heroWrap, matchWrap(0, 0, 0, 0));
        heroContainer.addView(card, matchWrap(0, 0, 0, 0));
    }

    private void renderList(List<TodoTask> active, boolean animateReorder) {
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

        if (active.isEmpty()) {
            TextView empty = text("Nothing in the index yet.", 14, 600, palette.sub);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            listContainer.addView(empty, matchWrap(0, 24, 0, 0));
            return;
        }
        int rankTotal = active.size();
        for (int i = 0; i < rankTotal; i++) {
            listContainer.addView(buildRow(active.get(i), i, rankTotal), matchWrap(0, 0, 0, 0));
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

    private FrameLayout buildRow(TodoTask task, int rank, int total) {
        int tier = PriorityPalette.spectrumColor(rank, total, isNightEffective());

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
        fg.setPadding(dp(4), dp(13), dp(4), dp(13));
        fg.setBackgroundColor(palette.surface);
        GradientDrawable bottomLine = new GradientDrawable();
        bottomLine.setColor(palette.surface);
        // hairline divider drawn via a thin bottom view
        CheckBox circle = new CheckBox(this);
        circle.setButtonDrawable(null);
        circle.setText("");
        circle.setMinWidth(0);
        circle.setMinHeight(0);
        circle.setPadding(0, 0, 0, 0);
        circle.setContentDescription("Complete task");
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(PriorityPalette.withAlpha(tier, 0x26));
        circleBg.setStroke(dp(2), tier);
        circle.setBackground(circleBg);
        circle.setClickable(true);
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
        boolean rowExpanded = isTaskExpanded("row", task.id);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        circleParams.rightMargin = dp(14);
        circleParams.gravity = rowExpanded ? Gravity.TOP : Gravity.CENTER_VERTICAL;
        if (rowExpanded) {
            circleParams.topMargin = dp(2);
        }
        fg.addView(circle, circleParams);

        LinearLayout copy = vertical();
        if (rowExpanded) {
            copy.setContentDescription("Expanded content for " + task.title);
        }
        TextView name = text(task.title, 16, 600, palette.ink);
        name.setSingleLine(!rowExpanded);
        name.setMaxLines(rowExpanded ? Integer.MAX_VALUE : 1);
        name.setEllipsize(rowExpanded ? null : TextUtils.TruncateAt.END);
        copy.addView(name);

        if (rowExpanded && !TextUtils.isEmpty(task.notes)) {
            TextView notes = text(task.notes, 13, 500, palette.sub);
            notes.setMaxLines(4);
            notes.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(notes, matchWrap(0, 7, 0, 0));
        }

        if (task.reminderAt > 0) {
            LinearLayout metaRow = horizontal();
            TextView meta = text("⏰ " + reminderShort(task), 11, 600, palette.sub);
            metaRow.addView(meta, wrap(0, 0, 0, 0));
            copy.addView(metaRow, matchWrap(0, 4, 0, 0));
        }
        fg.addView(copy, weight(1, 0, 0, 0, 0));

        wrap.addView(fg, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(palette.line);
        FrameLayout.LayoutParams divParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        divParams.gravity = Gravity.BOTTOM;
        wrap.addView(divider, divParams);

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
        task.completed = true;
        task.snoozed = false;
        ReminderScheduler.cancel(this, task);
        store.save(tasks);
        renderAll(true);
        showToast("Completed", "complete", id);
    }

    private void laterTask(String id) {
        TodoTask task = findTask(id);
        if (task == null || task.snoozed) {
            return;
        }
        task.snoozed = true;
        store.save(tasks);
        renderAll(true);
        showToast("Moved to Later", "later", id);
    }

    private void undoLast() {
        if (toastTaskId == null) {
            return;
        }
        TodoTask task = findTask(toastTaskId);
        if (task != null) {
            if ("complete".equals(toastActionType)) {
                task.completed = false;
                ReminderScheduler.schedule(this, task);
            } else {
                task.snoozed = false;
            }
            store.save(tasks);
            renderAll(true);
        }
        dismissToast();
    }

    // ===================== toast / cheer =====================

    private void showToast(String msg, String type, String id) {
        dismissToast();
        toastTaskId = id;
        toastActionType = type;

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
        undo.setOnClickListener(v -> undoLast());
        bar.addView(undo, wrap(0, 0, 0, 0));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(96);
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
        toastTaskId = null;
        toastActionType = null;
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
        lp.bottomMargin = dp(108);
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

    private boolean inActiveCat(TodoTask task) {
        return "All".equals(activeCat) || activeCat.equals(task.category);
    }

    private List<String> tabList() {
        List<String> out = new ArrayList<>();
        out.add("All");
        out.addAll(categories);
        return out;
    }

    private int catCount(String name) {
        int count = 0;
        for (TodoTask task : tasks) {
            if (!task.completed && ("All".equals(name) || name.equals(task.category))) {
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
        if (addCatDraft != null) {
            catStrip.addView(buildAddCatRow(), matchWrap(0, 0, 0, 0));
            return;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(dp(16), dp(2), dp(16), dp(4));
        List<String> tabs = tabList();
        for (int i = 0; i < tabs.size(); i++) {
            row.addView(buildCatTab(tabs.get(i)), wrap(0, 0, 7, 0));
        }
        row.addView(buildAddCatButton());
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        catStrip.addView(scroll, matchWrap(0, 0, 0, 0));
    }

    private View buildCatTab(String name) {
        boolean active = name.equals(activeCat);
        LinearLayout tab = horizontal();
        tab.setPadding(dp(13), dp(7), dp(13), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(999));
        if (active) {
            bg.setColor(palette.accent);
            bg.setStroke(dp(2), palette.accent);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(2), palette.line);
        }
        tab.setBackground(bg);

        TextView label = text(name, 13, active ? 800 : 700, active ? palette.accentInk : palette.sub);
        label.setIncludeFontPadding(false);
        tab.addView(label, wrap(0, 0, 0, 0));

        int n = catCount(name);
        if (n > 0) {
            TextView badge = text(String.valueOf(n), 10, 800, active ? palette.accentInk : palette.sub);
            badge.setIncludeFontPadding(false);
            badge.setPadding(dp(5), dp(2), dp(5), dp(2));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(999));
            badgeBg.setColor(PriorityPalette.withAlpha(active ? palette.accentInk : palette.sub, 0x2E));
            badge.setBackground(badgeBg);
            tab.addView(badge, wrap(6, 0, 0, 0));
        }

        tab.setOnClickListener(v -> switchCat(name));
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

        final EditText input = new EditText(this);
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
        categories.add(name);
        store.saveCategories(categories);
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
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + name + "\"?")
                .setMessage("The list is removed. Its tasks aren't deleted — they move back to All.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteCat(name))
                .show();
    }

    private void deleteCat(String name) {
        categories.remove(name);
        for (TodoTask task : tasks) {
            if (name.equals(task.category)) {
                task.category = null;
            }
        }
        if (name.equals(activeCat)) {
            activeCat = "All";
        }
        if (sheetOpen && name.equals(draftCategory)) {
            draftCategory = null;
            styleListPill();
        }
        store.saveCategories(categories);
        store.save(tasks);
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
            store.save(tasks);
            store.saveCategories(categories);
            rescheduleFutureReminders();
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
            if (existing == null) {
                tasks.add(imported);
                byId.put(imported.id, imported);
            } else {
                copyTask(imported, existing);
            }
            if (imported.category != null && !hasCategory(imported.category)) {
                categories.add(imported.category);
            }
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
        new AlertDialog.Builder(this)
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
        draftDep = "None";
        draftUrgent = false;
        draftQuick = true;
        draftReminderAt = 0;
        draftRepeatUnit = TodoTask.REPEAT_NONE;
        draftRepeatEvery = 1;
        draftCategory = "All".equals(activeCat) ? null : activeCat;
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
        draftDep = task.dependency;
        draftUrgent = task.urgent;
        draftQuick = task.quickTask;
        draftReminderAt = task.reminderAt;
        draftRepeatUnit = task.reminderRepeatUnit;
        draftRepeatEvery = Math.max(1, task.reminderRepeatEvery);
        draftCategory = task.category;
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
        GradientDrawable gripBg = new GradientDrawable();
        gripBg.setColor(palette.line);
        gripBg.setCornerRadius(dp(2));
        grip.setBackground(gripBg);
        LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        gripParams.gravity = Gravity.CENTER_HORIZONTAL;
        gripParams.topMargin = dp(2);
        gripParams.bottomMargin = dp(14);
        content.addView(grip, gripParams);

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("edit".equals(sheetMode) ? "Edit task" : "New task", 17, 800, palette.ink);
        titleRow.addView(title, weight(1, 0, 0, 12, 0));
        listPill = null;
        if (!categories.isEmpty()) {
            listPill = text("", 12, 700, palette.sub);
            listPill.setGravity(Gravity.CENTER);
            listPill.setSingleLine(true);
            listPill.setMaxWidth(dp(120));
            listPill.setEllipsize(TextUtils.TruncateAt.END);
            listPill.setPadding(dp(12), dp(8), dp(12), dp(8));
            listPill.setOnClickListener(v -> showListPicker());
            titleRow.addView(listPill, wrap(0, 0, 0, 0));
        }
        content.addView(titleRow, matchWrap(0, 0, 0, 13));

        sheetInput = new EditText(this);
        sheetInput.setId(View.generateViewId());
        sheetInput.setHint("What needs doing?");
        sheetInput.setText(draftName);
        sheetInput.setSelection(sheetInput.length());
        sheetInput.setSingleLine(true);
        sheetInput.setImeOptions(notesExpanded ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        sheetInput.setTextSize(16);
        sheetInput.setTextColor(palette.ink);
        sheetInput.setHintTextColor(palette.sub);
        sheetInput.setPadding(dp(15), dp(14), dp(15), dp(14));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(palette.bg);
        inputBg.setCornerRadius(dp(14));
        inputBg.setStroke(dp(2), palette.line);
        sheetInput.setBackground(inputBg);
        sheetInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                draftName = s.toString();
                updateSheetDynamic();
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
        content.addView(sheetInput, matchWrap(0, 0, 0, 0));
        if (listPill != null) {
            listPill.setAccessibilityTraversalAfter(sheetInput.getId());
        }

        addDetailsRow = text("＋  Add details", 13, 700, palette.sub);
        addDetailsRow.setGravity(Gravity.CENTER_VERTICAL);
        addDetailsRow.setMinHeight(dp(48));
        addDetailsRow.setPadding(dp(15), 0, dp(15), 0);
        addDetailsRow.setContentDescription("Add task details");
        addDetailsRow.setOnClickListener(v -> expandNotesEditor());
        addDetailsRow.setVisibility(notesExpanded ? View.GONE : View.VISIBLE);
        content.addView(addDetailsRow, matchWrap(0, 4, 0, 0));

        notesInput = new EditText(this);
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
        notesInput.setPadding(dp(15), dp(8), dp(15), dp(12));
        notesInput.setBackgroundTintList(ColorStateList.valueOf(palette.line));
        notesInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                draftNotes = s.toString();
                keepNotesCaretVisible();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        notesInput.setVisibility(notesExpanded ? View.VISIBLE : View.GONE);
        content.addView(notesInput, matchWrap(0, 0, 0, 0));

        LinearLayout reminderRow = horizontal();
        remindChip = text("", 13, 700, palette.sub);
        remindChip.setGravity(Gravity.CENTER_VERTICAL);
        remindChip.setPadding(dp(14), dp(10), dp(14), dp(10));
        remindChip.setOnClickListener(v -> openReminderPicker());
        reminderRow.addView(remindChip, wrap(0, 0, 8, 0));
        remindClear = text("Clear", 12, 700, palette.sub);
        remindClear.setGravity(Gravity.CENTER);
        remindClear.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(Color.TRANSPARENT);
        clearBg.setCornerRadius(dp(12));
        clearBg.setStroke(dp(2), palette.line);
        remindClear.setBackground(clearBg);
        remindClear.setOnClickListener(v -> {
            draftReminderAt = 0;
            draftRepeatUnit = TodoTask.REPEAT_NONE;
            draftRepeatEvery = 1;
            updateSheetDynamic();
        });
        reminderRow.addView(remindClear, wrap(0, 0, 0, 0));
        content.addView(reminderRow, wrap(0, 12, 0, 0));

        reminderRepeatRow = horizontal();
        content.addView(reminderRepeatRow, matchWrap(0, 8, 0, 0));

        detailsToggle = text("", 12, 800, palette.accentInk);
        LinearLayout toggleRow = horizontal();
        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(palette.bg);
        toggleBg.setCornerRadius(dp(12));
        toggleBg.setStroke(dp(2), PriorityPalette.withAlpha(palette.accent, 0x77));
        toggleRow.setBackground(toggleBg);
        toggleRow.setPadding(dp(14), dp(11), dp(14), dp(11));
        toggleRow.setElevation(dp(3));
        toggleRow.setOnClickListener(v -> toggleDetailsAnimated());
        detailsSummary = text("", 12, 600, palette.sub);
        toggleRow.addView(detailsSummary, weight(1, 0, 0, 12, 0));
        toggleRow.addView(detailsToggle, wrap(0, 0, 0, 0));
        content.addView(toggleRow, matchWrap(0, 12, 0, 0));

        chipsContainer = vertical();
        content.addView(chipsContainer, matchWrap(0, 12, 0, 0));

        if ("edit".equals(sheetMode)) {
            TextView del = text("Delete task", 13, 700, PriorityPalette.IMMEDIATE);
            del.setGravity(Gravity.CENTER);
            del.setPadding(dp(10), dp(12), dp(10), dp(4));
            del.setOnClickListener(v -> confirmDelete(sheetEditId));
            content.addView(del, matchWrap(0, 6, 0, 0));
        }
    }

    private LinearLayout buildSheetActions() {
        LinearLayout actions = horizontal();
        commitButton = text("", 15, 800, palette.accentInk);
        commitButton.setGravity(Gravity.CENTER);
        commitButton.setPadding(dp(15), dp(15), dp(15), dp(15));
        GradientDrawable commitBg = new GradientDrawable();
        commitBg.setColor(palette.accent);
        commitBg.setCornerRadius(dp(16));
        commitButton.setBackground(commitBg);
        commitButton.setOnClickListener(v -> commitSheet());
        actions.addView(commitButton, weight(1, 0, 0, 10, 0));

        TextView cancel = text("Cancel", 15, 700, palette.sub);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(18), dp(15), dp(18), dp(15));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.TRANSPARENT);
        cancelBg.setCornerRadius(dp(16));
        cancelBg.setStroke(dp(2), palette.line);
        cancel.setBackground(cancelBg);
        cancel.setOnClickListener(v -> closeSheet());
        actions.addView(cancel, wrap(0, 0, 0, 0));
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
        if (listPill == null || categories.isEmpty()) {
            return;
        }
        String[] choices = new String[categories.size() + 1];
        choices[0] = "No list";
        int checked = 0;
        for (int i = 0; i < categories.size(); i++) {
            choices[i + 1] = categories.get(i);
            if (categories.get(i).equals(draftCategory)) {
                checked = i + 1;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Move to list")
                .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                    draftCategory = which == 0 ? null : categories.get(which - 1);
                    styleListPill();
                    String destination = listPillLabel(draftCategory);
                    listPill.announceForAccessibility("Moved to " + destination);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void styleListPill() {
        if (listPill == null) {
            return;
        }
        String label = listPillLabel(draftCategory);
        boolean assigned = draftCategory != null && !draftCategory.trim().isEmpty();
        listPill.setText(label + "  ▾");
        listPill.setTextColor(assigned ? palette.accent : palette.sub);
        listPill.setContentDescription("List: " + label + ". Double tap to change.");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.bg);
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(2), assigned ? palette.accent : palette.line);
        listPill.setBackground(bg);
    }

    private void updateSheetDynamic() {
        List<String> parts = new ArrayList<>();
        parts.add(impactLabel(draftImpact) + " impact");
        parts.add(impactLabel(draftEffort) + " effort");
        if (!"None".equals(draftDep)) {
            parts.add(draftDep);
        }
        if (draftQuick) {
            parts.add("Quick win: fast");
        }
        if (draftUrgent) {
            parts.add("Urgent: top");
        }
        detailsSummary.setText("Assumed: " + TextUtils.join("  ·  ", parts));
        detailsToggle.setText(detailsExpanded ? "Done" : "Adjust");
        styleAdjustButton();
        styleListPill();

        boolean hasReminder = draftReminderAt > 0;
        remindChip.setText(hasReminder ? "⏰  " + reminderShortFromMillis(draftReminderAt) : "⏰  Add reminder");
        styleReminderChip(hasReminder);
        if (remindClear != null) {
            remindClear.setVisibility(hasReminder ? View.VISIBLE : View.GONE);
        }

        reminderRepeatRow.removeAllViews();
        if (draftReminderAt > 0) {
            buildRepeatControls(reminderRepeatRow);
        }

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

        boolean ready = draftName.trim().length() > 0;
        commitButton.setText("edit".equals(sheetMode) ? "Save" : "Add task");
        commitButton.setAlpha(ready ? 1f : 0.4f);
        commitButton.setEnabled(ready);
    }

    private void styleAdjustButton() {
        if (detailsToggle == null) {
            return;
        }
        detailsToggle.setTextColor(detailsExpanded ? palette.accent : palette.accentInk);
        detailsToggle.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(999));
        if (detailsExpanded) {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(2), palette.accent);
            detailsToggle.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
            detailsToggle.setElevation(0f);
            stopAdjustPulse();
        } else {
            bg.setColor(palette.accent);
            bg.setStroke(dp(2), PriorityPalette.withAlpha(0xFFFFFFFF, 0x99));
            detailsToggle.setShadowLayer(dp(4), 0, 0, PriorityPalette.withAlpha(palette.accent, 0xAA));
            detailsToggle.setElevation(dp(4));
            if ("add".equals(sheetMode)) {
                startAdjustPulse();
            } else {
                stopAdjustPulse();
            }
        }
        detailsToggle.setBackground(bg);
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
        applyFont(remindChip, 700);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (active) {
            bg.setColor(PriorityPalette.withAlpha(palette.accent, 0x1F));
            bg.setStroke(dp(2), palette.accent);
            remindChip.setTextColor(palette.accent);
        } else {
            bg.setColor(palette.bg);
            bg.setStroke(dp(2), palette.line);
            remindChip.setTextColor(palette.sub);
        }
        remindChip.setBackground(bg);
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
        container.addView(chipGroupLabel("IMPACT"));
        container.addView(impactEffortRow("impact"), matchWrap(0, 8, 0, 15));
        container.addView(chipGroupLabel("EFFORT"));
        container.addView(impactEffortRow("effort"), matchWrap(0, 8, 0, 15));
        container.addView(chipGroupLabel("DEPENDENCY"));
        container.addView(depRow(), matchWrap(0, 8, 0, 15));

        LinearLayout flags = horizontal();
        TextView urgent = chipButton("Urgent: do now", draftUrgent, PriorityPalette.IMMEDIATE, 0xFFFFFFFF);
        urgent.setOnClickListener(v -> {
            draftUrgent = !draftUrgent;
            updateSheetDynamic();
        });
        TextView quick = chipButton("Quick win: low effort", draftQuick, palette.accent, palette.accentInk);
        quick.setOnClickListener(v -> {
            draftQuick = !draftQuick;
            updateSheetDynamic();
        });
        flags.addView(urgent, wrap(0, 0, 8, 0));
        flags.addView(quick, wrap(0, 0, 0, 0));
        container.addView(flags, matchWrap(0, 0, 0, 0));
    }

    private LinearLayout impactEffortRow(String field) {
        LinearLayout row = horizontal();
        String[][] opts = {{"High", TodoTask.HIGH}, {"Medium", TodoTask.MEDIUM}, {"Low", TodoTask.LOW}};
        for (int i = 0; i < opts.length; i++) {
            String label = opts[i][0];
            String val = opts[i][1];
            boolean active = "impact".equals(field) ? draftImpact.equals(val) : draftEffort.equals(val);
            int color = valueColor(val);
            int ink = valueInk(val);
            TextView chip = chipButton(label, active, color, ink);
            chip.setOnClickListener(v -> {
                if ("impact".equals(field)) {
                    draftImpact = val;
                } else {
                    draftEffort = val;
                }
                updateSheetDynamic();
            });
            row.addView(chip, wrap(0, 0, i < opts.length - 1 ? 8 : 0, 0));
        }
        return row;
    }

    private LinearLayout depRow() {
        LinearLayout row = horizontal();
        for (int i = 0; i < DEPENDENCIES.length; i++) {
            String val = DEPENDENCIES[i];
            boolean active = draftDep.equals(val);
            TextView chip = chipButton(val, active, PriorityPalette.DEP_PURPLE, 0xFFFFFFFF);
            chip.setOnClickListener(v -> {
                draftDep = val;
                updateSheetDynamic();
            });
            row.addView(chip, wrap(0, 0, i < DEPENDENCIES.length - 1 ? 8 : 0, 0));
        }
        return row;
    }

    private void buildRepeatControls(LinearLayout row) {
        Spinner unit = new Spinner(this);
        unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, REPEAT_UNITS));
        unit.setSelection(repeatUnitIndex(draftRepeatUnit));
        unit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                draftRepeatUnit = repeatUnitFromIndex(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        row.addView(unit, weight(1, 0, 0, 8, 0));

        EditText every = new EditText(this);
        every.setText(String.valueOf(Math.max(1, draftRepeatEvery)));
        every.setInputType(InputType.TYPE_CLASS_NUMBER);
        every.setTextColor(palette.ink);
        every.setSingleLine(true);
        every.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                try {
                    draftRepeatEvery = Math.max(1, Integer.parseInt(s.toString().trim()));
                } catch (NumberFormatException ignored) {
                    draftRepeatEvery = 1;
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        row.addView(every, new LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void openReminderPicker() {
        Calendar calendar = Calendar.getInstance();
        if (draftReminderAt > 0) {
            calendar.setTimeInMillis(draftReminderAt);
        } else {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
        }
        DatePickerDialog datePicker = new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog timePicker = new TimePickerDialog(this, (tv, hour, minute) -> {
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
        if (repeats && draftReminderAt == 0) {
            draftRepeatUnit = TodoTask.REPEAT_NONE;
        }
        if (draftReminderAt > 0 && draftReminderAt <= System.currentTimeMillis()) {
            draftReminderAt = 0;
            draftRepeatUnit = TodoTask.REPEAT_NONE;
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
        task.dependency = draftDep;
        task.category = draftCategory;
        task.urgent = draftUrgent;
        task.quickTask = draftQuick;
        task.reminderAt = draftReminderAt;
        task.reminderRepeatUnit = draftReminderAt > 0 ? draftRepeatUnit : TodoTask.REPEAT_NONE;
        task.reminderRepeatEvery = TodoTask.REPEAT_NONE.equals(task.reminderRepeatUnit)
                ? 1 : Math.max(1, draftRepeatEvery);
        if (isNew) {
            tasks.add(task);
        }
        ReminderScheduler.schedule(this, task);
        store.save(tasks);
        closeSheet();
        renderAll(true);
        if (isNew) {
            showCheer("Let's go!");
        }
    }

    private void confirmDelete(String id) {
        TodoTask task = findTask(id);
        if (task == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete task?")
                .setMessage("This removes \"" + task.title + "\" from your priority index.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ReminderScheduler.cancel(this, task);
                    tasks.remove(task);
                    store.save(tasks);
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
        sheetOpen = false;
    }

    private void removeSheetImmediate() {
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
        if (reachArc != null) {
            reachArc.animate().cancel();
            reachArc.setAlpha(0f);
        }
    }

    private void restoreActZone() {
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
        SheetKeyboardFrame frame = sheetKeyboardFrame(
                root.getHeight(), keyboardInsetPx, dp(8), dp(12), dp(180));
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
        View target = sheetInput;
        sheetScroll.post(() -> sheetScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(12))));
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

    private int repeatUnitIndex(String unit) {
        if (TodoTask.REPEAT_DAY.equals(unit)) {
            return 1;
        }
        if (TodoTask.REPEAT_WEEK.equals(unit)) {
            return 2;
        }
        if (TodoTask.REPEAT_MONTH.equals(unit)) {
            return 3;
        }
        return 0;
    }

    private String repeatUnitFromIndex(int index) {
        if (index == 1) {
            return TodoTask.REPEAT_DAY;
        }
        if (index == 2) {
            return TodoTask.REPEAT_WEEK;
        }
        if (index == 3) {
            return TodoTask.REPEAT_MONTH;
        }
        return TodoTask.REPEAT_NONE;
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

    private List<TodoTask> activeTasks() {
        List<TodoTask> out = new ArrayList<>();
        for (TodoTask task : tasks) {
            if (!task.completed && inActiveCat(task)) {
                out.add(task);
            }
        }
        Collections.sort(out, new Comparator<TodoTask>() {
            @Override
            public int compare(TodoTask first, TodoTask second) {
                int byScore = Double.compare(second.score(), first.score());
                if (byScore != 0) {
                    return byScore;
                }
                return Long.compare(first.createdAt, second.createdAt);
            }
        });
        return out;
    }

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

    private void rescheduleFutureReminders() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (TodoTask task : tasks) {
            if (!task.completed && task.repeatsReminder() && task.reminderAt <= now) {
                long next = task.nextReminderAfter(now);
                if (next > 0) {
                    task.reminderAt = next;
                    changed = true;
                }
            }
            ReminderScheduler.schedule(this, task);
        }
        if (changed) {
            store.save(tasks);
        }
    }

    private final class HeaderIconButton extends View {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final Path path = new Path();
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
            if ("more".equals(kind)) {
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
            int size = dp(36);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Borderless "quiet line icons" treatment (Today.dc.html, option 1a):
            // no ring/background behind each icon — the glyphs sit bare in the header.
            if ("hand".equals(kind)) {
                drawHand(canvas);
            } else if ("more".equals(kind)) {
                drawMore(canvas);
            } else {
                drawTheme(canvas);
            }
        }

        private void drawMore(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float unit = getWidth() / 36f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.accent);
            float r = unit * 1.7f;
            float gap = unit * 5.4f;
            canvas.drawCircle(cx, cy - gap, r, paint);
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawCircle(cx, cy + gap, r, paint);
        }

        private void drawHand(Canvas canvas) {
            float center = getWidth() / 2f;
            float scale = getWidth() / 36f;
            canvas.save();
            if (HAND_LEFT.equals(handState)) {
                canvas.scale(-1f, 1f, center, center);
            } else if (HAND_CENTER.equals(handState)) {
                canvas.translate(-2f * scale, 0f);
            }
            canvas.translate(6f * scale, 6f * scale);
            canvas.scale(scale, scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(1.55f);
            paint.setColor(palette.accent);
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
        }

        private void drawTheme(Canvas canvas) {
            if (THEME_NIGHT.equals(themeState)) {
                drawMoon(canvas);
            } else if (THEME_SYSTEM.equals(themeState)) {
                drawAuto(canvas);
            } else {
                drawSun(canvas, getWidth() / 2f, getHeight() / 2f, 1f);
            }
        }

        private void drawSun(Canvas canvas, float cx, float cy, float scale) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(getWidth() / 36f * 1.55f);
            paint.setColor(palette.accent);
            float unit = getWidth() / 36f;
            float radius = unit * 4.2f * scale;
            canvas.drawCircle(cx, cy, radius, paint);
            float inner = unit * 9.2f * scale;
            float outer = unit * 12.5f * scale;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4d;
                float x1 = cx + (float) Math.cos(angle) * inner;
                float y1 = cy + (float) Math.sin(angle) * inner;
                float x2 = cx + (float) Math.cos(angle) * outer;
                float y2 = cy + (float) Math.sin(angle) * outer;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }

        private void drawMoon(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float unit = getWidth() / 36f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.accent);
            canvas.drawCircle(cx - unit * 1.8f, cy, unit * 7.8f, paint);
            paint.setColor(palette.surface);
            canvas.drawCircle(cx + unit * 2.2f, cy - unit * 2.3f, unit * 7.8f, paint);
        }

        private void drawAuto(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float unit = getWidth() / 36f;
            drawSun(canvas, cx, cy, 0.62f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(unit * 1.45f);
            paint.setColor(palette.accent);
            oval.set(cx - unit * 12.5f, cy - unit * 12.5f, cx + unit * 12.5f, cy + unit * 12.5f);
            canvas.drawArc(oval, 210, 245, false, paint);
            path.reset();
            path.moveTo(cx - unit * 10.4f, cy + unit * 9.4f);
            path.lineTo(cx - unit * 13.9f, cy + unit * 10.3f);
            path.lineTo(cx - unit * 12.1f, cy + unit * 6.9f);
            canvas.drawPath(path, paint);
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
