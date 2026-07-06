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
    private static final String CATEGORIES = "categories";

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

    List<String> loadCategories() {
        List<String> categories = new ArrayList<>();
        String raw = preferences.getString(CATEGORIES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                String name = array.optString(index, "").trim();
                if (!name.isEmpty()) {
                    categories.add(name);
                }
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(CATEGORIES).apply();
        }
        return categories;
    }

    void saveCategories(List<String> categories) {
        JSONArray array = new JSONArray();
        for (String name : categories) {
            array.put(name);
        }
        preferences.edit().putString(CATEGORIES, array.toString()).apply();
    }
}
