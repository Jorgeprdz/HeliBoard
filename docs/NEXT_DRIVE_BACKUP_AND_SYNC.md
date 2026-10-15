# HeliBoard — siguiente implementación: respaldo integral y sincronización con Google Drive

**Estado:** PLANIFICADO / DIFERIDO. **No implementado en R2.3.**  
**Fecha de decisión:** 2026-10-15.  
**Base funcional:** `feat/heliboard-ai-r2-3-full-auto-cloud`, commit `5afae24149164b9c9791e4a103a92e09f5d5c106` (R2.3).  
**Prioridad:** Siguiente ciclo de implementación, *no* continuar consumiendo cuota de desarrollo en esta sesión.  
**Regla de producto:** conservar intacta la experiencia visual y el comportamiento habitual del teclado. Los cambios de interfaz se limitan a pantallas de AJUSTES / gestión de respaldos, no a teclas, distribución, barra de sugerencias, accesos habituales ni animaciones existentes.

## Decisión de producto

Separar de forma inequívoca **dos funciones**:

1. **Sincronización de escritura (automática, opcional):** transportar entre dispositivos el aprendizaje explícito de correcciones aceptadas/rechazadas y los diccionarios personales compatibles, sin sincronizar conversaciones, pulsaciones, audio ni campos sensibles; nunca sobrescribir silenciosamente elecciones locales.
2. **Respaldo completo de configuración (manual o programable, opcional):** crear copias versionadas recuperables de la personalización del teclado, basadas en el formato de exportación oficial que soporta HeliBoard, con exclusiones de seguridad. Añadir restauración guiada, vista previa de lo que se importará y exportación local independiente de Drive.

No describir la sincronización parcial como "copia completa". El usuario debe poder entender qué está respaldado, cuándo se respaldó, dónde está, cómo restaurarlo y qué quedará excluido.

## Estado real en R2.3: no confundirlo con el objetivo

El código existente ofrece `Personal Typing Cloud` dentro de Ajustes → Avanzado → AI Keyboard:
- Cuenta Google + OAuth y permiso restringido `drive.appdata`. Debe configurarse **Google Cloud OAuth para paquete/certificado real** y habilitar la Drive API; autorización extremo a extremo todavía no probada en el Galaxy S25.
- Archivos cifrados AES-256-GCM derivados de frase de recuperación (mínimo 20 caracteres); la frase se protege localmente con Android Keystore. Espacio privado `appDataFolder`, **invisible en Mi unidad**.
- Un archivo por identidad de dispositivo: nombre `heliboard-typing-v1-<uuid>.bin`; se leen/validan/mezclan los respaldos disponibles y se actualiza el propio. No hay historial/rollback de snapshots por fecha.
- JobScheduler: cambios agrupados tras **mínimo 10 minutos**, reconciliación periódica cada **12 horas**, sujetos a red y batería suficiente; no prometer sincronización inmediata.
- Se respalda evidencia de aprendizaje, rechazos admitidos por la política de datos, palabras individuales del diccionario Android sin shortcuts, y un subconjunto permitido de preferencias visuales. **No** cubre todas las configuraciones ni shortcuts con frases, configuraciones completas de temas, API keys de Gemini, audio ni conversaciones.
- La restauración de apariencia requiere una elección explícita al conectar; la experiencia real de respaldo/recuperación y el estado OAuth no tienen prueba de aceptación en un dispositivo real. R2.3 pasó gates de código; no implica validación de Google Drive en producción.

**No modificar/romper ese estado hasta que exista implementación y pruebas planificadas.** Revisar `docs/r23-implementation.md`, `TypingSync.java`, `BackupPayload.java`, `TypingCloudActivity.java`, `DriveAppData.java` y reglas actuales de almacenamiento antes de escribir.

## UX deseada (pantallas de Ajustes, no teclado)

Entrada de primer nivel o sección claramente identificable: **Ajustes → Respaldo y sincronización**. No esconder funciones críticas al final de la configuración de Gemini.

- **Cuenta vinculada:** correo de la cuenta Google, conectar/cambiar/desconectar, explicación breve del permiso Drive y de la frase de recuperación.
- **Sincronización de escritura [activada/desactivada]:** qué incluye/excluye, fecha/hora de última sincronización **completada**, estado/errores y opción "Sincronizar ahora" (sin bloquear la UI).
- **Copia completa:** "Crear respaldo ahora"; elección de destino **Google Drive / archivo local**; opción de periodicidad y retención de versiones; mostrar progreso, tamaño, fecha y componentes incluidos. No llamarlo completo si faltan áreas exportables conocidas.
- **Restaurar:** listado de copias con fecha, origen, dispositivo y versión/esquema; selección de componentes (aprendizaje, diccionarios, ajustes/temas, shortcuts compatibles); vista previa, conflictos y confirmación explícita antes de reemplazar valores. Mantener la apariencia del dispositivo salvo selección expresa.
- **Seguridad y administración:** explicación de cifrado, comprobación de frase, exportación/importación local, revocar acceso, eliminar copias remotas con doble confirmación; revocar acceso **no** equivale a borrar respaldo.
- Distinguir claramente estados "programado", "subiendo", "sincronizado", "error", "autorización pendiente", "restaurado"; ofrecer errores accionables. Nunca fingir éxito tras solo programar un job.

## Requisitos funcionales

### A. Sincronización del aprendizaje

- Conservar el diseño R2.3 de captura **explícita** (aceptación/rechazo) y su prevención de autocorrecciones que el usuario rechazó; no inferir datos de editor ni keylogging.
- Resolver conflictos sin perder rechazos locales ni aplicar correcciones automáticamente a palabras válidas; preservar cercos de borrado y protección de Direct Boot.
- En nuevos dispositivos, usar misma cuenta + frase para importar y fusionar. La restauración selectiva no debe reactivar datos que el usuario borró expresamente.
- Explicar que la sincronización es eventual y permitir ejecución manual segura.

### B. Copia integral y restauración

- Mapear exhaustivamente el **export/import oficial existente** y sus límites antes de diseñar el esquema versionado; identificar ajustes, configuraciones del teclado, temas y personalizaciones, diccionarios del usuario y shortcuts/frases almacenados por la app.
- Implementar formato versionado, compatible con migraciones y autenticado. Probar ciclos exportar → importar → comparar datos; prohibido restaurar `SharedPreferences` o bases de datos arbitrarias a ciegas.
- Separar **respaldo local ZIP/archivo legible para administración** de **copia privada cifrada en Drive**. La exportación local debe poder realizarse aunque Google OAuth no funcione. El respaldo con datos personales debe advertir y/o permitir cifrado.
- Historial de snapshots con fechas, retención configurable y rollback real, incluso cuando el último archivo esté incompleto o corrupto. Evitar sobrescribir el único punto de recuperación válido.
- Exclusiones obligatorias: API keys, contraseñas, tokens, historial de pulsaciones, textos completos, audios y secretos de Android Keystore. Diferenciar "configuración completa restaurable" de "imagen literal de datos privados".
- La recuperación después de desinstalar/reinstalar o cambiar de teléfono no debe depender de la clave hardware del dispositivo antiguo: requiere frase portable conocida por el usuario. Comunicar las consecuencias de perderla.

### C. Drive y permisos

- Registrar **OAuth Android** del ID de paquete y SHA-1 del certificado efectivamente distribuido; confirmar estado del proyecto y Drive API. Mantener scope mínimo `drive.appdata`, sin permiso general para Mi unidad.
- La UI debe decir expresamente que `appDataFolder` está oculto en Mi unidad. Si se decide ofrecer carpeta visible, se requiere un diseño y autorización independientes, nunca elevar permisos en silencio.
- Manejar expiración OAuth, cambio de cuenta, interrupciones, cuota de Drive, red no disponible, archivos corruptos e incompatibilidad de esquemas; no borrar último respaldo válido ante errores.
- Permitir borrar remoto bajo consentimiento explícito; desconexión y revocación no deben borrar copias automáticamente.

## Criterios de aceptación (antes de considerar terminado)

1. **No regresión visual:** capturas/comparación del teclado actual en claro, oscuro, DeX y distintas alturas; teclas, distribución, barra de sugerencias y comportamiento habitual sin alteraciones.
2. **Flujo real en Galaxy S25:** conectar Google desde la APK instalada y firmada; crear respaldo, ver **éxito remoto** confirmado, cambiar un dato, sincronizar, desactivar y reactivar.
3. **Recuperación multiplataforma:** en una instalación limpia/segundo dispositivo, misma cuenta + frase permiten restaurar aprendizaje/diccionarios y opcionalmente apariencia y shortcuts; identificar claramente componentes no restaurables.
4. **Respaldo completo local:** exportar archivo sin conexión Google, importar y verificar paridad para todos los elementos declarados compatibles; demostrar no filtración de secretos.
5. **Historial/rollback:** generar al menos dos versiones diferentes, listar, restaurar la anterior; archivos alterados, incompletos y frase incorrecta deben fallar sin modificar el estado local.
6. **Resolución de conflictos:** probar cambios concurrentes, rechazos correctivos, reintentos, restauración tras borrado explícito, y que preferencias visuales **no** cambian sin opt-in.
7. **Robustez:** baterías/red intermitentes, revocación OAuth, reinicio, actualización firmada sobre la instalación existente, verificación de compatibilidad de firma y conservación de datos.
8. **Evidencias:** pruebas automáticas + pruebas manuales registradas (paso, versión, dispositivo, resultado); no declarar "Google Drive verificado" exclusivamente por Robolectric o unit tests.

## Plan para la próxima sesión de implementación

1. **Auditar:** exportación oficial HeliBoard, modelo R2.3 y registro OAuth; producir matriz **incluido / excluido / migrable**, riesgos y decisiones.
2. **Diseñar:** esquema versionado de full backup + índice de snapshots + UX sencilla de configuración/restauración; usar sincronización de aprendizaje ya existente como subsistema separado.
3. **Implementar incrementalmente:** primero archivo local export/import + paridad, luego snapshots Drive + OAuth, después sincronización/manual/retención/restauración selectiva.
4. **Verificar:** unit tests, integración y pruebas físicas. Compilar solo tras gates; publicar e instalar APK únicamente tras firma y aceptación en el dispositivo.
5. **Documentar:** README/ayuda de usuario, limitaciones, pasos de recuperación de frase y diferencias entre sincronización y respaldo completo.

**Límite de este registro:** planificación exclusiva. No implementar cambios, no despachar Codex, no tocar la APK ni lanzar builds/Actions con esta documentación. El usuario decidió pausar el proyecto tras consumir cerca del 50 % de su cuota.
