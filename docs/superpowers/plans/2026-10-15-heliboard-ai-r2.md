# HeliBoard AI R2 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline with TDD and one final independent review. Environment/signing discovery runs independently; no concurrent source mutations.

**Goal:** Build a verified intelligent keyboard APK retaining the installed debug identity and invariant keyboard UI; install only if its certificate matches the existing installation.
**Architecture:** Keep the dictionary hot path. Separate sensitivity, immutable editor snapshots, session cancellation, provider transport, entity extraction and dictation from the existing IME. A strip arbiter owns temporary content without changing dimensions, typography or themes.
**Tech Stack:** Existing Java/Kotlin Android IME, API21 floor, JDK21/Gradle/SDK37/NDK28; pure JVM logic tests plus Robolectric and Android build verification.
**Spec:** `docs/superpowers/specs/2026-10-15-heliboard-ai-r2.md` (user-supplied R2, binding).

## Global Constraints
- Preserve `com.jorgeprdz.ioskeyboard.debug`; versionCode greater than4104.
- Install only with certificate SHA256 E872CC903CA5AC08B87406B2657626F3603B3B72C75E50553432F38C1170FA92; no uninstall/clear/downgrade or default IME changes.
- No geometry/theme/font/microphone movement; AI OFF by default; only approved access icon/content may change when explicitly enabled.
- No per-key network, secret logging, sensitive input learning/cloud transmission, background clipboard/chat/audio capture or large model downloads without consent.
- Do not enable Actions, merge main or claim unexecuted checks pass. Missing signing key does not stop compilation.

## Review Focus
- Same selected text at different ranges or after a focus change must not accept stale edits.
- Whitespace/Unicode/composing and editor rejection must preserve text and Undo validity.
- Incognito, OTP and formatted banking text must never reach a provider or history.
- Prediction/autofill/clipboard callbacks must not remove deliberately active AI controls.
- Permission/audio/lifecycle loss must stop recording and prevent stale insertion.

### Task 0: Reproducible baseline, environment and signature
- [ ] Record Git head/status, immutable resource hashes, installed public certificate/package/version.
- [ ] Prove local Gradle/SDK/NDK tool execution and run baseline compilation/tests; record each failure without suppressing tests.
- [ ] Inspect authorized keystore/backup locations by public fingerprints only; provide missing-key evidence.

### Task 1: Privacy, sessions and editor contract
**Create:** `latin/ai/SensitiveInputPolicy.java`, `EditorSnapshot.java`, `AiSessionCoordinator.java`, `AiEditTransaction.java`.
**Test:** `app/src/test/java/helium314/keyboard/latin/ai/AiCoreTest.java`.
**Interfaces:** policy rejects protected input/settings/text; snapshot preserves raw selected text, range and generation; transaction calls a bounded editor adapter and retains exact Undo only for the same editor revision; coordinator owns one cancellable work item.
- [ ] RED: user incognito/formatted CLABE/card/OTP blocked; raw selection preserved; duplicate text range invalid; commit rejection never reports success; editor change invalidates undo; cancellation drops old results.
- [ ] GREEN: implement pure testable logic and then Android adapters using RichInputConnection/InputLogic cache reset.
- [ ] Run focused JVM/Robolectric checks and commit complete phase with evidence.

### Task 2: Opt-in, UI arbiter and credential lifecycle
**Modify:** `AiSettingsActivity.java`, `AiKeyboardAssistant.java`, `SuggestionStripView.kt`, `LatinIME.java`, manifest and credential backup rules.
**Create:** `SuggestionStripArbiter` equivalent state logic, explicit review Activity.
- [ ] RED: AI off routes clipboard unchanged; enabled approved access routes AI while tools menu preserves clipboard/custom codes/quick-pin; AI content survives normal callbacks and restores previous mode; font/tint use existing pipeline; lifecycle cleanup.
- [ ] GREEN: integrate P0 policy/session, off-by-default toggle, complete review and secure copy; credential states/no backup; preserve invariant layout resources.
- [ ] Verify resource baseline exceptions only for approved icon/backup/Activity metadata; commit.

### Task 3: Local contextual bilingual correction
**Create:** bounded local reviewer/corpus tests in `latin/ai/local`; integrate via debounced main-editor snapshots at word boundaries in `LatinIME`.
**Interfaces:** suggestions are spans with confidence/reason and editor revision; no cloud/runtime required by the hot path.
- [ ] RED: synthetic positive grammar/diacritics and negative mixed-language/names/quotes/numbers/URLs; cancellation and no sensitive learning.
- [ ] GREEN: generalized local candidate/rule ranking using existing dictionary context, suggestions for ambiguous edits and reversible high-confidence preference-governed changes.
- [ ] Measure corpus precision/false positives and CPU latency; retain traditional prediction; commit.

### Task 4: Gemini transport and all writing tools
**Create:** `GeminiProvider.java`, typed operations/results, bounded parser/HTTP transport and fake transport tests.
- [ ] RED: policy denies provider calls; actual disconnect/deadline; HTTP400/401/403/404/429/5xx; Retry-After, multipart/thought/safety/truncation/oversize.
- [ ] GREEN: authorized model discovery, all R2 actions/tones, explicit destination language, opt-in network, full review with numeric/negation changes flagged, no autoaccept.
- [ ] Run fake-server checks; real user-key request only when configured and authorized. Commit.

### Task 5: Explicit entities and Android actions
**Create:** span recognizer, relative-date resolver and tests; explicit PROCESS_TEXT/share review entry.
- [ ] RED: mixed meeting/time/address/phone, ambiguous dates, false address/company positives, formatted CLABE and multiple candidates, safe URL schemes.
- [ ] GREEN: intents prefilled by exact spans, editable calendar/contact/dial/maps, confidential copy excluded from DAO; no hidden input sources.
- [ ] Verify intent extras/Robolectric and corpus; commit.

### Task 6: Integrated dictation
**Create:** `DictationController`, visible recorder/permission Activity, provider abstraction and one real supported transcription path; attribution when adapting Ramblr.
- [ ] RED: permission denied, hidden/editor changed/cancel/audio error cannot insert; transcription stays reviewable; cleanup failure preserves raw text.
- [ ] GREEN: record from existing mic only when configured; original voice route remains when disabled; PCM/temp cleanup; preview/accept/undo; local mode unavailable unless genuinely executable, never fake.
- [ ] Run controller/recorder tests; prove one end-to-end path where provider/hardware access permits; document genuine credential/model blocker; commit.

### Task 7: Full verification, APK and safe delivery
- [ ] Run Java/Kotlin compilation, full debug unit/Robolectric suite, lint and assembleDebug, fixing failures and recording all output.
- [ ] Final independent branch review, fix important findings with RED->GREEN.
- [ ] Verify APK bytes/hash/package/version/apksigner cert/schemes; create idempotent update verifier that refuses mismatches.
- [ ] Compare available visual/performance/device evidence; keep unavailable runtime tests explicitly pending.
- [ ] Install only if all identity prerequisites are satisfied; verify package/data after install. Otherwise deliver development APK + precise signing blocker; prepare review PR without merging or calling it stable.

## Interface/consistency check
Task1 produces shared privacy/session/editor contracts consumed by2-6. Task2 alone owns strip and LatinIME integration; later tasks add hooks through those contracts without modifying geometry. Task3 and4 do not share inference/network paths. Task5 confidentiality policy uses Task1; Task6 never delivers outside Task1 editor session. Task7 is the sole installation owner. No task relaxes the certificate gate or depends on a fake provider to claim runtime acceptance.
