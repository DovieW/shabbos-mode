# Shabbos Mode

Native Android app in Kotlin and Jetpack Compose. [Project ideas](docs/ideas.md).

## Personal beta

Shabbos Mode is a local-first beta for the Galaxy S25 Ultra. Set a city or use current location before Shabbos. The app then caches candle-lighting, Shabbos end, and selected zmanim from [Hebcal](https://www.hebcal.com/home/developer-apis), plus hourly forecasts from [Open-Meteo](https://open-meteo.com/en/docs). Start and end can be changed in Settings. Check the displayed times against your practice, especially on holidays. When Hebcal does not return a Friday candle or Saturday Havdalah item, the app falls back to Friday sunset minus 18 minutes or Saturday sunset plus 50 minutes.

The clock stays awake while it is open, dims the display, and advances the next saved minyan and zman automatically. It can show cached weather offline with the forecast age. Close it with the top right button. Keeping the display on uses battery; plug in for an overnight session.

In Prepare, add checklist items and mark them done each week. Saved shul and minyan times continue weekly; choose **Keep last week's times** or edit them in Shuls. One checklist notification is scheduled four hours before the displayed Shabbos start by default. If Android exposes another app's next alarm clock, that notification warns when this *next detectable* alarm falls within Shabbos. This is not a complete scan of other apps' alarms.

In Alarms, add a one-time, Friday, or Saturday alarm. Choose one of three synthesized tones or import audio, and set volume, vibration-first time, volume rise, and stop duration. Use **Test tone** before relying on a custom audio file. Grant **Alarms & reminders** access; the app labels alarms as **Not scheduled** when exact access is unavailable. Grant notification access for alarm and preparation alerts.

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
