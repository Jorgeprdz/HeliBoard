# Tactic unified experience — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans, inline, without delegation as explicitly requested. Track task evidence here.

**Goal:** Simplify Tactic while preserving its identity and implementing the requested GIF and intelligent dictation flows.
**Architecture:** Preserve the existing IME/InputConnection pipeline and preference keys. Keep panel geometry, settings navigation, provider validation and voice edit policy as separate units. Reuse capture and transaction guards instead of adding another IME or recorder application.
**Tech Stack:** Java/Kotlin, Android Views/Compose, existing Gemini/GIPHY clients, JUnit/Robolectric; ARM64 compilation locally and x86 CI for Robolectric.
**Spec:** ../specs/2026-11-07-tactic-simplificacion-scope.md plus the current user's execution prompt, which authorizes implementation beyond the original document-only status.

## Global constraints

- Main keyboard resources/renderers, package and signature remain intact.
- Preserve data/preference keys; no secrets in documents or logs.
- Automatic, manual and voice network consent remain purpose-specific.
- No delegation, redundant reviews, new theme, private Pixel services or unverified parity claims.
- Work inline in the explicitly supplied dedicated checkout; existing feature branch is not main.

## Review focus

1. A late network result must not replace text after cursor/editor/session changes.
2. Large fonts and short/landscape windows must keep panel actions reachable.
3. Voice content resembling an instruction must not silently become a destructive edit.
4. Removing the Qwen experiment must not remove native gesture or handwriting dependencies.
5. Updating navigation must not lose preferences, personal dictionary, exclusions or keys.

## Task 1 — Baseline and inventory

- [x] Record installed Gboard/Tactic versions and settings reference without changing default IME.
- [x] Write docs/audits/r27/FUNCTION_MATRIX.md and settings-inventory.csv with implementation state, destination, dependencies and unverified gaps.
- [x] Present the first delivery in commentary before product edits.
- [ ] Run source appearance baseline and compile baseline; record actual results.

## Task 2 — Complete GIF geometry

Files: keyboard/gifs/GifPanel.java, keyboard/KeyboardSwitcher.java,
latin/gifs/GifItem.java, latin/gifs/GiphyClient.java; new latin/gifs/GifPanelGeometry.java.
Tests: GifPanelGeometryTest, GifPanelTest, GifNativeMountTest, GifPolicyTest.
Interfaces: browseHeight(normal, available) returns bounded expanded pixels; previewHeight(width, mediaWidth, mediaHeight) preserves dimensions. GifItem stores optional dimensions with backwards-compatible metadata.

- [ ] Add host regression tests for 25% expansion, short-window cap and portrait/landscape ratios; run RED before implementation.
- [ ] Add dimensions to metadata/parser, retain legacy entries, use aspect-fit preview height; place favorite/link below preview.
- [ ] Expand browsing only, restore native keys/search and normal height on ABC; correct compact strip placement after relayout.
- [ ] Keep results/pagination scrollable and attribution above insets; test large-font/short viewport and scroll restore.
- [ ] Run GREEN host tests and compilation; x86 Robolectric regressions verify attachment/query isolation and backwards-compatible history.

## Task 3 — Settings by task and short Gemini setup

Files: settings/screens/MainSettingsScreen.kt, SettingsNavHost.kt, new TacticSettingsScreen.kt,
latin/ai/AiSettingsActivity.java; new latin/ai/AiConnectionStatus.java.
Tests: AiSettingsTest, AutomaticGeminiSettingsTest, new AiConnectionStatusTest.
Interfaces: keep all existing static settings methods and preference keys; activity section extra selects Gemini, voice, privacy or advanced. Same existing screen destinations remain valid.

- [ ] Add host tests for human-readable connection errors and invalid-key rejection without overwriting the saved credential.
- [ ] Organize root into writing, appearance/preferences, tools, Gemini, privacy/data and advanced/about; reuse existing resources and controls.
- [ ] Give Gemini a short main page, safe key entry, automatic model and explicit background consent. Move manual-provider options to Advanced and privacy/voice to their own task entry.
- [ ] Keep prior choices; remove Qwen/style inspector from the visible primary flow, preserve learning data and dictionary.
- [ ] Verify consent separation, encrypted key preservation, routes/search and compile/lint.

## Task 4 — Intelligent dictation and contextual actions

Files: latin/ai/AiKeyboardAssistant.java, provider/GeminiProvider.java, provider/GeminiOperation.java;
new dictation/VoiceEditPolicy.java and dictation/DictationPanel.java; existing capture/session classes.
Tests: VoiceEditPolicyTest, DictationSessionTest, provider/GeminiProviderTest and Android provider tests.
Interfaces: provider response separates dictated content from explicitly requested edits; policy validates source snapshot, bounded result, changed facts and operation before applying through AiEditTransaction.

- [ ] Write RED fixtures for spontaneous speech, self-correction, mixed languages, literal quoted commands, changed names/numbers/negations, stale editor and cancel.
- [ ] Add explicit intelligent/literal mode and purpose-specific voice network opt-in, no silent consent migration.
- [ ] Connect habitual mic to capture → finish → provider → guarded insertion. Keep literal recognizer and actionable network/quota errors.
- [ ] Add explicit voice-edit context/mode for natural commands; never infer sending a message. Require reliable structured output and preserve original on ambiguous edits.
- [ ] Reuse undo transaction and reject stale results; contextual selection actions replace the general IA catalogue.
- [ ] Run host/provider tests and real-device checks only with working authorized connectivity. Preserve remaining limitations explicitly.

## Task 5 — Remove scoped experiments and unify secondary surfaces

Files: AiKeyboardAssistant.java, AiSettingsActivity.java, local/LocalContextReviewer.java,
settings/screens/AdvancedScreen.kt, SettingsNavHost.kt and existing panel classes.

- [ ] Trace Qwen/style/entity/gesture-research callers, JNI, manifest and reflection before removal.
- [ ] Remove scoped UI/integration only after replacement; never erase files or learning records as an incidental migration.
- [ ] Reuse the same PanelViews/AiMenuTheme for dictation/translation/ink/tool actions; preserve existing emoji/clipboard and make their task settings accessible.
- [ ] Exclude research routes/search from release; keep development diagnostics available only in development.
- [ ] Verify remaining optional native runtimes and appearance guard.

## Task 6 — Release evidence and ledger

- [ ] Compile, run full appropriate x86 regression/lint without skips, build final signed APK with new version code.
- [ ] Verify runtime reflection guards, package, signer, version, main keyboard visual comparison and representative editor/data preservation.
- [ ] Exercise complete changed flows on fixture; document unavailable network/provider/device tests honestly.
- [ ] Keep report, APK and outstanding gaps; do not merge or publish. Installation requires the authorization applicable to this implementation phase.

## Execution ledger

- User explicitly supplied inline/low-quota execution; no additional planning approval loop.
- Source checkout is clean at64af9ce, dedicated existing feature branch; no new worktree or dependencies required for inventory.
- Original spec's document-only wording is historical, overridden by the current implementation request.
- Baseline/verification results and subsequent task decisions are appended as work proceeds.

### Evidence so far
- Baseline appearance guard PASS; Kotlin+Java unit-test compilation BUILD SUCCESSFUL1m52s.
- RED geometry: missing policy; GREEN4 host tests. RED connection policy: missing class; GREEN3 host tests.
- RED provider: 33tests,3failures for missing smart voice method; GREEN33 tests including all legacy provider tests.
- Voice semantic policy6host tests PASS; legacy/portrait/untrusted GIF metadata3tests PASS.
- First integrated Kotlin+Java compile PASS; Android lint still running. No new APK installed yet.
- Ruling: preserve existing opt-in local personalization behavior/data; remove its experimental inspector, not useful prediction personalization.
- Ruling: reuse components of Ramblr for an integrated controller; remove the separate recorder menus, preserve attribution.
- Ruling: exclude only libheliboard_writing.so from delivery and remove its call path. Other native libraries remain unchanged; archival model sources/tests remain for now and cannot be called from the IME.

### Integrated build checkpoint
- First full local compile+lintDebug BUILD SUCCESSFUL4m10s.
- Essential host suite PASS51tests via scripts/ci/run-r27-host-tests.py.
- Native palette tests now assert actual keyboard background, not the old independent neutral surface.
- Added consent-separation, invalid-key preservation and controller stale-session Android regressions.
- Release compilation is running with code20261110/name4.1-tactic-r27.1-unified-ai-keyboard. Source frozen for this build.
- Device screen currently Dozing/NotificationShade; the attempted baseline capture cannot be treated as a keyboard screenshot. Preserve earlier approved R262 captures and recapture when unlocked.
