package com.tj90.prioritytodo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
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
    public void taskDetailsNormalizeWithoutLosingInternalLineBreaks() {
        assertEquals("", MainActivity.normalizeNotes(null));
        assertEquals("", MainActivity.normalizeNotes("   \n  "));
        assertEquals("First step\nSecond step",
                MainActivity.normalizeNotes("  First step\nSecond step  "));
    }

    @Test
    public void existingDetailsExpandOnlyWhenEditingTaskThatHasThem() {
        assertFalse(MainActivity.shouldExpandNotes("add", "Reference"));
        assertFalse(MainActivity.shouldExpandNotes("edit", "  "));
        assertTrue(MainActivity.shouldExpandNotes("edit", "Reference"));
    }

    @Test
    public void listPickerLabelHandlesAssignedAndUnassignedTasks() {
        assertEquals("No list", MainActivity.listPillLabel(null));
        assertEquals("No list", MainActivity.listPillLabel("  "));
        assertEquals("EdMe", MainActivity.listPillLabel("EdMe"));
    }

    @Test
    public void csvRoundTripPreservesEditableTaskFields() {
        TodoTask task = new TodoTask();
        task.id = "task-1";
        task.title = "Call, email \"Sam\"";
        task.notes = "Follow up on price\nAttach signed sheet";
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
        task.updatedAt = 101L;

        List<TodoTask> restored = CsvCodec.importTasks(CsvCodec.exportTasks(Arrays.asList(task)));

        assertEquals(1, restored.size());
        TodoTask copy = restored.get(0);
        assertEquals("task-1", copy.id);
        assertEquals("Call, email \"Sam\"", copy.title);
        assertEquals("Follow up on price\nAttach signed sheet", copy.notes);
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
        assertEquals(101L, copy.updatedAt);
    }

    @Test
    public void legacyJsonUsesCreatedAtAsUpdatedAt() throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", "legacy");
        json.put("createdAt", 42L);

        TodoTask restored = TodoTask.fromJson(json);

        assertEquals(42L, restored.createdAt);
        assertEquals(42L, restored.updatedAt);
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

    @Test
    public void completingRecurringReminderCreatesNextOccurrenceFromCompletionTime() {
        Calendar reminder = Calendar.getInstance();
        reminder.set(2026, Calendar.AUGUST, 31, 9, 0, 0);
        reminder.set(Calendar.MILLISECOND, 0);
        Calendar completed = (Calendar) reminder.clone();
        completed.add(Calendar.HOUR_OF_DAY, 4);
        Calendar expectedNext = (Calendar) completed.clone();
        expectedNext.add(Calendar.DAY_OF_YEAR, 2);

        TodoTask task = new TodoTask();
        task.title = "Fix shower head";
        task.notes = "Buy a replacement washer";
        task.impact = TodoTask.MEDIUM;
        task.effort = TodoTask.LOW;
        task.category = "Home";
        task.urgent = true;
        task.quickTask = true;
        task.snoozed = true;
        task.recurringMit = true;
        task.reminderAt = reminder.getTimeInMillis();
        task.reminderRepeatUnit = TodoTask.REPEAT_DAY;
        task.reminderRepeatEvery = 2;

        TodoTask next = task.completeAndCreateNextOccurrence(
                completed.getTimeInMillis(), completed.getTimeInMillis() + 1);

        assertEquals("Fix shower head", next.title);
        assertEquals("Buy a replacement washer", next.notes);
        assertEquals(TodoTask.MEDIUM, next.impact);
        assertEquals(TodoTask.LOW, next.effort);
        assertEquals("Home", next.category);
        assertTrue(next.urgent);
        assertTrue(next.quickTask);
        assertTrue(next.recurringMit);
        assertFalse(next.completed);
        assertFalse(next.snoozed);
        assertEquals(task.id + ":next", next.id);
        assertTrue(task.completed);
        assertFalse(task.snoozed);
        assertEquals(completed.getTimeInMillis() + 1, task.updatedAt);
        assertEquals(expectedNext.getTimeInMillis(), next.reminderAt);
        assertEquals(TodoTask.REPEAT_DAY, next.reminderRepeatUnit);
        assertEquals(2, next.reminderRepeatEvery);
        assertEquals(completed.getTimeInMillis() + 1, next.createdAt);
        assertEquals(completed.getTimeInMillis() + 1, next.updatedAt);

        task.reopenAfterCompletion(completed.getTimeInMillis() + 2);
        assertFalse(task.completed);
        assertEquals(completed.getTimeInMillis() + 2, task.updatedAt);
        assertEquals(next.id, task.completeAndCreateNextOccurrence(
                completed.getTimeInMillis(), completed.getTimeInMillis() + 3).id);
    }

    @Test
    public void recurringReminderSupportsHoursAndYears() {
        Calendar completed = Calendar.getInstance();
        completed.set(2024, Calendar.FEBRUARY, 29, 10, 15, 0);
        completed.set(Calendar.MILLISECOND, 0);

        TodoTask hourly = new TodoTask();
        hourly.reminderAt = completed.getTimeInMillis();
        hourly.reminderRepeatUnit = TodoTask.REPEAT_HOUR;
        hourly.reminderRepeatEvery = 3;
        Calendar expectedHour = (Calendar) completed.clone();
        expectedHour.add(Calendar.HOUR_OF_DAY, 3);
        assertEquals(expectedHour.getTimeInMillis(),
                hourly.nextOccurrenceAfterCompletion(completed.getTimeInMillis(), 1).reminderAt);

        TodoTask yearly = new TodoTask();
        yearly.reminderAt = completed.getTimeInMillis();
        yearly.reminderRepeatUnit = TodoTask.REPEAT_YEAR;
        yearly.reminderRepeatEvery = 1;
        Calendar expectedYear = (Calendar) completed.clone();
        expectedYear.add(Calendar.YEAR, 1);
        assertEquals(expectedYear.getTimeInMillis(),
                yearly.nextOccurrenceAfterCompletion(completed.getTimeInMillis(), 1).reminderAt);

        assertEquals(TodoTask.REPEAT_HOUR, TodoTask.normalizeRepeatUnit("hourly"));
        assertEquals(TodoTask.REPEAT_YEAR, TodoTask.normalizeRepeatUnit("yearly"));
    }

    @Test
    public void nonRepeatingReminderDoesNotCreateAnotherOccurrence() {
        TodoTask task = new TodoTask();
        task.reminderAt = 100L;

        assertNull(task.nextOccurrenceAfterCompletion(200L, 201L));
    }

    @Test
    public void repeatIntervalAcceptsOnlyOneThroughNineHundredNinetyNine() {
        assertEquals(1, TodoTask.parseRepeatInterval("1"));
        assertEquals(999, TodoTask.parseRepeatInterval("999"));
        assertEquals(0, TodoTask.parseRepeatInterval(null));
        assertEquals(0, TodoTask.parseRepeatInterval(""));
        assertEquals(0, TodoTask.parseRepeatInterval("0"));
        assertEquals(0, TodoTask.parseRepeatInterval("1000"));
        assertEquals(0, TodoTask.parseRepeatInterval("999999999999"));
    }

    @Test
    public void overdueEditClearsOnlyOneOffReminder() {
        assertTrue(TodoTask.shouldClearExpiredReminder(100L, TodoTask.REPEAT_NONE, 101L));
        assertFalse(TodoTask.shouldClearExpiredReminder(100L, TodoTask.REPEAT_DAY, 101L));
        assertFalse(TodoTask.shouldClearExpiredReminder(102L, TodoTask.REPEAT_NONE, 101L));
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
