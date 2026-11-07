# Typing and speech implementation report

Scope: independent changes under `latin/ai/local`, `latin/ai/dictation`, dictionary enumeration facades, InputLogicHandler, gesture capability settings gates, and corresponding tests. No keyboard appearance/resources, build files, AiKeyboardAssistant, or KeyboardSwitcher changed by this worker. A follow-up fixes only LatinIME.UIHandler final gesture dispatch.

## Implemented: local personalized next-word evidence

`PersonalTransitionModel` is a pure Java bounded model of individual previous-word/next-word pairs with a count, locale, and last-observed timestamp. It requires three observations before predicting; ranks count then recency; returns at most eight candidates. Max 2048 pairs globally, count capped at one million, words limited to 1–32 Unicode letters/marks and normalized NFC/lowercase. Spanish `es-MX` and English `en-US` are isolated. Phrases, numbers, sensitive label words, and unsupported locales are rejected. No editor buffers, phrases, full messages, audio, or full-chat storage exists.

`PersonalTypingStore` adds:

```java
store.observeTransition(previousWord, committedWord, languageTag);
List<String> personalized = store.predict(previousWord, languageTag, 3);
```

Reads are in memory. Writes run on the existing single SQLite writer. Database moves from schema 2 to 3, additively adding `transitions`; both v1 and v2 migrate without deleting evidence, styles, or vocabulary. Pending pre-erase writes are guarded by the existing generation. Existing `eraseAsync()`/`erase()` clear the model and SQLite table. Transitions deliberately stay local and are absent from the existing correction-only cloud backup schema.

### Integration required in parent-owned IME code

1. Gate both observation and prediction using the existing personal-learning opt-in plus `SensitiveInputPolicy`, incognito/app-exclusion and `IME_FLAG_NO_PERSONALIZED_LEARNING` checks. Store APIs cannot infer the active editor themselves.
2. At an actual word commit/boundary, obtain exactly the immediately preceding word and committed word (do not repeatedly scan/relearn the entire sentence). Observe once per real commit. Do not train provisional speech, correction previews, GIF searches, translation source or handwriting previews.
3. Reset any temporary previous-word tracking across editor/session/cursor/composition changes and sentence breaks. If extracting from InputConnection, validate current session and use a bounded snapshot, and reject sensitive context before splitting.
4. At native *next-word prediction* time, promote matching learned candidates and append learned candidates missing from native results. Preserve existing geometry and native suggestion-strip/candidate actions. Do not run this in the middle of a composing word, on gesture updates, or on other panel content.
5. Use actual active-language tags. Map the supported keyboard language to `es-MX`/`en-US` deliberately; do not silently pool bilingual evidence under an arbitrary locale.

Example, after existing policy gates:

```java
// In word-commit flow, once per committed adjacent pair:
PersonalTypingStore.get(ime).observeTransition(previous, committed, language);
// In next-word suggestion flow, using original native candidate objects where present:
List<String> learned = PersonalTypingStore.get(ime).predict(previous, language, 3);
// Merge/reorder native SuggestedWords preserving native flags/editor transaction semantics.
```

## Implemented: live dictation transport and safe preview lifecycle

`LocalSpeechRecognizer` requests `RecognizerIntent.EXTRA_PARTIAL_RESULTS=true` and delivers bounded provisional results through the backward-compatible default method `Callback.partial(String)`. Closed recognition sessions ignore later partials. Recognition remains strictly Android on-device recognition (API31+ with provider installed), never falls back to a cloud recognition service.

`DictationSession.partial(ticket, currentSnapshot, text)` validates recording ownership, exact editor snapshot and maximum 16384 UTF-16 units. It replaces the keyboard preview; it never writes to InputConnection, changes to REVIEW, or appends to final text. Partials work while RECORDING or TRANSCRIBING. Cancellation clears text and invalidates the lease; final result still requires TRANSCRIBING and matching editor snapshot.

### Integration required in parent-owned AiKeyboardAssistant

```java
public void partial(String text) {
    // Reuse the same ticket/snapshot captured for this recognition session.
    // Obtain current snapshot and apply the same protected-field policy as final callback.
    if (dictation.partial(ticket, currentSnapshot(), text)) {
        // Update a TextView in existing keyboard-owned panel, replacing old preview.
        preview.setText(dictation.text());
    }
}
```

Keep partials outside the editor. On final: stop recording state if needed, validate final via `dictation.result(...)`, then apply through existing exact-snapshot/native transaction once. Clear provisional UI; do not append preview to final or insert both. Changing editor/cursor or closing/cancelling dictation must invalidate the session and close the recognizer. Existing microphone start can directly use this recognizer after availability/permission check, with setup only if unavailable. Providers may legitimately produce no partials; the flag is a request, not a guarantee.

## Implemented: original local Java glide fallback

Native imported gesture engines remain preferred. When `JniUtils.sHaveGestureLib` is false, the new original GPL Java engine uses the installed active language's public main dictionaries. It neither downloads nor bundles third-party gesture binaries.

`ReadOnlyBinaryDictionary.getGestureWords` traverses real dictionary entries through existing native `getNextWordProperty`, keeping the highest-frequency 15,000 valid, non-offensive 2–32 letter words. Enumeration visits at most 250,000 entries or ten seconds per dictionary source, and excludes emoji. Locks cover individual native reads, not the full enumeration, so dictionary close/language switches can proceed; a queued writer interrupts enumeration. `DictionaryCollection` merges/caps entries. `DictionaryFacilitator` exposes public-main-only snapshot/identity APIs; contacts, application names, user history and editor text are never enumerated. Dictionary reload schedules asynchronous preparation; cache identity changes with active dictionary/locale.

`LocalGestureDecoder` maps words to actual `Keyboard.getSortedKeys()` centers, preserves direct layout keys, falls back to base letters for unsupported accents, collapses repeated identical centers, resamples paths to 24 equal-distance samples, filters by endpoints/length, shortlists 48 templates, and ranks using banded dynamic time warping with a small dictionary-frequency preference. It returns at most eight alternatives and abstains on invalid/distant/weak ambiguous traces. It reads a real dictionary snapshot capped at 15k; there is no fixed miniature vocabulary. Raw trace input is capped at 8192 coordinates and downsamples at most 512 source points.

`LocalGestureEngine` prepares dictionary snapshots on a separate executor and builds layout templates/decodes only on InputLogicHandler's worker. `isSupported()` means the real original Java implementation is present; `isReady(facilitator)` separately checks that a nonempty snapshot for the exact current dictionary is prepared. Initial gestures may wait on preparation on a worker; typing/UI do not execute enumeration or decoding.

`InputLogicHandler` branches to this engine only without imported native support. It copies input arrays, coalesces queued updates, preserves caps mode, returns regular batch SuggestedWords through the existing native preview/tail transaction, and checks gesture generation, active dictionary/facilitator/keyboard identities, expected cursor and composing word before delivery. Cancel/editor-reset invalidates the generation. Final tail delivery also carries the full lease predicate through the final UIHandler message; it rechecks generation/dictionary/keyboard/cursor/composition immediately before InputLogic commit, closing the enqueue-to-consume cancellation race. Empty fallback results never reuse a prior unrelated typing suggestion. Existing imported-native processing is retained.

Capability gates now expose the existing gesture screen/preferences and honor the user's existing gesture switch for the original Java engine; native main-dictionary availability and protected-field/accessibility gates remain active. Keyboard appearance, key positions, resources and trail/preview styling are unchanged.

### Glide limitations and external library provenance

The original decoder is a first implementation and requires real-device accuracy/latency checks. It does not implement multiword space-aware decoding or distinguish geometrically identical repeated-letter paths beyond dictionary frequency. Extremely large dictionaries may produce a bounded partial snapshot when enumeration hits a cap; only the active preferred dictionary language is decoded. Words containing punctuation, symbols, unknown layout letters and fewer than two distinct key centers abstain. Native imported libraries remain the stronger existing optional engine.

The [upstream HeliBoard README](https://github.com/HeliBorg/HeliBoard/blob/main/README.md), checked 2026-11-07, describes the existing compatible native engine as closed source. Public hosting and checksums do not establish redistribution permission. No third-party binary was downloaded or bundled. The existing user-selected native `.so` import remains available; automatic third-party library preparation still requires verified provenance and explicit permission. The original fallback avoids that prerequisite.

## Verification

- RED: 4 new transition model tests failed because class missing; implemented model, all passed.
- RED: 2 new session partial tests failed because method missing; implemented partial lifecycle, all passed.
- RED: new glide tests first failed to compile because LocalGestureDecoder was absent; implemented original decoder, five tests passed.
- Host JUnit: **17 tests pass** (`PersonalTransitionModelTest` 4, `DictationSessionTest` 8, `LocalGestureDecoderTest` 5).
- Host javac compiled production PersonalTypingStore/PersonalTransitionModel/LocalSpeechRecognizer against Android35 API, plus temporary no-op TypingSync compile dependency only. API fixture first failed missing observeTransition/predict/Callback.partial, then compiled after implementation. Temporary files are under `/tmp/typing-speech-host`; no stubs added to repository.
- New Robolectric `PersonalTransitionStoreTest` covers actual restart persistence, local-only backup behavior, full deletion and v2 migration; extended `LocalSpeechRecognizerTest` covers intent partial flag and real partial/final callback lifecycle. Coordinator Android run compiled production/tests, but these Android tests failed in `DefaultNativeRuntimeLoader.java:191`, alongside widespread platform failures (586 tests, 307 failures). This does not establish passing Android assertions.
- Follow-up `GuardedGestureDeliveryTest` queues a final gesture while current, invalidates its lease before paused-main-looper consumption, and verifies no InputLogic commit; a valid lease reaches the existing commit exactly once. API fixture RED confirmed the old unguarded-only dispatch API; coordinator regression execution pending.
- Primary should run `PersonalTransitionStoreTest`, `PersonalTransitionModelTest`, `LocalGestureDecoderTest`, `GuardedGestureDeliveryTest`, `DictationSessionTest`, `LocalSpeechRecognizerTest`, and existing `R24LearningTest`, then full normal suite.
- Synthetic host workload (15,000 generated dictionary-shaped words, not an accuracy corpus): template preparation 277.3 ms, 100 warmed decode runs mean 3.49 ms. Physical device latency and native dictionary enumeration speed remain unmeasured.
- No Gradle run started by this worker to avoid racing primary build. `git diff --check` passed.
- No actual S25 voice provider, microphone partial UI, prediction quality, glide or final editor insertion was exercised by this worker. Parent integration and physical validation remain necessary.
