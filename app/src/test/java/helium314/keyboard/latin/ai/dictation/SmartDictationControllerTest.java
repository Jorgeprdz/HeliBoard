package helium314.keyboard.latin.ai.dictation;
import android.content.Context;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.ai.EditorSnapshot;
import org.junit.Test;import org.junit.runner.RunWith;
import org.robolectric.Robolectric;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;import static org.mockito.Mockito.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SmartDictationControllerTest {
 @Test public void manualAndAutomaticConsentNeverStartSmartVoiceCapture(){
  LatinIME ime=Robolectric.buildService(LatinIME.class).get();
  ime.getSharedPreferences("keyboard_ai_private",Context.MODE_PRIVATE).edit().clear().putBoolean("enabled",true).putBoolean("network_consent",true).putBoolean("automatic_gemini",true).commit();
  SmartDictationController.Host host=mock(SmartDictationController.Host.class);when(host.capture()).thenReturn(new EditorSnapshot(new Object(),"test.editor",1,1,1,0,0,""));
  SmartDictationController controller=new SmartDictationController(ime,host);controller.start(false);assertTrue(controller.active());
  verify(host).mount(any(DictationPanel.class));verify(host,never()).apply(any(),anyString(),anyString());controller.cancel();controller.cancel();verify(host,times(1)).dismiss(any(DictationPanel.class));assertFalse(controller.active());controller.close();
 }
 @Test public void staleSelectionClosesVoiceSurfaceWithoutApplyingText(){
  LatinIME ime=Robolectric.buildService(LatinIME.class).get();SmartDictationController.Host host=mock(SmartDictationController.Host.class);Object editor=new Object();
  when(host.capture()).thenReturn(new EditorSnapshot(editor,"test.editor",1,1,1,0,0,""));
  SmartDictationController controller=new SmartDictationController(ime,host);controller.start(false);when(host.capture()).thenReturn(new EditorSnapshot(editor,"test.editor",1,1,1,3,3,""));controller.editorChanged();
  assertFalse(controller.active());verify(host,never()).apply(any(),anyString(),anyString());controller.close();
 }
}
