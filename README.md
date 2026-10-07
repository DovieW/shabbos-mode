# Shabbos Mode

Native Android app in Kotlin and Jetpack Compose. [Project ideas](docs/ideas.md).

The UI uses warm paper, brown ink, ruled lists, and a small pixel candle mark. Bundled IBM Plex serif, sans, and mono fonts work offline; their license is included in `app/src/main/assets/font_licenses/ibm_plex.txt`.

## Personal beta

Installable APKs are attached to [GitHub releases](https://github.com/DovieW/shabbos-mode/releases). See the [release process](docs/releasing.md) for versioning, signing, and publishing.

Shabbos Mode is a local-first beta for the Galaxy S25 Ultra. First launch opens a minimal welcome page. **Get started** asks for a location, then walks through **Zmanim preferences** one decision per page: candle lighting, daytime calculation, and Shabbos ending. **Set up later** opens Home with **Finish setup**; setup stays incomplete, and location-dependent reminders and automation remain unscheduled until it is finished. Choose a city or use location once; no background location is collected. These preferences are saved in DataStore and remain editable under **Settings → Zmanim preferences**.

[Hebcal](https://www.hebcal.com/home/developer-apis) supplies candle-lighting, Shabbos end, and selected zmanim; [Open-Meteo](https://open-meteo.com/en/docs) supplies hourly forecasts. Candle-lighting uses a visible local preset (18 minutes outside Israel, 20 in Israel, 40 in Jerusalem, 30 in Haifa and Zikhron Ya'akov) or a chosen lead time. Shabbos end is independently selected as nightfall (8.5°) or a fixed interval after sunset, including 72 minutes. The daytime calculation selects Gra, Magen Avraham (fixed 72-minute day), or Baal Hatanya fields for the relevant daytime zmanim. **Show both** shows Gra and MGA Shema and Shacharis deadlines with explicit labels. [Timing rationale and sources](docs/timing-practice.md).

Start and end can still be overridden for the current week. Check against your community's published times, especially on holidays. Missing astronomical nightfall is never replaced by an arbitrary fixed interval; changing location or timing clears incompatible cached times and requests a fresh sync. Once fetched, times remain available offline.

The clock uses a true black background with warm text for OLED displays. It stays awake while open, dims the display, and advances the next saved minyan and zman automatically. It can show cached weather offline with the forecast age. Close it with the top right button. Keeping the display on uses battery; plug in for an overnight session.

In Prepare, add checklist items and mark them done each week. Saved shul and minyan times continue weekly; choose **Keep times** or edit them in Shuls. One checklist notification is scheduled four hours before the displayed Shabbos start by default. If Android exposes another app's next alarm clock, that notification warns when this *next detectable* alarm falls within Shabbos. This is not a complete scan of other apps' alarms.

In Alarms, add a one-time, Friday, or Saturday alarm. Choose one of three synthesized tones or import audio, and set volume, vibration-first time, volume rise, and stop duration. Use **Preview · 8 seconds** before relying on a custom audio file. Grant **Alarms & reminders** access; the app labels alarms as **Not scheduled** when exact access is unavailable. Grant notification access for alarm and preparation alerts.

Optional Settings include a Shabbos Do Not Disturb rule, which requires Android DND access and uses the phone's existing priority exceptions, and Tasker events for selected boundaries, zmanim, and saved minyan times. In Tasker, add an Event profile using the Shabbos Mode plugin, then use `%shabbosevent` in the task. The event value is the selected key, such as `start`, `sunrise`, or `minyan:3`.

Alarms, checklists, shuls, and saved times are local. Network sync runs periodically when connected; the clock and alarms continue from local data offline. Location is requested once during setup and is not tracked in the background. Open-Meteo data is used under [CC BY 4.0](https://open-meteo.com/en/terms) for this noncommercial beta; attribution is also in Settings. Provider terms need review before public or paid distribution.

GoDaven live listings are omitted pending an approved API contract; see [access details](docs/godaven-access.md). Saved shuls are the supported experience. Photo and AI import remain future ideas.

## Develop on the desktop, preview on the phone

The working checkout is `~/repos/shabbos-mode` on `dovie-desktop-linux`. Its Codex Remote Control service is already running. The desktop builds the APK, connects to the Galaxy S25 Ultra over Tailscale, installs it, and opens the app. The script reconnects ADB before each deployment, so the USB cable is not needed for ordinary updates.

```bash
ssh dovie@dovie-desktop-linux
cd ~/repos/shabbos-mode
./scripts/preview-on-phone
```

The build uses a user-local JDK 17 at `~/.local/share/shabbos-mode/jdk17` and Android SDK at `~/Android/Sdk` on the desktop. The script accepts `JAVA_HOME`, `ANDROID_HOME`, and `SHABBOS_ANDROID_DEVICE` overrides. Its default device is `galaxy-s25u.barn-chameleon.ts.net:5555`. The phone must be online in Tailscale with ADB TCP mode enabled. A phone reboot can reset ADB TCP mode; if that happens, USB or Android's Wireless debugging pairing is needed once to re-enable it.

Read live logs from the desktop with `adb -s galaxy-s25u.barn-chameleon.ts.net:5555 logcat`.

To build without installing on a phone:

```bash
JAVA_HOME="$HOME/.local/share/shabbos-mode/jdk17" ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app:assembleDebug
```

Run unit tests with `./gradlew :app:testDebugUnitTest`. The [verification record](docs/beta-verification.md) lists the Galaxy checks completed and those still requiring elapsed time or a safe device session.
