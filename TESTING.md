# Validation for 0.1

- APK assembled successfully with Android SDK 36, AGP 8.13.2, Gradle 8.13, and JDK 17.
- `scripts/test.ps1`: 19 countdown behavior checks pass.
- `lintDebug`: passes with no issues. The report is included in the source archive.
- Tablet emulator testing was attempted but could not start because the installed emulator tried to write configuration files outside the permitted workspace. No emulator interaction or audible-alert result is claimed.

## First check on the Tab S9

1. Open the app and allow notifications.
2. Turn the dial to 1 minute. Tap Start, then Pause, and confirm the number stops changing.
3. Tap Resume, lock the screen, and check that the completion bell plays.
4. Open the app again and confirm it shows `00:00` and Start again.
5. Try a 25-minute session and rotate the tablet while it runs.

Also try Samsung split screen and S Pen input. These need device testing before considering this version validated for daily use.
