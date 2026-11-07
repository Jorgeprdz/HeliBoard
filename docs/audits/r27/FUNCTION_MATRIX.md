# Matriz funcional R27

Base: 64af9ce. Referencia instalada: Gboard 18.5.4.993608717-beta-arm64-v8a,
código176049710, español(México) e inglés(EE.UU.). Teléfono Samsung SM_S931B;
la presencia de Gboard no implica disponer de las exclusivas de un Pixel.
Capturas actuales y previas: rutas en baseline.json. Ajustes originales desglosados
en settings-inventory.csv. «Existente» describe código/control; no certifica calidad ni equivalencia.

| Función / subfunción | Estado comprobado en código | Destino | Dependencias / datos | Verificación pendiente o referencia |
| --- | --- | --- | --- | --- |
| Letras, símbolos, números, mayúsculas | Existente | Conservar | MainKeyboardView, recursos aprobados | Captura idéntica y escritura real |
| Idiomas, distribuciones, cambio de idioma | Existente | Conservar/optimizar | SubtypeSettings y diccionarios | es-MX/en-US y cambio de app |
| Predicción/corrección bilingüe local | Existente | Optimizar | InputLogic, diccionarios, PersonalTypingStore | Calidad, cursor, datos protegidos |
| Deshacer corrección con retroceso | Existente | Conservar | AiEditTransaction/InputLogic | Doble corrección, composición |
| Gestos y motor de deslizamiento | Existente | Conservar/optimizar | JNI y LocalGestureEngine | Palabras, pausas, orientación |
| Cursor por espacio y borrado gestual | Existente | Conservar | callbacks de InputLogic | No filtrar gestos del buscador al chat |
| Seleccionar/cortar/copiar/pegar/deshacer/rehacer | Existente | Conservar | ToolbarUtils y InputConnection | Editor compatible y campo protegido |
| Una mano, flotante y dividido | Controles/motores existentes | Conservar/validar | ToolbarKey, FloatingKeyboardUtils | No afirmar ausencia; falta comprobar DeX/orientación |
| Emojis: panel, búsqueda en línea y sugerencia | Existente | Conservar/optimizar/rediseñar | EmojiPalettes, 160 paquetes incluidos | es/en, escala de fuente, inserción |
| Sticker de texto en sugerencias | Existente; envío probado en R261 | Conservar | TextStickerStore, PNG privado | Convivencia con emoji y sugerencia |
| GIF/stickers: búsqueda, tendencias, recientes y favoritos | Existente, geometría incompleta | Optimizar/rediseñar | GifPanel, GiphyClient, metadatos locales y clave | Altura/controles/gestos/borrador |
| GIF vertical y relación de aspecto | Incompleta: alturas arbitrarias | Optimizar | GifItem no persiste dimensiones | Retratos/paisajes, sin recortes |
| GIF: acciones por miniatura | Incompleta: overlay tapa imagen | Rediseñar | GifPanel.render | Controles separados y accesibles |
| GIF: volver a búsqueda y posición | Consulta existe, scroll no garantizado | Optimizar | GifPanel/KeyboardSwitcher | Letras normales y restauración |
| Portapapeles: historial/fijados/caducidad | Existente | Conservar/rediseñar | ClipboardHistory y preferencias | Pegar/borrar/fijar, no perder datos |
| Dictado literal | Existente vía recognizer Android | Conservar | LocalSpeechRecognizer, permiso micro | Motor instalado y sin red implícita |
| Dictado inteligente tipo Pixel | Incompleta | Implementar/validar | RamblrAudioCapture, GeminiProvider, DictationSession | Hablar/terminar/limpiar/insertar |
| Autocorrección hablada y muletillas | No está como pipeline único | Implementar/validar | Modelo de voz/texto y política conservadora | Nombres/cifras/negaciones/es-en |
| Órdenes naturales de edición por voz | Ausente como flujo integrado | Implementar/validar | Borrador acotado + respuesta estructurada | Distinguir contenido y orden, deshacer |
| Dictado: alternativa literal ante errores | Parcial | Optimizar | Recognizer, estados y consentimiento | Red/cuota/modelo sin bloqueo |
| Traducción en panel | Existente | Conservar/rediseñar | TranslationPanel, proveedor | Fuente/destino, salida y cancelación |
| Escritura a mano | Existente, runtime opcional | Conservar/rediseñar | MLKit/WorkManager/CE/JNI protegidos | Inicialización, modelo y reconocimiento |
| Gemini automático | Existente, conexión no verificada con clave actual | Optimizar | AutomaticReviewGate, proveedor, clave cifrada | Consentimiento, errores, respuestas tardías |
| Corregir/reformular/traducir selección | Operaciones existentes, menú excesivo | Reubicar | AiKeyboardAssistant y snapshot editor | Selección válida, no reemplazo tardío |
| Menú de resumir/expandir/respuesta/leer | Existente | Retirar catálogo general | Ramas de acciones | No retirar acciones contextuales útiles |
| Grabadora separada | Existente | Sustituir por dictado integrado | Captura útil se conserva | No borrar infraestructura antes de reemplazo |
| Qwen local experimental 491MB | Existente/optativo | Retirar integración | NativeWritingModel, WritingModelFiles, CMake | Revisar JNI/reflexión y consumidores |
| Perfil/inspector de estilo | Existente/optativo | Retirar inspector experimental | PersonalStyleProfile/SQLite | Preservar aprendizaje de palabras |
| Agenda/contactos/entidades | Existente como menú auxiliar | Retirar ese menú | CalendarResolver/EntityRecognizer | No confundir con sugerencias de contactos |
| Investigación de gestos | Rutas presentes, entrada temporal | Excluir en producción | gesturedata + provider | Búsqueda/deep links no reabren en release |
| Ajustes originales: geometría, símbolos, tiempos y sonidos | Existente; inventario CSV | Conservar/agrupación/Avanzado | Mismas claves de preferencias | Valores y migración sin restablecer |
| Ajustes Gemini/modelo/red/clave | Existente; pantalla muy extensa | Simplificar/rediseñar | Android Keystore + consentimientos separados | Validar antes de guardar; estado accionable |
| Privacidad: exclusiones/incógnito/campos sensibles | Existente | Conservar y hacer accesible | SensitiveInputPolicy/Settings | No enviar texto ni audio protegido |
| Respaldo/restauración y nube de aprendizaje | Existente | Agrupar, sin activar nube | BackupRestorePreference/TypingCloudActivity | Datos/fijados/claves conservados |
| Apariencia, colores por app y glass | Existente/aprobada | Conservar intacta | Recursos/renderers y firma | check-r26-appearance + captura |
| Accesibilidad y tamaño de texto | Existente; nuevos paneles requieren revisión | Conservar/validar | Etiquetas, foco y escalado | Texto grande y botones completos |

## Brechas que siguen abiertas

- Calidad y latencia equivalentes a Gboard no se deducen de tener las funciones.
- Rambler exige implementar y probar interpretación de voz y edición contextual;
  la API Gemini no es acceso al servicio privado de Pixel.
- Se deben comprobar funciones dependientes del dispositivo, idioma y proveedor.
- No se certifica dictado, traducción ni Gemini remoto sin pruebas reales autorizadas.

## Primera entrega presentada

Altura GIF adaptable y miniaturas sin acciones superpuestas; dimensiones conservadas
en metadatos; ajustes por tarea y Gemini con configuración breve y errores claros.
Mantener claves de preferencias y motores nativos. Sin instalar otra APK en esta
fase hasta completar las verificaciones y revisar el alcance de instalación activo.
