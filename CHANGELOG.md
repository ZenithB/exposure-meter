# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

Everything below is implemented and awaiting the first tagged release. The
release tag (`vX.Y.Z`, `versionCode 1`) is held until the final application id
is confirmed, because F-Droid treats the application id as permanent.

### Added
- Pure-Kotlin exposure engine: third-stop stop tables (aperture, shutter, ISO),
  log2 EV maths (incident C=250, reflected spot with sRGB linearisation), and
  the lock-model solver, with a full JVM unit-test suite.
- Three camera-dial wheels with per-wheel locks, snap-to-detent and haptics.
- Over/under-exposure delta badge in thirds, colour-coded.
- Incident metering via the ambient light sensor, median-filtered against
  flicker, with a hold/freeze toggle and an EV + lux readout.
- Reflected spot metering via CameraX: tap-to-spot with a reticle, linearised
  region luma, camera AE result (N/t/S) with an aperture fallback chain.
- Manual EV mode (type or nudge).
- Exposure compensation (±5 EV), ND filter compensation, and per-mode
  calibration offsets, persisted with Jetpack DataStore.
- Material 3 UI with dynamic colour on Android 12+ and a static fallback scheme.

### Notes
- No `INTERNET` permission; `CAMERA` only, requested on first use of reflected
  mode. No Google Play Services, analytics, or tracking.
