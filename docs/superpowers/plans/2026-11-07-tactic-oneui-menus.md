# Unificación One UI de menús de Tactic

Autorización: petición activa del usuario de rediseñar todos los menús y submenús con https://github.com/OneUIProject/oneui-design y después https://github.com/tribalfs/oneui-design, eligiendo la más moderna. Se elige tribalfs main, One UI 8/SESL8 (README consultado). Sustituye la restricción previa de usar exclusivamente el aspecto actual en superficies secundarias. El teclado principal conserva su identidad, datos y geometría.

## Decisiones

- La referencia actual tribalfs depende de forks SESL8 de AndroidX/Material y su distribución indica GitHub Packages autenticado; la referencia antigua OneUIProject exige minSdk 23. Tactic usa Compose y Views y minSdk 21. Adaptar su lenguaje visual en componentes compartidos de ambas interfaces sin importar forks incompatibles ni elevar minSdk. No presentar esta adaptación como integración binaria de SESL.
- One UI: fondo neutral, listas blancas o gris oscuro agrupadas y redondeadas, títulos amplios con navegación compacta al desplazarse, jerarquía 28/20/17/13 sp, controles legibles, márgenes laterales 16/24 dp, separadores discretos, acciones y diálogos coherentes, claro/oscuro. Acento Tactic conservado.
- Solo menús, paneles y actividades propias. Selector/permiso Android pertenece al sistema. Teclado principal, vista previa del teclado, claves y preferencias no cambian.
- Ejecutar sin delegación ni nueva ronda de aprobación: el usuario ha elegido explícitamente el diseño y autorizado continuar hasta instalar y probar.

## Entregas y verificación

1. Terminar corrección R27 del pie GIPHY bajo navegación (prueba roja reproducida; cuatro pruebas verdes; release/lint en curso), instalar y comprobar horizontal.
2. Definir tokens One UI compartidos y aplicar a Theme Compose, SearchScreen, Preference, categorías, switches y todos los diálogos Compose. Inventariar rutas y excepción de pantalla que no use componentes compartidos.
3. Aplicar la misma base a actividades Views: Gemini y sus secciones, GIPHY, texto compartido y nube. Formularios desplazables, título y volver consistentes, filas/controles/tipografía coherentes, respetar insets. Adaptar menús/contextos del IME y paneles secundarios; no cambiar claves nativas.
4. Verificar estilos, compilación, lint y regresiones esenciales. Probar navegación, submenús, diálogo, búsqueda, GIF, error Gemini, fuente grande y horizontal en fixture seguro. Conservar datos. Instalar actualización firmada final y documentar cobertura real, pendientes y fallos remotos.

## Estado de partida

R27.1 instalada (20261110), fuente 62ef73e. CI 42419406184 aprobó 646 tests, cero fallos/errores/omitidos, lint, release y firma. GIF buscado con teclas reales (cat) sin cambiar borrador prueba; commitContent recibido correctamente. Error Gemini persiste al comprobar clave guardada y modelo automático; no hay validación remota exitosa. Nueva corrección 9e3a3b2 reserva navegación en browse y añade prueba de regresión; release en curso.

## Cobertura que debe cerrarse

No declarar todos los menús terminados solo por cambiar Theme. Revisar rutas Compose, diálogos, actividades Views, menús del teclado y paneles de traducción/tinta/dictado/GIF/portapapeles/emojis. Registrar lo no probado. Mantener contenido fijado y datos: no borrar datos ni extraer claves.
