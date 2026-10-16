# R2.5 By App — physical vector extraction gate (NOT EXECUTED)

Purpose: minimum instrumented Android fixture for a later, separately authorized phase. No APK, installation, IME integration, permissions, or launcher changes are authorized in this phase.

## Why this is needed

- Robolectric may inflate VectorDrawable but not faithfully rasterize complex vector/tinted content. Equality across two results is determinism, NOT a valid chromatic seed.
- The current host test probes the compiled multicolored `ic_launcher_foreground.xml` without modifying the approved artwork. It reports `R25_VECTOR_CHROMATIC_VALID` only if an opaque semantically chromatic result actually exists; `R25_VECTOR_ROBOLECTRIC_UNVERIFIED` explicitly means no Android vector success was demonstrated.
- A white or black vector must return `SeedResult.Unavailable(NO_CHROMA)` rather than the artificial fallback blue from `Score`; malformed drawables must return `EXTRACTION_ERROR`.

## Separately authorized future Android instrumented test

1. Place a *new test-only* XML vector in `app/src/androidTest/res/drawable/`, with an opaque 64dp square path filled `#FF00BCD4` (cyan) covering at least 80% of the viewport. Do not change existing app resources or branding.
2. Use Android instrumentation context to inflate that compiled VectorDrawable. On a dedicated executor, invoke the existing `AppIconSeedExtractor.extract(drawable)`; assert the executing thread is not the Android main thread.
3. Assert `SeedResult.Available`, alpha 255, `Hct.fromInt(seed).chroma >= 14` and hue angular difference <= 25 degrees from `Hct.fromInt(0xFF00BCD4)`. Repeat 10 independent inflations/extractions and require identical seeds.
4. Add pure white, pure black, transparent and throwing-drawable test resources; assert typed `NO_CHROMA` for the first three and `EXTRACTION_ERROR` for the last. For adaptive icons with an opaque neutral background and colored foreground, confirm colored foreground preference.
5. Execute on Galaxy S25 (Samsung One UI) and, separately where possible, Samsung DeX. Record Android/One UI versions, seed hexadecimal, chroma, hue distance, result types, execution thread, failure diagnostics, and test logs.
6. Record measured p50/p95 worker extraction, allocations and retention using Android profiling; the 64x64 bitmap itself is 16 KiB ARGB_8888 (not a measured whole-pipeline peak). No capture/screenshot, AccessibilityService, MediaProjection or network.

## Gate

- PASS: chromatic vector produces a verified matching chromatic seed on physical Android, while monochrome, transparent and broken assets fail safely and deterministically.
- NO-GO: if only Robolectric assertions exist, report 'Android vector compatibility NOT VERIFIED' without presenting the host suite as physical validation.
- The external `XLinkTest.knownDictionaries` and InputLogic URL harness forensic investigation remain separately tracked; never disable either to obtain green CI.