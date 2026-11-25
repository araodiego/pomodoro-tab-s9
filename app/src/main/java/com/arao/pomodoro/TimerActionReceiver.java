package com.arao.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimerActionReceiver extends BroadcastReceiver {
    static final String START_NEXT = "com.arao.pomodoro.START_NEXT";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (CountdownNotification.DISMISS.equals(action)) {
            context.getSharedPreferences("work",0).edit().putString("dismissedCountdown",
                    intent.getStringExtra("identity")).apply();
            return;
        }
        String session = intent.getStringExtra("token");
        if (CountdownNotification.PAUSE.equals(action)) {
            TimerController.pause(context,session);
            return;
        }
        if (CountdownNotification.RESUME.equals(action)) {
            TimerState state = TimerStore.load(context);
            if (session != null && !session.isEmpty() && state.token.equals(session) && !state.running && !state.finished) {
                try { TimerController.start(context,false,null); }
                catch (SecurityException denied) { /* The notification body opens the timer for settings. */ }
            }
            return;
        }
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
