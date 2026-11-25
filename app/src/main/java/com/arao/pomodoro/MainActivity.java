package com.arao.pomodoro;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static boolean visible;
    static boolean isVisible() { return visible; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimerState state;
    private TimerDialView dial;
    private Button start;
    private TextView instruction;
    private TextView alerts;
    private boolean startAfterPermission;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (state.running && state.remaining(SystemClock.elapsedRealtime()) == 0) finishIfDue();
            render();
            handler.postDelayed(this, 200);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimerAlarms.createChannel(this);
        state = TimerStore.load(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xFF192522);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(dp(24) + bars.left, dp(20) + bars.top, dp(24) + bars.right, dp(20) + bars.bottom);
            } else {
                root.setPadding(dp(24) + insets.getSystemWindowInsetLeft(), dp(20) + insets.getSystemWindowInsetTop(),
                        dp(24) + insets.getSystemWindowInsetRight(), dp(20) + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        TextView title = text("pomodoro", 23, 0xFFF1EDE1);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setLetterSpacing(.035f);
        root.addView(title);
        TextView subtitle = text("A little space to focus.", 13, 0xFF9FAFA4);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-2, -2);
        subtitleParams.topMargin = dp(7);
        root.addView(subtitle, subtitleParams);

        dial = new TimerDialView(this);
        dial.setId(R.id.timer_dial);
        dial.setListener(minutes -> {
            TimerAlarms.cancel(this);
            state.selectMinutes(minutes);
            TimerStore.save(this, state);
            render();
        });
        root.addView(dial, new LinearLayout.LayoutParams(-1, 0, 1));

        instruction = text("Turn the dial to set your minutes", 14, 0xFFB4C0B6);
        instruction.setMinHeight(dp(38));
        instruction.setGravity(Gravity.CENTER);
        root.addView(instruction);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        start = button("Start", true);
        start.setId(R.id.start_pause);
        start.setOnClickListener(view -> onStartPressed());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        startParams.setMarginEnd(dp(12));
        controls.addView(start, startParams);
        Button reset = button("Reset", false);
        reset.setId(R.id.reset_timer);
        reset.setOnClickListener(view -> {
            TimerAlarms.cancel(this);
            state.reset();
            TimerStore.save(this, state);
            render();
        });
        controls.addView(reset, new LinearLayout.LayoutParams(dp(116), dp(58)));
        FrameLayout controlFrame = new FrameLayout(this);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(Math.min(dp(400), screenWidth - dp(48)), -2, Gravity.CENTER);
        controlFrame.addView(controls, controlsParams);
        root.addView(controlFrame, new LinearLayout.LayoutParams(-1, dp(62)));

        alerts = text("", 12, 0xFFE9B49A);
        alerts.setPadding(0, dp(12), 0, 0);
        alerts.setGravity(Gravity.CENTER);
        alerts.setOnClickListener(view -> openNotificationSettings());
        root.addView(alerts);
        setContentView(root);
    }

    private void onStartPressed() {
        if (state.running) {
            finishIfDue();
            if (state.running) {
                TimerAlarms.cancel(this);
                state.pause(SystemClock.elapsedRealtime());
                TimerStore.save(this, state);
            }
            render();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("askedAlerts", false)) {
            startAfterPermission = true;
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedAlerts", true).apply();
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 10);
            return;
        }
        startCountdown();
    }

    private void startCountdown() {
        if (!TimerAlarms.canSchedule(this)) {
            Toast.makeText(this, "Allow precise alarms so your timer can ring on time.", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        TimerAlarms.cancel(this);
        TimerState previous = TimerStore.load(this);
        state.start(SystemClock.elapsedRealtime(), UUID.randomUUID().toString());
        try {
            TimerStore.save(this, state);
            TimerAlarms.schedule(this, state);
        } catch (SecurityException error) {
            state = previous;
            TimerStore.save(this, state);
            Toast.makeText(this, "Please allow precise alarms, then try Start again.", Toast.LENGTH_LONG).show();
        }
        render();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 10 && startAfterPermission) {
            startAfterPermission = false;
            startCountdown();
        }
    }

    private void finishIfDue() {
        // The receiver and activity run on the main thread. Reload to deliver one completion only.
        TimerState latest = TimerStore.load(this);
        if (latest.complete(SystemClock.elapsedRealtime(), latest.token)) {
            TimerStore.save(this, latest);
            TimerAlarms.signalFinished(this);
            dial.announceForAccessibility("Time's up");
        }
        state = latest;
    }

    private void render() {
        long remaining = state.remaining(SystemClock.elapsedRealtime());
        dial.update(remaining, state.duration, state.running, state.finished);
        String label = state.running ? "Pause" : state.finished ? "Start again" : remaining < state.duration ? "Resume" : "Start";
        if (!label.contentEquals(start.getText())) start.setText(label);
        instruction.setText(state.finished ? "Time's up. Take a breath." : state.running ? "Your time is yours." :
                remaining < state.duration ? "Paused. Continue whenever you're ready." : "Turn the dial to set your minutes");
        boolean enabled = TimerAlarms.alertsEnabled(this);
        alerts.setVisibility(enabled ? View.GONE : View.VISIBLE);
        alerts.setText(R.string.allow_alerts);
        if (state.running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        state = TimerStore.load(this);
        if (state.running && state.remaining(SystemClock.elapsedRealtime()) > 0 && TimerAlarms.canSchedule(this)) {
            try {
                TimerAlarms.schedule(this, state);
            } catch (SecurityException ignored) {
                Toast.makeText(this, "Precise alarms are disabled. Keep the app open for this timer.", Toast.LENGTH_LONG).show();
            }
        }
        ticker.run();
    }

    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? 0xFF202E27 : 0xFFE8EBDD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? 0xFFF27764 : 0xFF25352E);
        background.setCornerRadius(dp(18));
        if (!primary) background.setStroke(dp(1), 0xFF4D5C4F);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x30333333), background, null));
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
