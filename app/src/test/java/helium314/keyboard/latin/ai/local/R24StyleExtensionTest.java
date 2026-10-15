// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;import java.util.*;
import static org.junit.Assert.*;
/** Component tests only. These exercise real aggregate functions, not an LLM or mock generation. */
public class R24StyleExtensionTest {
 @Test public void openingsAreLearnedOnlyAtActualMessageStart(){
  assertTrue(PersonalStyleProfile.features("Oye, quería comentar.","es-MX",true).containsKey("opening.casual"));
  assertFalse(PersonalStyleProfile.features("Oye, quería comentar.","es-MX",false).containsKey("opening.casual"));
  assertFalse(PersonalStyleProfile.features("Quería comentar, oye.","es-MX",true).containsKey("opening.casual"));
  assertTrue(PersonalStyleProfile.features("Estimado equipo.","es-MX",true).containsKey("opening.professional"));
 }
 @Test public void explicitCurrentRegisterWinsOverContraryPersonalHistory(){
  Map<String,Long> history=new HashMap<>();history.put("register.professional",100L);
  assertEquals("casual",PersonalStyleProfile.preferredRegister("Oye, quería comentar",history));
  history.put("register.casual",1000L);
  assertEquals("professional",PersonalStyleProfile.preferredRegister("Estimado equipo",history));
 }
 @Test public void neutralContextUsesOnlySufficientDecisiveAggregateEvidence(){
  Map<String,Long> history=new HashMap<>();
  assertEquals("",PersonalStyleProfile.preferredRegister("Quería comentar",history));
  history.put("register.professional",4L);assertEquals("",PersonalStyleProfile.preferredRegister("Quería comentar",history));
  history.put("register.professional",5L);assertEquals("professional",PersonalStyleProfile.preferredRegister("Quería comentar",history));
  history.put("register.casual",4L);assertEquals("",PersonalStyleProfile.preferredRegister("Quería comentar",history));
 }
 @Test public void learnedOpeningsChangeRankingWithoutPersistingAnOpeningPhrase(){
  Map<String,Long> history=new HashMap<>();history.put("opening.casual",8L);
  assertTrue(PersonalStyleProfile.preference("hola","",history,true)>PersonalStyleProfile.preference("hola","",history,false));
  assertEquals(0,PersonalStyleProfile.preference("hola","professional",history,true)-PersonalStyleProfile.preference("hola","professional",history,false),.0001);
  assertFalse(history.toString().contains("Oye, quería"));
 }
 @Test public void syntaxPunctuationAndMixedLanguageAreOnlyAllowlistedCounters(){
  Map<String,Long> features=PersonalStyleProfile.features("Oye, the build y el cambio porque quiero preguntar...?","es-MX",true);
  for(String key:Arrays.asList("clause.coordination","clause.subordination","ending.question","language.mixed"))assertTrue(key,features.containsKey(key));
  assertTrue(PersonalStyleProfile.features("Quería comentar...","es-MX",false).containsKey("ending.ellipsis"));
  for(String key:features.keySet())assertTrue(PersonalStyleProfile.KEYS.contains(key));
  assertFalse(features.toString().contains("build"));assertFalse(features.toString().contains("preguntar"));
 }
 @Test public void secretsAddressesAndOversizedContextCannotContributeFeatures(){
  for(String text:Arrays.asList("password mysecret","contact user@example.org","cuenta 123456",String.join("",Collections.nCopies(513,"a"))))
   assertTrue(PersonalStyleProfile.features(text,"es-MX",true).isEmpty());
 }
}
