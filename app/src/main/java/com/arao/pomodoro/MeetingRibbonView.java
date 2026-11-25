package com.arao.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@android.annotation.SuppressLint("ViewConstructor") // Created only in code with required actions.
final class MeetingRibbonView extends LinearLayout {
    interface Actions { void edit(int id); void expand(); void settings(); }
    private final Actions actions;
    private final boolean compact;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<View> markers = new ArrayList<>();
    private String lastContent = "";
    private static final int INK = 0xFF192522, AMBER = 0xFFD38C1E, MUTED = 0xFF68736B;

    MeetingRibbonView(Context context, boolean compact, Actions actions) {
        super(context);
        this.actions = actions;
        this.compact = compact;
        setOrientation(VERTICAL);
        setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF6F2E7);
        bg.setCornerRadius(dp(18));
        setBackground(bg);
        setWillNotDraw(false);
    }
    void render(Instant now, List<MeetingSchedule> schedules) {
        String key = now.getEpochSecond()/60 + "/" + schedules + "/" + MeetingAlarms.alertsEnabled(getContext())
                + "/" + TimerAlarms.canSchedule(getContext());
        if (key.equals(lastContent)) return;
        lastContent = key;
        removeAllViews();
        markers.clear();
        TextView title = label("My day  ·  São Paulo", 18, INK, true);
        addView(title);
        MeetingPlanner.Occurrence next = MeetingPlanner.next(schedules, now);
        if (compact) {
            boolean unconfigured = schedules.stream().anyMatch(m -> !m.configured());
            String timeHint = next == null ? "" : next.start().atZone(MeetingSchedule.SP).toLocalDate()
                    .equals(now.atZone(MeetingSchedule.SP).toLocalDate()) ? MeetingPlanner.until(now,next.start())
                    : DateTimeFormatter.ofPattern("EEE, MMM d",Locale.US).format(next.start().atZone(MeetingSchedule.SP));
            TextView summary = label(next == null ? (unconfigured ? "Set meeting times  ›" : "Show My Day  ›") :
                    MeetingSchedule.brazilTime(next.start()) + "  " + next.meeting().name() + "  ›\n"
                            + timeHint, 16, INK,true);
            summary.setMinHeight(dp(48));
            summary.setGravity(Gravity.CENTER_VERTICAL);
            summary.setFocusable(true);
            summary.setOnClickListener(v -> actions.expand());
            if (next != null && Duration.between(now,next.start()).toMinutes() < next.meeting().lead()) {
                summary.setBackgroundColor(0xFFFFD997);
                summary.setPadding(dp(8),dp(6),dp(8),dp(6));
            }
            addView(summary);
        } else {
            List<MeetingPlanner.Occurrence> today = MeetingPlanner.today(schedules,now);
            Instant previous = null;
            boolean nowShown = false;
            for (MeetingPlanner.Occurrence event : today) {
                if (!nowShown && !event.start().isBefore(now)) {
                    addRow("Now  ·  " + MeetingSchedule.brazilTime(now), "", INK,-1, gap(previous,now));
                    previous = now;
                    nowShown = true;
                }
                boolean past = event.start().isBefore(now);
                boolean starting = past && Duration.between(event.start(),now).getSeconds() < 60;
                String status = starting ? "Starts now" : past ? "Started at " + MeetingSchedule.brazilTime(event.start())
                        : MeetingPlanner.until(now,event.start());
                addRow(MeetingSchedule.brazilTime(event.start()) + "  " + event.meeting().name(),
                        status, past && !starting ? MUTED : AMBER,event.meeting().id(),gap(previous,event.start()));
                if (starting || (!past && Duration.between(now,event.start()).getSeconds() <= event.meeting().lead()*60L))
                    getChildAt(getChildCount()-1).setBackgroundColor(0xFFFFD997);
                previous = event.start();
            }
            if (!nowShown) addRow("Now  ·  " + MeetingSchedule.brazilTime(now),"",INK,-1,gap(previous,now));
            if (today.isEmpty() || (next != null && !next.start().atZone(MeetingSchedule.SP).toLocalDate()
                    .equals(now.atZone(MeetingSchedule.SP).toLocalDate()))) {
                if (next != null) {
                    String date = DateTimeFormatter.ofPattern("EEE, MMM d",Locale.US).format(next.start().atZone(MeetingSchedule.SP));
                    addRow("Next: " + next.meeting().name(),date + " · " + MeetingSchedule.brazilTime(next.start()),
                            AMBER,next.meeting().id(),dp(8));
                } else addView(label("No upcoming meetings",14,MUTED,false));
            }
            for (MeetingSchedule meeting : schedules) {
                boolean visible = today.stream().anyMatch(o -> o.meeting().id() == meeting.id())
                        || (next != null && next.meeting().id() == meeting.id());
                if (!visible) {
                    Instant future = meeting.next(now,0);
                    String status = !meeting.configured() ? "Set meeting time" : !meeting.enabled() ? "Disabled"
                            : DateTimeFormatter.ofPattern("EEE, MMM d",Locale.US).format(future.atZone(MeetingSchedule.SP));
                    String name = meeting.name();
                    if (meeting.configured()) {
                        Instant display = future != null ? future : meeting.start(now.atZone(MeetingSchedule.NY).toLocalDate());
                        name = MeetingSchedule.brazilTime(display) + "  " + name;
                    }
                    addRow(name,status,MUTED,meeting.id(),dp(4));
                }
            }
        }
        if (schedules.stream().anyMatch(m -> m.configured() && m.enabled())
                && (!MeetingAlarms.alertsEnabled(getContext()) || !TimerAlarms.canSchedule(getContext()))) {
            TextView warning = label("Enable meeting alerts ›",14,0xFF8B4C11,true);
            warning.setMinHeight(dp(48));
            warning.setGravity(Gravity.CENTER_VERTICAL);
            warning.setFocusable(true);
            warning.setOnClickListener(v -> actions.settings());
            addView(warning);
        }
        invalidate();
    }
    private int gap(Instant previous, Instant next) {
        return previous == null ? dp(4) : dp((int)Math.min(24,Math.max(2,Duration.between(previous,next).toMinutes()/4)));
    }
    private void addRow(String name, String status, int color, int id, int gap) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        TextView dot = label("●",18,color,true);
        dot.setGravity(Gravity.CENTER);
        dot.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(dot,new LayoutParams(dp(22),-1));
        markers.add(dot);
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        content.setPadding(dp(8),dp(5),dp(4),dp(5));
        content.addView(label(name,16,INK,true));
        if (!status.isEmpty()) content.addView(label(status,13,color == AMBER ? 0xFF865000 : color,false));
        row.addView(content,new LayoutParams(0,-2,1));
        if (id >= 0) {
            TextView edit = label("Edit",13,INK,true);
            row.addView(edit);
            row.setFocusable(true);
            row.setContentDescription(name + ", " + status + ", edit meeting");
            row.setOnClickListener(v -> actions.edit(id));
        }
        LayoutParams params = new LayoutParams(-1,-2);
        params.topMargin = gap;
        addView(row,params);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (markers.size() < 2) return;
        View first = markers.get(0), last = markers.get(markers.size()-1);
        paint.setColor(0xFFB6B9A9);
        paint.setStrokeWidth(dp(2));
        float x = getPaddingLeft() + first.getWidth()/2f;
        canvas.drawLine(x,((View) first.getParent()).getTop()+first.getHeight()/2f,
                x,((View) last.getParent()).getTop()+last.getHeight()/2f,paint);
    }
    private TextView label(String value,int size,int color,boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setText(value); tv.setTextSize(size); tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return tv;
    }
    private int dp(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
}
