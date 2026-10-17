# R2.5.2 — LatinIME lifecycle forensic STOP

**Date:** 2026-10-16. **Base:** `d10009ee3d2ae40b02216a8a123ea576c69fe367` · `feat/tactic-r243-hybrid-writing` · PR #8 Draft.

**Verdict: NO-GO, STOP before modifying product code.** Owner explicitly requires stopping if applying By App requires an unsafe visible `InputView` reconstruction; that is the unmitigated path in current sources. This is a **static architectural finding**, not proof that physical input composition was lost. No R2.5.2 Android/IME integration tests were run and no new device validation is claimed.

## Verified call order (source of truth)

1. `LatinIME.onStartInputInternal(EditorInfo,restarting)` reads `editorInfo.packageName` for language subtype, but **never starts an app-color session**. Deferred `UIHandler` callbacks can reorder pending finish/start around orientation transitions.
2. `LatinIME.onStartInputViewInternal`: `isDifferentTextField = !restarting || inputTypeChanged`. For `restarting=true`, same input type and unchanged orientation, it **skips `loadSettings()`** even when `EditorInfo.packageName` changes.
3. `SettingsValues` creates `mColors = KeyboardTheme.getColorsForCurrentTheme(context)` inside `loadSettings()`, before invoking `KeyboardSwitcher.updateKeyboardTheme()`.
4. `KeyboardTheme` uses local `MonetKeyboardColors` for ten families, the legacy `dynamic` path for Android `DynamicColors`, and leaves static/custom themes distinct; there is currently **no SessionColorSnapshot input**.
5. `MonetKeyboardColors(seedOverride=...)` sets `tracksSeedProvider=false`; `haveColorsChanged` then returns false, even when the owner app/epoch/fingerprint changed.
6. `KeyboardSwitcher.updateKeyboardTheme()` invokes `Settings.loadSettings` and, when the theme is considered outdated and `mKeyboardView!=null`, **`setInputView(onCreateInputView(...))`**. `setThemeNeedsReload()` additionally calls `hideWindow()/showWindow()` while visible. `onStartInputViewInternal` invokes `updateKeyboardTheme()` **before** `mInputLogic.startInput(...)`, so a new chromatic epoch may rebuild a visible view before input/composition state is re-established. There is no proven composition-safe guard.
7. `AppSessionColorCoordinator` produces immutable snapshots and delayed `warmAsync` without UI callbacks (good); its `fingerprint` currently incorporates a newly incremented epoch even for an unchanged seed. Blindly interpreting this as a render-fingerprint would produce unnecessary updates across fields. Its restart semantics currently apply only to the isolated coordinator, not to LatinIME.

## Safety blockers

- **B1 / visible reconstruction:** No verified atomic, pre-first-frame-only palette transition for a warm A→B when the same `InputView` is already attached/shown. Forcing a new palette will hit the rebuild path, and keeping the old view can display A colors under host B. Neither is an acceptable unqualified implementation.
- **B2 / stale restart:** Same `inputType`, `restarting=true`, distinct package may bypass `loadSettings`, keeping old `mColors`.
- **B3 / fingerprint:** Bare `seedOverride` does not detect identity/seed transitions. Naively using every epoch as a visual change would also unnecessarily re-create the view.
- **B4 / physical:** Lossless A→B→A composition, multiwindow/DeX first frame, and active-view color transition are **not demonstrated** in JVM, emulator or physical S25.

## Conditions to resume R2.5.2 safely (not authorization for R2.5.3)

- Implement **pure/testable session transition policy first**, distinguishing editor epochs from *effective rendered palette* fingerprints and rejecting stale finish events. Prove no I/O/PM calls or worker callbacks touch visible UI.
- Find/verify a way to swap palette **only at a proven safe input-view boundary**; if neither atomic palette application nor zero-loss visible switch is demonstrable, retain this NO-GO. Do **not** use `hideWindow()/showWindow()` or `setInputView()` as a blind workaround.
- Add A→B→A, same-package different field, restart across packages, composing text, protected field, null host, dark mode, cache miss/hit and disabled-mode tests. Require measured 20 scheme/mode combinations, full `ColorType`, contrast and all JVM/edit-contract/Android vector regression evidence **after** a safe patch exists.
- Keep **By App OFF by default**, allow only test-injected activation, no preferences. Preserve `debugNoMinify/AndroidManifest.xml` boot-receiver test isolation, all writing/AI dictionaries, manifest/permissions, package ID/signing/branding.

## Evidentiary limits

The baseline R2.5.1 [run 38986684492](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38986684492) previously passed 520/520 JVM, 80/80 host contracts, 1/1 smoke and 5/5 Android vector tests. These are **baseline results, not new R2.5.2 integration results**.

Static checks can be reproduced with `python3 experiments/dynamic-by-app/check_r252_lifecycle.py`; a positive detection intentionally exits **2 (NO-GO)**. It is a structural tripwire, **not** a behavioral, Android or safety proof. Nothing is released, merged or installed.
