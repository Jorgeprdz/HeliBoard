// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context
import android.os.Build

internal enum class SeedOrigin { SYSTEM_WALLPAPER, BY_APP }

/** Immutable session choice: no text, editor instance or drawable is retained. */
internal data class SessionColorSnapshot(
    val epoch: Long,
    val identity: AppIdentity?,
    val isDark: Boolean,
    val origin: SeedOrigin,
    val seed: Int,
    val fingerprint: Long,
)

internal class AppSessionColorCoordinator(
    private val seeds: AppColorSeedProvider,
    private val system: (Boolean) -> DynamicColorSeedProvider.Snapshot,
) {
    constructor(context: Context, seeds: AppColorSeedProvider) :
        this(seeds, { dark -> DynamicColorSeedProvider.snapshot(context, dark) })

    private var enabled = false
    private var epoch = 0L
    private var active: SessionColorSnapshot? = null

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        epoch++
        active = null
        seeds.setEnabled(value)
    }

    /**
     * Not connected to LatinIME until phase 3. Trusted editor caller supplies identity.
     * A cold miss uses wallpaper/system now; async result may affect only the NEXT session.
     */
    @Synchronized
    fun onEditorStarted(
        packageName: String?,
        isDark: Boolean,
        profileScope: String = "current",
        restarting: Boolean = false,
    ): SessionColorSnapshot {
        // Framework validates EditorInfo.packageName against UID beginning with API 23.
        val id = if (Build.VERSION.SDK_INT >= 23 && !packageName.isNullOrBlank() &&
            profileScope.isNotBlank()) AppIdentity(packageName, profileScope) else null
        val current = active
        if (restarting && current != null && current.identity == id &&
            current.isDark == isDark && current.origin == expectedOrigin(id)
        ) return current

        val cached = if (enabled && id != null) seeds.peek(id) else null
        val appSeed = (cached as? SeedResult.Available)?.seed
        val fallback = if (appSeed == null) system(isDark) else null
        val origin = if (appSeed != null) SeedOrigin.BY_APP else SeedOrigin.SYSTEM_WALLPAPER
        val seed = appSeed ?: requireNotNull(fallback).seed
        val nextEpoch = ++epoch
        val fingerprint = (((nextEpoch * 31 + seed.toLong()) * 31 +
            (fallback?.fingerprint ?: 0L)) * 31 + if (isDark) 1L else 0L)
        return SessionColorSnapshot(nextEpoch, id, isDark, origin, seed, fingerprint).also {
            active = it
            if (enabled && id != null) seeds.warmAsync(id)
        }
    }

    private fun expectedOrigin(id: AppIdentity?): SeedOrigin =
        if (enabled && id != null && active?.origin == SeedOrigin.BY_APP) SeedOrigin.BY_APP
        else SeedOrigin.SYSTEM_WALLPAPER

    /** An old finish event cannot destroy another editor's active session. */
    @Synchronized
    fun onEditorFinished(expectedEpoch: Long): Boolean {
        if (active?.epoch != expectedEpoch) return false
        active = null
        epoch++
        return true
    }

    @Synchronized fun currentSnapshot(): SessionColorSnapshot? = active
}
