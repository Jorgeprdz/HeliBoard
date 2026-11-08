// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import java.util.*;

/** Dictionary-verified adjacent-key variants. No phrase list, IO, learned messages or model. */
public final class NeighborWordCorrector {
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzñ";
    private static final String VOWELS = "aeiou", ACUTE = "áéíóú";
    private static final Set<String> NEGATIONS = new HashSet<>(Arrays.asList("nunca", "jamás", "never", "neither"));
    private static final int MAX_PROBES = 1536;
    private NeighborWordCorrector() {}
    public static final class Choice {
        public final String word;
        public final double cost;
        Choice(String word, double cost) { this.word = word; this.cost = cost; }
    }

    public static Choice accent(String source, LocalContextReviewer.Lexicon lex,
                                LocalContextReviewer.Cancellation stop) {
        if (!eligible(source, lex, stop) || hasAcute(source)) return null;
        String found = null;
        for (int i = 0; i < source.length(); i++) {
            if (stop.cancelled()) return null;
            int vowel = VOWELS.indexOf(source.charAt(i));
            if (vowel < 0) continue;
            String candidate = source.substring(0, i) + ACUTE.charAt(vowel) + source.substring(i + 1);
            if (lex.valid(candidate) && !lex.preserved(candidate)) {
                if (found != null) return null;
                found = candidate;
            }
        }
        return found == null || stop.cancelled() || lex.rejected(source, found) ? null : new Choice(found, .12);
    }

    public static Choice choose(String source, LocalContextReviewer.Lexicon lex,
                                LocalContextReviewer.Cancellation stop) {
        if (!eligible(source, lex, stop) || source.length() < 5 || source.length() > 16) return null;
        List<List<Character>> neighbors = new ArrayList<>();
        for (char key : source.toCharArray()) {
            List<Character> nearby = new ArrayList<>();
            for (char candidate : LETTERS.toCharArray()) {
                double distance = lex.keyDistance(key, candidate);
                // Spanish rows are aligned; a corner neighbor is ~1.54 key widths away.
                if (candidate != key && Double.isFinite(distance) && distance > 0 && distance <= 1.55)
                    nearby.add(candidate);
            }
            nearby.sort(Comparator.<Character>comparingDouble(c -> lex.keyDistance(key, c)).thenComparingInt(c -> c));
            neighbors.add(nearby);
        }
        Search search = new Search(source, lex, stop);
        search.probeVariants(source);
        char[] letters = source.toCharArray();
        // One wrong key: cover every physically adjacent key, including diagonals.
        for (int i = 0; i < letters.length && !search.incomplete; i++) {
            for (char a : neighbors.get(i)) {
                letters[i] = a;
                search.probeVariants(new String(letters));
            }
            letters[i] = source.charAt(i);
        }
        // Two wrong keys: restrict the combinatorial search to the closest two per key.
        for (int i = 0; i < letters.length && !search.incomplete; i++) {
            List<Character> first = neighbors.get(i);
            for (char a : first.subList(0, Math.min(2, first.size()))) {
                letters[i] = a;
                for (int j = i + 1; j < letters.length && !search.incomplete; j++) {
                    List<Character> second = neighbors.get(j);
                    for (char b : second.subList(0, Math.min(2, second.size()))) {
                        letters[j] = b;
                        search.probeVariants(new String(letters));
                    }
                    letters[j] = source.charAt(j);
                }
            }
            letters[i] = source.charAt(i);
        }
        if (search.incomplete || stop.cancelled() || search.found.isEmpty()) return null;
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(search.found.entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
        Map.Entry<String, Double> best = ranked.get(0);
        if (best.getValue() > 1.45 || (ranked.size() > 1 && ranked.get(1).getValue() - best.getValue() < .35)
                || lex.rejected(source, best.getKey())) return null;
        return new Choice(best.getKey(), best.getValue());
    }

    private static boolean eligible(String source, LocalContextReviewer.Lexicon lex,
                                    LocalContextReviewer.Cancellation stop) {
        return source != null && source.matches("[a-zñüáéíóú]{3,32}") && !stop.cancelled()
                && !lex.preserved(source) && !lex.valid(source) && lex.knownInvalid(source);
    }
    private static boolean hasAcute(String word) {
        for (int i = 0; i < word.length(); i++) if (ACUTE.indexOf(word.charAt(i)) >= 0) return true;
        return false;
    }
    private static final class Search {
        final String source;
        final LocalContextReviewer.Lexicon lex;
        final LocalContextReviewer.Cancellation stop;
        final Set<String> seen = new HashSet<>();
        final Map<String, Double> found = new HashMap<>();
        boolean incomplete;
        Search(String source, LocalContextReviewer.Lexicon lex, LocalContextReviewer.Cancellation stop) {
            this.source = source; this.lex = lex; this.stop = stop;
        }
        void probeVariants(String candidate) {
            probe(candidate);
            if (hasAcute(candidate)) return;
            for (int i = 0; i < candidate.length() && !incomplete; i++) {
                int vowel = VOWELS.indexOf(candidate.charAt(i));
                if (vowel >= 0) probe(candidate.substring(0, i) + ACUTE.charAt(vowel) + candidate.substring(i + 1));
            }
        }
        void probe(String candidate) {
            if (incomplete || stop.cancelled()) { incomplete = true; return; }
            if (candidate.equals(source) || !seen.add(candidate)) return;
            if (seen.size() > MAX_PROBES) { incomplete = true; return; }
            if (!NEGATIONS.contains(candidate) && lex.valid(candidate) && !lex.preserved(candidate))
                found.put(candidate, TypingCandidateRanker.editCost(source, candidate, lex::keyDistance));
        }
    }
}
