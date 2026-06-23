package com.tj90.prioritytodo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class ReminderScheduler {
    private static final String ACTION_REMINDER = "com.tj90.prioritytodo.REMINDER";

    private ReminderScheduler() {
    }

    static void schedule(Context context, TodoTask task) {
        cancel(context, task);
        if (task.completed || task.reminderAt <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = reminderIntent(context, task, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.reminderAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.reminderAt, pendingIntent);
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.reminderAt, pendingIntent);
        }
    }

    static void cancel(Context context, TodoTask task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent = reminderIntent(context, task, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }

    private static PendingIntent reminderIntent(Context context, TodoTask task, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER + "." + task.id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_TITLE, task.title);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_SCORE, task.scoreLabel());
        int requestCode = task.id.hashCode();
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                flags | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
