# Final whole-change review

Reviewed the working-tree change against `9451fa9a7ab7994e32c5cbb1b13b8914d741a290`, `/tmp/heliboard-whole-review.diff`, the approved design in `docs/superpowers/specs/2026-11-07-gifs-y-correccion-design.md`, and the relevant existing editor, dictionary, and asynchronous-delivery implementations. This is a source review, not a claim of physical-provider verification. No source edits, builds, or test reruns were performed. The coordinator owns the ongoing Android tests/lint and installation evidence.

## Verdict

**Spec: substantially implemented, with three Important issues below. Delivery acceptance remains pending the coordinator's build/device checks and real-provider checks where credentials permit.** The missing GIPHY key is an external prerequisite, not an implementation defect.

**Quality: changes requested before approval.** No Critical issue identified. The three findings are concrete control-flow defects rather than hypothetical hardening suggestions.

## Important findings

### 1. P1 — Java glide loses its stale-result protection before the actual editor write

Location: `app/src/main/java/helium314/keyboard/latin/inputlogic/InputLogicHandler.java:126-138`, particularly lines 137-138. Follow-through: `LatinIME.java:437-438` and `LatinIME.java:301-306`, then `InputLogic.java:2399-2414`.

The new Java fallback checks generation, cursor, composition, dictionary, and keyboard inside its posted main-thread runnable. It then calls `showTailBatchInputResult`, which **posts another message**, carrying only `SuggestedWords`. The eventual `MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED` handler does not recheck any identity and calls `setComposingTextInternal` on the then-current editor. `InputLogicHandler.reset()` invalidates the first runnable and removes worker callbacks, but does not invalidate this final tail message.

Deterministic reproduction sequence: finish a Java-fallback gesture; queue the checked delivery runnable followed by an editor switch (or cancellation/cursor change); execute the delivery runnable. It appends the tail message behind that already-queued switch. Execute the switch, then the tail message: the old gesture word is written into the new target/current cursor. This is a source-traced interleaving; it was not executed as an additional test during this review. Opening an internal panel during this final delivery gap also leaves an unguarded editor-writing message, contrary to the panel's isolated-input contract.

Keep validation and final write in the same main-thread operation, or carry and check the captured generation/editor/cursor token in the ultimate message handler. Add a queue-order regression that switches editors after the first delivery runnable but before the final tail message. A test that switches before the first runnable would miss this defect.

### 2. P2 — The confusion fallback overrides explicitly protected words

Location: `app/src/main/java/helium314/keyboard/latin/ai/AutomaticCorrectionPolicy.java:47-59`, particularly lines 56-59.

`SemanticEditGuard.proposals` honors `lexicon.preserved` through its protected-term comparison. However, after that guard returns no proposals, the new direct `CONFUSIONS` fallback accepts its own edit without checking preserved terms. `personalLexicon().preserved` is backed by the user's explicit **Conservar palabra** entries.

Reproduction: protect `haber`; provide source `voy haber si funciona ` and model output `voy a ver si funciona `, with no rejected correction. The semantic guard rejects removal of the protected word. The fallback extracts `haber` / `a ver`, recognizes the enumerated pair, and returns an automatically allowed correction anyway. Protecting `tu` and receiving `tú` likewise bypasses the explicit protection. This follows directly from the fallback conditions; no extra test was run.

Apply the same preserved-word invariants to every acceptance path, including the fallback. Add protected-confusion tests so rejecting the semantic proposal cannot be undone by a later permissive path.

### 3. P2 — The valid-word restriction disables punctuation-only correction

Location: `app/src/main/java/helium314/keyboard/latin/ai/AutomaticCorrectionPolicy.java:40-44`.

The restriction added for unsafe valid-function-word substitutions compares the entire unaccented original/replacement strings, including punctuation. As a result, a punctuation-only proposal around correctly spelled words is always rejected: no original word is known invalid, the strings differ, and the change is not a lexical confusion. The later fallback also only accepts enumerated lexical confusions.

Reproduction with a lexicon validating `hola` and `amigo`: source `hola amigo `, output `hola, amigo `. `SemanticEditGuard` creates its real-left-word-anchored punctuation insertion `hola` → `hola,`; line 44 discards it. Thus the requested automatic punctuation capability is unavailable even for this minimal lexical-content-preserving edit. The same applies to a terminal punctuation insertion anchored to a valid word. Source traced, without a test rerun.

Distinguish unchanged lexical content with a permitted punctuation edit from a lexical substitution, while preserving negation/name/number and rejection checks. Keep the valid-function-word regression and add punctuation-only coverage.

## Reviewed paths that do not currently warrant blockers

- Temporary panels are mounted in the existing `InputView`; letters/resources are not regenerated. The toolbar addition is the authorized GIF entry. Panel-owned software and hardware text routing occurs before host-editor input logic. Ordinary GIF search and sending do not open another window.
- Translation/ink `PanelHost` identity is independent of assistant generation, so constructing the panel before `showInternalInputPanel` resets the assistant does not invalidate its initial token. Editor/cursor/prefix/selection checks and panel disposal guard their asynchronous results. Final text uses the existing edit transaction and input-cache integration.
- GIF search/download generations, connection/editor identity, query invalidation, explicit unsupported-editor link copying, MIME checks, HTTPS/media-host restrictions, size/dimension limits, bounded workers, and close cancellation are present. Rejected content is not reported as delivered. Native `commitContent` and direct read grants are wired. This does not substitute for a real compatible-editor send test.
- The Java gesture settings capability gate corresponds to an actual decoder, not a fake enabled flag. Native availability still wins. Vocabulary comes from public main dictionaries; enumeration acquires/releases the existing read/close lock per word and bounds retained entries/work. The remaining blocker is final delivery, not dictionary provenance.
- Transition persistence is bounded, requires three observations, is separated by language, and is excluded from the correction-only cloud payload. Full learned-data erase clears transitions in memory and SQLite; schema migration preserves prior evidence. Consent and sensitive-editor gates are present in observation/prediction hooks.
- Dictation partials are displayed in the keyboard and do not commit provisional text; the final result uses its session/editor transaction.
- Automatic Gemini requires separate opt-in, uses the stated pause/rate gates, cancels on typing/session changes, checks the submitted prefix before application, handles provider errors without retry loops, and uses the existing immediate-backspace undo path. The prior valid-function-word rewrite and terminal-space context-selection findings are addressed in this snapshot; the new issues above remain.
- ML Kit is explicitly optional, downloaded through the panel action, and guarded against unsupported larger page sizes. The target S25's reported 4 KB page size is compatible with that guard. The explicit local native-packaging override preserves source compilation as the default; independent APK/signature verification remains the coordinator's task.

## Verification boundary

Reported host assertions (automatic 10, GIF 13, typing/speech/glide 17) are acknowledged, not rerun or represented as proof of Android lifecycle correctness. The supplied new policy tests do not cover findings 2/3, and decoder tests do not cover the final-delivery queue race. Live GIPHY search/send requires the user's valid key; real Gemini behavior requires configured credentials/model. Physical appearance comparison, signed update installation, full Gradle results, and provider evidence must be reported separately by the coordinator.
