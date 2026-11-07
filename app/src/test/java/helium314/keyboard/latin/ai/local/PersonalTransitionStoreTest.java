// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class PersonalTransitionStoreTest {
 private Context context;
 private PersonalTypingStore store;
 private static void closeStore() throws Exception {
  Field instance=PersonalTypingStore.class.getDeclaredField("instance");instance.setAccessible(true);
  Object old=instance.get(null);
  if(old!=null) {
   Field writer=PersonalTypingStore.class.getDeclaredField("writer");writer.setAccessible(true);
   ExecutorService executor=(ExecutorService)writer.get(old);
   executor.submit(()->{}).get(30,TimeUnit.SECONDS);executor.shutdownNow();
   Field database=PersonalTypingStore.class.getDeclaredField("database");database.setAccessible(true);
   android.database.sqlite.SQLiteOpenHelper helper=(android.database.sqlite.SQLiteOpenHelper)database.get(old);
   if(helper!=null)helper.close();
  }
  instance.set(null,null);
 }
 @Before public void setup()throws Exception {
  closeStore();context=ApplicationProvider.getApplicationContext();context.deleteDatabase("personal_typing_v1.db");
 }
 @After public void teardown()throws Exception {closeStore();}
 private void observe()throws Exception {
  store.getClass().getMethod("observeTransition",String.class,String.class,String.class).invoke(store,"buenos","días","es-MX");
 }
 @SuppressWarnings("unchecked") private List<String> predict()throws Exception {
  return (List<String>)store.getClass().getMethod("predict",String.class,String.class,int.class).invoke(store,"buenos","es-MX",3);
 }
 @Test public void transitionsSurviveRestartAndEraseClearsDiskAndMemory()throws Exception {
  store=PersonalTypingStore.get(context);store.exportData();
  for(int i=0;i<3;i++)observe();
  store.exportData();assertEquals(Collections.singletonList("días"),predict());
  closeStore();store=PersonalTypingStore.get(context);store.exportData();
  assertEquals(Collections.singletonList("días"),predict());
  // Local predictions deliberately stay out of the correction-only cloud backup schema.
  assertFalse(new String(store.exportData(),java.nio.charset.StandardCharsets.UTF_8).contains("buenos"));
  store.erase();assertTrue(predict().isEmpty());
  closeStore();store=PersonalTypingStore.get(context);store.exportData();assertTrue(predict().isEmpty());
 }
 @Test public void migrationFromVersionTwoPreservesCorrectionEvidence()throws Exception {
  SQLiteDatabase old=context.openOrCreateDatabase("personal_typing_v1.db",0,null);
  old.execSQL("CREATE TABLE evidence(error TEXT NOT NULL,correction TEXT NOT NULL,language TEXT NOT NULL,device TEXT NOT NULL,accepts INTEGER NOT NULL,rejects INTEGER NOT NULL,frequency INTEGER NOT NULL,confidence REAL NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(error,correction,language,device))");
  old.execSQL("CREATE TABLE style(feature TEXT PRIMARY KEY NOT NULL,count INTEGER NOT NULL)");
  old.execSQL("CREATE TABLE vocabulary(word TEXT NOT NULL,language TEXT NOT NULL,PRIMARY KEY(word,language))");
  old.execSQL("INSERT INTO evidence VALUES('teh','the','en-US','00000000-0000-0000-0000-000000000000',5,0,5,.99,1)");
  old.setVersion(2);old.close();
  store=PersonalTypingStore.get(context);store.exportData();
  assertEquals("the",store.automatic("teh","en-US",false));
  for(int i=0;i<3;i++)observe();store.exportData();assertEquals(Collections.singletonList("días"),predict());
 }
}
