// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ai.AiSettingsActivity
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.preferences.Preference

/** New task hierarchy, same preference storage and existing destinations. */
@Composable
fun TacticSettingsScreen(group: String, onClickBack: () -> Unit, navigate: (String) -> Unit) {
    val context = LocalContext.current
    (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()?.value
    val title = when (group) {
        "writing" -> "Idiomas y escritura"
        "look" -> "Apariencia y preferencias"
        "tools" -> "Herramientas"
        "privacy" -> "Privacidad y datos"
        else -> "Avanzado y acerca de"
    }
    fun ai(section: String) {
        context.startActivity(Intent(context, AiSettingsActivity::class.java).putExtra("section", section))
    }
    SearchSettingsScreen(onClickBack, title, emptyList()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            @Composable fun link(label: String, description: String, destination: String) {
                Preference(name = label, description = description, onClick = { navigate(destination) }) { NextScreenIcon() }
            }
            @Composable fun setting(key: String) { SettingsActivity.settingsContainer[key]?.Preference() }
            when (group) {
                "writing" -> {
                    link("Idiomas y distribuciones", "Añadir idiomas y cambiar su distribución", SettingsDestination.Languages)
                    link("Corrección y sugerencias", "Autocorrección, predicciones y emojis", SettingsDestination.TextCorrection)
                    if (helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib || helium314.keyboard.latin.ai.local.LocalGestureEngine.isSupported())
                        link("Escritura gestual", "Escribir deslizando y ajustar los gestos", SettingsDestination.GestureTyping)
                    link("Diccionario personal", "Tus palabras y abreviaturas", SettingsDestination.PersonalDictionaries)
                    link("Diccionarios de idiomas", "Gestionar los datos de los idiomas", SettingsDestination.Dictionaries)
                    link("Símbolos y distribuciones adicionales", "Números, símbolos y alternativas", SettingsDestination.Layouts)
                }
                "look" -> {
                    link("Apariencia del teclado", "Tema, colores y tamaño", SettingsDestination.Appearance)
                    setting(Settings.PREF_SHOW_NUMBER_ROW)
                    setting(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY)
                    setting(Settings.PREF_SHOW_EMOJI_KEY)
                    setting(Settings.PREF_VIBRATE_ON)
                    setting(Settings.PREF_SOUND_ON)
                }
                "tools" -> {
                    link("Barra de herramientas", "Elegir y ordenar tus accesos", SettingsDestination.Toolbar)
                    Preference(name = "Dictado", description = "Dictado literal o inteligente", onClick = { ai("voice") }) { NextScreenIcon() }
                    setting(Settings.PREF_SUGGEST_EMOJIS)
                    setting(Settings.PREF_INLINE_EMOJI_SEARCH)
                    setting(Settings.PREF_ENABLE_CLIPBOARD_HISTORY)
                    setting(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME)
                    setting(Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST)
                    Preference(name = "GIF y stickers", description = "Configurar el proveedor de búsqueda", onClick = {
                        context.startActivity(Intent(context, helium314.keyboard.latin.gifs.GiphySettingsActivity::class.java))
                    }) { NextScreenIcon() }
                }
                "privacy" -> {
                    setting(Settings.PREF_ALWAYS_INCOGNITO_MODE)
                    setting(Settings.PREF_KEY_USE_PERSONALIZED_DICTS)
                    setting(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT)
                    Preference(name = "Privacidad de la asistencia", description = "Aplicaciones excluidas y aprendizaje local", onClick = { ai("privacy") }) { NextScreenIcon() }
                    setting(SettingsWithoutKey.BACKUP_RESTORE)
                }
                else -> {
                    link("Comportamiento avanzado", "Gestos, pulsaciones y acciones especiales", SettingsDestination.Advanced)
                    link("Preferencias detalladas", "Sonidos, símbolos, archivos y otros controles", SettingsDestination.Preferences)
                    Preference(name = "Gemini · Avanzado", description = "Acciones manuales y modelo opcional", onClick = { ai("advanced") }) { NextScreenIcon() }
                    link("Acerca de Tactic", "Versión, información y licencias", SettingsDestination.About)
                }
            }
        }
    }
}
