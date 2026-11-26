package com.arao.pomodoro;

/** For longer timers replay passages around minute 16, then reach the final chord at the deadline. */
final class MusicTimeline {
    static final long TRACK = 1_500_000L;
    static final long REPRISE = 960_000L;
    static final long MAX_PASS = 512_000L;
    static long inserted(long elapsed, long duration) {
        long extra = Math.max(0L, duration - TRACK);
        long shifted = 0;
        while (shifted < extra && elapsed >= REPRISE + shifted) {
            shifted += Math.min(MAX_PASS, extra - shifted);
        }
        return shifted;
    }
    private MusicTimeline() {}
}
