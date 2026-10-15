// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared conservative gate. Absence of a probabilistic entity is never an allow signal. */
public final class SensitiveInputPolicy {
    public static final int MAX_SELECTION = 4096;
    private static final int NO_LEARNING = 0x01000000;
    private static final Pattern SECRET_LABEL = Pattern.compile(
            "\\b(?:clabe|iban|cuenta|account|tarjeta|card|cvv|cvc|otp|pin|token|"
            + "contrasena|password|passcode|credencial|credential|codigo|code|"
            + "referencia\\s+(?:de\\s+)?pago|payment\\s+reference)\\b");
    private static final Pattern NUMBER_RUN = Pattern.compile("(?:\\p{Nd}[\\p{Z}\\s.()/+-]*){4,}");

    private SensitiveInputPolicy() {}

    public static boolean protectedEditor(int type, int options, boolean incognito, boolean excluded) {
        if (incognito || excluded || (options & NO_LEARNING) != 0) return true;
        if ((type & 0x0f) != 1) return true; // Only ordinary text, never null/number/phone/date.
        int variation = type & 0xff0;
        return variation == 0x80 || variation == 0x90 || variation == 0xe0;
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
