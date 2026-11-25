package com.arao.pomodoro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

final class CountdownNotification {
    private static final String CHANNEL = "live_countdown_v1";
    private static final int ID = 8;
    static final String PAUSE = "com.arao.pomodoro.PAUSE";
    static final String RESUME = "com.arao.pomodoro.RESUME";
    static final String DISMISS = "com.arao.pomodoro.DISMISS_COUNTDOWN";

    static void cancel(Context context) {
        context.getSystemService(NotificationManager.class).cancel(ID);
    }

    static void refresh(Context context, TimerState state) {
        long elapsed = SystemClock.elapsedRealtime();
        long remaining = state.remaining(elapsed);
        if (state.finished || state.token.isEmpty() || remaining == 0) {
            cancel(context);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        String identity = state.token + (state.running ? "/running" : "/paused");
        if (identity.equals(context.getSharedPreferences("work",0).getString("dismissedCountdown",""))) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL,"Focus and break countdown",
                NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
        String phase = state.phase == TimerState.Phase.FOCUS ? "Focus" : "Break";
        String title = state.running ? phase : phase + " paused · " + TimerState.format(remaining);
        long when = System.currentTimeMillis() + remaining;
        PendingIntent open = PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder safe = base(context,state,title,when,open);
        Notification.Builder detail = base(context,state,title,when,open)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(safe.setVisibility(Notification.VISIBILITY_PUBLIC).build());
        String jira = context.getSharedPreferences("work",0).getString("jiraKey","");
        if (!jira.isEmpty()) detail.setContentText(jira);
        detail.addAction(new Notification.Action.Builder(null,state.running ? "Pause" : "Resume",
                action(context,state.running ? PAUSE : RESUME,state.token,identity)).build());
        detail.setDeleteIntent(action(context,DISMISS,state.token,identity));
        if (Build.VERSION.SDK_INT >= 36 && state.running) {
            Bundle extras = new Bundle();
            // Android's public promotion extra, introduced in the 36.1 SDK.
            extras.putBoolean("android.requestPromotedOngoing",true);
            detail.addExtras(extras);
        }
        manager.notify(ID,detail.build());
    }

    private static Notification.Builder base(Context c,TimerState state,String title,long when,PendingIntent open) {
        return new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(title).setContentIntent(open).setOnlyAlertOnce(true)
                .setOngoing(state.running).setShowWhen(state.running).setWhen(when)
                .setUsesChronometer(state.running).setChronometerCountDown(state.running);
    }

    private static PendingIntent action(Context c,String action,String token,String identity) {
        Intent intent = new Intent(c,TimerActionReceiver.class).setAction(action)
                .setData(Uri.parse("pomodoro://countdown/" + identity))
                .putExtra("token",token).putExtra("identity",identity);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_IMMUTABLE);
    }
}
