// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeCmf
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import java.util.concurrent.ConcurrentHashMap

/** Dynamic scheme variants exposed by HeliBoard.
 *
 * [SYSTEM] intentionally is not generated here. It means "read Android system colors" and remains
 * implemented by [DynamicColors], so ColorBlendr or any other system-palette provider is picked up
 * indirectly through Android resources only.
 */
enum class KeyboardDynamicSchemeType {
    SYSTEM,
    NEUTRAL,
    MONOCHROME,
    TONAL_SPOT,
    VIBRANT,
    RAINBOW,
    EXPRESSIVE,
    FIDELITY,
    CONTENT,
    FRUIT_SALAD,
    CMF,
}

data class KeyboardDynamicScheme(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val outline: Int,
)

object DynamicSchemeProvider {
    private data class CacheKey(
        val seed: Int,
        val type: KeyboardDynamicSchemeType,
        val isDark: Boolean,
    )

    private val cache = ConcurrentHashMap<CacheKey, KeyboardDynamicScheme>()

    fun create(
        seed: Int,
        type: KeyboardDynamicSchemeType,
        isDark: Boolean,
    ): KeyboardDynamicScheme {
        require(type != KeyboardDynamicSchemeType.SYSTEM) {
            "SYSTEM dynamic colors must be read from Android system resources, not generated locally."
        }
        val key = CacheKey(seed, type, isDark)
        return cache.getOrPut(key) { build(seed, type, isDark) }
    }

    fun clearCache() = cache.clear()

    private fun build(seed: Int, type: KeyboardDynamicSchemeType, isDark: Boolean): KeyboardDynamicScheme {
        val source = Hct.fromInt(seed)
        val scheme: DynamicScheme = when (type) {
            KeyboardDynamicSchemeType.NEUTRAL -> SchemeNeutral(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.MONOCHROME -> SchemeMonochrome(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.TONAL_SPOT -> SchemeTonalSpot(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.VIBRANT -> SchemeVibrant(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.RAINBOW -> SchemeRainbow(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.EXPRESSIVE -> SchemeExpressive(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.FIDELITY -> SchemeFidelity(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.CONTENT -> SchemeContent(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.FRUIT_SALAD -> SchemeFruitSalad(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.CMF -> SchemeCmf(source, isDark, CONTRAST_LEVEL)
            KeyboardDynamicSchemeType.SYSTEM -> error("SYSTEM is not a local scheme")
        }
        val colors = MaterialDynamicColors()
        fun argb(color: com.materialkolor.dynamiccolor.DynamicColor): Int = color.getArgb(scheme)

        return KeyboardDynamicScheme(
            primary = argb(colors.primary()),
            onPrimary = argb(colors.onPrimary()),
            primaryContainer = argb(colors.primaryContainer()),
            onPrimaryContainer = argb(colors.onPrimaryContainer()),
            secondary = argb(colors.secondary()),
            onSecondary = argb(colors.onSecondary()),
            secondaryContainer = argb(colors.secondaryContainer()),
            onSecondaryContainer = argb(colors.onSecondaryContainer()),
            tertiary = argb(colors.tertiary()),
            onTertiary = argb(colors.onTertiary()),
            tertiaryContainer = argb(colors.tertiaryContainer()),
            onTertiaryContainer = argb(colors.onTertiaryContainer()),
            background = argb(colors.background()),
            onBackground = argb(colors.onBackground()),
            surface = argb(colors.surface()),
            onSurface = argb(colors.onSurface()),
            surfaceVariant = argb(colors.surfaceVariant()),
            onSurfaceVariant = argb(colors.onSurfaceVariant()),
            surfaceContainer = argb(colors.surfaceContainer()),
            surfaceContainerHigh = argb(colors.surfaceContainerHigh()),
            outline = argb(colors.outline()),
        )
    }

    private const val CONTRAST_LEVEL = 0.0
}
