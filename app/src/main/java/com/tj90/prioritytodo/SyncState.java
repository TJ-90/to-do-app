package com.tj90.prioritytodo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SyncState {
    final List<TodoTask> tasks;
    final List<TaskTombstone> taskTombstones;
    final List<CategoryState> categories;

    SyncState(List<TodoTask> tasks, List<TaskTombstone> taskTombstones,
              List<CategoryState> categories) {
        this.tasks = new ArrayList<>(tasks);
        this.taskTombstones = new ArrayList<>(taskTombstones);
        this.categories = new ArrayList<>(categories);
    }

    JSONObject toJson() throws JSONException {
        JSONArray taskArray = new JSONArray();
        for (TodoTask task : tasks) {
            taskArray.put(task.toJson());
        }
        JSONArray tombstoneArray = new JSONArray();
        for (TaskTombstone tombstone : taskTombstones) {
            tombstoneArray.put(tombstone.toJson());
        }
        JSONArray categoryArray = new JSONArray();
        for (CategoryState category : categories) {
            categoryArray.put(category.toJson());
        }
        JSONObject json = new JSONObject();
        json.put("tasks", taskArray);
        json.put("taskTombstones", tombstoneArray);
        json.put("categories", categoryArray);
        return json;
    }

    boolean hasSameWireState(SyncState other) {
        try {
            return canonicalJson().toString().equals(other.canonicalJson().toString());
        } catch (JSONException exception) {
            return false;
        }
    }

    long nextMutationTimestamp(long wallNow) {
        long latest = Math.max(0, wallNow);
        for (TodoTask task : tasks) {
            latest = Math.max(latest, incrementSafely(task.updatedAt));
        }
        for (TaskTombstone tombstone : taskTombstones) {
            latest = Math.max(latest, incrementSafely(tombstone.deletedAt));
        }
        for (CategoryState category : categories) {
            latest = Math.max(latest, incrementSafely(category.updatedAt));
            latest = Math.max(latest, incrementSafely(category.deletedAt));
        }
        return latest;
    }

    private JSONObject canonicalJson() throws JSONException {
        List<TodoTask> sortedTasks = new ArrayList<>(tasks);
        sortedTasks.sort(Comparator.comparing(task -> task.id));
        List<TaskTombstone> sortedTombstones = new ArrayList<>(taskTombstones);
        sortedTombstones.sort(Comparator.comparing(tombstone -> tombstone.id));
        List<CategoryState> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort(Comparator.comparing(category -> category.name));
        return new SyncState(sortedTasks, sortedTombstones, sortedCategories).toJson();
    }

    private static long incrementSafely(long value) {
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, value) + 1;
    }

    static SyncState fromJson(JSONObject json) {
        List<TodoTask> tasks = new ArrayList<>();
        JSONArray taskArray = json.optJSONArray("tasks");
        if (taskArray != null) {
            for (int index = 0; index < taskArray.length(); index++) {
                JSONObject value = taskArray.optJSONObject(index);
                if (value != null) {
                    tasks.add(TodoTask.fromJson(value));
                }
            }
        }

        List<TaskTombstone> tombstones = new ArrayList<>();
        JSONArray tombstoneArray = json.optJSONArray("taskTombstones");
        if (tombstoneArray != null) {
            for (int index = 0; index < tombstoneArray.length(); index++) {
                JSONObject value = tombstoneArray.optJSONObject(index);
                TaskTombstone tombstone = TaskTombstone.fromJson(value);
                if (tombstone != null) {
                    tombstones.add(tombstone);
                }
            }
        }

        List<CategoryState> categories = new ArrayList<>();
        JSONArray categoryArray = json.optJSONArray("categories");
        if (categoryArray != null) {
            for (int index = 0; index < categoryArray.length(); index++) {
                JSONObject value = categoryArray.optJSONObject(index);
                CategoryState category = CategoryState.fromJson(value);
                if (category != null) {
                    categories.add(category);
                }
            }
        }
        return new SyncState(tasks, tombstones, categories);
    }

    static final class TaskTombstone {
        final String id;
        long deletedAt;

        TaskTombstone(String id, long deletedAt) {
            this.id = id;
            this.deletedAt = Math.max(0, deletedAt);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("deletedAt", deletedAt);
        }

        static TaskTombstone fromJson(JSONObject json) {
            if (json == null) {
                return null;
            }
            String id = json.optString("id", "").trim();
            return id.isEmpty() ? null : new TaskTombstone(id, json.optLong("deletedAt", 0));
        }
    }

    static final class CategoryState {
        String name;
        long updatedAt;
        long deletedAt;

        CategoryState(String name, long updatedAt, long deletedAt) {
            this.name = name;
            this.updatedAt = Math.max(0, updatedAt);
            this.deletedAt = Math.max(0, deletedAt);
        }

        boolean isActive() {
            return updatedAt > deletedAt;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("name", name)
                    .put("updatedAt", updatedAt)
                    .put("deletedAt", deletedAt);
        }

        static CategoryState fromJson(JSONObject json) {
            if (json == null) {
                return null;
            }
            String name = json.optString("name", "").trim();
            return name.isEmpty() ? null : new CategoryState(
                    name, json.optLong("updatedAt", 0), json.optLong("deletedAt", 0));
        }
    }
}
