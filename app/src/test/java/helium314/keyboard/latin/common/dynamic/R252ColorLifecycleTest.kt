// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class R252ColorLifecycleTest {
    private class QueueWorker : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun first() = queue.removeFirst().run()
        fun last() = queue.removeLast().run()
    }
    private class Lookup : AppIconLookup {
        override fun stamp(packageName: String) = PackageStamp(1, 1)
        override fun icon(packageName: String): Drawable = ColorDrawable(
            if (packageName=="app.a") Color.GREEN else Color.MAGENTA
        )
    }
    private fun fixture(): Pair<R252ColorLifecycle, QueueWorker> {
        val worker=QueueWorker()
        val provider=AppColorSeedProvider(Lookup(),worker)
        val coordinator=AppSessionColorCoordinator(provider){ dark ->
            DynamicColorSeedProvider.Snapshot(if(dark) Color.DKGRAY else Color.LTGRAY,11L,false)
        }
        return R252ColorLifecycle(coordinator) to worker
    }
    private fun R252ColorLifecycle.start(app:String?, restart:Boolean=false, field:Int=1, dark:Boolean=false) =
        onEditorStart(app,field,1,0,0,restart,dark)

    @Test fun offByDefaultDoesNotWarmOrReadAnyPackage() {
        val (bridge,worker)=fixture()
        bridge.start("app.a")
        assertNull(bridge.snapshotForTests())
        assertTrue(worker.queue.isEmpty())
    }
    @Test fun coldWarmAndRapidAtoBtoARetainCorrectHost() {
        val (bridge,worker)=fixture();bridge.enableForTests(true)
        bridge.start("app.a");val cold=bridge.snapshotForTests()!!
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER,cold.origin)
        bridge.start("app.b");val b=bridge.snapshotForTests()!!
        worker.last(); worker.first()
        assertSame(b,bridge.snapshotForTests())
        bridge.start("app.a");val a=bridge.snapshotForTests()!!
        assertEquals(SeedOrigin.BY_APP,a.origin)
        assertEquals("app.a",a.identity?.packageName)
        assertNotEquals(b.seed,a.seed)
    }
    @Test fun restartingDifferentAppWithSameInputTypeCreatesNewSession() {
        val (bridge,unused)=fixture();bridge.enableForTests(true)
        bridge.start("app.a");val a=bridge.snapshotForTests()!!
        bridge.start("app.b",restart=true);val b=bridge.snapshotForTests()!!
        assertTrue(b.epoch>a.epoch)
        assertEquals("app.b",b.identity?.packageName)
    }
    @Test fun restartSamePackageRetainsSnapshotButDifferentFieldIsRepresented() {
        val (bridge,unused)=fixture();bridge.enableForTests(true)
        bridge.start("app.a");val old=bridge.snapshotForTests()!!
        bridge.start("app.a",restart=true,field=2)
        assertSame(old,bridge.snapshotForTests())
        assertEquals(2,bridge.sessionForTests()!!.fieldId)
    }
    @Test fun twoSamePackageWindowsWithNewConnectionCannotReuseOldSessionEpoch() {
        val (bridge,_) = fixture(); bridge.enableForTests(true)
        bridge.onEditorStart("same.app",1,1,0,null,false,false,101)
        val windowA=bridge.snapshotForTests()!!
        bridge.onEditorStart("same.app",1,1,0,null,true,false,202)
        val windowB=bridge.snapshotForTests()!!
        assertTrue(windowB.epoch>windowA.epoch, "New connection must advance epoch despite restarting")
        assertNotEquals(bridge.sessionForTests()!!.editorConnectionId,101)
        assertEquals(202,bridge.sessionForTests()!!.editorConnectionId)
        bridge.onEditorStart("same.app",1,1,0,null,true,false,202)
        assertSame(windowB,bridge.snapshotForTests(),"Repeat of same connection should reuse snapshot")
    }
    @Test fun darkModeAndUnknownHostAndDisableRemainSafe() {
        val (bridge,worker)=fixture();bridge.enableForTests(true)
        bridge.start(null)
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER,bridge.snapshotForTests()!!.origin)
        assertTrue(worker.queue.isEmpty())
        bridge.start("app.a",dark=true)
        assertTrue(bridge.snapshotForTests()!!.isDark)
        bridge.close()
        assertNull(bridge.snapshotForTests())
        bridge.start("app.b")
        assertNull(bridge.snapshotForTests())
    }
}
