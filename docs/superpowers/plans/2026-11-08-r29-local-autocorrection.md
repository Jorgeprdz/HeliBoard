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
- [ ] Revisión propia y guard de apariencia; commit/push, fullCI.
- [ ] VersionCode20261112, versionName4.1-tactic-r29-local-correction en CI/build local.
- [ ] lint+release1GB; comprobarfirma/revisión/JNI/runtime, install-r y SHA instalado.
- [ ] Fixture propio: texto escrito con teclas/eventos nativos, ejemplos/deshacer, estadoestable; conservar configuración y volverTermux.
- [ ] Documentar evidencia y límites; no declarar precisión estadística por fixtures sintéticos.
