// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context

/** No paint, view, input text, worker callback or theme reconstruction. OFF by default. */
class R252ColorLifecycle internal constructor(
    private val coordinator: AppSessionColorCoordinator,
) {
    constructor(context: Context) : this(AppSessionColorCoordinator(context, AppColorSeedProvider(context)))

    private var enabled = false
    private var active: SessionIdentity? = null
    private var snapshot: SessionColorSnapshot? = null

    @Synchronized
    internal fun enableForTests(value: Boolean) {
        if (enabled == value) return
        enabled = value
        active = null
        snapshot = null
        coordinator.setEnabled(value)
    }

    /** Normal installations do not schedule icon reads or retain host identity. */
    @Synchronized
    fun onEditorStart(
        packageName: String?, fieldId: Int, inputType: Int, imeOptions: Int,
        displayId: Int, restarting: Boolean, isDark: Boolean,
    ) {
        if (!enabled) return
        val next = coordinator.onEditorStarted(packageName, isDark, restarting = restarting)
        active = SessionIdentity(packageName, fieldId, inputType, imeOptions, displayId, next.epoch)
        snapshot = next
    }

    @Synchronized
    fun onEditorFinish() {
        val epoch = active?.epoch ?: return
        coordinator.onEditorFinished(epoch)
        active = null
        snapshot = null
    }

    @Synchronized
    fun close() {
        enableForTests(false)
    }

    @Synchronized internal fun sessionForTests(): SessionIdentity? = active
    @Synchronized internal fun snapshotForTests(): SessionColorSnapshot? = snapshot
    @Synchronized internal fun enabledForTests(): Boolean = enabled
}
