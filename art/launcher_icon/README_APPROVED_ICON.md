# Approved keyboard launcher icon — 2026-10-15

This is the user's approved minimal five-key icon; the app name is intentionally unchanged pending rebranding.

* **Source**: `approved_source_512.webp`, a cropped, compact 512×512 copy of the approved 1536px illustration.
* **Android 8+**: `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` with palette-matched vector background/foreground; visually faithful reconstruction of the five keys.
* **Android 13+ themed icons**: `drawable/ic_launcher_monochrome.xml` with independent single-color paths.
* **Android 5–7 legacy**: generated density bitmaps, regular and round: mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192 pixels.
* **Store**: generated `play-store-512.png` at 512×512 RGB.
* Generator: `scripts/branding/build_launcher_assets.py`; run with Pillow installed. A one-off workflow populates generated assets on the branding branch.

No manifest identity, app label, package ID, keyboard UI, behavior, signing configuration, or AI features change. The adaptive vector intentionally retains clean geometry rather than the source artwork's subtle shadows. Validate actual masks and monochrome tint on the Galaxy S25 before merging into a release branch.
