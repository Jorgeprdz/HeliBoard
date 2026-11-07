# GIF / media panel implementation

Implemented in the shared `HeliBoard-gifs-gemini` checkout. No commits made by this agent.

## Delivered behavior

- A labelled **GIF** control in the existing toolbar calls `LatinIME.onAiAction("GIF")`; the primary agent owns that action dispatcher. It works independently of the optional AI switch.
- `LatinIME.showGifPanel()` mounts `GifPanel` within the existing `InputView`. Ordinary search, preview, favorites, recents, link copying, and sending use no Activity/dialog/browser/WebView.
- The panel owns its query, Spanish letter/symbol keys and emoji buttons. It never reads or commits the application draft. Query field tapping / keyboard toggle shows or hides its keys without losing text. Explicit Search avoids an API call per keystroke.
- GIPHY GIF/sticker trends and search, exact UTF-8 query with `lang=es`, 12-result pagination, metadata-only favorites (50) and recents (30), history pagination. Emoji combinations are search terms for GIPHY stickers, with no claim of proprietary Emoji Kitchen access. Incognito suppresses recent history writes; favorites are explicit user actions.
- Animated previews use the small GIPHY GIF rendition with API21-compatible Movie rendering; frames stop and media references clear on query changes or closing.
- Tap a result to download a bounded GIF and call AndroidX Commit Content after checking supported editor MIME types. Shared file uses a dedicated non-exported FileProvider, grants read permission to the current editor package and AndroidX read-grant flags. Rejection/download failures are inline; success text says only that the app accepted content. An explicit **Enlace** action copies the provider link, including when GIF MIME is unsupported.
- The panel header also routes **Traducir en vivo** and **Escritura a mano** to the primary agent's existing dispatcher.

## Lifecycle / trust boundaries

- Reusable `InternalInputPanel` defines `onTextInput(String)`, `onCodeInput(int)`, `close()`.
- LatinIME routes software/hardware input before host input logic and consumes hardware key-up/back correctly. Opening invalidates AI editor work. Editor starts/finishes, IME hiding, destruction and external cursor movement close the panel. Batch input callbacks are blocked while the panel owns input.
- KeyboardSwitcher mounts an overlay, snapshots/restores the old main-frame visibility, cancels key popups and exposes the panel as the visible/wrapper view for correct IME touch insets. Temporary minimum panel height is 360dp. Existing letter keyboard layout, resources, icons, transparency, blur and per-app color machinery are untouched.
- A generation token plus exact current InputConnection and EditorInfo identity rejects old query/editor/closed-panel results before rendering or sharing.
- Network is HTTPS only: API host `api.giphy.com`; media allowlist `media.giphy.com`, numbered `mediaN.giphy.com`, `i.giphy.com`; no userinfo, arbitrary hosts, nonstandard ports or redirects. API bytes <=1MiB, preview <=512KiB, share <=5MiB; GIF header and dimensions (<=640 each) checked. Two workers, bounded queue; cancellation disconnects sockets and interrupts workers. Connect/read timeouts 8s; streaming deadline 15s. Explicit searches throttle to >=1.2s. HTTP quota/auth failures have inline states and no retry loop.
- Shared cache retains at most nine recently sent files after each send, pruning older files (eight previous + new), and files older than 24h on the next send; cache is transient, while persistent recent/favorite entries contain only metadata. No permanent downloaded collection.

## Configuration / external requirements

- **⚙** explicitly opens a separate, non-exported initial GIPHY setup Activity; ordinary use never opens it automatically. It explains query disclosure, user-owned API key, beta quota (100 API calls/hour), possible production approval/pricing and Android6 encryption requirement.
- Key encrypted using AES-GCM with an AndroidKeyStore key, ciphertext in `Context.getNoBackupFilesDir()`, API24+ unlocked-user gate. Because this app defaults to device-protected storage, ciphertext may reside there; it is still encrypted and excluded from backup. No plaintext fallback. API21/22 show honest unavailable setup, with app minSdk21 unchanged. Deleting the key disables provider search/preview/download. No existing user credential was inspected, copied or logged.
- GIPHY key/account/quota is an external prerequisite. A valid key is not bundled and no public example key is used. Real-provider searches, animation returned by GIPHY and successful delivery into a compatible physical app remain to be verified by the primary agent with user configuration.
- Unmodified official Powered By GIPHY PNG attribution marks are bundled only for the new panel under `assets/giphy/`; source/provenance recorded there. Source: <https://media.giphy.com/giphy-attribution-marks.zip>, linked from <https://developers.giphy.com/docs/api/>. Provider endpoints verified against <https://developers.giphy.com/docs/api/endpoint/> on 2026-11-07.

## Tests / evidence

A standalone behavior test was written first. Initial skeleton run failed with `AssertionError: provider media`, establishing RED. Implementation then passed all 13 checks: trusted URL, HTTPS requirement, hostile suffix, userinfo, nonstandard port, wildcard MIME, unsupported editor MIME, GIF signature, HTML rejection, zero/oversized dimensions, stale generation.

Standalone command (passes):

```sh
javac -d /tmp/heliboard-gif-tests app/src/main/java/helium314/keyboard/latin/gifs/GifPolicy.java app/src/test/java/helium314/keyboard/latin/gifs/GifPolicyChecks.java
java -cp /tmp/heliboard-gif-tests helium314.keyboard.latin.gifs.GifPolicyChecks
```

Output: `GifPolicyChecks: 13 assertions passed`.

JUnit adapter `GifPolicyTest.enforcesProviderMimeMediaAndStaleResultBoundaries` enables the same checks in Gradle. Required central command: `:app:testDebugNoMinifyUnitTest` (primary agent owns all Gradle runs). `git diff --check` passed for current shared changes. Central compilation initially found hidden Android credential-storage APIs during development; those references were removed and replaced with the public, accurately documented noBackup API. Await primary agent's final build/lint/device results; this report does not claim those passed.

Physical checks still required: open GIF directly, type/delete Spanish/emoji query without draft changes, no-key state, search with configured key, animated previews, pagination/history/favorites, recipient acceptance/rejection, cursor/app switch staleness, Back/ABC normal keyboard restoration, before/after letter screenshots, rotation/landscape/one-hand/floating layouts.
