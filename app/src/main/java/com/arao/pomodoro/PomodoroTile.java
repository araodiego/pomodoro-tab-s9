package com.arao.pomodoro;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class PomodoroTile extends TileService {
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (prefs,key) -> render();

    @Override public void onStartListening() {
        super.onStartListening();
        getSharedPreferences("timer",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changed);
        render();
    }

    @Override public void onStopListening() {
        getSharedPreferences("timer",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changed);
        super.onStopListening();
    }

    private void render() {
        Tile tile = getQsTile();
        if (tile == null) return;
        TimerState state = TimerStore.load(this);
        String label = state.primaryAction();
        tile.setLabel(label);
        tile.setContentDescription("Pomodoro Work, " + label);
        tile.setState(state.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        TimerState displayed = TimerStore.load(this);
        if (isLocked()) {
            unlockAndRun(() -> handleClick(displayed));
        } else handleClick(displayed);
    }

    private void handleClick(TimerState displayed) {
        TimerState current = TimerController.finishIfDue(this);
        if (!current.token.equals(displayed.token) || current.running != displayed.running
                || !current.primaryAction().equals(displayed.primaryAction())) {
            render();
            return;
        }
        if (current.running) TimerController.pause(this,current.token);
        else if (!TimerAlarms.canSchedule(this) || (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)) openTimer();
        else {
            try {
                TimerController.start(this,false,current.finished ? current.token : null);
            } catch (SecurityException denied) { openTimer(); }
        }
        render();
    }

    // The PendingIntent overload exists only on API 34+. Older systems require Intent.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void openTimer() {
        Intent intent = new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE));
        else startActivityAndCollapse(intent);
    }
}
