package com.arao.pomodoro;

import android.content.*;
import android.app.AlarmManager;

public final class MeetingScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            PendingResult pending = goAsync();
            MeetingStore.read(c, meetings -> MeetingAlarms.refresh(c, meetings), pending::finish);
        }
    }
}
