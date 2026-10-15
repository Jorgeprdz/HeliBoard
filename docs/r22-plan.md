# R2.2 remote execution plan
Binding specification: HELIBOARD_AI_R2_2_CODEX_EJECUCION_GITHUB_ACTIONS_INSTALACION_AUTOMATICA.md and remote annex supplied 2026-10-15.
Base: 1034e6c9e19521d5ef09943aaad847456f688020. Remote GitHub API edits; Actions alone runs Gradle.

1. RED tests of real LatinIME -> InputLogic -> fake InputConnection: separators, keyboard Send, UTF-16, one-shot undo, invalidation, protected input; independent spelling policy.
2. Add a bounded synchronous boundary review after native separator commit and before keyboard action; no dictionary/network blocking; snapshot and exact content revalidation; undo before native Backspace with native state cleared only on successful contextual edit.
3. Isolate Gemini menu/review surface, readable light/dark colors and interaction states; preserve permanent strip and its restoration. Test themes.
4. Gate focal tests, relevant regression and lint before assembly. Verify persistent certificate, package/version, checksum and commit/run manifest.
5. Termux watcher pinned to repository/workflow/branch/SHA; fail closed, one authorized Galaxy S25, checksum/package/certificate/version, adb install -r, postcheck and cleanup. Independent clone cleanup script with preview and explicit confirmation; preserve unpublished data and backups.
6. Publish branch, dispatch explicitly, inspect failures within scope, verify artifact. Device acceptance and latency only if measured; external app Send cannot generally be intercepted.

No automatic Gemini calls; retain opt-in/privacy policies. No uninstall, data deletion, downgrade, changing package or generating keys. Preserve all permanent iOS Glass UI.
Status: RED tests committed before production changes. Local baseline did not execute tests (host AAPT2 architecture); local clone removed under annex. No APK or device update performed.

RED evidence: run 38737582352 executed 152 tests; seven expected failures (four real IME assertions, spelling automatic policy, two missing Gemini theme behaviors). Stable editor metadata and Send snapshot now modeled in the test InputConnection. Production changes follow these assertions.
Device signing audit: installed D67CB026A39E6E7D3A332551F35E34B27C8EB94DAC309020F3401767CC31F3A0 differs from securely available persistent CFEA5C3E180245EC7DB199A1D8D799A4A91E0454FEF7D02C5313B6D923B66BA2. In-place installation BLOCKED unless a matching original identity is recovered. Never uninstall or clear data.

Review rulings:
- Boundary computation is capped at eight milliseconds, but Android InputConnection IPC itself has platform-controlled latency. The slow-connection gate now precedes snapshot reads; no claim of an eight-millisecond end-to-end latency. Phone p50/p95 NOT VERIFIED.
- A prefix beyond 512 UTF-16 units is deliberately ineligible for automatic contextual replacement because quote/token state at a truncated start is unknown. This trades long-message coverage for fail-closed protection; regression pins the limit.
- Device rendering, DeX, latency and original installed-key recovery remain device acceptance/signing gates, not inferred from Linux CI.
Review fixes: use fresh editor reads for apply/undo context, invalidate on composition span changes, handle multiline Enter, audit repository metadata before cleanup. Build sentinel represented as (char)1 to avoid API terminal control-character rendering.

2026-10-15 regression diagnosis (run 38741744430, SHA 367b8b4): full debug regression ran 352 tests with 20 failures. The ordinary editor fixture had a null package and correctly triggered the fail-closed learning policy; resource subtype fixtures still requested removed generic Spanish/Korean resources, and the German parser fixture lacked its extra-key input in this intentionally bilingual build. Candidate corrections supply test-only metadata without restoring production layouts or relaxing assertions. The pre-existing trailing-emoji-modifier regression is enabled for all variants and addressed in the emoji suffix parser. Runtime validation is pending the next Actions run; no PASS is claimed.
External availability failures remain real blockers: Reddit returned HTTP 403 and old Codeberg dictionary catalog URLs returned HTTP 404. No external-link test is skipped, no status code is accepted as success, and the unfiltered debug regression/lint/build/signing gates remain unchanged. A verification run may remain red until these separate blockers are resolved.
Owner authorization update: subsequent explicit instructions authorize uninstall/reinstall when certificates differ. This supersedes the earlier no-uninstall instruction, but does not establish a usable backup. No uninstall, data clear, or install has occurred. Before any destructive reinstall, verify the new APK and current data/APK backup; explain that Android Keystore-bound BYOK credentials may require re-entry. The current installer still refuses a mismatched signature; automatic destructive reinstallation is NOT implemented or verified.
