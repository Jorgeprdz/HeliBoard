// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Small real-message regression fixtures, NOT a statistically representative corpus. */
public final class R25ContextReviewTest {
 private static final Set<String> WORDS=new HashSet<>(Arrays.asList(
   ("este teclado funciona en casa hola esto y estoy escribiendo rápido "+
    "juan salto saltó por la ventana seguimos naciendo haciendo pruebas "+
    "aun aún tiene tienen sentido de manera los foros quiero no "+
    "fe archium una palabra").split(" ")));
 private static final class Lex implements LocalContextReviewer.Lexicon {
  final Set<String> unknown=new HashSet<>();
  public boolean valid(String word){return WORDS.contains(word);}
  public boolean knownInvalid(String word){return !valid(word)&&!unknown.contains(word);}
  public List<String> candidates(String word){return Collections.emptyList();}
  public boolean preserved(String word){return "archium".equals(word);}
 }
 private static List<LocalContextReviewer.Correction> context(String text,Lex lex){
  return new LocalContextReviewer().review(text,lex,Collections.emptySet(),()->false);
 }
 private static void manual(String text,String source,String target){
  List<LocalContextReviewer.Correction> found=context(text,new Lex());
  assertTrue("Not proposed: "+source+" → "+target+" in "+found,
    found.stream().anyMatch(c->c.original.equals(source)&&c.replacement.equals(target)
      &&!c.automaticAllowed&&text.substring(c.start,c.end).equals(c.original)));
 }
 @Test public void gluedWordNeedsRealDictionaryAndNeverAutoCorrectsPrematurely(){
  manual("Este tecladonfunciona en casa","tecladonfunciona","teclado funciona");
  Lex lex=new Lex();lex.unknown.add("tecladonfunciona");
  assertTrue(context("Este tecladonfunciona en casa",lex).isEmpty());
 }
 @Test public void validWordsCanHaveWrongBoundaryButMustRemainManual(){
  manual("Hola esto y escribiendo rápido","esto y","estoy");
  assertTrue(context("Hola estoy escribiendo rápido",new Lex()).isEmpty());
 }
 @Test public void properNamePastTenseAccentRequiresExplicitChoice(){
  manual("Juan salto por la ventana","salto","saltó");
  assertTrue(context("yo salto por la ventana",new Lex()).stream()
    .noneMatch(c->c.replacement.equals("saltó")));
 }
 @Test public void semanticVerbSubstitutionStaysManual(){
  manual("Seguimos naciendo pruebas","naciendo","haciendo");
  assertTrue(context("Seguimos naciendo",new Lex()).isEmpty());
 }
 @Test public void ambiguousAunOffersAccentAndSegmentationSeparately(){
  manual("Aun tienensentido","Aun","Aún");
  assertTrue(context("Aun tienensentido",new Lex()).stream()
    .noneMatch(c->c.automaticAllowed));
 }
 @Test public void dictionaryRecoveryWorksWithoutPriorStripObservations(){
  Lex lex=new Lex();
  List<LocalContextReviewer.Correction> result=HybridImeReview.propose("fe",
      Collections.emptyList(),lex,Collections.emptySet(),()->false);
  assertTrue("Expected dictionary-verified nearby-key choice, not an invented word: "+result,
    result.stream().anyMatch(c->c.original.equals("fe")&&c.replacement.equals("de")
         &&!c.automaticAllowed));
 }
 @Test public void negativeControlsAndEmojiRemainUntouched(){
  Lex lex=new Lex();
  for(String text:Arrays.asList("los foros","en casa","este teclado",
      "no quiero","Archium","Hola 🫠 este teclado")){
   assertTrue("Context should abstain: "+text,context(text,lex).isEmpty());
   assertTrue("Hybrid should abstain: "+text,
       HybridImeReview.propose(text,Collections.emptyList(),lex,Collections.emptySet(),()->false).isEmpty());
  }
 }
 @Test public void offsetAndCancellationSafety(){
  String text="🫠 Este tecladonfunciona en casa";
  for(LocalContextReviewer.Correction c:context(text,new Lex())){
   assertTrue(c.start>=2);
   assertEquals(c.original,text.substring(c.start,c.end));
   assertFalse(c.automaticAllowed);
  }
  assertTrue(HybridImeReview.propose("fe",Collections.emptyList(),new Lex(),
     Collections.emptySet(),()->true).isEmpty());
  Lex lex=new Lex();lex.unknown.add("fe");
  assertTrue(HybridImeReview.propose("fe",Collections.emptyList(),lex,
     Collections.emptySet(),()->false).isEmpty());
 }
 @Test public void boundedMicroMeasurementIsNotGeneralAccuracyEvidence(){
  final String[] cases={"Este tecladonfunciona en casa","Hola esto y escribiendo rápido",
    "Juan salto por la ventana","Seguimos naciendo pruebas","Aun tienensentido"};
  final String[] expected={"teclado funciona","estoy","saltó","haciendo","Aún"};
  int hits=0,atTop=0; long[] ns=new long[cases.length*12];int n=0;
  Runtime runtime=Runtime.getRuntime();
  long heapBefore=runtime.totalMemory()-runtime.freeMemory();
  for(int i=0;i<cases.length;i++){
   List<LocalContextReviewer.Correction> proposals=context(cases[i],new Lex());
   final String match=expected[i];
   if(proposals.stream().anyMatch(c->c.replacement.equals(match)))hits++;
   if(!proposals.isEmpty()&&proposals.get(0).replacement.equals(expected[i]))atTop++;
   for(int run=0;run<12;run++){
    long t=System.nanoTime();context(cases[i],new Lex());
    ns[n++]=System.nanoTime()-t;
   }
  }
  Arrays.sort(ns);long heapAfter=runtime.totalMemory()-runtime.freeMemory();
  System.out.println("R25_FIXTURE_ONLY: recall="+hits+"/"+cases.length+
    " top1="+atTop+"/"+cases.length+" latencyP50us="+ns[ns.length/2]/1000+
    " latencyP95us="+ns[(int)(ns.length*.95)]/1000+
    " heapDeltaBytes="+(heapAfter-heapBefore)+" (no device inference)");
  assertEquals("Real sample proposals",cases.length,hits);
 }
}
