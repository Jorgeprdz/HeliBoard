// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.toArgb
import helium314.keyboard.latin.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
class AppThemeColorsTest {
    @Test
    @Config(sdk = [30])
    fun preApi31FallbackHasDistinctLightAndDarkSchemes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val light = resolveAppColorScheme(context, dark = false)
        val dark = resolveAppColorScheme(context, dark = true)

        assertEquals(context.getColor(R.color.accent), light.primary.toArgb())
        assertEquals(context.getColor(R.color.accent), dark.primary.toArgb())
        assertEquals(false, light == dark)
    }

    @Test
    @Config(sdk = [31])
    fun api31MenusKeepTacticAccentAndReadableNeutralOneUiSurfaces() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val light = resolveAppColorScheme(context, dark = false)
        val dark = resolveAppColorScheme(context, dark = true)

        for (scheme in listOf(light, dark)) {
            assertEquals(context.getColor(R.color.accent), scheme.primary.toArgb())
            assertEquals(scheme.primary, scheme.secondary)
            assertEquals(scheme.surface, scheme.surfaceContainerHigh)
            org.junit.Assert.assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(
                scheme.onSurface.toArgb(), scheme.surface.toArgb()) >= 4.5)
        }
        assertEquals(0xfff2f2f2.toInt(), light.background.toArgb())
        assertEquals(0xff010101.toInt(), dark.background.toArgb())
    }
}
