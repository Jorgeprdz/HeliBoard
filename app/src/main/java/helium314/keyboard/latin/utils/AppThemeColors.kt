// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R

/** Resolves colors for HeliBoard's app surfaces only; keyboard colors are managed separately. */
internal fun resolveAppColorScheme(context: Context, dark: Boolean): ColorScheme {
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accent = ContextCompat.getColor(context, R.color.accent)
        if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(accent))
        else lightColorScheme(primary = androidx.compose.ui.graphics.Color(accent))
    }
    val palette = helium314.keyboard.settings.OneUiMenuStyle.palette(dark, ContextCompat.getColor(context, R.color.accent))
    val background = androidx.compose.ui.graphics.Color(palette.background)
    val surface = androidx.compose.ui.graphics.Color(palette.surface)
    val text = androidx.compose.ui.graphics.Color(palette.foreground)
    val accentColor = androidx.compose.ui.graphics.Color(palette.accent)
    val onAccent = androidx.compose.ui.graphics.Color(
        if (androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, palette.accent) >= 4.5)
            android.graphics.Color.WHITE else android.graphics.Color.BLACK
    )
    val accentContainer = androidx.compose.ui.graphics.Color(
        androidx.core.graphics.ColorUtils.blendARGB(palette.surface, palette.accent, .12f)
    )
    return base.copy(
        primary = accentColor, onPrimary = onAccent,
        secondary = accentColor, onSecondary = onAccent,
        primaryContainer = accentContainer, onPrimaryContainer = accentColor,
        secondaryContainer = accentContainer, onSecondaryContainer = accentColor,
        surfaceTint = androidx.compose.ui.graphics.Color.Transparent,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceContainerLowest = surface, surfaceContainerLow = background,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        onSurfaceVariant = androidx.compose.ui.graphics.Color(palette.secondary),
        outlineVariant = androidx.compose.ui.graphics.Color(palette.divider),
    )
}

internal object AppShapes {
    val compact = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(26.dp)
    val large = RoundedCornerShape(26.dp)
}

internal object AppSpacing {
    val xs: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp
    val huge: Dp = 32.dp
}
