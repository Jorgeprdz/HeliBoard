# Automatic correction review

Scope: automatic Gemini correction, setup/model selection, panel host identity, and personal prediction hooks in the working tree. Reviewed `/tmp/heliboard-automatic-review.diff` first. No source edits or Gradle runs. Ignore unrelated in-progress glide work.

## Important findings

1. **P1 — Valid function words can be silently rewritten with a different meaning.** `app/src/main/java/helium314/keyboard/latin/ai/AutomaticCorrectionPolicy.java:25-29` promotes the first `SemanticEditGuard` proposal to `automaticAllowed=true`. That guard was designed for explicit acceptance and deliberately permits substitutions between valid function words. Reproduced against the existing host classes using a lexicon recognizing only the seven words in the examples (also with key distance returning infinity, matching an unwarmed observed lexicon): `vengo a casa ` + generated `vengo de casa ` returns `a → de`; `quiero la casa ` + generated `quiero una casa ` returns `la → una`. Both originals are grammatical and the replacements change intent. The current valid-word-paraphrase test only exercises content nouns, so it misses this route. Keep valid function-word changes behind a specifically licensed contextual rule instead of blindly promoting every explicit proposal.

2. **P2 — Sentence-ending pauses stop automatic review in fields beyond the 512-character window.** `app/src/main/java/helium314/keyboard/latin/ai/AiKeyboardAssistant.java:55-59` selects the *last* period/question/exclamation/newline followed by whitespace. Once `base.start > raw.length`, text ending `... quiero escrbir bien. ` sets the boundary to the final period, retains only ` `, then exits for length <3. Consequently typing a long paragraph continuously and pausing after its final period-space never sends its latest sentence to Gemini; the same sentence is eligible in a short field. Reproduced the exact scan in JShell; its output is a single space. Select a complete starting boundary which retains the sentence just completed (and avoid treating the terminal separator as its beginning).

## Verified paths and limitations

- The normal key path cancels/resets the prior automatic job before processing the key. `InputLogic.onCodeInput` invokes the word-boundary hook after `endBatchEdit`, so the new debounce sees native correction results.
- Automatic upload requires `automatic_gemini`; previous manual `network_consent` alone does not authorize it. Private editor/lock/incognito/excluded-app gates run before capture/send and delivery.
- The coordinator invalidates already-queued deliveries on cancel; the callback also checks editor snapshot, composition and exact transmitted prefix before applying.
- Panel host captures content/editor/cursor identity independently of assistant generation, so the constructor token survives `showInternalInputPanel` calling `onNewEditor`. The current token checks only the preceding 512 characters plus selection; no integration evidence establishes invalidation for mutations exclusively after the cursor or outside that preceding window.
- Personal predictions merge as prediction-kind entries and preserve the existing auto-correct flags. Transition observation requires style consent and passes sensitivity gating.

## Required regression coverage

- Add the two valid-function-word examples above to policy tests and cover retained completed sentences at cursor positions 512/513 and beyond, with final period-space, newline and question-space.
- The reported eight host tests exercise policy/gate/model helpers, not the IME lifecycle. Add an editor/transport integration check for: post-native-boundary dispatch; cancellation before a subsequent key; discard after editor switch/selection change; private fields and manual-only consent issuing zero automatic requests; automatic span replacement followed immediately by backspace restoring the exact original.
- Exercise panel constructor → internal-panel reset → first translation/ink result/commit, and mutate editor text at the same cursor to validate the required invalidation contract.
- Exercise first setup with blank model, discovered model check, failed generation and clear-key/cancel during discovery. Full Gradle/build validation remains with the coordinating agent.
