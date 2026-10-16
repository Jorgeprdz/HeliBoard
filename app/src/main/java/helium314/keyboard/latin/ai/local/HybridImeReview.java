// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import java.util.*;

/**
 * Worker-only adapter of experimental hybrid proposals to existing IME context suggestions.
 * Input vocabulary contains observed native suggestions, not a guessed global dictionary.
 * Never applies an edit and never changes native suggestion ordering/auto-correction.
 */
public final class HybridImeReview {
    private HybridImeReview() {}

    public static List<LocalContextReviewer.Correction> propose(
            String context, Collection<String> observedNativeWords,
            LocalContextReviewer.Lexicon dictionary, Set<String> rejected,
            LocalContextReviewer.Cancellation cancelled) {
        if (context == null || context.isEmpty() || context.length() > 256
                || observedNativeWords == null || observedNativeWords.isEmpty()
                || observedNativeWords.size() > 256 || dictionary == null
                || cancelled == null || cancelled.cancelled()
                || SensitiveInputPolicy.sensitiveText(context)) return Collections.emptyList();
        try {
            final ArrayList<String> vocabulary = new ArrayList<>();
            final HashSet<String> unique = new HashSet<>();
            for (String word : observedNativeWords) {
                if (cancelled.cancelled()) return Collections.emptyList();
                if (word == null || !word.matches("[\\p{Ll}\\p{M}]{2,32}")
                        || !unique.add(word) || dictionary.preserved(word)
                        || !dictionary.valid(word)) continue;
                vocabulary.add(word);
            }
            if (vocabulary.isEmpty()) return Collections.emptyList();
            final HashSet<String> veto = new HashSet<>();
            if (rejected != null) for (String item : rejected) {
                if (item != null) veto.add(item.toLowerCase(Locale.ROOT));
            }
            final HybridCandidateGenerator.Lexicon lex = new HybridCandidateGenerator.Lexicon() {
                public Collection<String> vocabulary() { return vocabulary; }
                public boolean valid(String word) { return dictionary.valid(word); }
                public boolean knownInvalid(String word) { return dictionary.knownInvalid(word); }
                public boolean preserved(String word) { return dictionary.preserved(word); }
                public List<String> candidates(String word) { return dictionary.candidates(word); }
                public double prior(String source, String candidate) { return dictionary.prior(source,candidate); }
                public double keyDistance(char a, char b) { return dictionary.keyDistance(a,b); }
                public double personalPreference(String source,String candidate) {
                    return dictionary.personalPreference(source,candidate);
                }
                public boolean rejected(String source,String candidate) {
                    return dictionary.rejected(source,candidate);
                }
            };
            final ArrayList<LocalContextReviewer.Correction> out = new ArrayList<>();
            for (HybridCandidateGenerator.Edit edit :
                    new HybridCandidateGenerator(lex).generate(context,cancelled)) {
                if (cancelled.cancelled()) return Collections.emptyList();
                if (edit.automatic || edit.start < 0 || edit.end > context.length()
                        || edit.start >= edit.end) continue;
                final String original = context.substring(edit.start,edit.end);
                final String target = edit.replacement;
                if (!original.matches("[\\p{Ll}\\p{M}]+(?:[ \\t]+[\\p{Ll}\\p{M}]+){0,2}")
                        || target == null || !target.matches("[\\p{Ll}\\p{M}]+(?: [\\p{Ll}\\p{M}]+){0,2}")
                        || original.equals(target)) continue;
                // Every source token must be confirmed invalid or valid; UNKNOWN is never a typo.
                boolean allValid = true, provenTypo = false, unknown = false;
                for (String token : original.split("[ \\t]+")) {
                    if (dictionary.preserved(token)) { unknown = true; break; }
                    boolean valid = dictionary.valid(token);
                    allValid &= valid;
                    if (!valid) {
                        if (dictionary.knownInvalid(token)) provenTypo = true;
                        else unknown = true;
                    }
                }
                if (unknown || allValid || !provenTypo) continue;
                if (veto.contains(original.toLowerCase(Locale.ROOT))
                        || veto.contains(target.toLowerCase(Locale.ROOT))
                        || veto.contains((original+"→"+target).toLowerCase(Locale.ROOT))
                        || dictionary.rejected(original,target)) continue;
                out.add(new LocalContextReviewer.Correction(edit.start, edit.end,
                        original, target, "hybrid-native-manual", 0, false));
                if (out.size() == 3) break;
            }
            return Collections.unmodifiableList(out);
        } catch (RuntimeException dictionariesUnavailable) {
            return Collections.emptyList();
        }
    }
}
