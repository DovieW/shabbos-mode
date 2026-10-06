# Personal beta verification

## Release 0.1.0

- Added a tag-triggered GitHub release workflow and a local release script. Versions come from `version.properties`.
- Six unit tests and release lint pass. The signed APK matches version 0.1.0/code 1, is not debuggable, and verifies against the persistent release certificate.
- Confirmed a mismatched tag and unsigned Gradle release packaging are rejected. Workflow and shell script validation pass.
- Installed the signed APK on a fresh Android 16 emulator at the medium phone size. The app opens, and the two pixel candles appear as its launcher logo. The phone was not used, and no audio or vibration was triggered.
- Remaining device and elapsed-time checks below still apply to this personal beta.

## UI refinement

- Home now emphasizes the next Shabbos boundary, with preparation progress and the next saved minyan in its navigation rows.
- Settings use expandable groups. Editors have a fixed Save action, scrolling fields, and delete confirmation.
- Shared brown/paper styling covers controls and native time pickers. Screen transitions and settings expansion honor disabled animations.
- The clock supports portrait and landscape, 12/24-hour time, larger close/schedule targets, and lower overnight brightness.
- The debug APK builds, all six unit tests pass, and Android lint passes. Tests cover week rollover, DST, next minyan selection, boundary overrides, and time formatting.
- Reviewed Home, Settings, preparation, saved shuls, the alarm/minyan editors, the clock, and the schedule on an Android 16 emulator. Live city search, Hebcal times, and weather loaded successfully.
- Confirmed Save remains visible above the keyboard, choices wrap at 200% text size, and an alarm draft survives text-size changes and rotation. Reviewed the clock in portrait and landscape, including large text, and opened Settings with animations disabled.
- On October 1, restored wireless ADB, installed the updated APK on the Galaxy, and reviewed Home and the portrait clock with its true black OLED background and hourly weather. Closed the clock afterward. The remaining screens and landscape still need Galaxy review. No alarm audio or vibration was triggered during this refinement.
- On October 6, continued in the Android 16 emulator with audio disabled. Replaced the unclear edit mark with an outlined pencil and confirmed it opens the checklist editor. Reviewed alarm choices at normal and 200% text size, checked Friday selection and the one-time date field, and added spacing between wrapped choice rows. Build and lint pass. No alarm was saved or previewed; the phone was not used.
- Follow-up: enlarged the pencil canvas from 20 to 32 dp with a stronger stroke, retaining the 48 dp tap target. Added a 16 dp gap below the alarm time divider. Reviewed the checklist and alarm editor on a medium phone viewport (1080×2340 at 440 dpi, approximately 393×851 dp), including the alarm editor at 200% text size. Build passes; no alarm was saved or previewed.
- Design pass: bundled Plex serif/sans/mono fonts with their license, a lighter paper palette, a pixel candle mark, ruled checklist rows, flat text fields, and cut-corner actions. Choice controls now expose single-selection semantics and use a brief color transition that honors disabled animations. Reviewed Home, Prepare, Settings, Shuls, alarm/minyan editors, and the OLED clock on the medium viewport. Checked 200% text on Home, Prepare, and the alarm editor, keyboard visibility of Save, and the clock in landscape at 200% text. Build, six tests, and lint pass. The phone was not used, and no alarm was saved or previewed.

## Checked on the Galaxy S25 Ultra

- Installed the debug APK and confirmed the saved location, Hebcal times, and hourly Open-Meteo forecast display.
- Opened the dim clock and confirmed Android's keep-screen-on window flag. The forecast and next Shabbos boundary displayed without taps.
- Added, checked, and removed a checklist item. Confirmed weekly minyan times, created a shul and minyan, saw that minyan on Home, then removed the test shul.
- Confirmed the default four-hour preparation reminder is scheduled as an exact `RTC_WAKEUP` alarm for Friday 2:29 PM when Shabbos start is Friday 6:29 PM.
- Confirmed Tasker discovers `TaskerEventConfigActivity` as an Event plugin.
- Confirmed the brief alarm preview starts a foreground service and stops automatically. Do not repeat audible or vibrating tests while the owner is working.

## Still requires elapsed-time or device-state checks

- A configured alarm firing on the lock screen, and stopping after its selected duration.
- The preparation notification at its actual scheduled time, including the limited other-app alarm warning.
- DND activation and deactivation at the configured boundaries with the owner's chosen exceptions.
- A selected Tasker event firing into a real Tasker profile.
- A full overnight clock session and battery measurement.
- A forecast refresh during Shabbos and the cached forecast display with the phone offline.
- Reboot, device time-zone change, and exact-alarm permission revocation and restoration. The permission was toggled and restored during testing, but the full reschedule flow was not observed end to end.

Use a zero-volume, zero-vibration alarm for silent scheduling checks. Do not change the phone's clock or DND state while the owner is using it. Restore any temporary test data and settings after each check.
