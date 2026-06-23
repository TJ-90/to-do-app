package com.tj90.prioritytodo;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String[] DEPENDENCIES = {"None", "Sequential", "Reciprocal", "Pooled"};

    private final List<TodoTask> tasks = new ArrayList<>();

    private TaskStore store;
    private EditText titleInput;
    private EditText notesInput;
    private RadioGroup impactGroup;
    private RadioGroup effortGroup;
    private Spinner dependencySpinner;
    private CheckBox urgentInput;
    private CheckBox quickInput;
    private CheckBox recurringInput;
    private TextView reminderLabel;
    private Button saveButton;
    private TextView mitView;
    private LinearLayout taskList;
    private long selectedReminderAt;
    private String editingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new TaskStore(this);
        tasks.addAll(store.load());
        requestNotificationPermission();
        createNotificationChannel();
        rescheduleFutureReminders();
        buildUi();
        renderTasks();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView header = text("Priority Todo", 28, true);
        header.setTextColor(0xFF172026);
        root.addView(header);
        root.addView(text("Sorted by score: Impact / Effort + Urgency", 14, false));

        mitView = text("", 16, true);
        mitView.setPadding(dp(14), dp(14), dp(14), dp(14));
        mitView.setBackground(panelBackground(0xFFE6F2EE));
        root.addView(mitView, matchWrapMargins(0, 16, 0, 14));

        LinearLayout form = vertical();
        form.setPadding(dp(14), dp(14), dp(14), dp(14));
        form.setBackground(panelBackground(0xFFFFFFFF));
        root.addView(form, matchWrapMargins(0, 0, 0, 18));

        form.addView(text("Task", 18, true));
        titleInput = input("Task name");
        titleInput.setSingleLine(true);
        form.addView(titleInput, matchWrapMargins(0, 8, 0, 8));

        notesInput = input("Notes");
        notesInput.setMinLines(2);
        form.addView(notesInput, matchWrapMargins(0, 0, 0, 12));

        form.addView(label("Impact"));
        impactGroup = radioGroup("High", TodoTask.HIGH, "Medium", TodoTask.MEDIUM, "Low", TodoTask.LOW);
        form.addView(impactGroup, matchWrapMargins(0, 4, 0, 10));

        form.addView(label("Effort"));
        effortGroup = radioGroup("High", TodoTask.HIGH, "Medium", TodoTask.MEDIUM, "Low", TodoTask.LOW);
        checkRadioByTag(effortGroup, TodoTask.MEDIUM);
        form.addView(effortGroup, matchWrapMargins(0, 4, 0, 10));

        form.addView(label("Dependency"));
        dependencySpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                DEPENDENCIES
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dependencySpinner.setAdapter(adapter);
        form.addView(dependencySpinner, matchWrapMargins(0, 4, 0, 10));

        urgentInput = checkbox("Urgent (+1000)");
        quickInput = checkbox("Quick task");
        recurringInput = checkbox("Recurring MIT");
        form.addView(urgentInput);
        form.addView(quickInput);
        form.addView(recurringInput);

        LinearLayout reminderRow = horizontal();
        reminderLabel = text("No reminder", 14, false);
        Button reminderButton = button("Set reminder");
        reminderButton.setOnClickListener(view -> pickReminder());
        Button clearReminderButton = button("Clear");
        clearReminderButton.setOnClickListener(view -> {
            selectedReminderAt = 0;
            updateReminderLabel();
        });
        reminderRow.addView(reminderLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        reminderRow.addView(reminderButton);
        reminderRow.addView(clearReminderButton);
        form.addView(reminderRow, matchWrapMargins(0, 12, 0, 12));

        LinearLayout actionRow = horizontal();
        saveButton = button("Add task");
        saveButton.setOnClickListener(view -> saveTaskFromForm());
        Button resetButton = button("Reset");
        resetButton.setOnClickListener(view -> resetForm());
        actionRow.addView(saveButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        actionRow.addView(resetButton);
        form.addView(actionRow);

        TextView listTitle = text("Priority index", 20, true);
        root.addView(listTitle);
        taskList = vertical();
        root.addView(taskList);

        setContentView(scrollView);
    }

    private void saveTaskFromForm() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Task name required");
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
        task.recurringMit = recurringInput.isChecked();
        task.reminderAt = selectedReminderAt;

        if (editingId == null) {
            tasks.add(task);
        }

        ReminderScheduler.schedule(this, task);
        saveAndRender();
        resetForm();
    }

    private void renderTasks() {
        sortTasks();
        taskList.removeAllViews();

        TodoTask mit = primaryMit();
        if (mit == null) {
            mitView.setText("Primary MIT: add a task to start today's index.");
        } else {
            mitView.setText("Primary MIT: " + mit.title + " | Score " + mit.scoreLabel()
                    + " | " + mit.bucket());
        }

        if (tasks.isEmpty()) {
            TextView empty = text("No tasks yet.", 15, false);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            taskList.addView(empty, matchWrapMargins(0, 18, 0, 0));
            return;
        }

        for (TodoTask task : tasks) {
            taskList.addView(taskRow(task), matchWrapMargins(0, 10, 0, 0));
        }
    }

    private LinearLayout taskRow(TodoTask task) {
        LinearLayout row = vertical();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(panelBackground(task.completed ? 0xFFE7EBEF : 0xFFFFFFFF));

        TextView title = text(task.title + (task.completed ? "  [done]" : ""), 18, true);
        title.setTextColor(0xFF172026);
        row.addView(title);

        TextView score = text("Score " + task.scoreLabel() + " | " + task.bucket(), 14, true);
        score.setTextColor(task.score() >= 1000 ? 0xFFB42318 : 0xFF1C6E5A);
        row.addView(score, matchWrapMargins(0, 4, 0, 0));

        row.addView(text(taskDetails(task), 14, false), matchWrapMargins(0, 4, 0, 0));
        if (!task.notes.isEmpty()) {
            row.addView(text(task.notes, 14, false), matchWrapMargins(0, 6, 0, 0));
        }
        if (task.reminderAt > 0) {
            row.addView(text("Reminder: " + formatDateTime(task.reminderAt), 14, false),
                    matchWrapMargins(0, 6, 0, 0));
        }

        LinearLayout actions = horizontal();
        Button complete = button(task.completed ? "Restore" : "Complete");
        complete.setOnClickListener(view -> {
            task.completed = !task.completed;
            if (task.completed) {
                ReminderScheduler.cancel(this, task);
            } else {
                ReminderScheduler.schedule(this, task);
            }
            saveAndRender();
        });

        Button edit = button("Edit");
        edit.setOnClickListener(view -> editTask(task));

        Button delete = button("Delete");
        delete.setOnClickListener(view -> {
            ReminderScheduler.cancel(this, task);
            tasks.remove(task);
            saveAndRender();
        });

        actions.addView(complete);
        actions.addView(edit);
        actions.addView(delete);
        row.addView(actions, matchWrapMargins(0, 10, 0, 0));
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
        if (task.recurringMit) {
            details.add("Recurring MIT");
        }
        return join(details, " | ");
    }

    private void editTask(TodoTask task) {
        editingId = task.id;
        titleInput.setText(task.title);
        notesInput.setText(task.notes);
        checkRadioByTag(impactGroup, task.impact);
        checkRadioByTag(effortGroup, task.effort);
        dependencySpinner.setSelection(indexOfDependency(task.dependency));
        urgentInput.setChecked(task.urgent);
        quickInput.setChecked(task.quickTask);
        recurringInput.setChecked(task.recurringMit);
        selectedReminderAt = task.reminderAt;
        updateReminderLabel();
        saveButton.setText("Update task");
    }

    private void resetForm() {
        editingId = null;
        titleInput.setText("");
        notesInput.setText("");
        checkRadioByTag(impactGroup, TodoTask.HIGH);
        checkRadioByTag(effortGroup, TodoTask.MEDIUM);
        dependencySpinner.setSelection(0);
        urgentInput.setChecked(false);
        quickInput.setChecked(false);
        recurringInput.setChecked(false);
        selectedReminderAt = 0;
        updateReminderLabel();
        saveButton.setText("Add task");
    }

    private void pickReminder() {
        Calendar calendar = Calendar.getInstance();
        if (selectedReminderAt > 0) {
            calendar.setTimeInMillis(selectedReminderAt);
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
        reminderLabel.setText(selectedReminderAt > 0
                ? "Reminder: " + formatDateTime(selectedReminderAt)
                : "No reminder");
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
        for (TodoTask task : tasks) {
            ReminderScheduler.schedule(this, task);
        }
    }

    private RadioGroup radioGroup(String firstLabel, String firstValue,
                                  String secondLabel, String secondValue,
                                  String thirdLabel, String thirdValue) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.addView(radio(firstLabel, firstValue));
        group.addView(radio(secondLabel, secondValue));
        group.addView(radio(thirdLabel, thirdValue));
        checkRadioByTag(group, firstValue);
        return group;
    }

    private RadioButton radio(String label, String value) {
        RadioButton button = new RadioButton(this);
        button.setText(label);
        button.setTag(value);
        button.setId(View.generateViewId());
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

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(0xFF172026);
        input.setHintTextColor(0xFF7B8794);
        return input;
    }

    private CheckBox checkbox(String text) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setTextColor(0xFF172026);
        return checkBox;
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(0xFF3B4856);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 14, true);
        view.setTextColor(0xFF172026);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
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

    private GradientDrawable panelBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), 0xFFD5DCE3);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
}
