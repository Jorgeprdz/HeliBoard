// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LocalContextReviewerTest {
    static final LocalContextReviewer.Cancellation ACTIVE = () -> false;
    static final LocalContextReviewer.Lexicon LEXICON = new FixtureLexicon();
    final LocalContextReviewer reviewer = new LocalContextReviewer();

    static class FixtureLexicon implements LocalContextReviewer.Lexicon {
        final Set<String> words = new HashSet<>(Arrays.asList((
                "el la los las un una cliente proveedor equipo me te nos lo confirmo confirmó "
                + "aviso avisó reporto reportó indicó indico respondió digo dijo dice que si sí "
                + "voy vas va vamos van iremos iba iban espero esperamos deseo ojalá hallas "
                + "hayas halla haya hallan hayan llegado terminado enviado aprobado comprado "
                + "encontrado perdido escondido haber puedo puedes puede podemos revisar mañana "
                + "cotización cotizaciones información reunión acción propuesta tiempo respuesta "
                + "confianza no nunca jamás tampoco sin hey meeting update deploy build tests "
                + "are green good afternoon don't can't api release prueba necesito dos esto "
                + "yo tú ellos dijeron cotizar ubicación casa caza peso beso paso pero gato "
                + "well do check the software with team shipping tomorrow siemore siempre").split(" ")));
        public boolean valid(String word) { return words.contains(word.toLowerCase(Locale.ROOT)); }
        public List<String> candidates(String word) {
            switch (word.toLowerCase(Locale.ROOT)) {
                case "cotisacion": return Arrays.asList("cotización", "cotizaciones");
                case "informacion": return Arrays.asList("información");
                case "reunion": return Arrays.asList("reunión");
                case "ubicacion": return Arrays.asList("ubicación", "información");
                case "casz": return Arrays.asList("casa", "caza");
                case "psso": return Arrays.asList("paso", "peso");
                case "cotisacioñ": return Arrays.asList("cotización");
                default: return Collections.emptyList();
            }
        }
    }

    List<LocalContextReviewer.Correction> review(String text) {
        return reviewer.review(text, LEXICON, Collections.emptySet(), ACTIVE);
    }
    String apply(String text) {
        List<LocalContextReviewer.Correction> changes = review(text);
        StringBuilder out = new StringBuilder(text);
        for (int i = changes.size() - 1; i >= 0; i--) {
            LocalContextReviewer.Correction c = changes.get(i);
            assertEquals(c.original, text.substring(c.start, c.end));
            assertTrue(c.confidence > 0 && c.confidence <= 1);
            assertFalse(c.reason.isEmpty());
            out.replace(c.start, c.end, c.replacement);
        }
        return out.toString();
    }
    @Test public void r22UnambiguousSpellingHasIndependentAutomaticPolicy() {
        assertTrue(review("necesito cotisacion ").get(0).automaticAllowed);
        assertFalse(review("casz ").get(0).automaticAllowed);
        LocalContextReviewer.Lexicon personal = new FixtureLexicon() {
            @Override public boolean valid(String word) {
                return word.equals("cotisacion") || super.valid(word);
            }
        };
        assertTrue(reviewer.review("cotisacion ", personal, Collections.emptySet(), ACTIVE).isEmpty());
    }
    @Test public void nativeCandidatesFixTyposWithoutStoredExamplePhrases() {
        assertEquals("necesito cotización mañana", apply("necesito cotisacion mañana"));
        assertEquals("revisar información y ubicación", apply("revisar informacion y ubicacion"));
    }
    @Test public void motionVerbAndConditionalLicenseMultiwordSpan() {
        assertEquals("voy a ver si puedo", apply("voy haber si puedo"));
        assertEquals("mañana iremos a ver si podemos revisar", apply("mañana iremos haber si podemos revisar"));
        assertTrue(review("vamos haber si podemos").get(0).automaticAllowed);
        assertEquals("puede haber si se combina", apply("puede haber si se combina"));
    }
    @Test public void SubjunctiveWithParticipleGeneralizesAcrossSubjects() {
        assertEquals("espero que hayas llegado", apply("espero que hallas llegado"));
        assertEquals("deseo que hayan terminado", apply("deseo que hallan terminado"));
        assertEquals("espero que hallas la casa", apply("espero que hallas la casa"));
        assertEquals("¿Qué hallas escondido?", apply("¿Qué hallas escondido?"));
    }
    @Test public void explicitThirdPersonSubjectSupportsReportedPastButNeverSilently() {
        assertEquals("el cliente me confirmó que sí", apply("el cliente me confirmo que si"));
        assertEquals("la proveedora nos avisó que sí.", apply("la proveedora nos aviso que si."));
        assertEquals("yo confirmo que sí", apply("yo confirmo que si"));
        assertEquals("el cliente me confirmó que si puedo", apply("el cliente me confirmo que si puedo"));
        for (LocalContextReviewer.Correction c : review("el cliente me confirmo que si")) {
            assertFalse(c.automaticAllowed);
        }
    }
    @Test public void protectedBilingualTechnicalQuotedAndNumericTokensStayExact() {
        String[] texts = {"Hey! mañana tenemos meeting con Carlos", "Xochitl deploy HTTPServer_v2",
                "https://host/cotisacion cotisacion@example.org", "`voy haber si puedo`",
                "dijo \"espero que hallas llegado\"", "«haber qué pasa»", "'cotisacion'",
                "don't change the build", "123-cotisacion no nunca jamás tampoco sin",
                "la casa no es caza", "cotisacion.js", "confirmo\nque si", "voy. haber si puedo"};
        for (String text : texts) assertEquals(text, apply(text));
    }
    @Test public void emojiOffsetsRemainUtf16AndNoChangesTouchNegation() {
        String text = "🙂 no necesito cotisacion";
        assertEquals("🙂 no necesito cotización", apply(text));
        LocalContextReviewer.Correction c = review(text).get(0);
        assertEquals(15, c.start);
        assertEquals(25, c.end);
    }
    @Test public void rejectedPairSuppressesOnlyThatProposal() {
        assertTrue(reviewer.review("voy haber si puedo", LEXICON,
                Collections.singleton("haber→a ver"), ACTIVE).isEmpty());
        assertTrue(reviewer.review("necesito cotisacion", LEXICON,
                Collections.singleton("cotisacion"), ACTIVE).isEmpty());
    }
    @Test public void cancellationDiscardsAllPartialResultsAndLimitsInput() {
        assertTrue(reviewer.review("cotisacion", LEXICON, Collections.emptySet(), () -> true).isEmpty());
        int[] calls = {0};
        assertTrue(reviewer.review("cotisacion informacion reunion", LEXICON,
                Collections.emptySet(), () -> ++calls[0] > 4).isEmpty());
        assertTrue(review(String.join(" ", Collections.nCopies(110, "cotisacion"))).isEmpty());
        assertTrue(review(String.join("", Collections.nCopies(3000, "a"))).isEmpty());
    }
    @Test public void ambiguousCandidatesAreSuggestionsAndUnknownWordsAreKept() {
        assertFalse(review("casz").get(0).automaticAllowed);
        assertEquals("unobtainium Xochitl GPU", apply("unobtainium Xochitl GPU"));
    }
    @Test public void grammarDoesNotLowercaseNamesOrShoutedText() {
        assertEquals("voy Haber si puedo", apply("voy Haber si puedo"));
        assertEquals("espero que HALLAS llegado", apply("espero que HALLAS llegado"));
    }
    @Test public void unavailableDictionaryAndCandidateFloodFailSafely() {
        LocalContextReviewer.Lexicon failing = new FixtureLexicon() {
            @Override public boolean valid(String word) { throw new IllegalStateException("closed"); }
        };
        assertTrue(reviewer.review("cotisacion", failing, Collections.emptySet(), ACTIVE).isEmpty());
        LocalContextReviewer.Lexicon flooded = new FixtureLexicon() {
            @Override public List<String> candidates(String word) {
                List<String> candidates = new ArrayList<>(Collections.nCopies(24, "not-a-word"));
                candidates.add("cotización");
                return candidates;
            }
        };
        assertTrue(reviewer.review("cotisacion", flooded, Collections.emptySet(), ACTIVE).isEmpty());
    }
}
