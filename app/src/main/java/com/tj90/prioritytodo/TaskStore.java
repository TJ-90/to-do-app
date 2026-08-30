package com.tj90.prioritytodo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class TaskStore {
    private static final String PREFS = "priority_todo_store";
    private static final String TASKS = "tasks";
    private static final String CATEGORIES = "categories";
    private static final String TASK_TOMBSTONES = "task_tombstones_v1";
    private static final String CATEGORY_STATES = "category_states_v1";
    private static final String LEGACY_CATEGORY_BASELINE = "legacy_category_baseline_v1";
    private static final String LOCAL_REVISION = "local_revision_v1";

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
        preferences.edit()
                .putString(TASKS, tasksJson(tasks).toString())
                .putLong(LOCAL_REVISION, revision() + 1)
                .apply();
    }

    long revision() {
        return preferences.getLong(LOCAL_REVISION, 0);
    }

    SyncState loadSyncState() {
        List<TodoTask> tasks = load();
        List<SyncState.TaskTombstone> tombstones = loadTaskTombstones();
        return new SyncState(tasks, tombstones, loadCategoryStates(tasks, tombstones));
    }

    void saveSyncState(SyncState state) {
        JSONArray tombstones = new JSONArray();
        for (SyncState.TaskTombstone tombstone : state.taskTombstones) {
            try {
                tombstones.put(tombstone.toJson());
            } catch (JSONException ignored) {
                // Keep other valid deletion records.
            }
        }
        JSONArray categoryStates = new JSONArray();
        JSONArray activeCategories = new JSONArray();
        for (SyncState.CategoryState category : state.categories) {
            try {
                categoryStates.put(category.toJson());
                if (category.isActive()) {
                    activeCategories.put(category.name);
                }
            } catch (JSONException ignored) {
                // Keep other valid category records.
            }
        }
        preferences.edit()
                .putString(TASKS, tasksJson(state.tasks).toString())
                .putString(TASK_TOMBSTONES, tombstones.toString())
                .putString(CATEGORY_STATES, categoryStates.toString())
                .putString(CATEGORIES, activeCategories.toString())
                .putLong(LOCAL_REVISION, revision() + 1)
                .apply();
    }

    private JSONArray tasksJson(List<TodoTask> tasks) {
        JSONArray array = new JSONArray();
        for (TodoTask task : tasks) {
            try {
                array.put(task.toJson());
            } catch (JSONException ignored) {
                // Skip only the malformed task; keep the rest of the list usable.
            }
        }
        return array;
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

    private List<SyncState.TaskTombstone> loadTaskTombstones() {
        List<SyncState.TaskTombstone> tombstones = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(TASK_TOMBSTONES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                SyncState.TaskTombstone tombstone = SyncState.TaskTombstone.fromJson(
                        array.optJSONObject(index));
                if (tombstone != null) {
                    tombstones.add(tombstone);
                }
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(TASK_TOMBSTONES).apply();
        }
        return tombstones;
    }

    private List<SyncState.CategoryState> loadCategoryStates(
            List<TodoTask> tasks, List<SyncState.TaskTombstone> tombstones) {
        if (!preferences.contains(CATEGORY_STATES)) {
            long baseline = Math.max(
                    preferences.getLong(LEGACY_CATEGORY_BASELINE, 0),
                    new SyncState(tasks, tombstones, new ArrayList<>())
                            .nextMutationTimestamp(System.currentTimeMillis()));
            List<SyncState.CategoryState> migrated = migrateLegacyCategories(
                    loadCategories(), baseline);
            JSONArray array = new JSONArray();
            for (SyncState.CategoryState category : migrated) {
                try {
                    array.put(category.toJson());
                } catch (JSONException ignored) {
                    // Names came from the legacy JSON array and are safe strings.
                }
            }
            preferences.edit()
                    .putLong(LEGACY_CATEGORY_BASELINE, baseline)
                    .putString(CATEGORY_STATES, array.toString())
                    .apply();
            return migrated;
        }

        List<SyncState.CategoryState> categories = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(CATEGORY_STATES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject value = array.optJSONObject(index);
                SyncState.CategoryState category = SyncState.CategoryState.fromJson(value);
                if (category != null) {
                    categories.add(category);
                }
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(CATEGORY_STATES).apply();
            return loadCategoryStates(tasks, tombstones);
        }
        return categories;
    }

    static List<SyncState.CategoryState> migrateLegacyCategories(
            List<String> names, long baseline) {
        List<SyncState.CategoryState> migrated = new ArrayList<>();
        for (String name : names) {
            String normalized = name == null ? "" : name.trim();
            if (!normalized.isEmpty()) {
                migrated.add(new SyncState.CategoryState(normalized, baseline, 0));
            }
        }
        return migrated;
    }
}
