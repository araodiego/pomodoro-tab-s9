package com.arao.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

final class TimerStore {
    private TimerStore() {}

    static TimerState load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE);
        TimerState state = new TimerState();
        state.duration = Math.max(TimerState.MINUTE,
                Math.min(60 * TimerState.MINUTE, prefs.getLong("duration", state.duration)));
        state.pausedRemaining = prefs.getLong("remaining", state.duration);
        state.deadline = prefs.getLong("deadline", 0);
        state.running = prefs.getBoolean("running", false);
        state.finished = prefs.getBoolean("finished", false);
        state.token = prefs.getString("token", "");
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        // Android cancels alarms at reboot. Never display an active timer without an alarm.
        if (prefs.getInt("boot", boot) != boot) state.reset();
        return state;
    }

    @android.annotation.SuppressLint("ApplySharedPref") // Persist before scheduling or exiting a receiver.
    static void save(Context context, TimerState state) {
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        context.getSharedPreferences("timer", Context.MODE_PRIVATE).edit()
                .putLong("duration", state.duration)
                .putLong("remaining", state.pausedRemaining)
                .putLong("deadline", state.deadline)
                .putBoolean("running", state.running)
                .putBoolean("finished", state.finished)
                .putString("token", state.token)
                .putInt("boot", boot)
                .commit();
    }
}
