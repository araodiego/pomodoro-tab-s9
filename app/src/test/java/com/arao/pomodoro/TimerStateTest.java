package com.arao.pomodoro;

/** Dependency-free state tests; run with scripts/test.ps1. */
public final class TimerStateTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        TimerState timer = new TimerState();
        check(timer.remaining(0) == 1_500_000, "Default is 25 minutes");
        timer.selectMinutes(1);
        timer.start(1000, "first");
        check(timer.remaining(1001) == 59_999, "Countdown uses supplied monotonic time");
        check(TimerState.format(59_999).equals("01:00"), "Display rounds up partial seconds");
        timer.selectMinutes(50);
        check(timer.duration == 60_000, "Running dial cannot accidentally change time");
        timer.pause(11_000);
        check(timer.remaining(1_000_000) == 50_000, "Paused time does not elapse");
        timer.start(1_000_000, "second");
        check(timer.remaining(1_025_000) == 25_000, "Resume preserves remaining time");
        check(!timer.complete(2_000_000, "first"), "A cancelled session cannot finish a newer one");
        check(!timer.complete(1_049_999, "second"), "Never complete early");
        check(timer.complete(1_050_000, "second"), "Complete at deadline");
        check(!timer.complete(1_050_000, "second"), "Completion is delivered once");
        check(timer.finished && timer.remaining(2_000_000) == 0, "Complete stays at zero");
        timer.start(3_000_000, "third");
        check(timer.remaining(3_000_000) == 60_000, "Start again uses the selected duration");
        timer.reset();
        check(!timer.running && !timer.finished && timer.remaining(0) == 60_000, "Reset stops and restores");
        timer.selectMinutes(100);
        check(timer.duration == 3_600_000, "Maximum 60 minutes");
        timer.selectMinutes(0);
        check(timer.duration == 60_000, "Minimum one minute");
        check(TimerState.format(0).equals("00:00"), "Zero display");
        check(TimerState.format(3_600_000).equals("60:00"), "Full dial display");
        check(TimerState.format(-1).equals("00:00"), "Never display negative time");
        timer.start(0, "late-pause");
        timer.pause(60_001);
        check(timer.finished && timer.remaining(60_001) == 0, "Pause after deadline stays complete");
        System.out.println("Passed " + checks + " timer behavior checks.");
    }
}
