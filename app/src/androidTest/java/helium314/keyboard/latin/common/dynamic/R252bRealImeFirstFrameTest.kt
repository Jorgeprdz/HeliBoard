// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.BaseInputConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R252bHostActivity
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor
import java.util.concurrent.CopyOnWriteArrayList

/**
 * R2.5.2b: probes actual, attached LatinIME InputView and real EditText InputConnection.
 * The EditorInfo owner is synthetic for cross-package restarting=true; the bound host
 * and InputConnection are real. Never equate this with physical distinct app UIDs.
 */
@RunWith(AndroidJUnit4::class)
class R252bRealImeFirstFrameTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext

    private class ImmediateWorker: Executor {
        override fun execute(command:Runnable) { command.run() }
    }
    private class Icons: AppIconLookup {
        override fun stamp(packageName:String)=PackageStamp(5,5)
        override fun icon(packageName:String):Drawable = ColorDrawable(
            if(packageName=="r252.alpha") Color.CYAN else Color.MAGENTA)
    }
    private data class Frame(
        val pkg:String, val expected:Int, val loaded:Int, val pixel:Int?,
        val epoch:Long, val viewIdentity:Int, val first:Boolean
    )
    private class Observer:LatinIME.R252bHarnessObserver {
        @Volatile var installed=0
        @Volatile var hides=0
        @Volatile var shows=0
        @Volatile var frameLatch=CountDownLatch(1)
        val frames=CopyOnWriteArrayList<Frame>()
        override fun onInputViewInstalled(inputView:View) { installed++ }
        override fun onWindowHide(){ hides++ }
        override fun onWindowShow(){ shows++ }
        override fun onInputBoundary(inputView:View?,info:EditorInfo,restarting:Boolean) {
            requireNotNull(inputView) { "LatinIME returned null attached InputView" }
            val binding=requireNotNull(R252PaletteRegistry.current()) { "No session palette at IME boundary" }
            val p=binding.palette
            val expected=MonetColorMapper.map(DynamicSchemeProvider.get(p.argb,
                DynamicSchemeType.valueOf(p.family), p.dark),p.themeStyle,p.borders)
                .getValue(ColorType.MAIN_BACKGROUND)
            val target=inputView.findViewById<View>(R.id.main_keyboard_frame)
            val observer=target.viewTreeObserver
            val listener=object:ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw():Boolean {
                    if(observer.isAlive) observer.removeOnPreDrawListener(this)
                    val loaded=Settings.getValues().mColors.get(ColorType.MAIN_BACKGROUND)
                    val sample=target.background?.let { bg ->
                        val old=android.graphics.Rect(bg.bounds)
                        val bm=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
                        try {
                            bg.setBounds(0,0,8,8)
                            bg.draw(Canvas(bm))
                            bm.getPixel(4,4)
                        } finally {
                            bg.bounds=old
                            bm.recycle()
                        }
                    }
                    val row=Frame(info.packageName ?: "(none)",expected,loaded,sample,
                        binding.snapshot.epoch,System.identityHashCode(inputView),true)
                    frames.add(row)
                    android.util.Log.i("R252B_FIRST_FRAME",
                        "pkg=${row.pkg} epoch=${row.epoch} expected=${row.expected} " +
                        "loaded=${row.loaded} drawable=${row.pixel} view=${row.viewIdentity}")
                    frameLatch.countDown()
                    return true
                }
            }
            observer.addOnPreDrawListener(listener)
            target.invalidate()
        }
    }

    private fun shell(command:String) {
        // Shell command is asynchronous; drain its output before changing focus/IME.
        val fd:ParcelFileDescriptor=inst.uiAutomation.executeShellCommand(command)
        try { java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
        finally { fd.close() }
    }
    private fun <T> main(block:()->T):T {
        var v:T?=null
        var error:Throwable?=null
        inst.runOnMainSync { try {v=block()}catch(e:Throwable){error=e} }
        error?.let {throw AssertionError("IME main thread failed",it)}
        @Suppress("UNCHECKED_CAST") return v as T
    }
    private fun waitIme():LatinIME {
        repeat(60) {
            val ime=LatinIME.r252bRunningInstanceForTests()
            if(ime!=null && main { ime.isInputViewShown }) return ime
            SystemClock.sleep(150)
        }
        throw AssertionError("Real LatinIME did not bind and show an attached InputView")
    }
    private fun runFixture(test:(LatinIME,R252bHostActivity,Observer)->Unit) {
        val prefs=context.prefs()
        val oldDay=prefs.getString(Settings.PREF_THEME_COLORS,null)
        val oldNight=prefs.getString(Settings.PREF_THEME_COLORS_NIGHT,null)
        val originalIme=android.provider.Settings.Secure.getString(
            context.contentResolver,android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
        val component="${context.packageName}/helium314.keyboard.latin.LatinIME"
        var activity:R252bHostActivity?=null
        var life:R252ColorLifecycle?=null
        val o=Observer()
        try {
            assertNotNull("Cannot restore IME without a starting component",originalIme)
            prefs.edit().putString(Settings.PREF_THEME_COLORS,KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT)
                .putString(Settings.PREF_THEME_COLORS_NIGHT,KeyboardTheme.THEME_DYNAMIC_TONAL_SPOT).commit()
            shell("ime enable $component")
            shell("ime set $component")
            val intent=Intent().setClassName(context.packageName,
                "helium314.keyboard.latin.R252bHostActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity=inst.startActivitySync(intent) as R252bHostActivity
            val host=requireNotNull(activity)
            main{host.showImeForTest()}
            val ime=waitIme()
            val seeds=AppColorSeedProvider(Icons(),ImmediateWorker())
            val coordinator=AppSessionColorCoordinator(seeds) {dark->
                DynamicColorSeedProvider.Snapshot(if(dark)Color.DKGRAY else Color.LTGRAY,12L,false)
            }
            life=R252ColorLifecycle(coordinator)
            life.enableForTests(true)
            // Warm deterministic app seeds outside a key event; no fake UI callback.
            seeds.warmAsync(AppIdentity("r252.alpha"))
            seeds.warmAsync(AppIdentity("r252.beta"))
            main { ime.r252bInstallSessionForTests(requireNotNull(life)) }
            LatinIME.r252bObserveForTests(o)
            test(ime,host,o)
        } finally {
            LatinIME.r252bObserveForTests(null)
            life?.close()
            if(originalIme!=null) shell("ime set $originalIme")
            activity?.let{main{it.finish()}}
            val e=prefs.edit()
            if(oldDay==null)e.remove(Settings.PREF_THEME_COLORS)
            else e.putString(Settings.PREF_THEME_COLORS,oldDay)
            if(oldNight==null)e.remove(Settings.PREF_THEME_COLORS_NIGHT)
            else e.putString(Settings.PREF_THEME_COLORS_NIGHT,oldNight)
            e.commit()
        }
    }
    private fun transition(ime:LatinIME,host:R252bHostActivity,o:Observer,pkg:String,
                           restarting:Boolean=true):Frame {
        val info=main {
            EditorInfo().apply {
                packageName=pkg
                inputType=InputType.TYPE_CLASS_TEXT
                imeOptions=EditorInfo.IME_ACTION_DONE
                fieldId=1
                initialSelStart=host.editorForTest.selectionStart
                initialSelEnd=host.editorForTest.selectionEnd
            }
        }
        o.frameLatch=CountDownLatch(1)
        main {
            // Exercise the actual finish/start service sequence, including same-inputType
            // restarting=true cross-package transitions. Never mutate InputLogic directly.
            if(o.frames.isNotEmpty()) {
                ime.onFinishInputView(true)
                ime.onFinishInput()
            }
            ime.onStartInput(info,restarting)
            ime.onStartInputView(info,restarting)
        }
        assertTrue("$pkg had no real OnPreDraw in seven seconds",
            o.frameLatch.await(7,TimeUnit.SECONDS))
        return o.frames.last()
    }
    @Test fun aToBToAFirstVisibleFrameUsesEffectiveDrawable() = runFixture {ime,host,o->
        val a=transition(ime,host,o,"r252.alpha")
        val b=transition(ime,host,o,"r252.beta")
        val a2=transition(ime,host,o,"r252.alpha")
        assertNotEquals(a.expected,b.expected)
        for (frame in listOf(a,b,a2)) {
            assertEquals("${frame.pkg}: stale SettingsValues colors at first predraw",frame.expected,frame.loaded)
            assertNotNull("${frame.pkg}: keyboard frame has no drawable",frame.pixel)
            assertEquals("${frame.pkg}: rendered background mismatches expected",frame.expected,frame.pixel)
        }
        assertEquals(a.expected,a2.expected)
        android.util.Log.i("R252B_SEQUENCE",
            "firstFrames=3 inputView=${o.installed} hides=${o.hides} shows=${o.shows}")
        assertEquals("Theme transitions must not call hideWindow",0,o.hides)
    }
    @Test fun samePaletteRestartDoesNotRecreateInputView() = runFixture {ime,host,o->
        val a=transition(ime,host,o,"r252.alpha")
        val oldCount=o.installed
        val again=transition(ime,host,o,"r252.alpha")
        assertEquals(a.expected,again.expected)
        assertEquals("PaletteIdentity equal must not recreate InputView",oldCount,o.installed)
    }
    @Test fun realComposingSpanAndSelectedTextRemainIntact() = runFixture {ime,host,o->
        transition(ime,host,o,"r252.alpha")
        val before=main {
            val ic=host.editorForTest.onCreateInputConnection(EditorInfo())
            assertTrue(ic.setComposingText("abc",1))
            val ed=host.editorForTest.text
            assertTrue(BaseInputConnection.getComposingSpanStart(ed)>=0)
            ed.toString()
        }
        transition(ime,host,o,"r252.beta")
        transition(ime,host,o,"r252.alpha")
        val after=main { host.editorForTest.text.toString() }
        assertEquals("Real InputConnection editor text changed across transitions",before,after)
        assertTrue("Active composing span was destroyed during A->B->A",
            main { BaseInputConnection.getComposingSpanStart(host.editorForTest.text)>=0 })
        main {
            val e=host.editorForTest
            e.setSelection(0,3)
        }
        val selected=transition(ime,host,o,"r252.beta")
        assertEquals(before,main{host.editorForTest.text.toString()})
        assertTrue("Selection was not preserved",main {
            host.editorForTest.selectionStart==0 && host.editorForTest.selectionEnd==3
        })
        val orientation=main { host.requestedOrientation }
        try {
            main { host.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            repeat(35) {
                if(main { host.resources.configuration.orientation } ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE) return@repeat
                SystemClock.sleep(100)
            }
            assertEquals("Landscape rotation not applied",
                android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                main { host.resources.configuration.orientation })
            val rotated=transition(ime,host,o,"r252.alpha")
            assertEquals("Composed editor text changed on rotation",before,
                main { host.editorForTest.text.toString() })
            assertEquals(rotated.expected,rotated.loaded)
        } finally { main { host.requestedOrientation=orientation } }
        android.util.Log.i("R252B_COMPOSITION",
            "textRetained=true composingSpanRetained=true selection=true rotation=true firstFrame=${selected.first}")
    }
}
