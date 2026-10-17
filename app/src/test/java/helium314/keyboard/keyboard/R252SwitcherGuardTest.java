// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard;

import android.content.Context;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.RichInputConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** R2.5.2 guards with a real KeyboardSwitcher entrypoint, without rendering an IME. */
@RunWith(RobolectricTestRunner.class)
public final class R252SwitcherGuardTest {
    @Test public void activeCompositionBlocksR252ViewRebuild() throws Exception {
        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        final java.lang.reflect.Field field = KeyboardSwitcher.class.getDeclaredField("mLatinIME");
        field.setAccessible(true);
        final Object previous = field.get(switcher);
        final LatinIME ime = mock(LatinIME.class);
        when(ime.hasAiComposition()).thenReturn(true);
        final int before = switcher.getR252PaletteViewReplacements();
        field.set(switcher, ime);
        try {
            assertThrows(IllegalStateException.class, () ->
                    switcher.updateKeyboardTheme(RuntimeEnvironment.getApplication(), true));
            assertEquals(before, switcher.getR252PaletteViewReplacements());
            verify(ime, never()).getAiInputConnection();
        } finally { field.set(switcher, previous); }
    }
    @Test public void textSelectionBlocksR252ViewRebuild() throws Exception {
        final KeyboardSwitcher switcher = KeyboardSwitcher.getInstance();
        final java.lang.reflect.Field field = KeyboardSwitcher.class.getDeclaredField("mLatinIME");
        field.setAccessible(true);
        final Object previous = field.get(switcher);
        final LatinIME ime = mock(LatinIME.class);
        final RichInputConnection input = mock(RichInputConnection.class);
        when(ime.hasAiComposition()).thenReturn(false);
        when(ime.getAiInputConnection()).thenReturn(input);
        when(input.hasSelection()).thenReturn(true);
        final int before = switcher.getR252PaletteViewReplacements();
        field.set(switcher, ime);
        try {
            assertThrows(IllegalStateException.class, () ->
                    switcher.updateKeyboardTheme(RuntimeEnvironment.getApplication(), true));
            assertEquals(before, switcher.getR252PaletteViewReplacements());
        } finally { field.set(switcher, previous); }
    }
}
