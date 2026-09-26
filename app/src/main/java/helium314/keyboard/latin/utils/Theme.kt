// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val material3 = Typography()
    val colorScheme = resolveAppColorScheme(androidx.compose.ui.platform.LocalContext.current, dark)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            displayLarge = material3.displayLarge.copy(fontWeight = FontWeight.Bold),
            headlineLarge = material3.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = material3.headlineMedium.copy(fontWeight = FontWeight.Bold),
            titleLarge = material3.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = material3.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = material3.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = material3.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        shapes = Shapes(
            extraSmall = AppShapes.compact,
            small = AppShapes.compact,
            medium = AppShapes.medium,
            large = AppShapes.large,
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        ),
        content = content
    )
}

const val previewDark = true
