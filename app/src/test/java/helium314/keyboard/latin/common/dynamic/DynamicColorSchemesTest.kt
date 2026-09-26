// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DynamicColorSchemesTest {
    private val seedA = 0xFF6750A4.toInt()
    private val seedB = 0xFF006C4C.toInt()

    @Test
    fun everySchemeGeneratesLightAndDark() {
        DynamicSchemeType.entries.forEach { type ->
            val light = DynamicSchemeProvider.get(seedA, type, false)
            val dark = DynamicSchemeProvider.get(seedA, type, true)
            assertOpaque(light, "$type light")
            assertOpaque(dark, "$type dark")
            assertNotEquals(roleSignature(light), roleSignature(dark), "$type must have a real dark variant")
        }
    }

    @Test
    fun identicalSeedIsDeterministicAndCached() {
        DynamicSchemeProvider.clearCache()
        DynamicSchemeType.entries.forEach { type ->
            val first = DynamicSchemeProvider.get(seedA, type, false)
            val second = DynamicSchemeProvider.get(seedA, type, false)
            assertEquals(first, second, "$type must be deterministic")
            assertSame(first, second, "$type should come from the provider cache")
        }
    }

    @Test
    fun differentSeedChangesGeneratedPalette() {
        val first = DynamicSchemeProvider.get(seedA, DynamicSchemeType.TONAL_SPOT, false)
        val second = DynamicSchemeProvider.get(seedB, DynamicSchemeType.TONAL_SPOT, false)
        assertNotEquals(roleSignature(first), roleSignature(second))
    }

    @Test
    fun expressiveDiffersFromTonalSpot() {
        val expressive = DynamicSchemeProvider.get(seedA, DynamicSchemeType.EXPRESSIVE, false)
        val tonalSpot = DynamicSchemeProvider.get(seedA, DynamicSchemeType.TONAL_SPOT, false)
        assertNotEquals(roleSignature(expressive), roleSignature(tonalSpot))
    }

    @Test
    fun rainbowDiffersFromVibrant() {
        val rainbow = DynamicSchemeProvider.get(seedA, DynamicSchemeType.RAINBOW, false)
        val vibrant = DynamicSchemeProvider.get(seedA, DynamicSchemeType.VIBRANT, false)
        assertNotEquals(roleSignature(rainbow), roleSignature(vibrant))
    }

    @Test
    fun fruitSaladAndCmfGenerateInBothModes() {
        for (type in listOf(DynamicSchemeType.FRUIT_SALAD, DynamicSchemeType.CMF)) {
            assertOpaque(DynamicSchemeProvider.get(seedA, type, false), "$type light")
            assertOpaque(DynamicSchemeProvider.get(seedA, type, true), "$type dark")
        }
    }

    @Test
    fun systemThemeNeverResolvesToLocalScheme() {
        assertNull(DynamicSchemeType.fromThemeName(KeyboardTheme.THEME_DYNAMIC_SYSTEM))
        assertNull(KeyboardTheme.localDynamicSchemeType(KeyboardTheme.THEME_DYNAMIC_SYSTEM))
    }

    @Test
    fun seedFallbackPriorityIsWallpaperThenSystemThenInternal() {
        val wallpaper = 0xFF123456.toInt()
        val system = 0xFF654321.toInt()
        assertEquals(wallpaper, DynamicColorSeedProvider.resolveSeed(wallpaper, system))
        assertEquals(system, DynamicColorSeedProvider.resolveSeed(null, system))
        assertEquals(
            DynamicColorSeedProvider.FALLBACK_SEED,
            DynamicColorSeedProvider.resolveSeed(null, null),
        )
    }

    @Test
    fun legacyDynamicThemeResolvesToSystemWithoutChangingStoredId() {
        assertEquals(
            KeyboardTheme.THEME_DYNAMIC_SYSTEM,
            KeyboardTheme.normalizeThemeName(KeyboardTheme.THEME_DYNAMIC),
        )
        assertTrue(KeyboardTheme.isDynamicTheme(KeyboardTheme.THEME_DYNAMIC))
    }

    @Test
    fun everyNewThemeNameResolvesCorrectly() {
        val expected = mapOf(
            KeyboardTheme.THEME_DYNAMIC_NEUTRAL to DynamicSchemeType.NEUTRAL,
            KeyboardTheme.THEME_DYNAMIC_MONOCHROME to DynamicSchemeType.MONOCHROME,
            KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT to DynamicSchemeType.TONAL_SPOT,
            KeyboardTheme.THEME_DYNAMIC_VIBRANT to DynamicSchemeType.VIBRANT,
            KeyboardTheme.THEME_DYNAMIC_RAINBOW to DynamicSchemeType.RAINBOW,
            KeyboardTheme.THEME_DYNAMIC_EXPRESSIVE to DynamicSchemeType.EXPRESSIVE,
            KeyboardTheme.THEME_DYNAMIC_FIDELITY to DynamicSchemeType.FIDELITY,
            KeyboardTheme.THEME_DYNAMIC_CONTENT to DynamicSchemeType.CONTENT,
            KeyboardTheme.THEME_DYNAMIC_FRUIT_SALAD to DynamicSchemeType.FRUIT_SALAD,
            KeyboardTheme.THEME_DYNAMIC_CMF to DynamicSchemeType.CMF,
        )
        assertEquals(11, KeyboardTheme.DYNAMIC_THEMES.size)
        assertTrue(KeyboardTheme.THEME_DYNAMIC_SYSTEM in KeyboardTheme.DYNAMIC_THEMES)
        expected.forEach { (themeName, schemeType) ->
            assertEquals(schemeType, KeyboardTheme.localDynamicSchemeType(themeName))
            assertTrue(KeyboardTheme.isDynamicTheme(themeName))
        }
    }

    @Test
    fun everyColorTypeIsMappedForEverySchemeAndMode() {
        for (type in DynamicSchemeType.entries) {
            for (isDark in listOf(false, true)) {
                val scheme = DynamicSchemeProvider.get(seedA, type, isDark)
                for (style in KeyboardTheme.STYLES) {
                    for (borders in listOf(false, true)) {
                        val colors = MonetColorMapper.map(scheme, style, borders)
                        assertEquals(ColorType.entries.size, colors.size)
                        ColorType.entries.forEach { colorType ->
                            assertTrue(colors.containsKey(colorType), "$type/$isDark/$style/$borders missing $colorType")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun criticalTextAndIconPairsMeetReadableContrast() {
        for (type in DynamicSchemeType.entries) {
            for (isDark in listOf(false, true)) {
                val scheme = DynamicSchemeProvider.get(seedA, type, isDark)
                val colors = MonetColorMapper.map(scheme, KeyboardTheme.STYLE_MATERIAL, true)
                assertContrast(colors, ColorType.KEY_TEXT, ColorType.KEY_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.FUNCTIONAL_KEY_TEXT, ColorType.FUNCTIONAL_KEY_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.SPACE_BAR_TEXT, ColorType.SPACE_BAR_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.ACTION_KEY_ICON, ColorType.ACTION_KEY_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.POPUP_KEY_TEXT, ColorType.POPUP_KEYS_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.POPUP_KEY_ICON, ColorType.POPUP_KEYS_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.KEY_PREVIEW_TEXT, ColorType.KEY_PREVIEW_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.EMOJI_SEARCH_TEXT, ColorType.EMOJI_SEARCH_BACKGROUND, type, isDark)
                assertContrast(colors, ColorType.CLIPBOARD_SUGGESTION_ICON, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND, type, isDark)
            }
        }
    }

    private fun assertContrast(
        colors: Map<ColorType, Int>,
        foreground: ColorType,
        background: ColorType,
        type: DynamicSchemeType,
        isDark: Boolean,
    ) {
        val ratio = ColorUtils.calculateContrast(colors.getValue(foreground), colors.getValue(background))
        assertTrue(ratio >= 4.5, "$type dark=$isDark $foreground/$background contrast=$ratio")
    }

    private fun assertOpaque(scheme: KeyboardDynamicScheme, label: String) {
        roles(scheme).forEach { color ->
            assertEquals(255, Color.alpha(color), "$label produced a non-opaque required role")
        }
    }

    private fun roleSignature(scheme: KeyboardDynamicScheme): List<Int> = listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.background,
        scheme.surfaceContainer,
        scheme.primaryContainer,
        scheme.secondaryContainer,
        scheme.tertiaryContainer,
    )

    private fun roles(scheme: KeyboardDynamicScheme): List<Int> = listOf(
        scheme.primary,
        scheme.onPrimary,
        scheme.primaryContainer,
        scheme.onPrimaryContainer,
        scheme.secondary,
        scheme.onSecondary,
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
        scheme.tertiary,
        scheme.onTertiary,
        scheme.tertiaryContainer,
        scheme.onTertiaryContainer,
        scheme.background,
        scheme.onBackground,
        scheme.surface,
        scheme.onSurface,
        scheme.surfaceVariant,
        scheme.onSurfaceVariant,
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.outline,
        scheme.outlineVariant,
    )
}
