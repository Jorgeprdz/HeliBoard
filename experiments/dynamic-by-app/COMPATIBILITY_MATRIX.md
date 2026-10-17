# TACTIC KEYBOARD — R2.5 · DYNAMIC COLORS BY APP
## COMPATIBILITY_MATRIX.md — cobertura planificada, SIN ejecuciones

**Fecha:** 2026-10-16  
**Rama base auditada:** `feat/tactic-r243-hybrid-writing` · SHA: `5e58323713eaf7fbc463fec7bed8a1483780556d`  
**PR #8:** conservar Draft.  
**Estado de todos los escenarios de esta matriz:** **PENDIENTE DE IMPLEMENTAR / NO EJECUTADO**. La existencia de la clase `Scheme*` y del test base no constituye una prueba de By App.  
**Dictamen aplicable:** GO CON CONDICIONES.

## 1. Semántica global de By App

- `Fuente de color dinámico = SYSTEM_WALLPAPER | BY_APP`, opt-in; default actual (sistema/wallpaper).
- `PREF_THEME_COLORS` y `PREF_THEME_COLORS_NIGHT` **siguen siendo independientes**: la fuente es común, la familia puede ser distinta cada momento. Se recomienda no crear una fuente por modo hasta que exista necesidad comprobada.
- `BY_APP` es fuente para **las diez familias locales**, no familia #11. `DynamicSchemeProvider.get(seed, type, isDark)` mantiene `SPEC_2026` y sus propios algoritmos/roles.
- Tema heredado `THEME_DYNAMIC_SYSTEM="dynamic"`: continúa usando `DynamicColors` y colores nativos Android; no pasar por el extractor de iconos. Temas Light, Darker, Black, personalizados y demás estáticos no se modifican.
- Estado de semilla: `WARM_APP` → usar semilla de icono desde primera pintura de sesión; `COLD_APP` → usar sistema/wallpaper ahora, extraer fuera del hilo principal, **aplicar en siguiente sesión**; `UNAVAILABLE`/perfil restringido → sistema/wallpaper; `OFF` → ruta exacta existente.
- El `seedOverride` actual funciona para generar una familia pero desactiva `haveColorsChanged`: requiere una nueva noción de fingerprint o coordinación de sesión.
- Tema día/noche: la *misma semilla del icono*, con `isDark=false/true`; **no** tomar color oscuro de icono como decisión de night mode.
- Limitación conceptual: icono de app ≠ fondo/tema visual dentro de esa app.

## 2. Matriz completa: diez variantes por dos modos

Cada fila significa **compatibilidad arquitectónica verificada de la ruta común**, pero **comportamiento By App previsto, no validado**. Las familias existentes fueron observadas en `DynamicSchemeProvider.kt` en el HEAD indicado.

| Familia local / clase | Día: resultado esperado con semilla S | Noche: resultado esperado con la misma S | Personalidad cromática que conservar | Condición/fallback específico |
|---|---|---|---|---|
| **1. Neutral** / `SchemeNeutral` | `get(S, NEUTRAL, false)` | `get(S, NEUTRAL, true)` | Cromaticidad moderada/baja; la identidad de marca puede ser sutil | No forzar saturación artificial; icono sin color → sistema |
| **2. Monochrome** / `SchemeMonochrome` | `get(S, MONOCHROME, false)` | `get(S, MONOCHROME, true)` | Monocromía; **puede no parecer del color del logo** | No considerar falta de color visible como bug del extractor |
| **3. Tonal Spot** / `SchemeTonalSpot` | `get(S, TONAL_SPOT, false)` | `get(S, TONAL_SPOT, true)` | Balance tonal Material y acento derivado | Validar separación de fondo/texto y estilo con/sin bordes |
| **4. Vibrant** / `SchemeVibrant` | `get(S, VIBRANT, false)` | `get(S, VIBRANT, true)` | Acentos más intensos, sin desbordar roles | Validar acciones/sugerencias altamente cromáticas |
| **5. Rainbow** / `SchemeRainbow` | `get(S, RAINBOW, false)` | `get(S, RAINBOW, true)` | Distribución multicolor propia del algoritmo; no replica simplemente el logo | No fijar colores manuales para “parecerse” a cada app |
| **6. Expressive** / `SchemeExpressive` | `get(S, EXPRESSIVE, false)` | `get(S, EXPRESSIVE, true)` | Relaciones expresivas de tonos y matices complementarios | Probar color clave/suggestion bar contra fondos |
| **7. Fidelity** / `SchemeFidelity` | `get(S, FIDELITY, false)` | `get(S, FIDELITY, true)` | Intención de fidelidad cromática a semilla | No confundir fidelidad a icono con extracción real del tema de la app |
| **8. Content** / `SchemeContent` | `get(S, CONTENT, false)` | `get(S, CONTENT, true)` | Paleta cercana al contenido semilla | Contrast gate con fuentes muy luminosas/oscuras |
| **9. Fruit Salad** / `SchemeFruitSalad` | `get(S, FRUIT_SALAD, false)` | `get(S, FRUIT_SALAD, true)` | Relaciones cromáticas peculiares de su clase | No sustituir por Vibrant/Tonal Spot; validar cambios de semilla |
| **10. CMF** / `SchemeCmf` | `get(S, CMF, false)` | `get(S, CMF, true)` | Reglas específicas de `SchemeCmf` | Respetar clase existente, no inventar un perfil CMF por app |

**Cobertura mínima del producto:** 10 variantes × 2 modos = **20 combinaciones**. Para compatibilidad del mapper hay además 3 estilos actuales (`Material`, `Holo`, `Rounded`) × 2 estados de bordes → **120 combinaciones por semilla** (10 × 2 × 3 × 2). Múltiples semillas de iconos deben ampliar la matriz; estos conteos son **dimensiones de pruebas**, no tests ejecutados.

## 3. Matriz de extracción y fallback transversal (aplica a las diez)

| Icono / contexto | Detección prevista | Semilla elegida | Aplicación visual | Validación pendiente |
|---|---|---|---|---|
| App con icono a color simple | `EditorInfo.packageName` válido; bitmap/vector disponible | Cluster cromático seleccionado por Celebi + Score y filtro local | Warm: inmediato en nueva sesión; cold: siguiente sesión | Marca dominante razonable, determinismo y contraste |
| Icono multicolor | Foreground/máscara con varios tonos | Selección estable y representativa, sin favorecer fondo uniforme | La variante mantiene su personalidad, no “copia” el icono | WhatsApp/Instagram/Telegram u otros fixtures de prueba, **sin suponer colores exactos** |
| AdaptiveIconDrawable con fondo blanco y elemento de color | Acceso original a capas o raster compuesto | Prioridad a pigmento cromático real | Sin sobresaturación ni selección del blanco del fondo | Samsung/Android 13+ y máscara adaptativa |
| Themed icon / monochrome tintado por launcher | Puede representar máscara monocroma sin marca original | Probar icono original; si solo hay mono/tinte, **fallback** | Nunca inferir marca del accent del sistema | Pixel/One UI icon pack/theming |
| Blanco/negro/gris | Sin cluster de croma suficiente | `UNAVAILABLE` → wallpaper/sistema | Misma familia y modo, sin falso azul por `Score` | Fixtures de grises y alfas |
| Sin icono / drawable roto / excepción | Fallo local controlado | Sistema/wallpaper actual | IME aparece normalmente, sin bloqueo | Mock PackageManager y test de error |
| App no visible a PackageManager | Paquete editor válido, recursos no accesibles | Sistema/wallpaper | No solicitar permisos ni servicio externo | Android package visibility y excepciones |
| Work profile / Knox / Secure Folder | Paquete o UID en contexto de perfil diferente | Local solo si el recurso se puede leer legalmente; si no fallback | Ningún cruce de perfil | Prueba en un equipo donde exista perfil |
| Icono que se actualiza | Versión/lastUpdateTime o invalidación paquete; variantes dinámicas requieren política específica | Invalidar/warm nueva semilla | En **siguiente sesión**, sin cambio tardío | Update package, rollback versión y reinicio |
| Usuario cambia modo día/noche | `isDark`/preferencia independiente | **Misma** semilla local, distinto esquema | En próxima frontera de sesión / transición del sistema, nunca durante composición | Config changes y `SettingsActivity.forceNight` |
| Usuario cambia de familia | Preferencia de día/noche | Semilla de la app cacheada; nuevo `schemeType` | Paleta nueva sin cambiar UI/IDs | 10 → 10 y volver |
| Usuario desactiva By App | Fuente `SYSTEM_WALLPAPER` | `DynamicColorSeedProvider.snapshot` | Comportamiento original exacto | Sin worker ni rastreo de app posterior |
| Primera visita (cold miss) | Caché sin entrada | Sistema/wallpaper provisional, warm en background | **Sin recolor a mitad de escritura**; próximo ingreso usa app | Promesa UX explícita |
| A→B→A veloz / DeX multiventana | Epoch de sesión por paquete/perfil | Cada snapshot corresponde a su editor | Resultados obsoletos no alteran ventana actual | Race tests y pruebas físicas |
| Campo contraseña / protegido | `EditorInfo.inputType` / lógica de IME preexistente | No se derivan colores del texto; paquete solo cuando seguro y disponible | Protecciones originales intactas | Zero text access; composición no modificada |
| Direct Boot / perfil bloqueado | PackageManager no ofrece recurso | Sistema/acento/fallback existente | Teclado nunca depende de iconos para aparecer | Samsung S25 tras reboot sin unlock |
| API 21–22 | No confiar ciegamente en `packageName` (verificación UID inicia API 23) | Fallback o validación más estricta en fase autorizada | Sin permisos adicionales | Pruebas de compatibilidad/emulador |

## 4. Matriz de contraste, accesibilidad y regresión

Referencias reales: `MonetColorMapper.map` produce `ColorType` de todas las piezas; `readable()` prioriza contraste 4.5:1 y `isGoodContrast`, pero **no demuestra automáticamente** legibilidad para toda superposición o imagen de fondo. `DynamicColorSchemesTest.kt` cubre 9 pares críticos; ampliar.

| Zona | Roles a confrontar | Gate propuesto |
|---|---|---|
| Tecla ordinaria | `KEY_TEXT` vs `KEY_BACKGROUND` | ≥4.5:1 |
| Tecla funcional | `FUNCTIONAL_KEY_TEXT` vs `FUNCTIONAL_KEY_BACKGROUND` | ≥4.5:1 |
| Barra espaciadora y hints | `SPACE_BAR_TEXT` vs `SPACE_BAR_BACKGROUND`; `KEY_HINT_TEXT` vs `KEY_BACKGROUND` | ≥4.5:1 si texto legible requerido; documentar tratamiento de hints atenuados |
| Tecla de acción y popup | `ACTION_KEY_ICON` vs `ACTION_KEY_BACKGROUND`; `POPUP_KEY_TEXT` vs `POPUP_KEYS_BACKGROUND` | Texto ≥4.5:1; iconos funcionales ≥3:1 |
| Barra de sugerencias | `SUGGESTION_AUTO_CORRECT`, `SUGGESTION_TYPED_WORD`, `SUGGESTION_VALID_WORD`, `SUGGESTED_WORD` vs `STRIP_BACKGROUND` | ≥4.5:1 para texto visible |
| Toolbar | `TOOL_BAR_KEY`, `TOOL_BAR_EXPAND_KEY` vs fondos en estado habilitado/deshabilitado | Iconos significativos ≥3:1; texto ≥4.5:1 |
| Emoji / clipboard | `EMOJI_SEARCH_TEXT`, `CLIPBOARD_SUGGESTION_ICON` y labels vs fondos | Según texto/icono 4.5/3.0 |
| Más sugerencias | `MORE_SUGGESTIONS_HINT`, backgrounds y pressed states | Prueba por estado, no solo mapa sin interacción |
| Fondos de foto elegidos por usuario | Todos los textos/chips encima de la imagen | Si no puede garantizarse contraste, **preferir sistema/fallback/opacidad sin cambiar geometría** |
| Accesibilidad | Alto contraste, tamaño/fuentes instaladas, TalkBack, touch exploration | Sin pérdida de distinción de estados ni retrocesos de UX |
| Identidad visual | Recursos de icono, font, tamaño, gap, radio y distribución | **Cero diffs** fuera del sistema de colores y etiquetas de nueva preferencia |

**Método mínimo:** JUnit de pure functions para extracción (synthetic pixels), Robolectric para iconos Drawable/PackageManager/temas, test del mapper en 120 combinaciones por semilla, integración IME para sesión y regresión visual en Galaxy S25. **No se ejecutó nada para R2.5.**

## 5. Dispositivos y sesiones de aceptación

| Entorno | Escenarios imprescindibles | Qué no está demostrado |
|---|---|---|
| Galaxy S25 + One UI, claro/oscuro | A/B/A, iconos adaptativos, caché, 10 familias, escritura continua, campo protegido | Acceso real a iconos y ausencia de flash |
| Samsung DeX | Ventana redimensionada, split-screen, cambio frecuente de host, teclado físico/táctil | Frontera de sesión visible sin relayout |
| Android sin icono launcher | Editor sin `CATEGORY_LAUNCHER` o webview embebido | Recurso accesible y representación de host |
| Perfil empresarial/Secure Folder | UID/perfil diferente, paquete oculto, bloqueo del perfil | Visibilidad y uso seguro del recurso |
| Emuladores API 21, 23, 29, 30, 35, 37 cuando disponibles | `EditorInfo.packageName`, visibilidad de paquetes, fallbacks, themed icons por API | Compatibilidad efectiva minSdk 21 y target 37 |
| Reinicio y cambio de paquete | Cold start, direct boot, actualización de app/launcher | Invalidación LRU/tiempo y sin mutación tardía |

## 6. Puertas finales

**PASS para implementar después de aprobación** requiere: fuente opt-in con rollback instantáneo; obtención de iconos sin permisos nuevos en apps accesibles; 20/20 combinaciones principales; 120/120 permutaciones por semilla del mapper; contraste; caché acotada; cero recoloreados tardíos, `hideWindow/showWindow` disparados por worker, red o consultas al texto; UX estable en S25/DeX; suites de Fase 2E sin regresión.

**Fallback aceptado:** sin icono útil, usar semilla sistema/wallpaper de la **misma** familia seleccionada. No cambiar silenciosamente a Tonal Spot o a otro tema. Si un tema diurno/nocturno es el sistema heredado o estático, conservar su comportamiento y no aplicar By App.

**NO-GO / rollback:** cualquier necesidad de permisos invasivos o de pantalla; recreación visible que interrumpe escritura; colores ilegibles en condiciones habituales; filtración de identificador del editor al exterior; regresión de motor híbrido o pérdida de compatibilidad de preferencias. El fallo reportado de `XLinkTest.knownDictionaries` en Codeberg es **dependencia externa del pipeline**, no problema a reparar dentro de R2.5.

### Fuentes consultadas

- [DynamicSchemeProvider.kt](https://github.com/Jorgeprdz/HeliBoard/blob/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard/latin/common/dynamic/DynamicSchemeProvider.kt)
- [MonetKeyboardColors.kt](https://github.com/Jorgeprdz/HeliBoard/blob/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard/latin/common/dynamic/MonetKeyboardColors.kt)
- [DynamicColorSchemesTest.kt](https://github.com/Jorgeprdz/HeliBoard/blob/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/test/java/helium314/keyboard/latin/common/dynamic/DynamicColorSchemesTest.kt)
- [Android: visibilidad automática de apps para IME](https://developer.android.com/training/package-visibility/automatic)
- [Android: EditorInfo](https://developer.android.com/reference/android/view/inputmethod/EditorInfo)
- [MCU: extracción de color](https://github.com/material-foundation/material-color-utilities/blob/main/dev_guide/extracting_colors.md)
