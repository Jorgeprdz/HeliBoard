// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class R241SafetyTest {
 private final LocalContextReviewer reviewer=new LocalContextReviewer();
 @Test public void initialProperNameDoesNotProduceContextualReplacement(){
  for(String text:Arrays.asList("Poryectos ","Poryectos es importante ","Edta trabaja "))
   assertTrue(text,reviewer.review(text,new R241WritingTest.Vocabulary(),Collections.emptySet(),()->false).isEmpty());
 }
 @Test public void ambiguousInitialCandidateRemainsSuggestion(){
  List<LocalContextReviewer.Correction> result=reviewer.review("Slll estoy escribiendo ",new R241WritingTest.Vocabulary(),Collections.emptySet(),()->false);
  assertEquals(1,result.size());assertEquals("Solo",result.get(0).replacement);assertFalse(result.get(0).automaticAllowed);
 }
 @Test public void rejectionsVetoSegmentationGrammarAndPunctuation(){
  for(String[] pair:new String[][]{{"nincuidando ","nincuidando→ni cuidando"},{"tener mad coherencia ","mad→más"},{"mensaje .","mensaje .→mensaje."},{"la la gramática ","la la→la"}})
   assertEquals(pair[0],R241WritingTest.apply(pair[0],Collections.singleton(pair[1])));
 }
 @Test public void protectedPersonalVocabularyCannotBeSplitOrCorrected(){
  LocalContextReviewer.Lexicon vocabulary=new R241WritingTest.Vocabulary(){
   @Override public boolean preserved(String word){return word.equals("nincuidando")||word.equals("mad");}
  };
  assertTrue(reviewer.review("nincuidando tener mad coherencia ",vocabulary,Collections.emptySet(),()->false).isEmpty());
 }
 @Test public void cancellationDiscardsEvenEarlierProposals(){
  AtomicInteger checks=new AtomicInteger();
  assertTrue(reviewer.review("poryectos nincuidando funcoona ",new R241WritingTest.Vocabulary(),Collections.emptySet(),
   ()->checks.incrementAndGet()>8).isEmpty());
 }
 @Test public void dictionaryFailureLeavesOriginalText(){
  LocalContextReviewer.Lexicon broken=new R241WritingTest.Vocabulary(){
   @Override public boolean valid(String word){throw new IllegalStateException("dictionary unavailable");}
  };
  assertTrue(reviewer.review("nincuidando ",broken,Collections.emptySet(),()->false).isEmpty());
 }
 @Test public void wholeDictionaryWordAndAmbiguousPartitionsStayIntact(){
  LocalContextReviewer.Lexicon vocabulary=new R241WritingTest.Vocabulary(){
   @Override public boolean valid(String word){return Arrays.asList("nicuidando","ab","abcd","cdef","ef").contains(word)||super.valid(word);}
  };
  assertNull(WordBoundarySegmenter.choose("nicuidando",vocabulary,()->false));
  assertNull(WordBoundarySegmenter.choose("abcdef",vocabulary,()->false));
 }
 @Test public void physicalSpaceGeometryIsRequiredForBoundaryNoise(){
  ObservedLexicon observed=new ObservedLexicon();
  Map<Character,double[]> positions=new HashMap<>();
  positions.put('b',new double[]{50,50,10,10});positions.put(' ',new double[]{50,60,50,10});
  positions.put('q',new double[]{0,0,10,10});observed.keyboard(positions);
  assertTrue(observed.snapshot().spaceNeighbor('b'));assertFalse(observed.snapshot().spaceNeighbor('q'));
 }
 @Test public void workerDictionaryCachesAndHonorsCancellation(){
  AtomicInteger calls=new AtomicInteger();boolean[] stop={false};
  BoundedDictionaryLexicon lexicon=new BoundedDictionaryLexicon(new R241WritingTest.Vocabulary(),
   word->{calls.incrementAndGet();return word.equals("cuidando");},()->stop[0]);
  assertTrue(lexicon.valid("cuidando"));assertTrue(lexicon.valid("CUIDANDO"));assertEquals(1,calls.get());
  assertTrue(lexicon.knownInvalid("nincuidando"));stop[0]=true;
  try{lexicon.valid("cuidando");fail("cancelled result must not be used");}catch(java.util.concurrent.CancellationException expected){}
 }
 @Test public void multiSentenceWhatsAppReviewChangesOnlySupportedSpans(){
  String input="Edta es una prueba de escritura ay validación de acentos, estoy escribiendo sin seguir un orden en particular nincuidando la manera de escribir. Slll estoybescribiendonde manera rápida como lo suelo hacer cuando envió un mensaje . en teoría la la gramática también se debería de corregir y debería tener mad coherencia lo que digo y escribo";
  String expected="Esta es una prueba de escritura y validación de acentos, estoy escribiendo sin seguir un orden en particular ni cuidando la manera de escribir. Slll estoy escribiendo de manera rápida como lo suelo hacer cuando envío un mensaje. en teoría la gramática también se debería de corregir y debería tener más coherencia lo que digo y escribo";
  // MAX_CORRECTIONS is a per-boundary cap. Multiple real boundaries converge without semantic rewriting.
  String result=input;
  for(int i=0;i<3;i++)result=R241WritingTest.apply(result,Collections.emptySet());
  assertEquals(expected,result);
 }
}
