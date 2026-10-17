// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context
import android.provider.Settings
import helium314.keyboard.latin.BuildConfig

/**
 * ADB-only, debug-build experiment. No preference, UI, permission, observer or receiver.
 * Release and all other non-debug builds have a compile-time constant OFF gate.
 */
internal object R252cDebugSwitch {
    const val GLOBAL_FLAG = "tactic_r252c_by_app_debug"

    /** Testable with no Android or editor side effects. Only literal 1 enables. */
    fun enabledForVariant(isDebugBuild: Boolean, flagValue: Int): Boolean =
        isDebugBuild && flagValue == 1

    fun requested(context: Context): Boolean {
        // BuildConfig.DEBUG is a constant false in release; R8 eliminates the read path.
        if (!BuildConfig.DEBUG) return false
        val flag = try {
            Settings.Global.getInt(context.contentResolver, GLOBAL_FLAG, 0)
        } catch (ignored: SecurityException) {
            0
        }
        return enabledForVariant(BuildConfig.DEBUG, flag)
    }
}
