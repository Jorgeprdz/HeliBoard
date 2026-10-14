# HeliBoard AI: teclado original + Gemini + Ramblr + lectura inteligente

> Implementación destinada a Codex sobre `Jorgeprdz/HeliBoard`. Esta especificación NO implica que las funciones estén implementadas. La implementación debe culminar en APK verificable y PRs pequeños. No introducir cambios en `main` sin revisión.

## Decisión de producto (obligatoria)

Mantener **sin cambios visuales o geométricos** el teclado existente del usuario (Galaxy S25, Android 17 / One UI 9, DeX). La pantalla de referencia tiene la barra superior con flecha a la izquierda, accesos a portapapeles, ajustes y micrófono a la derecha y las teclas QWERTY originales.

- Reutilizar **la misma posición y dimensiones del botón portapapeles**, reemplazándolo por ✨ al activar AI Keyboard.
- **Pulsación prolongada en ✨ = abrir el portapapeles original**; la función portapapeles sigue disponible también en el menú expandible. No perder el acceso ni el historial.
- El botón de ajustes **no se mueve**.
- El botón del micrófono **no se mueve**: dictado Ramblr si está configurado, comportamiento original de voz si no lo está.
- **La barra de sugerencias ya existente** presenta chips compactos de acciones/contexto y los estados de vista previa; no añadir fila, modal invasivo, barra adicional ni aumentar la altura/ancho del IME. Mantener animaciones y colores actuales, incluidos temas, teclado flotante, landscape y DeX.
- El sistema predictivo original vuelve de inmediato al cerrar ✨, pulsar atrás, cambiar el editor o cancelar.

## Investigación del repositorio

Repositorio: https://github.com/Jorgeprdz/HeliBoard
- `app/src/main/java/helium314/keyboard/latin/suggestions/SuggestionStripView.kt`: maneja `toolbar`, `pinnedKeys`, `suggestionsStrip`, `setExternalSuggestionView(view, addCloseButton)`, y `setSuggestions`.
- `app/src/main/java/helium314/keyboard/latin/utils/ToolbarUtils.kt`: `ToolbarKey`, botones activos, orden, preferencias y eventos click/long click.
- `app/src/main/java/helium314/keyboard/latin/LatinIME.java`: servicio IME.
- `app/src/main/java/helium314/keyboard/latin/RichInputConnection.java`: acceso/edición al texto bajo permisos y capacidades `InputConnection`.
- `app/src/main/res/layout/suggestions_strip.xml`: NO aumentar dimensiones.
- `app/build.gradle.kts`: compilación Android, `minSdk = 21`, `compileSdk=37`, `targetSdk=37`, variante de release.
- La función de botón debe funcionar aun si el usuario ya tiene preferencias de toolbar guardadas: migración robusta sin reorganizar sus accesos.

## Fase 1: IA en barra existente (funcional, no maqueta)

1. Añadir `AI_ASSIST` (o equivalente robusto) al controlador de toolbar; preferir reemplazo visual/comportamental del acceso al portapapeles activado por ajuste opt-in. Long press conserva clipboard; no cambiar estado/pref predeterminado sin habilitación.
2. Añadir coordinador de sesiones IA independiente del motor de predicción: `IDLE -> ACTIONS -> PROCESSING -> PREVIEW -> CONFIRM/UNDO -> IDLE`. El estado no sobrevive al cambio de editor o campo. Peticiones async cancelables y asociadas a generation token/editor context.
3. Chips en la barra de sugerencias: «Corregir», «Reescribir», «Breve», «Traducir», y «Atrás». Mostrar en la MISMA barra. El resultado jamás sustituye texto por sí solo. Preview con aceptar, cancelar y deshacer, sin aumentar la geometría. Si la cadena es larga, utilizar una actividad/dialogo de revisión iniciado con interacción explícita, manteniendo el IME mismo tamaño.
4. Selección: `InputConnection.getSelectedText` para la selección activa; si no hay selección, usar únicamente el bloque anterior al cursor con límites definidos y vista previa que aclare el alcance. No extraer todo el campo silenciosamente. `beginBatchEdit/endBatchEdit`, cambios reversibles y comprobación del contenido antes de sustituir. Respetar composing spans y rollback si el editor cambió.
5. UI settings: AI Keyboard toggle OFF por defecto, estilo y prioridad local/nube; ajustes separados de micrófono.

## Fase 2: Gemini BYOK (usuario tiene una API key)

- Añadir proveedor opcional `GeminiProvider`; **NO introducir la API key en código, commits, CI, assets, logs o conversación**. Configuración por pantalla de ajustes dentro de la app.
- Cifrar credencial de usuario en Android Keystore (por ejemplo AES-GCM con clave del Keystore); prevenir backups accidentales del secreto y exponer «Borrar clave».
- Consumir la API Gemini con cliente HTTPS; validar modelo y disponibilidad reales mediante documentación vigente; no inventar identificadores/versiones ni credenciales; backoff y manejo de 401/403/429, timeouts/cancelación, límite de longitud y costo opcional.
- Toda petición a la nube requiere gesto explícito del usuario y aviso claro de que texto/voz irán a Google. Modo local predeterminado en cuanto exista la capacidad.
- Respuestas y prompts del contenido de terceros se tratan como **datos no confiables**, nunca como órdenes del sistema. Nunca enviar historial completo, todo el portapapeles ni texto de otros campos. No conservar transcripciones.
- Ningún envío de contraseñas, tarjetas, claves OTP, cuentas/CLABE ni formularios seguros. Bloquear flujo en password, web-password, secure/OTP, `IME_FLAG_NO_PERSONALIZED_LEARNING` y modo incógnito. Nunca habilitar capturas silenciosas.
- Estilos incluidos: Profesional, Casual, Corto, Ortografía, Traducir ES/EN y «Consultivo», útil para mensajes comerciales sin agresividad.

## Fase 3: dictado Ramblr dentro del micrófono existente

Fuente de referencia: https://github.com/trevornk/ramblr (GPL-3.0; preservar licencia y avisos).

NO lanzar ni seleccionar Ramblr Voice como un segundo IME (ello sustituye a HeliBoard). Portar/adaptar cuidadosamente el **pipeline reutilizable de dictado**: control de micrófono -> grabación -> transcripción -> limpieza opcional -> vista previa -> `InputConnection.commitText` dentro del HeliBoard activo; sin Accessibility, overlay flotante ni servicio que lea otras apps. Revisar `DictationRuntime.kt`, `RamblrImeService.kt`, `ProviderChain*`, `PostProcessor.kt` y `CleanupWaterfallExecutor.kt` antes de adaptar.

- Por defecto conservar el botón de voz actual mientras dictado Ramblr no esté habilitado.
- Ramblr soporta transcripción local sherpa-onnx y posproceso llama.cpp, pero modelos descargables suelen ocupar cientos de MB. Descargar solo bajo consentimiento, opción local separada, indicadores de progreso, verificación SHA-256, cancelación, borrar modelos, límites de memoria, evitar degradar UI.
- Prioridad práctica v1: transcripción en la nube con Gemini si el usuario la configura y consiente; modo local descargable después, manteniendo **por separado** selección de STT y cleanup.
- Estados: mic idle -> recording -> transcribing -> cleanup -> preview -> insert -> undo; cancelación inmediata, permisos de micrófono concedidos desde SettingsActivity, indicadores claros de grabación, resiliencia en rotación, interrupción de audio y editor cambiado.
- Texto de salida: eliminar «eh», «este», muletillas y autocorrecciones al hablar; respetar puntuación y terminología personalizada. No iniciar grabación automáticamente.

## Fase 4: lectura inteligente segura

API principal: ML Kit Entity Extraction (beta):
https://developers.google.com/ml-kit/language/entity-extraction/android
`com.google.mlkit:entity-extraction:16.0.0-beta6` según la documentación actual; verificar al implementar. Español soportado. **SDK min 23**; mantener HeliBoard `minSdk 21` y hacer gating/fallback para 21-22 o un módulo que no rompa la compatibilidad.

El teclado **no tiene autorización para leer mensajes entrantes completos de WhatsApp ni otras aplicaciones por el solo hecho de estar abierto**. Implementar flujo «seleccionar texto -> acción procesar/compartir/copia explícita -> analizar». Para texto recibido, ofrecer ACTION_PROCESS_TEXT o Share Target / pegar desde el portapapeles solo tras pulsación del usuario. Jamás servicio de Accessibility, scraping de pantalla, notificaciones o lectura global de clipboard.

Extraer/mostrar chips contextuales prioritarios:
- `DATE_TIME` + lugar/asunto: «📅 Crear evento». Usar primero `Intent.ACTION_INSERT` con `CalendarContract.Events.CONTENT_URI`, pasar fecha/hora, título, ubicación y abrir Google Calendar u otra app para revisión. **No crear eventos silenciosamente** ni requerir OAuth en esta fase. Si falta hora, fecha, título o zona `America/Mexico_City`, solicitar confirmación/edición. Usar la zona del dispositivo y semántica correcta de «mañana», DST y año.
- `ADDRESS`: «📍 Abrir Maps» con `ACTION_VIEW` a URI geo bien escapada, elegir aplicación disponible.
- `PHONE`: «📞 Marcar» con `ACTION_DIAL` (nunca llamada automática); opción «Guardar contacto» con intent de contactos.
- `EMAIL`, `URL`: «✉ Escribir» y «🔗 Abrir».
- **CLABE mexicana 18 dígitos** y otros números de cuenta: reconocimiento conservador, validación opcional del dígito verificador CLABE (cuando aplique), nunca afirmar automáticamente qué banco/titular tiene; botón «📋 Copiar número» explícito, tratar como dato confidencial: no nube ni telemetría. No confundir teléfono de 10 dígitos con cuentas ni guardar números.
- Textos compuestos, p.ej. «Nos vemos el viernes a las 5 en Insurgentes Sur 1200, llámame al 55...» ofrecen chips de calendario/mapas/llamada en esa MISMA barra, con prioridad dinámica.

## Privacidad y UX no negociables

- No IA autocorrectora pesada por pulsación, red en el hilo principal, keylogging ni almacenamiento de texto; IA solo bajo gesto explícito, salvo extracción LOCAL y SOLO sobre texto ya seleccionado/compartido con el usuario y con el control habilitado.
- Nunca leer portapapeles en segundo plano, ni disparar el toast de acceso inesperadamente. Preservar clipboard long-press.
- Suspender funciones sensibles en modo incógnito y campos protegidos, sin degradar la escritura.
- Testear con y sin red, sin modelos, con API key errónea, interrupción de grabación, texto no seleccionable, foco perdido, múltiples editores, emojis, Unicode, texto RTL, dictado en español, perfiles DeX, landscape, ventanas flotantes y cambio de orientación.
- Respetar términos de Ramblr GPLv3 y avisos de sherpa-onnx/llama.cpp si se incorporan; evitar cherry-pick masivo no auditado.
- Mantener la firma/identificador esperados según la política de releases del fork. No exponer llave de firma.

## Proceso de ejecución esperado de Codex

1. Auditar estado actual de la rama y tests; no tocar ramas protegidas.
2. Crear rama `feat/heliboard-ai-keyboard` partiendo de la rama de desarrollo adecuada.
3. Implementar entregables verticales compilables con commits pequeños: UI AI/toolbar + test; Gemini + test; smart-action extraction + intents + test; Ramblr STT + test.
4. Ejecutar Gradle lint, tests disponibles, compilación debug y release con firma existente SOLO si la configuración de firma ya está disponible en secretos; no reportar APK firmado si no se pudo producir.
5. Ejecutar GitHub Actions, resolver fallos y crear PR con evidencia, hashes, pasos reproducibles y capturas. Adjuntar APK descargable verificable desde el workflow.
6. Reportar expresamente cualquier función bloqueada (permisos, SDK, licencias, API, NDK, inferencia local) y no declarar PASS sin pruebas reales.

## Criterios de aceptación

- Captura antes/después superpuesta: **misma geometría exacta** en ancho, altura, teclas, barra de sugerencias, posicionamiento de engrane y micrófono.
- Tap ✨ ofrece herramientas IA en la barra existente; long press ✨ muestra clipboard.
- Tap ✨ nunca envía texto hasta que se elige una acción en la nube y existe consentimiento.
- Gemini BYOK funciona sin API key en código; desactivable; claves guardadas de forma segura.
- Lectura inteligente de texto copiado/seleccionado reconoce español y ofrece acciones para cita, dirección, teléfono y CLABE sin capturar mensajes de fondo.
- Calendar intent abre evento editable, no inserta sin confirmación.
- Ramblr dictado desde el micrófono actual; no necesita activar un segundo teclado ni Accessibility.
- El teclado sigue funcionando de modo normal y fluido sin ninguna función IA activada.
