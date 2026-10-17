// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import org.junit.Assert.*
import org.junit.Test

class R252ReleaseActivationPolicyTest {
    @Test fun releaseOffByDefaultAndAdbCannotEnable() {
        assertFalse(R252ReleaseActivationPolicy.enabled(false, false, false))
        assertFalse(R252ReleaseActivationPolicy.enabled(false, false, true))
    }
    @Test fun releaseOptInIsReversible() {
        assertTrue(R252ReleaseActivationPolicy.enabled(true, false, false))
        assertFalse(R252ReleaseActivationPolicy.enabled(false, false, false))
    }
    @Test fun debugKeepsOldAdbSwitch() {
        assertFalse(R252ReleaseActivationPolicy.enabled(false, true, false))
        assertTrue(R252ReleaseActivationPolicy.enabled(false, true, true))
        assertTrue(R252ReleaseActivationPolicy.enabled(true, true, false))
    }
}
