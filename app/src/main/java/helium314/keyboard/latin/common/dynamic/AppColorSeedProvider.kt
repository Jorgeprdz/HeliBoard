// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import com.drdisagree.materialcolorutilities.hct.Hct
import com.drdisagree.materialcolorutilities.quantize.QuantizerCelebi
import com.drdisagree.materialcolorutilities.score.Score
import java.util.LinkedHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max

/** Trusted editor identity. No cross-user PackageManager access is attempted. */
internal data class AppIdentity(val packageName: String, val profileScope: String = "current")
internal data class PackageStamp(val versionCode: Long, val lastUpdateTime: Long)

internal sealed interface SeedResult {
    data class Available(val seed: Int) : SeedResult
    data class Unavailable(val reason: Reason) : SeedResult
    enum class Reason { INVALID_APP, INACCESSIBLE, NO_ICON, NO_CHROMA, EXTRACTION_ERROR }
}

internal interface AppIconLookup {
    fun stamp(packageName: String): PackageStamp?
    fun icon(packageName: String): Drawable?
}

/** Uses the original installed-app resource, never the launcher or a screenshot. */
internal class AndroidAppIconLookup(context: Context) : AppIconLookup {
    private val packages = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    override fun stamp(packageName: String): PackageStamp? {
        val info = packages.getPackageInfo(packageName, 0)
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
            else info.versionCode.toLong()
        return PackageStamp(version, info.lastUpdateTime)
    }

    override fun icon(packageName: String): Drawable? = packages.getApplicationIcon(packageName)
}

/** Processes a bounded 64x64 rendering with the Material Color Utilities already bundled. */
internal object AppIconSeedExtractor {
    private const val SIZE = 64
    private const val MIN_CHROMA = 14.0
    private const val MIN_ALPHA = 160

    fun extract(icon: Drawable?): SeedResult {
        if (icon == null) return SeedResult.Unavailable(SeedResult.Reason.NO_ICON)
        return try {
            // A chromatic adaptive foreground beats a visually dominant plain background.
            val foreground = if (Build.VERSION.SDK_INT >= 26 && icon is AdaptiveIconDrawable)
                icon.foreground?.let { select(rasterize(it)) }
            else null
            foreground ?: select(rasterize(icon))
                ?: SeedResult.Unavailable(SeedResult.Reason.NO_CHROMA)
        } catch (error: Exception) {
            SeedResult.Unavailable(SeedResult.Reason.EXTRACTION_ERROR)
        }
    }

    private fun rasterize(icon: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        return try {
            icon.setBounds(0, 0, SIZE, SIZE)
            icon.draw(Canvas(bitmap))
            IntArray(SIZE * SIZE).also { bitmap.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun select(pixels: IntArray): SeedResult.Available? {
        val usable = pixels.filter { Color.alpha(it) >= MIN_ALPHA }.map {
            Color.rgb(Color.red(it), Color.green(it), Color.blue(it))
        }.toIntArray()
        if (usable.isEmpty()) return null
        val quantized = QuantizerCelebi.quantize(usable, 16)
        // Filter non-chromatic clusters BEFORE Score, which otherwise invents a fallback.
        val colorful = quantized.filter { (argb, count) ->
            val hct = Hct.fromInt(argb)
            count > 0 && hct.chroma >= MIN_CHROMA && hct.tone in 8.0..96.0
        }
        if (colorful.values.sum() < max(8, pixels.size / 100)) return null
        // Transparent sentinel cannot be produced by a genuine HCT color.
        val candidates = Score.score(colorful, 4, Color.TRANSPARENT, true)
        return candidates.firstOrNull {
            it != Color.TRANSPARENT && Hct.fromInt(it).chroma >= MIN_CHROMA
        }?.let(SeedResult::Available)
    }
}

/**
 * Local-only LRU of seed values, no icon bitmap retention or persisted app inventory.
 * No callback to the IME exists: async completion cannot recolor an active session.
 */
internal class AppColorSeedProvider(
    private val lookup: AppIconLookup,
    private val worker: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Tactic-AppColorSeed").apply { isDaemon = true }
    },
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
    maxEntries: Int = 64,
    private val ttlMs: Long = 60 * 60 * 1000L,
) {
    constructor(context: Context) : this(AndroidAppIconLookup(context))

    private data class Cached(val result: SeedResult, val stamp: PackageStamp?, val expires: Long)

    private val mutex = Any()
    private var enabled = false
    private var serial = 0L
    private val pending = HashMap<AppIdentity, Long>()
    private val capacity = maxEntries.also { require(it in 1..256) }
    private val cache = object : LinkedHashMap<AppIdentity, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AppIdentity, Cached>?): Boolean =
            size > capacity
    }

    internal val cacheSize get() = synchronized(mutex) { cache.size }
    internal val pendingCount get() = synchronized(mutex) { pending.size }

    fun setEnabled(value: Boolean) = synchronized(mutex) {
        if (enabled == value) return@synchronized
        enabled = value
        // Queued and running tickets are logically canceled when source changes.
        pending.clear()
        if (!value) cache.clear()
    }

    /** Pure memory lookup, null on cold miss, expiry or disabled feature; never calls PM. */
    fun peek(identity: AppIdentity): SeedResult? = synchronized(mutex) {
        if (!enabled || identity.packageName.isBlank()) return@synchronized null
        val cached = cache[identity] ?: return@synchronized null
        if (cached.expires <= nowMs()) {
            cache.remove(identity)
            return@synchronized null
        }
        cached.result
    }

    /** Schedules PM metadata, icon rasterization and quantization away from the caller. */
    fun warmAsync(identity: AppIdentity): Boolean {
        if (identity.packageName.isBlank()) return false
        val ticket = synchronized(mutex) {
            if (!enabled || pending.containsKey(identity) || pending.size >= 16) return false
            (++serial).also { pending[identity] = it }
        }
        return try {
            worker.execute {
                val result = try {
                    val stamp = lookup.stamp(identity.packageName)
                    if (stamp == null) Pair(
                        SeedResult.Unavailable(SeedResult.Reason.INACCESSIBLE) as SeedResult, null
                    ) else {
                        val prior = synchronized(mutex) { cache[identity] }
                        if (prior != null && prior.stamp == stamp && prior.expires > nowMs())
                            Pair(prior.result, stamp)
                        else Pair(AppIconSeedExtractor.extract(lookup.icon(identity.packageName)), stamp)
                    }
                } catch (error: PackageManager.NameNotFoundException) {
                    Pair(SeedResult.Unavailable(SeedResult.Reason.INACCESSIBLE), null)
                } catch (error: SecurityException) {
                    Pair(SeedResult.Unavailable(SeedResult.Reason.INACCESSIBLE), null)
                } catch (error: Exception) {
                    Pair(SeedResult.Unavailable(SeedResult.Reason.EXTRACTION_ERROR), null)
                }
                synchronized(mutex) {
                    if (pending[identity] == ticket) {
                        pending.remove(identity)
                        if (enabled) {
                            val ttl = if (result.first is SeedResult.Available) ttlMs
                                else minOf(ttlMs, 5 * 60_000L)
                            cache[identity] = Cached(result.first, result.second, nowMs() + ttl)
                        }
                    }
                }
            }
            true
        } catch (error: RuntimeException) {
            synchronized(mutex) { if (pending[identity] == ticket) pending.remove(identity) }
            false
        }
    }

    fun invalidate(identity: AppIdentity) = synchronized(mutex) {
        cache.remove(identity)
        pending.remove(identity)
    }

    fun invalidatePackage(packageName: String) = synchronized(mutex) {
        cache.keys.filter { it.packageName == packageName }.forEach(cache::remove)
        pending.keys.filter { it.packageName == packageName }.forEach(pending::remove)
    }

    fun clear() = synchronized(mutex) {
        cache.clear()
        pending.clear()
    }
}
