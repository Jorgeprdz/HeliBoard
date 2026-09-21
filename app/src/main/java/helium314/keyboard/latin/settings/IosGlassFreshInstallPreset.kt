// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import helium314.keyboard.latin.utils.prefs

private const val PREF_BILINGUAL_CLEANUP_V1 = "jorge_bilingual_es_en_cleanup_v1"

private const val EN_US_SUBTYPE =
    "en-US§SecondaryLocales=es-419,SupportTouchPositionCorrection,TrySuppressingImeSwitcher"

private const val ES_419_SUBTYPE =
    "es-419§KeyboardLayoutSet=MAIN:qwerty+,SecondaryLocales=en-US,SupportTouchPositionCorrection"

private const val BILINGUAL_SUBTYPES = "$EN_US_SUBTYPE;$ES_419_SUBTYPE"

/**
 * Seeds Jorge's preferred iOS Glass defaults on a completely fresh install, then applies
 * one-shot personal migrations that must also affect existing installs.
 */
internal fun Context.applyIosGlassFreshInstallPreset() {
    val preferences = prefs()

    if (preferences.all.isEmpty()) {
        preferences.edit()
            .putBoolean("add_to_personal_dictionary", true)
            .putBoolean("show_dpad_key", false)
            .putBoolean("abc_after_emoji", true)
            .putBoolean("vibrate_on", true)
            .putBoolean("url_detection", true)
            .putBoolean("abc_after_numpad_space", true)
            .putBoolean("show_emoji_key", true)
            .putBoolean("theme_key_borders", true)
            .putBoolean("theme_auto_day_night", true)
            .putBoolean("use_contacts", true)
            .putBoolean("use_apps", true)
            .putBoolean("abc_after_clip", true)
            .putBoolean("ios_glass_visual_profile_v4", true)
            .putInt("clipboard_history_retention_time", 121)
            .putInt("ios_glass_haptic_strength", 100)
            .putInt("clipboard_history_files_size_limit", 1001)
            .putInt("vibration_duration_settings", 100)
            .putFloat("keyboard_height_scale_false_false", 0.8047954f)
            .putFloat("keyboard_height_scale_true_false", 0.79921055f)
            .putString(
                "pinned_toolbar_keys",
                "CLIPBOARD:true|SETTINGS:true|VOICE:true|NUMPAD:false|DPAD:false|UNDO:false|REDO:false|SELECT_ALL:false|SELECT_WORD:false|COPY:false|CUT:false|PASTE:false|ONE_HANDED:false|FLOATING:false|SPLIT:false|INCOGNITO:false|AUTOCORRECT:false|CLEAR_CLIPBOARD:false|EMOJI:false|LEFT:false|RIGHT:false|UP:false|DOWN:false|WORD_LEFT:false|WORD_RIGHT:false|PAGE_UP:false|PAGE_DOWN:false|FULL_LEFT:false|FULL_RIGHT:false|PAGE_START:false|PAGE_END:false"
            )
            .putString("theme_style", "Material")
            .putString("theme_colors", "dynamic")
            .putString("theme_colors_night", "dynamic")
            .putString(
                "toolbar_keys",
                "SETTINGS:true|VOICE:true|CLIPBOARD:true|UNDO:false|REDO:false|SELECT_WORD:false|COPY:true|PASTE:true|LEFT:false|RIGHT:false|NUMPAD:false|DPAD:false|SELECT_ALL:false|CUT:false|ONE_HANDED:false|FLOATING:false|SPLIT:false|INCOGNITO:false|AUTOCORRECT:false|CLEAR_CLIPBOARD:false|EMOJI:false|UP:false|DOWN:false|WORD_LEFT:false|WORD_RIGHT:false|PAGE_UP:false|PAGE_DOWN:false|FULL_LEFT:false|FULL_RIGHT:false|PAGE_START:false|PAGE_END:false"
            )
            .apply()
    }

    if (!preferences.getBoolean(PREF_BILINGUAL_CLEANUP_V1, false)) {
        val editor = preferences.edit()
            .putString(Settings.PREF_ADDITIONAL_SUBTYPES, "")
            .putString(Settings.PREF_ENABLED_SUBTYPES, BILINGUAL_SUBTYPES)
            .putString(Settings.PREF_SELECTED_SUBTYPE, ES_419_SUBTYPE)
            .putBoolean(PREF_BILINGUAL_CLEANUP_V1, true)

        // GIF was removed from this personal build. Purge stale entries from existing installs.
        listOf(
            Settings.PREF_TOOLBAR_KEYS,
            Settings.PREF_PINNED_TOOLBAR_KEYS,
            Settings.PREF_CLIPBOARD_TOOLBAR_KEYS
        ).forEach { key ->
            val value = preferences.getString(key, null) ?: return@forEach
            val cleaned = value.split("|")
                .filterNot { it.substringBefore(":") == "GIF" }
                .joinToString("|")
            editor.putString(key, cleaned)
        }

        editor.apply()
    }
}
