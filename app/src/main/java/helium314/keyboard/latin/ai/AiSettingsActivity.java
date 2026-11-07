// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.os.UserManager;
import android.view.WindowManager;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Standalone configuration. Never persist a plaintext API token. Android 6+ only. */
public class AiSettingsActivity extends androidx.activity.ComponentActivity {
    private final AiSessionCoordinator discovery=new AiSessionCoordinator(action->new android.os.Handler(android.os.Looper.getMainLooper()).post(action));
    @Override public void onStop(){discovery.cancel();super.onStop();}
    @Override public void onDestroy(){discovery.close();super.onDestroy();}
    private static final String PREF = "keyboard_ai_private";
    private static final String ALIAS = "heliboard_gemini_key_v1";

    public enum KeyState { NO_KEY, AVAILABLE, LOCKED, INVALIDATED }
    public static boolean isEnabled(Context ctx) { return ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("enabled", false); }
    public static boolean networkAllowed(Context ctx) { return isEnabled(ctx) && ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("network_consent", false); }
    /** Separate opt-in: manual tool permission never authorizes background text upload. */
    public static boolean automaticNetworkAllowed(Context ctx) {
        return typingEnabled(ctx) && ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("automatic_gemini", false) && userUnlocked(ctx);
    }
    public static String automaticStatus(Context ctx) { return ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("automatic_status", "Sin revisar todavía"); }
    public static void setAutomaticStatus(Context ctx,String state) { ctx.getSharedPreferences(PREF,MODE_PRIVATE).edit().putString("automatic_status",state).apply(); }
    /** Balanced local typing is independent of optional Gemini/network consent. */
    public static boolean typingEnabled(Context ctx){return ctx.getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("intelligent_typing",true);}
    public static boolean styleEnabled(Context ctx){return typingEnabled(ctx)&&ctx.getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("personal_style_consent",false);}
    public static String styleTone(Context ctx){
        String value=ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("style_tone","auto");
        return "casual".equals(value)||"professional".equals(value)?value:"auto";
    }
    public static boolean contextualEnabled(Context ctx) { return isEnabled(ctx) && ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("local_context", false); }
    public static boolean dictationEnabled(Context ctx) { return ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("dictation", true); }
    public static boolean excludedApp(Context ctx, String name) {
        if (name == null) return true;
        for (String value : ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString("excluded_apps", "").split("[,;\\s]+"))
            if (name.equals(value)) return true;
        return false;
    }
    public static String dictationLanguage(Context ctx){String value=ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("dictation_language","auto");return "es-MX".equals(value)||"en-US".equals(value)?value:"auto";}
    public static java.util.List<String> readVocabulary(Context ctx){try{return helium314.keyboard.latin.ai.dictation.DictationOptions.vocabulary(ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("dictation_vocabulary",""));}catch(IllegalArgumentException e){return java.util.Collections.emptyList();}}
    public static synchronized java.util.Set<String> rejectedCorrections(Context ctx) {
        return new java.util.HashSet<>(ctx.getSharedPreferences(PREF,MODE_PRIVATE).getStringSet("rejected_corrections",java.util.Collections.emptySet()));
    }
    public static synchronized void rejectCorrection(Context ctx,String correction) {
        if(correction==null||correction.length()>100||SensitiveInputPolicy.sensitiveText(correction))return;
        java.util.Set<String> values=rejectedCorrections(ctx);
        // Old cloud snapshots must never evict newer local vetoes, including at capacity.
        if(values.contains(correction)||values.size()>=128)return;
        values.add(correction);
        ctx.getSharedPreferences(PREF,MODE_PRIVATE).edit().putStringSet("rejected_corrections",values).apply();
    }
    /** Clears automatically learned history and AI rejection preferences, not manual dictionaries. */
    public static synchronized void clearLearnedData(Context ctx) {
        ctx.getSharedPreferences(PREF,MODE_PRIVATE).edit().remove("rejected_corrections").commit();
        // Also stop cloud recovery before erasing; remote backup is deliberately retained.
        helium314.keyboard.latin.ai.cloud.TypingSync.stopSync(ctx);
        helium314.keyboard.latin.ai.local.PersonalTypingStore.get(ctx).eraseAsync();
        helium314.keyboard.latin.personalization.PersonalizationHelper.removeAllUserHistoryDictionaries(ctx);
    }
    private static boolean userUnlocked(Context ctx) {
        if (Build.VERSION.SDK_INT < 24) return true;
        UserManager user = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
        return user != null && user.isUserUnlocked();
    }
    public static KeyState keyState(Context ctx) {
        if (!userUnlocked(ctx)) return KeyState.LOCKED;
        if (ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString("key", "").isEmpty()) return KeyState.NO_KEY;
        return readKey(ctx).isEmpty() ? KeyState.INVALIDATED : KeyState.AVAILABLE;
    }
    public static String readModel(Context ctx) {
        return ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString("model", "");
    }
    public static String readKey(Context ctx) {
        if (Build.VERSION.SDK_INT < 23 || !userUnlocked(ctx)) return "";
        SharedPreferences prefs = ctx.getSharedPreferences(PREF, MODE_PRIVATE);
        try {
            String data = prefs.getString("key", "");
            if (data == null || data.isEmpty()) return "";
            String[] parts = data.split(":");
            if (parts.length != 2) return "";
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            SecretKey secret = ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret, new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }
    private static boolean saveKey(Context ctx, String key) {
        if (Build.VERSION.SDK_INT < 23 || !userUnlocked(ctx)) return false;
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            SecretKey secret;
            if (store.containsAlias(ALIAS))
                secret = ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
            else {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                secret = generator.generateKey();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
            ctx.getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("key", packed).apply();
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean intelligentDictation(Context ctx){return "intelligent".equals(ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("dictation_mode","literal"));}
    public static boolean voiceNetworkAllowed(Context ctx){return dictationEnabled(ctx)&&intelligentDictation(ctx)&&userUnlocked(ctx)&&ctx.getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("voice_network_consent",false);}
    private SharedPreferences preferences(){return getSharedPreferences(PREF,MODE_PRIVATE);}
    public SharedPreferences uiPreferences(){return preferences();}
    public interface ConnectionState { void update(String message); }
    public void openSection(String section){startActivity(new android.content.Intent(this,AiSettingsActivity.class).putExtra("section",section));}
    @Override public void onCreate(Bundle state){
        AiMenuTheme.applyActivity(this);super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        AiSettingsUi.install(this);
    }
    public void clearSavedKey(){
        preferences().edit().remove("key").remove("model").putBoolean("automatic_gemini",false).putBoolean("voice_network_consent",false).apply();
        if(Build.VERSION.SDK_INT>=23)try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}catch(Exception ignored){}
        setAutomaticStatus(this,"Sin clave");
    }
    public String saveVoiceOptions(String language,String vocabulary){
        if(!"auto".equals(language)&&!"es-MX".equals(language)&&!"en-US".equals(language))return "Selecciona uno de los idiomas disponibles.";
        try{
            java.util.List<String> terms=helium314.keyboard.latin.ai.dictation.DictationOptions.vocabulary(vocabulary);
            preferences().edit().putString("dictation_language",language).putString("dictation_vocabulary",android.text.TextUtils.join(",",terms)).apply();
            return "Opciones guardadas.";
        }catch(IllegalArgumentException bad){return "Usa hasta 32 términos de 48 caracteres, sin datos sensibles.";}
    }
    public void checkConnection(String rawKey,String rawModel,boolean updateModel,ConnectionState status){
        final String token=rawKey==null?"":rawKey.trim();
        final String override=updateModel?(rawModel==null?"":rawModel.trim()):preferences().getString("model_override","");
        // Validate all input before replacing an existing credential.
        if(!token.isEmpty()&&!AiConnectionStatus.validKey(token)){status.update(AiConnectionStatus.explain("INVALID_REQUEST"));return;}
        if(!override.isEmpty()&&!override.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")){status.update("Modelo inválido. Deja el campo vacío para selección automática.");return;}
        if(!token.isEmpty()&&!saveKey(this,token)){status.update("No se pudo cifrar la clave. Desbloquea el dispositivo y prueba otra vez.");return;}
        if(updateModel)preferences().edit().putString("model_override",override).apply();
        final String saved=readKey(this);if(saved.isEmpty()){status.update(AiConnectionStatus.explain("NO_KEY"));return;}
        if(!AiConnectionStatus.validKey(saved)){
            String message="La clave guardada tiene un formato inválido. Vuelve a pegar únicamente la clave de Gemini.";
            setAutomaticStatus(this,message);status.update(message);return;
        }
        // This button explicitly authorizes a fixed non-personal connection check only.
        // It never enables manual, voice or automatic transmission preferences.
        status.update("Comprobando conexión y modelo…");
        discovery.submit(cancel->{
            helium314.keyboard.latin.ai.provider.GeminiProvider provider=new helium314.keyboard.latin.ai.provider.GeminiProvider();
            helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation scope=new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable action){cancel.onCancel(action);}};
            String chosen=override.isEmpty()?GeminiModelChoice.choose(provider.listModels(saved,scope)):override;
            if(chosen.isEmpty())throw new IllegalStateException("No model");
            if(!provider.generate(saved,chosen,helium314.keyboard.latin.ai.provider.GeminiOperation.SPELLING,"",null,"hola",scope).complete)throw new IllegalStateException("Incomplete check");
            return chosen;
        },(chosen,error)->{
            if(isFinishing()||isDestroyed())return;
            if(error!=null){String reason=error instanceof helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException?((helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException)error).error.name():"CONNECTION_CHECK";String message=AiConnectionStatus.explain(reason);setAutomaticStatus(this,message);status.update(message);return;}
            preferences().edit().putString("model",chosen).apply();setAutomaticStatus(this,"Conectado");status.update("Conectado. "+(automaticNetworkAllowed(this)?"La corrección automática está activa.":"Activa solo la asistencia que quieras utilizar."));
        },60000);
    }
}
