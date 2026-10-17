// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

/**
 * Production color-source opt-in; independent of the experimental ADB flag.
 * Release ignores all debug switches. A normal install remains OFF.
 */
internal object R252ReleaseActivationPolicy {
    fun enabled(userOptIn: Boolean, isDebugBuild: Boolean, debugAdbRequested: Boolean): Boolean =
        userOptIn || (isDebugBuild && debugAdbRequested)
}
