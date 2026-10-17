package helium314.keyboard.latin.common.dynamic

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings as DeviceSettings
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.R252bHostActivity
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android EditorInfo and two independently-installed UIDs. No synthetic callbacks.
 * This is NOT evidence of first GPU-presented frame, detached overlays or DeX. */
@RunWith(AndroidJUnit4::class)
class R252cRealAppsTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val appA = "com.jorgeprdz.r252c.a"
    private val appB = "com.jorgeprdz.r252c.b"
    private data class Frame(val pkg:String,val origin:SeedOrigin,val expected:Int,
        val tint:Int?,val raster:Int?,val loaded:Int,val epoch:Long,val predrawMs:Long)
    private class Observer:LatinIME.R252bHarnessObserver {
        @Volatile var latch = CountDownLatch(1)
        val frames=CopyOnWriteArrayList<Frame>()
        @Volatile var installs=0
        @Volatile var hides=0
        @Volatile var shows=0
        override fun onInputViewInstalled(view:View) { installs++ }
        override fun onWindowHide() { hides++ }
        override fun onWindowShow() { shows++ }
        override fun onInputBoundary(inputView:View?,info:EditorInfo,restarting:Boolean) {
            val pkg=info.packageName
            if(pkg!="com.jorgeprdz.r252c.a" && pkg!="com.jorgeprdz.r252c.b") return
            val root=requireNotNull(inputView) { "No real InputView" }
            val binding=requireNotNull(R252PaletteRegistry.current()) { "No palette binding for "+pkg }
            check(binding.snapshot.identity?.packageName==pkg) { "Stale palette bound to "+pkg }
            val p=binding.palette
            val map=MonetColorMapper.map(DynamicSchemeProvider.get(p.argb,
                DynamicSchemeType.valueOf(p.family),p.dark),p.themeStyle,p.borders)
            val color=map.getValue(ColorType.MAIN_BACKGROUND)
            val expected=Color.argb(if(p.dark)196 else 202,
                Color.red(color),Color.green(color),Color.blue(color))
            val view=requireNotNull(root.findViewById<View>(R.id.main_keyboard_frame))
            val tree=view.viewTreeObserver
            tree.addOnPreDrawListener(object:ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw():Boolean {
                    if(tree.isAlive) tree.removeOnPreDrawListener(this)
                    val drawable=view.background
                    val tint=(drawable as? GradientDrawable)?.color?.defaultColor
                    val raster=drawable?.let { d ->
                        val old=Rect(d.bounds)
                        val bmp=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
                        try { d.setBounds(0,0,32,32);d.draw(Canvas(bmp));bmp.getPixel(16,16) }
                        finally { d.bounds=old;bmp.recycle() }
                    }
                    val frame=Frame(pkg,binding.snapshot.origin,expected,tint,raster,
                        Settings.getValues().mColors.get(ColorType.MAIN_BACKGROUND),
                        binding.snapshot.epoch,SystemClock.uptimeMillis())
                    frames.add(frame)
                    android.util.Log.i("R252C_REAL_APPS",
                        "pkg="+pkg+" origin="+frame.origin+" restarting="+restarting+
                        " epoch="+frame.epoch+" expected="+expected+" tint="+tint+
                        " raster="+raster+" loaded="+frame.loaded+" firstPreDraw="+frame.predrawMs)
                    android.util.Log.i("R252C_SURFACE_GATE",
                        "main=OBSERVED suggestionShown="+(root.findViewById<View>(R.id.suggestion_strip_view)?.isShown)+
                        " detachedPopups=NOT_PROVEN emoji=NOT_PROVEN clipboard=NOT_PROVEN oneHand=NOT_PROVEN")
                    latch.countDown()
                    return true
                }
            })
            view.invalidate()
        }
    }
    private fun shell(cmd:String):String {
        val fd:ParcelFileDescriptor=inst.uiAutomation.executeShellCommand(cmd)
        try { return java.io.FileInputStream(fd.fileDescriptor).use { String(it.readBytes(),Charsets.UTF_8) } }
        finally { fd.close() }
    }
    private fun <T> onMain(fn:()->T):T {
        var answer:T?=null
        var issue:Throwable?=null
        inst.runOnMainSync { try { answer=fn() } catch(t:Throwable) { issue=t } }
        issue?.let { throw AssertionError("Main-thread failure",it) }
        @Suppress("UNCHECKED_CAST") return answer as T
    }
    @Test fun realAtoBtoAThreeRounds() {
        val uidA=context.packageManager.getPackageUid(appA,0)
        val uidB=context.packageManager.getPackageUid(appB,0)
        assertNotEquals("UIDs must differ",uidA,uidB)
        android.util.Log.i("R252C_UID","pkgA="+appA+" uidA="+uidA+" pkgB="+appB+" uidB="+uidB)
        val prefs=context.prefs()
        val oldDay=prefs.getString(Settings.PREF_THEME_COLORS,null)
        val oldNight=prefs.getString(Settings.PREF_THEME_COLORS_NIGHT,null)
        val previous=DeviceSettings.Secure.getString(context.contentResolver,
            DeviceSettings.Secure.DEFAULT_INPUT_METHOD)
        var host:R252bHostActivity?=null
        var lifecycle:R252ColorLifecycle?=null
        val observer=Observer()
        try {
            assertNotNull("Prior IME must be restorable",previous)
            prefs.edit().putString(Settings.PREF_THEME_COLORS,KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT)
                .putString(Settings.PREF_THEME_COLORS_NIGHT,KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT).commit()
            val component=context.packageName+"/helium314.keyboard.latin.LatinIME"
            shell("ime enable "+component)
            shell("ime set "+component)
            host=inst.startActivitySync(Intent().setClassName(context.packageName,
                "helium314.keyboard.latin.R252bHostActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                as R252bHostActivity
            onMain { host!!.showImeForTest() }
            var ime:LatinIME?=null
            repeat(60) {
                val running=LatinIME.r252bRunningInstanceForTests()
                if(running!=null && onMain { running.isInputViewShown }) { ime=running;return@repeat }
                if(ime==null) SystemClock.sleep(150)
            }
            val latin=requireNotNull(ime) { "Real LatinIME not shown" }
            val seeds=AppColorSeedProvider(AndroidAppIconLookup(context),Executor { it.run() })
            val coord=AppSessionColorCoordinator(seeds) { dark ->
                DynamicColorSeedProvider.Snapshot(if(dark)Color.DKGRAY else Color.LTGRAY,9L,false)
            }
            lifecycle=R252ColorLifecycle(coord)
            lifecycle.enableForTests(true)
            assertTrue(seeds.warmAsync(AppIdentity(appA)))
            assertTrue(seeds.warmAsync(AppIdentity(appB)))
            assertTrue(seeds.peek(AppIdentity(appA)) is SeedResult.Available)
            assertTrue(seeds.peek(AppIdentity(appB)) is SeedResult.Available)
            onMain { latin.r252bInstallSessionForTests(lifecycle!!) }
            LatinIME.r252bObserveForTests(observer)
            for(round in 1..3) for((step,pkg) in listOf(appA,appB,appA).withIndex()) {
                observer.latch=CountDownLatch(1)
                val before=observer.frames.size
                val launched=shell("am start -W -n "+pkg+"/"+pkg+".EditorActivity")
                assertFalse(launched,launched.contains("Error:"))
                assertTrue("Missing OnPreDraw for real "+pkg+" round "+round+" step "+step,
                    observer.latch.await(12,TimeUnit.SECONDS))
                assertEquals(before+1,observer.frames.size)
                val frame=observer.frames.last()
                assertEquals(pkg,frame.pkg)
                assertEquals(SeedOrigin.BY_APP,frame.origin)
                assertEquals(frame.expected,frame.tint)
                assertNotNull("Rasterized drawable missing",frame.raster)
                val file="r252c-"+round+"-"+step+".png"
                shell("screencap -p /sdcard/Download/"+file)
                android.util.Log.i("R252C_COMPOSITOR",
                    "file="+file+" capturedAfterPreDraw="+frame.predrawMs+" firstPresented=NOT_PROVEN")
            }
            assertEquals(9,observer.frames.size)
            android.util.Log.i("R252C_TOTAL",
                "frames="+observer.frames.size+" setInputView="+observer.installs+
                " hideWindow="+observer.hides+" showWindow="+observer.shows)
        } finally {
            LatinIME.r252bObserveForTests(null)
            lifecycle?.close()
            if(previous!=null) shell("ime set "+previous)
            host?.let { onMain { it.finish() } }
            val e=prefs.edit()
            if(oldDay==null)e.remove(Settings.PREF_THEME_COLORS) else e.putString(Settings.PREF_THEME_COLORS,oldDay)
            if(oldNight==null)e.remove(Settings.PREF_THEME_COLORS_NIGHT) else e.putString(Settings.PREF_THEME_COLORS_NIGHT,oldNight)
            e.commit()
        }
    }
}
