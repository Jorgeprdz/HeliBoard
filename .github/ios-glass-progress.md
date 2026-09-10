# iOS Glass Keyboard — progress

Branch: `feature/ios-glass-keyboard`
Upstream: `HeliBorg/HeliBoard:main`

## M1 — usable iOS Glass keyboard

- [x] Fork kept remote-only; no local repository required.
- [x] iOS-inspired rounded keycaps wired into the default Material keyboard style.
- [x] Functional keys changed from pills to rounded rectangles.
- [x] Light/dark frosted panel fallback.
- [x] Android 12+ cross-window blur attempt with fail-safe frosted fallback.
- [x] Stronger custom-duration haptics using amplitude control when hardware supports it.
- [x] Feature-branch GitHub Actions debug build.
- [ ] CI compile green.
- [ ] Physical-device visual/haptic validation on Galaxy S25.

## Guardrails

- Preserve HeliBoard input engine, dictionaries, autocorrection, multilingual support, emoji and clipboard.
- Do not merge into `main` before physical-device validation.
- Do not claim real background blur when the platform/OEM rejects cross-window blur; keep the frosted fallback.
- Keep upstream mergeability: isolate iOS-specific behavior and avoid unrelated refactors.
