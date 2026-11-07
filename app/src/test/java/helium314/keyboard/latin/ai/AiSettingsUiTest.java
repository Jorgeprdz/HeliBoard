package helium314.keyboard.latin.ai;
import android.content.Context;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.junit.Test;import org.junit.runner.RunWith;
import org.robolectric.Robolectric;import org.robolectric.RuntimeEnvironment;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AiSettingsUiTest {
 @Test public void invalidPasteNeverOverwritesSavedKeyOrEnablesNetwork(){
  Context app=RuntimeEnvironment.getApplication();app.getSharedPreferences("keyboard_ai_private",0).edit().clear().putString("key","existing-encrypted").commit();
  org.robolectric.android.controller.ActivityController<AiSettingsActivity> controller=Robolectric.buildActivity(AiSettingsActivity.class).setup();
  try{View root=controller.get().findViewById(android.R.id.content);EditText key=password(root);assertNotNull(key);key.setText("\"not a key\"");find(root,"Guardar y comprobar conexión").performClick();assertEquals("existing-encrypted",app.getSharedPreferences("keyboard_ai_private",0).getString("key",""));assertFalse(AiSettingsActivity.automaticNetworkAllowed(app));assertFalse(AiSettingsActivity.networkAllowed(app));assertFalse(AiSettingsActivity.voiceNetworkAllowed(app));}finally{controller.pause().stop().destroy();}
 }
 @Test public void mainPageKeepsSetupShortAndDoesNotExposeRetiredExperiment(){
  org.robolectric.android.controller.ActivityController<AiSettingsActivity> controller=Robolectric.buildActivity(AiSettingsActivity.class).setup();
  try{View root=controller.get().findViewById(android.R.id.content);assertNotNull(find(root,"Corrección automática con Gemini"));assertNotNull(find(root,"Privacidad y datos"));assertNull(find(root,"Descargar y verificar modelo local (491 MB)"));assertNull(find(root,"Consultar perfil y aprendizaje"));}finally{controller.pause().stop().destroy();}
 }
 private static TextView find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){TextView found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
 private static EditText password(View root){if(root instanceof EditText&&(((EditText)root).getInputType()&InputType.TYPE_MASK_VARIATION)==InputType.TYPE_TEXT_VARIATION_PASSWORD)return (EditText)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){EditText found=password(g.getChildAt(i));if(found!=null)return found;}}return null;}
}
