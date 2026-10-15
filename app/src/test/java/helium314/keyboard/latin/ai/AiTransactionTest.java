// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiTransactionTest {
    private static final class Fixture {
        String value = "  hola 👋\n";
        int start = 0, end = value.length(); long revision = 1;
        boolean reject;
        final Object identity = new Object();
        EditorSnapshot capture() {
            return new EditorSnapshot(identity, "editor", 4, 3, revision, start, end, value.substring(start, end));
        }
        Object target(Class<?> type) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getName().equals("capture")) return capture();
                if (method.getName().equals("replace")) {
                    if (reject) return false;
                    int a = (int) args[0], b = (int) args[1];
                    String replacement = args[2].toString();
                    value = value.substring(0, a) + replacement + value.substring(b);
                    start = end = a + replacement.length(); revision++;
                    return true;
                }
                throw new AssertionError(method.getName());
            });
        }
    }
    private Object transaction() {
        try { return Class.forName("helium314.keyboard.latin.ai.AiEditTransaction").getConstructor().newInstance(); }
        catch (ReflectiveOperationException e) { throw new AssertionError("Edit transaction is missing", e); }
    }
    private boolean apply(Object tx, Fixture f, EditorSnapshot expected, String replacement) {
        try {
            Class<?> target = Class.forName("helium314.keyboard.latin.ai.AiEditTransaction$Target");
            return (boolean) tx.getClass().getMethod("apply", EditorSnapshot.class, CharSequence.class, target)
                    .invoke(tx, expected, replacement, f.target(target));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private boolean undo(Object tx, Fixture f) {
        try {
            Class<?> target = Class.forName("helium314.keyboard.latin.ai.AiEditTransaction$Target");
            return (boolean) tx.getClass().getMethod("undo", target).invoke(tx, f.target(target));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Test public void exactUnicodeWhitespaceUndo() {
        Fixture f = new Fixture(); String original = f.value; Object tx = transaction();
        assertTrue(apply(tx, f, f.capture(), "Buenos días 👋"));
        assertEquals("Buenos días 👋", f.value);
        assertTrue(undo(tx, f)); assertEquals(original, f.value);
        assertFalse(undo(tx, f));
    }
    @Test public void rejectedCommitDoesNotReportSuccessOrCreateUndo() {
        Fixture f = new Fixture(); String original = f.value; f.reject = true; Object tx = transaction();
        assertFalse(apply(tx, f, f.capture(), "changed"));
        assertEquals(original, f.value); assertFalse(undo(tx, f));
    }
    @Test public void revisionChangeRejectsReplaceAndUndo() {
        Fixture f = new Fixture(); Object tx = transaction(); EditorSnapshot old = f.capture();
        f.revision++; assertFalse(apply(tx, f, old, "changed"));
        assertTrue(apply(tx, f, f.capture(), "changed"));
        f.revision++; assertFalse(undo(tx, f));
    }
    @Test public void collapsedSelectionCannotBeRewritten() {
        Fixture f = new Fixture(); f.start = f.end;
        assertFalse(apply(transaction(), f, f.capture(), "changed"));
    }
}
