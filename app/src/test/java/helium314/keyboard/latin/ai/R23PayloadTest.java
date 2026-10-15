package helium314.keyboard.latin.ai;
import android.content.Context;import org.junit.Test;import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;import androidx.test.core.app.ApplicationProvider;
import helium314.keyboard.latin.ai.cloud.BackupPayload;
import helium314.keyboard.latin.ai.local.PersonalTypingStore;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class R23PayloadTest {
 @Test public void schemaExportNeverIncludesApiKeysClipboardOrUnlistedPreferences()throws Exception{
  Context c=ApplicationProvider.getApplicationContext();
  helium314.keyboard.latin.utils.DeviceProtectedUtils.getSharedPreferences(c).edit()
   .putString("gemini_api_key","SECRET_KEY").putString("clipboard_history","PRIVATE_CONVERSATION")
   .putString("theme_style","material").commit();
  PersonalTypingStore store=PersonalTypingStore.get(c);store.erase();
  String exported=new String(BackupPayload.export(c,store),"UTF-8");
  assertFalse(exported.contains("SECRET_KEY"));assertFalse(exported.contains("PRIVATE_CONVERSATION"));
  org.json.JSONObject root=new org.json.JSONObject(exported);assertEquals("material",root.getJSONObject("visual").getString("theme_style"));
  BackupPayload.validate(exported.getBytes("UTF-8"),store);
 }
 @Test public void incompatibleVisualTypesAndFutureVersionsCannotMutateLocalData()throws Exception{
  Context c=ApplicationProvider.getApplicationContext();PersonalTypingStore store=PersonalTypingStore.get(c);store.erase();
  for(int i=0;i<5;i++)store.accept("teh","the","en-US");
  byte[] before=store.exportData();
  org.json.JSONObject bad=new org.json.JSONObject().put("schema",2)
   .put("learning",new org.json.JSONObject().put("schema",1).put("rows",new org.json.JSONArray()))
   .put("dictionary",new org.json.JSONArray()).put("updated",1)
   .put("visual",new org.json.JSONObject().put("theme_key_borders","not-a-boolean"));
  try{BackupPayload.validate(bad.toString().getBytes("UTF-8"),store);fail("Incompatible preference accepted");}
  catch(IllegalArgumentException expected){}
  assertArrayEquals(before,store.exportData());
  bad.put("schema",99);try{BackupPayload.validate(bad.toString().getBytes("UTF-8"),store);fail("Future schema accepted");}
  catch(IllegalArgumentException expected){}
  BackupPayload.validate(before,store);
 }
}
