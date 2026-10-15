// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiStripTest {
    private Object arbiter() {
        try { return Class.forName("helium314.keyboard.latin.ai.SuggestionStripArbiter").getConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { throw new AssertionError("Strip arbiter missing", e); }
    }
    private Object invoke(Object o, String name, Class<?> type, Object value) {
        try { return type == null ? o.getClass().getMethod(name).invoke(o)
                : o.getClass().getMethod(name, type).invoke(o, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Test public void predictionUpdatesCannotExitAi() {
        Object a = arbiter(); invoke(a,"enter",boolean.class,false);
        invoke(a,"rememberPredictions",Object.class,"latest");
        assertEquals(true,invoke(a,"active",null,null));
        assertEquals("latest",invoke(a,"latestPredictions",null,null));
        invoke(a,"exit",null,null); assertEquals(false,invoke(a,"active",null,null));
        assertEquals(false,invoke(a,"restoreToolbarVisible",null,null));
    }
    @Test public void repeatedChoicesDoNotOverwriteOriginalToolbarState() {
        Object a = arbiter(); invoke(a,"enter",boolean.class,true);
        invoke(a,"enter",boolean.class,false); invoke(a,"exit",null,null);
        assertEquals(true,invoke(a,"restoreToolbarVisible",null,null));
    }
    @Test public void onlyEnabledUncustomizedPinnedAccessIsReplaced() {
        try {
            Class<?> c = Class.forName("helium314.keyboard.latin.ai.SuggestionStripArbiter");
            java.lang.reflect.Method m = c.getMethod("usesAiAccess",boolean.class,boolean.class,boolean.class,boolean.class);
            assertEquals(true,m.invoke(null,true,true,false,false));
            assertEquals(false,m.invoke(null,false,true,false,false));
            assertEquals(false,m.invoke(null,true,false,false,false));
            assertEquals(false,m.invoke(null,true,true,true,false));
            assertEquals(false,m.invoke(null,true,true,false,true));
        } catch (ReflectiveOperationException e) { throw new AssertionError("Access policy missing",e); }
    }
}
