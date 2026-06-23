package com.tj90.prioritytodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class TaskStore {
    private static final String PREFS = "priority_todo_store";
    private static final String TASKS = "tasks";

    private final SharedPreferences preferences;

    TaskStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<TodoTask> load() {
        List<TodoTask> tasks = new ArrayList<>();
        String raw = preferences.getString(TASKS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                tasks.add(TodoTask.fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(TASKS).apply();
        }
        return tasks;
    }

    void save(List<TodoTask> tasks) {
        JSONArray array = new JSONArray();
        for (TodoTask task : tasks) {
            try {
                array.put(task.toJson());
            } catch (JSONException ignored) {
                // Skip only the malformed task; keep the rest of the list usable.
            }
        }
        preferences.edit().putString(TASKS, array.toString()).apply();
    }
}
