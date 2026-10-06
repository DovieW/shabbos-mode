# Releases

GitHub version tags build a signed APK and attach it and a SHA-256 checksum to a GitHub release. The workflow uses pinned actions and publishes only after unit tests, release lint, signature verification, and APK version/debuggability checks pass.

## Create a release

1. Set `versionName` and increase `versionCode` in `version.properties`.
2. Add `docs/releases/<versionName>.md` with changes and remaining verification limits.
3. Commit and push the source changes.
4. Tag that commit and push the tag:

```bash
git tag -a v0.1.0 -m 'Shabbos Mode 0.1.0'
git push origin v0.1.0
```

The tag must match the version file. Monitor **Release APK** in GitHub Actions. A failed run can be retried, or dispatched with the existing tag. Published assets are never overwritten automatically.

## Signing

The repository's Actions secrets are `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.

`release-signing.sha256` records the public certificate fingerprint. The release script rejects APKs signed by another certificate.

The original keystore and a protected `signing.env` file are stored outside the checkout at `~/.local/share/shabbos-mode/release-signing/` on the desktop. Keep a secure backup of this directory: the same key is required for future APK updates. Do not commit signing material. [Android app signing](https://developer.android.com/studio/publish/app-signing).

To verify a release locally:

```bash
./scripts/release v0.1.0
```

This reads the desktop signing environment when no CI signing variables are supplied. `SHABBOS_SIGNING_ENV_FILE`, `JAVA_HOME`, and `ANDROID_HOME` can override the defaults. The result is in `dist/`. Gradle refuses to package unsigned releases.

The release certificate differs from earlier debug builds. Those installations need data migration before switching to a signed release; removing the debug app deletes its Room and DataStore data.
