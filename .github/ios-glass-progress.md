# iOS Glass Keyboard — progress

Branch: `feature/ios-glass-keyboard`
Upstream: `HeliBorg/HeliBoard:main`
Draft PR: `#1`

## M1 — usable iOS Glass keyboard

- [x] Fork kept remote-only; no local repository required on the phone.
- [x] Separate package `com.jorgeprdz.ioskeyboard` so testing cannot overwrite stock HeliBoard.
- [x] iOS-inspired rounded-square keycaps wired into the default Material keyboard style.
- [x] Functional keys changed from pills to matching rounded rectangles.
- [x] Light/dark frosted panel fallback with rounded top corners.
- [x] Android 12+ `FLAG_BLUR_BEHIND` / cross-window blur attempt with fail-safe frost fallback.
- [x] Independent 1–100% haptic-strength slider.
- [x] Existing vibration-duration slider preserved; custom pulses use amplitude control when hardware supports it.
- [x] Feature-branch GitHub Actions debug build configured.
- [ ] GitHub Actions enabled for this newly created fork.
- [ ] CI compile green.
- [ ] Physical-device visual/haptic validation on Galaxy S25.

## Guardrails

- Preserve HeliBoard input engine, dictionaries, autocorrection, multilingual support, emoji and clipboard.
- Do not merge into `main` before physical-device validation.
- Do not claim real background blur when the platform/OEM rejects cross-window blur; keep the frosted fallback.
- Keep upstream mergeability: isolate iOS-specific behavior and avoid unrelated refactors.
