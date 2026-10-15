// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded, offline grammatical rules and native-dictionary candidate ranking. This is not a
 * semantic language model. Call on a worker using a versioned editor snapshot; callers own
 * editor/sensitivity gates, debounce, lifecycle, and the decision to apply a proposal.
 * Lexicon must treat words valid in ANY configured language or user dictionary as valid.
 * Confidence values are heuristic rule scores, not calibrated probabilities of correctness.
 */
public final class LocalContextReviewer {
    public static final int MAX_TEXT_LENGTH = 2048;
    public static final int MAX_TOKENS = 96;
    public static final int MAX_CORRECTIONS = 8;
    private static final int MAX_WORD_LENGTH = 48;
    private static final int MAX_CANDIDATES = 24;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M}\\p{N}_'’-]*");
    private static final Set<String> MOTION = words("voy vas va vamos vais van iba ibas íbamos ibais iban iré irás irá iremos iréis irán");
    private static final Set<String> SUBJUNCTIVE = words("espero esperas espera esperamos esperan deseo deseas desea deseamos desean ojalá");
    private static final Set<String> ARTICLES = words("el la los las un una unos unas este esta ese esa nuestro nuestra");
    private static final Set<String> CLITICS = words("me te le nos os les lo la");
    private static final Set<String> NEGATIONS = words("no not never nevermore nunca jamás tampoco sin ni neither nor don't can't won't");
    private static final Set<String> IRREGULAR_PARTICIPLES = words("hecho dicho visto puesto escrito roto vuelto abierto muerto resuelto cubierto");
    private static final Set<String> REPORTING = words("digo dices dice decimos dicen dijo dijeron responde respondió contestó afirma afirmó asegura aseguró");
    public interface Lexicon { boolean valid(String word); List<String> candidates(String word); }
    public interface Cancellation { boolean cancelled(); }
    public static final class Correction {
        public final int start, end;
        public final String original, replacement, reason;
        public final double confidence;
        public final boolean automaticAllowed;
        public Correction(int start, int end, String original, String replacement, String reason,
                          double confidence, boolean automaticAllowed) {
            this.start=start; this.end=end; this.original=original; this.replacement=replacement;
            this.reason=reason; this.confidence=confidence; this.automaticAllowed=automaticAllowed;
        }
    }
    /**
     * UTF-16 offsets into the EXACT input; sorted, nonoverlapping, immutable results. Rejections
     * may contain original, replacement, or original + "→" + replacement (case insensitive).
     * Cancellation discards the entire result, including proposals already computed. Oversize
     * snapshots and failing dictionary callbacks fail closed. No text is retained by this class.
     */
    public List<Correction> review(String text, Lexicon lexicon, Set<String> rejected,
                                   Cancellation cancellation) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_LENGTH || lexicon == null
                || cancellation == null || cancellation.cancelled()) return Collections.emptyList();
        try {
            List<Token> tokens = tokenize(text);
            if (tokens.size() > MAX_TOKENS) return Collections.emptyList();
            Set<String> ignored = new HashSet<>();
            if (rejected != null) for (String value : rejected) {
                if (value != null) ignored.add(value.toLowerCase(Locale.ROOT));
            }
            List<Correction> result = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                if (cancellation.cancelled()) return Collections.emptyList();
                Token token = tokens.get(i);
                if (token.protectedText || token.word.length() > MAX_WORD_LENGTH
                        || NEGATIONS.contains(token.lower)) continue;
                Correction proposal = hasCapital(token.word) ? null : grammar(text, tokens, i, lexicon);
                if (proposal == null && !lexicon.valid(token.lower)
                        && !hasCapital(token.word)) {
                    proposal = spelling(tokens, i, lexicon, cancellation);
                }
                if (proposal != null && !ignored.contains(proposal.original.toLowerCase(Locale.ROOT))
                        && !ignored.contains(proposal.replacement.toLowerCase(Locale.ROOT))
                        && !ignored.contains((proposal.original + "→" + proposal.replacement)
                        .toLowerCase(Locale.ROOT))) {
                    result.add(proposal);
                    if (result.size() == MAX_CORRECTIONS) break;
                }
            }
            if (cancellation.cancelled()) return Collections.emptyList();
            return Collections.unmodifiableList(result);
        } catch (RuntimeException unavailableDictionary) {
            return Collections.emptyList();
        }
    }

    private static Correction grammar(String text, List<Token> tokens, int i, Lexicon lexicon) {
        Token t = tokens.get(i);
        if (t.lower.equals("haber") && i > 0 && i + 2 < tokens.size()
                && MOTION.contains(tokens.get(i - 1).lower)
                && tokens.get(i + 1).lower.equals("si")
                && linked(text, tokens, i - 1, i + 2)) {
            return correction(t, "a ver", "motion-infinitive-before-conditional", .995, true);
        }
        String auxiliary = t.lower.equals("hallas") ? "hayas"
                : t.lower.equals("halla") ? "haya" : t.lower.equals("hallan") ? "hayan" : null;
        if (auxiliary != null && i > 1 && i + 1 < tokens.size()
                && tokens.get(i - 1).lower.equals("que")
                && participle(tokens.get(i + 1).lower)
                && lexicon.valid(tokens.get(i + 1).lower)
                && linked(text, tokens, i - 1, i + 1)) {
            for (int j = i - 2; j >= Math.max(0, i - 5); j--) {
                if (!linked(text, tokens, j, i)) break;
                if (SUBJUNCTIVE.contains(tokens.get(j).lower)) {
                    return correction(t, auxiliary, "subjunctive-auxiliary-before-participle", .985, false);
                }
            }
        }
        // A determiner+noun subject and indirect object exclude first-person present. Tense is
        // still an interpretation: offer a suggestion, never an automatic edit.
        if (t.lower.endsWith("o") && t.lower.length() > 3 && i >= 3 && i + 1 < tokens.size()
                && ARTICLES.contains(tokens.get(i - 3).lower)
                && CLITICS.contains(tokens.get(i - 1).lower)
                && tokens.get(i + 1).lower.equals("que")
                && linked(text, tokens, i - 3, i + 1)) {
            String accented = t.lower.substring(0, t.lower.length() - 1) + "ó";
            if (lexicon.valid(accented)) {
                return correction(t, accented, "third-person-subject-past-accent", .94, false);
            }
        }
        // A terminal affirmative after reported speech is a possible reading. A following word
        // keeps the conditional 'si'; unfinished conditionals are never automatically accented.
        if (t.lower.equals("si") && i >= 2 && i == tokens.size() - 1
                && tokens.get(i - 1).lower.equals("que")
                && linked(text, tokens, i - 2, i)
                && reportedVerb(tokens.get(i - 2).lower, lexicon)) {
            return correction(t, "sí", "possible-reported-affirmative", .87, false);
        }
        return null;
    }

    private static boolean reportedVerb(String word, Lexicon lexicon) {
        return REPORTING.contains(word) || (word.length() > 3 && lexicon.valid(word)
                && (word.endsWith("ó") || (word.endsWith("o")
                && lexicon.valid(word.substring(0, word.length() - 1) + "ó"))));
    }

    private static Correction spelling(List<Token> tokens, int i, Lexicon lexicon,
                                        Cancellation cancellation) {
        Token t = tokens.get(i);
        List<String> candidates = lexicon.candidates(t.lower);
        if (candidates == null) return null;
        String best = null;
        double bestScore = -1, secondScore = -1;
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (String candidate : candidates) {
            if (++count > MAX_CANDIDATES || cancellation.cancelled()) break;
            if (candidate == null || candidate.length() > MAX_WORD_LENGTH || candidate.length() < 2
                    || hasCapital(candidate) || !letterWord(candidate)) continue;
            String value = candidate.toLowerCase(Locale.ROOT);
            if (!seen.add(value) || NEGATIONS.contains(value) || !lexicon.valid(value)) continue;
            String source = withoutAccents(t.lower), target = withoutAccents(value);
            int distance = distance(source, target);
            if (distance > 2 || distance > Math.max(1, source.length() / 4)) continue;
            double score = 1.0 - distance * .12;
            if (source.equals(target)) score += .1;
            // Weak agreement tie-breaker, never a semantic replacement of a valid noun.
            if (i > 0 && ARTICLES.contains(tokens.get(i - 1).lower)) {
                String article = tokens.get(i - 1).lower;
                if ((article.endsWith("a") && value.endsWith("a"))
                        || (article.equals("el") && value.endsWith("o"))) score += .025;
            }
            if (score > bestScore) {
                secondScore = bestScore; bestScore = score; best = value;
            } else secondScore = Math.max(secondScore, score);
        }
        if (best == null || best.equals(t.lower)) return null;
        double confidence = bestScore == secondScore ? .65
                : Math.min(.97, Math.max(.70, bestScore));
        return correction(t, best, "native-dictionary-spelling-candidate", confidence, false);
    }

    private static List<Token> tokenize(String text) {
        boolean[] quoted = new boolean[text.length()];
        char close = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean apostropheInWord = (c == '\'' || c == '’') && i > 0 && i + 1 < text.length()
                    && Character.isLetter(text.charAt(i - 1)) && Character.isLetter(text.charAt(i + 1));
            if (close != 0) {
                quoted[i] = true;
                if (c == close && !apostropheInWord) close = 0;
            } else if (!apostropheInWord && (c == '"' || c == '\'' || c == '`'
                    || c == '“' || c == '‘' || c == '«')) {
                close = c == '“' ? '”' : c == '‘' ? '’' : c == '«' ? '»' : c;
                quoted[i] = true;
            }
        }
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find() && tokens.size() <= MAX_TOKENS) {
            int start = matcher.start(), end = matcher.end();
            boolean protectedText = quoted[start];
            int left = start, right = end;
            while (left > 0 && !Character.isWhitespace(text.charAt(left - 1))) left--;
            while (right < text.length() && !Character.isWhitespace(text.charAt(right))) right++;
            String chunk = text.substring(left, right).replaceAll("[.,!?;]+$", "");
            for (int j = 0; j < chunk.length(); j++) {
                char c = chunk.charAt(j);
                if (Character.isDigit(c) || "@/:_\\=+".indexOf(c) >= 0
                        || (c == '.' && j > 0 && j + 1 < chunk.length())) protectedText = true;
            }
            String word = matcher.group();
            if (word.indexOf('-') >= 0 || word.indexOf('\'') >= 0 || word.indexOf('’') >= 0)
                protectedText = true;
            tokens.add(new Token(start, end, word, protectedText));
        }
        return tokens;
    }

    private static boolean linked(String text, List<Token> tokens, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (tokens.get(i).protectedText) return false;
            if (i > start) {
                String gap = text.substring(tokens.get(i - 1).end, tokens.get(i).start);
                if (!gap.matches("[ \\t]+")) return false;
            }
        }
        return true;
    }
    private static Correction correction(Token token, String replacement, String reason,
                                         double confidence, boolean automatic) {
        return new Correction(token.start, token.end, token.word, replacement, reason, confidence, automatic);
    }
    private static boolean participle(String word) {
        return word.endsWith("ado") || word.endsWith("ido") || IRREGULAR_PARTICIPLES.contains(word);
    }
    private static boolean hasCapital(String word) {
        for (int i = 0; i < word.length();) {
            int codePoint = word.codePointAt(i);
            if (Character.isUpperCase(codePoint)) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }
    private static boolean letterWord(String word) {
        for (int i = 0; i < word.length();) {
            int codePoint = word.codePointAt(i);
            if (!Character.isLetter(codePoint)
                    && Character.getType(codePoint) != Character.NON_SPACING_MARK) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }
    private static String withoutAccents(String word) {
        // ñ changes lexical identity, unlike vowel stress marks; retain it when ranking.
        return Normalizer.normalize(word.replace('ñ', '\u0001'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('\u0001', 'ñ');
    }
    private static int distance(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 2) return 3;
        int[] row = new int[right.length() + 1];
        for (int j = 0; j < row.length; j++) row[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int diagonal = row[0]; row[0] = i; int min = row[0];
            for (int j = 1; j <= right.length(); j++) {
                int old = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1),
                        diagonal + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
                diagonal = old; min = Math.min(min, row[j]);
            }
            if (min > 2) return 3;
        }
        return row[right.length()];
    }
    private static Set<String> words(String text) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(text.split(" "))));
    }
    private static final class Token {
        final int start, end;
        final String word, lower;
        final boolean protectedText;
        Token(int start, int end, String word, boolean protectedText) {
            this.start = start; this.end = end; this.word = word;
            lower = word.toLowerCase(Locale.ROOT); this.protectedText = protectedText;
        }
    }
}
