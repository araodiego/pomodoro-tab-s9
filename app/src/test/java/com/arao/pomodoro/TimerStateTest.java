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

        TimerState flow = new TimerState();
        flow.selectMinutes(40);
        check(flow.phase == TimerState.Phase.FOCUS && flow.canAdjustDial(), "Ready timer is focus and adjustable");
        check(!flow.startBreak(0, "too-early"), "A break cannot start before focus completes");
        flow.startNext(1_000, "focus-one");
        long focusEnd = 1_000 + 40 * TimerState.MINUTE;
        check(flow.complete(focusEnd, "focus-one"), "Focus completion is accepted");
        check(flow.phase == TimerState.Phase.FOCUS && flow.finished && !flow.running,
                "Focus completion waits in the focus phase, keeping the focus background");
        check(flow.matchesCompletion("focus-one"), "Finished focus identifies its notification action");
        check(!flow.matchesCompletion(null) && !flow.matchesCompletion("") && !flow.matchesCompletion("older"),
                "Missing and stale notification tokens are rejected");
        check(!flow.canAdjustDial(), "Completed focus cannot be accidentally changed by dragging");
        flow.selectMinutes(1);
        check(flow.focusDuration == 40 * TimerState.MINUTE, "Completed focus retains its duration");
        long actualBreakStart = focusEnd + 2 * TimerState.MINUTE;
        check(flow.remaining(actualBreakStart) == 0, "Waiting after focus does not start a hidden break");
        check(flow.startNext(actualBreakStart, "break-one"), "Primary action starts the break");
        check(flow.phase == TimerState.Phase.BREAK && flow.running,
                "Break phase and background begin only after the start action");
        check(flow.remaining(actualBreakStart) == 5 * TimerState.MINUTE,
                "Starting two minutes late still gives a full five-minute break");
        check(flow.focusDuration == 40 * TimerState.MINUTE && flow.duration == 5 * TimerState.MINUTE,
                "Break has a separate duration and preserves custom focus length");
        check(!flow.matchesCompletion("focus-one"), "An old focus notification cannot change the running break");
        check(!flow.startNext(actualBreakStart + 100, "double-tap"), "Repeated start action cannot restart an active timer");
        check(flow.token.equals("break-one"), "Repeated action does not replace the current session token");
        flow.pause(actualBreakStart + TimerState.MINUTE);
        check(flow.phase == TimerState.Phase.BREAK && !flow.running && !flow.canAdjustDial(),
                "Paused break keeps its phase and cannot change the dial");
        check(flow.remaining(actualBreakStart + 30 * TimerState.MINUTE) == 4 * TimerState.MINUTE,
                "Paused break does not silently elapse");
        flow.selectMinutes(15);
        check(flow.duration == 5 * TimerState.MINUTE && flow.focusDuration == 40 * TimerState.MINUTE,
                "Dragging a paused break cannot overwrite focus duration");
        long resumeAt = actualBreakStart + 31 * TimerState.MINUTE;
        flow.startNext(resumeAt, "break-resumed");
        check(flow.phase == TimerState.Phase.BREAK && flow.remaining(resumeAt) == 4 * TimerState.MINUTE,
                "Break resumes the remaining time in the same phase");
        long breakEnd = resumeAt + 4 * TimerState.MINUTE;
        check(!flow.complete(breakEnd, "break-one"), "Cancelled pre-pause break alarm is ignored");
        check(flow.complete(breakEnd, "break-resumed"), "Resumed break completes at its deadline");
        check(flow.phase == TimerState.Phase.BREAK && flow.finished && !flow.running,
                "Break completion keeps the break background and waits for a focus action");
        check(flow.matchesCompletion("break-resumed"), "Break completion exposes a valid next-focus action");
        check(!flow.complete(breakEnd, "break-resumed"), "Break completion cannot ring twice");
        check(flow.startNext(breakEnd + 1_000, "focus-two"), "Next primary action starts focus");
        check(flow.phase == TimerState.Phase.FOCUS && flow.remaining(breakEnd + 1_000) == 40 * TimerState.MINUTE,
                "Next focus restores all forty selected minutes");
        flow.complete(breakEnd + 1_000 + 40 * TimerState.MINUTE, "focus-two");
        check(flow.startFocus(breakEnd + 42 * TimerState.MINUTE, "focus-skip"), "Focus again skips the break");
        check(flow.phase == TimerState.Phase.FOCUS && flow.duration == 40 * TimerState.MINUTE,
                "Skipping break starts another focus with the saved duration");
        flow.reset();
        flow.startNext(0, "focus-for-end-break");
        flow.complete(40 * TimerState.MINUTE, "focus-for-end-break");
        flow.startNext(40 * TimerState.MINUTE, "break-to-end");
        flow.reset();
        check(flow.phase == TimerState.Phase.FOCUS && !flow.running && !flow.finished && flow.canAdjustDial(),
                "End break returns to an adjustable ready focus timer");
        check(flow.remaining(0) == 40 * TimerState.MINUTE && !flow.matchesCompletion("break-to-end"),
                "End break restores focus duration and invalidates notification actions");
        TimerState legacy = new TimerState();
        legacy.finished = true;
        legacy.pausedRemaining = 0;
        check(legacy.startNext(100, "migrated-break") && legacy.phase == TimerState.Phase.BREAK,
                "A completed version 0.1 timer can start a break after upgrading");
        check(legacy.alarmAtWallTime(1_000_000, 100) == 1_300_000,
                "System alarm time includes the full break duration");
        check(legacy.alarmAtWallTime(4_600_000, 60_100) == 4_840_000,
                "After the clock moves forward, rearming keeps four actual minutes");
        check(legacy.alarmAtWallTime(100_000, 60_100) == 340_000,
                "After the clock moves backward, rearming keeps four actual minutes");
        check(legacy.remaining(60_100) == 4 * TimerState.MINUTE,
                "Wall-clock alarm conversion cannot change the countdown");
        check("PROJECT-1234".equals(JiraKey.normalize(" project-1234 ", "")), "Keys are trimmed and normalized to uppercase");
        check("PROJECT-5678".equals(JiraKey.normalize("5678", "PROJECT-1234")), "Number-only entry reuses the saved prefix");
        check(JiraKey.normalize("1234", "") == null, "First key requires a project prefix");
        check(JiraKey.normalize("", "PROJECT-1234") == null, "Empty key requires explicit Clear");
        check(JiraKey.normalize("PROJECT-1234 extra", "") == null, "Invalid key text is rejected");
        check("OTHER-98765".equals(JiraKey.normalize("other-98765", "PROJECT-1234")), "Full keys can switch project and support longer issue numbers");
        check(JiraKey.normalize(null, "") == null, "Null key input is rejected");
        check(JiraKey.normalize("PROJECT-0", "") == null, "Issue number must be positive");
        System.out.println("Passed " + checks + " timer and JIRA behavior checks.");
    }
}
