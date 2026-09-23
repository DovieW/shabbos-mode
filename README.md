# Shabbos Mode

Native Android app in Kotlin and Jetpack Compose. [Project ideas](docs/ideas.md).

## Develop on the desktop, preview on the phone

The working checkout is `~/repos/shabbos-mode` on `dovie-desktop-linux`. Its Codex Remote Control service is already running. The desktop builds the APK; `scripts/preview-on-phone` copies it to `dovie-ideapad-linux`, where the Galaxy S25 Ultra is connected by USB, installs it, and opens the app.

```bash
ssh dovie@dovie-desktop-linux
cd ~/repos/shabbos-mode
./scripts/preview-on-phone
```

The build uses a user-local JDK 17 at `~/.local/share/shabbos-mode/jdk17` and Android SDK at `~/Android/Sdk` on the desktop. The script accepts `JAVA_HOME`, `ANDROID_HOME`, and `PREVIEW_HOST` overrides. It requires exactly one authorized Android device on the preview host.

To build without installing on a phone:

```bash
JAVA_HOME="$HOME/.local/share/shabbos-mode/jdk17" ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app:assembleDebug
```

The first app screen includes a test notification button to verify native notification permission and delivery on the device. Alarm scheduling and the other project ideas are still to be implemented.
