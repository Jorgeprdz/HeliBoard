/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import helium314.keyboard.keyboard.KeyboardTheme;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.KtxKt;

/** Applies the iOS-inspired adaptive frosted surface used by the Jorgeprdz HeliBoard fork. */
public final class IosGlassController {
    private static final int BLUR_RADIUS_DP = 80;
    private static final int PANEL_CORNER_RADIUS_DP = 18;
    private static final String PREF_VISUAL_PROFILE_V3 = "ios_glass_visual_profile_v3";

    private IosGlassController() {}

    public static void apply(final View inputView, final View keyboardPanel) {
        if (inputView == null || keyboardPanel == null) {
            return;
        }

        ensureVisualProfile(inputView.getContext());

        final boolean dark = isNightMode(inputView.getResources().getConfiguration());
        final GradientDrawable frost = new GradientDrawable();
        frost.setShape(GradientDrawable.RECTANGLE);
        // Day: iOS-like pale grey/white sheet. Night: smoked dark grey sheet.
        // Keep enough opacity that the app behind never competes with key legends if OEM blur is off.
        frost.setColor(dark
                ? Color.argb(228, 28, 28, 30)
                : Color.argb(238, 238, 238, 242));
        frost.setCornerRadii(new float[] {
                dp(inputView, PANEL_CORNER_RADIUS_DP), dp(inputView, PANEL_CORNER_RADIUS_DP),
                dp(inputView, PANEL_CORNER_RADIUS_DP), dp(inputView, PANEL_CORNER_RADIUS_DP),
                0f, 0f, 0f, 0f
        });
        keyboardPanel.setBackground(frost);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            inputView.post(() -> applyCrossWindowBlur(inputView));
        }
    }

    /** Migrate prototype installs once so preserved prefs do not keep the keyboard permanently dark. */
    private static void ensureVisualProfile(final Context context) {
        final SharedPreferences prefs = KtxKt.prefs(context);
        if (prefs.getBoolean(PREF_VISUAL_PROFILE_V3, false)) {
            return;
        }
        prefs.edit()
                .putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
                .putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT)
                .putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARKER)
                .putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
                .putBoolean(Settings.PREF_THEME_KEY_BORDERS, false)
                .putBoolean(Settings.PREF_SHOW_HINTS, false)
                .putBoolean(PREF_VISUAL_PROFILE_V3, true)
                .apply();
    }

    private static boolean isNightMode(final Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static float dp(final View view, final int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    private static void applyCrossWindowBlur(final View inputView) {
        try {
            final View root = inputView.getRootView();
            final ViewGroup.LayoutParams rawParams = root.getLayoutParams();
            if (!(rawParams instanceof WindowManager.LayoutParams)) {
                return;
            }

            final WindowManager.LayoutParams params = (WindowManager.LayoutParams) rawParams;
            params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            params.setBlurBehindRadius(Math.round(dp(inputView, BLUR_RADIUS_DP)));

            final WindowManager windowManager =
                    (WindowManager) inputView.getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.updateViewLayout(root, params);
            }
        } catch (RuntimeException ignored) {
            // Some OEM IME windows reject blur/layout changes. The frost fallback remains visible.
        }
    }
}
