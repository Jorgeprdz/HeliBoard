# HeliBoard: GIF integrados y corrección automática

## Estado y evidencia

Los GIF con panel propio y API de GIPHY están aprobados por el usuario. El objetivo
de Gemini es corrección ortográfica y contextual automática, sin pasos manuales.
El usuario aprobó este comportamiento y pidió implementar los seis puntos, GIF y Gemini simplificado, hasta la instalación. No se pide otra aprobación para cambios reversibles necesarios.

Inspección remota: `Jorgeprdz/HeliBoard`, rama `feat/tactic-r243-hybrid-writing`,
commit `9451fa9a7ab7994e32c5cbb1b13b8914d741a290`.
Última release consultada: `v4.1-tactic-r252c.1`.

En `AiKeyboardAssistant.java`, `onWordBoundary`, `reviewLocal` y `queueContinuous`
usan revisión local y el modelo local opcional. `requestGemini` se llama desde
acciones explícitas. `AiSettingsActivity.java` describe consentimiento de red
"solo al elegir una acción". El objetivo automático no está conectado a Gemini;
esto no prueba una avería de autenticación ni identifica el commit que lo decidió.

## Requisito fijo

Conservar apariencia de letras: geometría, tamaño, distribución, transparencia,
desenfoque, familias de colores, iconos aprobados y comportamiento de colores por
app. No regenerar recursos visuales. No modificar identidad ni firma de la app.

## A. GIF — diseño aprobado

Pulsar GIF muestra búsqueda y resultados animados dentro del mismo InputView del
IME. No usar Activity, diálogo, WebView, navegador ni ventana flotante para buscar.
El campo de búsqueda pertenece al teclado: sus caracteres no se insertan en el
chat. La entrada para buscar y los resultados conviven dentro del área del IME;
el usuario puede alternar resultados y letras de búsqueda sin perder la consulta.
Volver/ABC restaura el teclado normal. Abrir, cerrar o buscar no altera el borrador.

Funciones: búsqueda en español, tendencias, recientes y favoritos. Recientes y
favoritos guardan identificadores/metadatos permitidos por GIPHY, no copias
permanentes de los archivos. Resultados paginados y respuestas antiguas descartadas
al cambiar búsqueda, editor o sesión. Descargas y animaciones con límites de
memoria; detener animaciones y cancelar peticiones al cerrar el panel.

Enviar un GIF usa contenido compartido mediante URI con permiso de lectura y la
API Commit Content de Android, después de comprobar los MIME admitidos por el
editor. Nunca afirmar entrega si el editor rechaza el contenido. Si no admite GIF,
mostrar el estado dentro del panel y permitir copiar un enlace por acción explícita;
no abrir otra ventana como alternativa automática.

Proveedor: GIPHY API con presentación propia que reutiliza colores del teclado y
respeta su atribución obligatoria. Una clave válida es requisito externo para
probar búsquedas reales. No reutilizar claves públicas de ejemplos. La cuota beta
documentada es 100 llamadas/hora por clave; el diseño debe evitar consultas por
cada tecla y gestionar límites. No prometer servicio ilimitado ni gratuito en producción.
Tenor no es alternativa de nueva integración: su API pública cerró el 30-06-2026.

Alternativas consideradas: SDK GIPHY (menos control visual); API con panel propio
(seleccionada por el usuario, mantiene el estilo). No copiar la interfaz de Gboard.

## B. Gemini — comportamiento propuesto para revisión

Una sola configuración inicial: clave cifrada existente, selección de un modelo
disponible y activación independiente de "Corrección automática con Gemini".
Explicar en ese ajuste que la función envía fragmentos del campo actual a Google.
El consentimiento manual existente no habilita automáticamente envío en segundo
plano. No solicitar ni guardar claves en documentos, chat, repositorio o logs.

El corrector nativo responde inmediatamente. Propuesta de temporización: tras una
pausa de 900 ms en un límite de palabra y fuera de composición, revisar la última
cláusula completa con contexto acotado a 512 unidades UTF-16. Una petición activa
y una pendiente reemplazable; intervalo mínimo propuesto de 3 segundos entre
peticiones. No hacer una llamada por tecla ni bloquear escritura esperando red.
Estas cifras son decisiones propuestas, pendientes de medir en el dispositivo.

Aplicar únicamente cambios mínimos de ortografía, puntuación y gramática/contexto.
No resumir, cambiar tono, traducir, completar pensamientos ni reescribir párrafos.
Gemini no aporta una probabilidad fiable de corrección: no decidir por una supuesta
"confianza" del texto del modelo. Validar las diferencias; abstenerse ante cambios
ambiguos, nombres protegidos, cifras, fechas, enlaces, correos o negaciones.
La abstención conserva la escritura original, sin obligar a abrir un menú.

Aplicar solo si editor, sesión, posición de cursor, composición y texto fuente
siguen coincidiendo. Descartar resultados que lleguen después de mover el cursor,
editar, enviar, cambiar de app, activar búsqueda GIF o cerrar el teclado. Integrar
la transacción existente de deshacer: Retroceso inmediato revierte la corrección
y registra rechazo para no imponerla repetidamente. Mantener sincronizadas las
cachés nativas de InputLogic/InputConnection; no sustituir texto a espaldas del IME.

Contraseñas, campos protegidos, incógnito y apps excluidas no se envían. Respetar
SensitiveInputPolicy. Sin red, sin clave, cuota agotada, respuesta incompleta o
timeout: mantener corrección local y texto; no mostrar diálogos durante escritura
ni reintentar indefinidamente. Diagnóstico breve consultable en ajustes.

Alternativas: revisión manual (situación actual, no satisface la petición);
revisión automática con reemplazo mínimo validado (recomendada por el objetivo);
solo sugerencias que hay que pulsar (menos automática, no seleccionada).

## Criterios de entrega

- GIF: buscar realmente, reproducir miniaturas, insertar en una app compatible,
  gestionar rechazo, volver a letras sin perder texto ni abrir ventanas.
- Gemini: reproducir el comportamiento actual, probar una petición real con clave
  configurada en el dispositivo y demostrar corrección automática sin selecciones.
- En ambos: impedir escrituras o resultados de sesiones anteriores.
- Comparar capturas de letras antes/después con los mismos ajustes, tamaño y app;
  verificar que recursos y geometría existentes no se modificaron.
- Compilación, pruebas de integración IME, lint y pruebas físicas antes de afirmar
  funcionamiento. El código presente y las pruebas con respuestas simuladas no
  prueban por sí solos calidad lingüística ni disponibilidad del proveedor.

## Referencias

- https://developers.giphy.com/docs/api/quick-start-guide/
- https://support.google.com/tenor/answer/10455265?hl=en
- https://developer.android.com/develop/ui/views/touch-and-input/image-keyboard
- https://github.com/Jorgeprdz/HeliBoard/tree/9451fa9a7ab7994e32c5cbb1b13b8914d741a290

## Ampliación autorizada: seis puntos y entrega instalada

1. Corrección ortográfica/contextual automática mediante Gemini simplificado y respaldo local.
2. Predicción personalizada: aprender transiciones de palabras localmente, con consentimiento de aprendizaje existente, sin guardar frases completas; reordenar y ampliar siguientes palabras según evidencias, borrar con el aprendizaje.
3. Dictado: resultados parciales visibles dentro del teclado y resultado final insertado con transacción segura; no duplicar texto provisional al confirmar; iniciar desde micrófono sin menú intermedio cuando reconocimiento local disponible.
4. Deslizamiento: conservar motor existente, comprobar disponibilidad y permitir preparar la biblioteca compatible desde ajustes sin extracción manual. No redistribuir ni descargar bibliotecas de procedencia/licencia incierta silenciosamente.
5. Traducción: modo interno con campo fuente y vista previa asíncrona; no requiere seleccionar texto del chat; idiomas ES/EN iniciales y extensibles. Se usa Gemini configurado y aceptación de resultado, sin reemplazos silenciosos durante traducción.
6. Contenido: GIF, búsqueda de stickers/combinaciones visuales por emoji y modo de escritura a mano mediante reconocimiento digital local opcional. No afirmar acceso al catálogo propietario de Emoji Kitchen.

Verificación e instalación: construir APK firmado con identidad compatible, verificar versión/certificado y actualizar con datos preservados. El usuario autorizó la instalación. No desinstalar ni borrar datos para sortear una firma incompatible. Registrar claves/cuotas/modelos/permiso de conexión como requisitos externos pendientes si no disponibles. No pedir secretos en chat.
