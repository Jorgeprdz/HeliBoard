/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

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

    private IosGlassController() {}

    public static void apply(final View inputView, final View keyboardPanel) {
        if (inputView == null || keyboardPanel == null) {
            return;
        }

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
