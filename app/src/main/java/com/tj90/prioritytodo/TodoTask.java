package com.tj90.prioritytodo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

final class TodoTask {
    static final String HIGH = "H";
    static final String MEDIUM = "M";
    static final String LOW = "L";

    String id = UUID.randomUUID().toString();
    String title = "";
    String notes = "";
    String impact = HIGH;
    String effort = MEDIUM;
    String dependency = "None";
    boolean urgent;
    boolean quickTask;
    boolean recurringMit;
    boolean completed;
    long createdAt = System.currentTimeMillis();
    long reminderAt;

    double score() {
        return ((double) impactValue(impact) / effortValue(effort)) + (urgent ? 1000 : 0);
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
        json.put("recurringMit", recurringMit);
        json.put("completed", completed);
        json.put("createdAt", createdAt);
        json.put("reminderAt", reminderAt);
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
        task.recurringMit = json.optBoolean("recurringMit", false);
        task.completed = json.optBoolean("completed", false);
        task.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        task.reminderAt = json.optLong("reminderAt", 0);
        return task;
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
}
