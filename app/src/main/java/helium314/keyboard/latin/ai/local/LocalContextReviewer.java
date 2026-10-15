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
    public interface Lexicon {
        boolean valid(String word);
        List<String> candidates(String word);
        /** True only after native validity checked the configured languages and personal words. */
        default boolean preserved(String word) { return false; }
        default boolean spaceNeighbor(char c) { return "vbn".indexOf(c)>=0; }
        default boolean knownInvalid(String word) { return false; }
        default double prior(String source,String candidate){return 0;}
        default double personalPreference(String source,String candidate){return 0;}
        default boolean rejected(String source,String candidate){return false;}
        default double keyDistance(char a,char b){return TypingCandidateRanker.QWERTY.distance(a,b);}
    }
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
                        || NEGATIONS.contains(token.lower) || lexicon.preserved(token.lower)) continue;
                Correction proposal = hasCapital(token.word) ? title(text,tokens,i,lexicon,cancellation) : mechanics(text,tokens,i,lexicon);
                if(proposal==null&&!hasCapital(token.word))proposal=grammar(text,tokens,i,lexicon);
                if(proposal==null&&!hasCapital(token.word)){
                    WordBoundarySegmenter.Choice split=WordBoundarySegmenter.choose(token.lower,lexicon,cancellation);
                    if(split!=null)proposal=correction(token,split.text,"dictionary-boundary-segmentation",.9,split.automatic);
                }
                if (proposal == null && !lexicon.valid(token.lower)
                        && !hasCapital(token.word)) {
                    proposal = spelling(text, tokens, i, lexicon, cancellation);
                }
                if (proposal != null && (result.isEmpty()||result.get(result.size()-1).end<=proposal.start)
                        && !lexicon.rejected(proposal.original.toLowerCase(Locale.ROOT),proposal.replacement.toLowerCase(Locale.ROOT))
                        && !ignored.contains(proposal.original.toLowerCase(Locale.ROOT))
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
        if(i>0&&i+1<tokens.size()&&linked(text,tokens,i-1,i+1)){
            String previous=tokens.get(i-1).lower,next=tokens.get(i+1).lower;
            if(Arrays.asList("tener","tengo","tiene","tienen","necesito","necesita","necesitamos","requiere").contains(previous)
                    &&t.lower.length()==3&&!t.lower.equals("más")&&abstractNoun(next)&&lexicon.valid(next)&&lexicon.valid("más")
                    &&TypingCandidateRanker.editCost(t.lower,"más",lexicon::keyDistance)<=.7)
                return correction(t,"más","quantity-modifier-before-abstract-noun",.96,true);
            if(t.lower.length()==2&&t.lower.endsWith("y")&&abstractNoun(previous)&&abstractNoun(next)
                    &&lexicon.valid(previous)&&lexicon.valid(next)&&lexicon.valid("y"))
                return correction(t,"y","conjunction-between-abstract-nouns",.96,true);
        }
        if(t.lower.endsWith("ió")&&i>0&&linked(text,tokens,i-1,i)){
            boolean firstPerson=tokens.get(i-1).lower.equals("yo");
            if(!firstPerson&&i>=3&&tokens.get(i-1).lower.equals("cuando")&&linked(text,tokens,i-3,i)){
                String infinitive=tokens.get(i-2).lower;
                firstPerson=tokens.get(i-3).lower.equals("suelo")&&
                    (infinitive.endsWith("ar")||infinitive.endsWith("er")||infinitive.endsWith("ir"));
            }
            String present=t.lower.substring(0,t.lower.length()-2)+"ío";
            if(firstPerson&&lexicon.valid(present))return correction(t,present,"explicit-first-person-accent",.96,true);
        }
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

    private static boolean abstractNoun(String word){
        return word.matches(".*(ción|sión|ncia|dad|tad|miento|tura|idez|eza)$");
    }
    private static Correction title(String text,List<Token> tokens,int i,Lexicon lexicon,Cancellation cancel){
        Token t=tokens.get(i);
        if(t.word.length()<3||!Character.isUpperCase(t.word.charAt(0))
                ||!t.word.substring(1).equals(t.lower.substring(1))||lexicon.valid(t.lower))return null;
        String prefix=text.substring(0,t.start).trim();
        if(!prefix.isEmpty()&&!prefix.matches("(?s).*[.!?]$"))return null;
        String next=i+1<tokens.size()&&linked(text,tokens,i,i+1)?tokens.get(i+1).lower:"";
        TypingCandidateRanker.Choice choice=TypingCandidateRanker.choose(t.lower,"",next,lexicon,cancel);
        if(choice==null)return null;
        boolean safe=Arrays.asList("esta","este","estas","estos","esa","ese","esas","esos").contains(choice.word)
            &&Arrays.asList("es","son","era","eran").contains(next)&&choice.cost<=.7&&choice.margin>=.3&&lexicon.knownInvalid(t.lower);
        String replacement=Character.toUpperCase(choice.word.charAt(0))+choice.word.substring(1);
        return correction(t,replacement,"sentence-initial-dictionary-candidate",.9,safe);
    }
    private static Correction mechanics(String text,List<Token> tokens,int i,Lexicon lexicon){
        Token t=tokens.get(i);
        if(i+2<tokens.size()&&Arrays.asList("el","la","los","las","un","una","unos","unas").contains(t.lower)
                &&t.lower.equals(tokens.get(i+1).lower)&&!t.lower.equals(tokens.get(i+2).lower)
                &&linked(text,tokens,i,i+2)&&!hasCapital(tokens.get(i+2).word)
                &&lexicon.valid(tokens.get(i+2).lower)&&lexicon.valid(t.lower)){
            Token repeated=tokens.get(i+1);
            return new Correction(t.start,repeated.end,text.substring(t.start,repeated.end),t.word,
                "adjacent-duplicate-article",.96,true);
        }
        if(!lexicon.valid(t.lower))return null;
        int end=t.end;
        while(end<text.length()&&(text.charAt(end)==' '||text.charAt(end)==9))end++;
        if(end==t.end||end>=text.length()||".,;:!?".indexOf(text.charAt(end))<0)return null;
        int punctuation=end+1;
        if(text.charAt(end)=='.')while(punctuation<text.length()&&text.charAt(punctuation)=='.')punctuation++;
        if(punctuation<text.length()&&!Character.isWhitespace(text.charAt(punctuation)))return null;
        return new Correction(t.start,punctuation,text.substring(t.start,punctuation),
            t.word+text.substring(end,punctuation),"space-before-punctuation",.99,true);
    }

    private static boolean reportedVerb(String word, Lexicon lexicon) {
        return REPORTING.contains(word) || (word.length() > 3 && lexicon.valid(word)
                && (word.endsWith("ó") || (word.endsWith("o")
                && lexicon.valid(word.substring(0, word.length() - 1) + "ó"))));
    }

    private static Correction spelling(String text, List<Token> tokens, int i, Lexicon lexicon,
                                        Cancellation cancellation) {
        Token t = tokens.get(i);
        String previous=i>0&&linked(text,tokens,i-1,i)?tokens.get(i-1).lower:"",
            next=i+1<tokens.size()&&linked(text,tokens,i,i+1)?tokens.get(i+1).lower:"";
        TypingCandidateRanker.Choice choice=TypingCandidateRanker.choose(t.lower,previous,next,lexicon,cancellation);
        if(choice==null)return null;
        String best=choice.word;
        double confidence=Math.min(.97,Math.max(.65,1-choice.cost*.12));
        // Deliberately narrow: long lowercase words, one s/z/c confusion after accent
        // normalization, an accented verified target and a decisive candidate margin.
        // This decision is independent of the heuristic confidence value.
        String source=withoutAccents(t.lower), target=withoutAccents(best);
        int differences=0;
        boolean confusion=true;
        if(source.length()!=target.length())confusion=false;
        else for(int j=0;j<source.length();j++)if(source.charAt(j)!=target.charAt(j)) {
            differences++;
            if("szc".indexOf(source.charAt(j))<0||"szc".indexOf(target.charAt(j))<0)confusion=false;
        }
        boolean automatic=lexicon.knownInvalid(t.lower)&&!lexicon.valid(t.lower)
            &&source.length()>=8&&confusion&&differences==1
            &&!best.equals(target)&&choice.margin>=.30;
        return correction(t, best, "motor-error-native-context-ranking", confidence, automatic||choice.automatic);
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

    /** Shares lexical protection with contextual review; personal evidence cannot bypass it. */
    public boolean allowsPersonal(String text,int start,int end,String replacement,Set<String> rejected){
        if(text==null||text.length()>MAX_TEXT_LENGTH||replacement==null)return false;
        List<Token> tokens=tokenize(text);if(tokens.size()>MAX_TOKENS)return false;
        for(Token token:tokens)if(token.start==start&&token.end==end){
            if(token.protectedText||hasCapital(token.word)||NEGATIONS.contains(token.lower))return false;
            if(rejected!=null)for(String value:rejected){
                String rejection=value.toLowerCase(Locale.ROOT);
                if(rejection.equals(token.lower)||rejection.equals(replacement.toLowerCase(Locale.ROOT))
                    ||rejection.equals((token.word+"→"+replacement).toLowerCase(Locale.ROOT)))return false;
            }
            return true;
        }
        return false;
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
        return Normalizer.normalize(word.replace('ñ', (char) 1), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace((char) 1, 'ñ');
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
