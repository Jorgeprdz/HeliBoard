package helium314.keyboard.latin.ai;
import org.junit.Test;import static org.junit.Assert.*;
public class R23CertificateTest {
 @Test public void finalSpeechResultDeliveredAtMostOnce() {
  helium314.keyboard.latin.ai.dictation.DictationSession s=new helium314.keyboard.latin.ai.dictation.DictationSession();
  EditorSnapshot e=new EditorSnapshot(new Object(),"test",1,1,1,0,0,"");
  long t=s.begin(e,true,false);assertTrue(s.stop(t));assertTrue(s.result(t,e,"hola"));
  assertFalse(s.result(t,e,"hola"));assertFalse(s.stop(t));s.cancel();assertFalse(s.result(t,e,"hola"));
 }
}
