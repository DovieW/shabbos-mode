# Personal beta verification

## Community labels — October 7, 2026 (unreleased)

- Added short community associations beneath the daytime and Shabbos-ending options in onboarding and the shared Settings editor. Gra/MGA labels retain the overlap between Ashkenazi and Sephardi practice; help explains differing Shema/tefillah choices and calculation variants. Sources and limits are recorded in `docs/timing-practice.md`.
- Debug build and lint pass. Reviewed both pages on the medium Android 16 emulator (1080×2340 at 440 dpi) at normal and 200% text. Captions wrap without truncation; all choices and fixed Continue/Done actions remain accessible. Both help dialogs scroll and Close remains accessible. Completed setup and confirmed the labels appear in the scrolling Settings editor with its fixed Save action.
- Screenshots are under `app/build/screenshots/community-labels/`. Testing used only the owned emulator with audio disabled; no alarm was created or previewed and the phone was untouched. The published 0.1.2 APK does not include these changes.

## Release 0.1.1

- Includes location/timing setup and the refined paper UI described below. Version 0.1.1/code 2 uses the persistent release certificate from 0.1.0.
- All 10 unit tests, release lint, signature verification, APK identity/version, and non-debuggable checks pass.
- On the medium Android 16 emulator, installed the signed APK over signed 0.1.0 without uninstalling. Completed setup and confirmed a checklist item created in 0.1.0 was retained; removed that test item afterward. No alarm was saved or previewed, and the phone was not used.
- Release builds now use a bounded single-use Gradle JVM and in-process Kotlin compilation, avoiding persistent build daemons on the desktop.
- Galaxy device and elapsed-time checks remain as listed below.

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

## Onboarding implementation — October 6, 2026 (0.1.1)

- Three sparse screens: location, Shabbos timing, and zmanim tradition. Settings reuse the same choices; optional timing inputs use a numeric keyboard and range feedback.
- Debug build, 10 unit tests, and Android lint passed. Timing tests cover regional candle defaults, independent Havdalah flags, Gra/MGA/Baal Hatanya field mapping, and distinctly labeled dual deadlines.
- Live Hebcal checks confirmed New York nightfall versus fixed 72-minute end times, Jerusalem's 40-minute candle lead, and the expected Gra, MGA, and Baal Hatanya REST fields. Regional calendar behavior was checked on a holiday weekend; this beta does not prompt for holiday visitor overrides.
- Fresh Android 16 emulator at 1080×2340 / 440 dpi: city search, location permission denial with manual recovery, setup completion, normal and 200% text layouts, persistent setup/location/timing after restart, and offline cached displays passed.
- Room cache inspection confirmed separate Gra/MGA Shema events and a Shabbos end exactly 72 minutes after sunset. Offline editing to Chabad/nightfall persisted the new preference, removed incompatible cached events, and displayed Awaiting times.
- Testing used an emulator with audio disabled. No phone playback or alarm preview was performed. A successful one-time location fix still needs a real-device check; denial and city fallback were verified.
- Desktop memory pressure initially crashed the emulator. Stopping this task's Gradle/Kotlin daemons and using a bounded, single-use build restored stable verification.
- The published 0.1.0 APK predates this implementation. Existing Galaxy overnight/real alarm/DND/Tasker verification gates remain in effect.

## Refined paper follow-up — October 6, 2026 (0.1.1)

- Smaller, medium-weight Plex headings; darker secondary copy; tighter headers, lists, empty states, and editor padding. Touch targets remain at least 48 dp.
- Home groups the next boundary and clock action on one paper panel. Setup uses compact choices with an ink-colored selected state and keeps Continue/Done beside the choices in the scrolling flow. Optional timing controls have quieter labels.
- Debug build and lint pass; all 10 existing unit tests passed during this refinement. Reviewed setup, Home, Settings, Prepare, and an unsaved alarm draft on the medium Android 16 emulator. At 200% text, setup choices and alarm days wrap, and actions remain reachable. Confirmed the alarm editor’s Save stays above the visible software keyboard.
- Screenshots are under `app/build/screenshots/paper-polish/`. Testing used the emulator with audio disabled; no alarm was saved or previewed and the phone was not used.

## Guide cleanup (0.1.2)

- Setup now gives location, candle lighting, Shabbos end, and zmanim their own pages. Removed the repeated app heading, long location subtitle, and calculation jargon from the main choices. Quiet pixel progress marks have a spoken step description.
- Technical explanations are available under Help choosing; Other opens a minute dialog instead of expanding chips and fields in the page. Not sure explicitly identifies Gra and Magen Avraham. Timing calculations and saved preference keys are unchanged.
- Location starts with one active location action and offers city search as an alternative. City results omit repeated place names.
- Debug build and lint pass. Reviewed the medium Android 16 emulator at normal and 200% text: help content remains scrollable, custom presets work, an out-of-range value cannot be confirmed, cancelling preserves the old choice, back navigation preserves a selected 50-minute end, and setup saves that preference in Settings.
- Continue and Done now use a fixed bottom footer outside the scrolling choices and page transitions. Debug build and lint pass; normal and 200% text checks on the medium emulator confirm the actions remain clear of the navigation bar. Back/Continue/Done still work, and the selected Magen Avraham / 72-minute end persists in Settings. Screenshots are under `app/build/screenshots/setup-footer/`.
- Zmanim preferences is now the parent of candle lighting, daytime calculation, and Shabbos ending in setup and Settings, in that order. The daytime choices include an explicit Show both option. Debug build and lint pass; medium-emulator checks at normal and 200% text cover the revised pages, help dialog, back navigation, completion, scrolling Settings editor, and independently persisted Magen Avraham / 72-minute selections. DataStore keys and calculations are unchanged. Screenshots are under `app/build/screenshots/zmanim-hierarchy/`.
- Only the owned emulator fixture was used, with audio disabled. No alarm was saved or previewed and the phone was not touched. These changes are not included in the published 0.1.1 APK.

## Welcome and deferred setup (0.1.2)

- Welcome contains only the pixel candles, app name, Get started, and Set up later. Actions sit above the navigation bar; the logo uses the same square geometry as the header.
- Deferring setup is persisted separately from completion. Home offers Finish setup. Cached Shabbos/zmanim events do not schedule reminders, DND boundaries, or zmanim Tasker events while setup is deferred. Completing setup clears the deferred flag and requests a refresh after completion is saved.
- Debug build, all 10 existing unit tests, and lint pass. Reviewed the medium Android 16 emulator at normal and 200% text. Get started, Back, skip, restart, resume with the saved city, and completion passed. A cached reminder was removed on deferral, remained unscheduled after restart, and returned after setup completion; completed setup remained complete after another restart.
- Screenshots are under `app/build/screenshots/welcome/`. Testing used only the owned emulator with audio disabled; no alarm was created or previewed and the phone was untouched. This is not included in the published 0.1.1 APK.
