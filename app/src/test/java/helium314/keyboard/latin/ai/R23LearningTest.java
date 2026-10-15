package helium314.keyboard.latin.ai;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class R23LearningTest {
 private Object store() throws Exception {
  return Class.forName("helium314.keyboard.latin.ai.local.PersonalTypingStore")
   .getMethod("get",Context.class).invoke(null,ApplicationProvider.getApplicationContext());
 }
 private void accept(Object s,String a,String b,String language) throws Exception {
  s.getClass().getMethod("accept",String.class,String.class,String.class).invoke(s,a,b,language);
 }
 private String candidate(Object s,String a,String lang,boolean valid) throws Exception {
  return (String)s.getClass().getMethod("automatic",String.class,String.class,boolean.class).invoke(s,a,lang,valid);
 }
 @Before public void clear() throws Exception {Object s=store();s.getClass().getMethod("erase").invoke(s);}
 @Test public void learnsRepeatedExplicitPairsAndRejectsImmediately() throws Exception {
  Object s=store();assertNull(candidate(s,"siemore","es-MX",false));
  accept(s,"siemore","siempre","es-MX");assertNull(candidate(s,"siemore","es-MX",false));
  for(int i=0;i<4;i++)accept(s,"siemore","siempre","es-MX");
  assertEquals("siempre",candidate(s,"siemore","es-MX",false));
  s.getClass().getMethod("reject",String.class,String.class,String.class).invoke(s,"siemore","siempre","es-MX");
  assertNull(candidate(s,"siemore","es-MX",false));
 }
 @Test public void protectsLegitimateWordsSensitiveTokensCaseAndOtherLanguage() throws Exception {
  Object s=store();
  for(int i=0;i<8;i++){accept(s,"cotisacion","cotización","es-MX");accept(s,"teh","the","en-US");}
  assertEquals("cotización",candidate(s,"cotisacion","es-MX",false));
  assertEquals("the",candidate(s,"teh","en-US",false));
  assertNull(candidate(s,"teh","es-MX",false));
  assertNull(candidate(s,"cotisacion","es-MX",true));
  assertNull(candidate(s,"Cotisacion","es-MX",false));
  for(String word:new String[]{"no","https://teh.com","a@b.com","123456","foo_bar","USD500"})
   assertNull(candidate(s,word,"es-MX",false));
 }
 @Test public void ambiguousAlternativesNeverAutomaticallyReplace() throws Exception {
  Object s=store();for(int i=0;i<5;i++){accept(s,"sime","same","en-US");accept(s,"sime","some","en-US");}
  assertNull(candidate(s,"sime","en-US",false));
 }
 @Test public void persistentSnapshotReopensAndMergesWithoutDuplicatingEvidence() throws Exception {
  Object s=store();for(int i=0;i<5;i++)accept(s,"impeemtar","implementar","es-MX");
  byte[] exported=(byte[])s.getClass().getMethod("exportData").invoke(s);
  s.getClass().getMethod("erase").invoke(s);
  s.getClass().getMethod("merge",byte[].class).invoke(s,(Object)exported);
  s.getClass().getMethod("merge",byte[].class).invoke(s,(Object)exported);
  assertEquals("implementar",candidate(s,"impeemtar","es-MX",false));
  assertArrayEquals(exported,(byte[])s.getClass().getMethod("exportData").invoke(s));
 }
}
