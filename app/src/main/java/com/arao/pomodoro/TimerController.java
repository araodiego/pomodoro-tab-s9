package com.arao.pomodoro;

import android.content.Context;
import android.os.SystemClock;
import java.util.UUID;

/** The activity and notification actions share the same persisted transition and alarm. */
final class TimerController {
    private TimerController() {}

    static boolean pause(Context context, String expectedToken) {
        TimerState state = finishIfDue(context);
        if (!state.running || expectedToken == null || !state.token.equals(expectedToken)) return false;
        long now = SystemClock.elapsedRealtime();
        if (state.remaining(now) == 0) {
            finishIfDue(context);
            return false;
        }
        state.pause(now);
        TimerStore.save(context, state);
        TimerAlarms.cancelAlarm(context);
        CountdownNotification.refresh(context,state);
        return true;
    }

    static boolean start(Context context, boolean focusAgain, String completionToken) {
        TimerState previous = TimerStore.load(context);
        if (previous.running || (completionToken != null && !previous.matchesCompletion(completionToken))) {
            return false;
        }
        if (!TimerAlarms.canSchedule(context)) throw new SecurityException("Precise alarms are disabled");
        TimerState next = TimerStore.load(context);
        String token = UUID.randomUUID().toString();
        boolean started = focusAgain ? next.startFocus(SystemClock.elapsedRealtime(), token)
                : next.startNext(SystemClock.elapsedRealtime(), token);
        if (!started) return false;
        try {
            TimerStore.save(context, next);
            TimerAlarms.schedule(context, next);
        } catch (SecurityException error) {
            TimerStore.save(context, previous);
            throw error;
        }
        TimerAlarms.dismissAlert(context);
        return true;
    }

    static TimerState finishIfDue(Context context) {
        TimerState state = TimerStore.load(context);
        if (state.complete(SystemClock.elapsedRealtime(), state.token)) {
            TimerStore.save(context, state);
            TimerAlarms.cancelAlarm(context);
            TimerAlarms.signalFinished(context, state);
        }
        return state;
    }
}
