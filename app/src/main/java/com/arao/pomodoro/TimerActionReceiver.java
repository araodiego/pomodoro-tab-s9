package com.arao.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimerActionReceiver extends BroadcastReceiver {
    static final String START_NEXT = "com.arao.pomodoro.START_NEXT";

    @Override public void onReceive(Context context, Intent intent) {
        if (!START_NEXT.equals(intent.getAction())) return;
        String token = intent.getStringExtra("completionToken");
        if (token == null || token.isEmpty()) return;
        try {
            // Old notifications and repeated taps cannot restart or change a newer timer.
            TimerController.start(context, false, token);
        } catch (SecurityException ignored) {
            // Keep the completion notification available; tapping its body opens the app.
        }
    }
}
