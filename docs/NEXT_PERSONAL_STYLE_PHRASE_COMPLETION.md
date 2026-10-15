# HeliBoard R2.4 backlog — autocompletado de frases y estilo personal

**Estado:** ESPECIFICACIÓN PARA SIGUIENTE IMPLEMENTACIÓN; no desarrollado en R2.3.  
**Fecha:** 2026-10-15.  
**Base:** HeliBoard R2.3 en `feat/heliboard-ai-r2-3-full-auto-cloud`, SHA `5afae24149164b9c9791e4a103a92e09f5d5c106`.  
**Prioridad de producto:** 1) autocorrección de errores de pulsación respetando intención; 2) autocompletado de frases y aprendizaje del estilo personal; 3) dictado con reparación de rectificaciones, sin perder backlog de Google Drive.  
**Regla innegociable:** no modificar el diseño principal del teclado, la geometría de las teclas, su disposición ni los atajos existentes. Documentación únicamente; no ejecutar Codex ni builds.

## Intención de usuario

No basta aprender una tabla de secuencias `palabra1 -> palabra2` o repetir frases literales. El objetivo es que HeliBoard aprenda **cómo se expresa** el propietario del dispositivo: registro (casual o profesional), aperturas, conectores, longitudes habituales, puntuación, mezcla ES-MX/EN-US, vocabulario preferido y preferencias expresamente aceptadas. Debe poder proponer continuaciones inéditas acordes con el tema y la intención del texto actual.

Ejemplos conceptuales, no entrenados ni implementados:
- `Oye, te quería comentar…` -> sugerir, *si el contexto lo respalda*, una continuación breve con el estilo casual que usa el usuario; nunca inventar hechos, nombres, cifras ni compromisos.
- Mensaje de trabajo -> mantener un tono profesional aunque las conversaciones informales tengan modismos; no confundir contextos.
- `Voy al Estado dw Mexico` -> `Voy al Estado de México`, no transformar `dw` arbitrariamente en `DW`.
- Dictado `tráeme 2 kilos de huevo, no mejor 3, bueno sí 2` -> preservar la **última decisión explícita** (`2 kilos de huevo`) al ofrecer limpieza de dictado, con cuidado especial en números y negaciones.

## Diferencias funcionales obligatorias

1. **Autocorrección de typos:** arreglar letras vecinas, duplicadas, omitidas, transpuestas y errores de acento, sin sustituir palabras ya válidas ni siglas, códigos, nombres y términos personalizables; corregir solo con confianza alta.
2. **Autocompletado de frase:** predecir la continuación semánticamente adecuada de la oración en curso (no únicamente la próxima palabra), presentada como **sugerencia para aceptar**, no insertada por sorpresa.
3. **Modelo de estilo:** perfil editable y revocable de preferencias de expresión, que prioriza sugerencias que suenen naturales al propietario sin copiar literalmente conversaciones ni inventar datos.
4. **Dictado reescrito:** módulo separado que puede quitar muletillas y rectificaciones abandonadas y corregir transcripción; no confundir con predicción de texto escrito.

## Estado R2.3 y brecha técnica

- `PersonalTypingStore` conserva evidencia por par de palabras, idioma y dispositivo (aceptaciones/rechazos); **no tiene** modelo general de estilo del usuario.
- `LocalContextReviewer` aplica reglas heurísticas y candidatos de diccionario; **no es** un modelo semántico generativo.
- Los diccionarios tradicionales permiten predicción/validación de palabras, pero **no equivalen** a completar frases aprendiendo la manera personal de expresarse.
- Se exige un nuevo subsistema aislado, opcional y medible; no presentar el R2.3 como si incluyera personalización de estilo o completado semántico.

## Diseño técnico propuesto (para auditar antes de codificar)

### A. Capa inmediata (sin IA remota)
- Mantener las sugerencias y autocorrecciones nativas de HeliBoard, con ranking sensible a distancia física entre teclas, diccionario en los idiomas activos y negativas/rechazos registrados.
- Reusar, cuando sea seguro, el estado efímero del editor para detectar dónde termina la palabra/oración; no almacenar todo lo escrito.
- Presupuesto estricto de latencia y memoria; no bloquear el hilo de pulsaciones/IME.

### B. Capa de continuación de frases
- Activar solo bajo opt-in y tras pausa/frontera apropiada (espacio o puntuación), no invocar modelo a cada tecla.
- Máximo 1-3 opciones breves, relevantes, que respeten contexto *visible en el editor* y la selección actual. Aceptación mediante toque explícito; rechazar/ignorar no debería borrar texto.
- Usar plantilla/frase local recuperada cuando haya evidencia suficiente, combinada con un ranker contextual; considerar un modelo local pequeño y cuantizado si las pruebas prueban viabilidad en Galaxy S25. Generación remota únicamente mediante consentimiento por acción; no transmitir historial a Gemini por defecto.
- No proponer continuaciones si el contexto es insuficiente, ambiguo o sensible; ninguna sugerencia puede inventar destinatarios, horarios, montos, decisiones, nombres o promesas.

### C. Aprendizaje del estilo personal
- **Privado, local y opt-in por defecto desactivado.** Aprender señales del estilo de forma incremental, derivadas de interacciones consentidas (p. ej. sugerencias aceptadas/editadas y patrones de redacción a nivel agregado); no guardar conversaciones completas ni registrar todas las pulsaciones.
- Perfil legible y editable: formas de empezar/cerrar mensajes, nivel de formalidad, longitud de oraciones, conectores, emojis/puntuación y code-switching español/inglés; permitir excepciones por contexto o aplicación solo con consentimiento.
- Adaptarse a cómo redacta el usuario **sin amplificar errores ortográficos**: aprender preferencia de redacción y léxico, no typos como estilo.
- Separar **memoria estilística** (cómo redactar) de **datos factuales** (qué quiso decir), y evitar trasladar frases íntimas/sensibles a otras conversaciones.
- Controles para inspeccionar, pausar, desactivar, borrar y excluir aplicaciones/campos del aprendizaje, con política de retención limitada.
- Recuperación mediante futuras copias cifradas de Drive solo con elección explícita, esquema versionado y opción de exportar/importar perfil; jamás sincronizar datos personales crudos por defecto.

### D. Contexto y UX
- Usar barra de sugerencias existente sin cambiar geometría y sin interrumpir predicción normal, portapapeles, correcciones o el micrófono.
- Distinguir `palabra` (candidato de autocorrección) de `frase` (continuación explícita); no insertar párrafos con un espacio.
- Vistas de ajustes para `Aprender mi estilo`, `Autocompletar frases`, `Preferencia de tono`, `Gestionar aprendizaje`, `Excluir apps` y `Borrar mi perfil`; el usuario decide alcance y profundidad.
- Soportar ES-MX, EN-US, español informal y mezcla de ambos sin capitalizar siglas accidentalmente.

## Evaluación objetiva

- Precision de sugerencias propuestas y tasa de aceptación frente a baseline n-gram del teclado.
- `False auto-correction` y costo de revertir: **minimizar cambios no pedidos** por encima de maximizar sustituciones.
- Relevancia semántica y parecido estilístico evaluados con muestras voluntarias, no con conversaciones privadas recolectadas de fondo.
- Latencia P50/P95, RAM, batería y rendimiento en Galaxy S25 y DeX; fallback inmediato a teclado normal si el modelo no está listo.
- QA de regresión: WhatsApp casual, correo laboral formal, editor largo, apps con campos sensibles, Termux (código/comandos), cambios de idioma, mezcla ES/EN, siglas, negaciones, números, abreviaturas, nombres propios.
- No aprender ni sugerir sobre contraseña, tokens, datos bancarios, campos protegidos o modo incógnito; no enviar texto a servicios en segundo plano.

## Fuentes comparativas para estudiar (referencias, no licencia implícita de copiado)

- CleverKeys (predicción de frases y aprendizaje local): https://github.com/tribixbite/CleverKeys
- AutoBoard (LLM contextual local, **NO copiar** su modelo de lectura general de pantalla por Accessibility): https://github.com/CrnchyBekfast/AutoBoard
- KeyboardGPT (combinación de predicción n-gram inmediata y sugerencia por LLM al solicitarla): https://github.com/bOsowski/KeyboardGPT
- HeliBoard original: https://github.com/HeliBorg/HeliBoard

Revisar licencias, latencia, memoria, privacidad y criterios de calidad antes de adoptar código/modelos. No conectar servicios externos ni registrar Accesibilidad sin justificación y consentimiento específicos.

## Criterios de aceptación de próxima fase

- [ ] Los ejemplos de typos se corrigen sin transformar siglas válidas o términos protegidos.
- [ ] El teclado ofrece continuaciones **de varias palabras**, no solo n-gram siguiente palabra, para un contexto suficiente.
- [ ] El perfil de estilo cambia sugerencias casuales/profesionales de forma comprobable, sin alucinar hechos ni copiar texto privado.
- [ ] El usuario puede aceptar, ignorar, rechazar y borrar tanto frases como preferencias aprendidas.
- [ ] Persistencia local opt-in con exclusiones y borrado comprobables; sin transmisión espontánea de conversaciones.
- [ ] UI principal sin cambios, sin lag ni degradación en batería; pruebas Android reales y CI.
- [ ] Prioridad sobre módulos secundarios: una propuesta que escribe como el usuario y no fuerza autocorrecciones equivocadas.

**Estado final del documento:** backlog únicamente. Aún no implementado ni validado en el dispositivo; no realizar build por crear esta especificación.
