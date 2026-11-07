# Scope propuesto: Tactic, funcionalidad de Gboard e identidad intacta

Estado: alcance autorizado para implementación por el prompt actual del usuario. Las maquetas siguen siendo propuestas de composición; no certifican paridad funcional.
Base inspeccionada: /workspace/HeliBoard-gifs-gemini, HEAD 64af9ce.

## Objetivo

Ofrecer los flujos funcionales de Gboard con la identidad visual actual de Tactic.
Simplificar el teclado completo, incluidas las funciones originales de HeliBoard,
sus ajustes y todas las subpantallas. La reducción de controles no debe reducir
las capacidades de escritura que se pretenden igualar.

Gboard es referencia funcional y de facilidad de uso. La referencia de aceptación
será la versión instalada en el teléfono, registrando versión, idioma y funciones
disponibles; la documentación pública es apoyo. No se declara paridad ya lograda.
Funciones ausentes se registrarán como brechas, sin esconderlas como «limpieza».

## Identidad visual protegida

- Teclado principal: distribución y geometría aprobadas, tamaño, tipografía,
  iconos, formas y respuesta visual de las teclas.
- Paletas, colores dinámicos por aplicación, transparencias, desenfoque y
  acabados existentes. Los ajustes de tamaño que el usuario elija siguen disponibles.
- Nombre, icono de aplicación, paquete, firma y actualización conservando datos.
- Accesibilidad, contraste, escalado de texto y etiquetas accesibles.

Los paneles secundarios adoptarán estos mismos recursos. Rediseñar su composición
y navegación no implica sustituir el tema ni copiar el aspecto de Gboard.

## Qué se va

| Elemento | Alcance de la retirada propuesta |
| --- | --- |
| Modelo lingüístico local experimental Qwen de 491 MB | Retirar descarga, controles e integración de esta función. Bibliotecas nativas solo si se demuestra que ningún flujo conservado las necesita. No confundirlo con diccionarios ni corrección nativa. |
| Menú extenso de IA | Retirar el catálogo de resumir, expandir, cambiar tono, generar respuestas, leer texto y accesos repetidos como menú general del teclado. Una capacidad equivalente que resulte necesaria para la referencia de Gboard se incluirá en un flujo específico, no se descartará por conveniencia. |
| Grabadora independiente para enviar audio a Gemini | Sustituir el circuito separado de grabar/detener/enviar por dictado inteligente desde el micrófono habitual. Conservar y adaptar sus componentes útiles; no eliminar la captura necesaria para la equivalencia con Pixel. |
| Inspector y configuración del perfil de estilo experimental | Retirar contadores, selectores de registro y controles experimentales. Conservar aprendizaje de palabras y personalización útil de sugerencias. |
| Acciones añadidas de agenda, contactos y entidades | Retirar su menú auxiliar del alcance principal. No retirar entrada de direcciones, números ni los permisos que otra función conservada requiera. |
| Investigación de trazos y herramientas de desarrollo | Excluir de la interfaz de producción la recogida/revisión de datos gestuales y controles de depuración. Conservar herramientas de desarrollo fuera del flujo del usuario. |
| Controles duplicados y rutas antiguas | Eliminar accesos y estados redundantes después de migrar a su equivalente nuevo. |
| Código y recursos sin uso | Eliminar solo con comprobación de llamadas, manifiesto, recursos, reflexión, JNI y variantes. No declarar «sin uso» por falta de referencias textuales. |

Retirar interfaz y retirar implementación son acciones distintas. Primero se
identifica el reemplazo, se preservan los datos útiles y se verifica el flujo.
No se borrarán archivos personales, claves ni copias existentes como parte de esta limpieza.

## Qué se queda

| Área | Capacidad protegida |
| --- | --- |
| Escritura | Teclas, símbolos, números, mayúsculas, puntuación, sugerencias, autocorrección y deshacer correcciones. |
| Idiomas | Cambio de idioma, escritura bilingüe, distribuciones necesarias y diccionario personal. |
| Gestos y edición | Deslizar para escribir, control del cursor, borrado por gesto, selección, copiar y pegar. |
| Expresión | Emojis, búsqueda y sugerencias, diccionarios de emojis incluidos, GIF y stickers. Conservar el sticker de texto solicitado en la barra. |
| Portapapeles | Historial, fijados, eliminación y caducidad configurable. |
| Entrada alternativa | Dictado inteligente con comportamiento equivalente a Rambler de Pixel, alternativa literal, traducción integrada y escritura a mano. |
| Adaptación del teclado | Tamaño, fila numérica, sonido, vibración y modos de distribución disponibles y útiles. |
| Gemini | Corrector automático opcional, configuración inicial y estado comprensible de conexión. |
| Datos y privacidad | Exclusiones, incógnito, campos protegidos, aprendizaje local, borrar aprendizaje y respaldo/restauración. |
| Accesibilidad | Navegación accesible, etiquetas, contraste y compatibilidad con necesidades de entrada. |

Los modos a una mano, flotante y dividido, la edición de texto y otras capacidades
de la referencia se inventariarán individualmente: existente, incompleta o ausente.
Su inclusión no equivale a afirmar que ya funcionan. También se comprobarán búsqueda
de emojis y comportamientos por idioma. Servicios y paquetes de Google no se
considerarán reutilizables sin una integración válida.

## Qué se optimiza

| Área | Cambio esperado |
| --- | --- |
| Ajustes originales de HeliBoard | Agrupar por tarea. Un control principal por comportamiento, detalles técnicos en Avanzado, mostrar opciones dependientes solo cuando corresponda. |
| Corrección | Unificar sugerencias y corrección nativa con la revisión opcional de Gemini; evitar cambios duplicados, pérdida del borrador y resultados tardíos. |
| Gemini | Un alta inicial: clave, activación con explicación del envío de texto y conexión. Modelo automático; ajuste manual en Avanzado. Mensajes accionables para formato, permisos, conexión y cuota. |
| Consentimientos | Cada consentimiento explica el envío que habilita. Separar autorización manual y automática cuando sus finalidades difieran, sin controles repetidos ni nombres técnicos. |
| GIF y stickers | Aumentar moderadamente la altura del panel GIF para miniaturas verticales en dos columnas y todos sus controles completos. Teclado nativo para buscar, consulta separada del mensaje, paginación, recientes/favoritos y uso acotado de memoria/red. |
| Emojis y sticker de texto | Prioridad clara en la barra, convivencia con sugerencias, inserción correcta y ausencia de bloqueos al escribir. |
| Idiomas y diccionarios | Gestión automática de los paquetes incluidos; conservar incorporación de idiomas y edición del diccionario personal. Importación manual poco habitual en Avanzado. |
| Gestos | Valores predeterminados útiles. Sensibilidad, distancias, tiempos y alternativas especiales fuera del primer nivel. |
| Portapapeles | Acciones directas y estado claro; controles de archivos y límites técnicos en Avanzado. |
| Rendimiento | Medir latencia, arranque, memoria, tamaño y consumo antes/después. Reducir trabajo y dependencias realmente prescindibles. Sin prometer mejoras porcentuales sin medición. |
| Respaldo | Un acceso comprensible para respaldo y restauración. Cualquier sincronización existente se audita y agrupa, sin habilitar red ni cambiar destinos automáticamente. |

## Qué se rediseña al look existente

| Superficie | Trabajo visual y de interacción |
| --- | --- |
| Ajustes | Jerarquía corta, filas y selectores consistentes; búsqueda y un solo Avanzado para controles poco frecuentes. |
| Barra de herramientas | Accesos con orden e iconografía coherentes; secundarias en un único menú. Mantener personalización útil. |
| GIF, stickers y emojis | Cabeceras, pestañas, buscador, cuadrícula, favoritos y pie con el mismo lenguaje visual. |
| Portapapeles | Tarjetas, fijados, selección y acciones con las formas, superficies y espaciado de Tactic. |
| Traducción y escritura a mano | Contenedores, selectores, acciones y estados consistentes dentro del teclado. |
| Dictado | Un flujo de dictado inteligente desde el micrófono, con escucha, procesamiento, inserción final, edición por voz, deshacer y errores claros. Conservar una alternativa literal. |
| Gemini | Pantalla breve, estado visible, clave protegida, explicación concisa y errores comprensibles. |
| Todos los subflujos | Unificar volver, ABC, cerrar, confirmar y cancelar; tamaños de controles, radios, márgenes, tipografías, iconos y estados vacío/carga/error. |

Se reutilizan paleta, formas, iconos y materiales aprobados. Se permiten tamaños
distintos según jerarquía y accesibilidad; consistencia no significa forzar todos
los controles al mismo tamaño. Ningún panel debe reducir las teclas normales ni
invadir la navegación del sistema.

## Requisitos añadidos: dictado Pixel y panel GIF ampliado

Solicitados expresamente por el usuario después de la investigación sobre Rambler.

### Dictado inteligente equivalente en comportamiento

- Pulsar el micrófono habitual, hablar con naturalidad y terminar; insertar el
  resultado limpio al finalizar el procesamiento. Mostrar actividad y estado,
  sin confundir una transcripción parcial con el texto definitivo.
- Limpiar muletillas y falsos comienzos; resolver autocorrecciones expresas del
  hablante y estructurar puntuación. Preservar nombres, cifras, negaciones e intención.
- Admitir español y mezcla español/inglés como prioridad de validación.
- Editar por voz el borrador mediante peticiones naturales: sustituir una palabra,
  corregir una fecha, acortar, reformular o cambiar tono cuando se solicite.
  No transformar el tono automáticamente ni tratar el contenido dictado como
  una orden de edición sin una distinción fiable.
- Incluir deshacer, cancelación y descarte de resultados obsoletos. No enviar el
  mensaje ni operar otra aplicación por inferencia a partir de la voz.
- Un solo flujo y configuración sencilla; alternativa de dictado literal.
  El procesamiento remoto requiere explicación y autorización; los estados de
  cuota o red no bloquean la escritura normal.
- La meta es equivalencia funcional, no reutilizar servicios privados de Pixel.
  Evaluar el proveedor y modelo realmente disponibles. No prometer que las
  funciones avanzadas sean locales o funcionen sin conexión sin verificarlo.
- El componente Ramblr adaptado de trevornk es distinto de Rambler de Google;
  conservar componentes necesarios y atribuciones. Tener captura y transcripción
  no demuestra que la experiencia completa esté implementada.

### Panel GIF con altura adicional

- Ampliar la altura del panel multimedia, conservando el aspecto de Tactic y la
  geometría de las teclas normales. Como propuesta visual inicial, reservar
  aproximadamente un 20–25 % más de altura para el modo GIF; la cifra final se
  determina con el espacio útil del teléfono y se adapta en horizontal y DeX.
- Miniaturas verticales en dos columnas: respetar proporción, sin estirar ni
  recortar el contenido para fingir que cabe. Scroll vertical para más resultados.
- Cabecera/buscador, categorías, favoritos, enlaces, paginación y atribución
  deben caber completos o tener desplazamiento explícito. Ningún botón o etiqueta
  queda cortado, superpuesto o detrás de la navegación del sistema.
- Priorizar resultados: reducir espacios sobrantes antes de agrandar controles.
  Mantener visibles la búsqueda, ABC y configuración, y separar el contenido
  desplazable de las acciones y del pie.
- Entrar en búsqueda restaura las teclas nativas de tamaño normal y mantiene la
  consulta; volver a resultados recupera el panel ampliado y su posición de scroll.
  ABC restaura la altura normal del teclado principal.
- Comprobar tamaños de pantalla, escalado de texto, orientación, navegación por
  gestos y botones. No imponer una altura fija que tape todo el editor.

## Organización propuesta de los ajustes

1. Idiomas y escritura: idiomas, distribuciones, corrección, gestos y diccionario personal.
2. Apariencia y preferencias: tamaño, tema actual, fila numérica, sonido y vibración.
3. Herramientas: portapapeles, GIF/stickers, emojis, dictado, traducción y escritura a mano.
4. Gemini: configuración y corrector automático opcional.
5. Privacidad y datos: campos excluidos, aprendizaje, eliminación y respaldo.
6. Avanzado y Acerca de: ajustes técnicos necesarios, información y licencias.

No crear una página por interruptor. Accesos rápidos desde el panel correspondiente
llevan al mismo ajuste; no crean una segunda configuración.

## Orden y entregables

1. Inventario por pantalla y función, incluyendo ajustes originales y dependencias;
   matriz de correspondencia con Gboard instalado y capturas de la identidad actual.
2. Simplificación de navegación y configuración de Gemini; conservar datos y comportamiento útil.
3. Consistencia visual de paneles, diálogos y estados secundarios.
4. Retirada de funciones acordadas, migración y limpieza de código/dependencias.
5. Cierre de brechas funcionales identificadas y verificación de los flujos completos.

Cada fase tendrá cambios revisables. No agrupar toda la reescritura en una sola entrega.

## Criterios de aceptación

- Toda función auditada tiene destino: conservar, optimizar, rediseñar, retirar o brecha pendiente.
- Ninguna capacidad desaparece solo por tener controles complejos.
- La configuración básica permite escribir y usar herramientas sin elegir modelos ni motores.
- Apariencia principal y recursos aprobados conservados; subpaneles coherentes con ellos.
- Abrir/cerrar/buscar/cambiar de panel no modifica el borrador del editor.
- Red y descargas opcionales requieren su autorización correspondiente; errores no bloquean escritura.
- Sin pérdida de idiomas, palabras aprendidas, preferencias, claves ni fijados durante actualización.
- Pruebas específicas de escritura, gestos, edición, GIF, emojis, portapapeles, voz,
  traducción y tinta, según los cambios; compilación, lint y arranque de la APK final.
- No declarar completa la equivalencia funcional mientras existan brechas abiertas.
- Dictado Pixel: validar ejemplos de habla espontánea, autocorrección hablada,
  mezcla de idiomas, comandos de edición, deshacer y errores de red/cuota.
- Panel GIF: demostrar en el teléfono miniaturas verticales y controles completos,
  búsqueda con teclas normales y restauración correcta de alturas y borrador.

## Fuera de esta entrega documental

Este documento no cambia código, instala otra APK ni retira funciones. No incluye
un tema nuevo, un cambio de identidad, publicación o promesas de reproducción de
servicios propietarios. Las funciones ausentes detectadas se dimensionarán antes
de comprometer su implementación.

## Referencias

- Código inspeccionado: settings/screens, latin/ai, keyboard/gifs, manifiesto y TODO.md.
- Referencias de Gboard ya capturadas en /workspace/heliboard-verification/gboard-reference.
- Ayuda oficial: https://support.google.com/gboard?hl=es
- Escritura gestual: https://support.google.com/gboard/answer/2811346?hl=es
- Escritura a mano: https://support.google.com/gboard/answer/9108773?co=GENIE.Platform%3DAndroid&hl=es
- Rambler de Google: https://support.google.com/pixelphone/answer/17468539?hl=en
- Ramblr independiente: https://github.com/trevornk/ramblr
