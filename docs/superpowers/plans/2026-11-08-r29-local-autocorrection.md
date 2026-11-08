# R29 Local Autocorrection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Usuario exige ejecución sin agentes y continuar hasta instalar.

**Goal:** Corregir teclas vecinas/espacios/tildes y evitar sustituciones cortas con cambio de sentido.
**Architecture:** Guard nativo compartido en LatinIME/InputLogic; recuperación pura y acotada con geometría/diccionarios en LocalContextReviewer/WordBoundarySegmenter; usar worker y transacciones existentes.
**Tech Stack:** Java/Kotlin, Android21+, JUnit/Robolectric, Gradle/CI; sin dependencias nuevas.
**Spec:** docs/superpowers/specs/2026-11-08-r29-local-autocorrection-design.md

## Global Constraints
Sin Gemini/modelo nuevo/cambio visual/borrado. Misma firma/paquete. Memoria Gradle1GB,1worker,tests512MB; no dos Gradle simultáneos. Sin agentes. No tocar/capturar apps ajenas.

## Review Focus
Palabras cortas con candidato nativo erróneo aunque el diccionario aún no marque válido; atajos/elección manual; entradas válidas bilingües o nombres; recuperación incompleta por presupuesto; composición/cursor/deshacer y respuestas obsoletas. Cada caso tiene prueba en las tareas.

### Task 1: protección nativa
- [x] Escribir R29ShortNativeCorrectionsNeverChangeMeaningAutomatically: no→ni,me→le en commit real y asistencia desactivada; caso manual/atajo/case-only conservados.
- [x] Ejecutar rojo con Gradle focal acotado.
- [x] Crear NativeAutoCorrectionPolicy.protect(SuggestedWords), usar antes de UI y al preparar compromiso en InputLogic. No cambia candidatos ni scores.
- [x] Verde focal; revisar compatibilidad.

### Task 2: recuperación local compuesta
- [x] Escribir R29NeighborCorrectionTest con «detecxiomnde→detección de», N/B exactos, acentos únicos, válidos/ambigüedad/cancelación/geometría/rechazo/protegidos; ejecutar rojo host.
- [x] Crear corrector puro de variantes vecinas (máximo2 sustituciones y1tilde), presupuesto finito y abstención si incompleto. Integrar primero tildes, después límites N/B y candidatos nativos existentes.
- [x] Ajustar caché de validación solo si el presupuesto compuesto lo necesita; mantener cota y cancelación. No corpus de frases de reemplazo.
- [x] Verde host y regresión previa; ajustar expectativas antiguas únicamente para separaciones ahora autorizadas como automáticas.
- [x] Prueba InputLogic de transacción real de tilde y deshacer/rechazo; no guardar mensajes reales.

### Task 3: entrega
- [x] Revisión propia y guard de apariencia; commit/push, fullCI.
- [x] Entrega R29.1: VersionCode20261113, versionName4.1-tactic-r29.1-local-correction.
- [x] lint+release1GB; comprobarfirma/revisión/JNI/runtime, install-r y SHA instalado.
- [x] Fixture propio: texto escrito con teclas/eventos nativos, ejemplos/deshacer, estadoestable; conservar configuración y volverTermux.
- [x] Documentar evidencia y límites; no declarar precisión estadística por fixtures sintéticos.


## Evidencia final y límites (2026-11-08)

- Implementación R29.1 c4ac7160; corrección de fixtures de diccionario 18c56843: CI 42521009962, 671 pruebas, cero fallos/errores/omitidas, lint y release correctos.
- Compilación instalada e8636a9a904ce3025ac08bddcf6da001434489fe añade únicamente contadores internos sin contenido al diagnóstico del servicio. SHA256 del APK: 72e361dde26df5921815336de15cfe03090779bfe548519fb3445720e89f1695. Firma y 12 bibliotecas JNI verificadas frente a R28; guards de apariencia y runtime correctos.
- Host: 87 pruebas, incluidas 27 letras y 246 sustituciones en dos geometrías. Son fixtures sintéticos; no constituyen una medida de precisión general.
- El usuario autorizó expresamente borrar el diccionario personal el 8 de noviembre. Se borró el proveedor Android y se verificaron cero entradas sin leer palabras. También se ejecutó Borrar aprendizaje local en la interfaz y se comprobó su confirmación. No se borraron datos completos de la aplicación ni la clave.
- En S25, mediante pulsaciones del teclado nativo en editor propio, tras limpiar aprendizaje: `no me mejora la detecxiomnde tambien ` produjo `No me mejora la detección de tambien `. Se verificaron además `fracias → gracias`, `instalacion → instalación` y `cancion → canción`.
- Una pulsación de borrar tras `No me detección de ` recuperó `No me detecxiomnde ` como una sola transacción.
- Límites observados: el diccionario activo confirma `tambien` como válida incluso con proveedor personal vacío y proceso reiniciado; se conserva. `holabamigo` tampoco se corrigió automáticamente en la prueba. No se afirma resolver todos los casos N/B ni añadir toda tilde en contexto. Las variantes ambiguas conservan la elección manual.
- Todas las pruebas de teléfono vuelven a Termux en finally. Artefactos sintéticos y logs de ejecución: /workspace/heliboard-verification/r29/.
