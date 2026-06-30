package com.tj90.prioritytodo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String[] DEPENDENCIES = {"None", "Sequential", "Reciprocal", "Pooled"};
    private static final String[] REPEAT_UNITS = {"No repeat", "Day", "Week", "Month"};
    private static final String UI_PREFS = "priority_todo_ui";
    private static final String NIGHT_MODE = "night_mode";
    private static final int RED = 0xFFC1121F;

    private final List<TodoTask> tasks = new ArrayList<>();

    private TaskStore store;
    private TextView formTitleView;
    private EditText titleInput;
    private EditText notesInput;
    private RadioGroup impactGroup;
    private RadioGroup effortGroup;
    private Spinner dependencySpinner;
    private CheckBox urgentInput;
    private CheckBox quickInput;
    private TextView reminderLabel;
    private EditText repeatEveryInput;
    private Spinner repeatUnitSpinner;
    private TextView recurrenceHint;
    private LinearLayout detailsSection;
    private Button detailsToggleButton;
    private Button saveButton;
    private Button resetButton;
    private LinearLayout mitCard;
    private TextView mitKickerView;
    private TextView mitTitleView;
    private TextView mitMetaView;
    private LinearLayout taskList;
    private long selectedReminderAt;
    private String editingId;
    private boolean nightMode;
    private boolean detailsExpanded;
    private boolean formOpen;
    private Palette palette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new TaskStore(this);
        nightMode = getSharedPreferences(UI_PREFS, MODE_PRIVATE).getBoolean(NIGHT_MODE, false);
        palette = Palette.from(nightMode);
        tasks.addAll(store.load());
        requestNotificationPermission();
        createNotificationChannel();
        rescheduleFutureReminders();
        buildUi();
        renderTasks();
    }

    private void buildUi() {
        palette = Palette.from(nightMode);
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        getWindow().getDecorView().setSystemUiVisibility(nightMode ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout screen = vertical();
        screen.setBackgroundColor(palette.background);

        LinearLayout topInfo = vertical();
        topInfo.setPadding(dp(24), dp(24), dp(24), dp(14));
        topInfo.setBackgroundColor(palette.background);

        LinearLayout headerCopy = vertical();
        TextView header = text("Priority Todo", 31, true);
        header.setTextColor(palette.primaryText);
        header.setIncludeFontPadding(false);
        TextView subhead = text("See the MIT. Act from the thumb zone.", 15, false);
        subhead.setTextColor(palette.secondaryText);
        headerCopy.addView(header);
        headerCopy.addView(subhead, matchWrapMargins(0, 8, 0, 0));
        topInfo.addView(headerCopy);

        mitCard = vertical();
        mitCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        mitCard.setBackground(roundedBackground(palette.surface, palette.divider, 8));
        mitKickerView = text("Primary MIT", 12, true);
        mitTitleView = text("", 18, true);
        mitTitleView.setSingleLine(false);
        mitTitleView.setMaxLines(3);
        mitTitleView.setEllipsize(TextUtils.TruncateAt.END);
        mitMetaView = text("", 13, false);
        mitCard.addView(mitKickerView);
        mitCard.addView(mitTitleView, matchWrapMargins(0, 9, 0, 0));
        mitCard.addView(mitMetaView, matchWrapMargins(0, 8, 0, 0));
        topInfo.addView(mitCard, matchWrapMargins(0, 18, 0, 0));
        screen.addView(topInfo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(palette.background);

        LinearLayout content = vertical();
        content.setPadding(dp(24), dp(10), dp(24), dp(20));
        content.setBackgroundColor(palette.background);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView listTitle = text("Priority index", 22, true);
        listTitle.setTextColor(palette.primaryText);
        content.addView(listTitle);
        taskList = vertical();
        content.addView(taskList, matchWrapMargins(0, 10, 0, 24));

        Button modeButton = quietButton(nightMode ? "Switch to day" : "Switch to night");
        modeButton.setOnClickListener(view -> toggleMode());
        content.addView(modeButton, matchWrapMargins(0, 0, 0, 0));

        screen.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));

        if (formOpen) {
            screen.addView(formPanel(), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout bottomBar = horizontal();
        bottomBar.setPadding(dp(16), dp(10), dp(16), dp(16));
        bottomBar.setBackground(roundedBackground(palette.surface, palette.divider, 0));
        if (formOpen) {
            saveButton = button(editingId == null ? "Add task" : "Update task");
            saveButton.setOnClickListener(view -> saveTaskFromForm());
            resetButton = quietButton("Cancel");
            resetButton.setOnClickListener(view -> resetForm());
            addWeightedButton(bottomBar, saveButton, 0);
            addWeightedButton(bottomBar, resetButton, 10);
        } else {
            saveButton = button("+ Add");
            saveButton.setOnClickListener(view -> openAddPanel());
            addWeightedButton(bottomBar, saveButton, 0);
            resetButton = null;
        }
        screen.addView(bottomBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(screen);
        updateReminderLabel();
        updateDetailsVisibility();
    }

    private LinearLayout formPanel() {
        LinearLayout form = vertical();
        form.setPadding(dp(24), dp(10), dp(24), dp(10));
        form.setBackground(roundedBackground(palette.background, palette.divider, 0));

        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundedBackground(palette.surface, palette.divider, 8));
        form.addView(card, matchWrapMargins(0, 0, 0, 0));

        formTitleView = text(editingId == null ? "Add a task" : "Edit task", 18, true);
        formTitleView.setTextColor(palette.primaryText);
        card.addView(formTitleView);

        TextView quickHint = text("Title first. Details are optional.", 13, false);
        quickHint.setSingleLine(false);
        quickHint.setTextColor(palette.secondaryText);
        card.addView(quickHint, matchWrapMargins(0, 5, 0, 10));

        titleInput = input("Task name");
        titleInput.setSingleLine(true);
        card.addView(titleInput, matchWrapMargins(0, 0, 0, 10));

        TextView defaults = text("High impact · Medium effort · Not urgent", 12, false);
        defaults.setTextColor(palette.secondaryText);
        card.addView(defaults, matchWrapMargins(0, 0, 0, 8));

        detailsToggleButton = quietButton(detailsExpanded ? "Hide details" : "Details");
        detailsToggleButton.setOnClickListener(view -> {
            detailsExpanded = !detailsExpanded;
            updateDetailsVisibility();
        });
        card.addView(detailsToggleButton, matchWrapMargins(0, 0, 0, 0));

        detailsSection = vertical();
        card.addView(detailsSection, matchWrapMargins(0, 14, 0, 0));

        notesInput = input("Notes");
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        detailsSection.addView(notesInput, matchWrapMargins(0, 0, 0, 16));

        detailsSection.addView(label("Impact"));
        impactGroup = radioGroup("High", TodoTask.HIGH, "Medium", TodoTask.MEDIUM, "Low", TodoTask.LOW);
        detailsSection.addView(impactGroup, matchWrapMargins(0, 6, 0, 14));

        detailsSection.addView(label("Effort"));
        effortGroup = radioGroup("High", TodoTask.HIGH, "Medium", TodoTask.MEDIUM, "Low", TodoTask.LOW);
        checkRadioByTag(effortGroup, TodoTask.MEDIUM);
        detailsSection.addView(effortGroup, matchWrapMargins(0, 6, 0, 14));

        detailsSection.addView(label("Dependency"));
        dependencySpinner = spinner(DEPENDENCIES);
        detailsSection.addView(dependencySpinner, matchWrapMargins(0, 6, 0, 14));

        urgentInput = checkbox("Urgent (+1000)");
        quickInput = checkbox("Quick task");
        detailsSection.addView(urgentInput);
        detailsSection.addView(quickInput, matchWrapMargins(0, 2, 0, 12));

        detailsSection.addView(label("Reminder"));
        reminderLabel = text("No reminder", 14, false);
        reminderLabel.setSingleLine(false);
        detailsSection.addView(reminderLabel, matchWrapMargins(0, 8, 0, 10));

        LinearLayout reminderButtons = horizontal();
        Button reminderButton = button("Pick time");
        reminderButton.setOnClickListener(view -> pickReminder());
        Button clearReminderButton = quietButton("Clear");
        clearReminderButton.setOnClickListener(view -> {
            selectedReminderAt = 0;
            updateReminderLabel();
        });
        addWeightedButton(reminderButtons, reminderButton, 0);
        addWeightedButton(reminderButtons, clearReminderButton, 10);
        detailsSection.addView(reminderButtons, matchWrapMargins(0, 0, 0, 14));

        detailsSection.addView(label("Repeat reminder"));
        LinearLayout recurrenceRow = horizontal();
        TextView everyLabel = text("Every", 14, false);
        everyLabel.setTextColor(palette.secondaryText);
        repeatEveryInput = input("1");
        repeatEveryInput.setText("1");
        repeatEveryInput.setSingleLine(true);
        repeatEveryInput.setSelectAllOnFocus(true);
        repeatEveryInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        repeatUnitSpinner = spinner(REPEAT_UNITS);
        repeatUnitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateReminderLabel();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateReminderLabel();
            }
        });
        recurrenceRow.addView(everyLabel, wrapMargins(0, 0, 10, 0));
        recurrenceRow.addView(repeatEveryInput, new LinearLayout.LayoutParams(dp(72),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        recurrenceRow.addView(repeatUnitSpinner, weightedMargins(1, 10, 0, 0, 0));
        detailsSection.addView(recurrenceRow, matchWrapMargins(0, 6, 0, 7));

        recurrenceHint = text("One-time reminder.", 12, false);
        recurrenceHint.setSingleLine(false);
        detailsSection.addView(recurrenceHint, matchWrapMargins(0, 0, 0, 0));

        TodoTask editingTask = editingId == null ? null : findTask(editingId);
        if (editingTask != null) {
            populateForm(editingTask);
        }
        return form;
    }

    private void saveTaskFromForm() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Task name required");
            return;
        }

        String repeatUnit = selectedRepeatUnit();
        int repeatEvery = repeatEveryFromInput();
        if (!TodoTask.REPEAT_NONE.equals(repeatUnit) && repeatEvery < 1) {
            revealDetailsForError(repeatEveryInput);
            repeatEveryInput.setError("Use 1 or more");
            return;
        }
        if (!TodoTask.REPEAT_NONE.equals(repeatUnit) && selectedReminderAt == 0) {
            revealDetailsForError(reminderLabel);
            reminderLabel.setText("Pick a first reminder time before enabling repeat.");
            reminderLabel.setTextColor(RED);
            return;
        }
        if (selectedReminderAt > 0 && selectedReminderAt <= System.currentTimeMillis()) {
            revealDetailsForError(reminderLabel);
            reminderLabel.setText("Choose a future reminder time.");
            reminderLabel.setTextColor(RED);
            return;
        }

        TodoTask task = editingId == null ? new TodoTask() : findTask(editingId);
        if (task == null) {
            task = new TodoTask();
            editingId = null;
        }
        task.title = title;
        task.notes = notesInput.getText().toString().trim();
        task.impact = selectedRadioTag(impactGroup, TodoTask.HIGH);
        task.effort = selectedRadioTag(effortGroup, TodoTask.MEDIUM);
        task.dependency = dependencySpinner.getSelectedItem().toString();
        task.urgent = urgentInput.isChecked();
        task.quickTask = quickInput.isChecked();
        task.recurringMit = !TodoTask.REPEAT_NONE.equals(repeatUnit);
        task.reminderAt = selectedReminderAt;
        task.reminderRepeatUnit = selectedReminderAt > 0 ? repeatUnit : TodoTask.REPEAT_NONE;
        task.reminderRepeatEvery = TodoTask.REPEAT_NONE.equals(task.reminderRepeatUnit) ? 1 : repeatEvery;

        if (editingId == null) {
            tasks.add(task);
        }

        ReminderScheduler.schedule(this, task);
        sortTasks();
        store.save(tasks);
        resetForm();
    }

    private void renderTasks() {
        sortTasks();
        taskList.removeAllViews();

        TodoTask mit = primaryMit();
        if (mit == null) {
            mitCard.setBackground(roundedBackground(palette.surface, palette.divider, 8));
            mitKickerView.setTextColor(RED);
            mitTitleView.setTextColor(palette.primaryText);
            mitMetaView.setTextColor(palette.secondaryText);
            mitTitleView.setText("No primary MIT");
            mitMetaView.setText("Add a task to start today's index.");
        } else {
            mitCard.setBackground(roundedBackground(palette.surface, palette.divider, 8));
            mitKickerView.setTextColor(RED);
            mitTitleView.setTextColor(palette.primaryText);
            mitMetaView.setTextColor(palette.secondaryText);
            mitTitleView.setText(mit.title);
            mitMetaView.setText("Score " + mit.scoreLabel() + " · " + mit.bucket());
        }

        if (tasks.isEmpty()) {
            TextView empty = text("No tasks yet.", 15, false);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setTextColor(palette.secondaryText);
            taskList.addView(empty, matchWrapMargins(0, 18, 0, 0));
            return;
        }

        for (TodoTask task : tasks) {
            taskList.addView(taskRow(task), matchWrapMargins(0, 10, 0, 0));
        }
    }

    private LinearLayout taskRow(TodoTask task) {
        LinearLayout row = vertical();
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(rowBackground(task.completed));

        LinearLayout titleRow = horizontal();
        CheckBox completeToggle = checkbox("");
        completeToggle.setChecked(task.completed);
        completeToggle.setContentDescription((task.completed ? "Restore " : "Complete ") + task.title);
        completeToggle.setOnClickListener(view -> toggleTaskCompleted(task));
        titleRow.addView(completeToggle, wrapMargins(0, 0, 10, 0));

        LinearLayout copy = vertical();
        TextView title = text(task.title, 17, true);
        title.setTextColor(task.completed ? palette.secondaryText : palette.primaryText);
        if (task.completed) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        title.setSingleLine(false);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title);

        TextView score = text("Score " + task.scoreLabel() + " · " + task.bucket(), 13, true);
        score.setTextColor(task.urgent && !task.completed ? RED : palette.secondaryText);
        copy.addView(score, matchWrapMargins(0, 5, 0, 0));
        titleRow.addView(copy, weightedMargins(1, 0, 0, 0, 0));
        row.addView(titleRow);

        row.addView(text(taskDetails(task), 13, false), matchWrapMargins(0, 7, 0, 0));
        if (!task.notes.isEmpty()) {
            TextView notes = text(task.notes, 13, false);
            notes.setSingleLine(false);
            row.addView(notes, matchWrapMargins(0, 8, 0, 0));
        }
        if (task.reminderAt > 0) {
            String reminderText = "Reminder " + formatDateTime(task.reminderAt);
            if (task.repeatsReminder()) {
                reminderText += " · " + task.recurrenceLabel();
            }
            row.addView(text(reminderText, 13, false), matchWrapMargins(0, 8, 0, 0));
        }

        LinearLayout secondaryActions = horizontal();
        Button edit = quietButton("Edit");
        edit.setOnClickListener(view -> editTask(task));

        Button delete = quietButton("Delete");
        delete.setOnClickListener(view -> confirmDelete(task));

        addWeightedButton(secondaryActions, edit, 0);
        addWeightedButton(secondaryActions, delete, 12);
        row.addView(secondaryActions, matchWrapMargins(0, 8, 0, 0));
        return row;
    }

    private String taskDetails(TodoTask task) {
        List<String> details = new ArrayList<>();
        details.add("Impact " + task.impact);
        details.add("Effort " + task.effort);
        details.add(task.urgent ? "Urgent" : "Not urgent");
        details.add("Dependency " + task.dependency);
        if (task.quickTask) {
            details.add("Quick");
        }
        return join(details, " / ");
    }

    private void toggleTaskCompleted(TodoTask task) {
        task.completed = !task.completed;
        if (task.completed) {
            ReminderScheduler.cancel(this, task);
        } else {
            ReminderScheduler.schedule(this, task);
        }
        saveAndRender();
    }

    private void openAddPanel() {
        editingId = null;
        selectedReminderAt = 0;
        detailsExpanded = false;
        formOpen = true;
        buildUi();
        renderTasks();
    }

    private void editTask(TodoTask task) {
        editingId = task.id;
        selectedReminderAt = task.reminderAt;
        detailsExpanded = true;
        formOpen = true;
        buildUi();
        renderTasks();
    }

    private void populateForm(TodoTask task) {
        titleInput.setText(task.title);
        notesInput.setText(task.notes);
        checkRadioByTag(impactGroup, task.impact);
        checkRadioByTag(effortGroup, task.effort);
        dependencySpinner.setSelection(indexOfDependency(task.dependency));
        urgentInput.setChecked(task.urgent);
        quickInput.setChecked(task.quickTask);
        selectedReminderAt = task.reminderAt;
        repeatEveryInput.setText(String.valueOf(Math.max(1, task.reminderRepeatEvery)));
        repeatUnitSpinner.setSelection(indexOfRepeatUnit(task.reminderRepeatUnit));
    }

    private void resetForm() {
        editingId = null;
        selectedReminderAt = 0;
        detailsExpanded = false;
        formOpen = false;
        buildUi();
        renderTasks();
    }

    private void updateDetailsVisibility() {
        if (detailsSection == null || detailsToggleButton == null) {
            return;
        }
        detailsSection.setVisibility(detailsExpanded ? View.VISIBLE : View.GONE);
        detailsToggleButton.setText(detailsExpanded ? "Hide details" : "Details");
    }

    private void revealDetailsForError(View target) {
        detailsExpanded = true;
        updateDetailsVisibility();
        if (target != null) {
            target.requestFocus();
        }
    }

    private void confirmDelete(TodoTask task) {
        new AlertDialog.Builder(this)
                .setTitle("Delete task?")
                .setMessage("This removes \"" + task.title + "\" from your priority index.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ReminderScheduler.cancel(this, task);
                    tasks.remove(task);
                    saveAndRender();
                })
                .show();
    }

    private void pickReminder() {
        Calendar calendar = Calendar.getInstance();
        if (selectedReminderAt > 0) {
            calendar.setTimeInMillis(selectedReminderAt);
        } else {
            calendar.add(Calendar.HOUR_OF_DAY, 1);
        }

        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    TimePickerDialog timePicker = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                calendar.set(Calendar.MINUTE, minute);
                                calendar.set(Calendar.SECOND, 0);
                                calendar.set(Calendar.MILLISECOND, 0);
                                selectedReminderAt = calendar.getTimeInMillis();
                                updateReminderLabel();
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false
                    );
                    timePicker.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePicker.show();
    }

    private void updateReminderLabel() {
        if (reminderLabel == null) {
            return;
        }

        if (selectedReminderAt > 0) {
            reminderLabel.setText("First reminder: " + formatDateTime(selectedReminderAt));
            reminderLabel.setTextColor(palette.primaryText);
        } else {
            reminderLabel.setText("No reminder");
            reminderLabel.setTextColor(palette.secondaryText);
        }

        if (recurrenceHint == null || repeatEveryInput == null) {
            return;
        }
        String repeatUnit = selectedRepeatUnit();
        boolean repeats = !TodoTask.REPEAT_NONE.equals(repeatUnit);
        repeatEveryInput.setEnabled(repeats);
        repeatEveryInput.setAlpha(repeats ? 1.0f : 0.48f);
        if (!repeats) {
            recurrenceHint.setText("One-time reminder.");
        } else if (selectedReminderAt == 0) {
            recurrenceHint.setText("Pick a first reminder time, then repeat "
                    + recurrencePreview(repeatUnit) + ".");
        } else {
            recurrenceHint.setText("Repeats " + recurrencePreview(repeatUnit)
                    + " after the first reminder.");
        }
        recurrenceHint.setTextColor(palette.secondaryText);
    }

    private void saveAndRender() {
        sortTasks();
        store.save(tasks);
        renderTasks();
    }

    private void sortTasks() {
        Collections.sort(tasks, new Comparator<TodoTask>() {
            @Override
            public int compare(TodoTask first, TodoTask second) {
                int score = Double.compare(second.score(), first.score());
                if (score != 0) {
                    return score;
                }
                return Long.compare(first.createdAt, second.createdAt);
            }
        });
    }

    private TodoTask primaryMit() {
        for (TodoTask task : tasks) {
            if (!task.completed) {
                return task;
            }
        }
        return null;
    }

    private TodoTask findTask(String id) {
        for (TodoTask task : tasks) {
            if (task.id.equals(id)) {
                return task;
            }
        }
        return null;
    }

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
                long nextReminder = task.nextReminderAfter(now);
                if (nextReminder > 0) {
                    task.reminderAt = nextReminder;
                    changed = true;
                }
            }
            ReminderScheduler.schedule(this, task);
        }
        if (changed) {
            store.save(tasks);
        }
    }

    private RadioGroup radioGroup(String firstLabel, String firstValue,
                                  String secondLabel, String secondValue,
                                  String thirdLabel, String thirdValue) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.addView(radio(firstLabel, firstValue), new RadioGroup.LayoutParams(
                0, RadioGroup.LayoutParams.WRAP_CONTENT, 1));
        group.addView(radio(secondLabel, secondValue), new RadioGroup.LayoutParams(
                0, RadioGroup.LayoutParams.WRAP_CONTENT, 1));
        group.addView(radio(thirdLabel, thirdValue), new RadioGroup.LayoutParams(
                0, RadioGroup.LayoutParams.WRAP_CONTENT, 1));
        checkRadioByTag(group, firstValue);
        return group;
    }

    private RadioButton radio(String label, String value) {
        RadioButton button = new RadioButton(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(palette.primaryText);
        button.setTag(value);
        button.setId(View.generateViewId());
        button.setMinHeight(dp(48));
        button.setMinWidth(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void checkRadioByTag(RadioGroup group, String value) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (value.equals(child.getTag())) {
                group.check(child.getId());
                return;
            }
        }
    }

    private String selectedRadioTag(RadioGroup group, String fallback) {
        int selectedId = group.getCheckedRadioButtonId();
        View selected = group.findViewById(selectedId);
        return selected == null ? fallback : String.valueOf(selected.getTag());
    }

    private int indexOfDependency(String value) {
        for (int index = 0; index < DEPENDENCIES.length; index++) {
            if (DEPENDENCIES[index].equals(value)) {
                return index;
            }
        }
        return 0;
    }

    private int indexOfRepeatUnit(String value) {
        String normalized = TodoTask.normalizeRepeatUnit(value);
        if (TodoTask.REPEAT_DAY.equals(normalized)) {
            return 1;
        }
        if (TodoTask.REPEAT_WEEK.equals(normalized)) {
            return 2;
        }
        if (TodoTask.REPEAT_MONTH.equals(normalized)) {
            return 3;
        }
        return 0;
    }

    private String selectedRepeatUnit() {
        if (repeatUnitSpinner == null) {
            return TodoTask.REPEAT_NONE;
        }
        int position = repeatUnitSpinner.getSelectedItemPosition();
        if (position == 1) {
            return TodoTask.REPEAT_DAY;
        }
        if (position == 2) {
            return TodoTask.REPEAT_WEEK;
        }
        if (position == 3) {
            return TodoTask.REPEAT_MONTH;
        }
        return TodoTask.REPEAT_NONE;
    }

    private int repeatEveryFromInput() {
        String raw = repeatEveryInput.getText().toString().trim();
        if (raw.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String recurrencePreview(String repeatUnit) {
        int every = Math.max(1, repeatEveryFromInput());
        return "every " + every + " " + repeatUnitLabel(repeatUnit, every);
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(palette.primaryText);
        input.setHintTextColor(palette.secondaryText);
        input.setMinHeight(dp(48));
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setSingleLine(false);
        input.setBackground(roundedBackground(palette.field, palette.divider, 8));
        return input;
    }

    private CheckBox checkbox(String text) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextSize(14);
        checkBox.setTextColor(palette.primaryText);
        checkBox.setMinHeight(dp(48));
        return checkBox;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(spinnerAdapter(values));
        spinner.setBackground(roundedBackground(palette.field, palette.divider, 8));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setMinimumHeight(dp(48));
        return spinner;
    }

    private ArrayAdapter<String> spinnerAdapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return spinnerText(getItem(position), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return spinnerText(getItem(position), true);
            }
        };
    }

    private TextView spinnerText(String value, boolean dropDown) {
        TextView view = text(value == null ? "" : value, 15, false);
        view.setTextColor(dropDown ? 0xFF000000 : palette.primaryText);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinHeight(dp(48));
        view.setPadding(dp(10), 0, dp(10), 0);
        return view;
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(palette.secondaryText);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(0, 1.05f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13, true);
        view.setTextColor(palette.primaryText);
        return view;
    }

    private Button button(String text) {
        Button button = baseButton(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(buttonBackground(RED, RED));
        return button;
    }

    private Button quietButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(palette.secondaryText);
        button.setBackground(buttonBackground(palette.surface, palette.divider));
        return button;
    }

    private Button baseButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
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

    private Drawable rowBackground(boolean completed) {
        return roundedBackground(completed ? palette.completedSurface : palette.surface, palette.divider, 8);
    }

    private Drawable buttonBackground(int fill, int stroke) {
        GradientDrawable content = rounded(fill, stroke, 8);
        GradientDrawable mask = rounded(0xFFFFFFFF, 0xFFFFFFFF, 8);
        int rippleColor = fill == RED ? 0x33FFFFFF : 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private Drawable roundedBackground(int fill, int stroke, int radiusDp) {
        return rounded(fill, stroke, radiusDp);
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void addWeightedButton(LinearLayout row, Button button, int leftMargin) {
        row.addView(button, weightedMargins(1, leftMargin, 0, 0, 0));
    }

    private void toggleMode() {
        nightMode = !nightMode;
        SharedPreferences preferences = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        preferences.edit().putBoolean(NIGHT_MODE, nightMode).apply();
        buildUi();
        renderTasks();
    }

    private LinearLayout.LayoutParams matchWrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams wrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weightedMargins(float weight, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatDateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(millis));
    }

    private String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(separator);
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private String repeatUnitLabel(String unit, int every) {
        if (TodoTask.REPEAT_MONTH.equals(unit)) {
            return every == 1 ? "month" : "months";
        }
        if (TodoTask.REPEAT_WEEK.equals(unit)) {
            return every == 1 ? "week" : "weeks";
        }
        return every == 1 ? "day" : "days";
    }

    private static final class Palette {
        final int background;
        final int surface;
        final int field;
        final int completedSurface;
        final int primaryText;
        final int secondaryText;
        final int divider;

        private Palette(int background, int surface, int field, int completedSurface,
                        int primaryText, int secondaryText, int divider) {
            this.background = background;
            this.surface = surface;
            this.field = field;
            this.completedSurface = completedSurface;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.divider = divider;
        }

        static Palette from(boolean nightMode) {
            if (nightMode) {
                return new Palette(
                        0xFF000000,
                        0xFF1B1B1D,
                        0xFF101011,
                        0xFF111111,
                        0xFFFFFFFF,
                        0xFFB8B8B8,
                        0xFF2A2A2A
                );
            }
            return new Palette(
                    0xFFFFFFFF,
                    0xFFFFFFFF,
                    0xFFF5F5F6,
                    0xFFF6F6F6,
                    0xFF000000,
                    0xFF8D8D8D,
                    0xFFE3E3E3
            );
        }
    }
}
