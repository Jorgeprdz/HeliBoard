// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import helium314.keyboard.latin.utils.prefs

/**
 * Seeds the iOS Glass build with Jorge's known-good HeliBoard configuration.
 *
 * This intentionally runs only when the default SharedPreferences are completely empty.
 * Existing installs, restored Android backups, and upgrades are therefore left untouched.
 * Migration/runtime bookkeeping such as version_code and emoji_max_sdk is deliberately
 * excluded so HeliBoard can initialize those values normally.
 */
internal fun Context.applyIosGlassFreshInstallPreset() {
    val preferences = prefs()
    if (preferences.all.isNotEmpty()) return

    preferences.edit()
        .putBoolean("add_to_personal_dictionary", true)
        .putBoolean("show_dpad_key", true)
        .putBoolean("abc_after_emoji", false)
        .putBoolean("vibrate_on", true)
        .putBoolean("url_detection", true)
        .putBoolean("abc_after_numpad_space", true)
        .putBoolean("show_emoji_key", true)
        .putBoolean("theme_key_borders", true)
        .putBoolean("theme_auto_day_night", true)
        .putBoolean("use_contacts", false)
        .putBoolean("use_apps", false)
        .putBoolean("abc_after_clip", false)
        .putBoolean("ios_glass_visual_profile_v4", true)
        .putInt("clipboard_history_retention_time", 121)
        .putInt("ios_glass_haptic_strength", 100)
        .putInt("clipboard_history_files_size_limit", 1001)
        .putInt("vibration_duration_settings", 100)
        .putFloat("keyboard_height_scale", 0.8047954f)
        .putFloat("keyboard_height_scale_landscape", 0.79921055f)
        .putString("toolbar_keys", "0,3,11,4,7,6,10,5,13,14")
        .putString("theme_style", "Material")
        .putString("theme_colors", "dynamic")
        .putString("theme_colors_night", "dynamic")
        .putString("theme_day_night_mode", "system")
        .apply()
}
