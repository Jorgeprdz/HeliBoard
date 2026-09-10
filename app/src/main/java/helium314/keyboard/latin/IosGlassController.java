/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import helium314.keyboard.keyboard.KeyboardTheme;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.KtxKt;

/**
 * Applies the iOS-inspired frosted-glass surface used by the Jorgeprdz HeliBoard fork.
 *
 * The reference design is intentionally a dark smoked glass surface even when the host app is
 * using a light theme. Android's cross-window blur can be disabled by the platform/OEM at runtime,
 * so a dense translucent frost layer is always present while FLAG_BLUR_BEHIND remains an optional
 * enhancement. This intentionally never blurs the keyboard's own content.
 */
public final class IosGlassController {
    private static final int BLUR_RADIUS_DP = 88;
    private static final int PANEL_CORNER_RADIUS_DP = 18;
    private static final String PREF_VISUAL_PROFILE_V2 = "ios_glass_visual_profile_v2";

    private IosGlassController() {}

    public static void apply(final View inputView, final View keyboardPanel) {
        if (inputView == null || keyboardPanel == null) {
            return;
        }

        ensureVisualProfile(inputView.getContext());

        final GradientDrawable frost = new GradientDrawable();
        frost.setShape(GradientDrawable.RECTANGLE);
        // iOS concept target: smoky black glass, not a system-light translucent sheet.
        // Alpha is intentionally high enough that text from the app behind the IME does not compete
        // visually with the key legends when OEM blur is unavailable.
        frost.setColor(Color.argb(222, 24, 24, 26));
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

    /**
     * Migrate existing installs of the first prototype to the visual profile used by the concept.
     * The marker makes this a one-shot migration, so the user's later theme choices are respected.
     */
    private static void ensureVisualProfile(final Context context) {
        final SharedPreferences prefs = KtxKt.prefs(context);
        if (prefs.getBoolean(PREF_VISUAL_PROFILE_V2, false)) {
            return;
        }
        prefs.edit()
                .putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_DARKER)
                .putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARKER)
                .putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
                .putBoolean(Settings.PREF_SHOW_HINTS, false)
                .putBoolean(PREF_VISUAL_PROFILE_V2, true)
                .apply();
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
