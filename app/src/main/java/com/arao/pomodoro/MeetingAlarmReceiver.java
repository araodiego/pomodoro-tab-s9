package com.arao.pomodoro;

import android.content.*;
import java.time.Instant;

public final class MeetingAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id",-1);
        if (id < 0) return;
        PendingResult pending = goAsync();
        MeetingStore.read(c, meetings -> {
            MeetingSchedule m = MeetingStore.find(meetings, id);
            if (m == null || m.revision() != intent.getIntExtra("revision",-1)) return;
            boolean start = intent.getBooleanExtra("start", false);
            Instant now = Instant.now();
            MeetingAlarms.deliver(c, m, start, intent.getLongExtra("occurrence", 0), now);
            MeetingAlarms.schedule(c, m, start, now);
        }, pending::finish);
    }
}
