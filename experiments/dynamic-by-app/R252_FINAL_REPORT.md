# TACTIC KEYBOARD — R2.5.2 · INFORME DE INTEGRACIÓN

**Fecha:** 16 de octubre de 2026, CDMX · **Rama:** `feat/tactic-r243-hybrid-writing`
**HEAD técnico validado:** `67e181b878badf7801f8e6a42404601933c8e8ef` · [PR #8](https://github.com/Jorgeprdz/HeliBoard/pull/8), **Draft, sin merge**.
**Decisión:** **GO técnico condicionado** para conservar la integración interna y sus pruebas; **NO-GO para activarla en usuarios o afirmar seguridad visual de primer frame** hasta disponer de una prueba real de `InputView`. R2.5.3 **NO AUTORIZADA**.

## Inspección enfocada: 10 hallazgos

1. `LatinIME.onStartInputViewInternal` omite `loadSettings` con `restarting=true`, mismo inputType y orientación.
2. `UIHandler` puede agrupar finish/start durante rotación, en ese orden.
3. `SettingsValues.mColors` se crea dentro de `loadSettings`, antes de `KeyboardSwitcher.updateKeyboardTheme`.
4. `updateKeyboardTheme` puede reemplazar `InputView` cuando considera desactualizado el tema y existe una vista.
5. `setThemeNeedsReload` oculta/muestra el IME si se encuentra visible; no se usa en workers By App.
6. `Colors` aplica tintes y backgrounds a Drawable/View; `KeyboardSwitcher` recarga iconos dependientes del tema.
7. `LatinIME.hasAiComposition()` refleja existencia de palabra compuesta; la integración solo usa su booleano. Pendiente: evaluar un getter de solo estado sin materializar texto.
8. `RichInputConnection.hasSelection()` compara posiciones guardadas, sin leer texto.
9. El coordinador previo mezclaba epoch en su fingerprint; la política nueva separa epoch de identidad visual.
10. `MonetKeyboardColors(seedOverride)` desactiva el seguimiento del wallpaper: la nueva vinculación compara `PaletteIdentity` y el caso OFF preserva la lógica heredada.

## Implementación, commits atómicos y evidencia

| Etapa | Commit | Validación |
|---|---|---|
| A — política pura `SessionIdentity/PaletteIdentity`, Keep/Apply/Defer/Fallback/RejectStale | [`6c7c796`](https://github.com/Jorgeprdz/HeliBoard/commit/6c7c7960a0e76ceb21b304c335bf69cd36358ddd) | [527/527 JVM](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38988407062) |
| B — enlace LatinIME/coordinador inerte por defecto, casos de sesión | [`6c17fe3`](https://github.com/Jorgeprdz/HeliBoard/commit/6c17fe379c950ed2e6dfcf6c3a65ecfd1b9befbc) | [532/532 JVM](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38988674939) |
| C — binding por paleta efectiva, guard de composición/selección, 20 combinaciones | [`a8cb02e`](https://github.com/Jorgeprdz/HeliBoard/commit/a8cb02e5544b11cc481a8814a2cb0c4575a3f88c) | [536/536 JVM](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38989182728) |
| D — pruebas emuladas y recuperación forense runner, política ante fallback | `9d7282d`, `89e3549`, `931bd25`, `edfeab1`, `67e181b` | [run FINAL 38993165298](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38993165298): **539/539 JVM**, **80/80 contratos**, Android **1/1 smoke + 5/5 vectores + 3/3 sesiones** |

No se relajaron pruebas. Un fallo anterior de `XLinkTest.knownDictionaries` fue HTTP **504** de Codeberg (esperaba 200); pasó sin modificaciones en el run final.

**Evidencia directa Android 15/API 35:** [artefacto XML/logcat/métricas 12086216590](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38993165298/artifacts/12086216590). El `logcat` del test confirma `R252_WORKER_METRICS: setInputView=0 hideWindow=0 showWindow=0`. El IME antes y después de las nueve pruebas fue idéntico. La semilla cian sintética fue `#FF00BCD4`, cromaticidad HCT 49.961, determinista en diez muestras, sin cuantizar en hilo principal; fallbacks tipados para iconos neutros/inaccesibles/rotos.

## Seguridad y límites del dictamen

Se preservaron el tema `dynamic` heredado, temas estáticos/personalizados, las diez familias Material, geometría, branding, firma, IDs, preferencias, autocorrección, diccionarios y Gemini/Qwen. No se añadieron permisos, redes, accesibilidad o capturas. `debugNoMinify/AndroidManifest.xml` conserva la eliminación exclusiva para tests del receptor `BOOT_COMPLETED`. By App sigue **OFF para uso ordinario**; solo lo encienden fixtures de inyección interna. Ningún APK de producto, merge ni release.

**Limitación principal:** las 3 pruebas de sesión Android son tests instrumentados del *coordinador aislado*, no una sesión real de LatinIME que dibuje la `InputView` bajo alternancia visible de aplicaciones. Por tanto **no se demostró** que el primer frame ya tenga la paleta correcta, ni ausencia física de flash, ni preservación de composición real del editor en Samsung/DeX. Los contadores `0/0/0` miden específicamente la **finalización del worker**, no todos los cambios de vista por fronteras de editor. Esta precisión impide presentar el gate como aceptación visual total.

**Siguiente validación necesaria, SIN iniciarla aquí:** harness aislado de LatinIME/host en Android que observe `OnPreDraw` y composición efectiva antes de dar GO visual; Galaxy S25/One UI/DeX y preferencias corresponden a una autorización posterior. Hasta entonces el producto permanece intacto para usuarios normales.

**Detener R2.5.2 tras este informe.**
