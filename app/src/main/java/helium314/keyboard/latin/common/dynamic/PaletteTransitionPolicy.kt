// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

/** No Android APIs, views, editor text, PackageManager or wall-clock state. */
internal data class SessionIdentity(
    val packageName: String?,
    val fieldId: Int,
    val inputType: Int,
    val imeOptions: Int,
    val displayId: Int?,
    val epoch: Long,
)

/** Render equivalence excludes editor epochs and package names. */
internal data class PaletteIdentity(
    val argb: Int,
    val family: String,
    val dark: Boolean,
    val themeStyle: String,
    val borders: Boolean,
    val backgroundSignature: String?,
    val mapperVersion: Int = 1,
    val byAppActive: Boolean,
)

internal sealed interface PaletteDecision {
    data object KeepPalette : PaletteDecision
    data class ApplyAtBoundary(val palette: PaletteIdentity) : PaletteDecision
    data class Defer(val palette: PaletteIdentity) : PaletteDecision
    data class Fallback(val reason: String) : PaletteDecision
    data object RejectStale : PaletteDecision
}

/** Pure policy; does not repaint, load a package, or mutate an editor. */
internal object PaletteTransitionPolicy {
    fun decide(
        previous: SessionIdentity?,
        next: SessionIdentity,
        restarting: Boolean,
        composing: Boolean,
        current: PaletteIdentity?,
        candidate: PaletteIdentity?,
        activeEpoch: Long,
    ): PaletteDecision {
        if (next.epoch != activeEpoch || next.epoch < (previous?.epoch ?: Long.MIN_VALUE))
            return PaletteDecision.RejectStale
        if (candidate == null) return PaletteDecision.Fallback("NO_EFFECTIVE_PALETTE")
        if (candidate == current) return PaletteDecision.KeepPalette
        if (composing) return PaletteDecision.Defer(candidate)
        // Same input type on restarting does not imply the same owner package.
        // Host/field/epoch never force a visual rebuild when the palette is equal.
        return PaletteDecision.ApplyAtBoundary(candidate)
    }
}
