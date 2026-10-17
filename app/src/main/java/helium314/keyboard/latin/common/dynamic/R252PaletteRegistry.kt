// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

/** Main-thread-only rendering binding. Never updated by icon extraction workers. */
internal object R252PaletteRegistry {
    internal data class Binding(val snapshot: SessionColorSnapshot, val palette: PaletteIdentity)
    @Volatile private var active: Binding? = null

    @Synchronized fun bind(snapshot: SessionColorSnapshot, palette: PaletteIdentity) {
        active = Binding(snapshot, palette)
    }
    fun current(): Binding? = active
    @Synchronized fun clear() { active = null }
}
