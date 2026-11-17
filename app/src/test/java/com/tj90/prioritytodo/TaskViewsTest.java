package com.tj90.prioritytodo;

import org.junit.Test;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class TaskViewsTest {
    private TodoTask task(String title, String category, boolean later, boolean done) {
        TodoTask task = new TodoTask();
        task.title = title;
        task.category = category;
        task.snoozed = later;
        task.completed = done;
        task.impact = TodoTask.LOW;
        task.effort = TodoTask.LOW;
        return task;
    }

    @Test public void screensSeparateLaterAndDoneWhileTasksPreserveActivePriorityOrder() {
        TodoTask active = task("Active", null, false, false);
        TodoTask later = task("Later", null, true, false);
        TodoTask done = task("Done", null, false, true);
        List<TodoTask> all = Arrays.asList(later, done, active);
        assertEquals(Arrays.asList(active, later), TaskViews.select(all, null, false, "Tasks", "", 100));
        assertEquals(Arrays.asList(active), TaskViews.select(all, null, false, "Focus", "", 100));
        assertEquals(Arrays.asList(later), TaskViews.select(all, null, false, "Later", "", 100));
    }

    @Test public void searchMatchesTitleNotesAndListWithoutChangingSavedTasks() {
        TodoTask task = task("Plan budget", "Work", false, false);
        task.notes = "Ask SARAH about next quarter";
        List<TodoTask> all = Arrays.asList(task);
        assertEquals(all, TaskViews.select(all, null, false, "Tasks", " sarah ", 100));
        assertEquals(all, TaskViews.select(all, null, false, "Tasks", "work", 100));
        assertEquals(all, TaskViews.select(all, null, false, "Tasks", "BUDGET", 100));
        assertTrue(TaskViews.select(all, null, false, "Tasks", "missing", 100).isEmpty());
        assertEquals("Plan budget", task.title);
    }

    @Test public void recurringFilterRequiresActualReminderAndCanCombineWithListAndSearch() {
        TodoTask recurring = task("Review", "Work", false, false);
        recurring.reminderAt = 200;
        recurring.reminderRepeatUnit = TodoTask.REPEAT_WEEK;
        TodoTask noReminder = task("Review", "Work", false, false);
        noReminder.reminderRepeatUnit = TodoTask.REPEAT_WEEK;
        TodoTask oneOff = task("Review", "Work", false, false);
        oneOff.reminderAt = 200;
        List<TodoTask> all = Arrays.asList(noReminder, oneOff, recurring);
        assertEquals(Arrays.asList(recurring), TaskViews.select(all, "Work", true, "Tasks", "review", 100));
        assertTrue(TaskViews.select(all, "Home", true, "Tasks", "", 100).isEmpty());
    }

    @Test public void focusKeepsReminderAwareOrderingAndExcludesLater() {
        TodoTask urgent = task("Urgent", null, false, false);
        urgent.urgent = true;
        TodoTask due = task("Due", null, false, false);
        due.reminderAt = 90;
        TodoTask later = task("Later", null, true, false);
        later.reminderAt = 80;
        assertEquals(Arrays.asList(due, urgent), TaskViews.select(Arrays.asList(urgent, later, due), null, false, "Focus", "", 100));
    }

    private Calendar at(int day, int hour) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        now.clear();
        now.set(2026, Calendar.NOVEMBER, day, hour, 30);
        return now;
    }

    @Test public void reminderShortcutsAlwaysChooseFutureLocalCalendarTimes() {
        Calendar now = at(17, 19); // Tuesday, already after 6pm
        Calendar later = TaskViews.reminderPreset(now, "Later today");
        assertEquals(18, later.get(Calendar.HOUR_OF_DAY));
        assertEquals(18, later.get(Calendar.DAY_OF_MONTH));
        Calendar tomorrow = TaskViews.reminderPreset(now, "Tomorrow");
        assertEquals(18, tomorrow.get(Calendar.DAY_OF_MONTH));
        assertEquals(9, tomorrow.get(Calendar.HOUR_OF_DAY));
        Calendar weekend = TaskViews.reminderPreset(now, "Weekend");
        assertEquals(Calendar.SATURDAY, weekend.get(Calendar.DAY_OF_WEEK));
        assertEquals(10, weekend.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, weekend.get(Calendar.MINUTE));
        assertEquals(19, now.get(Calendar.HOUR_OF_DAY));
    }

    @Test public void saturdayShortcutAdvancesAWeekAfterTen() {
        Calendar now = at(21, 11);
        Calendar next = TaskViews.reminderPreset(now, "Weekend");
        assertEquals(28, next.get(Calendar.DAY_OF_MONTH));
        assertTrue(next.after(now));
    }
}
