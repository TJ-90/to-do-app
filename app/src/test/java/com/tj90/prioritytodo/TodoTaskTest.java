package com.tj90.prioritytodo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class TodoTaskTest {
    @Test
    public void toJsonStoresNullCategoryAsJsonNull() throws Exception {
        TodoTask task = new TodoTask();

        JSONObject json = task.toJson();

        assertTrue(json.has("category"));
        assertTrue(json.isNull("category"));
    }

    @Test
    public void fromJsonRestoresAssignedCategory() throws Exception {
        TodoTask task = new TodoTask();
        task.category = "Work";

        TodoTask restored = TodoTask.fromJson(task.toJson());

        assertEquals("Work", restored.category);
    }

    @Test
    public void fromJsonTreatsMissingCategoryAsUnassigned() {
        TodoTask restored = TodoTask.fromJson(new JSONObject());

        assertNull(restored.category);
    }

    @Test
    public void fromJsonTreatsEmptyCategoryAsUnassigned() throws Exception {
        JSONObject json = new JSONObject();
        json.put("category", "");

        TodoTask restored = TodoTask.fromJson(json);

        assertNull(restored.category);
    }

    @Test
    public void scoreStillPrioritizesUrgentTasksAboveNonUrgentTasks() {
        TodoTask normal = new TodoTask();
        normal.impact = TodoTask.HIGH;
        normal.effort = TodoTask.LOW;

        TodoTask urgent = new TodoTask();
        urgent.impact = TodoTask.LOW;
        urgent.effort = TodoTask.HIGH;
        urgent.urgent = true;

        assertTrue(urgent.score() > normal.score());
        assertEquals("Immediate", urgent.bucket());
    }
}
