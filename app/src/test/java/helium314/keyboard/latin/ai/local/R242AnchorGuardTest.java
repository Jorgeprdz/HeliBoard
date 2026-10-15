// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
/** Component safety tests. Real model execution is separately reported by CI, never mocked here. */
public class R242AnchorGuardTest {
 private final Object editor=new Object();
 @Test public void appendTypingPreservesDependencyButMutationFocusAndSelectionDoNot(){
  ContinuousReviewAnchor a=new ContinuousReviewAnchor(editor,"app",7,3,0,"nincuidando ");
  assertTrue(a.matches(editor,"app",7,3,16,16,"nincuidando más "));
  assertFalse(a.matches(editor,"app",7,3,16,16,"micuidando más  "));
  assertFalse(a.matches(editor,"app",7,4,16,16,"nincuidando más "));
  assertFalse(a.matches(new Object(),"app",7,3,16,16,"nincuidando más "));
  assertFalse(a.matches(editor,"other",7,3,16,16,"nincuidando más "));
  assertFalse(a.matches(editor,"app",8,3,16,16,"nincuidando más "));
  assertFalse(a.matches(editor,"app",7,3,0,16,"nincuidando más "));
 }
 @Test public void windowAnchorNeverFuzzyMatchesOrUsesRepeatedLaterText(){
  ContinuousReviewAnchor a=new ContinuousReviewAnchor(editor,"app",0,1,10,"hola ");
  assertTrue(a.matches(editor,"app",0,1,19,19,"0123456789hola más "));
  assertFalse(a.matches(editor,"app",0,1,24,24,"0123456789adiós hola más "));
  assertTrue(a.matches(editor,"app",0,1,19,19,"hola más ")); // Complete anchor in a bounded window.
  assertFalse(a.matches(editor,"app",0,1,19,19,"ola más ")); // Anchor itself is truncated.
 }
 private LocalContextReviewer.Lexicon vocabulary(){
  final Set<String> words=new HashSet<>(Arrays.asList("entonces","vamos","a","hacer","si","ya","mi","escritura","estoy","tecleando","no","quiero","revisa","los","prs","de","archium","envió","envío","yo","mensaje","la","gramática"));
  return new LocalContextReviewer.Lexicon(){
   public boolean valid(String word){return words.contains(word.toLowerCase(Locale.ROOT));}
   public boolean knownInvalid(String word){return !valid(word);}
   public List<String> candidates(String word){return Collections.emptyList();}
  };
 }
 private List<LocalContextReviewer.Correction> guarded(String a,String b){
  return SemanticEditGuard.proposals(a,b,vocabulary(),Collections.emptySet(),()->false);
 }
 @Test public void realNonwordRepairBecomesBoundedOneActionProposalNeverAutomatic(){
  List<LocalContextReviewer.Correction> result=guarded("vamos sibya ","vamos si ya ");
  assertFalse(result.isEmpty());assertEquals("sibya",result.get(0).original);assertEquals("si ya",result.get(0).replacement);
  assertFalse(result.get(0).automaticAllowed);assertEquals(0,result.get(0).confidence,0);
 }
 @Test public void modelCannotChangeQuantitiesNegationNamesOrCreateCommitments(){
  for(String[] pair:new String[][]{
   {"No quiero 3, quiero 2.","Quiero 3, quiero 2."},
   {"No quiero 3, quiero 2.","No quiero 2, quiero 3."},
   {"Revisa los PRs de Archium.","Revisa los PRs de Archivo."},
   {"Archium funciona.","Archivo funciona."},
   {"O’Neil funciona.","O’Neal funciona."},
   {"Ana-María funciona.","Ana-Marina funciona."},
   {"I might call tomorrow.","I will call tomorrow."},
   {"https://host/sibya","https://host/siya"},
   {"echo sibya > file","echo si ya > file"}})assertTrue(Arrays.toString(pair),guarded(pair[0],pair[1]).isEmpty());
 }
 @Test public void outputCannotReplaceWholeMessageOrInsertUnprovidedFacts(){
  assertTrue(guarded("vamos ","vamos a hacer un mensaje mañana ").isEmpty());
  assertTrue(guarded("yo envió un mensaje ","yo envío un mensaje a Juan a las 9 ").isEmpty());
 }
 @Test public void cancellationAndVetoDiscardModelProposals(){
  assertTrue(SemanticEditGuard.proposals("vamos sibya ","vamos si ya ",vocabulary(),
    Collections.singleton("sibya→si ya"),()->false).isEmpty());
  assertTrue(SemanticEditGuard.proposals("vamos sibya ","vamos si ya ",vocabulary(),Collections.emptySet(),()->true).isEmpty());
 }
 @Test public void validLexicalMeaningIsNotChangedByModelPopularity(){
  assertTrue(guarded("mi escritura ","la gramática ").isEmpty());
  List<LocalContextReviewer.Correction> result=guarded("yo envió ","yo envío ");
  assertFalse(result.isEmpty());assertFalse(result.get(0).automaticAllowed);
 }
}
