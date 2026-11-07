package helium314.keyboard.latin.ai;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class AutomaticGeminiSettingsTest {
    private Context app;
    @Before public void setup(){app=ApplicationProvider.getApplicationContext();app.getSharedPreferences("keyboard_ai_private",0).edit().clear().commit();}
    private boolean allowed()throws Exception{return (boolean)AiSettingsActivity.class.getMethod("automaticNetworkAllowed",Context.class).invoke(null,app);}
    @Test public void manualConsentNeverEnablesBackgroundTransmission()throws Exception{
        app.getSharedPreferences("keyboard_ai_private",0).edit().putBoolean("enabled",true).putBoolean("network_consent",true).commit();
        assertFalse(allowed());
    }
    @Test public void oneAutomaticSwitchIsIndependentOfManualTools()throws Exception{
        app.getSharedPreferences("keyboard_ai_private",0).edit().putBoolean("automatic_gemini",true).commit();
        assertTrue(allowed());
        app.getSharedPreferences("keyboard_ai_private",0).edit().putBoolean("intelligent_typing",false).commit();assertFalse(allowed());
    }
    @Test public void manualAndAutomaticConsentNeverAuthorizesVoiceUploads(){
        app.getSharedPreferences("keyboard_ai_private",0).edit().putBoolean("enabled",true).putBoolean("network_consent",true).putBoolean("automatic_gemini",true).putString("dictation_mode","intelligent").commit();
        assertFalse(AiSettingsActivity.voiceNetworkAllowed(app));
        app.getSharedPreferences("keyboard_ai_private",0).edit().putBoolean("voice_network_consent",true).commit();
        assertTrue(AiSettingsActivity.voiceNetworkAllowed(app));
    }
    @Test public void voiceConsentDoesNotEnableBackgroundTypingReview()throws Exception{
        app.getSharedPreferences("keyboard_ai_private",0).edit().putString("dictation_mode","intelligent").putBoolean("voice_network_consent",true).commit();
        assertTrue(AiSettingsActivity.voiceNetworkAllowed(app));assertFalse(allowed());
    }
}
