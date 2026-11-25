package com.arao.pomodoro;

/** Countdown arithmetic uses a monotonic clock, so changing the device time cannot move it. */
public final class TimerState {
    public static final long MINUTE = 60_000L;
    public static final long BREAK_DURATION = 5 * MINUTE;
    public enum Phase { FOCUS, BREAK }
    public Phase phase = Phase.FOCUS;
    public long focusDuration = 25 * MINUTE;
    public long duration = focusDuration;
    public long pausedRemaining = duration;
    public long deadline;
    public boolean running;
    public boolean finished;
    public String token = "";

    public long remaining(long now) {
        return running ? Math.max(0L, deadline - now) : pausedRemaining;
    }

    public long alarmAtWallTime(long wallNow, long elapsedNow) {
        return wallNow + remaining(elapsedNow);
    }

    public void selectMinutes(int minutes) {
        if (!canAdjustDial()) return;
        focusDuration = Math.max(1, Math.min(60, minutes)) * MINUTE;
        reset();
    }

    public boolean canAdjustDial() {
        return phase == Phase.FOCUS && !running && !finished;
    }

    public String primaryAction() {
        String phaseName = phase == Phase.FOCUS ? "focus" : "break";
        if (running) return "Pause " + phaseName;
        if (finished) return phase == Phase.FOCUS ? "Start break" : "Start focus";
        return (pausedRemaining < duration || !token.isEmpty() ? "Resume " : "Start ") + phaseName;
    }

    /** The primary action waits for a tap at each phase boundary. */
    public boolean startNext(long now, String newToken) {
        if (running) return false;
        if (finished && phase == Phase.FOCUS) return startBreak(now, newToken);
        if (finished && phase == Phase.BREAK) return startFocus(now, newToken);
        start(now, newToken);
        return true;
    }

    public boolean startBreak(long now, String newToken) {
        if (running || !finished || phase != Phase.FOCUS) return false;
        phase = Phase.BREAK;
        duration = BREAK_DURATION;
        pausedRemaining = duration;
        finished = false;
        start(now, newToken);
        return true;
    }

    public boolean startFocus(long now, String newToken) {
        if (running) return false;
        reset();
        start(now, newToken);
        return true;
    }

    public boolean matchesCompletion(String expectedToken) {
        return finished && !running && expectedToken != null && !expectedToken.isEmpty()
                && token.equals(expectedToken);
    }

    public void start(long now, String newToken) {
        if (running) return;
        if (finished || pausedRemaining <= 0) pausedRemaining = duration;
        deadline = now + pausedRemaining;
        running = true;
        finished = false;
        token = newToken;
    }

    public void pause(long now) {
        if (!running) return;
        pausedRemaining = remaining(now);
        running = false;
        finished = pausedRemaining == 0;
    }

    public void reset() {
        phase = Phase.FOCUS;
        duration = focusDuration;
        pausedRemaining = duration;
        deadline = 0;
        running = false;
        finished = false;
        token = "";
    }

    public boolean complete(long now, String expectedToken) {
        if (!running || !token.equals(expectedToken) || remaining(now) > 0) return false;
        running = false;
        finished = true;
        pausedRemaining = 0;
        return true;
    }

    public static String format(long millis) {
        long seconds = (Math.max(0L, millis) + 999L) / 1000L;
        return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
