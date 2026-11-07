// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai

import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.components.OneUiSwitch
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.preferences.Preference

/** Same components as the rest of settings. Plaintext credentials never enter saved state. */
object AiSettingsUi {
    @JvmStatic fun install(activity: AiSettingsActivity) {
        activity.setContent { Theme { Screen(activity) } }
    }

    @Composable private fun Info(text: String) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp))
    }

    @Composable private fun Field(label: String, value: String, change: (String) -> Unit,
                                  hint: String = "", secret: Boolean = false) {
        OutlinedTextField(value = value, onValueChange = change, label = { Text(label) },
            placeholder = { Text(hint) }, singleLine = true, shape = RoundedCornerShape(20.dp),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false,
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    }

    @Composable private fun Toggle(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
        Preference(name = title, description = description, onClick = { change(!checked) }) {
            OneUiSwitch(checked = checked, onCheckedChange = change)
        }
    }

    @Composable private fun Action(title: String, description: String? = null, action: () -> Unit) {
        Preference(name = title, description = description, onClick = action) { NextScreenIcon() }
    }

    @Composable private fun Screen(activity: AiSettingsActivity) {
        val prefs = remember { activity.uiPreferences() }
        var revision by remember { mutableIntStateOf(0) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        // Observe preference updates without copying encrypted credentials into composition.
        val current = revision.let { prefs }
        val section = activity.intent.getStringExtra("section") ?: "gemini"
        val title = when (section) {
            "advanced" -> "Gemini · Avanzado"
            "voice" -> "Dictado"
            "privacy" -> "Privacidad y datos"
            else -> "Gemini"
        }
        var status by remember { mutableStateOf(when (section) {
            "advanced" -> "El modelo se elige automáticamente al comprobar la conexión."
            "voice" -> "Hasta 32 términos. No introduzcas datos sensibles."
            "privacy" -> "Estas exclusiones se comparten entre las funciones de asistencia."
            else -> "Estado: ${AiSettingsActivity.automaticStatus(activity)}"
        }) }
        var key by remember { mutableStateOf("") }
        var model by remember { mutableStateOf(current.getString("model_override", "") ?: "") }
        var vocabulary by remember { mutableStateOf(current.getString("dictation_vocabulary", "") ?: "") }
        var excluded by remember { mutableStateOf(current.getString("excluded_apps", "") ?: "") }
        var language by remember { mutableStateOf(AiSettingsActivity.dictationLanguage(activity)) }
        var languages by remember { mutableStateOf(false) }
        var clear by remember { mutableStateOf(false) }
        val keyState = remember(revision) { AiSettingsActivity.keyState(activity) }
        SearchScreen<Unit>(onClickBack = activity::finish, title = { Text(title) },
            filteredItems = { emptyList() }, itemContent = {}, icon = {}) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp)) {
                when (section) {
                    "advanced" -> {
                        Toggle("Permitir acciones manuales con Google",
                            "Autoriza enviar el texto que elijas para corregir, reformular o traducir. No activa la corrección automática ni el dictado de nube.",
                            AiSettingsActivity.networkAllowed(activity)) { on ->
                            current.edit().putBoolean("enabled", on || current.getBoolean("automatic_gemini", false))
                                .putBoolean("network_consent", on).apply()
                        }
                        Field("Modelo opcional", model, { model = it }, "Vacío: elegir automáticamente")
                        Info(status)
                        Action("Guardar y comprobar modelo") {
                            activity.checkConnection("", model, true) { status = it }
                        }
                        Action("Borrar clave", "Se conserva la escritura local y tus diccionarios") { clear = true }
                    }
                    "voice" -> {
                        Toggle("Dictado desde el micrófono del teclado", "Literal: utiliza el motor instalado sin cambiar a nube silenciosamente.",
                            AiSettingsActivity.dictationEnabled(activity)) { current.edit().putBoolean("dictation", it).apply() }
                        Toggle("Dictado inteligente con Gemini",
                            "Activarlo autoriza enviar a Google el audio que grabes desde el micrófono para obtener texto limpio. La edición por voz también envía el texto que elijas editar. No activa la revisión automática y puede consumir cuota.",
                            AiSettingsActivity.intelligentDictation(activity) && current.getBoolean("voice_network_consent", false)) { on ->
                            current.edit().putString("dictation_mode", if (on) "intelligent" else "literal")
                                .putBoolean("voice_network_consent", on).apply()
                        }
                        Action("Configurar conexión de Gemini") { activity.openSection("gemini") }
                        val names = mapOf("auto" to "Automático si el motor lo admite", "es-MX" to "Español México", "en-US" to "English US")
                        Action("Idioma del dictado literal", names[language]) { languages = true }
                        Field("Vocabulario opcional", vocabulary, { vocabulary = it }, "Nombres y términos")
                        Info(status)
                        Action("Guardar opciones de voz") { status = activity.saveVoiceOptions(language, vocabulary) }
                        Info("Captura adaptada de Ramblr · © 2026 Trevor NK · GPLv3+. Proyecto independiente de Rambler de Google. github.com/trevornk/ramblr")
                    }
                    "privacy" -> {
                        Toggle("Escritura inteligente local", "Conserva los diccionarios y el aprendizaje útil.",
                            AiSettingsActivity.typingEnabled(activity)) { current.edit().putBoolean("intelligent_typing", it).apply() }
                        Toggle("Personalización local de sugerencias", "Aprendizaje opcional de tu escritura en el dispositivo.",
                            current.getBoolean("personal_style_consent", false)) { current.edit().putBoolean("personal_style_consent", it).apply() }
                        Info("Gemini solo usa la red para las funciones que autorices. Los campos protegidos y aplicaciones excluidas quedan fuera.")
                        Field("Aplicaciones excluidas", excluded, { excluded = it }, "Paquetes separados por coma")
                        Info(status)
                        Action("Guardar exclusiones") {
                            current.edit().putString("excluded_apps", excluded.trim()).apply(); status = "Exclusiones guardadas."
                        }
                        Action("Borrar aprendizaje local", "El diccionario personal manual se conserva") { clear = true }
                        Action("Respaldo cifrado del aprendizaje") {
                            activity.startActivity(Intent(activity, helium314.keyboard.latin.ai.cloud.TypingCloudActivity::class.java))
                        }
                    }
                    else -> {
                        Info("Asistencia opcional. La escritura y corrección local no necesitan conexión.")
                        Toggle("Corrección automática con Gemini",
                            "Activarlo autoriza enviar fragmentos del campo actual a Google durante las pausas. Conserva intención y tono; retroceso deshace. Se excluyen contraseñas, incógnito y aplicaciones protegidas.",
                            current.getBoolean("automatic_gemini", false)) { on ->
                            current.edit().putBoolean("automatic_gemini", on).putBoolean("enabled", true).apply()
                            if (!on) { AiSettingsActivity.setAutomaticStatus(activity, "Desactivado"); status = "Desactivado" }
                        }
                        Info(status)
                        Field("Clave de API", key, { key = it },
                            if (keyState == AiSettingsActivity.KeyState.AVAILABLE) "Clave guardada en este dispositivo" else "Pega únicamente tu clave", true)
                        Action("Guardar y comprobar conexión") {
                            val submitted = key
                            if (key.isBlank() || AiConnectionStatus.validKey(key.trim())) key = ""
                            activity.checkConnection(submitted, "", false) { status = it }
                        }
                        Action("Opciones avanzadas de Gemini", "Permiso manual y modelo opcional") { activity.openSection("advanced") }
                        Action("Dictado", "Literal o inteligente, con autorización independiente") { activity.openSection("voice") }
                        Action("Privacidad y datos", "Exclusiones, aprendizaje y respaldo") { activity.openSection("privacy") }
                    }
                }
            }
        }
        if (languages) ListPickerDialog(onDismissRequest = { languages = false },
            items = listOf("auto", "es-MX", "en-US"), selectedItem = language,
            onItemSelected = { language = it; languages = false },
            title = { Text("Idioma del dictado literal") },
            getItemName = { TextName -> when (TextName) { "es-MX" -> "Español México"; "en-US" -> "English US"; else -> "Automático si el motor lo admite" } })
        if (clear) ConfirmationDialog(onDismissRequest = { clear = false },
            title = { Text(if (section == "advanced") "¿Borrar la clave de Gemini?" else "¿Borrar aprendizaje local?") },
            content = { Text(if (section == "advanced") "La escritura local y tus diccionarios se conservan." else "El diccionario personal manual se conserva.") },
            confirmButtonText = "Borrar", onConfirmed = {
                if (section == "advanced") { activity.clearSavedKey(); status = "Clave eliminada. La escritura local sigue disponible." }
                else { AiSettingsActivity.clearLearnedData(activity); status = "Aprendizaje local borrado." }
            })
    }
}
