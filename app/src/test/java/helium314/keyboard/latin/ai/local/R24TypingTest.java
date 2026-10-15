// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class R24TypingTest {
 private LocalContextReviewer.Lexicon lex(String typo,String... candidates){
  Set<String> valid=new HashSet<>(Arrays.asList(candidates));
  return new LocalContextReviewer.Lexicon(){
   public boolean valid(String w){return valid.contains(w);}
   public boolean knownInvalid(String w){return w.equals(typo);}
   public List<String> candidates(String w){return w.equals(typo)?Arrays.asList(candidates):Collections.emptyList();}
  };
 }
 private List<LocalContextReviewer.Correction> review(String text,LocalContextReviewer.Lexicon lex,Set<String> reject){
  return new LocalContextReviewer().review(text,lex,reject,()->false);
 }
 @Test public void balancedPolicyCorrectsTransposedAdjacentOmittedAndRepeatedLetters(){
  String[][] pairs={{"poryectos","proyectos"},{"funcoona","funciona"},{"torillas","tortillas"},{"helllo","hello"}};
  for(String[] p:pairs){
   List<LocalContextReviewer.Correction> r=review(p[0]+" ",lex(p[0],p[1]),Collections.emptySet());
   assertEquals(p[0],1,r.size());assertEquals(p[1],r.get(0).replacement);assertTrue(p[0],r.get(0).automaticAllowed);
  }
 }
 @Test public void longerMultiEditTypoIsOfferedWithoutPretendingItIsCertain(){
  List<LocalContextReviewer.Correction> r=review("profeskonistas ",lex("profeskonistas","profesionistas"),Collections.emptySet());
  assertEquals(1,r.size());assertEquals("profesionistas",r.get(0).replacement);
 }
 @Test public void geographicContextResolvesShortTypoButPreservesAcronyms(){
  assertTrue(review("Estado dw Mexico ",lex("dw","de"),Collections.emptySet()).get(0).automaticAllowed);
  assertFalse(review("dw ",lex("dw","de"),Collections.emptySet()).get(0).automaticAllowed);
  assertFalse(review("Estado. dw Mexico ",lex("dw","de"),Collections.emptySet()).get(0).automaticAllowed);
  assertTrue(review("DW ",lex("dw","de"),Collections.emptySet()).isEmpty());
 }
 @Test public void validWordsNumbersNegationNamesAndCodeRemainExact(){
  for(String s:new String[]{"2 cocas","No quiero 3, quiero 2","DW","Poryectos","https://x/poryectos","poryectos.js","`poryectos`"})
   assertTrue(s,review(s,lex("poryectos","proyectos"),Collections.emptySet()).isEmpty());
  assertTrue(review("casa ",lex("casa","casa","caza"),Collections.emptySet()).isEmpty());
 }
 @Test public void rejectionWinsOverFrequencyAndAdjacentCompetitorsStaySuggestions(){
  assertTrue(review("poryectos ",lex("poryectos","proyectos"),Collections.singleton("poryectos→proyectos")).isEmpty());
  assertFalse(review("casz ",lex("casz","casa","caza"),Collections.emptySet()).get(0).automaticAllowed);
  assertTrue(TypingCandidateRanker.editCost("funcoona","funciona",TypingCandidateRanker.QWERTY)<TypingCandidateRanker.editCost("funcoona","funcxona",TypingCandidateRanker.QWERTY));
 }
 @Test public void cancellationAndUnavailableLexiconFailClosed(){
  assertTrue(new LocalContextReviewer().review("poryectos ",lex("poryectos","proyectos"),Collections.emptySet(),()->true).isEmpty());
  LocalContextReviewer.Lexicon broken=new LocalContextReviewer.Lexicon(){public boolean valid(String w){throw new IllegalStateException();}public List<String> candidates(String w){return Collections.emptyList();}};
  assertTrue(review("poryectos ",broken,Collections.emptySet()).isEmpty());
 }
 @Test public void styleRetainsOnlyAllowlistedAggregateFeaturesAndRanksAcceptedConnectors(){
  Map<String,Long> traits=PersonalStyleProfile.features("Oye, además quería comentar. 🙂","es-MX");
  assertTrue(traits.containsKey("emoji"));assertTrue(traits.containsKey("register.casual"));assertTrue(traits.containsKey("connector.además"));
  assertTrue(traits.containsKey("emoji"));assertFalse(traits.toString().contains("quería"));
  assertTrue(PersonalStyleProfile.features("password ABCDEF123456","en-US").isEmpty());
  assertTrue(PersonalStyleProfile.features("contact alice@example.org","en-US").isEmpty());
  assertTrue(PersonalStyleProfile.preference("además","casual",traits)>PersonalStyleProfile.preference("asimismo","casual",traits));
  assertTrue(PersonalStyleProfile.preference("however","professional",Collections.emptyMap())>PersonalStyleProfile.preference("hey","professional",Collections.emptyMap()));
 }
}
