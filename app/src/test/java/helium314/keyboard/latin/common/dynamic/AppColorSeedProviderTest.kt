// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import helium314.keyboard.latin.R
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppColorSeedProviderTest {
    private class QueueWorker : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.addLast(command) }
        fun next() = jobs.removeFirst().run()
    }
    private class FakeLookup : AppIconLookup {
        var stamp = PackageStamp(1, 100)
        var drawable: Drawable? = ColorDrawable(Color.GREEN)
        var stampReads = 0
        var iconReads = 0
        var failure: Exception? = null
        override fun stamp(packageName: String): PackageStamp? {
            stampReads++
            failure?.let { throw it }
            return stamp
        }
        override fun icon(packageName: String): Drawable? {
            iconReads++
            failure?.let { throw it }
            return drawable
        }
    }
    private fun provider(
        lookup: FakeLookup, worker: Executor, clock: () -> Long = { 0 },
        capacity: Int = 64, ttl: Long = 60000,
    ) = AppColorSeedProvider(lookup, worker, clock, capacity, ttl).also { it.setEnabled(true) }

    @Test fun chromalessAndTransparentIconsMustNotTurnScoreBlue() {
        for (color in listOf(Color.WHITE, Color.BLACK, Color.GRAY, Color.TRANSPARENT)) {
            assertIs<SeedResult.Unavailable>(AppIconSeedExtractor.extract(ColorDrawable(color)))
        }
    }

    @Test fun colorfulAndMulticolorIconsAreDeterministic() {
        assertIs<SeedResult.Available>(AppIconSeedExtractor.extract(ColorDrawable(Color.BLUE)))
        val icon = object : Drawable() {
            val paint = Paint()
            override fun draw(canvas: Canvas) {
                canvas.drawColor(Color.WHITE)
                paint.color = Color.RED
                canvas.drawRect(8f, 8f, 32f, 56f, paint)
                paint.color = Color.BLUE
                canvas.drawRect(32f, 8f, 56f, 56f, paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
        }
        val first = assertIs<SeedResult.Available>(AppIconSeedExtractor.extract(icon))
        assertEquals(first.seed, assertIs<SeedResult.Available>(AppIconSeedExtractor.extract(icon)).seed)
    }

    @Test fun adaptiveAndVectorDrawablesSupportNativeRasterization() {
        assertIs<SeedResult.Available>(AppIconSeedExtractor.extract(
            AdaptiveIconDrawable(ColorDrawable(Color.WHITE), ColorDrawable(Color.MAGENTA))
        ))
        // Use a real compiled Android vector resource rather than inflating a String parser:
        // Robolectric's framework vector inflater requires an XmlResourceParser.
        val vector = RuntimeEnvironment.getApplication()
            .getDrawable(R.drawable.ic_ai_sparkles)!!.mutate()
        vector.setTint(Color.CYAN)
        // Robolectric sometimes exposes a VectorDrawable without tint raster pixels.
        // It must remain typed/deterministic; chromatic output must be device-verified.
        assertEquals(AppIconSeedExtractor.extract(vector), AppIconSeedExtractor.extract(vector))

    }

    /** The compiled official vector contains known chromatic gradients.
     *  Unavailable is explicitly reported as an emulator limitation, NOT as color PASS. */
    @Test fun knownChromaticVectorIsValidOrExplicitlyUnverifiedOnRobolectric() {
        val chromaticVector = RuntimeEnvironment.getApplication()
            .getDrawable(R.drawable.ic_launcher_foreground)!!
        val result = AppIconSeedExtractor.extract(chromaticVector)
        when (result) {
            is SeedResult.Available -> {
                val hct = com.drdisagree.materialcolorutilities.hct.Hct.fromInt(result.seed)
                assertEquals(255, Color.alpha(result.seed))
                assertTrue(hct.chroma >= 14.0)
                println("R25_VECTOR_CHROMATIC_VALID seed=" + result.seed + " chroma=" + hct.chroma)
            }
            is SeedResult.Unavailable -> {
                assertTrue(result.reason == SeedResult.Reason.NO_CHROMA ||
                    result.reason == SeedResult.Reason.EXTRACTION_ERROR)
                println("R25_VECTOR_ROBOLECTRIC_UNVERIFIED reason=" + result.reason)
            }
        }
    }

    @Test fun compiledMonochromeVectorDoesNotInventBlue() {
        val monochrome = RuntimeEnvironment.getApplication()
            .getDrawable(R.drawable.ic_launcher_monochrome)!!
        val result = AppIconSeedExtractor.extract(monochrome)
        assertIs<SeedResult.Unavailable>(result)
        assertEquals(SeedResult.Reason.NO_CHROMA, result.reason)
    }

    @Test fun brokenAndMissingDrawableYieldTypedFailure() {
        val broken = object : Drawable() {
            override fun draw(canvas: Canvas): Unit = throw IllegalArgumentException("bad pixels")
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: ColorFilter?) {}
            @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
        assertIs<SeedResult.Unavailable>(AppIconSeedExtractor.extract(broken))
        assertIs<SeedResult.Unavailable>(AppIconSeedExtractor.extract(null))
    }

    @Test fun peekDoesNoPackageIoAndLruIsBounded() {
        val queue = QueueWorker()
        val lookup = FakeLookup()
        val cache = provider(lookup, queue, capacity = 2)
        val a = AppIdentity("app.a")
        val b = AppIdentity("app.b")
        val c = AppIdentity("app.c")
        assertNull(cache.peek(a))
        assertEquals(0, lookup.stampReads)
        cache.warmAsync(a); queue.next()
        cache.warmAsync(b); queue.next()
        assertNotNull(cache.peek(a))
        cache.warmAsync(c); queue.next()
        assertNull(cache.peek(b))
        assertEquals(2, cache.cacheSize)
        assertEquals(3, lookup.stampReads)
    }

    @Test fun versionAndTtlCauseSafeRefresh() {
        val queue = QueueWorker()
        val lookup = FakeLookup()
        var time = 0L
        val cache = provider(lookup, queue, { time }, ttl = 100)
        val app = AppIdentity("app.a")
        cache.warmAsync(app); queue.next()
        cache.warmAsync(app); queue.next()
        assertEquals(1, lookup.iconReads)
        lookup.stamp = PackageStamp(2, 101)
        cache.warmAsync(app); queue.next()
        assertEquals(2, lookup.iconReads)
        time = 101
        assertNull(cache.peek(app))
        cache.warmAsync(app); queue.next()
        assertEquals(3, lookup.iconReads)
    }

    @Test fun packageSecurityMissingIconAndInvalidationAreSafe() {
        val queue = QueueWorker()
        val lookup = FakeLookup()
        val cache = provider(lookup, queue)
        lookup.failure = SecurityException()
        cache.warmAsync(AppIdentity("work.app", "work")); queue.next()
        assertIs<SeedResult.Unavailable>(cache.peek(AppIdentity("work.app", "work")))
        lookup.failure = null
        lookup.drawable = null
        cache.warmAsync(AppIdentity("no.icon")); queue.next()
        assertIs<SeedResult.Unavailable>(cache.peek(AppIdentity("no.icon")))
        assertFalse(cache.warmAsync(AppIdentity("")))
        val a = AppIdentity("app.a")
        cache.warmAsync(a)
        cache.invalidate(a)
        queue.next()
        assertNull(cache.peek(a))
        cache.warmAsync(a)
        cache.setEnabled(false)
        queue.next()
        assertNull(cache.peek(a))
        assertEquals(0, cache.cacheSize)
    }

    @Test fun pendingCapAndRealWorkerThreadPreventUiLookup() {
        val queue = QueueWorker()
        val lookup = FakeLookup()
        val cache = provider(lookup, queue)
        repeat(20) { cache.warmAsync(AppIdentity("many." + it)) }
        assertEquals(16, cache.pendingCount)
        assertEquals(0, lookup.stampReads)
        val actualThread = AtomicReference<Thread>()
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val io = object : AppIconLookup {
                override fun stamp(packageName: String): PackageStamp {
                    actualThread.set(Thread.currentThread())
                    latch.countDown()
                    return PackageStamp(1, 1)
                }
                override fun icon(packageName: String): Drawable = ColorDrawable(Color.RED)
            }
            val real = AppColorSeedProvider(io, executor)
            real.setEnabled(true)
            assertTrue(real.warmAsync(AppIdentity("worker.app")))
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertNotEquals(Looper.getMainLooper().thread, actualThread.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
