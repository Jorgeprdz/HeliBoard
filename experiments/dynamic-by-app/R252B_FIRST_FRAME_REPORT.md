# TACTIC KEYBOARD — R2.5.2b · Informe de primer frame

**16 oct 2026, CDMX.** Base de integración: `a686f7d`. **Commit técnico probado:** `ca448581d063950fdb758284a89eb10e84313d16`. **PR #8:** Draft, sin merge, release ni APK de producto. **By App:** OFF para usuarios normales.

**Veredicto: GO VISUAL ACOTADO para el harness instrumentado Android 15 (API 35).** No implica activación pública, prueba de dos apps con UID real distinto, ni aceptación física Samsung/DeX. R2.5.3 NO se inició.

## Resultados (evidencia real)

| Escenario | Comprobación | Resultado |
|---|---|---|
| A → B → A, mismo inputType y `restarting=true` | Tres `OnPreDraw` de InputView real; fondo dibujable y SettingsValues iguales a paleta efectiva | **PASS 3/3 frames** |
| Misma PaletteIdentity en reinicio | Ningún `setInputView` adicional ni cambio de fondo indebido | **PASS; 0 recreaciones** |
| Ciclo `onFinishInputView/onFinishInput/onStartInput/onStartInputView` | Editor con InputConnection real y packageName sintético alternante | **PASS** |
| Composición A → B → A | Texto y span compuesto intactos | **PASS** |
| Selección de texto | Posiciones 0–3 y texto intactos | **PASS** |
| Rotación del host a paisaje | Primer frame y texto conservados | **PASS** |
| Worker cromático / caché | 0 `setInputView`, 0 `hideWindow`, 0 `showWindow` desde workers | **PASS** |
| Temas heredados y By App OFF | Ruta de frost original conservada cuando no existe sesión By App vinculada | **Sin cambio de código en rama OFF; regresión PASS** |

**Colores de primer frame observados (en el propio drawable de `main_keyboard_frame`):**
- **A:** `MAIN_BACKGROUND=#FFF6FAF9`; superficie esmerilada realmente dibujada `#CAF6FAF9` (alfa 202).
- **B:** `MAIN_BACKGROUND=#FFFFF7F9`; superficie `#CAFFF7F9`.
- **A de regreso:** `#CAF6FAF9`, sin arrastrar la superficie de B.

**Métricas de toda A→B→A:** 3 frames iniciales, **3 llamadas a `setInputView`** por cambios de paleta efectivos, **0 llamadas `hideWindow`**, **0 `showWindow`**. Reinicio de misma paleta: **0 reemplazos innecesarios**. El test registra la entrada real del servicio, no solo callbacks del extractor.

## Pruebas y evidencias

- **JVM + contratos:** [run 38995256043](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38995256043), **539/539 JVM, 80/80 contratos**, 0 errores y 0 omitidas.
- **Emulador Android 15:** mismo run, **1/1 smoke + 5/5 vector + 3/3 sesión + 3/3 R2.5.2b con LatinIME/host real = 12/12 PASS**.
- **Artefacto auditado:** [XML, logcat y diagnósticos (12087560010)](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38995256043/artifacts/12087560010). Rutas: `r25-evidence/r252b/logcat-all.txt`, `r25-evidence/r252b/androidTest-results/`, `r25-evidence/ime-before.txt`, `r25-evidence/ime-after-r252b.txt`. El IME previo (Gboard) se restauró exactamente.

## Fallo reproducido y corrección mínima

[Run inicial 38994654177](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38994654177), caso A→B→A: la paleta interna cambiaba, pero el fondo visible era el `#CAF2F2F7` fijo impuesto por `IosGlassController.apply()`. **2/3 harness PASS, 1/3 FAIL**, sin relajar aserciones. El commit `ca448581d` limita la corrección al **RGB del frost si `MonetKeyboardColors` tiene sesión By App vinculada**, conservando el alfa 202/196, forma, blur, recursos y todos los estilos cuando By App está OFF. El retest completo es **PASS**.

**Hallazgo 7 cerrado:** getter booleano `InputLogic.isComposingForPalette()` que consulta `WordComposer.isComposingWord()`, sin leer/materializar palabras ni modificar la lógica de composición. `LatinIME` y `KeyboardSwitcher` usan esa ruta.

## Límites y riesgos residuales

- El **host y la InputConnection sí son reales**, con LatinIME activo; **`EditorInfo.packageName` A/B fue simulado** en la misma aplicación para forzar el `restarting=true`. No prueba dos procesos/UID de apps reales ni todos los estados multiwindow.
- `OnPreDraw` y el drawable de fondo están medidos; **no se capturó una señal GPU/Pixels de la primera imagen presentada ni todas las capas de teclas/pictogramas**. Las 20 combinaciones del mapper están cubiertas por las pruebas de R2.5.2, no por capturas individuales de este harness.
- **Físico Samsung Galaxy S25, One UI y DeX: NO EJECUTADO**; corresponde a una autorización posterior de R2.5.3.

**Regla de salida:** conservar PR Draft, no activar By App para uso normal, no publicar APK ni fusionar.


## R2.5.2b — verificación complementaria de la regresión By App OFF

**Commit test-only:** [`39b07d0`](https://github.com/Jorgeprdz/HeliBoard/commit/39b07d072f18aa7dde8f712926e27682b26da13e). No se modificó código de producción: solamente el criterio de medición del frost translúcido en el test instrumentado.

**Resultado completo:** [run 38998431754](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38998431754) **SUCCESS**: **541/541 JVM**, **80/80 contratos**, Android API 35 **14/14** (smoke 1/1; vectores 5/5; sesiones 3/3; harness real 5/5), sin errores ni omitidos. [Artefacto directo XML/logcat](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38998431754/artifacts/12088356902).

**Hallazgo medido:** un `GradientDrawable` con alfa 202/196 puede devolver al rasterizar en `Bitmap.Config.ARGB_8888` un RGB con ±1 nivel por premultiplicación/despremultiplicación. La prueba anterior comparaba indebidamente el píxel rasterizado con el entero de tinte sin rasterizar: esperaba `#CAF2F2F7` y recibía `#CAF1F1F6`. Ahora son dos aserciones **exactas**, sin tolerancias relajadas: `GradientDrawable.color.defaultColor == ARGB original` y `píxel real == píxel de referencia renderizada con el mismo tinte, geometría y alfa`. Ambas pasan en **OFF claro y oscuro**. El logcat conserva los valores originales `#CAF2F2F7` y `#C41F1F21`; sus muestras rasterizadas fueron `#CAF1F1F6` y `#C41E1E21`.

El run volvió a medir A→B→A en el primer `OnPreDraw` sin mezcla (`#CAF6FAF9 → #CAFFF7F9 → #CAF6FAF9`), tres `setInputView` justificados, `hideWindow/showWindow=0/0`, composición, selección y rotación preservadas. El IME anterior de Gboard se restauró. **GO visual acotado al harness instrumentado**, sin validar dos UIDs/host independientes ni píxeles de GPU presentados. Samsung/DeX físico queda pendiente. PR #8 Draft; By App OFF para usuarios; sin merge, release, APK de producto ni R2.5.3.
