# Exposure Meter

A photographic light meter and exposure calculator for Android. It measures scene
light — incident via the ambient light sensor, or reflected spot metering via the
camera — and presents the exposure triangle (aperture, shutter, ISO) as three
camera-style scrolling wheels. Lock any parameter and adjusting an unlocked one
moves the remaining free parameter so exposure stays correct for the metered light.

Built for [F-Droid](https://f-droid.org/): 100% free/open source, **no network
access**, no proprietary dependencies (no Google Play Services, Firebase, or
analytics). The only runtime permission is `CAMERA`, requested only when you first
switch to reflected metering.

## Status

Early development. Built in phases (see the project brief):

- [x] **Phase 1 — Engine.** Pure-Kotlin exposure maths: third-stop tables, EV
      conversions, and the lock-model solver, with a full unit-test suite.
- [ ] Phase 2 — UI shell + manual EV mode (wheels, locks, delta badge, EVcomp, ND).
- [ ] Phase 3 — Incident metering (ambient light sensor).
- [ ] Phase 4 — Reflected spot metering (CameraX).
- [ ] Phase 5 — F-Droid packaging (Fastlane metadata, tagged release).

## Modules

| Module    | Contents                                                                 |
|-----------|--------------------------------------------------------------------------|
| `:engine` | Pure Kotlin/JVM. Stop tables, EV maths, lock-model solver. No Android deps — unit-testable without an emulator. |
| `:app`    | Compose UI, ViewModels, sensor + CameraX adapters, DataStore. *(Added in Phase 2.)* |

The exposure engine is deliberately Android-free so the maths — the heart of the
app — is fully testable on any JVM. See
[`engine/src/main/kotlin/com/zenni/exposuremeter/engine`](engine/src/main/kotlin/com/zenni/exposuremeter/engine).

## The exposure model

All internal state is held as **EV at ISO 100 (EV₁₀₀)** plus three third-stop
parameter indices; arithmetic is done in log2 space. Each parameter scale's
third-stop index equals its exposure contribution in thirds, so the exposure
constraint reduces to exact integer arithmetic:

```
apertureIndex + shutterIndex − isoIndex = 3·(EV₁₀₀ + EVcomp + NDstops + CAL)
```

The solver moves the appropriate free wheel to satisfy this, snapping to the
third-stop grid and folding any rounding residual or range-clamp shortfall into an
over/under-exposure delta. Derivations are documented in KDoc on the relevant
functions.

## Building & testing

Requires a JDK 17. The Gradle wrapper fetches Gradle itself.

```sh
./gradlew :engine:test        # run the engine unit tests
```

The `:app` module (Phase 2 onward) additionally needs the Android SDK.

## License

[GPL-3.0-or-later](LICENSE).

## Notes for the product owner

Open items from the brief (§12) are **not yet decided** and can still be changed:

- **App name and final application id.** Currently the placeholder
  `com.zenni.exposuremeter` — F-Droid treats the application id as permanent, so
  this must be finalised before the first tagged release.
- **ISO range** (25–102400) and **aperture range** (f/1.0–f/64) were chosen
  unilaterally and are open to veto.
- **Zero-lock absorber rule** (least-recently-touched; default shutter, then
  aperture) chosen unilaterally and open to veto.

There is also a sign question flagged in `MeterInputs` KDoc: the brief's ND prose
("longer exposure required") reads opposite to its literal equation (§3.3). The
equation is implemented as written, with the sign isolated so it can be flipped
after review.
