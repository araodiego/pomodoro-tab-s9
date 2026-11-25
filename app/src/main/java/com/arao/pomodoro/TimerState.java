package com.arao.pomodoro;

/** Countdown arithmetic uses a monotonic clock, so changing the device time cannot move it. */
public final class TimerState {
    public static final long MINUTE = 60_000L;
    public long duration = 25 * MINUTE;
    public long pausedRemaining = duration;
    public long deadline;
    public boolean running;
    public boolean finished;
    public String token = "";

    public long remaining(long now) {
        return running ? Math.max(0L, deadline - now) : pausedRemaining;
    }

    public void selectMinutes(int minutes) {
        if (running) return;
        duration = Math.max(1, Math.min(60, minutes)) * MINUTE;
        reset();
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
        token = "";
    }

    public void reset() {
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
        token = "";
        return true;
    }

    public static String format(long millis) {
        long seconds = (Math.max(0L, millis) + 999L) / 1000L;
        return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
