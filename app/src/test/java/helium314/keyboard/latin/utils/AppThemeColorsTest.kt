// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppThemeColorsTest {
    @Test
    @Config(sdk = [30])
    fun preApi31FallbackHasDistinctLightAndDarkSchemes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val light = resolveAppColorScheme(context, dark = false)
        val dark = resolveAppColorScheme(context, dark = true)

        assertEquals(context.getColor(R.color.accent), light.primary)
        assertEquals(context.getColor(R.color.accent), dark.primary)
        assertEquals(false, light == dark)
    }

    @Test
    @Config(sdk = [31])
    fun api31AndAboveUseSystemDynamicColors() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val light = resolveAppColorScheme(context, dark = false)
        val dark = resolveAppColorScheme(context, dark = true)

        assertEquals(context.getColor(android.R.color.system_accent1_600), light.primary)
        assertEquals(context.getColor(android.R.color.system_accent1_200), dark.primary)
    }
}
