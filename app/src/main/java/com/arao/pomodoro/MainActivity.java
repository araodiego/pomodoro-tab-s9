package com.arao.pomodoro;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

public final class MainActivity extends Activity {
    private static boolean visible;
    static boolean isVisible() { return visible; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimerState state;
    private LinearLayout root;
    private TimerDialView dial;
    private Button start;
    private Button secondary;
    private TextView instruction;
    private TextView alerts;
    private boolean startAfterPermission;
    private boolean pendingFocusAgain;
    private SharedPreferences preferences;
    private Boolean displayedBreak;
    private int displayedBackground = 0xFF192522;
    private ValueAnimator backgroundFade;
    private final SharedPreferences.OnSharedPreferenceChangeListener stateChanged = (prefs, key) -> {
        state = TimerStore.load(this);
        render();
    };
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
        preferences = getSharedPreferences("timer", MODE_PRIVATE);
        if (savedInstanceState != null) {
            startAfterPermission = savedInstanceState.getBoolean("startAfterPermission");
            pendingFocusAgain = savedInstanceState.getBoolean("pendingFocusAgain");
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        buildScreen();
    }

    private void buildScreen() {
        root = new LinearLayout(this);
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
        secondary = button(getString(R.string.reset_timer), false);
        secondary.setId(R.id.reset_timer);
        secondary.setOnClickListener(view -> onSecondaryPressed());
        controls.addView(secondary, new LinearLayout.LayoutParams(dp(116), -2));
        FrameLayout controlFrame = new FrameLayout(this);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(Math.min(dp(440), screenWidth - dp(48)), -2, Gravity.CENTER);
        controlFrame.addView(controls, controlsParams);
        controlFrame.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = Math.min(dp(440), right - left);
            boolean stacked = width < dp(360) || getResources().getConfiguration().fontScale > 1.25f;
            if (controls.getLayoutParams().width == width
                    && controls.getOrientation() == (stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL)) return;
            controls.getLayoutParams().width = width;
            controls.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams primary = new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1);
            primary.setMarginEnd(stacked ? 0 : dp(12));
            primary.bottomMargin = stacked ? dp(8) : 0;
            start.setLayoutParams(primary);
            secondary.setLayoutParams(new LinearLayout.LayoutParams(stacked ? -1 : dp(116), -2));
            controls.requestLayout();
        });
        root.addView(controlFrame, new LinearLayout.LayoutParams(-1, -2));

        alerts = text("", 12, 0xFFE9B49A);
        alerts.setPadding(0, dp(12), 0, 0);
        alerts.setGravity(Gravity.CENTER);
        alerts.setOnClickListener(view -> openNotificationSettings());
        root.addView(alerts);
        setContentView(root);
    }

    private void onStartPressed() {
        state = TimerStore.load(this);
        if (state.running) {
            finishIfDue();
            if (state.running) {
                long now = SystemClock.elapsedRealtime();
                if (state.remaining(now) == 0) finishIfDue();
                else {
                    TimerAlarms.cancel(this);
                    state.pause(now);
                    TimerStore.save(this, state);
                }
            }
            render();
            return;
        }
        requestStart(false);
    }

    private void onSecondaryPressed() {
        state = TimerStore.load(this);
        if (state.finished && state.phase == TimerState.Phase.FOCUS) {
            requestStart(true);
            return;
        }
        TimerAlarms.cancel(this);
        state.reset();
        TimerStore.save(this, state);
        render();
    }

    private void requestStart(boolean focusAgain) {
        pendingFocusAgain = focusAgain;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("askedAlerts", false)) {
            startAfterPermission = true;
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedAlerts", true).apply();
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 10);
            return;
        }
        startCountdown(focusAgain);
    }

    private void startCountdown(boolean focusAgain) {
        if (!TimerAlarms.canSchedule(this)) {
            Toast.makeText(this, "Allow precise alarms so your timer can ring on time.", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        try {
            TimerController.start(this, focusAgain, null);
        } catch (SecurityException error) {
            Toast.makeText(this, "Please allow precise alarms, then try Start again.", Toast.LENGTH_LONG).show();
        }
        state = TimerStore.load(this);
        render();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 10 && startAfterPermission) {
            startAfterPermission = false;
            startCountdown(pendingFocusAgain);
        }
    }

    private void finishIfDue() {
        boolean wasRunning = state.running;
        state = TimerController.finishIfDue(this);
        if (wasRunning && state.finished) {
            dial.announceForAccessibility(getString(state.phase == TimerState.Phase.BREAK
                    ? R.string.break_complete : R.string.focus_complete));
        }
    }

    private void render() {
        long remaining = state.remaining(SystemClock.elapsedRealtime());
        boolean breakPhase = state.phase == TimerState.Phase.BREAK;
        dial.update(remaining, state);
        String label = state.running ? getString(R.string.pause_timer) : state.finished
                ? (breakPhase ? getString(R.string.start_focus, state.focusDuration / TimerState.MINUTE) : getString(R.string.start_break))
                : remaining < state.duration ? getString(R.string.resume_timer) : getString(R.string.start_timer);
        if (!label.contentEquals(start.getText())) start.setText(label);
        secondary.setText(breakPhase ? R.string.end_break : state.finished ? R.string.focus_again : R.string.reset_timer);
        int hint = state.finished ? (breakPhase ? R.string.break_complete_hint : R.string.focus_complete_hint)
                : state.running ? (breakPhase ? R.string.break_running_hint : R.string.focus_running_hint)
                : remaining < state.duration ? R.string.paused_hint : R.string.focus_ready_hint;
        if (!getString(hint).contentEquals(instruction.getText())) instruction.setText(hint);
        updateBackground(breakPhase);
        boolean enabled = TimerAlarms.alertsEnabled(this);
        alerts.setVisibility(enabled ? View.GONE : View.VISIBLE);
        alerts.setText(R.string.allow_alerts);
        if (state.running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void updateBackground(boolean breakPhase) {
        if (displayedBreak != null && displayedBreak == breakPhase) return;
        if (backgroundFade != null) backgroundFade.cancel();
        int target = breakPhase ? 0xFF183744 : 0xFF192522;
        if (displayedBreak == null || !ValueAnimator.areAnimatorsEnabled()) {
            displayedBackground = target;
            root.setBackgroundColor(target);
        } else {
            backgroundFade = ValueAnimator.ofArgb(displayedBackground, target);
            backgroundFade.setDuration(220);
            backgroundFade.addUpdateListener(animation -> {
                displayedBackground = (int) animation.getAnimatedValue();
                root.setBackgroundColor(displayedBackground);
            });
            backgroundFade.start();
        }
        displayedBreak = breakPhase;
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        preferences.registerOnSharedPreferenceChangeListener(stateChanged);
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
        preferences.unregisterOnSharedPreferenceChangeListener(stateChanged);
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("startAfterPermission", startAfterPermission);
        outState.putBoolean("pendingFocusAgain", pendingFocusAgain);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        if (backgroundFade != null) backgroundFade.cancel();
        super.onDestroy();
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
        button.setMinHeight(dp(58));
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
