// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context
import helium314.keyboard.keyboard.KeyboardTheme

/** No paint, view, input text, worker callback or theme reconstruction. OFF by default. */
class R252ColorLifecycle internal constructor(
    private val coordinator: AppSessionColorCoordinator,
) {
    constructor(context: Context) : this(AppSessionColorCoordinator(context, AppColorSeedProvider(context)))

    private var enabled = false
    private var active: SessionIdentity? = null
    private var previous: SessionIdentity? = null
    private var snapshot: SessionColorSnapshot? = null

    // State is session-scoped, not persisted by the lifecycle.
    private var lastEnabledGate: Boolean? = null

    /** Read only a local opt-in preference in release; debug retains its ADB override.
     * Applied at the next real editor boundary; never from an icon worker.
     */
    @Synchronized
    fun refreshActivation(context: Context) {
        val userOptIn = helium314.keyboard.latin.utils.KtxKt.prefs(context).getBoolean(
            helium314.keyboard.latin.settings.Settings.PREF_R252_BY_APP_COLORS, false)
        val debugAdbRequested = helium314.keyboard.latin.BuildConfig.DEBUG &&
            R252cDebugSwitch.requested(context)
        val requested = R252ReleaseActivationPolicy.enabled(userOptIn,
            helium314.keyboard.latin.BuildConfig.DEBUG, debugAdbRequested)
        if (enabled != requested) enableForTests(requested)
        if (lastEnabledGate != requested) {
            android.util.Log.i("R252_COLOR_SOURCE",
                "state=" + (if (requested) "ON" else "OFF") +
                " source=" + (if (userOptIn) "user-opt-in" else if (debugAdbRequested) "debug-adb" else "system"))
            if (helium314.keyboard.latin.BuildConfig.DEBUG && debugAdbRequested) {
                android.util.Log.i("R252C_DEBUG_SWITCH",
                    "state=ON variant=debug source=Settings.Global")
            }
            lastEnabledGate = requested
        }
    }

    @Synchronized
    internal fun enableForTests(value: Boolean) {
        if (enabled == value) return
        enabled = value
        active = null
        previous = null
        snapshot = null
        R252PaletteRegistry.clear()
        coordinator.setEnabled(value)
    }

    /** Normal installations do not schedule icon reads or retain host identity. */
    @Synchronized
    fun onEditorStart(
        packageName: String?, fieldId: Int, inputType: Int, imeOptions: Int,
        displayId: Int?, restarting: Boolean, isDark: Boolean,
        editorConnectionId: Int? = null,
    ) {
        if (!enabled) return
        // A second editor connection from the same package is a new session, even
        // when framework delivers restarting=true and inputType remains identical.
        val sameClientConnection = active?.editorConnectionId == editorConnectionId
        val next = coordinator.onEditorStarted(packageName, isDark,
            restarting = restarting && sameClientConnection)
        previous = active
        active = SessionIdentity(packageName, fieldId, inputType, imeOptions,
            displayId, next.epoch, editorConnectionId)
        snapshot = next
    }

    /**
     * Called strictly from LatinIME.onStartInputViewInternal before SettingsValues and
     * KeyboardSwitcher render. A delayed worker has no access to this method or the view.
     */
    @Synchronized
    fun prepareAtInputBoundary(context: Context, restarting: Boolean,
                               composing: Boolean, selected: Boolean): Boolean {
        if (!enabled) return false
        val currentSession = active ?: return false
        val currentSnapshot = snapshot ?: return false
        val candidate = KeyboardTheme.paletteIdentityForSession(context, currentSnapshot)
        val previousPalette = R252PaletteRegistry.current()?.palette
        val decision = PaletteTransitionPolicy.decide(previous, currentSession,
            restarting, composing || selected, previousPalette, candidate, currentSession.epoch)
        return when (decision) {
            is PaletteDecision.ApplyAtBoundary -> {
                R252PaletteRegistry.bind(currentSnapshot, decision.palette)
                true
            }
            PaletteDecision.KeepPalette -> {
                if (candidate != null) R252PaletteRegistry.bind(currentSnapshot, candidate)
                false
            }
            is PaletteDecision.Fallback -> {
                // Non-local/unsupported theme: use the existing system/static path.
                val wasApp = previousPalette != null
                R252PaletteRegistry.clear()
                wasApp
            }
            is PaletteDecision.Defer, PaletteDecision.RejectStale -> false
        }
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
