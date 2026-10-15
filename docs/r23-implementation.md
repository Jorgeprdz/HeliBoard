# HeliBoard R2.3 execution record

Base: 19c0926cf26b3a328bc388e3f7d7bb87544f329b.
Scope authorized by the owner's master prompt and certificate annex.

Design: preserve the keyboard rendering and native undo; add a bounded, locale-aware
SQLite learning store behind an in-memory cache and background writer. Learn only explicit
candidate acceptance and attributable rejection. Use learned alternatives at word boundaries
after native correction, with conservative invalid-word and margin gates. No keystroke log.
Local dictation uses Android's on-device SpeechRecognizer; Ramblr remains optional audio capture.
Final local recognition applies a versioned transaction automatically, with one native-dispatch undo.
Gemini operations authorize their requests, and a single full result dialog contains Apply/Copy/Cancel.
Temporary AI surfaces resolve keyboard brightness independently of system night mode.
Cloud backups contain only an allowlisted schema, encrypted AES-256-GCM before Drive appDataFolder.
PBKDF2-HMAC-SHA256 (600,000 iterations, random salt) supports recovery across devices.
WorkManager batches sync; per-device monotonic components merge by maximum, never sum duplicates.
Google OAuth configuration/consent and recovery unlock are initial external prerequisites.

Execution: inline, GitHub API commits only; no source checkout or Gradle on the phone.
1. Publish failing behavioral tests and automatic cloud workflow; inspect RED.
2. Implement bounded personal learning and persistent schema; integrate candidate/boundary/undo.
3. Implement local one-tap dictation and unified Gemini review; verify lifecycle and themes.
4. Implement encrypted backup, OAuth setup, background retries and conflict merge.
5. Harden CI manifests and Termux update agent; preserve signing secrets and installed package.
6. Run complete tests/lint, repair failures, verify APK, compare installed signature, update only
   if compatible. Record all physical-device limitations as NOT VERIFIED.

Review focus: stale editor/cursor callbacks; accepted-word false positives; replayed cloud counters;
corrupt/foreign backup; duplicate speech results. Test these before publishing an installable APK.

Progress: branch created; installed versionCode 4106 on Android 17 identified.
Known baseline bug: certificate parser ignores "V2 Signer"; prior report has empty identity.
Known baseline failure: documentation readmeLinks; it must be investigated, not hidden.
Initial OAuth and physical speech/DeX acceptance have not been performed.
