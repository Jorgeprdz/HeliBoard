// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.drdisagree.materialcolorutilities.hct.Hct
import helium314.keyboard.latin.test.R as TestR
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** No service binding, IME activation, screenshots, network, or production-resource edits. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class VectorAndroidGateTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val expected = 0xFF00BCD4.toInt()
    private fun vector(id: Int): Drawable = instrumentation.context.getDrawable(id)!!.mutate()
    private fun <T> worker(body: () -> T): T {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "R25-VectorAndroidGate") }
        return try {
            executor.submit(Callable {
                assertNotEquals("Extractor must run off main", Looper.getMainLooper().thread, Thread.currentThread())
                body()
            }).get(60, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            assertTrue("Worker must terminate", executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
    private fun bytesAllocated(): Long? = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
    private fun hueDistance(a: Double, b: Double): Double {
        val delta = abs(a - b) % 360.0
        return min(delta, 360.0 - delta)
    }
    private fun assertCyan(result: SeedResult): SeedResult.Available {
        assertTrue("Expected real chromatic seed, got $result", result is SeedResult.Available)
        val available = result as SeedResult.Available
        val hct = Hct.fromInt(available.seed)
        assertEquals(255, Color.alpha(available.seed))
        assertTrue("Chroma=${hct.chroma}", hct.chroma >= 14.0)
        assertTrue("Hue=${hct.hue}", hueDistance(hct.hue, Hct.fromInt(expected).hue) <= 25.0)
        return available
    }
    private fun evidence(name: String, report: JSONObject) {
        val directory = File(instrumentation.targetContext.filesDir, "r25-vector-gate")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.json").writeText(report.toString(2))
        Log.i("R25_VECTOR_GATE", report.put("case", name).toString())
    }
    @Test fun cyanVectorRealRasterizationIsChromaticDeterministicAndOffMain() = worker {
        val samples = JSONArray()
        val durations = mutableListOf<Long>()
        val seeds = mutableSetOf<Int>()
        repeat(10) { index ->
            val icon = vector(TestR.drawable.r25_vector_cyan)
            val before = bytesAllocated()
            val start = SystemClock.elapsedRealtimeNanos()
            val result = AppIconSeedExtractor.extract(icon)
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            val after = bytesAllocated()
            val available = assertCyan(result)
            seeds.add(available.seed)
            durations.add(elapsed)
            val hct = Hct.fromInt(available.seed)
            samples.put(JSONObject().put("iteration", index + 1)
                .put("argb", "#%08X".format(available.seed)).put("hue", hct.hue)
                .put("chroma", hct.chroma).put("hue_distance", hueDistance(hct.hue, Hct.fromInt(expected).hue))
                .put("elapsed_ns", elapsed).put("thread", Thread.currentThread().name)
                .put("main_thread", false).put("process_managed_allocated_bytes_delta",
                    if (before != null && after != null && after >= before) after - before else JSONObject.NULL))
        }
        assertEquals("Ten independent inflations must yield identical seeds", 1, seeds.size)
        val sorted = durations.sorted()
        fun percentile(p: Double) = sorted[(ceil(p * sorted.size).toInt() - 1).coerceAtLeast(0)]
        evidence("cyan", JSONObject().put("execution", "ANDROID_INSTRUMENTATION")
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("android_release", Build.VERSION.RELEASE)
            .put("samples", samples).put("n", 10).put("p50_ns", percentile(0.50)).put("p95_ns", percentile(0.95))
            .put("percentile_method", "nearest-rank; no warmup; extraction only, inflation excluded")
            .put("allocation_scope", "ART process managed allocation counter delta; concurrent work possible; not peak/retention/native heap"))
    }
    @Test fun whiteBlackAndTransparentVectorsNeverInventChromaticSeeds() = worker {
        val report = JSONObject()
        for ((name, id) in listOf("white" to TestR.drawable.r25_vector_white,
            "black" to TestR.drawable.r25_vector_black, "transparent" to TestR.drawable.r25_vector_transparent)) {
            repeat(10) {
                val result = AppIconSeedExtractor.extract(vector(id))
                assertEquals(SeedResult.Unavailable(SeedResult.Reason.NO_CHROMA), result)
            }
            report.put(name, "NO_CHROMA_10_OF_10")
        }
        evidence("neutral", report)
    }
    @Test fun realAdaptiveForegroundProducesCyanSeed() = worker {
        val icon = AdaptiveIconDrawable(vector(TestR.drawable.r25_vector_white), vector(TestR.drawable.r25_vector_cyan))
        val result = assertCyan(AppIconSeedExtractor.extract(icon))
        evidence("adaptive", JSONObject().put("argb", "#%08X".format(result.seed))
            .put("hue", Hct.fromInt(result.seed).hue).put("chroma", Hct.fromInt(result.seed).chroma))
    }
    @Test fun throwingAndMissingDrawablesReturnTypedSafeFallbacks() = worker {
        val broken = object : Drawable() {
            override fun draw(canvas: Canvas): Unit = throw IllegalArgumentException("R25 test-only broken drawable")
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
        repeat(10) {
            assertEquals(SeedResult.Unavailable(SeedResult.Reason.EXTRACTION_ERROR), AppIconSeedExtractor.extract(broken))
            assertEquals(SeedResult.Unavailable(SeedResult.Reason.NO_ICON), AppIconSeedExtractor.extract(null))
        }
        evidence("fallback", JSONObject().put("throwing", "EXTRACTION_ERROR_10_OF_10").put("null", "NO_ICON_10_OF_10"))
    }
    @Test fun nonexistentPackageRemainsInaccessibleWithoutChangingIme() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val provider = AppColorSeedProvider(AndroidAppIconLookup(instrumentation.targetContext), executor)
            val identity = AppIdentity("invalid.r25.never.installed")
            assertNull(provider.peek(identity))
            provider.setEnabled(true)
            assertTrue(provider.warmAsync(identity))
            executor.submit {}.get(30, TimeUnit.SECONDS) // barrier after actual PackageManager lookup
            assertEquals(SeedResult.Unavailable(SeedResult.Reason.INACCESSIBLE), provider.peek(identity))
            provider.setEnabled(false)
            assertNull(provider.peek(identity))
            assertEquals(0, provider.cacheSize)
            evidence("inaccessible", JSONObject().put("result", "INACCESSIBLE").put("disabled_cache", "EMPTY"))
        } finally {
            executor.shutdownNow()
        }
    }
}
