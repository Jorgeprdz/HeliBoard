# TACTIC KEYBOARD — R2.5 · DYNAMIC COLORS BY APP
## SCOPE.md — auditoría técnica de factibilidad (solo documentación)

**Fecha:** 2026-10-16  
**Repositorio:** `Jorgeprdz/HeliBoard`  
**Rama auditada:** `feat/tactic-r243-hybrid-writing`  
**HEAD de referencia leído:** `5e58323713eaf7fbc463fec7bed8a1483780556d`  
**PR:** [#8](https://github.com/Jorgeprdz/HeliBoard/pull/8), abierto y **Draft** al momento de la inspección.  
**Evidencia:** inspección estática de fuentes de esa rama y documentación oficial Android/Material. **Sin compilaciones, cambios de código, pruebas instrumentadas ni mediciones de rendimiento.**  
**Dictamen:** **GO CON CONDICIONES**. No equivale a autorización de implementación.

## 1. Resumen ejecutivo (menos de 800 palabras)

Es técnicamente viable reutilizar las diez familias existentes de Material Color Utilities con una semilla procedente del icono de la aplicación anfitriona. `DynamicSchemeProvider.get(seed, type, isDark)` ya es transversal y genera Neutral, Monochrome, Tonal Spot, Vibrant, Rainbow, Expressive, Fidelity, Content, Fruit Salad y CMF mediante `SPEC_2026`. `MonetKeyboardColors` ya admite `seedOverride`; `MonetColorMapper` traduce los roles a colores del teclado. Por tanto, **no se requieren diez motores nuevos ni cambios de geometría**.

La integración aún **no existe**. La semilla actual se obtiene del wallpaper (asíncrono) o del acento del sistema, con fallback morado. `seedOverride` desactiva la comparación posterior del fingerprint del proveedor: pasarle una semilla de app sin rediseñar el seguimiento de sesiones no resuelve por sí solo el cambio entre apps. `DynamicSchemeProvider` usa un `HashMap` sin límite. Y `KeyboardSwitcher.setThemeNeedsReload()`, cuando el IME está visible, ejecuta `hideWindow()/showWindow()`; no debe invocarse como efecto de la extracción tardía. La recarga por cambio de colores puede reconstruir la vista mediante `setInputView(onCreateInputView(...))`. Son los principales riesgos de parpadeo y latencia.

`EditorInfo.packageName` es una señal legítima de la aplicación que aporta el editor (Android comprueba su consistencia con el UID desde API 23); el repositorio ya la usa para recordar subtipos por aplicación. Android hace automáticamente visibles al IME los paquetes que reciben su entrada. Además, el manifiesto existente declara `<queries>` para launcher y otros IME. Esto favorece la estrategia `EditorInfo.packageName -> PackageManager -> icono` **sin nuevos permisos**, pero la lectura de iconos no está demostrada en el Galaxy S25, apps sin actividad launcher, perfiles laborales o Secure Folder. No usar `QUERY_ALL_PACKAGES`, Accessibility, MediaProjection ni captura de pantalla.

Se propone un `AppColorSeedProvider` local con caché LRU y extracción asíncrona, un `ColorSourceResolver` que seleccione entre `SYSTEM_WALLPAPER` y `BY_APP`, y una instantánea inmutable por sesión de entrada. Si la semilla de app ya está en caché, se fija **antes** de `loadSettings()` y del primer render; si no está, se muestra la paleta actual y se prepara la semilla **solo para la siguiente apertura**, sin recolorearla mientras se escribe. El cambio de app invalida la sesión mediante paquete/generación/familia/modo claro-oscuro, sin usar una recarga de ventana para cambios asíncronos.

Las diez familias, los temas diurno/nocturno, los iconos y las protecciones del IME se conservan. El tema heredado `dynamic` sigue usando `DynamicColors` del sistema; By App se define para las diez familias locales y no transforma temas estáticos. La configuración transversal es una fuente de color global, por defecto sistema/wallpaper, manteniendo **dos selecciones independientes de familia** para día y noche; dos fuentes distintas por horario quedan diferidas.

Antes de cualquier desarrollo hay que superar puertas de validación: demostrar obtención de icono sin permisos extra en dispositivos reales, asegurar contrastes completos (también fondos de usuario), evitar recreaciones/flash y asegurar que la rama de autocorrección no cambia. Se recomienda **GO CON CONDICIONES**, con posibilidad de **NO-GO para promesa universal** si la visibilidad del icono falla en determinados perfiles. La Fase 2E y `XLinkTest.knownDictionaries` quedan fuera de alcance.

## 2. Estado actual: hechos verificados en código

| Componente | Comportamiento observado | Implicación |
|---|---|---|
| `DynamicColorSeedProvider.kt` | `snapshot(context,isDark)`: wallpaper por `WallpaperManager` API 27+, lectura en `HeliBoard-DynamicSeed`; acento del sistema, fallback `#FF6750A4`. Mezcla `seed`, generación, firma de acentos y modo oscuro en fingerprint. Listener de wallpaper limpia esquema y llama a `setThemeNeedsReload()`. | Reutilizar fallback existente; **no** emplear listener de wallpaper para actualizaciones tardías de la app. |
| `DynamicSchemeProvider.kt` | `get(seed,type,isDark)` usa `Hct.fromInt`, `ColorSpec.SPEC_2026`, diez clases `Scheme*`, `contrastLevel=0.0`. `HashMap<CacheKey,KeyboardDynamicScheme>` sincronizado, **sin límite**. | Permite una única semilla externa para diez familias; limitar tamaño de caché si hay muchas apps. |
| `MonetKeyboardColors.kt` | `seedOverride: Int?` opcional; sin override consulta proveedor y compara `fingerprint`; con override crea snapshot con `seed.toLong()` y `tracksSeedProvider=false`. | **Insuficiente por sí solo** para detectar cambios de paquete: introducir snapshot/fingerprint de sesión explícito o invalidación gobernada externamente. |
| `MonetColorMapper` | Mapea cada `ColorType` y elige candidatos de texto mediante contraste; `check(colors.size == ColorType.entries.size)`. | Conservar el mapper y añadir matriz de contraste ampliada, incluidos fondos/imágenes. |
| `KeyboardTheme.kt` | `getColorsForCurrentTheme()` elige día/noche y `PREF_THEME_COLORS(_NIGHT)`; diez familias locales crean `MonetKeyboardColors`. `THEME_DYNAMIC_SYSTEM = "dynamic"` usa `DynamicColors` Android S+ y no es familia local. | Fuente By App afecta solo a familias locales; no crea tema nuevo. |
| `KeyboardSwitcher.java` | `updateKeyboardTheme()` puede rehacer `SettingsValues` y `setInputView(onCreateInputView(...))`; `setThemeNeedsReload()` oculta/muestra ventana si visible. | No emitir invalidaciones desde una extracción tardía; programar cambio exclusivamente en frontera de sesión. |
| `LatinIME.java` | `onStartInputInternal()` usa `editorInfo.packageName` para subtipo por app. `onStartInputViewInternal()` llama `loadSettings()` y luego `switcher.updateKeyboardTheme()`; puede omitir `loadSettings()` en restart. | Fijar contexto de sesión antes de materializar `SettingsValues.mColors`; detectar cambio de paquete incluso si `restarting=true`. |
| `SettingsValues.java` | Inicializa `mColors = KeyboardTheme.getColorsForCurrentTheme(context)`. | El selector necesita consultar el snapshot vigente antes de construir `mColors`. |
| `AppearanceScreen.kt` / `ColorThemePickerDialog.kt` | Preferencias diurnas/nocturnas independientes; picker mezcla temas locales, heredado y personalizados. | Una opción transversal junto a colores, no diez switches ni diez temas. |
| `Settings.java` / `Defaults.kt` | Claves `PREF_THEME_COLORS`, `PREF_THEME_COLORS_NIGHT`, `PREF_THEME_DAY_NIGHT`; valores por defecto Light/Darker. | Preferencia nueva opcional con default `SYSTEM_WALLPAPER`, sin migración de valores existentes. |
| `AndroidManifest.xml` / `app/build.gradle.kts` | `minSdk=21`, `targetSdk=37`; IME `directBootAware`; `<queries>` para launcher y otros IMEs. | No introducir permisos ni cambios de manifest en la primera implementación. |
| `DynamicColorSchemesTest.kt` | Robolectric SDK 35: diez variantes día/noche, caché, determinismo, mapeo completo, pares críticos 4.5:1. | Ampliar cobertura, no afirmar que ya se validó By App. |

**Fuentes de código (anclaje de la auditoría):** [dynamic](https://github.com/Jorgeprdz/HeliBoard/tree/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard/latin/common/dynamic), [KeyboardTheme](https://github.com/Jorgeprdz/HeliBoard/blob/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard/keyboard/KeyboardTheme.kt), [KeyboardSwitcher](https://github.com/Jorgeprdz/HeliBoard/blob/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard/keyboard/KeyboardSwitcher.java), [LatinIME](https://github.com/Jorgeprdz/HeliBoard/blob/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard/latin/LatinIME.java).

## 3. Arquitectura propuesta (NO implementada)

```text
EditorInfo.packageName ──> AppSessionColorCoordinator ──┬──> ColorSourceResolver
       sesión/generación + modo                          │        │
                                                         │        ├─ SYSTEM_WALLPAPER
                                                         │        │    DynamicColorSeedProvider.snapshot()
                                                         │        └─ BY_APP
                                                         │             AppColorSeedProvider
                                                         │             ├─ LRU (package,user/profile,version,lastUpdateTime)
                                                         │             └─ worker: PackageManager → Drawable → raster → Celebi → Score
                                                         │
                                                         └──> immutable SessionColorSnapshot
                                                                  seed + source + fingerprint + status
                                                                       ↓
                                          KeyboardTheme/SettingsValues → MonetKeyboardColors
                                                                       ↓
                                                  DynamicSchemeProvider(seed,type,isDark)
                                                                       ↓
                                                     MonetColorMapper → Colors / teclado
```

Interfaces sugeridas **conceptuales**:

- `ColorSource = SYSTEM_WALLPAPER | BY_APP`. Desactivación total con valor por defecto sistema.
- `AppColorSeedProvider.peek(packageId, profile): SeedResult?` **solo caché**, no bloqueo del hilo UI.
- `AppColorSeedProvider.warmAsync(packageId, profile, token)`: solo metadatos de paquete e icono, worker dedicado, límites y cancelación lógica.
- `SessionColorSnapshot(seed, source, packageToken, epoch, isDark, schemeType, fallbackReason)`: inmutable, privada al proceso; no persistir historial de apps por defecto.
- `AppSessionColorCoordinator.onEditorStarted(EditorInfo?)`: registra cambio de host, congela semilla para esa sesión; `onEditorFinished` invalida completaciones obsoletas. Debe vivir en el flujo existente de `LatinIME`, antes de `loadSettings()`.
- `KeyboardTheme.getThemeColors` consume snapshot desde el resolutor; **no** consultar PackageManager directamente allí. `MonetKeyboardColors` recibe seed+fingerprint o se construye tras una invalidación sincrónica de sesión; el `seedOverride` actual admite la semilla pero no resuelve el fingerprint.
- Prohibido depender de `setThemeNeedsReload()` cuando llegue un icono en segundo plano; el resultado se aplica en la **próxima sesión**. Evitar `setInputView` si paleta/fingerprint no cambiaron; medir recreaciones necesarias en cambios de app.

**Cuestión a resolver en implementación:** si puede introducirse un cambio de `SettingsValues.mColors` en frontera de entrada sin provocar ocultación/mostrar ventana o interrupción de composición. No afirmar `zero recreation` como verificado: el mecanismo actual de `KeyboardSwitcher` reconstruye vistas al detectar cambio.

## 4. Selección de semilla por icono

**Ruta principal:** `EditorInfo.packageName` (API 23+ con verificación sistema) → `PackageManager` del contexto/perfil accesible → drawable de la aplicación → render **fuera del hilo principal** en bitmap pequeño (propuesta 64×64; evaluar 96/128) → tratamiento de alfa/fondo → `QuantizerCelebi.quantize(pixels,maxColors)` → `Score.score(populations,...)` → filtro cromático y contraste → semilla ARGB.

**Material ya incluido en repo:** `com.drdisagree.materialcolorutilities.quantize.QuantizerCelebi` devuelve `Map<Int,Int>`; `score.Score` acepta ese mapa; `hct.Hct` permite evaluar croma/tono. `Score.score` devuelve un azul de fallback aun sin candidatos: **el proveedor debe diferenciar semilla válida de fallback interno** para no asignar a todas las apps monocromas un falso “azul de marca”.

**Jerarquía de iconos:** (a) adaptive icon: evaluar foreground + background y muestreo del elemento distintivo, sin sobreponderar un plano blanco/negro; (b) drawable aplanado original sin tintar; (c) themed/monochrome: no deducir color de marca a partir del tinte actual del sistema; (d) iconos animados/dinámicos: normalizar frame y versionar caché. Priorizar un color representativo cromático, no necesariamente el pixel dominante. Evitar extracción del fondo de la app, pues no se observa sin invadir pantalla.

**Filtros propuestos:** descartar alfa muy bajo, grises/negros/blancos dominantes y clusters con baja croma; usar umbrales configurables sujetos a prueba (no asumir que `Score` basta); evitar cambios de tonalidad en cada apertura por animación del icono; si no hay candidato, devolver `null` y usar el fallback actual.

## 5. Android, seguridad y alternativas

- **Identificación:** `EditorInfo.packageName` representa al dueño del editor. Android verifica su relación con UID desde API 23; API 21-22 requieren cautela o fallback; no usar nombre de ventana ni servicios de accesibilidad. Ref.: https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- **Visibilidad de paquetes:** Android 11+ filtra `PackageManager`, pero hace **automáticamente visibles** al IME las apps que reciben su texto. El manifest ya contiene `<queries>` con `ACTION_MAIN/CATEGORY_LAUNCHER`. Esto respalda la factibilidad, no prueba acceso universal a iconos de apps empresariales, sin launcher o de otro perfil. Ref.: https://developer.android.com/training/package-visibility/automatic ; https://developer.android.com/training/package-visibility
- **Perfiles/One UI:** `PackageManager` del usuario activo podría no acceder a recursos de un perfil laboral/Knox/Secure Folder distinto; no solicitar privilegios de cruce de perfiles. Registrar **solo motivo técnico agregado**, no paquete ni campo.
- **Direct Boot / multipantalla / DeX:** si el usuario aún no desbloqueó o el IME está en una ventana DeX, no depender del launcher en primer render. El modo claro/oscuro se resuelve por `KeyboardTheme`, no por el color oscuro del icono.
- **Alternativas:** icono local (prioritaria, fiel a app y sin pantalla); color estándar de app/manifiesto (no existe API general confiable de “brand seed” y exige recursos ajenos, secundaria); color elegido manualmente por usuario (privado, pero no automático; posible futuro), sistema/wallpaper (fallback robusto); extraer pantalla, AccessibilityService, MediaProjection, capturas o red/Gemini (**prohibidos**).
- **Compatibilidad real:** un icono no representa necesariamente el **tema actual dentro de la app**; por diseño no se promete coincidencia pixel-perfect con su UI.

## 6. UX, caché, riesgos y objetivos NO MEDIDOS

- **Fuente única** `SYSTEM_WALLPAPER` (default) / `BY_APP`, aplicada solo si familia diurna/nocturna seleccionada es una de las diez locales. Se mantienen independientes `PREF_THEME_COLORS` y `PREF_THEME_COLORS_NIGHT`. **No** separar origen por día/noche en R2.5 inicial: añadir dos fuentes aumenta estados y validación sin necesidad demostrada.
- **Cold miss:** usar snapshot de sistema de manera inmediata; precalcular icono tras mostrar teclado, registrar solo la semilla en caché y aplicar **en siguiente inicio de input**; así no existe flash a mitad de escritura. La primera apertura **puede no reflejar todavía el icono**: limitación UX explícita.
- **Warm hit:** congelar semilla de app por sesión; ninguna tarea antigua podrá publicar colores de otra app. Token monotónico/generación + chequeo de package/profile antes de entregar resultado.
- **LRU preliminar:** 64 semillas de apps; invalidación por `lastUpdateTime`/versión (consultas fuera de UI y no en cada pulsación), broadcasts de paquete accesibles, cambio de perfil/usuario, cambio de icono y limpieza. Almacén **en memoria**, no inventario persistido por defecto. Limitar además caché de esquemas (hoy es ilimitada; p. ej. 128-256 combinaciones); estas cifras requieren perf gate.
- **Objetivos para probar, NO resultados:** p95 hit de caché <=2 ms en el tramo del IME; p95 extracción en worker <=120 ms a 64–128 px, con límite/tarea y cero acceso a disco en tecla; memoria incremental de cachés <=2 MiB; 0 llamadas de extracción en hilo principal; 0 `hideWindow()/showWindow()` por finalización de tarea; 0 aplicaciones tardías tras cambiar de app; 0 cambios de color durante composición activa. Medir con trazas locales anónimas y pruebas físicas.
- **Contraste:** `4.5:1` en texto pequeño de teclas, hints importantes, sugerencias, toolbar y acciones; iconos significativos `3:1` mínimo (preferir 4.5 cuando viable); fondos de imagen pueden impedir garantía -> priorizar legibilidad o fallback. No alterar tipografía, tamaño, layout, iconos oficiales ni animaciones.
- **Riesgo principal:** reconstrucción de `InputView` para cambio de sesión (medio-alto). **Mitigación:** cambio atómico antes de primer frame visible, benchmark DeX/S25, y rollback si hay flash o pérdida de selección/composición.
- **Riesgos secundarios:** acceso a icono de perfiles (medio), resultados cromáticos no representativos (medio), memoria por combinatoria (medio), invalidación de icono dinámico (medio). Cada uno tiene fallback a wallpaper/sistema.
- **Privacidad:** no leer texto, sugerencias, conversaciones ni UI de host para derivar la paleta; solo identificador del editor temporal para resolver recurso local; no servicios externos, permiso nuevo ni telemetría de paquete. Respetar flags de entradas sensibles y los mecanismos existentes de incognito/autocorrección.

## 7. Puertas GO / NO-GO y aislamiento

**GO solo si** se verifica en pruebas posteriores: (G1) acceso a iconos en apps normales sin cambios de permisos, incluyendo perfil no accesible con fallback; (G2) diez familias × claro/oscuro × contraste; (G3) aplicación en frontera sin parpadeos, sin reload tardío, sin interrupción de escritura; (G4) memoria acotada y latencia medida; (G5) diffs limitados a componentes de tema/preferencias/test; (G6) PR #8 permanece Draft y Fase 2E intacta. Si G1 falla para todas las apps objetivo o G3 no puede cumplirse sin romper IME, **NO-GO** de implementación. Casos aislados de iconos inaccesibles habilitan fallback, no promesa universal.

**Fuera del scope:** motor híbrido, diccionarios, autocorrección, backspace, Gemini, Qwen, branding, package ID, firma, build APK, release y merge. **`XLinkTest.knownDictionaries` (Codeberg):** reportado como falla externa/dependencia de pipeline, no diagnosticado ni reparado aquí. No debe bloquear la publicación de *estos documentos* ni utilizarse para declarar funcionalidad probada.

### Referencias públicas

- Android package visibility (actualizado 2026-10-01): https://developer.android.com/training/package-visibility
- Visibilidad automática para IME: https://developer.android.com/training/package-visibility/automatic
- `EditorInfo.packageName`: https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- Adaptive icons: https://source.android.com/docs/core/display/adaptive-icons
- Material Color Utilities, extracción: https://github.com/material-foundation/material-color-utilities/blob/main/dev_guide/extracting_colors.md
