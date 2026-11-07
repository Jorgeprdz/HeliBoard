# GIF y Gemini Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:dispatching-parallel-agents to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Incorporar GIF dentro del IME y recuperar el objetivo de corrección automática con Gemini sin modificar apariencia.

**Architecture:** Dos entregables independientes sobre la rama actual de Tactic. Los GIF tienen controlador de sesión propio y entrada de búsqueda interna; Gemini usa una sesión automática independiente de las herramientas manuales y las transacciones existentes del IME.

**Tech Stack:** Android Java/Kotlin, InputView existente, GIPHY REST, GeminiProvider existente, InputConnectionCompat, pruebas JVM e integración IME del proyecto.

**Spec:** `../specs/2026-11-07-gifs-y-correccion-design.md`

## Global Constraints

- Conservar apariencia de letras: geometría, tamaño, distribución, transparencia, desenfoque, familias de colores, iconos aprobados y comportamiento de colores por app.
- No modificar identidad ni firma de la app.
- No usar Activity, diálogo, WebView, navegador ni ventana flotante para buscar GIF.
- El consentimiento manual existente no habilita automáticamente envío en segundo plano.
- No afirmar entrega ni corrección funcional antes de probar el comportamiento real.

## Review Focus

- Escribir una búsqueda GIF no puede contaminar el borrador ni alimentar Gemini.
- Un resultado asíncrono de otra búsqueda/editor no puede mostrar contenido ni editar texto.
- Un editor que rechaza contenido no debe producir falso éxito ni cambiar de ventana.
- Retroceso debe deshacer el cambio automático manteniendo el estado de composición/caché.
- Rotación, cierre, red ausente y límites de cuota deben cancelar trabajo sin bloquear escritura.

## Prerrequisitos de ejecución

- [ ] Obtener checkout aislado del commit inspeccionado y leer instrucciones aplicables del repositorio.
- [ ] Confirmar HEAD remoto y revisar diferencias frente al commit inspeccionado; no partir de main ni de la release antigua por defecto.
- [ ] Verificar baseline de tests y build con el flujo cloud existente; no asumir que hay SDK Android local utilizable.
- [ ] Mapear los hooks exactos de entrada y estado antes de editar; no introducir un segundo KeyboardState incompatible.
- [x] Diseño de Gemini y alcance completo aprobados por el usuario; continuar sin nuevas confirmaciones.

## Entregable 1: GIF dentro del teclado — aprobado

### Task 1: Proveedor y estado de búsqueda

**Files:** crear `app/src/main/java/helium314/keyboard/latin/gifs/GiphyProvider.java`, `GifSearchSession.java`, `GifItem.java`; pruebas homónimas bajo `app/src/test/java/helium314/keyboard/latin/gifs/`.

**Interfaces:** `GifSearchSession.search(String query)` reemplaza consulta pendiente; `close()` cancela trabajo y no entrega callbacks posteriores. El proveedor devuelve identificador, URLs HTTPS verificadas, MIME, dimensiones y atribución, con paginación.

- [ ] Escribir pruebas fallidas: búsqueda A/B con A tardía solo muestra B; cierre suprime callback; 429 conserva estado y no crea bucle de reintentos; JSON inválido no produce ítems insertables.
- [ ] Ejecutar pruebas específicas con el runner Gradle del proyecto y registrar fallo esperado.
- [ ] Implementar transporte acotado, cancelación, debounce y tratamiento de cuotas; configurar clave fuera del código fuente.
- [ ] Ejecutar pruebas específicas y confirmar PASS.

### Task 2: Panel y entrada interna

**Files:** crear `app/src/main/java/helium314/keyboard/keyboard/gifs/GifPanelView.java`; modificar `KeyboardSwitcher.java`, `LatinIME.java`, `latin/utils/ToolbarUtils.kt` y el layout `input_view` solo para incorporar la nueva vista.

**Interfaces:** `GifPanelView.open()`, `close()`, `onSearchText(CharSequence)`, `onSearchBackspace()`; el enrutamiento de eventos se decide usando el modo activo, antes de escribir en InputLogic.

- [ ] Escribir prueba IME fallida: GIF abre dentro de InputView; escribir consulta no altera texto del editor; ABC/Volver restaura letras y borrador; cambiar de editor cierra la búsqueda.
- [ ] Ejecutar y comprobar fallo esperado.
- [ ] Implementar panel con búsqueda, tendencias y miniaturas animadas; reutilizar paleta actual sin alterar recursos de teclas.
- [ ] Implementar recientes/favoritos por identificadores y metadatos conforme a términos vigentes; incluir atribución obligatoria.
- [ ] Ejecutar pruebas IME y comprobar PASS; verificar cierre/cancelación al cambiar app, rotar o recortar memoria.

### Task 3: Inserción y validación real

**Files:** crear `app/src/main/java/helium314/keyboard/latin/gifs/GifContentSender.java`; modificar `AndroidManifest.xml` y configuración de FileProvider según componentes existentes.

**Interfaces:** `GifContentSender.send(GifItem item)` comprueba MIME y sesión, descarga con límite y concede lectura de URI; resultado distingue aceptación, rechazo, cancelación y error.

- [ ] Escribir pruebas fallidas: editor rechaza MIME/commit; sesión cambia durante descarga; contenido excede límite; permiso de lectura correcto; rechazo nunca muestra éxito.
- [ ] Ejecutar y comprobar fallo esperado.
- [ ] Implementar inserción Commit Content con URI temporal; no abrir compartir como fallback automático.
- [ ] Ejecutar tests, compilación y lint. Con clave GIPHY válida, probar búsqueda e inserción física en apps compatibles y rechazo en campo incompatible.
- [ ] Comparar capturas del teclado normal y revisar diff visual; no publicar ni instalar hasta tener artefacto verificable e identidad compatible.

## Entregable 2: Corrección automática Gemini — diseño por revisar

### Task 4: Sesión automática independiente

**Files:** crear `latin/ai/AutomaticGeminiReview.java` y pruebas; modificar `AiSettingsActivity.java`, `AiKeyboardAssistant.java`, y hooks pertinentes en `LatinIME.java`/`InputLogic.java`.

**Interfaces:** sesión separada con `onWordBoundary()`, `onEditorChanged()`, `onSearchModeChanged(boolean)`, `close()`; usa `GeminiProvider` y consentimiento automático independiente.

- [ ] Primero demostrar en test que escribir no invoca Gemini en el código actual; no confundirlo con fallo del proveedor.
- [ ] Probar temporizador propuesto de 900 ms, intervalo mínimo de 3 s y contexto máximo de 512 unidades UTF-16; cero peticiones en composición, campos privados, incógnito, exclusiones, modo GIF o consentimiento desactivado.
- [ ] Implementar configuración inicial y sesión automática; descartar respuestas antiguas y no bloquear eventos de escritura.
- [ ] Probar sin clave, offline, 429, timeout y salida incompleta: texto intacto, corrector local operativo, sin diálogos.

### Task 5: Diferencias mínimas, aplicación y deshacer

**Files:** crear `latin/ai/AutomaticCorrectionPolicy.java`; integrar `AiEditTransaction`, `SemanticEditGuard` y las transacciones/cachés existentes sin duplicar undo.

**Interfaces:** la política acepta o rechaza un conjunto de ediciones mínimas sobre snapshot exacto; no acepta una probabilidad inventada por el modelo.

- [ ] Tests fallidos para protección de nombres, cifras, negaciones, URLs, cambios de significado y reemplazos de párrafo; todos deben abstenerse.
- [ ] Tests de carrera: cursor movido, edición concurrente, enviar, otra app, composición o cambio a GIF invalidan respuesta.
- [ ] Implementar aplicación en límite seguro con las mismas reglas de actualización de caché y composición que el motor nativo.
- [ ] Probar Retroceso inmediato: recupera texto original, registra rechazo y no cambia texto posterior.
- [ ] Con clave configurada en dispositivo, evaluar respuestas reales en español mexicano, inglés y mezcla de idiomas; medir latencia, errores introducidos, cuota y fluidez.
- [ ] Ejecutar gates del proyecto y comparar apariencia antes de afirmar que Gemini funciona automáticamente.

## Estado

Plan preparado; sin cambios de producto, claves nuevas, compilación, APK, instalación ni publicación realizados en este documento.

## Task 6: Predicción, dictado y deslizamiento

- Añadir transiciones de palabras a aprendizaje local protegido, con pruebas de aislamiento por idioma, rechazo, borrado y límites; integrar ranking existente.
- Añadir callback de transcripción parcial y visualización dentro del IME, insertar solo resultado final y conservar cancelación/undo.
- Verificar biblioteca glide instalada; mejorar preparación y diagnóstico sin alterar geometría ni redistribuir código sin licencia comprobada.
- Tests primero, ejecutar fallo esperado y después suite focal y regresiones.

## Task 7: Traducción interna y escritura a mano

- Crear panel interno de traducción con campo fuente y resultado; sesión independiente, resultados obsoletos descartados, no escribir fuente en chat.
- Crear pad de tinta dentro del IME con reconocimiento digital opcional descargado una vez, selección de idioma y candidatos insertables; no abrir Activity durante uso.
- Añadir contenido de stickers mediante proveedor de medios, sin prometer catálogo exclusivo de Google.
- Probar rutas de entrada, cancelación, modelo ausente y preservación de borrador.

## Task 8: Verificación e instalación autorizada

- Ejecutar suites completas, lint, compilar artefacto firmado, revisar diff y recursos visuales con revisión independiente.
- Localizar dispositivo autorizado, verificar paquete/firma/version actual y guardar evidencia de apariencia.
- Instalar como actualización conservando datos; verificar version instalada, ausencia de crash y apariencia normal intacta.
- Probar funciones con credenciales configuradas, y distinguir pruebas simuladas de conexión real.
