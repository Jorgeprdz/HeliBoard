// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
    onClickWriting: () -> Unit = onClickLanguage,
    onClickLook: () -> Unit = onClickAppearance,
    onClickTools: () -> Unit = onClickToolbar,
    onClickPrivacy: () -> Unit = onClickAdvanced,
    onClickMore: () -> Unit = onClickAdvanced,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).then(Modifier.padding(innerPadding))
            ) {
                Preference(name = "Idiomas y escritura", description = enabledSubtypes.joinToString(", ") { it.displayName() },
                    onClick = onClickWriting, icon = R.drawable.ic_settings_languages) { NextScreenIcon() }
                Preference(name = "Apariencia y preferencias", description = "Tema, tamaño, sonido y vibración",
                    onClick = onClickLook, icon = R.drawable.ic_settings_appearance) { NextScreenIcon() }
                Preference(name = "Herramientas", description = "Portapapeles, emojis, GIF y dictado",
                    onClick = onClickTools, icon = R.drawable.ic_settings_toolbar) { NextScreenIcon() }
                val context = LocalContext.current
                Preference(name = "Gemini", description = "Asistencia y corrección automática opcional", onClick = {
                    context.startActivity(android.content.Intent(context, helium314.keyboard.latin.ai.AiSettingsActivity::class.java))
                }, icon = R.drawable.ic_settings_correction) { NextScreenIcon() }
                Preference(name = "Privacidad y datos", description = "Aprendizaje, exclusiones y respaldos",
                    onClick = onClickPrivacy, icon = R.drawable.ic_settings_advanced) { NextScreenIcon() }
                Preference(name = "Avanzado y acerca de", description = "Controles detallados, versión y licencias",
                    onClick = onClickMore, icon = R.drawable.ic_settings_about) { NextScreenIcon() }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
