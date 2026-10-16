// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppSessionColorCoordinatorTest {
    private class Worker : Executor {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun first() = queue.removeFirst().run()
        fun last() = queue.removeLast().run()
    }
    private class Icons : AppIconLookup {
        var blocked = false
        override fun stamp(packageName: String): PackageStamp {
            if (blocked) throw SecurityException("no profile")
            return PackageStamp(1, 1)
        }
        override fun icon(packageName: String): Drawable = ColorDrawable(
            if (packageName == "app.a") Color.GREEN else Color.MAGENTA
        )
    }
    private data class Fixture(
        val worker: Worker, val provider: AppColorSeedProvider,
        val coordinator: AppSessionColorCoordinator
    )
    private fun fixture(blocked: Boolean = false): Fixture {
        val worker = Worker()
        val provider = AppColorSeedProvider(Icons().apply { this.blocked = blocked }, worker)
        val coordinator = AppSessionColorCoordinator(provider) { dark ->
            DynamicColorSeedProvider.Snapshot(
                if (dark) Color.DKGRAY else Color.LTGRAY,
                if (dark) 12L else 11L, false
            )
        }
        return Fixture(worker, provider, coordinator)
    }

    @Test fun offByDefaultAndDisableClearsPending() {
        val f = fixture()
        val original = f.coordinator.onEditorStarted("app.a", false)
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER, original.origin)
        assertEquals(0, f.worker.queue.size)
        f.coordinator.setEnabled(true)
        f.coordinator.onEditorStarted("app.a", false)
        f.coordinator.setEnabled(false)
        f.worker.first()
        assertEquals(0, f.provider.cacheSize)
        assertNull(f.coordinator.currentSnapshot())
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER,
            f.coordinator.onEditorStarted("app.a", false).origin)
    }

    @Test fun coldSessionIsFrozenAndNextSessionGetsAppSeed() {
        val f = fixture()
        f.coordinator.setEnabled(true)
        val cold = f.coordinator.onEditorStarted("app.a", false)
        f.worker.first()
        assertSame(cold, f.coordinator.currentSnapshot())
        assertEquals(Color.LTGRAY, cold.seed)
        val warm = f.coordinator.onEditorStarted("app.a", false)
        assertEquals(SeedOrigin.BY_APP, warm.origin)
        assertNotEquals(cold.seed, warm.seed)
        assertNotEquals(cold.fingerprint, warm.fingerprint)
    }

    @Test fun aToBToAReverseWorkersNeverModifyActiveB() {
        val f = fixture()
        f.coordinator.setEnabled(true)
        val a = f.coordinator.onEditorStarted("app.a", false)
        val b = f.coordinator.onEditorStarted("app.b", false)
        f.worker.last() // B then A
        assertSame(b, f.coordinator.currentSnapshot())
        f.worker.first()
        assertSame(b, f.coordinator.currentSnapshot())
        assertTrue(b.epoch > a.epoch)
        val nextA = f.coordinator.onEditorStarted("app.a", false)
        assertEquals(SeedOrigin.BY_APP, nextA.origin)
        assertNotEquals(b.seed, nextA.seed)
    }

    @Test fun staleFinishDoesNotFinishAnotherEditor() {
        val f = fixture()
        val a = f.coordinator.onEditorStarted("app.a", false)
        val b = f.coordinator.onEditorStarted("app.b", false)
        assertFalse(f.coordinator.onEditorFinished(a.epoch))
        assertSame(b, f.coordinator.currentSnapshot())
        assertTrue(f.coordinator.onEditorFinished(b.epoch))
        assertNull(f.coordinator.currentSnapshot())
    }

    @Test fun restartFreezesExistingSessionAndDarkModeChangesSnapshot() {
        val f = fixture()
        f.coordinator.setEnabled(true)
        val a = f.coordinator.onEditorStarted("app.a", false)
        f.worker.first()
        assertSame(a, f.coordinator.onEditorStarted("app.a", false, restarting = true))
        val dark = f.coordinator.onEditorStarted("app.a", true, restarting = true)
        assertTrue(dark.isDark)
        assertEquals(SeedOrigin.BY_APP, dark.origin)
        assertNotEquals(a.epoch, dark.epoch)
    }

    @Test fun missingPackageProfileFailureAndSeparateScopes() {
        val f = fixture(blocked = true)
        f.coordinator.setEnabled(true)
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER,
            f.coordinator.onEditorStarted(null, false).origin)
        assertEquals(0, f.worker.queue.size)
        val personal = f.coordinator.onEditorStarted("work.app", false, "personal")
        f.worker.first() // security failure; negative result is cached, not thrown
        val second = f.coordinator.onEditorStarted("work.app", false, "work")
        assertNotEquals(personal.identity, second.identity)
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER, second.origin)
        f.worker.first()
        assertEquals(SeedOrigin.SYSTEM_WALLPAPER,
            f.coordinator.onEditorStarted("work.app", false, "work").origin)
    }
}
