// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** R2.5.1 infrastructure-only probe. No IME service activation or color extraction. */
@RunWith(AndroidJUnit4::class)
class R251RunnerSmokeTest {
    @Test fun instrumentationAndTargetContextsLoad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertNotNull(instrumentation.context)
        assertNotNull(instrumentation.targetContext)
        Log.i("R251_RUNNER", "SMOKE_PASS context_and_target_context_initialized")
    }
}
