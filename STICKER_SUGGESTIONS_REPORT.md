# Automatic text-sticker suggestion integration

Production source stable for coordinator release build. No Gradle build launched by this worker.

## Behavior

After an ordinary native suggestion update with a composing word, the controller waits 250 ms, renders the source locally off the UI thread, writes a temporary PNG via the existing sticker store, and decodes a small thumbnail on its worker. Only a current exact target can publish or act.

The existing physical rightmost native TextView displays an ImageSpan; its layout parameters, colors, width, weight, divider and other suggestion views stay unchanged. While the sticker occupies the slot it is explicitly enabled, even if the native helper disabled an empty slot. The original enabled state, text, accessibility description and native click/long-click listeners are restored when cleared. No panel or popup is introduced. If native layout consolidates to one full-width suggestion, there is no independent rightmost slot to reuse, so the sticker is suppressed. AI choices, dictation choices, external suggestions, internal panels, expanded toolbar and more-suggestions popup suppress it.

Tap validates current target and advertised PNG MIME support, then uses existing `.gifcontent` FileProvider and InputConnectionCompat.commitContent with read permission. Receiver rejection is reported in a Toast; there is no automatic alternate transport. Typed text is kept intact. Long press explicitly saves the current PNG on the worker, using the gif agent's private bounded collection, with a Toast and no popup. No network, clipboard reads, editor replacements or text deletion occur.

## Privacy / ownership

Automatic suggestions require normal unprotected text, suggestions enabled, no incognito, no excluded app, no `IME_FLAG_NO_PERSONALIZED_LEARNING`, no AI/internal panel and no keyguard. Source is the currently composing last word; at most 120 code points. Sensitive surrounding context/source is rejected. In-memory target uses at most 256 preceding UTF-16 units and 128 following units, along with selection coordinates.

Target identity copies package, field ID, inputType and imeOptions, plus InputConnection identity and controller generation. Equivalent recreated EditorInfo objects remain valid; changed logical fields are rejected. Exact before/after text, source and selection must still match when publishing or tapping. Editor/view lifecycle, explicit cursor moves, AI actions, panel opening and configuration changes invalidate work; expected native typing selection notifications do not unnecessarily cancel a fresh debounce.

## Owned files / API

- `latin/gifs/TextStickerSuggestionController.java`: IME lifecycle/debounce/worker/PNG sharing integration. Constructor `(LatinIME)`; `refresh(SuggestionStripView)`, `invalidate()`, `close()`.
- `latin/gifs/TextStickerSuggestionPolicy.java`: pure privacy/source/target policy.
- `suggestions/SuggestionStripView.kt`: `canShowTextStickerSuggestion()`, `showTextStickerSuggestion(Bitmap,Runnable send,Runnable save)`, `clearTextStickerSuggestion()`; physical slot reuse and restoration.
- `LatinIME.java`: lazily creates controller only when a word is composing in an eligible normal strip, refreshes after native suggestions, invalidates lifecycle/AI/panel/cursor changes, closes on destroy.
- `TextStickerSuggestionPolicyTest.java`: four focused host tests.

Renderer/store owned separately by gif agent; integration uses agreed APIs under `latin.gifs`: `TextStickerRenderer.render(String)` and `new TextStickerStore(cacheDir,filesDir).preview/save`.

## Verification

- RED: focused policy tests failed to compile before policy existed.
- GREEN: four host JUnit tests pass: protected/suggestions policy, exact ownership/context mismatch, equivalent/changed logical editor fields, bounded source including valid supplementary emoji (120 vs 121 code points) and sensitive context rejection.
- `git diff --check` passed.
- Coordinator release build, Android strip geometry/image rendering, PNG receiver acceptance, long-press save and stale callback/action physical checks remain pending. No claim of on-device behavior was made by this worker.

Follow-up regression `TextStickerSuggestionSlotTest`: creates a real native two-prediction strip with an empty disabled right slot, verifies sticker makes it enabled and both tap/save callbacks work, then verifies clearing restores disabled/empty native state and the identical layout parameters/child count. Android execution delegated to coordinator; source test added before fix, not claimed run here.
