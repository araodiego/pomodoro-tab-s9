package com.arao.pomodoro;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.SystemClock;
import java.time.*;

final class MeetingAlarms {
    static final String ADVANCE = "meeting_advance_v1", START = "meeting_start_v1";
    static void channels(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        for (boolean start : new boolean[]{false, true}) {
            NotificationChannel channel = new NotificationChannel(start ? START : ADVANCE,
                    start ? "Meeting starts" : "Upcoming meetings", NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(Uri.parse("android.resource://" + c.getPackageName() + "/" +
                    (start ? R.raw.meeting_start : R.raw.meeting_advance)),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build());
            channel.enableVibration(true);
            channel.setVibrationPattern(start ? new long[]{0,300,100,300} : new long[]{0,120});
            manager.createNotificationChannel(channel);
        }
    }
    static boolean alertsEnabled(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        return manager.areNotificationsEnabled()
                && manager.getNotificationChannel(ADVANCE) != null
                && manager.getNotificationChannel(ADVANCE).getImportance() != NotificationManager.IMPORTANCE_NONE
                && manager.getNotificationChannel(START) != null
                && manager.getNotificationChannel(START).getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private static Intent event(Context c, MeetingSchedule m, boolean start, long occurrence) {
        return new Intent(c, MeetingAlarmReceiver.class)
                .setData(Uri.parse("pomodoro://meeting/" + m.id() + "/" + start))
                .putExtra("id", m.id()).putExtra("start", start)
                .putExtra("occurrence", occurrence).putExtra("revision", m.revision());
    }
    static void refresh(Context c) {
        channels(c);
        Instant now = Instant.now();
        for (MeetingSchedule m : MeetingStore.all(c)) {
            for (boolean isStart : new boolean[]{false,true}) {
                // Recover a recently due alarm when opening the app or changing the clock.
                int lead = isStart ? 0 : m.lead();
                boolean active = m.enabled() && m.configured() && (isStart ? m.startAlert() : lead > 0);
                if (active) {
                    Instant recent = m.next(now.minusSeconds(60), lead);
                    if (recent != null && !recent.isAfter(now))
                        deliver(c, m, isStart, recent.plusSeconds(lead * 60L).toEpochMilli(), now);
                }
                schedule(c, m, isStart, now);
            }
        }
    }
    static void schedule(Context c, MeetingSchedule m, boolean start, Instant now) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        PendingIntent cancel = PendingIntent.getBroadcast(c, 0, event(c,m,start,0),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (cancel != null) { manager.cancel(cancel); cancel.cancel(); }
        int lead = start ? 0 : m.lead();
        if (!m.enabled() || !m.configured() || (start ? !m.startAlert() : lead == 0)
                || !TimerAlarms.canSchedule(c)) return;
        Instant trigger = m.next(now, lead);
        if (trigger == null) return;
        PendingIntent fire = PendingIntent.getBroadcast(c, 0,
                event(c,m,start,trigger.plusSeconds(lead*60L).toEpochMilli()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(c, 20, new Intent(c,MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        try {
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(trigger.toEpochMilli(), show), fire);
        } catch (SecurityException ignored) { /* UI reports alarm access. */ }
    }
    static void deliver(Context c, MeetingSchedule m, boolean start, long occurrence, Instant now) {
        if (!MeetingPlanner.validReminder(m,start,occurrence,now) || !MeetingStore.claim(c,m.id(),start,occurrence)) return;
        channels(c);
        if (start) MeetingStore.pending(c,m,occurrence);
        TimerState timer = TimerController.finishIfDue(c);
        Notification.Builder builder = new Notification.Builder(c, start ? START : ADVANCE)
                .setSmallIcon(R.drawable.ic_timer).setCategory(Notification.CATEGORY_REMINDER)
                .setContentTitle(m.name() + (start ? " starts now" : " in " + m.lead() + " min"))
                .setContentText(MeetingSchedule.brazilTime(Instant.ofEpochMilli(occurrence)) + " São Paulo")
                .setContentIntent(PendingIntent.getActivity(c,20,new Intent(c,MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true)
                .setTimeoutAfter(start ? 600000 : m.lead() * 60000L);
        if (start) {
            if (timer.running) builder.addAction(new Notification.Action.Builder(null,"Pause for meeting",
                    action(c,m,occurrence,timer.token,true)).build());
            builder.addAction(new Notification.Action.Builder(null,timer.running ? "Keep focusing" : "Got it",
                    action(c,m,occurrence,"",false)).build());
        }
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (start) manager.cancel(notificationId(m.id(),false));
        if (android.os.Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED)
            manager.notify(notificationId(m.id(),start),builder.build());
    }
    static int notificationId(int id, boolean start) { return 40 + id*2 + (start ? 1 : 0); }
    private static PendingIntent action(Context c, MeetingSchedule m, long occurrence, String token, boolean pause) {
        Intent intent = new Intent(c,MeetingActionReceiver.class)
                .setData(Uri.parse("pomodoro://meeting-action/" + m.id() + "/" + occurrence + "/" + m.revision() + "/" + pause))
                .putExtra("id",m.id()).putExtra("occurrence",occurrence).putExtra("revision",m.revision())
                .putExtra("token",token).putExtra("pause",pause);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_IMMUTABLE);
    }
    static void acknowledge(Context c, MeetingSchedule m, long occurrence) {
        if (MeetingStore.pending(c,m) != occurrence) return;
        MeetingStore.acknowledge(c,m.id(),occurrence);
        c.getSystemService(NotificationManager.class).cancel(notificationId(m.id(),true));
    }
}
