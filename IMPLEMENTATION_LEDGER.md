# Implementation ledger

Plan: docs/superpowers/plans/2026-11-07-gifs-y-gemini.md
Base: 9451fa9a7ab7994e32c5cbb1b13b8914d741a290
User approved entire scope and installation; appearance fixed.
Ruling: use separate feature clone and branch, no worktree needed; existing checkout was created for this request.
Ruling: parallel independent domains with explicit file ownership; primary integrates shared hooks and runs builds to avoid Gradle conflicts.
No product code changed yet.

Implementation: GIF custom internal panel and direct toolbar key; GIPHY secure setup, media preview/send/history; translation + optional MLKit ink panels; personalized word transitions and partial speech; original Java dictionary/geometry glide fallback; automatic Gemini consent/debounce/minimal edits/undo.
Verification: 10 automatic policy/gate/model-choice host tests PASS; 17 transition/dictation/glide host tests PASS; 13 GIF policy assertions PASS. Central Android suite/lint pending.
Review fixed: explicit-choice function-word rewrites must not auto-apply; added intent-change regression and restricted valid-word edits. Review fixed: truncated context ending punctuation-space selects usable complete clause; regression added.
Build ruling: ARM host uses existing AAPT2 x86 wrapper under qemu. For APK packaging reuse native libraries extracted from installed owner-signed v4.1-tactic-r252c.1 only after git diff confirmed unchanged app/src/main/jni and native-writing sources; default/CI builds still compile native sources. Corresponding source remains in repository; verifiedNative path is explicit environment-only input.
Device: S25 connected via adb; installed release versionCode20261017, matching owner certificate cfea5c3e180245ec7db199a1d8d799a4a91e0454fef7d02c5313b6d923b66ba2. Created isolated offline fixture app for appearance/input checks; before screenshot saved outside repo.

Full local Android run: 586 tests, 307 failures: 304 native Robolectric runtime unsupported Linux/aarch64, 2 native Conscrypt unavailable on ARM, 1 enum-operation expectation updated from9 to10 with automatic instruction assertions. No tests excluded from final validation: added x86 GitHub workflow for complete tests+lint+signed release artifact, without public release publishing.
Final review fixed: original Java glide now guards the final tail message at consumption before commit; paused-looper regression added. Final review fixed: protected vocabulary veto applies before confusion fallback. Final review fixed: punctuation edits derive exact spans from model string (prevent incorrect space/comma placement). Automatic host suite now12/12 PASS.

CI403749d: 592 tests,591PASS. New panel identity regression and live dictionary gate PASS. Existing R24 dictionary-readiness case failed intermittently under its wall-clock 8ms budget (passed previous run). Kept production8ms System.nanoTime budget unchanged; inject monotonic time supplier for deterministic readiness test, with separate regression that exhausted budget preserves text. No test exclusions, retries-until-green or increased UI blocking budget.
