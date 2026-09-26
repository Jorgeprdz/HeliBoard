// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import com.drdisagree.materialcolorutilities.dynamiccolor.ColorSpec
import com.drdisagree.materialcolorutilities.dynamiccolor.DynamicScheme
import com.drdisagree.materialcolorutilities.hct.Hct
import com.drdisagree.materialcolorutilities.scheme.SchemeCmf
import com.drdisagree.materialcolorutilities.scheme.SchemeContent
import com.drdisagree.materialcolorutilities.scheme.SchemeExpressive
import com.drdisagree.materialcolorutilities.scheme.SchemeFidelity
import com.drdisagree.materialcolorutilities.scheme.SchemeFruitSalad
import com.drdisagree.materialcolorutilities.scheme.SchemeMonochrome
import com.drdisagree.materialcolorutilities.scheme.SchemeNeutral
import com.drdisagree.materialcolorutilities.scheme.SchemeRainbow
import com.drdisagree.materialcolorutilities.scheme.SchemeTonalSpot
import com.drdisagree.materialcolorutilities.scheme.SchemeVibrant

enum class DynamicSchemeType(val themeName: String) {
    NEUTRAL("dynamic_neutral"),
    MONOCHROME("dynamic_monochrome"),
    TONAL_SPOT("dynamic_tonal_spot"),
    VIBRANT("dynamic_vibrant"),
    RAINBOW("dynamic_rainbow"),
    EXPRESSIVE("dynamic_expressive"),
    FIDELITY("dynamic_fidelity"),
    CONTENT("dynamic_content"),
    FRUIT_SALAD("dynamic_fruit_salad"),
    CMF("dynamic_cmf");

    companion object {
        fun fromThemeName(themeName: String): DynamicSchemeType? =
            entries.firstOrNull { it.themeName == themeName }
    }
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
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val outline: Int,
    val outlineVariant: Int,
)

object DynamicSchemeProvider {
    private data class CacheKey(val seed: Int, val type: DynamicSchemeType, val isDark: Boolean)

    private val cache = HashMap<CacheKey, KeyboardDynamicScheme>()

    @Synchronized
    fun get(seed: Int, type: DynamicSchemeType, isDark: Boolean): KeyboardDynamicScheme {
        val key = CacheKey(seed, type, isDark)
        return cache[key] ?: generate(seed, type, isDark).also { cache[key] = it }
    }

    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    internal fun generate(seed: Int, type: DynamicSchemeType, isDark: Boolean): KeyboardDynamicScheme {
        val source = Hct.fromInt(seed)
        val spec = ColorSpec.SpecVersion.SPEC_2026
        val platform = DynamicScheme.DEFAULT_PLATFORM
        val scheme: DynamicScheme = when (type) {
            DynamicSchemeType.NEUTRAL ->
                SchemeNeutral(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.MONOCHROME ->
                SchemeMonochrome(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.TONAL_SPOT ->
                SchemeTonalSpot(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.VIBRANT ->
                SchemeVibrant(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.RAINBOW ->
                SchemeRainbow(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.EXPRESSIVE ->
                SchemeExpressive(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.FIDELITY ->
                SchemeFidelity(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.CONTENT ->
                SchemeContent(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.FRUIT_SALAD ->
                SchemeFruitSalad(source, isDark, 0.0, spec, platform)
            DynamicSchemeType.CMF ->
                SchemeCmf(source, isDark, 0.0, spec, platform)
        }
        return KeyboardDynamicScheme(
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
            primaryContainer = scheme.primaryContainer,
            onPrimaryContainer = scheme.onPrimaryContainer,
            secondary = scheme.secondary,
            onSecondary = scheme.onSecondary,
            secondaryContainer = scheme.secondaryContainer,
            onSecondaryContainer = scheme.onSecondaryContainer,
            tertiary = scheme.tertiary,
            onTertiary = scheme.onTertiary,
            tertiaryContainer = scheme.tertiaryContainer,
            onTertiaryContainer = scheme.onTertiaryContainer,
            background = scheme.background,
            onBackground = scheme.onBackground,
            surface = scheme.surface,
            onSurface = scheme.onSurface,
            surfaceVariant = scheme.surfaceVariant,
            onSurfaceVariant = scheme.onSurfaceVariant,
            surfaceContainerLowest = scheme.surfaceContainerLowest,
            surfaceContainerLow = scheme.surfaceContainerLow,
            surfaceContainer = scheme.surfaceContainer,
            surfaceContainerHigh = scheme.surfaceContainerHigh,
            surfaceContainerHighest = scheme.surfaceContainerHighest,
            outline = scheme.outline,
            outlineVariant = scheme.outlineVariant,
        )
    }
}
