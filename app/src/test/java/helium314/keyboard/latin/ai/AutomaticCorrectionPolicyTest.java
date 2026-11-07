package helium314.keyboard.latin.ai;

import helium314.keyboard.latin.ai.local.LocalContextReviewer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AutomaticCorrectionPolicyTest {
    private final LocalContextReviewer.Lexicon lexicon = new LocalContextReviewer.Lexicon() {
        final Set<String> words = new HashSet<>(Arrays.asList("quiero", "escribir", "bien", "la", "casa", "cosa", "no", "si", "sí", "tu", "tú", "haber", "a", "ver", "voy", "proyectos", "el", "él", "funciona"));
        public boolean valid(String word) { return words.contains(word); }
        public boolean knownInvalid(String word) { return !valid(word); }
        public List<String> candidates(String word) { return Collections.emptyList(); }
    };
    private String corrected(String source,String generated) {
        LocalContextReviewer.Correction c=AutomaticCorrectionPolicy.choose(source,generated,lexicon,Collections.emptySet());
        return c==null?source:source.substring(0,c.start)+c.replacement+source.substring(c.end);
    }
    @Test public void automaticallyFixesTypoWithoutRewritingMessage() {
        assertEquals("quiero escribir bien ", corrected("quiero escrbir bien ","quiero escribir bien "));
    }
    @Test public void contextHomophoneCanBeCorrected() {
        assertEquals("voy a ver si funciona ",corrected("voy haber si funciona ","voy a ver si funciona "));
    }
    @Test public void validWordParaphraseIsNotAnAutomaticCorrection() {
        assertEquals("la casa ",corrected("la casa ","la cosa "));
    }
    @Test public void protectsNegationsNumbersNamesAndLinks() {
        for(String[] pair:new String[][]{{"no quiero ","quiero "},{"quiero 25 ","quiero 35 "},{"Jorge escrbe ","Juan escribe "},{"https://casa.es ","https://cosa.es "}})
            assertEquals(pair[0],corrected(pair[0],pair[1]));
    }
    @Test public void validFunctionWordsAreNotPermissionToChangeIntent() {
        assertEquals("quiero la casa ",corrected("quiero la casa ","quiero una casa "));
        assertEquals("vengo a casa ",corrected("vengo a casa ","vengo de casa "));
    }
    @Test public void truncatedWindowKeepsTheLastCompleteSentenceInsteadOfTrailingSpace() {
        assertEquals("quiero escrbir bien. ",AutomaticCorrectionPolicy.context("cortado. quiero escrbir bien. ",800));
    }
    @Test public void punctuationMayBeFixedWithoutChangingWords() {
        assertEquals("quiero, escribir bien ",corrected("quiero escribir bien ","quiero, escribir bien "));
        assertEquals("no quiero ",corrected("no quiero ","no, quiero "));
    }
    @Test public void explicitProtectedVocabularyAlsoBlocksContextFallback() {
        LocalContextReviewer.Lexicon protectedLexicon=new LocalContextReviewer.Lexicon(){
            public boolean valid(String word){return lexicon.valid(word);}
            public boolean knownInvalid(String word){return lexicon.knownInvalid(word);}
            public List<String> candidates(String word){return Collections.emptyList();}
            public boolean preserved(String word){return "haber".equals(word);}
        };
        assertNull(AutomaticCorrectionPolicy.choose("voy haber si funciona ","voy a ver si funciona ",protectedLexicon,Collections.emptySet()));
    }
    @Test public void explicitRejectionVetoesAutomaticChange() {
        assertNull(AutomaticCorrectionPolicy.choose("quiero escrbir bien ","quiero escribir bien ",lexicon,Collections.singleton("escrbir→escribir")));
    }
    @Test public void whitespaceAndEmojiArePreserved() {
        assertEquals("🙂 quiero escribir bien  ",corrected("🙂 quiero escrbir bien  ","🙂 quiero escribir bien  "));
    }
    @Test public void automaticSessionDebouncesAndThrottles() {
        AutomaticReviewGate gate=new AutomaticReviewGate();
        assertFalse(gate.ready(1000)); gate.boundary(1000);
        assertFalse(gate.ready(1899)); assertTrue(gate.ready(1900));
        gate.started(1900); gate.boundary(2000);
        assertFalse(gate.ready(4899));assertTrue(gate.ready(4900));
        gate.invalidate(); assertFalse(gate.ready(10000));
    }
    @Test public void preferredModelIsDiscoverableTextFlashNotMediaModel() {
        assertEquals("gemini-3.8-flash",GeminiModelChoice.choose(new String[]{"gemini-3.8-flash-image","gemini-3.8-pro","gemini-3.8-flash"}));
        assertEquals("",GeminiModelChoice.choose(new String[]{"gemini-3-flash-tts"}));
    }
}
