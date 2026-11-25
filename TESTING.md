# Validation for 0.4-work

My Day Ribbon adds 24 dependency-free meeting schedule checks to the existing 63 timer and JIRA checks. The meeting checks cover New York seasonal offsets, both DST transitions, weekday and weekend recurrence, editing, chronological display, and rejection of premature, stale or disabled reminders.

## Meeting device checks

1. Open My Day, edit both meeting times, verify the New York and future clock-change previews, save and reopen the app.
2. Schedule a meeting several minutes ahead with an advance reminder; start a focus timer. Check the advance notification and amber highlight, then the start prompt. Pause, verify the remaining time, and resume after a delay.
3. Repeat with the screen locked and with the app minimized; verify different sounds and direct notification actions. Check permissions and notification channel settings.
4. Change or disable a meeting after scheduling; verify old alarms do not ring. Reboot with a future meeting configured and verify the reminder after unlocking.
5. Rotate during editing, test both tablet orientations, phone and split-screen, and verify no timer controls or meeting rows are clipped.
6. Schedule a meeting at the same instant as the timer completion; confirm the meeting and timer state are both correct. Try an old meeting action after starting a new timer.

Real-device checks are pending.

## Previous timer validation

All 63 timer and JIRA behavior checks pass. JIRA parsing covers full keys, normalization, number-only entry, changing projects, and invalid input. On-device UI checks remain pending: edit/save/cancel/clear the card during a running timer, rotate with the editor open, relaunch to verify persistence, and check the RETURN TO label during a break.

- APK assembled successfully with Android SDK 36, AGP 8.13.2, Gradle 8.13, and JDK 17.
- `scripts/test.ps1`: all 63 timer and JIRA checks pass, covering manual focus/break transitions, late starts, pause/resume, custom focus length, old notification tokens, alarm-time conversion, and JIRA input.
- `lintDebug`: passes with no issues. The report is included in the source archive.
- Tablet emulator testing was attempted but could not start because the installed emulator tried to write configuration files outside the permitted workspace. No emulator interaction or audible-alert result is claimed.

## First check on the Tab S9

1. Open the app and allow notifications.
2. Turn the dial to 1 minute. Tap Start, then Pause, and confirm the number stops changing.
3. Tap Resume, lock the screen, and check that the completion bell plays.
4. Open the app again and confirm it shows `00:00`, FOCUS COMPLETE, the green background, and **Start 5-minute break**.
5. Wait two minutes, then start the break. Confirm it starts at `05:00`, says BREAK, and fades to blue.
6. Pause and resume the break. Confirm it stays blue and retains its remaining time.
7. Let the break finish with the screen off. Confirm the bell and **Start focus** notification action. Start the next focus and confirm the original focus duration and green background.
8. Try **Focus again** after focus completion and **End break** during a break.
9. Start a break from the focus-completion notification. Tap a stale or repeated notification action and confirm it does not restart or replace the current timer.
10. Try a custom 40-minute focus duration and rotate the tablet while each phase is running or paused. Confirm the focus length survives a full break.
11. During a running break, adjust the device clock forward or backward. Confirm the remaining countdown is unchanged and the alarm still rings when it reaches zero. Restore automatic time afterwards.

Also try Samsung split screen and S Pen input. These need device testing before considering this version validated for daily use.
