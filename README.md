# pomodoro-tab-s9

PomodoroTimer for tab Samsung S9

A personal, offline Android timer for Arao's Galaxy Tab S9. One physical-style dial, Start/Pause/Resume, Reset, and a short bell at zero.

## Use it on the tablet

1. Copy **Pomodoro-0.1.apk** to the Tab S9 using Quick Share or a USB cable.
2. Open the APK in **My Files**. If Android asks, allow that app to install this APK.
3. Open **Pomodoro**. Drag the outside of the dial to choose **1–60 minutes**.
4. Tap **Start** and allow notifications so the bell can play with the screen off.
5. Tap **Pause** to stop temporarily, **Resume** to continue, or **Reset** to restore the selected duration.

The default is 25 minutes. You can set 5 minutes yourself for a break. No accounts, network access, tracking, task list, ticking sound, or automatic cycles.

The screen stays awake while the timer is running and visible. Pressing the power button still locks it. Android's alarm-volume, notification-channel, and Do Not Disturb settings control whether the completion bell is audible.

## Behavior and limits

- The colored dial uses a fixed 60-minute scale and shrinks with the remaining time.
- The countdown uses Android's monotonic clock, so changing the device's clock does not change its duration.
- Rotation, backgrounding, and normal process termination preserve the countdown. An exact Android alarm delivers the completion notification without a constantly running service.
- Rebooting the device resets the timer. Android force-stop also cancels alarms; open the app and reset/start a timer afterwards.
- If notification permission is denied, the app shows a reminder and provides a foreground sound when it is visible. Background sound requires notifications.
- Minimum Android version: 8.0 / API 26. Designed for a tablet; also resizes to smaller windows.
- This is a debug-signed personal build. The source archive includes `development/debug.keystore` so later personal builds can update this installation. It uses Android's standard debug credentials and is excluded from Git. Use a separate private release key if you ever publish the app.

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
| `TimerStore.java` | Persists countdown state |
| `TimerAlarms.java` | Schedules and configures the completion alert |
| `TimerReceiver.java` | Completes a background countdown |

The 19 dependency-free state checks exercise pause/resume, the deadline boundary, reset/restart, stale alarm tokens, duplicate completion, dial limits, and countdown formatting. Run them using `scripts/test.ps1`; they do not require an emulator.

Android documentation: [Alarms](https://developer.android.com/develop/background-work/services/alarms), [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
