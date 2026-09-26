// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

object DynamicColorSeedProvider {
    const val DEFAULT_SEED: Int = -10053458 // 0xFF6750A4, Material baseline purple

    private data class SeedKey(
        val wallpaperId: Int,
        val systemAccent: Int,
        val uiMode: Int,
    )

    private data class SeedCache(
        val key: SeedKey,
        val seed: Int,
    )

    @Volatile private var cache: SeedCache? = null
    private val extractionRunning = AtomicBoolean(false)

    fun getSeed(context: Context, isDark: Boolean): Int {
        val appContext = context.applicationContext
        val key = buildKey(appContext, isDark)
        val cached = cache
        if (cached != null && cached.key == key) return cached.seed

        val fallback = systemAccentOrFallback(appContext, isDark)
        cache = SeedCache(key, fallback)
        requestWallpaperSeed(appContext, key, fallback)
        return fallback
    }

    fun hasSeedChanged(context: Context, isDark: Boolean, currentSeed: Int): Boolean {
        val key = buildKey(context.applicationContext, isDark)
        val cached = cache
        return cached == null || cached.key != key || cached.seed != currentSeed
    }

    internal fun fallbackSeedForTests(context: Context?, isDark: Boolean = false): Int =
        context?.let { systemAccentOrFallback(it, isDark) } ?: DEFAULT_SEED

    private fun requestWallpaperSeed(context: Context, key: SeedKey, fallback: Int) {
        if (!extractionRunning.compareAndSet(false, true)) return
        Thread({
            try {
                val wallpaperSeed = extractWallpaperSeed(context) ?: fallback
                val current = cache
                if (current?.key == key && current.seed != wallpaperSeed) {
                    cache = SeedCache(key, wallpaperSeed)
                    DynamicSchemeProvider.clearCache()
                    runCatching { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
                }
            } finally {
                extractionRunning.set(false)
            }
        }, "HeliBoard-DynamicColorSeed").apply { isDaemon = true }.start()
    }

    private fun buildKey(context: Context, isDark: Boolean): SeedKey {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return SeedKey(
            wallpaperId = wallpaperId(context),
            systemAccent = systemAccentOrFallback(context, isDark),
            uiMode = uiMode,
        )
    }

    private fun wallpaperId(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return 0
        return runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrDefault(0)
    }

    private fun systemAccentOrFallback(context: Context, isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val colorRes = if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
            return runCatching { ContextCompat.getColor(context, colorRes) }.getOrDefault(DEFAULT_SEED)
        }
        return DEFAULT_SEED
    }

    private fun extractWallpaperSeed(context: Context): Int? = runCatching {
        val drawable = WallpaperManager.getInstance(context).drawable ?: return@runCatching null
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> Bitmap.createBitmap(
                max(1, min(96, drawable.intrinsicWidth.takeIf { it > 0 } ?: 96)),
                max(1, min(96, drawable.intrinsicHeight.takeIf { it > 0 } ?: 96)),
                Bitmap.Config.ARGB_8888,
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        } ?: return@runCatching null
        averageSeed(bitmap)
    }.getOrNull()

    private fun averageSeed(source: Bitmap): Int? {
        val bitmap = if (source.width > 96 || source.height > 96) {
            Bitmap.createScaledBitmap(source, 96, 96, true)
        } else source

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val hsv = FloatArray(3)
        val strideX = max(1, bitmap.width / 48)
        val strideY = max(1, bitmap.height / 48)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 200) {
                    Color.colorToHSV(color, hsv)
                    val value = hsv[2]
                    val saturation = hsv[1]
                    if (value in 0.08f..0.95f && saturation >= 0.08f) {
                        red += Color.red(color)
                        green += Color.green(color)
                        blue += Color.blue(color)
                        count++
                    }
                }
                x += strideX
            }
            y += strideY
        }
        if (count == 0L) return null
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }
}
