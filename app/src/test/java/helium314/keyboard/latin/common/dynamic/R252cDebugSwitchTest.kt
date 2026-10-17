// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs unchanged against testDebugUnitTest AND testReleaseUnitTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class R252cDebugSwitchTest {
    @Test fun releaseMustNeverBeActivatableEvenWithFlagOne() {
        assertFalse(R252cDebugSwitch.enabledForVariant(false, 1))
        assertFalse(R252cDebugSwitch.enabledForVariant(false, 42))
        assertFalse(R252cDebugSwitch.enabledForVariant(false, -1))
    }

    @Test fun debugWithoutFlagDefaultsOff() {
        assertFalse(R252cDebugSwitch.enabledForVariant(true, 0))
        assertFalse(R252cDebugSwitch.enabledForVariant(true, -1))
        assertFalse(R252cDebugSwitch.enabledForVariant(true, 2))
    }

    @Test fun debugWithLiteralOneIsOnAndDisablingRestoresOff() {
        assertTrue(R252cDebugSwitch.enabledForVariant(true, 1))
        assertFalse(R252cDebugSwitch.enabledForVariant(true, 0))
    }

    @Test fun actualBuildConfigEnforcesReleaseIsolationAndMissingGlobalFlag() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val original = Settings.Global.getString(resolver, R252cDebugSwitch.GLOBAL_FLAG)
        try {
            Settings.Global.putString(resolver,R252cDebugSwitch.GLOBAL_FLAG,null)
            assertFalse("No ADB flag MUST remain OFF",R252cDebugSwitch.requested(context))
            Settings.Global.putInt(resolver,R252cDebugSwitch.GLOBAL_FLAG,1)
            assertEquals("Release compiled gate MUST ignore ADB flag",
                BuildConfig.DEBUG,R252cDebugSwitch.requested(context))
            Settings.Global.putInt(resolver,R252cDebugSwitch.GLOBAL_FLAG,0)
            assertFalse("Disabling the ADB flag MUST restore OFF",
                R252cDebugSwitch.requested(context))
        } finally {
            Settings.Global.putString(resolver,R252cDebugSwitch.GLOBAL_FLAG,original)
        }
    }
}
