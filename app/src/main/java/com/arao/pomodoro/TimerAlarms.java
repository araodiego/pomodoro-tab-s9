package com.arao.pomodoro;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

final class TimerAlarms {
    static final String CHANNEL = "timer_finished_v1";
    static final int NOTIFICATION_ID = 7;

    private TimerAlarms() {}

    static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Timer finished",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("A short bell when the countdown reaches zero.");
        channel.setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.bell),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] {0, 250, 150, 250});
        manager.createNotificationChannel(channel);
    }

    static boolean canSchedule(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    }

    private static PendingIntent pending(Context context, String token) {
        Intent intent = new Intent(context, TimerReceiver.class).putExtra("token", token);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context context, TimerState state) {
        context.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, state.deadline, pending(context, state.token));
    }

    static void cancel(Context context) {
        context.getSystemService(AlarmManager.class).cancel(pending(context, ""));
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }

    static boolean alertsEnabled(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && channel != null
                && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void signalFinished(Context context) {
        if (!alertsEnabled(context)) {
            if (MainActivity.isVisible()) {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500);
                new Handler(Looper.getMainLooper()).postDelayed(tone::release, 1800);
            }
            return;
        }
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Time's up")
                .setContentText("Your timer has finished.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }
}
