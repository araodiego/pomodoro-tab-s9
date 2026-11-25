# pomodoro-tab-s9

PomodoroTimer for tab Samsung S9

Work variant on `work/jira-focus`: **Pomodoro Work** installs alongside the personal timer. A cream card above the dial shows the current JIRA. Tap it to enter a full issue key, save it, or clear it. After saving a key, entering just a number reuses that project prefix. The card says RETURN TO during breaks. Editing the key leaves the countdown running; the key is stored locally and survives restarts.

Install `Pomodoro-Work-0.3.apk` for this variant. The personal app has its own independent timer and preferences. See the [original design options](docs/jira-focus-design.md).

A personal, offline Android timer for Arao's Galaxy Tab S9. A physical-style focus dial, a five-minute break, and a short bell at each completion.

## Use it on the tablet

1. Copy **Pomodoro-Work-0.3.apk** to the Tab S9 using Quick Share or a USB cable. It installs as a separate work app.
2. Open the APK in **My Files**. If Android asks, allow that app to install this APK.
3. Open **Pomodoro**. Drag the outside of the dial to choose **1–60 minutes**.
4. Tap **Start** and allow notifications so the bell can play with the screen off.
5. Tap **Pause** to stop temporarily, **Resume** to continue, or **Reset** to restore the selected duration.
6. When focus finishes, tap **Start 5-minute break**. The background fades from deep green (`#192522`) to dark blue (`#183744`). You receive the full five minutes starting from that tap.
7. When the break finishes, tap **Start focus** to start another session with your original focus duration. The background returns to green.

Focus defaults to 25 minutes and can be set to 1–60 minutes. Breaks are fixed at five minutes. Each transition waits for your tap. **Focus again** skips a break after focus finishes; **End break** returns to a ready focus timer without starting it. The completion notification offers the same next-phase action as the app.

No accounts, network access, tracking, task list, or ticking sound.

The screen stays awake while the timer is running and visible. Pressing the power button still locks it. Android's alarm-volume, notification-channel, and Do Not Disturb settings control whether the completion bell is audible.

## Behavior and limits

- The colored dial uses a fixed 60-minute scale and shrinks with the remaining time.
- Focus stays green until a break actually starts. A running, paused, or completed break stays blue. Explicit FOCUS / BREAK / PAUSED / COMPLETE labels identify the state alongside color.
- The dial adjusts focus only. Five-minute breaks never overwrite the selected focus length.
- Saved notification actions identify the completed session; old notifications and repeated taps cannot restart a newer timer.
- The countdown uses Android's monotonic clock, so changing the device's clock does not change its duration.
- Rotation, backgrounding, and normal process termination preserve the countdown. An exact Android alarm delivers the completion notification without a constantly running service.
- While running, the timer uses Android's alarm-clock scheduling so a short break can ring during device idle. Android may show a system alarm indicator. Changing the device clock rearms the alarm from the remaining monotonic countdown.
- Rebooting the device resets the timer. Android force-stop also cancels alarms; open the app and reset/start a timer afterwards.
- If notification permission is denied, the app shows a reminder and provides a foreground sound when it is visible. Background sound requires notifications.
- Minimum Android version: 8.0 / API 26. Designed for a tablet; also resizes to smaller windows.
- This is a debug-signed personal build. The original source archive includes `development/debug.keystore` so later personal builds can update this installation. It uses Android's standard debug credentials and is excluded from Git. A fresh GitHub clone builds with your local default debug key; restore the original key to update the existing installation. Use a separate private release key if you publish the app.

## Open the project

1. Install/open Android Studio on Windows.
2. Choose **Open** and select this `pomodoro` directory.
3. Use **JDK 17** for Gradle. Install **Android SDK 36** if prompted.
4. Let Gradle sync, then run on the Tab S9 using USB debugging, or a tablet emulator.

Build from PowerShell with JDK 17 and the Android SDK configured:

```powershell
.\gradlew.bat assembleDebug lintDebug
.\scripts\test.ps1
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Implementation

Java 17, native Android Views and Canvas, Android Gradle Plugin 8.13.2, Gradle 8.13. No runtime libraries or network permission. The dial supports finger/S Pen input, keyboard arrows, and accessibility adjustment actions.

| File | Responsibility |
| --- | --- |
| `MainActivity.java` | One-screen interface and user controls |
| `TimerDialView.java` | Draws and adjusts the physical-style dial |
| `TimerState.java` | Clock-independent countdown behavior |
| `TimerController.java` | Shared app/notification transitions and alarm scheduling |
| `TimerStore.java` | Persists countdown state |
| `TimerAlarms.java` | Schedules and configures the completion alert |
| `TimerReceiver.java` | Completes a background countdown |
| `TimerActionReceiver.java` | Starts the next phase from a completion notification |
| `ClockChangeReceiver.java` | Rearms the completion alarm after a device clock adjustment |

The dependency-free state checks exercise pause/resume, the deadline boundary, reset/restart, stale alarm and notification tokens, duplicate completion, dial limits, delayed break starts, preserved focus duration, and phase transitions. Run them using `scripts/test.ps1`; they do not require an emulator.

Android documentation: [Alarms](https://developer.android.com/develop/background-work/services/alarms), [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
