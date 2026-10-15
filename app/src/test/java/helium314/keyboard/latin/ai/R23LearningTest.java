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
 @Test public void deletionFencesAlreadyDownloadedCloudSnapshot() throws Exception {
  Object s=store();for(int i=0;i<5;i++)accept(s,"teh","the","en-US");
  byte[] old=(byte[])s.getClass().getMethod("exportData").invoke(s);
  long generation=(long)s.getClass().getMethod("generation").invoke(s);
  s.getClass().getMethod("erase").invoke(s);
  try{s.getClass().getMethod("merge",byte[].class,long.class).invoke(s,old,generation);fail("Old cloud import repopulated erased data");}
  catch(java.lang.reflect.InvocationTargetException expected){}
  assertNull(candidate(s,"teh","en-US",false));
 }
 @Test public void actualSqliteReopenRetainsAcceptedPair() throws Exception {
  Object s=store();for(int i=0;i<5;i++)accept(s,"siemore","siempre","es-MX");
  byte[] persisted=(byte[])s.getClass().getMethod("exportData").invoke(s);
  java.lang.reflect.Constructor<?> constructor=s.getClass().getDeclaredConstructor(Context.class);constructor.setAccessible(true);
  Object reopened=constructor.newInstance(ApplicationProvider.getApplicationContext());
  assertEquals(new String(persisted,"UTF-8"),new String((byte[])reopened.getClass().getMethod("exportData").invoke(reopened),"UTF-8"));
  assertEquals("siempre",candidate(reopened,"siemore","es-MX",false));
 }
 @Test public void corruptOrFutureSchemaLeavesOriginalEvidenceUnchanged() throws Exception {
  Object s=store();for(int i=0;i<5;i++)accept(s,"teh","the","en-US");
  for(String bad:new String[]{"{","{\"schema\":99,\"rows\":[]}","{\"schema\":1,\"rows\":[{\"error\":\"password\"}]}"}){
   try{s.getClass().getMethod("merge",byte[].class).invoke(s,(Object)bad.getBytes("UTF-8"));fail("Invalid snapshot accepted");}
   catch(java.lang.reflect.InvocationTargetException expected){}
  }
  assertEquals("the",candidate(s,"teh","en-US",false));
 }

 @Test public void directBootRecoversLearningAfterUnlockWithoutProcessRestart() throws Exception {
  Context app=ApplicationProvider.getApplicationContext();
  android.os.UserManager user=(android.os.UserManager)app.getSystemService(Context.USER_SERVICE);
  org.robolectric.Shadows.shadowOf(user).setUserUnlocked(false);
  java.lang.reflect.Constructor<?> constructor=store().getClass().getDeclaredConstructor(Context.class);constructor.setAccessible(true);
  Object deferred=constructor.newInstance(app);
  deferred.getClass().getMethod("exportData").invoke(deferred);
  org.robolectric.Shadows.shadowOf(user).setUserUnlocked(true);
  candidate(deferred,"teh","en-US",false);
  deferred.getClass().getMethod("exportData").invoke(deferred);
  for(int n=0;n<5;n++)accept(deferred,"teh","the","en-US");
  assertEquals("the",candidate(deferred,"teh","en-US",false));
 }

}
