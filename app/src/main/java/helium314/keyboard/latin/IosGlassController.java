/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/**
 * Applies the iOS-inspired frosted-glass surface used by the Jorgeprdz HeliBoard fork.
 *
 * Android's cross-window blur can be disabled by the platform/OEM at runtime. The translucent
 * frost layer is therefore always present, while FLAG_BLUR_BEHIND is an enhancement when the
 * platform accepts it. This intentionally never blurs the keyboard's own content.
 */
public final class IosGlassController {
    private static final int BLUR_RADIUS_DP = 64;
    private static final int PANEL_CORNER_RADIUS_DP = 18;

    private IosGlassController() {}

    public static void apply(final View inputView, final View keyboardPanel) {
        if (inputView == null || keyboardPanel == null) {
            return;
        }

        final boolean dark = isNightMode(inputView.getResources().getConfiguration());
        final GradientDrawable frost = new GradientDrawable();
        frost.setShape(GradientDrawable.RECTANGLE);
        frost.setColor(dark
                ? Color.argb(196, 31, 31, 33)
                : Color.argb(202, 242, 242, 247));
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
