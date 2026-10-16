// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared conservative gate. Absence of a probabilistic entity is never an allow signal. */
public final class SensitiveInputPolicy {
    public static final int MAX_SELECTION = 4096;
    // Android InputType / EditorInfo constants, inlined to preserve host-only javac tests.
    // InputType.TYPE_MASK_CLASS / TYPE_CLASS_TEXT / TYPE_MASK_VARIATION.
    private static final int CLASS_MASK = 0x0000000f;
    private static final int TEXT_CLASS = 0x00000001;
    private static final int VARIATION_MASK = 0x00000ff0;
    // InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS / TYPE_TEXT_FLAG_AUTO_COMPLETE.
    private static final int NO_SUGGESTIONS = 0x00080000;
    private static final int AUTO_COMPLETE = 0x00010000;
    // EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.
    private static final int NO_LEARNING = 0x01000000;
    private static final Pattern SECRET_LABEL = Pattern.compile(
            "\\b(?:clabe|iban|cuenta|account|tarjeta|card|cvv|cvc|otp|pin|token|"
            + "contrasena|password|passcode|credencial|credential|codigo|code|"
            + "referencia\\s+(?:de\\s+)?pago|payment\\s+reference)\\b");
    private static final Pattern NUMBER_RUN = Pattern.compile("(?:\\p{Nd}[\\p{Z}\\s.()/+-]*){4,}");

    private SensitiveInputPolicy() {}

    /**
     * Safety gate for optional AI, cloud processing and personalized learning.
     * This does not change LatinIME's conventional autocorrection behavior.
     * Unknown text variations fail closed; only ordinary writing fields are admitted.
     */
    public static boolean protectedEditor(int type, int options, boolean incognito, boolean excluded) {
        if (incognito || excluded || (options & NO_LEARNING) != 0) {
            return true;
        }
        if ((type & CLASS_MASK) != TEXT_CLASS) return true;
        if ((type & (NO_SUGGESTIONS
                | AUTO_COMPLETE)) != 0) return true;
        switch (type & VARIATION_MASK) {
            case 0x00000000: // TYPE_TEXT_VARIATION_NORMAL
            case 0x00000040: // TYPE_TEXT_VARIATION_SHORT_MESSAGE
            case 0x00000050: // TYPE_TEXT_VARIATION_LONG_MESSAGE
            case 0x00000030: // TYPE_TEXT_VARIATION_EMAIL_SUBJECT
            case 0x000000a0: // TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                return false;
            // URI, e-mail addresses, passwords, names, postal addresses, filter,
            // phonetic fields and future/unknown variations are protected by default.
            default:
                return true;
        }
    }

    public static boolean permitsLearning(int type,int options,boolean incognito,boolean excluded,
                                          CharSequence context,CharSequence word) {
        return !protectedEditor(type,options,incognito,excluded)&&!sensitiveText(context)&&!sensitiveText(word);
    }

    /** Also used for local learning and clipboard retention, independently of cloud opt-in. */
    public static boolean sensitiveText(CharSequence value) {
        if (value == null || value.length() > MAX_SELECTION) return true;
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC);
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return SECRET_LABEL.matcher(text).find() || NUMBER_RUN.matcher(text).find();
    }

    public static boolean permitsCloud(int type, int options, boolean incognito,
                                       boolean excluded, CharSequence value) {
        return !protectedEditor(type, options, incognito, excluded)
                && value != null && value.length() > 0 && !sensitiveText(value);
    }
}
