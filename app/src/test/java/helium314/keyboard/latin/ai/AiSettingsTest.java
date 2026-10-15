// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AiSettingsTest {
    @Test public void disabledUnlessExplicitlyEnabled() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("keyboard_ai_private",0).edit().clear().commit();
        try {
            assertFalse((boolean) AiSettingsActivity.class.getMethod("isEnabled",Context.class).invoke(null,context));
            assertFalse((boolean) AiSettingsActivity.class.getMethod("networkAllowed",Context.class).invoke(null,context));
        } catch (ReflectiveOperationException e) { throw new AssertionError("Opt-in gates missing",e); }
    }
    @Test @Config(sdk=23) public void legacyVersionGuardDoesNotAttemptKeystore23() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("keyboard_ai_private",0).edit().putString("key","opaque").commit();
        // Robolectric 4.16 has no API21 runtime. Exercise the version guard on API23;
        // this does not claim API21 framework/device compatibility.
        int original = android.os.Build.VERSION.SDK_INT;
        try {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class,"SDK_INT",21);
            assertEquals("",AiSettingsActivity.readKey(context));
        } finally { org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class,"SDK_INT",original); }
    }
    @Test public void excludesOnlyExplicitApplicationNames() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("keyboard_ai_private",0).edit().putString("excluded_apps","bank.app,secure.editor").commit();
        try {
            assertTrue((boolean)AiSettingsActivity.class.getMethod("excludedApp",Context.class,String.class).invoke(null,context,"bank.app"));
            assertFalse((boolean)AiSettingsActivity.class.getMethod("excludedApp",Context.class,String.class).invoke(null,context,"example.editor"));
        } catch (ReflectiveOperationException e) { throw new AssertionError("Exclusion gate missing",e); }
    }
    @Test public void clearLearnedDataRemovesHistoryAndContextualRejections() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("keyboard_ai_private",0).edit()
            .putStringSet("rejected_corrections",java.util.Collections.singleton("haber→a ver")).commit();
        File learned=new File(context.getFilesDir(),"UserHistoryDictionary.test");
        assertTrue(learned.createNewFile()||learned.isFile());
        AiSettingsActivity.clearLearnedData(context);
        assertTrue(AiSettingsActivity.rejectedCorrections(context).isEmpty());
        assertFalse(learned.exists());
    }
}
