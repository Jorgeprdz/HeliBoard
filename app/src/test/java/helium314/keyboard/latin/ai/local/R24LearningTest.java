// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.*;import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class R24LearningTest {
 private PersonalTypingStore model;
 private Context context;
 @Before public void reset()throws Exception{
  clearSingleton();context=ApplicationProvider.getApplicationContext();
  context.deleteDatabase("personal_typing_v1.db");
 }
 private static void clearSingleton()throws Exception{
  Field f=PersonalTypingStore.class.getDeclaredField("instance");f.setAccessible(true);
  Object old=f.get(null);
  if(old!=null){
   Field writer=PersonalTypingStore.class.getDeclaredField("writer");writer.setAccessible(true);
   ExecutorService executor=(ExecutorService)writer.get(old);executor.submit(()->{}).get(30,TimeUnit.SECONDS);executor.shutdownNow();
   Field db=PersonalTypingStore.class.getDeclaredField("database");db.setAccessible(true);
   android.database.sqlite.SQLiteOpenHelper helper=(android.database.sqlite.SQLiteOpenHelper)db.get(old);if(helper!=null)helper.close();
  }f.set(null,null);
 }
 @After public void close()throws Exception{clearSingleton();}
 @Test public void additiveMigrationKeepsR23EvidenceAndCreatesEmptyOptionalProfile()throws Exception{
  SQLiteDatabase old=context.openOrCreateDatabase("personal_typing_v1.db",0,null);
  old.execSQL("CREATE TABLE evidence(error TEXT NOT NULL,correction TEXT NOT NULL,language TEXT NOT NULL,device TEXT NOT NULL,accepts INTEGER NOT NULL,rejects INTEGER NOT NULL,frequency INTEGER NOT NULL,confidence REAL NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(error,correction,language,device))");
  old.execSQL("INSERT INTO evidence VALUES('teh','the','en-US','00000000-0000-0000-0000-000000000000',5,0,5,.99,1)");
  old.setVersion(1);old.close();
  model=PersonalTypingStore.get(context);model.exportData();
  assertEquals("the",model.automatic("teh","en-US",false));assertTrue(model.styleSnapshot().isEmpty());
  assertFalse(model.keepWord("HeliBoard","en-US")); // Mixed caps are deliberately not persisted.
  assertTrue(model.keepWord("Kotlin","en-US"));assertTrue(model.protectedWord("kotlin"));
  clearSingleton();model=PersonalTypingStore.get(context);model.exportData();
  assertTrue(model.protectedWord("Kotlin"));assertEquals("the",model.automatic("teh","en-US",false));
 }
 @Test public void aggregatesPersistButProfileDeletionPreservesCorrectionsAndVocabulary()throws Exception{
  model=PersonalTypingStore.get(context);model.exportData();model.erase();
  for(int i=0;i<5;i++)model.accept("teh","the","en-US");
  assertTrue(model.keepWord("Kotlin","en-US"));
  model.observeStyle("Oye, además quiero comentar.","es-MX");model.exportData();
  assertEquals(Long.valueOf(1),model.styleSnapshot().get("register.casual"));
  clearSingleton();model=PersonalTypingStore.get(context);model.exportData();
  assertEquals(Long.valueOf(1),model.styleSnapshot().get("connector.además"));
  model.eraseStyle();model.exportData();
  assertTrue(model.styleSnapshot().isEmpty());assertTrue(model.protectedWord("Kotlin"));assertEquals("the",model.automatic("teh","en-US",false));
  assertFalse(new String(model.exportData(),"UTF-8").contains("connector."));
 }
 @Test public void vocabularyAndStyleRejectSecretsAndNeverStoreSentenceText()throws Exception{
  model=PersonalTypingStore.get(context);model.exportData();model.erase();
  assertFalse(model.keepWord("password","en-US"));assertFalse(model.keepWord("two words","en-US"));
  model.observeStyle("password abcdef123456","en-US");
  model.observeStyle("mail person@example.org","en-US");model.exportData();
  assertTrue(model.styleSnapshot().isEmpty());
 }
}
