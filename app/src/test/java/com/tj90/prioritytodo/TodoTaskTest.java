package com.tj90.prioritytodo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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

    @Test
    public void sheetKeyboardFrameKeepsPanelAboveImeAndWithinScreen() {
        MainActivity.SheetKeyboardFrame frame = MainActivity.sheetKeyboardFrame(
                1280, 520, 8, 12, 180);

        assertEquals(528, frame.bottomMarginPx);
        assertEquals(740, frame.maxHeightPx);
    }

    @Test
    public void sheetKeyboardFrameUsesNaturalHeightWhenImeIsHidden() {
        MainActivity.SheetKeyboardFrame frame = MainActivity.sheetKeyboardFrame(
                1280, 0, 8, 12, 180);

        assertEquals(0, frame.bottomMarginPx);
        assertEquals(1268, frame.maxHeightPx);
    }

    @Test
    public void csvRoundTripPreservesEditableTaskFields() {
        TodoTask task = new TodoTask();
        task.id = "task-1";
        task.title = "Call, email \"Sam\"";
        task.notes = "Follow up on price";
        task.completed = true;
        task.impact = TodoTask.MEDIUM;
        task.effort = TodoTask.LOW;
        task.urgent = true;
        task.quickTask = true;
        task.snoozed = true;
        task.recurringMit = true;
        task.category = "Work";
        task.dependency = "Sequential";
        task.reminderAt = 123456L;
        task.reminderRepeatUnit = TodoTask.REPEAT_WEEK;
        task.reminderRepeatEvery = 2;
        task.createdAt = 99L;

        List<TodoTask> restored = CsvCodec.importTasks(CsvCodec.exportTasks(Arrays.asList(task)));

        assertEquals(1, restored.size());
        TodoTask copy = restored.get(0);
        assertEquals("task-1", copy.id);
        assertEquals("Call, email \"Sam\"", copy.title);
        assertEquals("Follow up on price", copy.notes);
        assertTrue(copy.completed);
        assertEquals(TodoTask.MEDIUM, copy.impact);
        assertEquals(TodoTask.LOW, copy.effort);
        assertTrue(copy.urgent);
        assertTrue(copy.quickTask);
        assertTrue(copy.snoozed);
        assertTrue(copy.recurringMit);
        assertEquals("Work", copy.category);
        assertEquals("Sequential", copy.dependency);
        assertEquals(123456L, copy.reminderAt);
        assertEquals(TodoTask.REPEAT_WEEK, copy.reminderRepeatUnit);
        assertEquals(2, copy.reminderRepeatEvery);
        assertEquals(99L, copy.createdAt);
    }

    @Test
    public void csvImportSkipsBlankTitlesAndAcceptsHumanLevelNames() {
        String csv = "ID,Title,Impact,Effort,Urgent,QuickTask,RepeatUnit\n"
                + "skip,,High,Low,false,false\n"
                + "keep,Ship fix,High,Low,yes,1,Week\n";

        List<TodoTask> restored = CsvCodec.importTasks(csv);

        assertEquals(1, restored.size());
        TodoTask task = restored.get(0);
        assertEquals("keep", task.id);
        assertEquals("Ship fix", task.title);
        assertEquals(TodoTask.HIGH, task.impact);
        assertEquals(TodoTask.LOW, task.effort);
        assertTrue(task.urgent);
        assertTrue(task.quickTask);
        assertEquals(TodoTask.REPEAT_WEEK, task.reminderRepeatUnit);
    }
}
