package com.tj90.prioritytodo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public void scoreMatrixIsExactUniqueAndUrgentlyPartitioned() {
        double[][] expected = {
                {303, 453, 903},
                {202, 302, 602},
                {101, 151, 301}
        };
        String[] levels = {TodoTask.HIGH, TodoTask.MEDIUM, TodoTask.LOW};
        Set<Double> scores = new HashSet<>();
        double normalMaximum = Double.NEGATIVE_INFINITY;
        double urgentMinimum = Double.POSITIVE_INFINITY;

        for (int impact = 0; impact < levels.length; impact++) {
            for (int effort = 0; effort < levels.length; effort++) {
                double normal = score(levels[impact], levels[effort], false, false);
                double urgent = score(levels[impact], levels[effort], true, false);
                assertEquals(expected[impact][effort], normal, 0.0);
                assertEquals(normal + 1000, urgent, 0.0);
                scores.add(normal);
                scores.add(urgent);
                normalMaximum = Math.max(normalMaximum, normal);
                urgentMinimum = Math.min(urgentMinimum, urgent);
            }
        }

        assertEquals(18, scores.size());
        assertEquals(903, normalMaximum, 0.0);
        assertEquals(1101, urgentMinimum, 0.0);
        assertTrue(urgentMinimum > normalMaximum);
    }

    @Test
    public void scoreOrdersTasksByReturnOnEffort() {
        double[] increasingRoiScores = {
                score(TodoTask.LOW, TodoTask.HIGH, false, false),
                score(TodoTask.LOW, TodoTask.MEDIUM, false, false),
                score(TodoTask.MEDIUM, TodoTask.HIGH, false, false),
                score(TodoTask.LOW, TodoTask.LOW, false, false),
                score(TodoTask.HIGH, TodoTask.MEDIUM, false, false),
                score(TodoTask.MEDIUM, TodoTask.LOW, false, false),
                score(TodoTask.HIGH, TodoTask.LOW, false, false)
        };

        for (int index = 1; index < increasingRoiScores.length; index++) {
            assertTrue(increasingRoiScores[index] > increasingRoiScores[index - 1]);
        }
    }

    @Test
    public void equalReturnOnEffortUsesImpactRankAsTieBreaker() {
        assertTrue(score(TodoTask.LOW, TodoTask.LOW, false, false)
                < score(TodoTask.MEDIUM, TodoTask.MEDIUM, false, false));
        assertTrue(score(TodoTask.MEDIUM, TodoTask.MEDIUM, false, false)
                < score(TodoTask.HIGH, TodoTask.HIGH, false, false));
    }

    @Test
    public void scoreBucketsRemainCompatibleWithExistingThresholds() {
        assertEquals("Immediate", task(TodoTask.LOW, TodoTask.HIGH, true, false).bucket());
        assertEquals("Next week", task(TodoTask.HIGH, TodoTask.LOW, false, false).bucket());
        assertEquals("Next week", task(TodoTask.MEDIUM, TodoTask.LOW, false, false).bucket());
        assertEquals("Someday", task(TodoTask.LOW, TodoTask.HIGH, false, false).bucket());
    }

    @Test
    public void snoozedPenaltyKeepsEverySnoozedTaskBelowActiveTasks() {
        double activeMinimum = score(TodoTask.LOW, TodoTask.HIGH, false, false);
        double snoozedMaximum = score(TodoTask.HIGH, TodoTask.LOW, true, true);

        assertEquals(101, activeMinimum, 0.0);
        assertEquals(-3097, snoozedMaximum, 0.0);
        assertEquals(score(TodoTask.HIGH, TodoTask.LOW, true, false) - 5000,
                snoozedMaximum, 0.0);
        assertTrue(snoozedMaximum < activeMinimum);
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

    private static double score(String impact, String effort, boolean urgent, boolean snoozed) {
        return task(impact, effort, urgent, snoozed).score();
    }

    private static TodoTask task(String impact, String effort, boolean urgent, boolean snoozed) {
        TodoTask task = new TodoTask();
        task.impact = impact;
        task.effort = effort;
        task.urgent = urgent;
        task.snoozed = snoozed;
        return task;
    }
}
