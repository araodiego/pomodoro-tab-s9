package com.arao.pomodoro;

import android.content.*;
import java.time.Instant;

public final class MeetingAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int id = intent.getIntExtra("id",-1);
        if (id < 0 || id > 1) return;
        MeetingSchedule m = MeetingStore.load(c,id);
        if (m.revision() != intent.getIntExtra("revision",-1)) return;
        boolean start = intent.getBooleanExtra("start",false);
        Instant now = Instant.now();
        MeetingAlarms.deliver(c,m,start,intent.getLongExtra("occurrence",0),now);
        MeetingAlarms.schedule(c,m,start,now);
    }
}
