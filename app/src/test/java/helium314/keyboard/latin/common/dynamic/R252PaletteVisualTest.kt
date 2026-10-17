// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class R252PaletteVisualTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun snap(epoch: Long, seed: Int) = SessionColorSnapshot(
        epoch, AppIdentity("app.a"), false, SeedOrigin.BY_APP, seed, epoch * 31
    )
    private fun palette(seed: Int) = PaletteIdentity(
        seed, DynamicSchemeType.TONAL_SPOT.name, false,
        KeyboardTheme.STYLE_MATERIAL, true, null, 1, true
    )

    @Test fun equalEffectivePaletteDoesNotInvalidateForAnotherEpochOrHost() {
        val seed = 0xFF00BCD4.toInt()
        val p = palette(seed)
        try {
            R252PaletteRegistry.bind(snap(1,seed),p)
            val colors = MonetKeyboardColors(context, KeyboardTheme.STYLE_MATERIAL,
                true, DynamicSchemeType.TONAL_SPOT, false)
            R252PaletteRegistry.bind(snap(2,seed).copy(identity=AppIdentity("app.b")),p)
            assertFalse(colors.haveColorsChanged(context))
            R252PaletteRegistry.bind(snap(3,0xFFCC00AA.toInt()),
                p.copy(argb=0xFFCC00AA.toInt()))
            assertTrue(colors.haveColorsChanged(context))
        } finally { R252PaletteRegistry.clear() }
    }

    @Test fun disconnectingByAppInvalidatesOnlyBoundPalette() {
        val seed=0xFF00BCD4.toInt()
        try {
            R252PaletteRegistry.bind(snap(1,seed),palette(seed))
            val colors=MonetKeyboardColors(context,KeyboardTheme.STYLE_MATERIAL,
                true,DynamicSchemeType.TONAL_SPOT,false)
            R252PaletteRegistry.clear()
            assertTrue(colors.haveColorsChanged(context))
        } finally { R252PaletteRegistry.clear() }
    }

    @Test fun twentyExistingSchemeModesRetainCompleteColorMapAndContrast() {
        val seed=0xFF00BCD4.toInt()
        var count=0
        for (variant in DynamicSchemeType.entries) for (dark in listOf(false,true)) {
            val mapped=MonetColorMapper.map(
                DynamicSchemeProvider.get(seed,variant,dark),
                KeyboardTheme.STYLE_MATERIAL,true
            )
            assertEquals(ColorType.entries.size,mapped.size)
            ColorType.entries.forEach { assertTrue(mapped.containsKey(it)) }
            val ratio=ColorUtils.calculateContrast(
                mapped.getValue(ColorType.KEY_TEXT),mapped.getValue(ColorType.KEY_BACKGROUND))
            assertTrue("$variant/$dark contrast $ratio",ratio>=4.5)
            count++
        }
        assertEquals(20,count)
    }

    @Test fun offModeNeverSetsGlobalRegistryByItself() {
        R252PaletteRegistry.clear()
        assertNull(R252PaletteRegistry.current())
        val colors=MonetKeyboardColors(context,KeyboardTheme.STYLE_MATERIAL,
            true,DynamicSchemeType.TONAL_SPOT,false)
        assertNull(R252PaletteRegistry.current())
        assertEquals(255,Color.alpha(colors.get(ColorType.KEY_TEXT)))
    }
}
