// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure JVM behavioral checks; no network, editor, key or user data required. */
public class AiCoreTest {
    private boolean permits(int type, int options, boolean incognito, boolean excluded, String text) {
        try {
            Class<?> policy = Class.forName("helium314.keyboard.latin.ai.SensitiveInputPolicy");
            return (boolean) policy.getMethod("permitsCloud", int.class, int.class,
                    boolean.class, boolean.class, CharSequence.class)
                    .invoke(null, type, options, incognito, excluded, text);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("The shared sensitive-input policy is not available", e);
        }
    }

    @Test public void ordinaryBilingualWritingAllowed() {
        assertTrue(permits(1, 0, false, false, "Hey! mañana tenemos meeting con Carlos"));
    }
    @Test public void userIncognitoNeverReachesCloud() {
        assertFalse(permits(1, 0, true, false, "Revisa esta propuesta"));
    }
    @Test public void editorNoLearningNeverReachesCloud() {
        assertFalse(permits(1, 0x01000000, false, false, "Revisa esta propuesta"));
    }
    @Test public void protectedEditorsNeverReachCloud() {
        for (int type : new int[]{0, 2, 3, 4, 0x81, 0x91, 0xe1, 0x12})
            assertFalse("inputType=" + type, permits(type, 0, false, false, "private"));
    }
    @Test public void excludedApplicationNeverReachesCloud() {
        assertFalse(permits(1, 0, false, true, "Revisa esta propuesta"));
    }
    @Test public void formattedBankingAndOtpNeverReachCloud() {
        for (String text : new String[]{"CLABE 032 180 000118359719", "CLABE 032-180-000118359719",
                "Mi tarjeta 4111 1111 1111 1111", "OTP: 724913", "código 9284",
                "Cuenta 123456789", "Referencia de pago AB123", "Contraseña secreta", "password: secret"})
            assertFalse(text, permits(1, 0, false, false, text));
    }
    @Test public void unicodeDigitsAreSensitiveToo() {
        assertFalse(permits(1, 0, false, false, "Código ９２８４"));
    }
    @Test public void missingAndOversizeSelectionNotSent() {
        assertFalse(permits(1, 0, false, false, null));
        assertFalse(permits(1, 0, false, false, ""));
        assertFalse(permits(1, 0, false, false, new String(new char[4097]).replace('\0', 'x')));
    }
}
