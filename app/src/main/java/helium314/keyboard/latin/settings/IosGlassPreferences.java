/* SPDX-License-Identifier: GPL-3.0-only */
package helium314.keyboard.latin.settings;

import android.content.SharedPreferences;

/** Preferences owned by the iOS Glass layer, kept separate from upstream HeliBoard settings. */
public final class IosGlassPreferences {
    public static final String PREF_HAPTIC_STRENGTH = "ios_glass_haptic_strength";
    public static final int DEFAULT_HAPTIC_STRENGTH = 82;

    public static final String PREF_BLUR_INTENSITY = "ios_glass_blur_intensity";
    public static final int DEFAULT_BLUR_INTENSITY = 50;
    private static final int MIN_BLUR_INTENSITY = 0;
    private static final int MAX_BLUR_INTENSITY = 100;
    private static final int MIN_BLUR_RADIUS_DP = 16;
    private static final int MAX_BLUR_RADIUS_DP = 160;

    private IosGlassPreferences() {}

    public static int getBlurIntensity(final SharedPreferences prefs) {
        final int value = prefs.getInt(PREF_BLUR_INTENSITY, DEFAULT_BLUR_INTENSITY);
        return Math.max(MIN_BLUR_INTENSITY, Math.min(MAX_BLUR_INTENSITY, value));
    }

    /** Maps the user-facing 0..100 control to a useful cross-window blur radius. */
    public static int resolveBlurRadiusDp(final SharedPreferences prefs) {
        final float fraction = getBlurIntensity(prefs) / 100.0f;
        return Math.round(MIN_BLUR_RADIUS_DP
                + fraction * (MAX_BLUR_RADIUS_DP - MIN_BLUR_RADIUS_DP));
    }
}
