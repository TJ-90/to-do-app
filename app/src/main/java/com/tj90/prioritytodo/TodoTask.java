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
    static final String REPEAT_HOUR = "hour";
    static final String REPEAT_DAY = "day";
    static final String REPEAT_WEEK = "week";
    static final String REPEAT_MONTH = "month";
    static final String REPEAT_YEAR = "year";

    String id = UUID.randomUUID().toString();
    String title = "";
    String notes = "";
    String impact = HIGH;
    String effort = MEDIUM;
    String dependency = "None";
    String category = null;
    boolean urgent;
    boolean quickTask;
    boolean snoozed;
    boolean recurringMit;
    boolean completed;
    long createdAt = System.currentTimeMillis();
    long updatedAt = createdAt;
    long reminderAt;
    String reminderRepeatUnit = REPEAT_NONE;
    int reminderRepeatEvery = 1;

    double score() {
        double s = (urgent ? 1000 : 0)
                + (10 * ((double) impactValue(impact) / effortValue(effort)))
                + impactRank(impact);
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
        if (category == null) {
            json.put("category", JSONObject.NULL);
        } else {
            json.put("category", category);
        }
        json.put("urgent", urgent);
        json.put("quickTask", quickTask);
        json.put("snoozed", snoozed);
        json.put("recurringMit", recurringMit);
        json.put("completed", completed);
        json.put("createdAt", createdAt);
        json.put("updatedAt", updatedAt);
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
        if (json.has("category") && !json.isNull("category")) {
            String cat = json.optString("category", "");
            task.category = cat.isEmpty() ? null : cat;
        } else {
            task.category = null;
        }
        task.urgent = json.optBoolean("urgent", false);
        task.quickTask = json.optBoolean("quickTask", false);
        task.snoozed = json.optBoolean("snoozed", false);
        task.recurringMit = json.optBoolean("recurringMit", false);
        task.completed = json.optBoolean("completed", false);
        task.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        task.updatedAt = json.optLong("updatedAt", task.createdAt);
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

    TodoTask nextOccurrenceAfterCompletion(long completedAt, long mutationTimestamp) {
        if (!repeatsReminder()) {
            return null;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Math.max(reminderAt, completedAt));
        calendar.add(repeatCalendarField(reminderRepeatUnit), reminderRepeatEvery);

        TodoTask next = new TodoTask();
        next.id = recurringSuccessorId();
        next.title = title;
        next.notes = notes;
        next.impact = impact;
        next.effort = effort;
        next.dependency = dependency;
        next.category = category;
        next.urgent = urgent;
        next.quickTask = quickTask;
        next.recurringMit = recurringMit;
        next.createdAt = mutationTimestamp;
        next.updatedAt = mutationTimestamp;
        next.reminderAt = calendar.getTimeInMillis();
        next.reminderRepeatUnit = reminderRepeatUnit;
        next.reminderRepeatEvery = reminderRepeatEvery;
        return next;
    }

    TodoTask completeAndCreateNextOccurrence(long completedAt, long mutationTimestamp) {
        if (completed) {
            return null;
        }
        TodoTask next = nextOccurrenceAfterCompletion(completedAt, mutationTimestamp);
        completed = true;
        snoozed = false;
        updatedAt = mutationTimestamp;
        return next;
    }

    void reopenAfterCompletion(long mutationTimestamp) {
        completed = false;
        updatedAt = mutationTimestamp;
    }

    String recurringSuccessorId() {
        return id + ":next";
    }

    static int parseRepeatInterval(String value) {
        if (value == null) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 1 && parsed <= 999 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static boolean shouldClearExpiredReminder(long reminderAt, String repeatUnit, long now) {
        return reminderAt > 0 && reminderAt <= now && REPEAT_NONE.equals(repeatUnit);
    }

    String recurrenceLabel() {
        if (!repeatsReminder()) {
            return "Does not repeat";
        }
        String unit = repeatUnitLabel(reminderRepeatUnit, reminderRepeatEvery);
        return "Every " + reminderRepeatEvery + " " + unit;
    }

    static String normalizeRepeatUnit(String value) {
        if (REPEAT_HOUR.equals(value) || "hourly".equals(value)) {
            return REPEAT_HOUR;
        }
        if (REPEAT_DAY.equals(value) || "daily".equals(value)) {
            return REPEAT_DAY;
        }
        if (REPEAT_WEEK.equals(value) || "weekly".equals(value)) {
            return REPEAT_WEEK;
        }
        if (REPEAT_MONTH.equals(value) || "monthly".equals(value)) {
            return REPEAT_MONTH;
        }
        if (REPEAT_YEAR.equals(value) || "yearly".equals(value)) {
            return REPEAT_YEAR;
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

    private static int impactRank(String impact) {
        if (HIGH.equals(impact)) {
            return 3;
        }
        if (MEDIUM.equals(impact)) {
            return 2;
        }
        return 1;
    }

    private static int repeatCalendarField(String unit) {
        if (REPEAT_YEAR.equals(unit)) {
            return Calendar.YEAR;
        }
        if (REPEAT_MONTH.equals(unit)) {
            return Calendar.MONTH;
        }
        if (REPEAT_WEEK.equals(unit)) {
            return Calendar.WEEK_OF_YEAR;
        }
        if (REPEAT_HOUR.equals(unit)) {
            return Calendar.HOUR_OF_DAY;
        }
        return Calendar.DAY_OF_YEAR;
    }

    private static String repeatUnitLabel(String unit, int every) {
        if (REPEAT_YEAR.equals(unit)) {
            return every == 1 ? "year" : "years";
        }
        if (REPEAT_MONTH.equals(unit)) {
            return every == 1 ? "month" : "months";
        }
        if (REPEAT_WEEK.equals(unit)) {
            return every == 1 ? "week" : "weeks";
        }
        if (REPEAT_HOUR.equals(unit)) {
            return every == 1 ? "hour" : "hours";
        }
        return every == 1 ? "day" : "days";
    }
}
