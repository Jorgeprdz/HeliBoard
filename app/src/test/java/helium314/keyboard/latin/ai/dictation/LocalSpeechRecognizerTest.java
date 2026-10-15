// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;

import android.content.Intent;
import android.speech.RecognizerIntent;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36)
public class LocalSpeechRecognizerTest {
 @Test public void autoLanguageRequestsOfflineBilingualSwitchingAndVocabulary(){
  Intent intent=LocalSpeechRecognizer.buildIntent("auto",Arrays.asList("HeliBoard","Carlos"),36);
  assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false));
  assertEquals("es-MX",intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
  assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING));
  assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,false));
  assertEquals(RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION,intent.getStringExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH));
  assertEquals(Arrays.asList("es-MX","en-US"),intent.getStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES));
  assertEquals(Arrays.asList("es-MX","en-US"),intent.getStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES));
  assertEquals(Arrays.asList("HeliBoard","Carlos"),intent.getStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS));
 }
 @Test public void fixedLanguageDoesNotEnableLanguageSwitching(){
  Intent intent=LocalSpeechRecognizer.buildIntent("en-US",java.util.Collections.emptyList(),36);
  assertEquals("en-US",intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
  assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION));
  assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH));
 }
}
