# Rediseño de la interfaz de HeliBoard con Material 3 Expressive

Estado: propuesta de diseño para revisión. Este documento no contiene cambios de interfaz.

## Objetivo

Aplicar un lenguaje visual Material 3 Expressive coherente a la interfaz de la aplicación HeliBoard: Ajustes, navegación, menús, diálogos, selectores, controles de preferencias, iconos y asistente inicial. La apariencia y el comportamiento del teclado iOS actual deben permanecer intactos.

El proyecto ya usa Jetpack Compose Material 3 y colores dinámicos de Android en `Theme.kt`. El rediseño consolidará y extenderá ese sistema a toda la interfaz de la app, sin cambiar la estructura de navegación ni añadir otro framework visual.

## Diseño visible para el usuario

- Conservar destinos, nombres de ajustes, claves de preferencias, búsqueda, navegación atrás, flujos de importación/exportación y controles existentes de iOSGlass.
- Definir un tema compartido para las superficies de la app: colores dinámicos del sistema en Android 12+, una paleta clara/oscura coordinada en versiones anteriores, jerarquía tipográfica definida y una escala pequeña de formas con radios expresivos pero moderados.
- Reorganizar visualmente la pantalla principal de Ajustes en grupos legibles, con contenedores de icono por categoría y mayor jerarquía entre título y descripción. Mantener las categorías y rutas actuales.
- Unificar barras superiores, espaciado, encabezados de sección, estados seleccionados, switches, sliders y textos de apoyo en las pantallas internas.
- Actualizar menús, selectores, diálogos de confirmación e información, búsqueda, bienvenida/configuración inicial y estados vacíos o de error usando componentes Material 3 y tokens compartidos.
- Dar a los iconos de navegación y acciones un tratamiento coherente. Reutilizar o retocar los vectores existentes; no añadir una dependencia grande de iconos solo para este cambio.
- Mantener los colores de la interfaz de la app ligados a la paleta del sistema Android e independientes del tema de color elegido para el teclado. Las opciones de tema del teclado siguen afectando únicamente al teclado.
- Usar movimiento solo para orientar la navegación y comunicar estados; respetar la escala de animaciones del sistema.

## Límite estricto: el teclado no cambia

Esta fase solo modifica la interfaz de la app. No cambiar el renderizado del teclado, sus colores, geometría de teclas, superficies de preview, toolbar, emoji/clipboard, drawables iOSGlass ni el servicio de entrada. En particular, no modificar `KeyboardTheme.kt`, `Colors.kt`, `InputView.java`, `KeyboardSwitcher.java`, layouts/drawables del teclado ni el runtime de `latin/common/dynamic`. Las pantallas de Ajustes pueden recibir el nuevo estilo visual mientras mantienen sus controles y escrituras actuales de preferencias.

## Enfoque de implementación

1. Centralizar tokens exclusivos de la app para color, tipografía, formas y espaciado alrededor del composable `Theme` existente.
2. Actualizar primero los elementos compartidos: contenedor/búsqueda y barra superior de Ajustes, filas de preferencias, encabezados de sección, contenedores de icono y menús/diálogos comunes.
3. Revisar cada pantalla y el asistente inicial para reemplazar colores, espaciados y controles aislados por los elementos compartidos, conservando lógica de navegación y estado.
4. Verificar modo claro/oscuro, color dinámico de Android, fallback anterior a Android 12, escala de fuente grande, RTL, etiquetas traducidas largas y rutas existentes.
5. Ejecutar pruebas y build en GitHub Actions. No añadir infraestructura de screenshot tests ni dependencias de runtime nuevas sin una necesidad concreta y aprobación aparte.

## Zonas principales de código

- Tema y tokens compartidos: `app/src/main/java/helium314/keyboard/latin/utils/Theme.kt`.
- Contenedor y rutas de Ajustes: `settings/SettingsActivity.kt`, `settings/SettingsNavHost.kt` y `settings/SearchScreen.kt`.
- Controles de preferencias compartidos: `settings/preferences/Preference.kt` y composables relacionados.
- Diálogos y selectores: `settings/dialogs/`.
- Revisión de pantallas: `settings/screens/`, incluyendo apariencia, colores, idiomas/diseños, diccionarios, corrección, toolbar, avanzados, acerca de y datos de gestos.
- Bienvenida y superficies de ajustes del corrector: `settings/WelcomeWizard.kt` y `latin/spellcheck/SpellCheckerSettingsActivity.kt` donde usan Compose propio.
- Vectores de la app en `app/src/main/res/drawable/` solo si tintes y contenedores compartidos no bastan para conseguir coherencia.

## Alternativas evaluadas

1. **Actualizar el tema y los componentes compartidos (recomendado):** conservar la arquitectura y el comportamiento actuales mientras se aplica la nueva identidad visual mediante tokens y controles reutilizables. Cubre todas las pantallas con menor riesgo para el descubrimiento de ajustes y las preferencias guardadas.
2. **Crear un dashboard y una navegación nueva:** reorganizar categorías y destinos. Cambia más la experiencia, pero altera cómo se encuentran ajustes existentes y añade riesgos de accesibilidad sin ser necesario para el rediseño visual solicitado.
3. **Cambiar solo superficies:** modificar colores y algunas formas, dejando menús, iconos, filas de preferencias y diálogos como están. No cumple el alcance integral pedido.

## Validación y aceptación

- Pasan las pruebas existentes y el APK debug compila en `feature/dynamic-color-schemes`.
- Todas las pantallas, menús, selectores, diálogos y elementos de navegación de la app usan el tema compartido.
- Continúan funcionando las rutas, búsqueda, preferencias, importación/exportación, onboarding y opciones de iOSGlass.
- Los colores dinámicos y los modos claro/oscuro son coherentes, incluido el fallback en Android anterior a 12.
- El teclado usado para escribir mantiene su apariencia y comportamiento sin cambios.
- No se añaden frameworks de screenshot ni dependencias de runtime para el rediseño.

## Referencia

- [Material 3 en Compose, Android Developers](https://developer.android.com/develop/ui/compose/designsystems/material3) describe color, tipografía, formas, componentes y color dinámico como partes del sistema visual de Compose.
