package com.tj90.prioritytodo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class TaskViews {
    private TaskViews() { }

    static List<TodoTask> select(List<TodoTask> tasks, String category, boolean recurringOnly,
                                 String screen, String query, long now) {
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<TodoTask> selected = new ArrayList<>();
        for (TodoTask task : tasks) {
            if (task.completed || (category != null && !category.equals(task.category))) {
                continue;
            }
            if (("Focus".equals(screen) && task.snoozed)
                    || ("Later".equals(screen) && !task.snoozed)) {
                continue;
            }
            if (recurringOnly && (task.reminderAt <= 0
                    || TodoTask.REPEAT_NONE.equals(task.reminderRepeatUnit))) {
                continue;
            }
            if (!search.isEmpty() && !contains(task.title, search)
                    && !contains(task.notes, search) && !contains(task.category, search)) {
                continue;
            }
            selected.add(task);
        }
        selected.sort((first, second) -> TodoTask.compareForActiveList(first, second, now));
        return selected;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    static Calendar reminderPreset(Calendar now, String label) {
        Calendar reminder = (Calendar) now.clone();
        reminder.set(Calendar.MINUTE, 0);
        reminder.set(Calendar.SECOND, 0);
        reminder.set(Calendar.MILLISECOND, 0);
        if ("Later today".equals(label)) {
            reminder.set(Calendar.HOUR_OF_DAY, 18);
            if (!reminder.after(now)) {
                reminder.add(Calendar.DAY_OF_MONTH, 1);
            }
        } else if ("Tomorrow".equals(label)) {
            reminder.set(Calendar.HOUR_OF_DAY, 9);
            reminder.add(Calendar.DAY_OF_MONTH, 1);
        } else if ("Weekend".equals(label)) {
            reminder.set(Calendar.HOUR_OF_DAY, 10);
            int daysUntilSaturday = (Calendar.SATURDAY - reminder.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            reminder.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);
            if (!reminder.after(now)) {
                reminder.add(Calendar.DAY_OF_MONTH, 7);
            }
        } else {
            throw new IllegalArgumentException("Unknown reminder preset: " + label);
        }
        return reminder;
    }
}
