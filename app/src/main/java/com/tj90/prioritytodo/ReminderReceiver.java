package com.tj90.prioritytodo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

public final class ReminderReceiver extends BroadcastReceiver {
    static final String EXTRA_TASK_ID = "task_id";
    static final String EXTRA_TASK_TITLE = "task_title";
    static final String EXTRA_TASK_SCORE = "task_score";

    private static final String CHANNEL_ID = "task_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_TASK_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            rescheduleRecurringReminder(context, id);
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            rescheduleRecurringReminder(context, id);
            return;
        }
        createChannel(context, manager);

        String title = intent.getStringExtra(EXTRA_TASK_TITLE);
        String score = intent.getStringExtra(EXTRA_TASK_SCORE);
        if (title == null || title.trim().isEmpty()) {
            title = "Priority task";
        }
        if (score == null) {
            score = "0";
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText("Reminder due now. Score " + score + ".")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();

        int notificationId = id == null ? 0 : id.hashCode();
        manager.notify(notificationId, notification);
        rescheduleRecurringReminder(context, id);
    }

    static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Date and time reminders for priority tasks");
        manager.createNotificationChannel(channel);
    }

    private void rescheduleRecurringReminder(Context context, String taskId) {
        if (taskId == null) {
            return;
        }

        TaskStore store = new TaskStore(context);
        List<TodoTask> tasks = store.load();
        for (TodoTask task : tasks) {
            if (!taskId.equals(task.id) || task.completed || !task.repeatsReminder()) {
                continue;
            }

            long nextReminder = task.nextReminderAfter(System.currentTimeMillis());
            if (nextReminder > 0) {
                task.reminderAt = nextReminder;
                store.save(tasks);
                ReminderScheduler.schedule(context, task);
            }
            return;
        }
    }
}
