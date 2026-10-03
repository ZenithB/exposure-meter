# F-Droid submission checklist

Status of this repository against F-Droid's inclusion requirements, and the
steps to publish. Items marked **[blocked]** must be resolved before the first
tagged release.

## Before the first release

- [ ] **[blocked] Finalise the application id.** Currently the placeholder
  `com.zenni.exposuremeter` (in `app/build.gradle.kts`). F-Droid treats the
  application id as **permanent** — it cannot change once published. Decide the
  final id (and app name in `fastlane/.../title.txt` and `strings.xml`) first.
- [ ] **[blocked] Capture screenshots** on a real device into
  `fastlane/metadata/android/en-US/images/phoneScreenshots/`. See the README
  there. Also complete the on-device verification pass (brief §10 acceptance for
  incident and reflected metering).
- [ ] Bump `versionName` if desired; keep `versionCode` monotonically
  increasing (currently 1).

## Already satisfied

- **Free software licence.** GPL-3.0-or-later; `LICENSE` present; SPDX headers
  on every Kotlin source.
- **No proprietary dependencies.** No `com.google.android.gms` /
  `com.google.firebase`; no analytics or crash-reporting SDKs. Verify with
  `./gradlew :app:dependencies` (see "Verify" below).
- **No network.** No `INTERNET` permission in the merged manifest; only
  `CAMERA` (feature `required="false"`), requested at runtime on first use of
  reflected mode.
- **Reproducible-friendly build.** AGP, Kotlin and all libraries are pinned via
  the version catalog (`gradle/libs.versions.toml`); no dynamic versions or
  snapshots. No dependency jars are committed (only the standard Gradle wrapper
  jar). No closed-source Gradle plugins.
- **Fastlane metadata** under `fastlane/metadata/android/en-US/`
  (`title.txt`, `short_description.txt`, `full_description.txt`,
  `changelogs/1.txt`).

## Verify (hygiene)

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

# No Google Play Services / Firebase on the runtime classpath:
./gradlew :app:dependencies --configuration releaseRuntimeClasspath \
  | grep -Ei 'com\.google\.android\.gms|com\.google\.firebase' && echo "FOUND (bad)" || echo "clean"

# No committed binaries other than the Gradle wrapper jar:
git ls-files '*.jar' '*.aar' '*.so'

# Merged manifest permissions (debug):
grep -oE '<uses-permission android:name="[^"]*"' \
  app/build/intermediates/merged_manifests/debug/*/AndroidManifest.xml
```

## Publish

1. Lock the application id and app name; commit.
2. Capture screenshots; commit.
3. Tag the release: `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. Submit to F-Droid by adding a metadata recipe to `fdroiddata`
   (`metadata/<applicationId>.yml`) with `Builds` pointing at the tag, or
   request inclusion per the F-Droid docs. F-Droid builds from the tag.
