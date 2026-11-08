package helium314.keyboard.latin.ai.local;

import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class R30SpanishAccentsTest {
    static SpanishAccentIndex index() throws IOException {
        Path path=Paths.get("src/main/assets/spanish-accents.tsv");
        if(!Files.exists(path))path=Paths.get("app/src/main/assets/spanish-accents.tsv");
        try(Reader reader=Files.newBufferedReader(path)){return SpanishAccentIndex.read(reader);}
    }
    static LocalContextReviewer.Lexicon lex(SpanishAccentIndex index,Set<String> personal) {
        return new BoundedDictionaryLexicon(new R29NeighborCorrectionTest.Lex(),w->true,()->false,
            w->personal.contains(w)?null:index.get(w));
    }
    static String apply(String text, LocalContextReviewer.Lexicon lex,Set<String> rejected) {
        StringBuilder result=new StringBuilder(text);
        List<LocalContextReviewer.Correction> corrections=new LocalContextReviewer().review(text,lex,rejected,()->false);
        for(int i=corrections.size()-1;i>=0;i--){LocalContextReviewer.Correction c=corrections.get(i);if(c.automaticAllowed)result.replace(c.start,c.end,c.replacement);}
        return result.toString();
    }
    @Test public void realBundledEvidenceOverridesPermissiveValidity() throws Exception {
        SpanishAccentIndex index=index();assertEquals(10407,index.size());
        assertEquals("también además después teléfono instalación ",apply("tambien ademas despues telefono instalacion ",lex(index,Collections.emptySet()),Collections.emptySet()));
        assertEquals("También ",apply("Tambien ",lex(index,Collections.emptySet()),Collections.emptySet()));
    }
    @Test public void validSpanishFormsAndMultipleAccentedAlternativesStayExact() throws Exception {
        SpanishAccentIndex index=index();
        for(String word:Arrays.asList("termino","continuo","ingles","practico","publico","si","el","tu","mas","solo","jose")){
            assertNull(word,index.get(word));assertEquals(word+" ",apply(word+" ",lex(index,Collections.emptySet()),Collections.emptySet()));
        }
    }
    @Test public void personalVetoRejectionAndProtectedTextStayExact() throws Exception {
        SpanishAccentIndex index=index();
        assertEquals("tambien ",apply("tambien ",lex(index,Collections.singleton("tambien")),Collections.emptySet()));
        assertEquals("tambien ",apply("tambien ",lex(index,Collections.emptySet()),Collections.singleton("tambien→también")));
        for(String text:Arrays.asList("TAMBIEN ","TaMbien ","hola Tambien ","`tambien` ","https://tambien.example ","\"tambien\" "))
            assertEquals(text,apply(text,lex(index,Collections.emptySet()),Collections.emptySet()));
        assertTrue(new LocalContextReviewer().review("tambien ",lex(index,Collections.emptySet()),Collections.emptySet(),()->true).isEmpty());
    }
    @Test public void absentEvidenceOrInvalidTargetCannotOverrideValidSource() {
        assertEquals("tambien ",apply("tambien ",lex(SpanishAccentIndex.empty(),Collections.emptySet()),Collections.emptySet()));
        LocalContextReviewer.Lexicon wrong=new BoundedDictionaryLexicon(new R29NeighborCorrectionTest.Lex(),w->w.equals("tambien"),()->false,w->"malicious");
        assertEquals("tambien ",apply("tambien ",wrong,Collections.emptySet()));
        LocalContextReviewer.Lexicon invalidTarget=new BoundedDictionaryLexicon(new LocalContextReviewer.Lexicon(){
            public boolean valid(String word){return false;}public List<String> candidates(String word){return Collections.emptyList();}
        },w->w.equals("tambien"),()->false,w->"también");
        assertEquals("tambien ",apply("tambien ",invalidTarget,Collections.emptySet()));
    }
    @Test public void corruptDuplicateAndOversizedAssetsFailClosed() throws Exception {
        for(String text:Arrays.asList("tambien\ttambién\ntambien\ttambién\n","termino\ttérmino\nBAD\n","hola\thólaa\n","abc\tábc\n")){
            try{SpanishAccentIndex.read(new StringReader(text));fail("Must reject malformed index");}catch(IOException expected){}
        }
        StringBuilder huge=new StringBuilder();for(int i=0;i<12001;i++){String tail=""+(char)('a'+i/676)+(char)('a'+i/26%26)+(char)('a'+i%26);huge.append("camino"+tail+"\tcámino"+tail+"\n");}
        try{SpanishAccentIndex.read(new StringReader(huge.toString()));fail();}catch(IOException expected){}
    }
}
