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


## Evidencia verificada posterior — 16 oct. 2026, barrido de superficies

**[Run 38997035375](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38997035375):** el **job Android** fue **SUCCESS** con 1/1 smoke, 5/5 vectores, 3/3 sesiones y **4/4 harness real (incluido el nuevo barrido R252c)**. El workflow global fue FAILURE solo por la prueba externa `XLinkTest.knownDictionaries` (1/541 en el job JVM de ese run); no se modificó ni relajó. El [reintento independiente 38996632819](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38996632819) de regresión completa: **541/541 PASS, 80/80 contratos PASS**, sin omisiones.

**[Artefacto directo XML + logcat (12087467249)](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38997035375/artifacts/12087467249):**
- `r25-evidence/r252b/logcat-all.txt`: `R252C_SURFACE_GATE: transitions=3 totalNodes=141 popup=NOT_ATTACHED_TO_INPUTVIEW hidden=NOT_DRAWN`.
- A primer frame: paleta `#FFF6FAF9`, frost `#CAF6FAF9`, drawable **`#CAF6FAF9`**.
- B primer frame: paleta `#FFFFF7F9`, frost `#CAFFF7F9`, drawable **`#CAFFF7F9`**.
- A retorno: frost **`#CAF6FAF9`**; sin arrastrar el color B.
- `R252B_SEQUENCE: firstFrames=3 inputView=3 hides=0 shows=0`: **3 cambios efectivos de paleta, 3 reemplazos de vista, cero hide/show**. El reinicio de misma paleta no recreó InputView según su test independiente.
- `R252B_COMPOSITION: textRetained=true composingSpanRetained=true selection=true rotation=true firstFrame=true`.
- El logcat completo del harness (todos sus casos, no solo las tres transiciones del barrido) clasificó 644 líneas etiquetadas, incluyendo una línea de resumen. Entre los nodos: **0 `UNCLASSIFIED_COLOR` visibles**, `PALETTE_MATCH` 28, `PALETTE_ROLE_MATCH` 14, `FIXED_STATUS_INDICATOR` 4; el resto fue transparente, sin fondo/tinte u oculto. El indicador fijo `#FFA00000` es estado de recopilación, NO semilla By App.

**Resultado de paso 2: PASS únicamente para las superficies presentes/visibles del árbol InputView.** Popups fuera del árbol y vistas ocultas **NO se han dibujado ni muestreado**; no constituyen GO total para el paso 2.

## Paso 3 — Estado de ejecución
El commit `18a7544` añadió prueba Android del frost claro/oscuro **By App OFF** con los valores originales `#CAF2F2F7` y `#C41F1F21`, radio de esquinas, transparencia, clip y mapa completo de ColorType invariantes. El run de validación `38997689861` debe concluir antes de marcar PASS o fallos.

**Los pasos 4 (dos apps con UID distinto + screencap real), 5 (ventanas DeX) y la cobertura de ventanas/popup ocultos continúan sin GO. No confundir `OnPreDraw` con píxeles GPU finalmente presentados.**
