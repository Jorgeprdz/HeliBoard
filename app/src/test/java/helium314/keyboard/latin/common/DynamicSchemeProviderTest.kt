// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicSchemeProviderTest {
    private val localTypes = KeyboardDynamicSchemeType.entries.filter { it != KeyboardDynamicSchemeType.SYSTEM }

    @Test
    fun eachLocalSchemeGeneratesLight() {
        localTypes.forEach { type ->
            val scheme = DynamicSchemeProvider.create(SEED_A, type, isDark = false)
            assertRequiredColors(type, scheme)
        }
    }

    @Test
    fun eachLocalSchemeGeneratesDark() {
        localTypes.forEach { type ->
            val scheme = DynamicSchemeProvider.create(SEED_A, type, isDark = true)
            assertRequiredColors(type, scheme)
        }
    }

    @Test
    fun identicalSeedProducesDeterministicScheme() {
        val first = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.EXPRESSIVE, isDark = false)
        val second = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.EXPRESSIVE, isDark = false)
        assertEquals(first, second)
    }

    @Test
    fun differentSeedChangesPalette() {
        val first = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.VIBRANT, isDark = false)
        val second = DynamicSchemeProvider.create(SEED_B, KeyboardDynamicSchemeType.VIBRANT, isDark = false)
        assertNotEquals(first.primary, second.primary)
    }

    @Test
    fun expressiveDiffersFromTonalSpot() {
        val expressive = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.EXPRESSIVE, isDark = false)
        val tonalSpot = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.TONAL_SPOT, isDark = false)
        assertNotEquals(expressive.secondary, tonalSpot.secondary)
    }

    @Test
    fun rainbowDiffersFromVibrant() {
        val rainbow = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.RAINBOW, isDark = false)
        val vibrant = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.VIBRANT, isDark = false)
        assertNotEquals(rainbow.tertiary, vibrant.tertiary)
    }

    @Test
    fun fruitSaladGeneratesPalette() {
        val scheme = DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.FRUIT_SALAD, isDark = false)
        assertRequiredColors(KeyboardDynamicSchemeType.FRUIT_SALAD, scheme)
    }

    @Test
    fun systemModeDoesNotUseLocalSchemeProvider() {
        assertFailsWith<IllegalArgumentException> {
            DynamicSchemeProvider.create(SEED_A, KeyboardDynamicSchemeType.SYSTEM, isDark = false)
        }
    }

    @Test
    fun fallbackSeedWorks() {
        assertEquals(DynamicColorSeedProvider.DEFAULT_SEED, DynamicColorSeedProvider.fallbackSeedForTests(null))
    }

    @Test
    fun legacyDynamicThemeIsSystemTheme() {
        assertEquals(KeyboardTheme.THEME_DYNAMIC_SYSTEM, KeyboardTheme.THEME_DYNAMIC)
    }

    @Test
    fun everyRequiredColorTypeResolves() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val colors = MonetKeyboardColors(
            context = context,
            schemeType = KeyboardDynamicSchemeType.TONAL_SPOT,
            themeStyle = KeyboardTheme.STYLE_MATERIAL,
            hasKeyBorders = false,
            isDark = false,
            keyboardBackground = null,
        )
        ColorType.entries.forEach { colorType ->
            assertNotNull(colors.get(colorType), colorType.name)
        }
    }

    private fun assertRequiredColors(type: KeyboardDynamicSchemeType, scheme: KeyboardDynamicScheme) {
        val values = listOf(
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
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.outline,
        )
        values.forEach { value ->
            assertNotEquals(0, value, "${type.name} returned transparent/empty color")
        }
    }

    private companion object {
        private const val SEED_A = -16738680 // 0xFF009688
        private const val SEED_B = -769226 // 0xFFF44336
    }
}
