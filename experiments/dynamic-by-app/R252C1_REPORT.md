# TACTIC KEYBOARD — R2.5.2c-1 · Real Apps / Surface / Compositor

**Status as of 2026-10-16 (CDMX): PENDING_CI — NO GATE APPROVAL.** This is a deliberately provisional forensic report. Never treat a started CI job as a PASS.

## Base and changes

- Base verified before changes: `4c4c6ac4e572a41a0120a282782653eb6a601c57`, branch `feat/tactic-r243-hybrid-writing`, PR #8 Draft.
- Source authority: `R252B_FIRST_FRAME_REPORT.md`. Existing proof: 541/541 JVM, 80/80 contracts, 14/14 Android 15 from [run 38998431754](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38998431754); synthetic cross-package EditorInfo only.
- Added distinct installable, launcher-visible Android editor hosts `com.jorgeprdz.r252c.a` and `com.jorgeprdz.r252c.b`, identical inputType, distinct solid chromatic launcher icons.
- Added instrumentation `R252cRealAppsTest` driving app switches by `am start -W` and asserting actual distinct PackageManager UIDs, genuine `EditorInfo.packageName`, bound seed origin, 9 attached InputView OnPreDraw callbacks in three A→B→A rounds.
- Screenshot capture occurs **after** observed OnPreDraw: it is **not proof of first compositor-presented frame**.
- CI workflow `.github/workflows/tactic-r252c-real-apps.yml` and script `.github/scripts/r252c-real-apps-gate.sh` preserve XML, full logcat, displays, screenshot samples, original IME restoration and two fixture APKs.
- No changes to `debugNoMinify`, existing approved visual UI, LatinIME production behavior, release configuration or user-facing activation.

## Inspection (10 lines maximum)

1. `SessionIdentity.displayId` is supplied as **null** by LatinIME because the trusted editor display is unavailable; it is **not** taken from the IME window/display.
2. `getCurrentInputBinding().getConnectionToken()` feeds `editorConnectionId` to distinguish client connections; equal physical displays are not artificially separated.
3. `AppColorSeedProvider` already accepts an injected `Executor`; no production seam is necessary.
4. `AppSessionColorCoordinator` uses `epoch` and ticket identity for stale work protection.
5. `PaletteTransitionPolicy.RejectStale` checks epochs; existing pure-policy tests are not an observed delayed cross-UID callback.
6. Render binding occurs at the input boundary; asynchronous workers never repaint.
7. R2.5.2b measured the main frosted panel, but detached overlays remain outside its observation tree.
8. R2.5.2b checked exact unbound OFF frost tint and independent raster comparison (with premultiplication effects).
9. New fixture icons use actual `PackageManager` lookup rather than synthetic `EditorInfo`.
10. First GPU-presented frame and physical phone/DeX remain independent acceptance gates.

## Gate matrix — evidence-aware

| Gate | Scope | Status | Evidence needed |
|---|---|---|---|
| S | Main panel, strip, popups, emoji, clipboard, one hand, iOS glass, detached windows | **NOT_PROVEN** | Complete activation/role screenshots and rendering inspection of all separate windows |
| A | Independent UIDs, real app focus, warm-cache A→B→A | **PENDING_CI** | XML PASS, nine log timestamps, actual UID numbers, three 3/3 rounds |
| A | Cold-cache A→B, A→A, B→B, A→B while typing, rapid A→B→A→B | **NOT_PROVEN** | Separate observed scenarios; no simulation |
| B | Real composition/selection/cursor/rotation across two actual UIDs | **NOT_PROVEN** | App-owned editor state and InputConnection observations |
| C1 | Configured tint vs effective drawable and software raster | **PENDING_CI** | Android 15 logcat and raster measurements |
| C2 | Temporal correspondence of display compositor's **first presented** frame | **NOT_PROVEN** | Supported first-present correlation; post-draw screencap alone is insufficient |
| OFF | Original unmodified light/dark baseline | **PREVIOUSLY_PASS_ONLY** | R2.5.2b evidence; new R2.5.2c regression pending |
| Physical | Galaxy S25 and Samsung DeX | **PENDING_PHYSICAL_DEVICE** | Actual handset, monitor, screenshots of correct displays |

## Stability / test records

| Scenario | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| A→B→A real apps | PENDING_CI | PENDING_CI | PENDING_CI |
| Real composition | NOT_EXECUTED | NOT_EXECUTED | NOT_EXECUTED |
| Detached overlay roles | NOT_EXECUTED | NOT_EXECUTED | NOT_EXECUTED |
| Compositor first presented | NOT_PROVEN | NOT_PROVEN | NOT_PROVEN |

**Metrics:** `setInputView / hideWindow / showWindow`, UIDs, `displayId`, epochs, requested/applied palette, first-pre-draw timestamps, count and sampled raster are expected in CI logcat. Do not interpret missing output as zero.

**Gate verdict: BLOCKED_PENDING_EVIDENCE.** Neither `GO_CONDITIONED` nor `NO_GO` is supported yet. No product defect has yet been established by a completed R2.5.2c-1 run, but that is not proof of absence. **R2.5.2c-2 may not be described as completed, nor may a product APK be published, until the phase-transition rule is satisfied.**

**CI candidate:** [R2.5.2c-1 run 39000221436](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000221436). This is an execution link, **not** an assertion of completion. Other observed workflow runs include 39000189961 (prior revision; missing script) and 39000140968 (existing R25 regression).

## Diagnóstico actualizado del primer ciclo de CI (sin promover a PASS)

- [Run 39000189961](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000189961): **FAIL de infraestructura** por falta del script `.github/scripts/r252c-real-apps-gate.sh` en el commit que lo ejecutó; se incorporó después.
- [Run 39000221436](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000221436): **FAIL de infraestructura en configuración**: `Test Build Type 'debugNoMinify' does not exist` para fixture `app-a`. Las dos apps se instalaron; no hubo pruebas reales, OnPreDraw ni validación de UID (no confundir instalación con PASS).
- Se corrigió únicamente el init script de pruebas con `if (project.path != ":app") return` para no aplicar `debugNoMinify` a los proyectos auxiliares; la variante protegida del teclado no se modificó. Commit [a4ef4b6](https://github.com/Jorgeprdz/HeliBoard/commit/a4ef4b6364b4a8dcd72e2eaebfe6aadcdff2d6c5).
- [Run 39000407892](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000407892): nueva ejecución con corrección; **aún no validado su resultado** al registrar este apartado.
- [Run R25 39000140968](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000140968): regressión JVM **540/541, un FAIL** en `InputLogicTest` (cola diferida restante `what=0`, aserción de cero); 80 contratos de host pasaron en su propio paso, pero no hubo JVM completa en verde. Android instrumentation falló por el mismo `debugNoMinify` de fixture. **No se clasifica ese test como flaky PASS ni como regresión funcional probada sin repetición independiente.**
- [Run R25 39000407904](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000407904): nueva regresión sobre HEAD corregido, sin veredicto al registrar este apartado.

Estas ejecuciones **no satisfacen** la transición automática a R2.5.2c-2; no hay evidencia de un `GO_CONDITIONED` ni de que todas las limitaciones sean estrictamente infraestructura sin pruebas esenciales pendientes.
