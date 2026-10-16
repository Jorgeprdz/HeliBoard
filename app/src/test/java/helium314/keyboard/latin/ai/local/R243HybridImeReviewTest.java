// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Real hybrid algorithm + bounded dictionary adapter; no device claims. */
public class R243HybridImeReviewTest {
 private static final List<String> NATIVE=Arrays.asList(
   "teclado","esta","estoy","de","rápida","escritura","foros","casa",
   "este","no","quiero","mensaje","archium","funciona","correctamente",
   "rápido","describiendo","manera","revisar","los","hola","un","si","el",
   "es","una","prueba","y","saber","mi","quiero");
 private static final class Dictionary implements LocalContextReviewer.Lexicon {
  final Set<String> valid=new HashSet<>(NATIVE);
  final Set<String> unknown=new HashSet<>();
  boolean cancelled=false;
  public boolean valid(String w){return valid.contains(w);}
  public boolean knownInvalid(String w){return !valid(w)&&!unknown.contains(w);}
  public boolean preserved(String w){return "archium".equals(w);}
  public List<String> candidates(String w){return Collections.emptyList();}
 }
 private List<LocalContextReviewer.Correction> check(String source, Dictionary d){
  return HybridImeReview.propose(source,NATIVE,d,Collections.emptySet(),()->d.cancelled);
 }
 private void protect(String source){
  Dictionary d=new Dictionary();
  for(LocalContextReviewer.Correction c:check(source,d))
   fail("Unexpected candidate "+c.original+" -> "+c.replacement+" for "+source);
 }
 @Test public void observedNativeCandidatesOfferManualMotorRepairOnly(){
  List<LocalContextReviewer.Correction> r=check("el tecmaod funciona",new Dictionary());
  assertTrue("transposition/neighbor candidate",r.stream().anyMatch(
      x->x.original.equals("tecmaod")&&x.replacement.equals("teclado")&&!x.automaticAllowed));
 }
 @Test public void threeGalaxySamplesAreEvaluatedWithoutFabricatingKeystrokes(){
  Dictionary d=new Dictionary();
  for(String sample:Arrays.asList(
   "Edtad es una prueba de escritura regaoufa y quiero saber si el tecmaod funciona correctamente",
   "Estly describiendo fe manera rápida y quiero revisar los foros de arxhium",
   "Hola 🫠 eTlybescribkendi un mensaje rápido")){
   for(LocalContextReviewer.Correction c:check(sample,d)){
    assertEquals(c.original,sample.substring(c.start,c.end));
    assertFalse(c.automaticAllowed);
    assertTrue(Character.isLetter(c.original.codePointAt(0)));
   }
  }
 }
 @Test public void controlsStayUnchanged(){
  for(String t:Arrays.asList("foros","casa","este teclado","no quiero","Archium","arxhium",
   "Hola 🫠 eTlybescribkendi un mensaje rápido"))protect(t);
 }
 @Test public void emojiOffsetsAreUtf16AndRemainComplete(){
  String s="Hola 🫠 tecmaod";
  for(LocalContextReviewer.Correction c:check(s,new Dictionary())){
   assertTrue(c.start>=s.indexOf("tecmaod"));
   assertEquals(c.original,s.substring(c.start,c.end));
  }
  assertEquals(2,"🫠".length());
 }
 @Test public void unknownAndMissingNativeEvidenceAbstain(){
  Dictionary d=new Dictionary();d.unknown.add("tecmaod");
  assertTrue(check("tecmaod",d).isEmpty());
  d.unknown.clear();d.valid.add("tecmaod");
  assertTrue(check("tecmaod",d).isEmpty());
  d.valid.remove("tecmaod");
  assertTrue(HybridImeReview.propose("tecmaod",Collections.emptyList(),d,Collections.emptySet(),()->false).isEmpty());
  assertTrue(HybridImeReview.propose("tecmaod",NATIVE,d,Collections.emptySet(),()->true).isEmpty());
 }
 @Test public void vetoAndSensitiveInputsAbstain(){
  Dictionary d=new Dictionary();
  assertTrue(HybridImeReview.propose("tecmaod",NATIVE,d,
      Collections.singleton("tecmaod→teclado"),()->false).isEmpty());
  for(String t:Arrays.asList("https://host/tecmaod","user@example.com","tel:525533445566"))
   assertTrue(check(t,d).isEmpty());
 }
 @Test public void noNewAutomaticReplacementEvenForClearTypos(){
  for(String text:Arrays.asList("tecmaod","regaoufa","fe","escritra","arxhium")){
   for(LocalContextReviewer.Correction c:check(text,new Dictionary()))
    assertFalse(c.automaticAllowed);
  }
 }
}
