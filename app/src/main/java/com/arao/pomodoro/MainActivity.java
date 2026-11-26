package com.arao.pomodoro;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import java.time.Instant;
import java.util.List;

public final class MainActivity extends Activity {
    private static boolean visible;
    static boolean isVisible() { return visible; }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TimerState state;
    private LinearLayout root;
    private TimerDialView dial;
    private Button start;
    private Button music;
    private Button secondary;
    private TextView instruction;
    private TextView alerts;
    private TextView jiraLabel;
    private TextView jiraKey;
    private EditText jiraEditor;
    private MeetingRibbonView ribbon;
    private ScrollView ribbonScroll;
    private TextView overflow;
    private LinearLayout undoBar;
    private final Runnable dismissUndo = () -> undoBar.setVisibility(View.GONE);
    private MeetingRibbonView expandedRibbon;
    private AlertDialog ribbonDialog;
    private MeetingEditor meetingEditor;
    private AlertDialog meetingPrompt;
    private int promptId = -1;
    private long promptOccurrence;
    private long lastRibbonMinute = Long.MIN_VALUE;
    private List<MeetingSchedule> meetings;
    private SharedPreferences workPreferences;
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
            updateMeetings();
            handler.postDelayed(this, 200);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimerAlarms.createChannel(this);
        MeetingAlarms.channels(this);
        state = TimerStore.load(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        preferences = getSharedPreferences("timer", MODE_PRIVATE);
        workPreferences = getSharedPreferences("work", MODE_PRIVATE);
        if (savedInstanceState != null) {
            startAfterPermission = savedInstanceState.getBoolean("startAfterPermission");
            pendingFocusAgain = savedInstanceState.getBoolean("pendingFocusAgain");
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        buildScreen();
        if (savedInstanceState != null && savedInstanceState.containsKey("meetingDraft")) {
            Bundle draft = savedInstanceState.getBundle("meetingDraft");
            root.post(() -> editMeeting(draft.getInt("id"), draft));
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("jiraDraft")) {
            String draft = savedInstanceState.getString("jiraDraft");
            root.post(() -> editJira(draft));
        }
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
        FrameLayout header = new FrameLayout(this);
        header.addView(title,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        overflow = text("⋮",26,0xFFF6F2E7);
        overflow.setGravity(Gravity.CENTER);
        overflow.setContentDescription("More options");
        overflow.setFocusable(true);
        overflow.setOnClickListener(view -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(this,view,Gravity.END);
            boolean show = ribbonScroll.getVisibility() != View.VISIBLE;
            menu.getMenu().add(show ? "Show My Day" : "Hide My Day");
            if (Build.VERSION.SDK_INT >= 33) menu.getMenu().add(0,1,1,"Add Pomodoro tile");
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1 && Build.VERSION.SDK_INT >= 33) {
                    getSystemService(android.app.StatusBarManager.class).requestAddTileService(
                            new android.content.ComponentName(this,PomodoroTile.class),"Pomodoro Work",
                            android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_timer),
                            getMainExecutor(),result -> {
                                if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                                    Toast.makeText(this,"Pomodoro tile already added",Toast.LENGTH_SHORT).show();
                            });
                    return true;
                }
                setRibbonVisible(show);
                return true;
            });
            menu.show();
        });
        header.addView(overflow,new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        TextView subtitle = text("A little space to focus.", 13, 0xFF9FAFA4);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-2, -2);
        subtitleParams.topMargin = dp(7);
        root.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(0xFFF6F2E7);
        cardBackground.setCornerRadius(dp(18));
        card.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x2033483E), cardBackground, null));
        card.setClickable(true);
        card.setFocusable(true);
        card.setMinimumHeight(dp(80));
        card.setOnClickListener(view -> editJira(null));
        jiraLabel = text(getString(R.string.current_jira), 12, 0xFF33483E);
        jiraLabel.setLetterSpacing(.08f);
        card.addView(jiraLabel);
        jiraKey = text("", 28, 0xFF192522);
        jiraKey.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        jiraKey.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        jiraKey.setAutoSizeTextTypeUniformWithConfiguration(16, 30, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        jiraKey.setMaxLines(2);
        card.addView(jiraKey);
        TextView edit = text(getString(R.string.tap_edit_jira), 12, 0xFF33483E);
        card.addView(edit);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(16);
        root.addView(card, cardParams);

        int widthDp = getResources().getConfiguration().screenWidthDp;
        int heightDp = getResources().getConfiguration().screenHeightDp;
        if (heightDp < 540) {
            subtitle.setVisibility(View.GONE);
            cardParams.topMargin = dp(4);
            card.setMinimumHeight(dp(60));
            card.setPadding(dp(12),dp(6),dp(12),dp(6));
        }
        boolean wide = widthDp >= 640 && widthDp > heightDp && heightDp >= 440;
        boolean compact = !wide && (widthDp < 600 || heightDp < 720);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.topMargin = dp(12);
        root.addView(body, bodyParams);
        LinearLayout timerPanel = new LinearLayout(this);
        timerPanel.setOrientation(LinearLayout.VERTICAL);
        timerPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(timerPanel, wide ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, 0, 1));
        dial = new TimerDialView(this);
        dial.setId(R.id.timer_dial);
        dial.setListener(minutes -> {
            TimerAlarms.cancel(this);
            state.selectMinutes(minutes);
            TimerStore.save(this, state);
            render();
        });
        timerPanel.addView(dial, new LinearLayout.LayoutParams(-1, 0, 1));

        instruction = text("Turn the dial to set your minutes", 14, 0xFFB4C0B6);
        instruction.setMinHeight(dp(38));
        instruction.setGravity(Gravity.CENTER);
        timerPanel.addView(instruction);
        music = new Button(this, null, android.R.attr.borderlessButtonStyle);
        music.setAllCaps(false);
        music.setTextColor(0xFFCFDED3);
        music.setTextSize(14);
        music.setMinHeight(dp(48));
        updateMusicLabel();
        music.setOnClickListener(v -> chooseFocusMusic());
        timerPanel.addView(music);

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
        timerPanel.addView(controlFrame, new LinearLayout.LayoutParams(-1, -2));

        ribbon = new MeetingRibbonView(this, compact, ribbonActions());
        ribbonScroll = new ScrollView(this);
        ribbonScroll.setFillViewport(false);
        ribbonScroll.addView(ribbon, new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams ribbonParams = wide ? new LinearLayout.LayoutParams(0,-1,1)
                : new LinearLayout.LayoutParams(-1,-2);
        if (wide) ribbonParams.setMarginStart(dp(16));
        else ribbonParams.topMargin = dp(10);
        body.addView(ribbonScroll,ribbonParams);
        ribbonScroll.setVisibility(workPreferences.getBoolean("myDayVisible",true) ? View.VISIBLE : View.GONE);
        undoBar = new LinearLayout(this);
        undoBar.setGravity(Gravity.CENTER);
        undoBar.addView(text("My Day hidden",14,0xFFF6F2E7));
        Button undo = new Button(this,null,android.R.attr.borderlessButtonStyle);
        undo.setText(R.string.undo);
        undo.setOnClickListener(view -> setRibbonVisible(true));
        undoBar.addView(undo);
        undoBar.setVisibility(View.GONE);
        root.addView(undoBar);

        alerts = text("", 12, 0xFFE9B49A);
        alerts.setPadding(0, dp(12), 0, 0);
        alerts.setGravity(Gravity.CENTER);
        alerts.setOnClickListener(view -> openNotificationSettings());
        root.addView(alerts);
        if (heightDp < 540) {
            ScrollView screen = new ScrollView(this);
            screen.setFillViewport(true);
            root.setMinimumHeight(dp(440));
            screen.addView(root,new ScrollView.LayoutParams(-1,-2));
            setContentView(screen);
        } else setContentView(root);
    }

    private MeetingRibbonView.Actions ribbonActions() {
        return new MeetingRibbonView.Actions() {
            @Override public void edit(int id) { editMeeting(id,null); }
            @Override public void expand() { showRibbon(); }
            @Override public void settings() { requestMeetingAlerts(); }
            @Override public void close() { setRibbonVisible(false); }
        };
    }

    private void setRibbonVisible(boolean show) {
        if (ribbonDialog != null) ribbonDialog.dismiss();
        workPreferences.edit().putBoolean("myDayVisible",show).apply();
        ribbonScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(dismissUndo);
        undoBar.setVisibility(View.GONE);
        if (show) {
            ribbon.render(Instant.now(),MeetingStore.all(this));
        } else {
            overflow.requestFocus();
            if (!workPreferences.getBoolean("myDayUndoShown",false)) {
                undoBar.setVisibility(View.VISIBLE);
                undoBar.announceForAccessibility("My Day hidden. Undo available.");
                workPreferences.edit().putBoolean("myDayUndoShown",true).apply();
                handler.postDelayed(dismissUndo,8000);
            }
        }
    }

    private void showRibbon() {
        expandedRibbon = new MeetingRibbonView(this,false,ribbonActions());
        expandedRibbon.render(Instant.now(),MeetingStore.all(this));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(expandedRibbon);
        ribbonDialog = new AlertDialog.Builder(this).setView(scroll).setPositiveButton("Done",null).create();
        ribbonDialog.setOnDismissListener(d -> { expandedRibbon = null; ribbonDialog = null; });
        ribbonDialog.show();
    }

    private void editMeeting(int id, Bundle draft) {
        if (meetingEditor != null) return;
        meetingEditor = new MeetingEditor(this,id,draft,() -> {
            lastRibbonMinute = Long.MIN_VALUE;
            updateMeetings();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
        }, () -> meetingEditor = null);
        meetingEditor.dialog.show();
    }

    private void requestMeetingAlerts() {
        if (!TimerAlarms.canSchedule(this) && Build.VERSION.SDK_INT >= 31) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
        } else openNotificationSettings();
    }

    private void updateMeetings() {
        Instant now = Instant.now();
        long minute = now.getEpochSecond()/60;
        if (minute != lastRibbonMinute) {
            lastRibbonMinute = minute;
            meetings = MeetingStore.all(this);
            if (!TimerAlarms.canSchedule(this)) MeetingAlarms.refresh(this);
            if (ribbonScroll.getVisibility() == View.VISIBLE) ribbon.render(now,meetings);
            if (expandedRibbon != null) expandedRibbon.render(now,meetings);
        }
        if (meetingPrompt != null) {
            if (MeetingStore.pending(this,MeetingStore.load(this,promptId)) != promptOccurrence)
                meetingPrompt.dismiss();
            return;
        }
        if (meetingEditor != null || jiraEditor != null || ribbonDialog != null || !visible) return;
        for (MeetingSchedule meeting : meetings) {
            long occurrence = MeetingStore.pending(this,meeting);
            if (occurrence != 0) {
                promptMeeting(meeting,occurrence);
                break;
            }
        }
    }

    private void promptMeeting(MeetingSchedule meeting, long occurrence) {
        TimerState current = TimerController.finishIfDue(this);
        promptId = meeting.id();
        promptOccurrence = occurrence;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(meeting.name() + " starts now")
                .setMessage(MeetingSchedule.brazilTime(Instant.ofEpochMilli(occurrence)) + " São Paulo"
                        + (current.running ? "\nYour timer can wait for the meeting." : ""))
                .setNegativeButton(current.running ? "Keep focusing" : "Got it",
                        (d,w) -> MeetingAlarms.acknowledge(this,meeting,occurrence));
        if (current.running) builder.setPositiveButton("Pause for meeting",(d,w) -> {
            TimerController.pause(this,current.token);
            MeetingAlarms.acknowledge(this,meeting,occurrence);
        });
        meetingPrompt = builder.create();
        meetingPrompt.setOnCancelListener(d -> MeetingAlarms.acknowledge(this,meeting,occurrence));
        meetingPrompt.setOnDismissListener(d -> meetingPrompt = null);
        meetingPrompt.show();
    }

    private void onStartPressed() {
        state = TimerStore.load(this);
        if (state.running) {
            TimerController.pause(this,state.token);
            state = TimerStore.load(this);
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
        FocusMusic.sync(this,state);
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
        jiraLabel.setText(breakPhase ? R.string.return_to_jira : R.string.current_jira);
        String key = workPreferences.getString("jiraKey", "");
        String displayedKey = key.isEmpty() ? getString(R.string.set_jira) : key;
        if (!displayedKey.contentEquals(jiraKey.getText())) jiraKey.setText(displayedKey);
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

    private void editJira(String draft) {
        String previous = workPreferences.getString("jiraKey", "");
        jiraEditor = new EditText(this);
        jiraEditor.setSingleLine(true);
        jiraEditor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        jiraEditor.setHint(R.string.jira_hint);
        jiraEditor.setText(draft == null ? previous : draft);
        jiraEditor.selectAll();
        jiraEditor.setContentDescription(getString(R.string.jira_field));
        LinearLayout container = new LinearLayout(this);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(jiraEditor, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.jira_field).setView(container)
                .setPositiveButton(R.string.save_jira, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.clear_jira, (d, which) -> {
                    workPreferences.edit().remove("jiraKey").apply();
                    CountdownNotification.refresh(this,TimerStore.load(this));
                    render();
                }).create();
        dialog.setOnDismissListener(d -> jiraEditor = null);
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String normalized = JiraKey.normalize(jiraEditor.getText().toString(), previous);
                if (normalized == null) {
                    jiraEditor.setError(getString(R.string.invalid_jira));
                    return;
                }
                workPreferences.edit().putString("jiraKey", normalized).apply();
                CountdownNotification.refresh(this,TimerStore.load(this));
                render();
                dialog.dismiss();
            });
            jiraEditor.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
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

    private void updateMusicLabel() {
        music.setText(getString(R.string.focus_music_selection, getString(FocusMusic.selection(this).label)));
    }

    private void chooseFocusMusic() {
        FocusMusic.Mix[] mixes = FocusMusic.Mix.values();
        String[] labels = new String[mixes.length];
        for (int i = 0; i < mixes.length; i++) labels[i] = getString(mixes[i].label);
        new AlertDialog.Builder(this)
                .setTitle(R.string.focus_music_title)
                .setSingleChoiceItems(labels, FocusMusic.selection(this).ordinal(), (dialog, index) -> {
                    FocusMusic.select(this, mixes[index]);
                    updateMusicLabel();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openNotificationSettings() {
        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    @Override protected void onResume() {
        super.onResume();
        updateMusicLabel();
        visible = true;
        FocusMusic.sync(this,TimerStore.load(this));
        MeetingAlarms.refresh(this);
        lastRibbonMinute = Long.MIN_VALUE;
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
        if (!state.running) CountdownNotification.refresh(this,state);
    }

    @Override protected void onPause() {
        visible = false;
        if (meetingPrompt != null) meetingPrompt.dismiss();
        preferences.unregisterOnSharedPreferenceChangeListener(stateChanged);
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (jiraEditor != null) outState.putString("jiraDraft", jiraEditor.getText().toString());
        if (meetingEditor != null) outState.putBundle("meetingDraft",meetingEditor.draft());
        outState.putBoolean("startAfterPermission", startAfterPermission);
        outState.putBoolean("pendingFocusAgain", pendingFocusAgain);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(dismissUndo);
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
