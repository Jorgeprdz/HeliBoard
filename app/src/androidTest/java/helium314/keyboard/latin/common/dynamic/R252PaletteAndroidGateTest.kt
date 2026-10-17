// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque
import java.util.concurrent.Executor

/** Android 15 real lifecycle probes; no app UI/IME activation, no product APK publication. */
@RunWith(AndroidJUnit4::class)
class R252PaletteAndroidGateTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Queue : Executor {
        val pending=ArrayDeque<Runnable>()
        override fun execute(command:Runnable){ pending.addLast(command) }
        fun first()=pending.removeFirst().run()
        fun last()=pending.removeLast().run()
    }
    private class Icons : AppIconLookup {
        override fun stamp(packageName:String)=PackageStamp(1,1)
        override fun icon(packageName:String): Drawable = ColorDrawable(
            if(packageName=="app.a") Color.CYAN else Color.MAGENTA)
    }
    private fun withBoundary(test:(R252ColorLifecycle,Queue)->Unit) {
        val prefs=ctx.prefs()
        val oldDay=prefs.getString(Settings.PREF_THEME_COLORS,null)
        val oldNight=prefs.getString(Settings.PREF_THEME_COLORS_NIGHT,null)
        prefs.edit().putString(Settings.PREF_THEME_COLORS,KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT)
            .putString(Settings.PREF_THEME_COLORS_NIGHT,KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT).commit()
        val queue=Queue()
        val seeds=AppColorSeedProvider(Icons(),queue)
        val coordinator=AppSessionColorCoordinator(seeds) { dark ->
            DynamicColorSeedProvider.Snapshot(if(dark) Color.DKGRAY else Color.LTGRAY,13L,false)
        }
        val lifecycle=R252ColorLifecycle(coordinator)
        try {
            lifecycle.enableForTests(true)
            test(lifecycle,queue)
        } finally {
            lifecycle.close()
            R252PaletteRegistry.clear()
            val editor=prefs.edit()
            if(oldDay==null)editor.remove(Settings.PREF_THEME_COLORS)
            else editor.putString(Settings.PREF_THEME_COLORS,oldDay)
            if(oldNight==null)editor.remove(Settings.PREF_THEME_COLORS_NIGHT)
            else editor.putString(Settings.PREF_THEME_COLORS_NIGHT,oldNight)
            editor.commit()
        }
    }
    private fun R252ColorLifecycle.start(pkg:String,restarting:Boolean=false) =
        onEditorStart(pkg,1,1,0,-1,restarting,false)

    @Test fun coldWarmAndRapidAtoBtoAOnlyApplyOnBoundary() = withBoundary { bridge,queue ->
        bridge.start("app.a")
        assertTrue(bridge.prepareAtInputBoundary(ctx,false,false,false))
        val cold=R252PaletteRegistry.current()!!
        assertEquals(Color.LTGRAY,cold.palette.argb)
        queue.first()
        assertSame(cold,R252PaletteRegistry.current()) // worker never pushes UI changes
        bridge.start("app.b",true)
        assertFalse(bridge.prepareAtInputBoundary(ctx,true,false,false)) // equal cold palette
        queue.first()
        bridge.start("app.a",true)
        assertTrue(bridge.prepareAtInputBoundary(ctx,true,false,false))
        assertEquals(Color.CYAN,R252PaletteRegistry.current()!!.palette.argb)
        assertEquals("app.a",R252PaletteRegistry.current()!!.snapshot.identity?.packageName)
    }
    @Test fun compositionAndSelectionDeferWithoutMixingSnapshots() = withBoundary { bridge,queue ->
        bridge.start("app.a");bridge.prepareAtInputBoundary(ctx,false,false,false)
        queue.first()
        bridge.start("app.a");bridge.prepareAtInputBoundary(ctx,false,false,false)
        val a=R252PaletteRegistry.current()!!.palette
        bridge.start("app.b",true)
        assertFalse(bridge.prepareAtInputBoundary(ctx,true,true,false))
        assertEquals(a,R252PaletteRegistry.current()!!.palette)
        assertFalse(bridge.prepareAtInputBoundary(ctx,true,false,true))
        assertEquals(a,R252PaletteRegistry.current()!!.palette)
        // Explicit input boundary only, never asynchronously inside composition.
        assertTrue(bridge.prepareAtInputBoundary(ctx,true,false,false))
    }
    @Test fun delayedWorkerCannotCallViewReloadOrHideShow() = withBoundary { bridge,queue ->
        val switcher=KeyboardSwitcher.getInstance()
        val before=listOf(switcher.r252PaletteViewReplacements,
            switcher.r252PaletteHideCalls,switcher.r252PaletteShowCalls)
        bridge.start("app.a");bridge.prepareAtInputBoundary(ctx,false,false,false)
        val registryBefore=R252PaletteRegistry.current()
        queue.first()
        assertSame(registryBefore,R252PaletteRegistry.current())
        val after=listOf(switcher.r252PaletteViewReplacements,
            switcher.r252PaletteHideCalls,switcher.r252PaletteShowCalls)
        assertEquals(before,after)
        val delta=after.zip(before).map { it.first-it.second }
        assertEquals(listOf(0,0,0),delta)
        android.util.Log.i("R252_WORKER_METRICS",
            "setInputView=${delta[0]} hideWindow=${delta[1]} showWindow=${delta[2]}")
    }
}
