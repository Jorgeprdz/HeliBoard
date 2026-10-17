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
                || observedNativeWords == null
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
            // The old prototype could only find words seen in a recent suggestion strip.
            // Probe a strict, small set of keyboard-local edit neighbors against the
            // *already configured* native dictionaries, without storing a new word list.
            recoverNativeCandidates(context,dictionary,cancelled,unique,vocabulary);
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

    private static final String[] KEYS = {"qwertyuiop", "asdfghjklñ", "zxcvbnm"};
    private static final java.util.regex.Pattern WORD =
            java.util.regex.Pattern.compile("[\\p{L}\\p{M}]{2,24}");

    /** Maximum 96 dictionary probes per context and 16 recovered words; worker only. */
    private static void recoverNativeCandidates(String context, LocalContextReviewer.Lexicon lexicon,
            LocalContextReviewer.Cancellation cancel, Set<String> seen, List<String> out) {
        final int[] probes={0}, recovered={0};
        final HashSet<String> examined=new HashSet<>();
        java.util.regex.Matcher matcher=WORD.matcher(context);
        int inspected=0;
        while(matcher.find() && inspected++<5 && probes[0]<96 && recovered[0]<16) {
            if(cancel.cancelled())return;
            String source=matcher.group();
            if(!source.equals(source.toLowerCase(Locale.ROOT))
                    ||lexicon.preserved(source)||lexicon.valid(source)
                    ||!lexicon.knownInvalid(source))continue;
            final char[] letters=source.toCharArray();
            // Transposition and a missing/duplicated boundary are cheaper to test than
            // a blind all-dictionary search. All hits remain MANUAL suggestions.
            for(int i=0;i+1<letters.length&&probes[0]<96;i++){
                char[] candidate=letters.clone();char v=candidate[i];
                candidate[i]=candidate[i+1];candidate[i+1]=v;
                probe(new String(candidate),lexicon,cancel,seen,examined,out,probes,recovered);
            }
            for(int i=0;i<letters.length&&probes[0]<96;i++){
                String candidate=source.substring(0,i)+source.substring(i+1);
                probe(candidate,lexicon,cancel,seen,examined,out,probes,recovered);
            }
            for(int i=0;i<letters.length&&probes[0]<96;i++){
                final char original=letters[i];
                for(String row:KEYS){
                    int x=row.indexOf(original);
                    if(x<0)continue;
                    for(int delta=-2;delta<=2&&probes[0]<96;delta++){
                        if(delta==0||x+delta<0||x+delta>=row.length())continue;
                        char[] candidate=letters.clone();candidate[i]=row.charAt(x+delta);
                        probe(new String(candidate),lexicon,cancel,seen,examined,out,probes,recovered);
                    }
                }
            }
        }
    }

    private static void probe(String word,LocalContextReviewer.Lexicon lexicon,
            LocalContextReviewer.Cancellation cancel,Set<String> seen,Set<String> examined,
            List<String> out,int[] probes,int[] recovered){
        if(probes[0]>=96||recovered[0]>=16||cancel.cancelled()
                ||word.length()<2||!word.matches("[\\p{Ll}\\p{M}]{2,32}")
                ||seen.contains(word)||!examined.add(word))return;
        probes[0]++;
        if(lexicon.valid(word)&&!lexicon.preserved(word)){
            seen.add(word);out.add(word);recovered[0]++;
        }
    }
}
