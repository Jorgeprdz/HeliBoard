# CODEX — HELIBOARD AI R2
## Implementación integral, pruebas, compilación e instalación como actualización

**MODO: EJECUTAR Y RESOLVER. NO ES OTRA AUDITORÍA.**

Trabaja de forma autónoma y metódica con el repositorio real, implementa código funcional, ejecuta pruebas, corrige errores y busca llegar a un APK instalable que actualice la versión existente **sin desinstalar**. Reporta hitos verificables. No des por terminada una fase sin sus pruebas. No ocultes bloqueos; no inventes APK, pruebas, firma ni instalación.

### 0. Fuentes y estado de partida

- Repositorio: https://github.com/Jorgeprdz/HeliBoard
- Rama con el prototipo IA e identidad iOS Glass: `feature/dynamic-color-schemes`.
- Commit auditado (referencia inmutable): `b7246d0fc0cf58dde12781411cb6d2d638ced8c7`.
- PR de código en borrador: https://github.com/Jorgeprdz/HeliBoard/pull/4 (puede tener conflictos; no fusionar automáticamente).
- Especificación anterior: https://github.com/Jorgeprdz/HeliBoard/pull/3.
- Auditoría completa: `HeliBoard_AI_Auditoria_2026-10-14.txt` (si está adjunta o accesible localmente, léela entera; no asumas su presencia en el repo). Este prompt resume sus hallazgos críticos para funcionar por sí mismo.
- Proyecto de dictado que se quiere adaptar, NO instalar como segundo teclado: https://github.com/trevornk/ramblr, referencia auditada `6538900746676382c2422a42a026fa154264f6a0`.
- Dispositivo principal: Galaxy S25 / Android 17 / One UI 9, con Samsung DeX. Comprobar también Android mínimo y ventanas externas.

**Hallazgos previos que debes reparar:**
- `AiKeyboardAssistant.java` mezcla lógica de red, acciones y editor; ignora incógnito; filtro insuficiente de OTP/cuentas; edición directa con `InputConnection.commitText` sin transacción/undo seguro; preview truncada; cancelación solo lógica; posibles estados obsoletos.
- `SuggestionStripView.kt`: IA secuestra TODOS los botones Clipboard, incluso menú original; sobrescribe/restaura mal toolbar y sugerencias, pierde quick pin/códigos personalizados; icono sin tint del tema y chips con tipografía no integrada.
- `AiSettingsActivity.java`: falta toggle opt-in, política de backups, errores de claves y compatibilidad; modelo Gemini predeterminado puede no estar disponible con una API key nueva.
- «Lectura inteligente»: regex prototipo, Calendario solo con título, Maps recibe todo el mensaje, contactos incompletos, CLABE sensible puede quedar en historial.
- Autocorrector semántico/local ES-MX + EN-US: **NO implementado**; solo motor tradicional de HeliBoard.
- Ramblr dentro de HeliBoard: **NO implementado**, el micrófono original sigue delegando a otro IME.
- No existe build demostrado del commit auditado. No afirmar que compila hasta hacerlo.

### 1. REGLA ABSOLUTA: LA INTERFAZ NO SE MODIFICA

Congela el estado aprobado del teclado iOS Glass como *baseline*. No cambies altura del IME, geometría de la barra de sugerencias, insets, márgenes, posición/tamaño/distribución de teclas, esquinas, colores, transparencias, tipografías, espacios, animaciones, posición del engrane ni del micrófono. No agregues filas, overlays persistentes o botones fuera de la superficie original. No cambies la disposición al abrir/cerrar IA.

- Reutiliza exactamente el espacio del portapapeles para ✨ **solo cuando AI Keyboard esté habilitado** (OFF por defecto). Pulsación corta: herramientas IA. Pulsación larga: portapapeles; además debe seguir existiendo portapapeles **independiente** en el menú de herramientas. Los atajos personalizados, quick pin y preferencias preexistentes conservan significado; diseña migración segura sin secuestrar globalmente `ToolbarKey.CLIPBOARD`.
- Reutiliza la barra de sugerencias existente para acciones/chips y resultados cortos. Intercala con predicción/autofill/clipboard mediante un `SuggestionStripArbiter` o equivalente; restaura inmediatamente el estado anterior al salir, pulsar Back/Escape, ocultar IME o cambiar de editor. No permitir que `setSuggestions()` borre estados IA activos involuntariamente.
- El micrófono permanece exactamente donde está. Si Ramblr integrado está habilitado inicia nuestro dictado; si está deshabilitado conserva el mecanismo original, sin obligar a activar Ramblr como otro teclado.
- Resultados largos: revisión completa en una actividad de confirmación **invocada explícitamente** si no caben en la barra, sin redimensionar el teclado. Ninguna edición sin ver el texto completo.
- Icono IA debe respetar sistema de tint/temas, fuente personalizada, Material/Dynamic Monet, oscuro/claro, pressed/activated, escala de fuente, accesibilidad, TalkBack y teclado físico.
- Contrasta capturas y bounding boxes/insets con baseline en teléfono vertical/horizontal, flotante y DeX. Estabilidad visual demostrada, no solo XML sin cambios. Si no hay dispositivo accesible, marcar prueba visual **pendiente**, nunca PASS inventado.

### 2. ENTORNO DE TRABAJO, RAMA Y PROTECCIONES

1. `git status`, fetch de remotos y comprobación de commits, versiones y branch real. Evita pisar trabajo ajeno. Crea rama de implementación a partir de `feature/dynamic-color-schemes` (p. ej. `feat/heliboard-ai-r2-implementation`); no escribas directo sobre `main`, no hagas merge de PR #4 sucio sin resolver conflictos explícitamente.
2. Guarda baseline reproducible de recursos/UI, pruebas iniciales e identidad APK. Lee auditoría si está disponible y confronta hallazgos con el código actual; si cambió, documenta delta.
3. Haz commits pequeños y trazables; conserva licencia GPL y attribution si se adapta código de Ramblr. No copies su app completa, ni su AccessibilityService, overlays o configuración insegura.
4. Investiga **desde el inicio** la disponibilidad de la firma original y el entorno de build, en paralelo con desarrollo; jamás lo dejes como sorpresa al final.
5. GitHub Actions arrojó al usuario HTTP 422 «Actions has been disabled for this repository». Verifica su estado por medios autorizados; prueba build local con JDK/Gradle/Android SDK/NDK. No habilites Actions ni alteres políticas de repo sin autorización explícita. Si se habilita legítimamente, úsalo como opción, no dependencia.

### 3. FASE P0: PRIVACIDAD, EDITOR Y CICLO DE VIDA

Antes de activar Gemini o datos sensibles, implementa clases testables separadas: `SensitiveInputPolicy`, `AiSessionCoordinator`, `EditorSnapshot` versionado, `AiEditTransaction`, `SuggestionStripArbiter`, proveedor Gemini, reconocedor de entidades y `DictationController` (nombres sugeridos, no obligatorios).

- Política única de sensibilidad: contraseña/visible/web, credenciales, número, OTP, tarjetas, CLABE/cuentas (también separados con espacios o guiones), `IME_FLAG_NO_PERSONALIZED_LEARNING`, modo incógnito de HeliBoard, campos/app excluidos. Nunca enviar esos datos a la nube ni aprenderlos. No usar detectores probabilísticos como prueba de inocuidad. API key nunca en logs/repositorio/CI/capturas.
- IA opcional OFF por defecto. IA apagada = mismo comportamiento de antes (incluido portapapeles); revocar la función limpia estado y cancela pendientes. Nunca red al escribir ni HTTP por pulsación.
- Mantén el texto original exacto, incluidos espacios, acentos, emojis, surrogate pairs, saltos y spans. Snapshot con editor, foco, rango de selección, revisión de contenido y generación. `getSelectedText` puede ser null: no leer contenido extra sin mostrar alcance. No usar `trim()` como identidad del rango.
- Toda sustitución: revisión completa, consentimiento, transacción compatible con `InputLogic` / `RichInputConnection` y composing/cachés, retorno comprobado, Undo real, rechazo/copia segura. Si cambió el editor, selección o foco: descarta propuesta, no toca texto. No declarar «Insertado» si falla `commitText`.
- Cancelación real: una petición activa, `Future`/call cancelable, deadline global, sin cola infinita; limpiado en onFinishInput, onFinishInputView, onWindowHidden, onDestroy, rotación y cambio de display. Cancelar local no garantiza revocar bytes ya enviados a Google: explicarlo sin fingir.
- Android Keystore AES-GCM: estados NO_KEY/AVAILABLE/LOCKED/INVALIDATED, gestionar restauración, limpiar key alias cuando corresponda, excluir credenciales de backups, mantener compatibilidad mínima 21 sin ejecutar APIs 23+ donde no existan.
- Copiar datos bancarios: clip sensible + exclusión EFECTIVA del DAO/historial propio, no solo etiqueta visual. Conservar historial legítimo del teclado y permitir borrado explícito.
- Pruebas con fakes: cero solicitudes en incógnito/OTP/banca; editor cambiado, red lenta, sesión cancelada, dos selecciones idénticas en posiciones distintas, selección con whitespace y Unicode, conexiones null/rechazo, no inserciones tardías, undo exacto.

### 4. FASE CENTRAL: AUTOCORRECCIÓN CONTEXTUAL REAL, LOCAL Y MULTILINGÜE

Objetivo: mejorar corrección mientras escribimos, sin acción manual, sin subir cada letra ni volver lento HeliBoard.

- Mantén la ruta caliente actual de InputLogic + diccionarios + n-gramas para respuesta instantánea y funcionalidad offline. Trabaja con esa infraestructura, no la sustituyas por peticiones a Gemini ni un LLM grande.
- Añade revisor local asíncrono y cancelable por límite de palabra/pausa (debounce), con snapshots acotados y presupuesto estricto. Detección/ranking ES-MX y EN-US, cambio de idioma y code-switching dentro de una frase sin traducir palabras válidas, nombres propios, marcas ni mexicanismos.
- Propón correcciones por *spans*, confianza y motivo. Umbral alto: autocorrección reversible según preferencias. Ambigüedad: sugerencia no invasiva en barra existente. Respeta rechazos y permite desactivar/aprender localmente y borrar datos; no usar entrenamiento con texto sensible.
- Casos de evaluación positivos: «cotisacion»→«cotización»; «voy haber si puedo»→«voy a ver si puedo» según contexto; «espero que hallas llegado»→«hayas»; «el cliente me confirmo que si»→«confirmó que sí» cuando proceda. No codificar estas frases como listas fijas: son pruebas de calidad. Negativos: «haber qué pasa» en citas/expresiones intencionadas, «Hey! mañana tenemos meeting con Carlos», nombres, números, enlaces y texto técnico; nunca cambiar cifras o negaciones silenciosamente.
- Predicción de siguiente palabra y corrección tradicionales deben seguir visibles salvo cuando el usuario está deliberadamente en modo IA. No introducir red ni inferencia pesada en el hilo principal. Detecta si un modelo local es realmente viable antes de añadir runtime; LiteRT no incluye un corrector entrenado. Gemini Nano/ML Kit proofreading tienen restricciones de primer plano para IME: validar en dispositivo antes de convertirlos en dependencia; mantener alternativa local ligera compatible con minSdk 21.
- Crear corpus de pruebas sintético ES-MX/EN-US/code-switching, medir precisión, falsos positivos y tiempo. Presupuesto inicial: no degradar p95 de entrada/sugerencias >5% frente a baseline comparable. Publica resultados medidos, no números supuestos.

### 5. GEMINI BYOK FUNCIONAL

- Mantener la API key proporcionada **por el usuario dentro de Ajustes**, cifrada. No pedírsela por chat, no grabarla en Git, no integrarla en APK. Añadir habilitación explícita, selector de modelo disponible y estado de cuota/acceso. Consultar modelos realmente autorizados por API; no dar por válido `gemini-2.5-flash` para todas las claves.
- Herramientas: corregir ortografía, corregir gramática, reescribir, resumir, traducir ES↔EN con elección de idioma de destino, acortar, expandir, mejorar claridad y proponer respuesta. Tonos: profesional, casual, amigable, formal, directo, conciso, persuasivo y consultivo.
- Solo texto seleccionado/compartido explícitamente, con alcance visible y consentimiento de red, jamás mensajes recibidos no entregados por el usuario. No enviar números bancarios/OTP ni campos protegidos. HTTPS, límites de entrada/salida, cancelación, errores tipados 400/401/403/404/429/5xx, Retry-After pertinente, JSON multipart/finishReason/safety/truncación, modelo no disponible, offline, pruebas con fake server y solicitud real solo cuando el usuario configura su clave y autoriza.
- Preview íntegra antes de reemplazar, comparar texto/estructura cuando haya cifras, fechas, nombres o negaciones modificados; botones Usar / Copiar / Descartar / Deshacer. Nunca autoaceptar reescritura de nube.

### 6. LECTURA INTELIGENTE DE INFORMACIÓN EXPLÍCITA

Acceso: selección de texto mediante IME; PROCESS_TEXT y/o Share Intent, o entrada explícita al portapapeles, pero no monitorear portapapeles global ni chats/WhatsApp por Accessibility/Notification Listener/screen scraping.

Usar extracción local robusta con rango exacto, tipo, confianza y desambiguación; ML Kit opcional solo si compatibilidad de minSdk 21 y dependencias se resuelve de forma real (el gating de runtime NO arregla manifest merge de SDK 23).

- Fecha/hora/«mañana»/«el próximo jueves»: resolver respecto de zona del dispositivo, fecha local, ambigüedades y duración; `Intent.ACTION_INSERT` de Calendar con título, inicio/fin y ubicación correctamente detectados; **revisión obligatoria**, no crear silenciosamente. OAuth Calendar no requerido en v1.
- Dirección: extraer solo el span de dirección, abrir Maps/indicaciones/copia. No mandar todo el mensaje como query.
- Teléfonos: México/internacionales, ACTION_DIAL y contactos bajo revisión, nunca llamada automática.
- CLABE mexicana: normalizar formato, validar checksum, no inferir existencia/titular, no enviar a nube ni guardar en historial del propio clipboard. También cuentas/referencias sensibles.
- Email, URL (esquemas seguros), RFC/CP, montos/monedas, porcentajes y números de seguimiento donde precisión lo permita; no activar acciones por falsos positivos.
- Una frase con reunión + hora + dirección + teléfono debe ofrecer chips relevantes sin aumentar altura de la barra ni ocultar texto editado.

### 7. RAMBLR: DICTADO REAL INTEGRADO (NO SEGUNDO IME)

Revisa el código Ramblr en el commit indicado y adapta componentes reutilizables como `DictationRuntime`, `RecordingEngine`, `ProviderChain`, `LocalTranscriber` y `VocabularyTerms` a nuestro servicio, con licencias y avisos completos. No copiar su AccessibilityService ni instalar Ramblr como IME adicional.

- Tap en micrófono existente: iniciar grabación después de permiso `RECORD_AUDIO` explícito en actividad apta para solicitar permisos; estado RECORDING/STOP/TRANSCRIBING/CLEANUP/PREVIEW/USE/CANCEL en la barra existente. No grabación automática ni en background oculto.
- Soporte ES-MX/EN-US, puntuación, términos personalizados, eliminación opcional y contextual de muletillas (no borrar «este» en «este documento»), mantener texto crudo disponible si cleanup falla. Preview antes de insertar y Undo después.
- Camino inicial viable: batch de audio con proveedor configurado + consentimiento explícito (puede tener costo). Camino local con sherpa-onnx y modelos descargables opcionales, solo si se puede compilar y ejecutar sin degradar teclado ni violar licencias/minSdk/ABI; verificar SHA-256, memoria y borrado de modelos. No incluir modelos de cientos de MB sin autorización; no afirmar local ES si el modelo solo habla EN. No hacer depender el primer APK de experimental Live WebSocket si no está validado.
- Grabar y transcribir con cancelación real, mic ocupado, llamadas entrantes, orientación, pantalla apagada, editor cambiado, sesión destruida, permiso revocado, red interrumpida. No insertar en otra app ni guardar audios por defecto. Modo «estrictamente local» debe poder funcionar en modo avión sin fallback cloud.
- Si una variante STT no es viable por dependencia/modelos, dejar su opción deshabilitada y **documentar bloqueo real**, no pintar un botón falso. El objetivo de esta ejecución sigue siendo conseguir al menos UN dictado de extremo a extremo probado.

### 8. MANTENER IDENTIDAD Y RECUPERAR MISMA FIRMA: CRÍTICO

La auditoría pudo leer del teléfono la identidad del teclado instalado:

- Paquete instalado: `com.jorgeprdz.ioskeyboard.debug`.
- `versionCode` instalado: **4104**; nuevo debe ser **mayor**.
- Certificado SHA-256 del APK instalado (EXACTO):
  `E8:72:CC:90:3C:A5:AC:08:B8:74:06:B2:65:76:26:F3:60:3B:3B:72:C7:5E:50:55:34:32:F3:8C:11:70:FA:92`.

ATENCIÓN: certificados de builds posteriores no coinciden:
- 27 sep: `54:47:1E:4F:B6:F1:BB:0F:B2:7A:37:89:72:D6:D9:2A:F9:66:B0:AF:0A:8A:8F:BF:02:17:3F:DF:2C:FF:D1:CC`.
- Release 11 oct: `5F:19:E8:A9:0B:18:A7:22:E3:EE:BD:FC:F0:B0:78:C1:07:15:C3:B5:F2:39:85:6F:11:D2:77:01:60:62:FD:12`.

No sirven para actualizar lo instalado. Debes localizar **legítimamente la clave privada** del certificado E872, en el entorno original de compilación, keystore autenticado o backup autorizado; no extraer claves de otros usuarios, no intentar deducirlas desde APK ni regenerarlas. Enumerar solo rutas autorizadas de keystores, mostrar huellas públicas con keytool, nunca mostrar ni commitear secretos. El APK instalado contiene certificado público, NO la clave privada. 

Conservar paquete `.debug` y configuración de firma correspondiente, no intentar `release` con paquete distinto. Ajustar `versionCode >4104` sin afectar datos. Verifica con **apksigner** sobre APK firmado nuevo y APK instalado: nombre paquete, versionCode, SHA-256 cert, esquema de firma compatible; contempla lineage solo si EXISTE y está autorizado por clave antigua. No basta con `keytool -printcert` v1 ni con un hash del APK. Ninguna solución basada en desinstalar, borrar datos, `pm uninstall`, `pm clear`, `adb install -r -d`, renombrar paquete, nuevos certificados o root para evitar comprobaciones.

**Si falta la clave correcta:** continúa compilando y validando un artefacto de desarrollo de forma segura, pero NO lo presentes ni instales como actualización. Entrega diagnóstico preciso de dónde se buscaron respaldos autorizados, qué huella falta y cuál es la acción humana necesaria (proporcionar la clave original mediante canal seguro). No ejecutar sustituciones destructivas.

### 9. COMPILACIÓN REAL Y PRUEBAS

1. Comprueba entorno Linux/macOS/host Codex y dependencias JDK 21 (si así lo exige proyecto), Android SDK 37/NDK configurado, Gradle wrapper y herramientas `apksigner`, `adb`; no simules Android Gradle Plugin en Termux nativo si el toolchain no lo soporta. Usa entorno viable y documentado. GitHub Actions puede estar deshabilitado: no te detengas por ese 422 si tienes compilación local.
2. Ejecuta en secuencia limpieza controlada, compilar código Kotlin/Java, `testDebugUnitTest`, pruebas Robolectric existentes, lint, instrumentación donde haya dispositivo y `assembleDebug` con la variante/identidad apropiada. Captura logs y errores. Corrige y repite hasta verde. No ignores pruebas fallidas ni elimines dependencias para silenciar errores.
3. Pruebas de regresión: edición simple y multilinea en WhatsApp/texto genérico, `EditText`, Compose, WebView, Unicode, teclado físico, autofill, incognito, copiar, emojis, escritura gestual, themes, fallback sin clave/red, teclado bloqueado, cambio de app, app con editor no compatible, barra expandida y deshabilitada.
4. Rendimiento: compara p50/p95/p99 de escritura, predicción, consumo RAM/CPU/batería/temperatura con IA OFF/ON, inferencia local y dictado. Si no hay acceso al S25, no afirme validación en hardware: provee scripts de pruebas instrumentales y evidencia disponible.
5. Si dispones de conexión ADB autorizada al mismo S25 y recuperaste la firma E872, haz copia/registro no sensible del estado (preferencias sin secretos), valida actualizabilidad y realiza `adb install -r` SIN borrar datos. Comprueba `pm path`/`dumpsys package`, paquete/versionCode, prueba abrir teclado y persistencia de preferencias. No hacer cambios de teclado predeterminado sin consentimiento del usuario. Si ADB no está conectado, proporciona un **script idempotente de instalación de actualización** con validaciones previas y deja paso final al usuario, sin afirmar instalado.
6. No es aceptable terminar con «APK probablemente construido». Debe existir archivo real, tamaño >0, huellas, hash SHA-256 del archivo, reporte de tareas Gradle, ubicación verificable, y si es posible adjunto/Release en entorno autorizado. No publiques APK de prueba como estable ni firmware/archivos confidenciales.

### 10. MATRIZ DE ACEPTACIÓN / PROHIBICIONES

Se aceptará solo si:
- UI original pixel-identical en regiones invariantes del teclado; mismos insets/teclas/toolbar y funcionamiento con IA off. Solo contenido textual/icons solicitado cambia en la MISMA posición.
- Clipboard accesible por long press y menú propio, tema/tint/fuente correctos; voz en micrófono original.
- Autocorrección local contextual bilingüe funcional en corpus (sin enviar por pulsación), predicción intacta, sugerencias ambiguas reversibles.
- Gemini explícito con privacidad, preview íntegro, cancel/undo, fallbacks claros.
- Acciones de calendario/mapa/teléfono/banca seguras sobre texto explícito con review; cero exfiltración involuntaria.
- Dictado integrable real y probado donde el hardware/proveedor lo permitan; ningún segundo IME.
- Tests y build reales, sin falsear PASS.
- **Para INSTALACIÓN:** package `.debug`, `versionCode>4104`, certificado E872 y datos preservados. Falta de firma = NO INSTALAR.

Prohibido desinstalar, borrar datos, regenerar una firma distinta y llamarla «la misma», habilitar permisos/instrumentos invasivos, leer mensajes de fondo, keylogging, enviar datos bancarios, mover la UI, crear segunda barra, pedir que el usuario pegue su API key en el chat, fusionar main automáticamente o cerrar falsamente tareas incompletas.

### 11. AUTONOMÍA, CONTINUACIÓN Y ENTREGABLE FINAL

No respondas con otro «plan» sin ejecutar. Ejecuta fase por fase hasta pruebas/compilación y tentativa segura de instalación. No detengas el trabajo para preguntar preferencias triviales; decide de manera conservadora respetando esta especificación. Solicita intervención humana SOLO para bloqueos no solucionables por código: clave E872 ausente, permisos de repositorio, API key/modelo que solo el usuario puede autorizar, acceso físico/ADB que no exista o decisión de aceptar una limitación técnica genuina.

Entrega por hitos cortos con evidencia:
- Componente/commit y diff.
- Tests ejecutados y resultado.
- Build APK: ruta exacta, bytes, SHA-256, package, versionCode, certificado SHA-256.
- Matriz funcional: implementado y **probado** vs parcial/no disponible (no llamar completo a algo decorativo).
- Comparativa visual, rendimiento y privacidad.
- Estado instalación: INSTALADO/VERIFICADO con evidencia del dispositivo, o BLOQUEADO con causa concreta y sin desinstalar.
- PR listo para revisión con CHANGELOG y licencia/NOTICE Ramblr si se utilizó código.

**ORDEN DE TRABAJO OBLIGATORIO:** 0) baseline + firma + entorno, 1) P0 privacidad/UI/editor, 2) autocorrección contextual bilingüe local, 3) Gemini, 4) entidades y acciones, 5) Ramblr dictado, 6) pruebas/build, 7) verificación de certificado e instalación IN-PLACE cuando sea legítimamente posible.

**VISIÓN INNEGOCIABLE:** HeliBoard debe sentirse exactamente como nuestro teclado actual, pero escribir más inteligentemente: comprende idioma y contexto, predice y corrige localmente, reescribe con Gemini a petición, entiende citas/direcciones/teléfonos/cuentas cuando seleccionamos texto y transcribe nuestra voz desde el micrófono original. Todo ello sin mover la UI ni arriesgar datos o firma.
