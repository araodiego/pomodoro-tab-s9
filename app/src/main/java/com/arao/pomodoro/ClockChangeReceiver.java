package com.arao.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ClockChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) return;
        TimerState state = TimerController.finishIfDue(context);
        if (!state.running) return;
        try {
            TimerAlarms.schedule(context, state);
        } catch (SecurityException ignored) {
            // Reopening the app handles a revoked precise-alarm permission.
        }
    }
}
