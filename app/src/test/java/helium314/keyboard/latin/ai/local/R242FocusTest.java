// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
/** Scope component tests only. Real model output is evaluated by the cloud native decoder. */
public class R242FocusTest {
 private LocalContextReviewer.Lexicon lexicon(){
  Set<String> valid=new HashSet<>(Arrays.asList("vamos","a","hacer","prueba","de","escritura","para","saber","si","ya","mi","estoy","el","texto","no","quiero","hola","works","the","is","build"));
  return new LocalContextReviewer.Lexicon(){
   public boolean valid(String word){return valid.contains(word);}
   public boolean knownInvalid(String word){return !valid(word);}
   public List<String> candidates(String word){return Collections.emptyList();}
  };
 }
 @Test public void scopeContainsOnlyNonwordAndContextIsReadOnly(){
  String text="vamos a saber sibya mi escritura";
  List<CorrectionFocus> targets=CorrectionFocus.targets(text,lexicon(),()->false);
  assertEquals(1,targets.size());CorrectionFocus target=targets.get(0);
  assertEquals("sibya",text.substring(target.start,target.end));
  assertTrue(CorrectionFocus.prompt(text,target.original,"casual").contains("TARGET: sibya"));
 }
 @Test public void namesAcronymsNumbersUrlsCommandsAndValidTermsAreExcluded(){
  for(String text:Arrays.asList("Archium DW O’Neil Ana-María","\"texot\"","No quiero 3 2","https://host/noisy","echo noisy > file","hola works the build"))
   assertTrue(text,CorrectionFocus.targets(text,lexicon(),()->false).isEmpty());
 }
 @Test public void nativeConfirmedValidityOverridesStaleNegativeObservation(){
  ObservedLexicon observations=new ObservedLexicon();observations.record("texot",false,Collections.emptyList(),true);
  // A positive result from the current configured dictionaries must not be masked
  // by a historical native observation, e.g. after switching language/dictionaries.
  BoundedDictionaryLexicon live=new BoundedDictionaryLexicon(observations.snapshot(),word->true,()->false);
  assertTrue(live.valid("texot"));assertFalse(live.knownInvalid("texot"));assertTrue(live.valid("texto"));
  assertTrue(CorrectionFocus.targets("el texot",live,()->false).isEmpty());
 }
 @Test public void currentNativeInvalidProofKeepsTypoCorrectable(){
  ObservedLexicon observations=new ObservedLexicon();observations.record("texot",false,Collections.emptyList(),true);
  BoundedDictionaryLexicon nativeInvalid=new BoundedDictionaryLexicon(observations.snapshot(),word->!word.equals("texot"),()->false);
  assertFalse(nativeInvalid.valid("texot"));assertTrue(nativeInvalid.knownInvalid("texot"));
  assertTrue(nativeInvalid.valid("texto"));
  assertEquals(1,CorrectionFocus.targets("el texot",nativeInvalid,()->false).size());
 }
 @Test public void explicitPersonalVocabularyPreventsFalseInvalidAndSkipsNativeLookup(){
  java.util.concurrent.atomic.AtomicInteger lookups=new java.util.concurrent.atomic.AtomicInteger();
  LocalContextReviewer.Lexicon personal=new LocalContextReviewer.Lexicon(){
   public boolean valid(String word){return false;}
   public boolean preserved(String word){return "texot".equals(word);}
   public List<String> candidates(String word){return Collections.emptyList();}
  };
  BoundedDictionaryLexicon lex=new BoundedDictionaryLexicon(personal,word->{lookups.incrementAndGet();return false;},()->false);
  assertTrue(lex.valid("texot"));assertFalse(lex.knownInvalid("texot"));
  assertEquals(0,lookups.get());
 }
 @Test public void unavailableDictionaryDoesNotTurnUnknownIntoInvalidEvidence(){
  BoundedDictionaryLexicon lex=new BoundedDictionaryLexicon(lexicon(),word->{throw new IllegalStateException("dictionary unavailable");},()->false);
  try {lex.knownInvalid("texot");fail("unavailable dictionary must not be a negative verdict");}
  catch(IllegalStateException expected){}
 }
 @Test public void independentTyposProduceSeparateScopesAndCancellationDropsAll(){
  assertEquals(2,CorrectionFocus.targets("el texot is buidl",lexicon(),()->false).size());
  assertTrue(CorrectionFocus.targets("texot buidl",lexicon(),()->true).isEmpty());
 }
}
