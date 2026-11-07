# Cobertura del rediseño One UI

Referencia vigente: tribalfs/oneui-design, One UI 8/SESL8. Adaptación en Compose y Views compatible con API21; no se integra el binario SESL. Estado: cambios en código; la cobertura visual/dispositivo se registra aparte.

| Superficie | Archivo | Estado |
|---|---|---|
| settings/screens | AboutScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | AdvancedScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | AppearanceScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | ColorsScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | DebugScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | DictionaryScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | GestureTypingScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | LanguageScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | MainSettingsScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | PersonalDictionariesScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | PersonalDictionaryScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | PreferencesScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | SecondaryLayoutScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | SubtypeScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | TacticSettingsScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | TextCorrectionScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/screens | ToolbarScreen.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ColorPickerDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ColorThemePickerDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ConfirmationDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | CustomizeIconsDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | DictionaryDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | InfoDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | LayoutEditDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | LayoutPickerDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ListPickerDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | MultiListPickerDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | NewDictionaryDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ReorderDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | SliderDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | TextInputDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ThreeButtonAlertDialog.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/dialogs | ToolbarKeysCustomizer.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | BackgroundImagePreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | BackupRestorePreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | CustomFontPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | KeyboardScalePreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | ListPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | LoadGestureLibPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | Preference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | ReorderSwitchPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | SliderPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | SwitchPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| settings/preferences | TextInputPreference.kt | Componentes compartidos One UI; dispositivo pendiente |
| latin/ai/panels | PanelDraft.java | Componentes compartidos One UI; dispositivo pendiente |
| latin/ai/panels | PanelHost.java | Componentes compartidos One UI; dispositivo pendiente |
| latin/ai/panels | PanelViews.java | Componentes compartidos One UI; dispositivo pendiente |
| latin/ai/panels | TranslationPanel.java | Componentes compartidos One UI; dispositivo pendiente |
| Actividad Views | latin/ai/AiSettingsActivity.java | Componentes compartidos One UI; dispositivo pendiente |
| Actividad Views | latin/ai/AiSharedTextActivity.java | Componentes compartidos One UI; dispositivo pendiente |
| Actividad Views | latin/ai/cloud/TypingCloudActivity.java | Componentes compartidos One UI; dispositivo pendiente |
| Actividad Views | latin/ai/dictation/DictationPermissionActivity.java | Componentes compartidos One UI; dispositivo pendiente |
| Actividad Views | latin/gifs/GiphySettingsActivity.java | Componentes compartidos One UI; dispositivo pendiente |

## Integración transversal

- SearchScreen: cabecera ampliada que colapsa, fondo neutral y acciones de navegación coherentes.
- Theme/Preference: jerarquía 28/20/17/13 sp, filas redondeadas, paleta clara/oscura, OneUiSwitch con semántica y zona táctil 52x48 dp.
- ThreeButtonAlertDialog y diálogos derivados: superficies neutras, radio26, márgenes24 y tipografía compartida. Diálogos Material3 heredan Theme.
- OneUiMenuStyle/AiMenuTheme: formularios Views (Gemini, voz/privacidad/avanzado, texto compartido, nube y GIPHY), sin cambiar valores, consentimientos ni callbacks.
- GIF, tinta, traducción y dictado: paleta/controles del panel compartidos; portapapeles y pestañas emoji adaptados. El dibujo de teclas nativas y las variantes de emoji no se reescriben.
- Selector Android y permisos de cuenta/micrófono son pantallas del sistema. Investigación y grabadora retirada no son superficies de producción a reintroducir.

## Límites de verificación

No se certifican todos los submenús por heredar el tema. Se probarán rutas representativas y se registrarán las restantes. Horizontal: funcionalidad y accesos, sin perfeccionamiento visual, por instrucción del usuario. Gemini remoto sigue sin conexión validada.
