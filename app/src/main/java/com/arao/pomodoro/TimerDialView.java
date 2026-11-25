package com.arao.pomodoro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** A real 60-minute scale: the colored area shrinks as time passes. */
public final class TimerDialView extends View {
    public interface Listener { void onMinutesChanged(int minutes); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface mono = Typeface.create("sans-serif", Typeface.NORMAL);
    private Listener listener;
    private long remaining = 25 * TimerState.MINUTE;
    private String status = "READY";
    private boolean adjustable = true;
    private boolean dragging;
    private int selectedMinutes = 25;
    private float cx, cy, radius;

    public TimerDialView(Context context) {
        super(context);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener value) { listener = value; }

    public void update(long millis, long duration, boolean running, boolean finished) {
        remaining = millis;
        selectedMinutes = (int) (duration / TimerState.MINUTE);
        adjustable = !running;
        status = finished ? "TIME'S UP" : running ? "FOCUS" : millis < duration ? "PAUSED" : "READY";
        String description = "Timer, " + TimerState.format(millis) + ", " + status.toLowerCase(java.util.Locale.US)
                + (adjustable ? ". Swipe up or down to adjust minutes." : "");
        if (!description.contentEquals(getContentDescription() == null ? "" : getContentDescription())) {
            setContentDescription(description);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        cx = getWidth() / 2f;
        cy = getHeight() / 2f;
        radius = Math.min(Math.min(getWidth(), getHeight()) * .46f, dp(278));
        if (radius <= 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(36, 48, 43));
        paint.setShadowLayer(radius * .07f, 0, radius * .045f, 0x65000000);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.clearShadowLayer();
        paint.setColor(Color.rgb(76, 85, 75));
        canvas.drawCircle(cx, cy, radius * .975f, paint);
        paint.setColor(Color.rgb(234, 230, 216));
        canvas.drawCircle(cx, cy, radius * .947f, paint);

        float sweep = (float) (360d * remaining / (60d * TimerState.MINUTE));
        paint.setColor(Color.rgb(221, 216, 201));
        canvas.drawCircle(cx, cy, radius * .725f, paint);
        paint.setColor(Color.rgb(242, 119, 100));
        arc.set(cx - radius * .725f, cy - radius * .725f, cx + radius * .725f, cy + radius * .725f);
        canvas.drawArc(arc, -90, sweep, true, paint);

        for (int minute = 0; minute < 60; minute++) {
            double angle = Math.toRadians(minute * 6 - 90);
            float outer = radius * .91f;
            float inner = radius * (minute % 5 == 0 ? .845f : .88f);
            paint.setColor(minute % 5 == 0 ? 0xFF3F4A43 : 0xFF9D9F91);
            paint.setStrokeWidth(radius * (minute % 5 == 0 ? .011f : .005f));
            canvas.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, paint);
            if (minute % 5 == 0) {
                paint.setTypeface(medium);
                paint.setTextSize(radius * .082f);
                paint.setTextAlign(Paint.Align.CENTER);
                float baseline = cy + (float) Math.sin(angle) * radius * .79f
                        - (paint.ascent() + paint.descent()) / 2;
                canvas.drawText(minute == 0 ? "60" : String.valueOf(minute),
                        cx + (float) Math.cos(angle) * radius * .79f, baseline, paint);
            }
        }

        // A raised center keeps the numbers readable while the outer sector shows time visually.
        paint.setColor(0xFFF6F2E7);
        paint.setShadowLayer(radius * .045f, 0, radius * .012f, 0x26000000);
        canvas.drawCircle(cx, cy, radius * .48f, paint);
        paint.clearShadowLayer();
        paint.setColor(0xFF33483E);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(medium);
        paint.setTextSize(radius * .065f);
        canvas.drawText(status, cx, cy - radius * .19f, paint);
        paint.setTypeface(mono);
        paint.setTextSize(radius * .263f);
        canvas.drawText(TimerState.format(remaining), cx, cy + radius * .06f, paint);
        paint.setTypeface(regular);
        paint.setTextSize(radius * .054f);
        paint.setColor(0xFF777D6D);
        canvas.drawText("MINUTES", cx, cy + radius * .21f, paint);

        if (isFocused()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xFFF27764);
            canvas.drawCircle(cx, cy, radius + dp(5), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!adjustable) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float distance = (float) Math.hypot(event.getX() - cx, event.getY() - cy);
            if (distance < radius * .49f || distance > radius * 1.08f) return false;
            dragging = true;
            getParent().requestDisallowInterceptTouchEvent(true);
            adjustAt(event.getX(), event.getY());
            return true;
        }
        if (dragging && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            adjustAt(event.getX(), event.getY());
            return true;
        }
        if (dragging && (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            dragging = false;
            getParent().requestDisallowInterceptTouchEvent(false);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return false;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void adjustAt(float x, float y) {
        double angle = (Math.toDegrees(Math.atan2(y - cy, x - cx)) + 450) % 360;
        int value = (int) Math.round(angle / 6);
        if (value == 0) value = selectedMinutes > 30 ? 60 : 1;
        changeMinutes(value);
    }

    private void changeMinutes(int value) {
        int clamped = Math.max(1, Math.min(60, value));
        if (clamped != selectedMinutes && listener != null) listener.onMinutesChanged(clamped);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (adjustable && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            changeMinutes(selectedMinutes + 1); return true;
        }
        if (adjustable && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
            changeMinutes(selectedMinutes - 1); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 1, 60, selectedMinutes));
        if (adjustable) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (adjustable) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { changeMinutes(selectedMinutes + 1); return true; }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { changeMinutes(selectedMinutes - 1); return true; }
            if (action == android.R.id.accessibilityActionSetProgress && args != null) {
                changeMinutes(Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));
                return true;
            }
        }
        return super.performAccessibilityAction(action, args);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
