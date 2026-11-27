package com.arao.pomodoro;

import android.content.*;

public final class MeetingActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id",-1);
        if (id < 0) return;
        PendingResult pending = goAsync();
        MeetingStore.read(c, meetings -> {
            MeetingSchedule m = MeetingStore.find(meetings, id);
            long occurrence = intent.getLongExtra("occurrence", 0);
            if (m == null || occurrence == 0 || m.revision() != intent.getIntExtra("revision", -1)
                    || MeetingStore.pending(c, m) != occurrence) return;
            if (intent.getBooleanExtra("pause", false))
                TimerController.pause(c, intent.getStringExtra("token"));
            MeetingAlarms.acknowledge(c, m, occurrence);
        }, pending::finish);
    }
}
