/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;

import helium314.keyboard.keyboard.KeyboardTheme;
import helium314.keyboard.latin.settings.IosGlassPreferences;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.KtxKt;

/** Applies the iOS-inspired adaptive frosted surface used by the Jorgeprdz HeliBoard fork. */
public final class IosGlassController {
    private static final int PANEL_CORNER_RADIUS_DP = 18;
    private static final String PREF_VISUAL_PROFILE_V4 = "ios_glass_visual_profile_v4";

    private IosGlassController() {}

    public static void apply(final View inputView, final View keyboardPanel) {
        if (inputView == null || keyboardPanel == null) {
            return;
        }

        ensureVisualProfile(inputView.getContext());

        // The IME root must stay transparent. Otherwise the rectangular SoftInputWindow/inputArea
        // leaks through around the rounded panel and produces white corner wedges.
        inputView.setBackgroundColor(Color.TRANSPARENT);

        final boolean dark = isNightMode(inputView.getResources().getConfiguration());
        final GradientDrawable frost = new GradientDrawable();
        frost.setShape(GradientDrawable.RECTANGLE);
        // Restore the earlier, visibly translucent frost. The real blur comes from the window
        // behind this surface; the tint only keeps keys readable.
        frost.setColor(dark
                ? Color.argb(196, 31, 31, 33)
                : Color.argb(202, 242, 242, 247));

        final float radius = dp(inputView, PANEL_CORNER_RADIUS_DP);
        frost.setCornerRadii(new float[] {
                radius, radius,
                radius, radius,
                radius, radius,
                radius, radius
        });
        keyboardPanel.setBackground(frost);
        // Same principle used by modern BlurView integrations: clip the rendered surface to the
        // rounded background so neither children nor blur/tint spill into the transparent corners.
        keyboardPanel.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        keyboardPanel.setClipToOutline(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            inputView.post(() -> applyCrossWindowBlur(inputView));
        }
    }

    /**
     * Keep every layer outside the rounded keyboard panel transparent. SoftInputWindow uses a
     * rectangular inputArea even though the visible keyboard is rounded, so an opaque decor or
     * inputArea shows up as white wedges at the corners.
     */
    public static void prepareWindow(final LatinIME service, final View inputView) {
        if (service == null || inputView == null || service.getWindow() == null) {
            return;
        }
        try {
            final Window window = service.getWindow().getWindow();
            if (window == null) {
                return;
            }
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            final View decor = window.getDecorView();
            if (decor != null) {
                decor.setBackgroundColor(Color.TRANSPARENT);
            }
            final View inputArea = window.findViewById(android.R.id.inputArea);
            if (inputArea != null) {
                inputArea.setBackgroundColor(Color.TRANSPARENT);
            }
            inputView.setBackgroundColor(Color.TRANSPARENT);
        } catch (RuntimeException ignored) {
            // Transparency is a visual enhancement; never make the IME unusable if an OEM rejects it.
        }
    }

    /**
     * One-shot visual migration. It only changes theme presentation flags; keyboard features and
     * behavioral preferences remain untouched and subsequent user theme changes are respected.
     */
    private static void ensureVisualProfile(final Context context) {
        final SharedPreferences prefs = KtxKt.prefs(context);
        if (prefs.getBoolean(PREF_VISUAL_PROFILE_V4, false)) {
            return;
        }
        prefs.edit()
                .putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
                .putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT)
                .putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARKER)
                .putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
                // Logical borders keep normal/functional/action color roles separate. The iOS
                // drawable itself has no hard outline, so this does not reintroduce Material borders.
                .putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
                .putBoolean(PREF_VISUAL_PROFILE_V4, true)
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
            final SharedPreferences prefs = KtxKt.prefs(inputView.getContext());
            final int blurRadiusDp = IosGlassPreferences.resolveBlurRadiusDp(prefs);
            if (blurRadiusDp <= 0) {
                params.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                params.setBlurBehindRadius(0);
            } else {
                params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                params.setBlurBehindRadius(Math.round(dp(inputView, blurRadiusDp)));
            }

            final WindowManager windowManager =
                    (WindowManager) inputView.getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.updateViewLayout(root, params);
            }
        } catch (RuntimeException ignored) {
            // Some OEM IME windows reject cross-window blur. The translucent frost remains usable.
        }
    }
}
