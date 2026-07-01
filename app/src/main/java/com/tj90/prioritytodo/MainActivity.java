package com.tj90.prioritytodo;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

public final class MainActivity extends Activity {
    private static final String UI_PREFS = "priority_todo_ui";
    private static final String LEGACY_NIGHT_MODE = "night_mode";
    private static final String KEY_THEME = "theme";
    private static final String KEY_HAND = "hand";

    private static final String THEME_DAY = "day";
    private static final String THEME_NIGHT = "night";
    private static final String THEME_SYSTEM = "system";
    private static final String HAND_RIGHT = "right";
    private static final String HAND_LEFT = "left";

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
    private String draftImpact = TodoTask.LOW;
    private String draftEffort = TodoTask.LOW;
    private String draftDep = "None";
    private boolean draftUrgent;
    private boolean draftQuick = true;
    private long draftReminderAt;
    private String draftRepeatUnit = TodoTask.REPEAT_NONE;
    private int draftRepeatEvery = 1;
    private boolean detailsExpanded;

    private FrameLayout sheetOverlay;
    private View sheetScrim;
    private LinearLayout sheetPanel;
    private EditText sheetInput;
    private TextView landsPill;
    private TextView detailsSummary;
    private TextView detailsToggle;
    private TextView remindChip;
    private LinearLayout reminderRepeatRow;
    private LinearLayout chipsContainer;
    private TextView commitButton;
    private boolean detailsAnimating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new TaskStore(this);
        loadPrefs();
        palette = activePalette();
        tasks.addAll(store.load());
        requestNotificationPermission();
        createNotificationChannel();
        rescheduleFutureReminders();
        buildChrome();
        renderAll(false);
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
        hand = HAND_RIGHT.equals(hand) ? HAND_LEFT : HAND_RIGHT;
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
        header.setPadding(dp(24), dp(18), dp(20), dp(4));
        header.setGravity(Gravity.TOP);

        LinearLayout titleCol = vertical();
        TextView today = text("Today", 23, 800, palette.ink);
        today.setIncludeFontPadding(false);
        headerSubView = text("", 13, 600, palette.sub);
        titleCol.addView(today);
        titleCol.addView(headerSubView, matchWrap(0, 5, 0, 0));
        header.addView(titleCol, weight(1, 0, 0, 0, 0));

        LinearLayout toggles = horizontal();
        handPill = new HeaderIconButton(this, "hand");
        handPill.setOnClickListener(v -> toggleHand());
        themePill = new HeaderIconButton(this, "theme");
        themePill.setOnClickListener(v -> cycleTheme());
        toggles.addView(handPill, wrap(0, 0, 6, 0));
        toggles.addView(themePill, wrap(0, 0, 0, 0));
        header.addView(toggles, wrap(0, 0, 0, 0));
        return header;
    }

    private void addActZone() {
        reachArc = new View(this);
        GradientDrawable arc = new GradientDrawable();
        arc.setShape(GradientDrawable.OVAL);
        arc.setColor(Color.TRANSPARENT);
        arc.setStroke(dp(2), PriorityPalette.withAlpha(palette.accent, 0x52), dp(8), dp(8));
        reachArc.setBackground(arc);
        FrameLayout.LayoutParams arcParams = new FrameLayout.LayoutParams(dp(280), dp(280));
        arcParams.gravity = Gravity.BOTTOM | (HAND_RIGHT.equals(hand) ? Gravity.END : Gravity.START);
        arcParams.bottomMargin = dp(-118);
        if (HAND_RIGHT.equals(hand)) {
            arcParams.rightMargin = dp(-118);
        } else {
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
        fabParams.gravity = Gravity.BOTTOM | (HAND_RIGHT.equals(hand) ? Gravity.END : Gravity.START);
        fabParams.bottomMargin = dp(24);
        if (HAND_RIGHT.equals(hand)) {
            fabParams.rightMargin = dp(20);
        } else {
            fabParams.leftMargin = dp(20);
        }
        root.addView(fab, fabParams);

        fabTarget = new View(this);
        fabTarget.setContentDescription("Add task");
        fabTarget.setOnClickListener(v -> openAddSheet());
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

    // ===================== rendering =====================

    private void renderAll(boolean animateReorder) {
        sortTasks();
        List<TodoTask> active = activeTasks();
        int total = tasks.size();
        int remaining = active.size();
        int done = total - remaining;
        int pct = total == 0 ? 0 : Math.round((done / (float) total) * 100f);

        TodoTask mit = active.isEmpty() ? null : active.get(0);
        String mitCat = mit == null ? PriorityPalette.IMMEDIATE_LABEL : PriorityPalette.bucket(mit.score());

        headerSubView.setText(remaining + " to go  ·  " + done + " done");
        if (handPill != null) {
            handPill.setState(hand, theme);
        }
        if (themePill != null) {
            themePill.setState(hand, theme);
        }

        renderHero(mit, mitCat, pct, done, total);
        renderList(active, animateReorder);
    }

    private void renderHero(TodoTask mit, String mitCat, int pct, int done, int total) {
        heroContainer.removeAllViews();
        LinearLayout card = vertical();
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.surface);
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(2), PriorityPalette.catOutline(mitCat));
        card.setBackground(bg);

        LinearLayout progressRow = horizontal();
        FrameLayout track = new FrameLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(PriorityPalette.withAlpha(palette.heroInk, 0x17));
        trackBg.setCornerRadius(dp(3));
        track.setBackground(trackBg);
        progressFill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(PriorityPalette.catColor(mitCat));
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
        LinearLayout pillRow = horizontal();
        TextView catPill = categoryPill(mitCat);
        TextView kicker = text("YOUR #1 RIGHT NOW", 11, 700, palette.heroSub);
        kicker.setLetterSpacing(0.1f);
        pillRow.addView(catPill, wrap(0, 0, 8, 0));
        pillRow.addView(kicker, wrap(0, 0, 0, 0));
        heroFg.addView(pillRow, matchWrap(0, 0, 0, 9));

        TextView mitName = text(mit.title, 23, 800, palette.heroInk);
        mitName.setSingleLine(false);
        mitName.setMaxLines(3);
        mitName.setEllipsize(TextUtils.TruncateAt.END);
        heroFg.addView(mitName);

        if (mit.reminderAt > 0) {
            TextView remind = chipText(reminderShort(mit), PriorityPalette.catColor(mitCat),
                    PriorityPalette.catSoft(mitCat));
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
        for (TodoTask task : active) {
            listContainer.addView(buildRow(task), matchWrap(0, 0, 0, 0));
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

    private FrameLayout buildRow(TodoTask task) {
        String cat = PriorityPalette.bucket(task.score());
        int tier = PriorityPalette.catColor(cat);

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
        circleBg.setColor(PriorityPalette.catSoft(cat));
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
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        circleParams.rightMargin = dp(14);
        circleParams.gravity = Gravity.CENTER_VERTICAL;
        fg.addView(circle, circleParams);

        LinearLayout copy = vertical();
        TextView name = text(task.title, 16, 600, palette.ink);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);

        LinearLayout metaRow = horizontal();
        TextView catLabel = text(cat.toUpperCase(), 10, 800, tier);
        catLabel.setLetterSpacing(0.07f);
        metaRow.addView(catLabel, wrap(0, 0, 8, 0));
        if (task.reminderAt > 0) {
            TextView meta = text("⏰ " + reminderShort(task), 11, 600, palette.sub);
            metaRow.addView(meta, wrap(0, 0, 0, 0));
        }
        copy.addView(metaRow, matchWrap(0, 4, 0, 0));
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

    // ===================== gestures =====================

    private int completeDir() {
        return HAND_RIGHT.equals(hand) ? 1 : -1;
    }

    private void attachRowSwipe(View fg, TextView reveal, String id) {
        final float[] startX = new float[1];
        final boolean[] moved = new boolean[1];
        final Runnable[] longPress = new Runnable[1];
        fg.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    moved[0] = false;
                    longPress[0] = () -> {
                        if (!moved[0]) {
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
        fg.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - startX[0];
                    if (!moved[0]) {
                        if (Math.abs(dx) > dp(6)) {
                            moved[0] = true;
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
                    float dx = event.getRawX() - startX[0];
                    if (moved[0] && Math.abs(dx) >= dp(72)) {
                        int dir = dx > 0 ? 1 : -1;
                        if (dir == completeDir()) {
                            completeTask(id);
                        } else {
                            laterTask(id);
                        }
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

    // ===================== sheet =====================

    private void openAddSheet() {
        sheetMode = "add";
        sheetEditId = null;
        draftName = "";
        draftImpact = TodoTask.LOW;
        draftEffort = TodoTask.LOW;
        draftDep = "None";
        draftUrgent = false;
        draftQuick = true;
        draftReminderAt = 0;
        draftRepeatUnit = TodoTask.REPEAT_NONE;
        draftRepeatEvery = 1;
        detailsExpanded = false;
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
        draftImpact = task.impact;
        draftEffort = task.effort;
        draftDep = task.dependency;
        draftUrgent = task.urgent;
        draftQuick = task.quickTask;
        draftReminderAt = task.reminderAt;
        draftRepeatUnit = task.reminderRepeatUnit;
        draftRepeatEvery = Math.max(1, task.reminderRepeatEvery);
        detailsExpanded = true;
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

        sheetPanel = vertical();
        sheetPanel.setPadding(dp(18), dp(8), dp(18), dp(18));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(palette.surface);
        panelBg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        sheetPanel.setBackground(panelBg);
        sheetPanel.setElevation(dp(26));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM;

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = vertical();
        buildSheetContent(content);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        sheetPanel.addView(scroll, matchWrap(0, 0, 0, 0));

        sheetOverlay.addView(sheetPanel, panelParams);
        root.addView(sheetOverlay, overlayParams);

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
        TextView title = text("edit".equals(sheetMode) ? "Edit task" : "New task", 17, 800, palette.ink);
        titleRow.addView(title, weight(1, 0, 0, 0, 0));
        TextView landsLabel = text("Lands in", 11, 700, palette.sub);
        titleRow.addView(landsLabel, wrap(0, 0, 7, 0));
        landsPill = categoryPill(predictBucket());
        titleRow.addView(landsPill, wrap(0, 0, 0, 0));
        content.addView(titleRow, matchWrap(0, 0, 0, 13));

        sheetInput = new EditText(this);
        sheetInput.setHint("What needs doing?");
        sheetInput.setText(draftName);
        sheetInput.setSingleLine(true);
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
        content.addView(sheetInput, matchWrap(0, 0, 0, 0));

        remindChip = text("", 13, 700, palette.sub);
        remindChip.setPadding(dp(4), dp(8), dp(4), dp(8));
        remindChip.setOnClickListener(v -> openReminderPicker());
        content.addView(remindChip, wrap(0, 12, 0, 0));

        reminderRepeatRow = horizontal();
        content.addView(reminderRepeatRow, matchWrap(0, 6, 0, 0));

        detailsToggle = text("", 12, 800, palette.accent);
        LinearLayout toggleRow = horizontal();
        GradientDrawable toggleBg = new GradientDrawable();
        toggleBg.setColor(palette.bg);
        toggleBg.setCornerRadius(dp(12));
        toggleRow.setBackground(toggleBg);
        toggleRow.setPadding(dp(14), dp(11), dp(14), dp(11));
        toggleRow.setOnClickListener(v -> toggleDetailsAnimated());
        detailsSummary = text("", 12, 600, palette.sub);
        toggleRow.addView(detailsSummary, weight(1, 0, 0, 12, 0));
        toggleRow.addView(detailsToggle, wrap(0, 0, 0, 0));
        content.addView(toggleRow, matchWrap(0, 12, 0, 0));

        chipsContainer = vertical();
        content.addView(chipsContainer, matchWrap(0, 12, 0, 0));

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
        content.addView(actions, matchWrap(0, 18, 0, 0));

        if ("edit".equals(sheetMode)) {
            TextView del = text("Delete task", 13, 700, PriorityPalette.IMMEDIATE);
            del.setGravity(Gravity.CENTER);
            del.setPadding(dp(10), dp(12), dp(10), dp(4));
            del.setOnClickListener(v -> confirmDelete(sheetEditId));
            content.addView(del, matchWrap(0, 6, 0, 0));
        }

        updateSheetDynamic();
    }

    private void updateSheetDynamic() {
        String bucket = predictBucket();
        styleCategoryPill(landsPill, bucket);

        List<String> parts = new ArrayList<>();
        parts.add(impactLabel(draftImpact) + " impact");
        parts.add(impactLabel(draftEffort) + " effort");
        if (!"None".equals(draftDep)) {
            parts.add(draftDep);
        }
        if (draftQuick) {
            parts.add("Quick win");
        }
        if (draftUrgent) {
            parts.add("Urgent");
        }
        detailsSummary.setText("Assumed: " + TextUtils.join("  ·  ", parts));
        detailsToggle.setText(detailsExpanded ? "Done" : "Adjust");

        remindChip.setText(draftReminderAt > 0 ? "⏰ " + reminderShortFromMillis(draftReminderAt) : "⏰ Add reminder");
        remindChip.setTextColor(draftReminderAt > 0 ? palette.accent : palette.sub);

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
        TextView urgent = chipButton("Urgent", draftUrgent, PriorityPalette.SOMEDAY, 0xFFFFFFFF);
        urgent.setOnClickListener(v -> {
            draftUrgent = !draftUrgent;
            updateSheetDynamic();
        });
        TextView quick = chipButton("Quick win", draftQuick, palette.accent, palette.accentInk);
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
        task.impact = draftImpact;
        task.effort = draftEffort;
        task.dependency = draftDep;
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
        if (sheetOverlay == null) {
            sheetOpen = false;
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && sheetInput != null) {
            imm.hideSoftInputFromWindow(sheetInput.getWindowToken(), 0);
        }
        final FrameLayout overlay = sheetOverlay;
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
        if (sheetOverlay != null) {
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
            if (!task.completed) {
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
            if ("hand".equals(kind)) {
                setContentDescription(HAND_RIGHT.equals(handState) ? "Right hand" : "Left hand");
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
            float stroke = dp(1);
            oval.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.surface);
            canvas.drawOval(oval, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(palette.line);
            canvas.drawOval(oval, paint);
            if ("hand".equals(kind)) {
                drawHand(canvas);
            } else {
                drawTheme(canvas);
            }
        }

        private void drawHand(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.save();
            if (HAND_LEFT.equals(handState)) {
                canvas.scale(-1f, 1f, cx, cy);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2));
            paint.setColor(palette.sub);
            canvas.drawLine(cx - dp(7), cy + dp(2), cx - dp(7), cy - dp(6), paint);
            canvas.drawLine(cx - dp(2), cy + dp(1), cx - dp(2), cy - dp(10), paint);
            canvas.drawLine(cx + dp(3), cy + dp(1), cx + dp(3), cy - dp(8), paint);
            canvas.drawLine(cx + dp(8), cy + dp(4), cx + dp(8), cy - dp(3), paint);
            path.reset();
            path.moveTo(cx - dp(12), cy + dp(2));
            path.quadTo(cx - dp(17), cy - dp(3), cx - dp(12), cy - dp(7));
            path.quadTo(cx - dp(9), cy - dp(2), cx - dp(8), cy + dp(7));
            path.quadTo(cx - dp(6), cy + dp(15), cx + dp(2), cy + dp(16));
            path.quadTo(cx + dp(11), cy + dp(15), cx + dp(12), cy + dp(5));
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
            paint.setStrokeWidth(dp(2));
            paint.setColor(palette.sub);
            float radius = dp(5) * scale;
            canvas.drawCircle(cx, cy, radius, paint);
            float inner = dp(11) * scale;
            float outer = dp(15) * scale;
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
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.sub);
            canvas.drawCircle(cx - dp(2), cy, dp(10), paint);
            paint.setColor(palette.surface);
            canvas.drawCircle(cx + dp(3), cy - dp(3), dp(10), paint);
        }

        private void drawAuto(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            drawSun(canvas, cx, cy, 0.72f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(2));
            paint.setColor(palette.sub);
            oval.set(cx - dp(15), cy - dp(15), cx + dp(15), cy + dp(15));
            canvas.drawArc(oval, 205, 250, false, paint);
            path.reset();
            path.moveTo(cx - dp(13), cy + dp(11));
            path.lineTo(cx - dp(17), cy + dp(12));
            path.lineTo(cx - dp(15), cy + dp(8));
            canvas.drawPath(path, paint);
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
