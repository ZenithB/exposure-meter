# Store graphics (pending on-device capture)

F-Droid reads graphics from this directory. These assets must come from a real
running build, so they are captured during the on-device verification pass
before the first tagged release:

- `phoneScreenshots/1.png`, `2.png`, … — screenshots in capture order.
  Suggested set: incident mode, reflected (camera) mode with the spot reticle,
  the three wheels with a lock engaged and the delta badge, and the settings
  screen.
- `icon.png` (512×512, optional) — F-Droid otherwise uses the launcher icon
  from the APK, which is the adaptive icon in `app/src/main/res`.
- `featureGraphic.png` (1024×500, optional).

No placeholder binaries are committed; add the real PNGs here when capturing.
