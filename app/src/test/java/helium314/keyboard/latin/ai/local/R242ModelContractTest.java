// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;import helium314.keyboard.latin.ai.AiSessionCoordinator;
import static org.junit.Assert.*;
/** Availability/prompt/integrity tests only. Not substituted for the real GGUF evaluation. */
@RunWith(RobolectricTestRunner.class)
public class R242ModelContractTest {
 @Test public void absentModelAndNoConsentAreIndependentOfGeminiAndDoNotLoadRuntime(){
  android.content.Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("keyboard_ai_private",0).edit().clear().commit();
  assertFalse(WritingModelFiles.enabled(context));assertFalse(WritingModelFiles.available(context));
  assertTrue(WritingModelFiles.file(context).getPath().startsWith(context.getNoBackupFilesDir().getPath()));
 }
 @Test public void profileRegistersAffectRealInferencePromptWithoutStoredConversations(){
  String casual=CorrectionFocus.prompt("mensaje de prueba","prueab","casual; ending.ellipsis");
  String professional=CorrectionFocus.prompt("mensaje de prueba","prueab","professional");
  assertNotEquals(casual,professional);assertTrue(casual.contains("ending.ellipsis"));
  assertTrue(professional.contains("Preserve names, numbers, negations and commitments"));
  assertFalse(casual.contains("https://"));
 }
 @Test public void truncatedOrForeignModelCannotPassIntegrityValidation()throws Exception{
  java.io.File file=java.io.File.createTempFile("foreign-model",".gguf");
  try{assertFalse(WritingModelFiles.verify(file,new AiSessionCoordinator.Cancellation()));}finally{assertTrue(file.delete());}
 }
}
