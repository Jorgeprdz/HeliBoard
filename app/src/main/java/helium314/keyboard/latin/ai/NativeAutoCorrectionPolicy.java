// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Preserve short grammatical words even while native dictionary validity is undecided. */
public final class NativeAutoCorrectionPolicy {
    private static final Set<String> KEEP = new HashSet<>(Arrays.asList(
        "no", "ni", "me", "te", "se", "le", "lo", "la", "mi", "mí", "tu", "tú",
        "su", "el", "él", "es", "en", "de", "un", "al", "si", "sí", "ya", "os", "ha", "he", "yo"));
    private NativeAutoCorrectionPolicy() {}

    public static SuggestedWords protect(SuggestedWords words) {
        if (!words.mWillAutoCorrect || words.isPrediction() || words.mTypedWordInfo == null
                || words.size() <= SuggestedWords.INDEX_OF_AUTO_CORRECTION) return words;
        SuggestedWordInfo candidate = words.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION);
        if (candidate.isKindOf(SuggestedWordInfo.KIND_SHORTCUT)) return words;
        String source = words.mTypedWordInfo.mWord, target = candidate.mWord;
        if (source == null || target == null || source.equalsIgnoreCase(target)) return words;
        String lower = source.toLowerCase(Locale.ROOT);
        boolean shortSubstitution = lower.matches("[\\p{L}\\p{M}]{1,2}")
                && target.matches("[\\p{L}\\p{M}]{1,2}");
        if (!KEEP.contains(lower) && !shortSubstitution) return words;
        ArrayList<SuggestedWordInfo> candidates = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) candidates.add(words.getInfo(i));
        return new SuggestedWords(candidates, words.mRawSuggestions, words.mTypedWordInfo,
            words.mTypedWordValid, false, words.mIsObsoleteSuggestions, words.mInputStyle, words.mSequenceNumber);
    }
}
