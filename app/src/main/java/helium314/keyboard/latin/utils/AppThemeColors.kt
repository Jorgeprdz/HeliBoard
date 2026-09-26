// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R

/** Resolves colors for HeliBoard's app surfaces only; keyboard colors are managed separately. */
internal fun resolveAppColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accent = context.resources.getColor(R.color.accent, context.theme)
        if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(accent))
        else lightColorScheme(primary = androidx.compose.ui.graphics.Color(accent))
    }

internal object AppShapes {
    val compact: Shape = RoundedCornerShape(12.dp)
    val medium: Shape = RoundedCornerShape(20.dp)
    val large: Shape = RoundedCornerShape(28.dp)
}

internal object AppSpacing {
    val xs: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp
    val huge: Dp = 32.dp
}
