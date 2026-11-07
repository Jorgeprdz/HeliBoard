# Local text sticker renderer and storage

Scope followed the user's latest steering: reusable local renderer/store only. No creator panel was added and GifPanel was not changed by this work. The primary/integration agents own the automatic suggestion-strip slot, session validation, tap-to-send and long-press explicit save.

## Public APIs

Package `helium314.keyboard.latin.gifs`:

- `TextStickerRenderer.validate(String)` returns trimmed validated text or throws `IllegalArgumentException` with an inline-ready Spanish error.
- `TextStickerRenderer.render(String)` returns a decorated transparent 512×512 PNG as `byte[]`. Call off the UI thread. It creates/recycles its bitmap, uses no credentials/network/editor/storage, preserves combining/emoji clusters in wrapping, shrinks long text to fit, and supports bidi shaping via StaticLayout. It paints original gradient/stroke text and small decorations, keeping transparent margins.
- `new TextStickerStore(File cacheDirectory, File privateDirectory)`; pass `ime.getCacheDir(), ime.getFilesDir()`.
- `preview(byte[] png)` writes a transient File under the existing `gif-share/` FileProvider cache path.
- `save(byte[] png)` explicitly persists a content-hashed private PNG under `files/text-stickers/`; deduplicates identical content and retains at most 40 files, retaining the current save even with future/tied file timestamps.
- `saved()` returns persistent files ordered newest first; empty if unavailable.
- `shareCopy(File saved)` accepts only the store's private saved PNGs and returns a scoped cache copy for URI delivery. It rejects arbitrary files.
- Preview/save/shareCopy throw `IOException` for invalid PNG/storage. PNG data is bounded to 2MiB and checked for signature, 512×512 IHDR, complete chunks, CRC, IDAT and IEND. Sharing still requires the caller's current-editor MIME/session checks.
- `TextStickerText.validate/graphemes/appendWithinLimit/deleteLast` support isolated Unicode input. The hard bound is 120 code points, unpaired surrogates/empty/control text rejected. Accent marks, emoji modifiers, ZWJ sequences, variation selectors, regional flag pairs and common virama conjuncts stay together.

No FileProvider XML changes are needed: `.gifcontent` already exposes `cache/gif-share/`. Saved files stay private and are copied into that scoped path only for sharing. Store alone never sends, autosaves, or reads editor text. Incognito save behavior belongs to the explicit user action in the integration.

## Verification

1. Pure tests written first; initial implementation skeleton failed `AssertionError: Spanish emoji text retained`.
2. Implemented validation, cluster handling and bounded persistent storage. Pure tests passed.
3. Added a regression proving a new save must survive existing future/tied timestamps. Observed RED at `AssertionError: new save survives future timestamps and ties`, fixed pruning/order, then GREEN.
4. Final standalone run: `TextStickerChecks: 18 assertions passed`.
5. Renderer/store/text classes compile directly against public Android SDK37 android.jar with javac. No full build was started by this agent. `git diff --check` passed.
6. JUnit adapter `TextStickerStoreTest` discovers the pure checks. `TextStickerRendererTest` uses Robolectric SDK28 with NATIVE graphics: transparent corners/margins, PNG512 dimensions and visible ink for Spanish+emoji, combining/ZWJ/flags, RTL, long unspaced words, plus empty/overlimit rejection. Those raster tests were written before the renderer implementation and await x86 CI; the ARM host is not a supported native-Robolectric execution target. Do not claim raster/device tests passed from javac/pure results.

Commands executed:

```sh
javac -cp /opt/android-sdk/platforms/android-37.0/android.jar -d /tmp/text-sticker-compile app/src/main/java/helium314/keyboard/latin/gifs/TextSticker{Text,Store,Renderer}.java
javac -d /tmp/text-sticker-tests app/src/main/java/helium314/keyboard/latin/gifs/TextSticker{Text,Store}.java app/src/test/java/helium314/keyboard/latin/gifs/TextStickerChecks.java
java -cp /tmp/text-sticker-tests helium314.keyboard.latin.gifs.TextStickerChecks
```

Required CI targets include `TextStickerStoreTest`, `TextStickerRendererTest`, then full JVM/lint/signed build and physical receiver checks owned by the primary agent.

Ruling: latest user steering replaces the old Creator/Gallery panel UI plan. Only reusable renderer/store APIs were implemented here; automatic suggestion-strip integration is owned by the integration agent. No existing letter keyboard geometry/resources/style was edited.
