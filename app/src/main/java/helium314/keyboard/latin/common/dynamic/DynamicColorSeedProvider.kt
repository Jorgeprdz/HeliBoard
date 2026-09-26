// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object DynamicColorSeedProvider {
    internal const val FALLBACK_SEED: Int = -10006364 // #FF6750A4

    data class Snapshot(
        val seed: Int,
        val fingerprint: Long,
        val fromWallpaper: Boolean,
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HeliBoard-DynamicSeed").apply { isDaemon = true }
    }
    private val refreshInFlight = AtomicBoolean(false)
    private val generation = AtomicLong(0L)

    @Volatile
    private var wallpaperSeed: Int? = null

    fun snapshot(context: Context, isDark: Boolean): Snapshot {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperSeedApi27.ensureRegistered(appContext)
            if (wallpaperSeed == null) refreshWallpaperAsync(appContext, false)
        }
        val systemSeed = systemAccentOrNull(appContext)
        val seed = resolveSeed(wallpaperSeed, systemSeed)
        val systemSignature = systemPaletteSignature(appContext)
        val fingerprint = mixFingerprint(seed, generation.get(), systemSignature, isDark)
        return Snapshot(seed, fingerprint, wallpaperSeed != null)
    }

    internal fun resolveSeed(wallpaper: Int?, systemAccent: Int?): Int =
        wallpaper ?: systemAccent ?: FALLBACK_SEED

    internal fun systemAccentOrNull(context: Context): Int? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getColor(context, android.R.color.system_accent1_500)
        } else {
            val value = TypedValue()
            if (!context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) return@runCatching null
            if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
        }
    }.getOrNull()

    internal fun systemPaletteSignature(context: Context): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return (systemAccentOrNull(context) ?: FALLBACK_SEED).toLong()
        }
        return runCatching {
            val colors = intArrayOf(
                ContextCompat.getColor(context, android.R.color.system_accent1_100),
                ContextCompat.getColor(context, android.R.color.system_accent1_500),
                ContextCompat.getColor(context, android.R.color.system_accent1_900),
                ContextCompat.getColor(context, android.R.color.system_accent2_500),
                ContextCompat.getColor(context, android.R.color.system_accent3_500),
                ContextCompat.getColor(context, android.R.color.system_neutral1_500),
                ContextCompat.getColor(context, android.R.color.system_neutral2_500),
            )
            colors.fold(1125899906842597L) { acc, color -> acc * 31L + color.toLong() }
        }.getOrElse { (systemAccentOrNull(context) ?: FALLBACK_SEED).toLong() }
    }

    private fun mixFingerprint(seed: Int, generation: Long, systemSignature: Long, isDark: Boolean): Long {
        var result = 17L
        result = result * 31L + seed.toLong()
        result = result * 31L + generation
        result = result * 31L + systemSignature
        result = result * 31L + if (isDark) 1L else 0L
        return result
    }

    private fun refreshWallpaperAsync(context: Context, forceInvalidation: Boolean) {
        if (!refreshInFlight.compareAndSet(false, true)) return
        executor.execute {
            val newSeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                WallpaperSeedApi27.readSeed(context)
            } else null
            val oldSeed = wallpaperSeed
            wallpaperSeed = newSeed
            val changed = oldSeed != newSeed || forceInvalidation
            if (changed) {
                generation.incrementAndGet()
                DynamicSchemeProvider.clearCache()
                Handler(Looper.getMainLooper()).post {
                    runCatching { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
                }
            }
            refreshInFlight.set(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private object WallpaperSeedApi27 {
        private val registered = AtomicBoolean(false)
        private var contextRef: WeakReference<Context>? = null

        private val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM == 0) return@OnColorsChangedListener
            contextRef?.get()?.let { refreshWallpaperAsync(it, true) }
        }

        fun ensureRegistered(context: Context) {
            contextRef = WeakReference(context)
            if (!registered.compareAndSet(false, true)) return
            runCatching {
                WallpaperManager.getInstance(context)
                    .addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            }.onFailure {
                registered.set(false)
            }
        }

        fun readSeed(context: Context): Int? = runCatching {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
        }.getOrNull()
    }
}
