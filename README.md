# Shabbos Mode

Native Android app in Kotlin and Jetpack Compose. [Project ideas](docs/ideas.md).

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

The first app screen includes a test notification button to verify native notification permission and delivery on the device. Alarm scheduling and the other project ideas are still to be implemented.
