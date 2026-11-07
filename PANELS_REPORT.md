# Translation and handwriting panels

Integration:

- `new helium314.keyboard.latin.ai.panels.TranslationPanel(context, host)`
- `new helium314.keyboard.latin.ai.handwriting.HandwritingPanel(context, host)`
- Both extend native `LinearLayout` and implement `keyboard.gifs.InternalInputPanel`.
- Mount with `LatinIME.showInternalInputPanel(view)`; route text/code/delete through that interface. `ABC` calls `host.closePanel()`.
- `PanelHost.editorToken()` must return a stable equality token covering editor identity, selection and revision. `commitText(acceptedText)` must revalidate the original editor snapshot and return false on changes. `canUseNetwork()` must incorporate sensitive-editor policy and current Gemini consent. Host should call `close()` on hide, editor changes and replacement.

Translation holds source internally, including its temporary letters/symbols/accents buttons. It never accesses InputConnection. ES→EN and EN→ES use existing settings credentials/provider. A 700ms debounce requests Gemini preview; every source edit/target change cancels in-flight work and invalidates old results. Only complete current results can be applied via explicit Use translation. Closing clears source/result and shuts down executor. Normal keyboard styling is untouched.

Handwriting uses ordered, bounded touch/stylus strokes; cancel drops unfinished strokes, Undo removes latest complete stroke. Model availability is checked locally. The explicit Download model button is the only download trigger; progress/failure are inline. Recognition runs offline after download and returns up to five selectable candidates, never automatic insertion. New ink, language changes, editor changes and close invalidate recognition results.

Dependency (primary owns Gradle): `implementation("com.google.mlkit:digital-ink-recognition:18.1.0")`. Primary verified official AAR declares minSdk19; retains project minSdk21. Official current API guide documents 19.0.0 and minSdk23; 18.1.0 is deliberately selected for API21 compatibility. Older SDK native libraries have reported 16KB-page incompatibility: panel checks OS page size and shows unavailable on >4096-byte pages rather than loading unsupported recognizer. This limits handwriting on such devices; translation is independent.

Official API reference and samples: https://developers.google.com/ml-kit/vision/digital-ink-recognition/android and https://github.com/googlesamples/mlkit/tree/master/android/digitalink . Published older-native alignment issue: https://github.com/googlesamples/mlkit/issues/938 . No custom recognition algorithm or cloud ink upload is substituted.

Verification: tests were written before pure state implementations and observed missing-class failure. Standalone javac + JUnit 4.13.2 run: 5 tests pass (source edit invalidation, stale editor/close, incomplete output, Unicode delete, cancelled stroke, immutable ordered stroke snapshots and undo). `TranslationPanelTest` adds Robolectric UI isolation/clear-on-close assertions; central Gradle verification by primary is pending. No Gradle invocation, commit, or shared file modifications performed here.

Limits: recognition accuracy/models require real-device validation; no model is automatically downloaded. Source editing is append/delete with symbols and accents, not cursor editing. Full temporary overlay is native Views; normal ABC is restored on exit. Error text deliberately excludes API key/provider response bodies. No live Gemini request was made by tests.

Polish: temporary panel surfaces, labels, buttons and ink pad now derive colors from `AiMenuTheme.resolve`, following the active palette. Translation reserves weighted scrollable space for the full source/preview/status while keeping its compact 32dp keyboard/actions within a 250dp panel. This avoids cutting off bottom controls and permits reviewing long translations by scrolling. Single-setup automatic-network consent is accepted alongside explicit network consent. Added measured-height/palette assertions and a handwriting ink-clear/idempotent-close Robolectric test; central execution pending.
