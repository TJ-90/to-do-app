package com.tj90.prioritytodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TaskStore {
    private static final String PREFS = "priority_todo_store";
    private static final String TASKS = "tasks";
    private static final long TOMBSTONE_TTL_MS = 30L * 24 * 60 * 60 * 1000;

    private final SharedPreferences preferences;

    TaskStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<TodoTask> load() {
        List<TodoTask> visible = new ArrayList<>();
        for (TodoTask task : loadAll()) {
            if (!task.deleted) {
                visible.add(task);
            }
        }
        return visible;
    }

    List<TodoTask> loadAll() {
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

    void save(List<TodoTask> visibleTasks) {
        Map<String, TodoTask> byId = new LinkedHashMap<>();
        for (TodoTask task : loadAll()) {
            byId.put(task.id, task);
        }
        for (TodoTask task : visibleTasks) {
            byId.put(task.id, task);
        }
        persist(new ArrayList<>(byId.values()));
    }

    void persist(List<TodoTask> tasks) {
        JSONArray array = new JSONArray();
        for (TodoTask task : tasks) {
            try {
                array.put(task.toJson());
            } catch (JSONException ignored) {
                // Skip only the malformed task; keep the rest usable.
            }
        }
        preferences.edit().putString(TASKS, array.toString()).apply();
    }

    static List<TodoTask> merge(List<TodoTask> local, List<TodoTask> remote) {
        Map<String, TodoTask> byId = new LinkedHashMap<>();
        for (TodoTask task : local) {
            byId.put(task.id, task);
        }
        for (TodoTask task : remote) {
            TodoTask current = byId.get(task.id);
            if (current == null || task.updatedAt >= current.updatedAt) {
                byId.put(task.id, task);
            }
        }
        long cutoff = System.currentTimeMillis() - TOMBSTONE_TTL_MS;
        List<TodoTask> merged = new ArrayList<>();
        for (TodoTask task : byId.values()) {
            if (task.deleted && task.updatedAt < cutoff) {
                continue;
            }
            merged.add(task);
        }
        return merged;
    }
}
