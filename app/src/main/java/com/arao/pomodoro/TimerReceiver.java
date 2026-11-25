package com.arao.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public final class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        TimerState state = TimerStore.load(context);
        if (state.complete(SystemClock.elapsedRealtime(), intent.getStringExtra("token"))) {
            TimerStore.save(context, state);
            TimerAlarms.signalFinished(context);
        }
    }
}
