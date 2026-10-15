# HeliBoard R2.3 execution ledger

Base: 19c0926cf26b3a328bc388e3f7d7bb87544f329b.
Owner master prompt, certificate annex and Reddit HTTP403 exception authorize this branch.

Implementation:
- SQLite evidence keyed by word pair, locale and device, with in-memory automatic lookup.
- Explicit candidate acceptance and attributable Backspace rejection. Arbitrary edits are never inferred.
- Minimum five acceptances, dominance margin and known-invalid dictionary gate. A rejection vetoes auto.
- AES-256-GCM envelope, random 128-bit salt/96-bit nonce, authenticated version/KDF header.
- PBKDF2-HMAC-SHA256 600000 iterations; recovery phrase wrapped locally by Android Keystore.
- Google Identity AuthorizationClient, Drive appDataFolder only; initial account/OAuth/recovery UI.
- Android JobScheduler persists periodic/network/battery constrained jobs across restarts.
- Android on-device recognizer supplies local dictation; Ramblr only supplies optional audio capture.
- Local results apply automatically through the existing versioned transaction and Backspace dispatch.
- Gemini operation starts the consented request; full-result Apply/Copy/Cancel replaces extra confirmations.
- Material Components 1.14.0-alpha04 Expressive Views, scoped to temporary AI UI (preserves minSdk 21).
- GitHub push workflow, gated APK build and persistent signing; historical identity differences warn.
- Termux downloads/verifies artifacts and installed signer; no clone, Gradle or source checkout.

Rulings:
- Use JobScheduler instead of WorkManager: HeliBoard defaults to device-protected storage and must
  retain Direct Boot keyboard behavior. No global storage migration or hidden storage APIs.
- Use the current app-private storage with unlocked-user and protected-editor gates for learning.
- Use Google Identity 21.4.0: 21.3.0 lacks the revocation API; Android minSdk remains 21.
- A rejected pair remains available for explicit use but cannot automatically replace again.
- Cloud schema 2 adds single-word manual dictionaries and allowlisted compatible built-in visual preferences.
  Phrase shortcuts and arbitrary custom-theme labels/definitions are omitted. Visual restore requires its
  initial explicit toggle; ordinary synchronization preserves the active device appearance.
- Initialization/imports are fenced by deletion generation; per-job tokens own cancellation.
- Android backup/transfer excludes both credential and device-protected learning/identity storage.
- Initial Google Cloud Android OAuth package/certificate registration, Drive API enablement, account
  consent and recovery phrase are external prerequisites. No user phrase or OAuth session was supplied.
- Owner subsequently removed all documentation HTTP availability audits, their category and Gradle mode. Static documentation stays static; live dictionary download gates and local source-path checks remain blocking.

Evidence:
- RED run 38759491119: six failures for missing persistent learning/cipher classes; duplicate-result gate passes.
- Run 38760704065: compilation fails on unsupported credential-context method and missing identity revocation.
- Run 38761443846: installer test catches malformed fallback loop; fixed with shell syntax verification.
- Installed R2.2 versionCode 4106, package com.jorgeprdz.ioskeyboard.debug, Android 17.
- Installed signing SHA256 cfea5c3e180245ec7db199a1d8d799a4a91e0454fef7d02c5313b6d923b66ba2
  matches the configured persistent signer. New APK verification/installation still pending.
- Real speech, theme screenshots, DeX, hardware keyboard, OAuth, restoration on second device,
  and latency percentiles remain NOT VERIFIED until measured.

Next: run installer gates, R2.3 tests, full functional suite, documentation audit and Android lint;
repair concrete failures, publish signed APK, verify on-device compatibility before any update.

Latest verification: run38763269181 passed15/16 R2.3 behaviors (only SQLite reopen failed).
Run38763848048 reproduced an empty reopened snapshot (17/18 behaviors pass) and found17 lint errors:
Java API desugaring, Android version guards/resources, and AppCompat recommendations on existing
platform keyboard views introduced by the AI-only Material library. These are being repaired.
The test harness now closes/reset singleton stores between Robolectric applications instead of carrying
a prior test's app context/SQLite connection into a new application's temporary filesystem.
No functional/security test has been excluded. Documentation HTTP audits have been removed entirely.
