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
    interface Actions { void edit(int id); void add(); void expand(); void settings(); void close(); }
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
        addHeader(now);
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
                addRow(event.meeting().name(),status, past && !starting ? MUTED : AMBER,
                        event.meeting().id(),gap(previous,event.start()),MeetingSchedule.brazilTime(event.start()));
                if (starting || (!past && Duration.between(now,event.start()).getSeconds() <= event.meeting().lead()*60L))
                    getChildAt(getChildCount()-1).setBackground(surface(0xFFFFE4AE,12));
                previous = event.start();
            }
            if (!nowShown && !today.isEmpty()) addRow("Now  ·  " + MeetingSchedule.brazilTime(now),"",INK,-1,gap(previous,now));
            LocalDate localDate = now.atZone(MeetingSchedule.SP).toLocalDate();
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
            List<MeetingPlanner.Occurrence> upcoming = MeetingPlanner.upcoming(schedules,
                    localDate.plusDays(1).atStartOfDay(MeetingSchedule.SP).toInstant().minusNanos(1)).stream()
                    .filter(o -> o.start().atZone(MeetingSchedule.SP).toLocalDate().isAfter(localDate))
                    .collect(java.util.stream.Collectors.toList());
            if (!upcoming.isEmpty()) {
                TextView heading = label("Upcoming", 14, MUTED, true);
                heading.setPadding(0, dp(14), 0, 0);
                addView(heading);
                for (MeetingPlanner.Occurrence event : upcoming) {
                    addRow(event.meeting().name(), dateFormat.format(event.start().atZone(MeetingSchedule.SP)),
                            AMBER, event.meeting().id(), dp(6), MeetingSchedule.brazilTime(event.start()));
                }
            }
            for (MeetingSchedule meeting : schedules) {
                boolean visible = today.stream().anyMatch(o -> o.meeting().id() == meeting.id())
                        || upcoming.stream().anyMatch(o -> o.meeting().id() == meeting.id());
                if (!visible) {
                    String status = !meeting.configured() ? "" : !meeting.enabled() ? "Reminders off"
                            : "Past meeting · " + dateFormat.format(meeting.start(meeting.date()).atZone(MeetingSchedule.SP));
                    addScheduleTile(meeting,meeting.name(),status);
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
    private void addHeader(Instant now) {
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(VERTICAL);
        titles.addView(label("My Day",22,INK,true));
        titles.addView(label("São Paulo  ·  " + MeetingSchedule.brazilTime(now),13,0xFF56675E,false));
        header.addView(titles,new LayoutParams(0,-2,1));
        TextView add = label("+ Add meeting", 14, 0xFF29503E, true);
        add.setPadding(dp(8), 0, dp(8), 0);
        add.setGravity(Gravity.CENTER);
        add.setMinHeight(dp(48));
        add.setContentDescription("Add meeting");
        add.setFocusable(true);
        add.setOnClickListener(view -> actions.add());
        header.addView(add,new LayoutParams(-2,dp(48)));
        TextView close = label("×",24,INK,false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Hide My Day");
        close.setFocusable(true);
        close.setBackground(surface(0xFFE9E7DC,24));
        close.setOnClickListener(view -> actions.close());
        header.addView(close,new LayoutParams(dp(48),dp(48)));
        addView(header);
    }

    private android.graphics.drawable.Drawable surface(int color,int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1833483E),shape,null);
    }

    private void addScheduleTile(MeetingSchedule meeting,String name,String status) {
        LinearLayout tile = new LinearLayout(getContext());
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setMinimumHeight(dp(56));
        tile.setPadding(dp(14),dp(8),dp(10),dp(8));
        tile.setBackground(surface(0xFFEDEBE1,12));
        LinearLayout details = new LinearLayout(getContext());
        details.setOrientation(VERTICAL);
        details.addView(label(name,16,INK,true));
        if (!status.isEmpty()) details.addView(label(status,13,MUTED,false));
        tile.addView(details,new LayoutParams(0,-2,1));
        String action = meeting.configured() ? "Edit" : "Set time";
        TextView actionLabel = label(action,14,0xFF29503E,true);
        actionLabel.setPadding(dp(8),dp(4),dp(8),dp(4));
        tile.addView(actionLabel);
        tile.setFocusable(true);
        tile.setContentDescription(name + (status.isEmpty() ? "" : ". " + status) + ". " + action);
        tile.setOnClickListener(view -> actions.edit(meeting.id()));
        LayoutParams layout = new LayoutParams(-1,-2);
        layout.topMargin = dp(8);
        addView(tile,layout);
    }
    private int gap(Instant previous, Instant next) {
        return previous == null ? dp(4) : dp((int)Math.min(24,Math.max(2,Duration.between(previous,next).toMinutes()/4)));
    }
    private void addRow(String name, String status, int color, int id, int gap) {
        addRow(name,status,color,id,gap,null);
    }
    private void addRow(String name, String status, int color, int id, int gap,String time) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        TextView dot = label("●",18,color,true);
        dot.setGravity(Gravity.CENTER);
        dot.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(dot,new LayoutParams(dp(22),-1));
        markers.add(dot);
        if (time != null) {
            TextView clock = label(time,17,INK,true);
            clock.setFontFeatureSettings("tnum");
            clock.setGravity(Gravity.CENTER);
            clock.setPadding(dp(8),dp(6),dp(8),dp(6));
            clock.setBackground(surface(color == AMBER ? 0xFFF4E0BA : 0xFFE5E5DC,8));
            row.addView(clock);
        }
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
            String spokenStatus = status.replaceAll("(\\d+)h ", "$1 hours ").replaceAll("(\\d+) ?min$", "$1 minutes")
                    .replaceAll("\\b1 hours\\b","1 hour").replaceAll("\\b1 minutes\\b","1 minute");
            row.setContentDescription(name + " at " + time + ", " + spokenStatus + ". Edit meeting");
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
