package com.tj90.prioritytodo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

final class TodoTask {
    static final String HIGH = "H";
    static final String MEDIUM = "M";
    static final String LOW = "L";
    static final String REPEAT_NONE = "none";
    static final String REPEAT_DAY = "day";
    static final String REPEAT_WEEK = "week";
    static final String REPEAT_MONTH = "month";

    String id = UUID.randomUUID().toString();
    String title = "";
    String notes = "";
    String impact = HIGH;
    String effort = MEDIUM;
    String dependency = "None";
    boolean urgent;
    boolean quickTask;
    boolean snoozed;
    boolean recurringMit;
    boolean completed;
    long createdAt = System.currentTimeMillis();
    long reminderAt;
    String reminderRepeatUnit = REPEAT_NONE;
    int reminderRepeatEvery = 1;

    double score() {
        double s = ((double) impactValue(impact) / effortValue(effort)) + (urgent ? 1000 : 0);
        if (snoozed) {
            s -= 5000;
        }
        return s;
    }

    String scoreLabel() {
        return String.format(Locale.US, "%.1f", score());
    }

    String bucket() {
        double score = score();
        if (score >= 1000) {
            return "Immediate";
        }
        if (score >= 500) {
            return "Next week";
        }
        return "Someday";
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("notes", notes);
        json.put("impact", impact);
        json.put("effort", effort);
        json.put("dependency", dependency);
        json.put("urgent", urgent);
        json.put("quickTask", quickTask);
        json.put("snoozed", snoozed);
        json.put("recurringMit", recurringMit);
        json.put("completed", completed);
        json.put("createdAt", createdAt);
        json.put("reminderAt", reminderAt);
        json.put("reminderRepeatUnit", reminderRepeatUnit);
        json.put("reminderRepeatEvery", reminderRepeatEvery);
        return json;
    }

    static TodoTask fromJson(JSONObject json) {
        TodoTask task = new TodoTask();
        task.id = json.optString("id", UUID.randomUUID().toString());
        task.title = json.optString("title", "");
        task.notes = json.optString("notes", "");
        task.impact = json.optString("impact", HIGH);
        task.effort = json.optString("effort", MEDIUM);
        task.dependency = json.optString("dependency", "None");
        task.urgent = json.optBoolean("urgent", false);
        task.quickTask = json.optBoolean("quickTask", false);
        task.snoozed = json.optBoolean("snoozed", false);
        task.recurringMit = json.optBoolean("recurringMit", false);
        task.completed = json.optBoolean("completed", false);
        task.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        task.reminderAt = json.optLong("reminderAt", 0);
        task.reminderRepeatUnit = normalizeRepeatUnit(json.optString("reminderRepeatUnit", REPEAT_NONE));
        task.reminderRepeatEvery = Math.max(1, json.optInt("reminderRepeatEvery", 1));
        return task;
    }

    boolean repeatsReminder() {
        return reminderAt > 0
                && reminderRepeatEvery > 0
                && !REPEAT_NONE.equals(reminderRepeatUnit);
    }

    long nextReminderAfter(long now) {
        if (!repeatsReminder()) {
            return 0;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(reminderAt);
        int field = repeatCalendarField(reminderRepeatUnit);
        while (calendar.getTimeInMillis() <= now) {
            calendar.add(field, reminderRepeatEvery);
        }
        return calendar.getTimeInMillis();
    }

    String recurrenceLabel() {
        if (!repeatsReminder()) {
            return "Does not repeat";
        }
        String unit = repeatUnitLabel(reminderRepeatUnit, reminderRepeatEvery);
        return "Every " + reminderRepeatEvery + " " + unit;
    }

    static String normalizeRepeatUnit(String value) {
        if (REPEAT_DAY.equals(value) || "daily".equals(value)) {
            return REPEAT_DAY;
        }
        if (REPEAT_WEEK.equals(value) || "weekly".equals(value)) {
            return REPEAT_WEEK;
        }
        if (REPEAT_MONTH.equals(value) || "monthly".equals(value)) {
            return REPEAT_MONTH;
        }
        return REPEAT_NONE;
    }

    private static int impactValue(String impact) {
        if (HIGH.equals(impact)) {
            return 900;
        }
        if (MEDIUM.equals(impact)) {
            return 600;
        }
        return 300;
    }

    private static int effortValue(String effort) {
        if (HIGH.equals(effort)) {
            return 30;
        }
        if (MEDIUM.equals(effort)) {
            return 20;
        }
        return 10;
    }

    private static int repeatCalendarField(String unit) {
        if (REPEAT_MONTH.equals(unit)) {
            return Calendar.MONTH;
        }
        if (REPEAT_WEEK.equals(unit)) {
            return Calendar.WEEK_OF_YEAR;
        }
        return Calendar.DAY_OF_YEAR;
    }

    private static String repeatUnitLabel(String unit, int every) {
        if (REPEAT_MONTH.equals(unit)) {
            return every == 1 ? "month" : "months";
        }
        if (REPEAT_WEEK.equals(unit)) {
            return every == 1 ? "week" : "weeks";
        }
        return every == 1 ? "day" : "days";
    }
}
