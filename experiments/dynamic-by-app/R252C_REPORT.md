# TACTIC KEYBOARD — R2.5.2c · Evidencia de superficies / identidad / píxeles

**Estado:** EN VALIDACIÓN. **GO emulador: NO AUTORIZADO** hasta completar todos los gates sin omisiones.  
**Base:** `81d93a7fcd9b56014fc4b1ff4a9567060708a1e8`; **PR #8 Draft**, sin merge, release, APK de producto, preferencias visibles ni R2.5.3. By App permanece OFF en uso normal.

## Paso 1 — Inspección de identidad de editor (cinco líneas)
1. LatinIME enviaba `displayId = -1` en todas las entradas: no representaba el display anfitrión.
2. El display del IME es **el teléfono**, incluso si el editor está en un monitor/ventana DeX.
3. `EditorInfo` no ofrece un ID público fiable del display ni de la ventana del cliente.
4. `getCurrentInputBinding()?.getConnectionToken()` identifica la conexión cliente; su hash se incorpora como `editorConnectionId`, independiente de `displayId=null`.
5. Cambiar token obliga a crear nueva sesión con `restarting=true`, sin recrear la vista si `PaletteIdentity` es igual. **No garantiza** distinguir ventanas que compartan token: DeX real queda pendiente.

**Código + tests:** `61a2017`, `b4502c4`. [Pruebas focalizadas y suite independiente](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38996632808): PASS. Otra ejecución completa [38996632819](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38996632819) presentó 1/541 fallo en `InputLogicTest.R24MotorCorrectionAndUndoReuseNativeTransaction` (motor de escritura intacto); se ha solicitado repetir el job sin relajar aserciones. **No registrar 541/541 PASS** hasta contar con XML limpio.

## Paso 2 — Primer draw y barrido de superficies

**Test nuevo:** `e306615`, `R252bRealImeFirstFrameTest.r252cWholeInputViewSurfaceSweepOnFirstPreDraw`. Recorre todo el árbol inflado en `OnPreDraw` y registra `R252C_SURFACE` (app, ID, visibilidad, fondo/tinte observado, esperado y clasificación). El gate rechaza colores visibles sin justificar. **[Run Android 38997035375](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38997035375): EN EJECUCIÓN al crear este informe**.

| Superficie | Regla que se verifica | Evidencia R2.5.2c |
|---|---|---|
| Frost / `main_keyboard_frame` | RGB de paleta + alfa 202/196 | PENDIENTE XML/logcat actual |
| Teclas / `keyboard_view` | Colores de `ColorType` y capa dibujada | PENDIENTE muestreo píxel |
| Barra de sugerencias | `STRIP_BACKGROUND`, tintes | PENDIENTE barrido |
| Emoji y portapapeles | Fondo transparente compartido y tintes cuando visibles | PENDIENTE; vista oculta no cuenta como pixel PASS |
| Popups | Ventana separada, no incluida automáticamente en árbol de InputView | NO CUBIERTO por barrido base |
| Botones de modo una mano | Tinte/color según tema y visibilidad | PENDIENTE |
| `IosGlassController` | Frost por paleta solo By App ON, alfa/blur/geometría originales | PENDIENTE nuevo test |
| Indicador `backgroundGatheringIndicator` | `android:tint="#a00000"`, señal de estado ajena a By App | COLOR FIJO JUSTIFICADO (recurso original), no recolorear |
| Superposición AI de sugerencias | `aiTheme.surface`, función AI ajena a By App | SIN CAMBIOS; no suponer cobertura si no visible |

**Referencias verificadas de R2.5.2b** (no equivalen a píxeles nuevos): A fondo `#FFF6FAF9` / frost `#CAF6FAF9`; B `#FFFFF7F9` / `#CAFFF7F9`; regreso A `#CAF6FAF9`. El primer frame previo midió 3 transiciones y 3 `setInputView` legítimos, 0 hide/show; la nueva secuencia debe volver a registrar totales, no solo el worker.

## Pasos 3–6 — Gates aún sin evidencia R2.5.2c

- **By App OFF:** referencia explícita original `a686f7d`: frost claro **`#CAF2F2F7`**, oscuro **`#C41F1F21`**. Se necesita un test con colores reales de `View`, además de verificar las superficies tocadas.
- **Dos apps reales de UID distintos:** no se han instalado fixtures de dos clientes ni obtenido un screencap del display del teléfono; **no hay píxeles nuevos que declarar PASS**. La tolerancia se deberá definir antes del muestreo incluyendo alfa y blur, y guardar capturas/logcat.
- **DeX / foco de dos ventanas:** un emulador Pixel/API35 no reproduce la integración Samsung DeX con el teclado anclado al teléfono. La aceptación física de ese comportamiento queda para R2.5.3, sin contarla como cobertura de emulador.
- **Métricas completas:** exigir por transición `OnPreDraw >=1`, cambios efectivos `setInputView` justificados, misma `PaletteIdentity` = 0 recreaciones, `hideWindow/showWindow=0` desde extracción, 0 mezcla entre sesiones. No reutilizar conteos del worker como totales.
- **Físico:** Samsung S25 / One UI / DeX: **NO EJECUTADO**.

## Veredicto provisional

**PENDIENTE / SIN GO emulador.** Los escenarios todavía no ejecutados o no observables no se etiquetan PASS. Cambios limitados a identidad de cliente y tests; ninguna autorización para activar By App o iniciar R2.5.3.
