package com.tj90.prioritytodo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class SyncStateTest {
    @Test
    public void codecUsesExactWireKeysAndRoundTripsAllState() throws Exception {
        TodoTask task = new TodoTask();
        task.id = "task-1";
        task.title = "Ship sync";
        task.notes = "Keep working offline";
        task.impact = TodoTask.MEDIUM;
        task.effort = TodoTask.LOW;
        task.dependency = "Sequential";
        task.category = null;
        task.urgent = true;
        task.quickTask = true;
        task.snoozed = true;
        task.recurringMit = true;
        task.completed = true;
        task.createdAt = 10L;
        task.updatedAt = 20L;
        task.reminderAt = 30L;
        task.reminderRepeatUnit = TodoTask.REPEAT_WEEK;
        task.reminderRepeatEvery = 2;
        SyncState state = new SyncState(
                Arrays.asList(task),
                Arrays.asList(new SyncState.TaskTombstone("gone", 40L)),
                Arrays.asList(
                        new SyncState.CategoryState("Work", 50L, 0L),
                        new SyncState.CategoryState("Old", 5L, 60L)));

        JSONObject json = state.toJson();

        assertEquals(3, json.length());
        assertTrue(json.has("tasks"));
        assertTrue(json.has("taskTombstones"));
        assertTrue(json.has("categories"));
        JSONObject taskJson = json.getJSONArray("tasks").getJSONObject(0);
        assertTrue(taskJson.has("updatedAt"));
        assertTrue(taskJson.isNull("category"));

        SyncState restored = SyncState.fromJson(json);
        TodoTask copy = restored.tasks.get(0);
        assertEquals("task-1", copy.id);
        assertEquals("Ship sync", copy.title);
        assertEquals("Keep working offline", copy.notes);
        assertEquals(TodoTask.MEDIUM, copy.impact);
        assertEquals(TodoTask.LOW, copy.effort);
        assertEquals("Sequential", copy.dependency);
        assertNull(copy.category);
        assertTrue(copy.urgent);
        assertTrue(copy.quickTask);
        assertTrue(copy.snoozed);
        assertTrue(copy.recurringMit);
        assertTrue(copy.completed);
        assertEquals(10L, copy.createdAt);
        assertEquals(20L, copy.updatedAt);
        assertEquals(30L, copy.reminderAt);
        assertEquals(TodoTask.REPEAT_WEEK, copy.reminderRepeatUnit);
        assertEquals(2, copy.reminderRepeatEvery);
        assertEquals("gone", restored.taskTombstones.get(0).id);
        assertEquals(40L, restored.taskTombstones.get(0).deletedAt);
        assertTrue(restored.categories.get(0).isActive());
        assertFalse(restored.categories.get(1).isActive());
    }

    @Test
    public void missingArraysBecomeEmptyLists() {
        SyncState state = SyncState.fromJson(new JSONObject());

        assertTrue(state.tasks.isEmpty());
        assertTrue(state.taskTombstones.isEmpty());
        assertTrue(state.categories.isEmpty());
    }

    @Test
    public void legacyTaskUpdatedAtFallsBackToCreatedAt() throws Exception {
        JSONObject task = new JSONObject();
        task.put("id", "legacy");
        task.put("createdAt", 77L);
        JSONObject json = new JSONObject();
        json.put("tasks", new JSONArray().put(task));

        SyncState state = SyncState.fromJson(json);

        assertEquals(77L, state.tasks.get(0).updatedAt);
    }

    @Test
    public void syncFieldsDoNotChangePriorityScore() {
        TodoTask task = new TodoTask();
        task.impact = TodoTask.HIGH;
        task.effort = TodoTask.LOW;
        task.urgent = true;
        double before = task.score();

        task.updatedAt = Long.MAX_VALUE;

        assertEquals(before, task.score(), 0.0);
    }

    @Test
    public void wireEqualityDetectsIdenticalAndChangedState() throws Exception {
        TodoTask first = new TodoTask();
        first.id = "same";
        first.title = "Original";
        TodoTask copy = TodoTask.fromJson(first.toJson());

        SyncState left = new SyncState(Arrays.asList(first), Arrays.asList(), Arrays.asList());
        SyncState right = new SyncState(Arrays.asList(copy), Arrays.asList(), Arrays.asList());

        assertTrue(left.hasSameWireState(right));
        copy.title = "Changed";
        assertFalse(left.hasSameWireState(right));
    }

    @Test
    public void wireEqualityIgnoresCollectionOrderWithoutReorderingInputs() throws Exception {
        TodoTask alpha = task("a", "Alpha", 10L);
        TodoTask beta = task("b", "Beta", 20L);
        List<TodoTask> originalOrder = Arrays.asList(beta, alpha);
        SyncState left = new SyncState(
                originalOrder,
                Arrays.asList(
                        new SyncState.TaskTombstone("gone-b", 40L),
                        new SyncState.TaskTombstone("gone-a", 30L)),
                Arrays.asList(
                        new SyncState.CategoryState("Work", 50L, 0L),
                        new SyncState.CategoryState("Home", 60L, 0L)));
        SyncState right = new SyncState(
                Arrays.asList(task("a", "Alpha", 10L), task("b", "Beta", 20L)),
                Arrays.asList(
                        new SyncState.TaskTombstone("gone-a", 30L),
                        new SyncState.TaskTombstone("gone-b", 40L)),
                Arrays.asList(
                        new SyncState.CategoryState("Home", 60L, 0L),
                        new SyncState.CategoryState("Work", 50L, 0L)));

        assertTrue(left.hasSameWireState(right));
        assertEquals("b", left.tasks.get(0).id);
        assertEquals("Work", left.categories.get(0).name);

        right.taskTombstones.get(0).deletedAt++;
        assertFalse(left.hasSameWireState(right));
    }

    @Test
    public void nextMutationTimestampAdvancesPastAllObservedRemoteState() {
        SyncState state = new SyncState(
                Arrays.asList(task("task", "Task", 900L)),
                Arrays.asList(new SyncState.TaskTombstone("gone", 1000L)),
                Arrays.asList(
                        new SyncState.CategoryState("Active", 1100L, 0L),
                        new SyncState.CategoryState("Deleted", 10L, 1200L)));

        assertEquals(1201L, state.nextMutationTimestamp(100L));
        assertEquals(2000L, state.nextMutationTimestamp(2000L));
    }

    @Test
    public void responseEligibilityRequiresMatchingRevisionAndEndpoint() {
        assertTrue(SyncClient.responseMatchesRequest(4L, 4L, "http://old", "http://old"));
        assertFalse(SyncClient.responseMatchesRequest(4L, 5L, "http://old", "http://old"));
        assertFalse(SyncClient.responseMatchesRequest(4L, 4L, "http://old", "http://new"));
    }

    @Test
    public void legacyCategoryMigrationIsStableAndUsesProvidedLogicalBaseline() {
        List<SyncState.CategoryState> migrated = TaskStore.migrateLegacyCategories(
                Arrays.asList(" Work ", "", null, "Home"), 701L);

        assertEquals(2, migrated.size());
        assertEquals("Work", migrated.get(0).name);
        assertEquals(701L, migrated.get(0).updatedAt);
        assertEquals("Home", migrated.get(1).name);
        assertEquals(701L, migrated.get(1).updatedAt);
    }

    @Test
    public void syncBaseUrlAcceptsHttpAndHttpsAndRemovesTrailingSlash() {
        assertEquals("http://10.0.2.2:8787", SyncClient.normalizeBaseUrl(" http://10.0.2.2:8787/ "));
        assertEquals("https://example.test/sync", SyncClient.normalizeBaseUrl("https://example.test/sync///"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void syncBaseUrlRejectsNonHttpSchemes() {
        SyncClient.normalizeBaseUrl("file:///tmp/state.json");
    }

    @Test(expected = java.io.IOException.class)
    public void syncClientRejectsAResponseMissingTheWireArrays() throws Exception {
        SyncClient.parseResponse("{}");
    }

    private static TodoTask task(String id, String title, long updatedAt) {
        TodoTask task = new TodoTask();
        task.id = id;
        task.title = title;
        task.createdAt = updatedAt;
        task.updatedAt = updatedAt;
        return task;
    }
}
