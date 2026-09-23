# Personal beta verification

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
