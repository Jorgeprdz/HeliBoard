package helium314.keyboard.latin.ai;
import android.content.Context;
import android.content.Intent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AiSettingsUiTest {
 @Test public void invalidPasteNeverOverwritesSavedKeyOrEnablesNetwork(){
  Context app=RuntimeEnvironment.getApplication();app.getSharedPreferences("keyboard_ai_private",0).edit().clear().putString("key","existing-encrypted").commit();
  org.robolectric.android.controller.ActivityController<AiSettingsActivity> controller=Robolectric.buildActivity(AiSettingsActivity.class).setup();
  try{
   ArrayList<String> states=new ArrayList<>();
   controller.get().checkConnection("\"not a key\"","",false,states::add);
   assertEquals("existing-encrypted",app.getSharedPreferences("keyboard_ai_private",0).getString("key",""));
   assertFalse(AiSettingsActivity.automaticNetworkAllowed(app));assertFalse(AiSettingsActivity.networkAllowed(app));assertFalse(AiSettingsActivity.voiceNetworkAllowed(app));
   assertFalse(states.isEmpty());assertFalse(states.get(states.size()-1).contains("not a key"));
  }finally{controller.pause().stop().destroy();}
 }
 @Test public void openingEverySectionPreservesSavedDataAndSeparateConsents(){
  Context app=RuntimeEnvironment.getApplication();
  android.content.SharedPreferences prefs=app.getSharedPreferences("keyboard_ai_private",0);
  prefs.edit().clear().putString("key","existing-encrypted").putString("model","existing-model")
   .putBoolean("automatic_gemini",true).putBoolean("network_consent",false)
   .putBoolean("voice_network_consent",false).putString("dictation_mode","literal").commit();
  Map<String,?> before=new HashMap<>(prefs.getAll());
  for(String section:new String[]{"gemini","advanced","voice","privacy"}){
   Intent intent=new Intent(app,AiSettingsActivity.class).putExtra("section",section);
   org.robolectric.android.controller.ActivityController<AiSettingsActivity> controller=Robolectric.buildActivity(AiSettingsActivity.class,intent).setup();
   try{assertEquals(before,prefs.getAll());assertFalse(AiSettingsActivity.networkAllowed(app));assertFalse(AiSettingsActivity.voiceNetworkAllowed(app));}
   finally{controller.pause().stop().destroy();}
  }
 }
}
