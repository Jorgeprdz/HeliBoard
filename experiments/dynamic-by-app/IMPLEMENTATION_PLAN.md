# TACTIC KEYBOARD — R2.5 · DYNAMIC COLORS BY APP
## IMPLEMENTATION_PLAN.md — plan de ejecución condicionado, NO autorizado

**Fecha:** 2026-10-16 · **rama:** `feat/tactic-r243-hybrid-writing` · **PR #8:** conservar Draft.  
**Base de la revisión estática:** `5e58323713eaf7fbc463fec7bed8a1483780556d`.  
**Dictamen:** **GO CON CONDICIONES**. Este archivo solo propone cambios. **Ninguno fue implementado, compilado ni probado en dispositivo.**

## 0. Contrato inmutable

- Solo color semilla/paleta de las **diez familias locales**. Sin reflow, cambio de tamaño, altura, fuentes, tipografía, iconos, geometría, animaciones, distribución de teclas, barra o acciones.
- Sin permisos nuevos, AccessibilityService, screenshots/captura de UI, MediaProjection, redes ni IA; mantener por defecto `SYSTEM_WALLPAPER`.
- Mantener en forma literal IDs de temas actuales, preferencias de familia independientes (`PREF_THEME_COLORS` y `PREF_THEME_COLORS_NIGHT`), tema heredado `dynamic`, temas estáticos y personalizaciones.
- No tocar Fase 2E: `latin/ai/**`, diccionarios, backspace, autocorrección, Gemini/Qwen, branding, firma y package ID.
- **Prohibido usar** `KeyboardSwitcher.setThemeNeedsReload()` como respuesta a un resultado asíncrono de icono: hoy puede `hideWindow()/showWindow()`. No usar reconstrucción de vista durante composición.
- Autorización separada del Owner tras aprobar scope, antes de modificar código. No usar este plan como autorización tácita.

## 1. Fases mínimas y orden

| Fase propuesta | Cambios acotados | Gate observable | Resultado esperado |
|---|---|---|---|
| **0 — alcance y evidencia (read-only)** | Confirmar HEAD/PR y diagrama de flujo del snapshot; enumerar modos (sistema/local/estático); documentar límites API 21/37 y perfiles. | Revisar tres documentos; consentimiento explícito para implementar. | Aprobación o STOP; nada ejecutable. |
| **1 — extractor puro y contrato de fuentes** | Proponer `ColorSource`, `AppColorSeedProvider` en `common/dynamic/` y un extractor `Drawable → ARGB → QuantizerCelebi → Score`; sin usar UI/captura. Test fixtures de adaptive, bitmap, vector, mono y transparente. | Unit tests de semilla estable, filtrado de grises, errores/fallback, ninguna red y ninguna operación bloqueante en UI. | Proveedor testeable y reutilizable para todas las familias. |
| **2 — coordinación por sesión + caché acotada** | `AppSessionColorCoordinator` con token, perfil, package id y snapshot inmutable; LRU semillas; versionado `PackageInfo.lastUpdateTime`/versionCode cuando accesible; caché acotada en `DynamicSchemeProvider`. | Cambio rápido A→B→A no recibe resultado tardío de B; sin icono retorna fuente existente; stale data invalidada. | Consistencia/sin flash tardío. |
| **3 — conexión con el IME** | Introducir token en `LatinIME.onStartInputInternal` (antes de `onStartInputViewInternal/loadSettings`); hacer disponible snapshot para `KeyboardTheme`/materialización de `SettingsValues.mColors`; ampliar `MonetKeyboardColors` con fingerprint de sesión o invalidación explícita; conservar `MonetColorMapper`. | No recarga de ventana causada por worker; alternancia de app y day/night correcta; sin composición interrumpida. | Esquema de app en **primer render con hit** y fallback de primera visita. |
| **4 — preferencia transversal** | Una lista `Fuente de color dinámico: Sistema/Wallpaper · By App` en apariencia, solo visible/activa con familias locales (o mostrar explicando cuándo se aplica). Usar nueva clave + default sistema; no diez entradas nuevas de tema. | Preferencia OFF restaura comportamiento actual exacto; backup/restauración compatibles; modo claro y oscuro conservan familia. | UX opt-in y reversible. |
| **5 — validación cerrada + revisión** | JUnit, Robolectric y sesiones manuales S25 con One UI/DeX, perfiles, apps e iconos. Recoger duración/UI relayout y ratios reales. | Gates de sección 4; diff sin elementos prohibidos; PR sigue Draft; revisión humana específica antes de APK. | Recomendación para fase posterior, **sin build/release automático**. |

**Dependencias:** fases 1–2 no requieren cambios visuales; fase 3 solo si las pruebas de acceso al icono y de seguridad de sesión pasan. No mezclar esta implementación futura con correcciones de `XLinkTest.knownDictionaries` ni regresiones de Fase 2E.

## 2. Archivos que *requerirían* cambios (hipótesis, no diff)

| Ruta | Tipo | Intervención mínima esperada |
|---|---|---|
| `app/src/main/java/helium314/keyboard/latin/common/dynamic/AppColorSeedProvider.kt` | **Nuevo propuesto** | Obtener y puntuar icono local, `peek` y `warmAsync`, fallbacks, caché LRU, invalidación por paquete. |
| `.../common/dynamic/AppSessionColorCoordinator.kt` | **Nuevo propuesto** | Sesión inmutable, epoch/token, cancelación lógica, determinismo al cambiar de app. Puede unificarse con proveedor si reduce complejidad. |
| `.../common/dynamic/DynamicColorSeedProvider.kt` | Existente | Mantener interfaz/política actual como fallback; si se necesita, exponer resolver sin cambiar orden wallpaper → system → interno. **Preferir sin cambios.** |
| `.../common/dynamic/DynamicSchemeProvider.kt` | Existente | Conservar los diez `Scheme*`; **solo** acotar `HashMap` si evidencia exige límite. No bifurcar por app. |
| `.../common/dynamic/MonetKeyboardColors.kt` | Existente | Inyectar snapshot de sesión semilla+fingerprint o mecanismo de comparación controlada; preservar `MonetColorMapper`. Con `seedOverride` solo, hoy `haveColorsChanged=false`. |
| `app/src/main/java/helium314/keyboard/keyboard/KeyboardTheme.kt` | Existente | Leer fuente seleccionada y snapshot antes de crear `MonetKeyboardColors` para solo familias locales. No migrar el tema heredado `dynamic`. |
| `app/src/main/java/helium314/keyboard/latin/LatinIME.java` | Existente | Contexto de editor/host en inicio de entrada antes de `loadSettings`; invalidar al finalizar; no tocar composición/autocorrección. |
| `app/src/main/java/helium314/keyboard/keyboard/KeyboardSwitcher.java` | Existente, **preferible sin cambios** | Auditar si la recarga en frontera de sesión es atómica y sin ventana oculta. Intervenir solo si el gate de UX demuestra necesidad; evitar tocar core sin pruebas. |
| `app/src/main/java/helium314/keyboard/latin/settings/Settings.java` | Existente | Nueva constante `PREF_DYNAMIC_COLOR_SOURCE`, independiente de familia día/noche. |
| `app/src/main/java/helium314/keyboard/latin/settings/Defaults.kt` | Existente | Default `SYSTEM_WALLPAPER`. |
| `app/src/main/java/helium314/keyboard/settings/screens/AppearanceScreen.kt` | Existente | Una preferencia transversal junto a colores; no cambiar apariencia general de la pantalla. |
| `app/src/main/res/values/strings.xml` y traducciones vigentes | Existentes | Etiqueta/descripción y opciones accesibles/localizadas; sin branding. La ubicación concreta y recursos a traducir se verifican antes de tocar archivos. |
| `app/src/test/java/helium314/keyboard/latin/common/dynamic/DynamicColorSchemesTest.kt` | Existente | Conservar aserciones actuales; sumar corpus amplio de semillas, 20 familias/modos y controles del mapa. |
| `app/src/test/java/helium314/keyboard/latin/common/dynamic/AppColorSeedProviderTest.kt` | **Nuevo propuesto** | Robolectric + doubles de iconos, permisos/ausencias, extracción/caché/invalidation. |
| `app/src/test/java/helium314/keyboard/latin/common/dynamic/AppSessionColorCoordinatorTest.kt` | **Nuevo propuesto** | Tokens stale, composición activa, restart, cambio app/perfil/día-noche, órdenes concurrentes. |

**No tocar:** `AndroidManifest.xml` (las consultas launcher/IME ya existen), Gradle, `latin/ai/**`, recursos de launcher/branding, archivos de firma o instalación. Si el acceso a iconos necesita permisos o declaración de visibilidad adicional, escalar al Owner: **STOP, reevaluar** en vez de ampliar silenciosamente el alcance.

## 3. Especificación de diseño de integración

1. Entrada: `EditorInfo.packageName` validado por sistema desde API 23; nulo/vacío/desconocido significa fallback. No confiar en UID-paquete en APIs 21–22 sin tratamiento especial. `onStartInputInternal` ya conoce `packageName` y maneja subtipos por app.
2. Separación: `ColorSourceResolver` no lee iconos; retorna snapshot de caché o sistema. `AppColorSeedProvider` **no** conoce temas, UI ni texto; solo `PackageManager`, recursos y bytes de icono.
3. `peek` sincronizado para semilla cacheada y *sin I/O*; primera extracción se agenda en worker y no muta colores de sesión visible. Resultado obsoleto se guarda bajo clave correcta si sigue siendo válido, **nunca** se aplica a la sesión de otra app.
4. Clave LRU: `(packageName, user/profile scope si accesible, versionCode/lastUpdateTime, algoritmoVersion)`. Separar versión de icono (puede variar sin paquete actualizado) y limpiar en eventos de paquete/theme si detectables; cuando no sea comprobable, TTL conservador. No guardar historial persistente ni paquetes en logs.
5. Pasos de extracción: `getApplicationIcon/getApplicationInfo` o equivalente sin permiso nuevo → normalización de `AdaptiveIconDrawable` / vector / bitmap → raster 64–128px fuera de main → ignorar transparente/tintes del sistema → `QuantizerCelebi.quantize` → `Score.score` → validar saturación/tono/contraste y distinguir fallback de Score → `SeedResult.Valid` o `Unavailable(reason)`.
6. La selección **de familia** se resuelve a partir de `PREF_THEME_COLORS` / `PREF_THEME_COLORS_NIGHT` y **no cambia por app**; un `seed` alimenta el `DynamicSchemeProvider` existente. El modo `THEME_DYNAMIC_SYSTEM` continúa siendo `DynamicColors` y temas estáticos no cambian.
7. `SettingsValues.mColors` se construye en `loadSettings()`: el snapshot activo **debe existir antes**. Para `restarting=true`, decidir explícitamente si el paquete realmente cambió: misma sesión = no cambio; otro paquete = nueva instantánea y carga de colores.
8. Reconstrucción: `KeyboardSwitcher.updateKeyboardTheme()` recrea `InputView` cuando detecta tema cambiado. Objetivo: no recrear si fingerprint igual; si distinto, hacerlo solo en frontera antes de mostrar, sin invocar hide/show; trazar número de recreaciones y pérdida de composición. Si no puede hacerse sin flash, STOP.
9. Entrada protegida: By App no modifica `inputType`, `mInputAttributes` ni las reglas existentes de incognito, textos o sugerencias. Nunca usar los caracteres ingresados para derivar color. No exponer packageId en telemetría.

## 4. Gates cuantitativos propuestos (no son mediciones)

| Gate | Prueba | Criterio de aceptación inicial |
|---|---|---|
| **P0 privacidad** | Revisión estática del diff/permisos/red + pruebas de icon lookup | 0 permisos nuevos; 0 capturas/pantalla, accesibilidad, red, texto de usuario o Gemini/Qwen; 0 logs de paquete/entrada. |
| **P1 compatibilidad** | 10 familias × 2 modos × 3 estilos × bordes sí/no (matriz extendida del test existente) | Todos los `ColorType` presentes, semilla A/B determinista, 0 excepciones. |
| **P2 contraste** | Test de cada par texto-fondo e icono-fondo real: key, functional, space, action, suggestion strip, toolbar, popup, hints, emoji, clipboard, navigation; fondos con foto | Texto legible ≥4.5:1 para pares obligatorios, iconos funcionales ≥3:1; si imagen/rol rompe legibilidad, fallback; documentar roles excepcionados. |
| **P3 sesión** | Robolectric: A→B→A, host null, restart, direct boot, bloqueo de perfil; promesas reordenadas | 0 colores de B aplicados a A; 0 mutaciones durante misma sesión; sin cambio de dictado/composición. |
| **P4 parpadeo** | Galaxy S25: entrada/salida, WhatsApp, Instagram, Telegram, DeX split-window | 0 ciclos `hideWindow()/showWindow()` por warmAsync; 0 cambios de colores a mitad de sesión; 0 pérdida de foco/tecla/palabra compuesta. |
| **P5 rendimiento** | Instrumentación temporal local sin PII; histograma p50/p95, tracing main-thread | Propuesta: p95 caché ≤2ms; extracción worker ≤120ms; crecimiento cachés ≤2MiB; sin PM/cuantización en UI. **Umbrales por ajustar con datos reales.** |
| **P6 regresión 2E** | Comparación de archivos tocados, suites previas en fase autorizada | Cero diff en Fase 2E/IA/autocorrección; suites anteriores sin regresión. Falta externa Codeberg documentada por separado. |
| **P7 visual identidad** | Inspección visual y snapshots propios de UI del teclado en entorno de prueba (no capturar pantalla de otras apps para extracción) | Mismas medidas, tipografías, iconos, controles y 0 cambios fuera de colores. |

**Pruebas físicas mínimas:** Galaxy S25 One UI con tema claro/oscuro, Samsung DeX con teclado flotante/ventana/multiventana, navegación rápida entre apps, autenticación/campos sensibles, apps sin launcher, launcher con themed icons, work profile si existe y modo sin desbloquear; comparar widget/icono original frente al tintado. **No se ejecutó ninguna de estas pruebas en este scope.**

## 5. Fallos, fallback y rollback

- **App no visible / packageName ausente / icono inaccesible:** mantener `DynamicColorSeedProvider.snapshot()`; no exigir nuevo permiso ni detener IME.
- **Icono transparente/monocromo/gris / score sin candidato:** fallback a sistema, no tomar el azul interno por defecto de `Score` como color real de la app.
- **Lectura lenta / cancelación:** la primera sesión conserva sistema; worker prepara siguiente apertura. **Sin recoloreado tardío.**
- **Error del proveedor / cache corrupta / excepción:** `runCatching`/resultado tipado local, circuito de fallo acotado; sin propagación que derribe IME.
- **Preferencia OFF:** bypass completo: no tareas nuevas, cancelar lógicamente pendientes, vaciar caché temporal si procede, usar flujo exacto anterior. Comprobar preferencias guardadas de familia.
- **Reversión:** flag opt-in deshabilitado por default; revertir commits de fase 1–4 o quitar nueva ruta detrás del flag; mantener intactas preferencias previas; no requerir migración ni desinstalación. La desactivación jamás depende de permisos especiales.
- **STOP/NO-GO:** si implementación requiere nuevos permisos, extracción de pantalla, cambio de UI, reinicio visible del IME en cambio de app, acceso a datos de usuario o modificaciones en motor de escritura.

## 6. Registro de riesgos y dependencias

| Riesgo | Severidad | Mitigación / evidencia que falta |
|---|---|---|
| `seedOverride` no rastrea la nueva semilla | Alta | Snapshot con fingerprint por sesión + prueba de A/B. |
| Recarga activa usa `hideWindow()/showWindow()` | Alta | Nunca publicar resultado asíncrono en UI; benchmark de recreación antes del primer render. |
| `DynamicSchemeProvider` sin límite | Media | LRU acotado, estrés con cientos de apps/semillas. |
| Icono themed/monochrome falsea marca | Media | Preferir capas originales, filtrar grises y usar fallback. |
| Perfil laboral/Knox/Direct Boot | Media | Pruebas Samsung; fallback por perfil sin permisos. |
| Diferencia icono ↔ tema real dentro de app | Baja (expectativa) | Documentar que By App sigue icono, no píxeles de pantalla. |
| Cambio rápido A→B→A o restart | Alta | Tokens/generación; congelación por sesión; ausencia de I/O en UI. |
| `XLinkTest.knownDictionaries` externo | Pipeline, fuera del scope | Registrar como bloqueo ajeno, no tocar/reparar aquí. |

## 7. Decisión requerida antes de escribir código

El Owner debe autorizar **expresamente** una fase separada de implementación tras aprobar el análisis. La aprobación de estos documentos **no** autoriza APK, CI Android, release ni merge. Continuar solo con gates de acceso local a iconos, pure functions y un cambio incremental validado; detener ante cualquier violación del contrato de privacidad o estabilidad.

**Fuentes primarias:**  
- Código de la rama, anclado a `5e58323713eaf7fbc463fec7bed8a1483780556d`: https://github.com/Jorgeprdz/HeliBoard/tree/5e58323713eaf7fbc463fec7bed8a1483780556d/app/src/main/java/helium314/keyboard  
- Android IME package visibility: https://developer.android.com/training/package-visibility/automatic  
- EditorInfo: https://developer.android.com/reference/android/view/inputmethod/EditorInfo  
- MCU image seed: https://github.com/material-foundation/material-color-utilities/blob/main/dev_guide/extracting_colors.md


## R2.5 gated sequence — authorization updated 2026-10-16

Owner authorized **only R2.5.1** with initial HEAD `c5bf4aed97e10a82439933d42978eb4474f35d75`. This overrides the earlier documentation-only status **only for the limited R2.5.1 diagnostic/validation scope**; product integration remains prohibited.

| Subphase | Authorized purpose | Release condition |
|---|---|---|
| **R2.5.1 — current** | Validate existing isolated chromatic extractor in real Android instrumentation; collect main/system/crash logs and Gradle/XML even on error; diagnose runner first with a one-case smoke test, then run five existing chromatic tests; preserve JVM gate and physical fallback instructions. | Five actual PASS, valid ARGB/HCT vectors, repeatability, safe neutral/missing fallback, worker thread, measured latency and preserved protections. Any missing item = **NO-GO**. |
| **R2.5.2 — blocked** | Later isolated LatinIME session integration with no mid-session UI reloads. | Separate owner authorization after R2.5.1 GO; not part of this commit. |
| **R2.5.3 — blocked** | Later preferences and physical visual acceptance. | Separate authorization after R2.5.2; not part of this commit. |

### R2.5.1 incident and forensic design

Failed action [38984755357](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38984755357) assembled both Android packages but logged `Starting 0 tests`, `Process crashed`. **Root crash stack is not present in the main job log**, and the old workflow terminated immediately on Gradle failure before collecting emulator logcat, leaving the underlying reason **unconfirmed**. R2.5.1 repairs only test infrastructure: an isolated `R251RunnerSmokeTest` (no color extraction/IME binding) precedes the existing five-test fixture; a wrapper script always saves logcat/all, crash buffer, DropBox crash entries, instrumentation registrations, activity processes, output XML/HTML and original Gradle exit. These files stay in the no-APK evidence artifact. A failed smoke stops the vector gate rather than masking failures.

The legacy original Android test cases remain unmodified. Neither `app/build.gradle.kts`, production Kotlin/Java, product branding, package ID/signing, writing models, nor LatinIME is authorized to change. **Do not mark R2.5.1 PASS from compilation, host JVM tests or artifact presence alone.** The direct physical S25/DeX test is tracked separately in `VECTOR_ANDROID_PHYSICAL_TEST_GATE.md`; its absence must remain explicit in the report.
