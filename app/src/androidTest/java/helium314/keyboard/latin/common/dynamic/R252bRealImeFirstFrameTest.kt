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
import android.view.ViewGroup
import android.content.res.Configuration
import helium314.keyboard.latin.IosGlassController
import helium314.keyboard.latin.common.dynamic.MonetKeyboardColors
import android.widget.ImageView
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
        val pkg:String, val expected:Int, val expectedSurface:Int, val loaded:Int, val pixel:Int?,
        val epoch:Long, val viewIdentity:Int, val first:Boolean
    )
    private data class SurfaceRow(
        val app:String, val name:String, val visible:Boolean, val observed:Int?,
        val expected:Int?, val tint:Int?, val status:String
    )
    private class Observer:LatinIME.R252bHarnessObserver {
        val surfaceRows=CopyOnWriteArrayList<SurfaceRow>()
        private fun rgb(a:Int,b:Int)=(a and 0x00ffffff)==(b and 0x00ffffff)
        private fun sample(v:View):Int? {
            val bg=v.background ?: return null
            val old=android.graphics.Rect(bg.bounds)
            val bm=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            return try {
                bg.setBounds(0,0,8,8);bg.draw(Canvas(bm));bm.getPixel(4,4)
            } finally {bg.bounds=old;bm.recycle()}
        }
        private fun scan(root:View,app:String,p:PaletteIdentity,frost:Int) {
            val map=MonetColorMapper.map(DynamicSchemeProvider.get(
                p.argb,DynamicSchemeType.valueOf(p.family),p.dark),p.themeStyle,p.borders)
            fun visit(v:View) {
                val name=if(v.id==View.NO_ID) v.javaClass.simpleName
                    else runCatching {v.resources.getResourceEntryName(v.id)}
                        .getOrElse {v.javaClass.simpleName}
                val col=sample(v)
                val tint=(v as? ImageView)?.imageTintList?.defaultColor
                val expected=when(v.id) {
                    R.id.main_keyboard_frame->frost
                    R.id.suggestion_strip_view->map.getValue(ColorType.STRIP_BACKGROUND)
                    else->null
                }
                val status=when {
                    !v.isShown -> "NOT_DRAWN"
                    v.id==R.id.backgroundGatheringIndicator -> "FIXED_STATUS_INDICATOR"
                    col==null && tint==null -> "NO_BG_OR_TINT"
                    expected!=null && col!=null && rgb(expected,col) -> "PALETTE_MATCH"
                    col!=null && col==Color.TRANSPARENT -> "TRANSPARENT"
                    col!=null && map.values.any{rgb(it,col)} -> "PALETTE_ROLE_MATCH"
                    tint!=null && map.values.any{rgb(it,tint)} -> "PALETTE_TINT_MATCH"
                    else -> "UNCLASSIFIED_COLOR"
                }
                surfaceRows.add(SurfaceRow(app,name,v.isShown,col,expected,tint,status))
                android.util.Log.i("R252C_SURFACE",
                    "app=$app view=$name shown=${v.isShown} observed=$col expected=$expected tint=$tint status=$status")
                if(v is ViewGroup) for(i in 0 until v.childCount)visit(v.getChildAt(i))
            }
            visit(root)
        }

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
            val alpha=if(p.dark) 196 else 202
            val expectedSurface=Color.argb(alpha,Color.red(expected),
                Color.green(expected),Color.blue(expected))
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
                    val row=Frame(info.packageName ?: "(none)",expected,expectedSurface,loaded,sample,
                        binding.snapshot.epoch,System.identityHashCode(inputView),true)
                    frames.add(row)
                    scan(inputView,row.pkg,p,row.expectedSurface)
                    android.util.Log.i("R252B_FIRST_FRAME",
                        "pkg=${row.pkg} epoch=${row.epoch} expected=${row.expected} " +
                        "loaded=${row.loaded} surface=${row.expectedSurface} drawable=${row.pixel} view=${row.viewIdentity}")
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
            assertEquals("${frame.pkg}: rendered frosted background mismatches actual palette tint/alpha",
                frame.expectedSurface,frame.pixel)
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
    /** A/B OFF must preserve the exact frost tint from a686f7d, before R2.5.2b. */
    @Test fun r252cByAppOffMatchesOriginalFrostAndThemeRoles() = runFixture {ime,host,o->
        R252PaletteRegistry.clear()
        val reload=ime.javaClass.getDeclaredMethod("loadSettings").apply{isAccessible=true}
        main {reload.invoke(ime)}
        val colors=Settings.getValues().mColors
        assertFalse("OFF unexpectedly bound a By App session",
            (colors as? MonetKeyboardColors)?.isByAppPaletteBound()==true)
        val allRoles=ColorType.entries.associateWith{colors.get(it)}
        fun check(dark:Boolean,original:Int) {
            val config=Configuration(context.resources.configuration).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            val themed=context.createConfigurationContext(config)
            val root=View(themed);val panel=View(themed)
            main { IosGlassController.apply(root,panel) }
            fun sample(v:View):Int {
                val d=requireNotNull(v.background)
                val old=android.graphics.Rect(d.bounds)
                val bm=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
                return try {
                    d.setBounds(0,0,256,256);d.draw(Canvas(bm));bm.getPixel(128,128)
                } finally { d.bounds=old;bm.recycle() }
            }
            assertEquals("By App OFF frost deviated from pre-fix base",original,main{sample(panel)})
            assertEquals("Transparent keyboard root changed",Color.TRANSPARENT,main{sample(root)})
            assertTrue("Frost clip-to-outline changed",main{panel.clipToOutline})
            val corner=(panel.background as android.graphics.drawable.GradientDrawable).cornerRadii
            assertNotNull("Original 8 rounded-corner radii missing",corner)
            assertEquals(8,corner!!.size)
            android.util.Log.i("R252C_OFF_BASELINE",
                "mode=${if(dark) "dark" else "light"} expected=$original observed=${main{sample(panel)}} " +
                "clip=${main{panel.clipToOutline}} corners=${corner.joinToString()}")
        }
        check(false,Color.argb(202,242,242,247))
        check(true,Color.argb(196,31,31,33))
        val after=ColorType.entries.associateWith{Settings.getValues().mColors.get(it)}
        assertEquals("By App OFF changed ColorType role values",allRoles,after)
    }
    @Test fun r252cWholeInputViewSurfaceSweepOnFirstPreDraw() = runFixture {ime,host,o->
        val frames=listOf(transition(ime,host,o,"r252.alpha"),
            transition(ime,host,o,"r252.beta"),transition(ime,host,o,"r252.alpha"))
        assertEquals("Every transition must have a first frame",3,frames.size)
        for(f in frames) {
            val rows=o.surfaceRows.filter{it.app==f.pkg}
            for(name in listOf("main_keyboard_frame","suggestion_strip_view",
                "emoji_palettes_view","clipboard_history_view",
                "btn_stop_one_handed_mode","btn_switch_one_handed_mode")) {
                assertTrue("Missing surface $name on ${f.pkg}",rows.any{it.name==name})
            }
            assertTrue("No matching frost in first frame ${f.pkg}",rows.any{
                it.name=="main_keyboard_frame" && it.status=="PALETTE_MATCH"
            })
            val unmatched=rows.filter{it.status=="UNCLASSIFIED_COLOR"}
            assertTrue("Unexplained fixed colors $unmatched",unmatched.isEmpty())
        }
        android.util.Log.i("R252C_SURFACE_GATE",
            "transitions=3 totalNodes=${o.surfaceRows.size} " +
            "popup=NOT_ATTACHED_TO_INPUTVIEW hidden=NOT_DRAWN")
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
