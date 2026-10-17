// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import static org.junit.Assert.*;

/** Host-runnable contract: no Android runtime or editor instance is needed. */
public class SensitiveInputPolicyTest {
    private static final int TEXT = InputType.TYPE_CLASS_TEXT;

    private static void blocked(int variation) {
        int type = TEXT | variation;
        assertTrue(SensitiveInputPolicy.protectedEditor(type, 0, false, false));
        assertFalse(SensitiveInputPolicy.permitsCloud(type, 0, false, false, "hello"));
        assertFalse(SensitiveInputPolicy.permitsLearning(type, 0, false, false, "hello", "world"));
    }

    private static void allowed(int variation) {
        int type = TEXT | variation;
        assertFalse(SensitiveInputPolicy.protectedEditor(type, 0, false, false));
        assertTrue(SensitiveInputPolicy.permitsCloud(type, 0, false, false, "hola mundo"));
        assertTrue(SensitiveInputPolicy.permitsLearning(type, 0, false, false, "hola", "mundo"));
    }

    @Test public void sixHistoricFailuresStayFixed() {
        for (int variation : new int[]{InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS}) {
            blocked(variation);
        }
    }

    @Test public void passwordsAndSensitiveVariationsFailClosed() {
        for (int variation : new int[]{InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_FILTER, InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS, InputType.TYPE_TEXT_VARIATION_PHONETIC,
                0x000000f0}) {
            blocked(variation);
        }
    }

    @Test public void ordinaryWritingVariationsRemainAllowed() {
        for (int variation : new int[]{InputType.TYPE_TEXT_VARIATION_NORMAL,
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT}) {
            allowed(variation);
        }
    }

    @Test public void modifiersDoNotBypassClassOrVariation() {
        int caps = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        assertFalse(SensitiveInputPolicy.protectedEditor(TEXT | caps, 0, false, false));
        assertTrue(SensitiveInputPolicy.protectedEditor(TEXT | caps | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0, false, false));
        for (int type : new int[]{InputType.TYPE_NULL, InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME}) {
            assertTrue(SensitiveInputPolicy.protectedEditor(type, 0, false, false));
        }
    }

    @Test public void explicitEditorPrivacyFlagsApplyEvenToOrdinaryText() {
        assertTrue(SensitiveInputPolicy.protectedEditor(TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false, false));
        assertTrue(SensitiveInputPolicy.protectedEditor(TEXT, 0, true, false));
        assertTrue(SensitiveInputPolicy.protectedEditor(TEXT, 0, false, true));
        assertTrue(SensitiveInputPolicy.protectedEditor(TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0, false, false));
        assertTrue(SensitiveInputPolicy.protectedEditor(TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE, 0, false, false));
    }

    @Test public void nullBlankAndSensitiveValuesFailClosed() {
        assertFalse(SensitiveInputPolicy.permitsCloud(TEXT, 0, false, false, null));
        assertFalse(SensitiveInputPolicy.permitsCloud(TEXT, 0, false, false, ""));
        assertFalse(SensitiveInputPolicy.permitsLearning(TEXT, 0, false, false, null, "hola"));
        assertFalse(SensitiveInputPolicy.permitsLearning(TEXT, 0, false, false, "hola", null));
        assertFalse(SensitiveInputPolicy.permitsCloud(TEXT, 0, false, false, "contraseña"));
        assertFalse(SensitiveInputPolicy.permitsLearning(TEXT, 0, false, false, "número 12345", "hola"));
        assertFalse(SensitiveInputPolicy.permitsCloud(TEXT, 0, false, false, "a".repeat(SensitiveInputPolicy.MAX_SELECTION+1)));
    }

    @Test public void normalInputStillSupportsLocalLearningAndExplicitCloudPolicy() {
        assertTrue(SensitiveInputPolicy.permitsLearning(TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0, false, false, "hola", "mundo"));
        assertTrue(SensitiveInputPolicy.permitsCloud(TEXT | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, 0, false, false, "hola mundo"));
        assertFalse(SensitiveInputPolicy.permitsCloud(TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                0, false, false, "hola mundo"));
    }
}
