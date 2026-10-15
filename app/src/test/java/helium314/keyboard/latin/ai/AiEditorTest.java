// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public class AiEditorTest {
    private Object snapshot(Object editor, int start, int end, String text, long revision) {
        try {
            return Class.forName("helium314.keyboard.latin.ai.EditorSnapshot")
                    .getConstructor(Object.class, String.class, int.class, long.class,
                            long.class, int.class, int.class, CharSequence.class)
                    .newInstance(editor, "example.editor", 12, 5L, revision, start, end, text);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Editor snapshot is missing", e); }
    }
    private boolean matches(Object first, Object second) {
        try { return (boolean) first.getClass().getMethod("matches", first.getClass()).invoke(first, second); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Test public void identicalTextAtDifferentRangesIsNotSameTarget() {
        Object editor = new Object();
        assertFalse(matches(snapshot(editor, 0, 4, "hola", 1), snapshot(editor, 8, 12, "hola", 1)));
    }
    @Test public void editorIdentityCannotBeReused() {
        assertFalse(matches(snapshot(new Object(), 0, 4, "hola", 1), snapshot(new Object(), 0, 4, "hola", 1)));
    }
    @Test public void whitespaceAndUnicodeAreComparedExactly() {
        Object editor = new Object(); String text = "  Hola 👋\n";
        assertTrue(matches(snapshot(editor, 3, 3 + text.length(), text, 1), snapshot(editor, 3, 3 + text.length(), text, 1)));
        assertFalse(matches(snapshot(editor, 0, 4, "hola", 1), snapshot(editor, 0, 4, " hola ", 1)));
    }
    @Test public void contentRevisionMustMatch() {
        Object editor = new Object();
        assertFalse(matches(snapshot(editor, 0, 4, "hola", 1), snapshot(editor, 0, 4, "hola", 2)));
    }
}
