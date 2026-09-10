/* SPDX-License-Identifier: GPL-3.0-only */
package helium314.keyboard.latin.settings;

/** Preferences owned by the iOS Glass layer, kept separate from upstream HeliBoard settings. */
public final class IosGlassPreferences {
    public static final String PREF_HAPTIC_STRENGTH = "ios_glass_haptic_strength";
    public static final int DEFAULT_HAPTIC_STRENGTH = 82;

    private IosGlassPreferences() {}
}
