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
            TimerAlarms.signalFinished(context, state);
        } else if (state.running && state.token.equals(intent.getStringExtra("token"))) {
            // A wall-clock adjustment can deliver an alarm before its monotonic deadline.
            try {
                TimerAlarms.schedule(context, state);
            } catch (SecurityException ignored) {
                // The app will explain precise-alarm access when next opened.
            }
        }
    }
}
