// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.os.UserManager;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Standalone configuration. Never persist a plaintext API token. Android 6+ only. */
public class AiSettingsActivity extends Activity {
    private final AiSessionCoordinator discovery=new AiSessionCoordinator(action->new android.os.Handler(android.os.Looper.getMainLooper()).post(action));
    @Override protected void onStop(){discovery.cancel();super.onStop();}
    @Override protected void onDestroy(){discovery.close();super.onDestroy();}
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
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout parent,String value,int size){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setPadding(0,dp(8),0,dp(8));parent.addView(text);return text;}
    private Button button(LinearLayout parent,String value,Runnable action){Button b=new com.google.android.material.button.MaterialButton(this);b.setText(value);b.setAllCaps(false);b.setMinHeight(dp(48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));parent.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private void toggle(LinearLayout parent,String label,String pref,boolean fallback){CheckBox box=new CheckBox(this);box.setText(label);box.setMinHeight(dp(48));box.setChecked(preferences().getBoolean(pref,fallback));box.setOnCheckedChangeListener((v,on)->preferences().edit().putBoolean(pref,on).apply());parent.addView(box);}
    private EditText input(LinearLayout parent,String hint,boolean password){EditText text=new com.google.android.material.textfield.TextInputEditText(this);text.setSingleLine(true);text.setHint(hint);text.setMinHeight(dp(52));if(password)text.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;}
    private void openSection(String section){startActivity(new android.content.Intent(this,AiSettingsActivity.class).putExtra("section",section));}
    @Override protected void onCreate(Bundle state){
        AiMenuTheme.applyActivity(this);super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);LinearLayout stack=new LinearLayout(this);stack.setOrientation(LinearLayout.VERTICAL);stack.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(stack);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        button(stack,"Volver",this::finish);
        String section=getIntent().getStringExtra("section");
        if("privacy".equals(section))privacy(stack);
        else if("voice".equals(section))voice(stack);
        else if("advanced".equals(section))advanced(stack);
        else gemini(stack);
        AiMenuTheme.resolve(this).styleSurface(stack);setContentView(scroll);androidx.core.view.ViewCompat.requestApplyInsets(scroll);
    }
    private void gemini(LinearLayout stack){
        label(stack,"Gemini",28);label(stack,"Asistencia opcional. La escritura y corrección local no necesitan conexión.",15);
        CheckBox automatic=new CheckBox(this);automatic.setText("Corrección automática con Gemini");automatic.setMinHeight(dp(48));automatic.setChecked(preferences().getBoolean("automatic_gemini",false));
        automatic.setOnCheckedChangeListener((v,on)->{preferences().edit().putBoolean("automatic_gemini",on).putBoolean("enabled",true).apply();if(!on)setAutomaticStatus(this,"Desactivado");});stack.addView(automatic);
        label(stack,"Activarlo autoriza enviar fragmentos del campo actual a Google durante las pausas. Conserva tu intención y tono. Retroceso deshace la corrección. Se excluyen contraseñas, incógnito y aplicaciones protegidas.",14);
        TextView status=label(stack,"Estado: "+automaticStatus(this),15);
        label(stack,"Clave de API",16);EditText key=input(stack,keyState(this)==KeyState.AVAILABLE?"Clave guardada en este dispositivo":"Pega únicamente tu clave",true);
        button(stack,"Guardar y comprobar conexión",()->connect(key,null,status));
        button(stack,"Opciones avanzadas de Gemini",()->openSection("advanced"));
        button(stack,"Dictado",()->openSection("voice"));
        button(stack,"Privacidad y datos",()->openSection("privacy"));
    }
    private void advanced(LinearLayout stack){
        label(stack,"Gemini · Avanzado",26);
        CheckBox manual=new CheckBox(this);manual.setText("Permitir acciones manuales con Google");manual.setChecked(networkAllowed(this));manual.setMinHeight(dp(48));manual.setOnCheckedChangeListener((v,on)->preferences().edit().putBoolean("enabled",on||preferences().getBoolean("automatic_gemini",false)).putBoolean("network_consent",on).apply());stack.addView(manual);
        label(stack,"Autoriza enviar el texto que elijas para corregir, reformular o traducir. Este permiso no activa corrección automática ni dictado de nube.",14);
        label(stack,"Modelo opcional",16);EditText model=input(stack,"Vacío: elegir automáticamente",false);model.setText(preferences().getString("model_override",""));
        TextView status=label(stack,"El modelo se elige automáticamente al comprobar la conexión.",14);
        button(stack,"Guardar y comprobar modelo",()->connect(null,model,status));
        button(stack,"Borrar clave",()->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("¿Borrar la clave de Gemini?").setMessage("La escritura local y tus diccionarios se conservan.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->{
            preferences().edit().remove("key").remove("model").putBoolean("automatic_gemini",false).putBoolean("voice_network_consent",false).apply();
            if(Build.VERSION.SDK_INT>=23)try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}catch(Exception ignored){}
            setAutomaticStatus(this,"Sin clave");status.setText("Clave eliminada. La escritura local sigue disponible.");
        }).show());
    }
    private void connect(EditText key,EditText model,TextView status){
        final String token=key==null?"":key.getText().toString().trim();
        final String override=model==null?preferences().getString("model_override",""):model.getText().toString().trim();
        // Validate all input before replacing an existing credential.
        if(!token.isEmpty()&&!AiConnectionStatus.validKey(token)){status.setText(AiConnectionStatus.explain("INVALID_REQUEST"));return;}
        if(!override.isEmpty()&&!override.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")){status.setText("Modelo inválido. Deja el campo vacío para selección automática.");return;}
        if(!token.isEmpty()&&!saveKey(this,token)){status.setText("No se pudo cifrar la clave. Desbloquea el dispositivo y prueba otra vez.");return;}
        if(model!=null)preferences().edit().putString("model_override",override).apply();
        if(key!=null){key.setText("");key.setHint(keyState(this)==KeyState.AVAILABLE?"Clave guardada en este dispositivo":"Pega únicamente tu clave");}
        final String saved=readKey(this);if(saved.isEmpty()){status.setText(AiConnectionStatus.explain("NO_KEY"));return;}
        // This button explicitly authorizes a fixed non-personal connection check only.
        // It never enables manual, voice or automatic transmission preferences.
        status.setText("Comprobando conexión y modelo…");
        discovery.submit(cancel->{
            helium314.keyboard.latin.ai.provider.GeminiProvider provider=new helium314.keyboard.latin.ai.provider.GeminiProvider();
            helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation scope=new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable action){cancel.onCancel(action);}};
            String chosen=override.isEmpty()?GeminiModelChoice.choose(provider.listModels(saved,scope)):override;
            if(chosen.isEmpty())throw new IllegalStateException("No model");
            if(!provider.generate(saved,chosen,helium314.keyboard.latin.ai.provider.GeminiOperation.SPELLING,"",null,"hola",scope).complete)throw new IllegalStateException("Incomplete check");
            return chosen;
        },(chosen,error)->{
            if(isFinishing()||isDestroyed())return;
            if(error!=null){String reason=error instanceof helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException?((helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException)error).error.name():"CONNECTION_CHECK";String message=AiConnectionStatus.explain(reason);setAutomaticStatus(this,message);status.setText(message);return;}
            preferences().edit().putString("model",chosen).apply();setAutomaticStatus(this,"Conectado");status.setText("Conectado. "+(automaticNetworkAllowed(this)?"La corrección automática está activa.":"Activa solo la asistencia que quieras utilizar."));
        },60000);
    }
    private void voice(LinearLayout stack){
        label(stack,"Dictado",28);toggle(stack,"Dictado desde el micrófono del teclado","dictation",true);
        label(stack,"Literal: reconocimiento del motor instalado. No cambia a nube de forma silenciosa.",14);
        CheckBox smart=new CheckBox(this);smart.setText("Dictado inteligente con Gemini");smart.setMinHeight(dp(48));smart.setChecked(intelligentDictation(this)&&preferences().getBoolean("voice_network_consent",false));
        smart.setOnCheckedChangeListener((v,on)->preferences().edit().putString("dictation_mode",on?"intelligent":"literal").putBoolean("voice_network_consent",on).apply());stack.addView(smart);
        label(stack,"Activarlo autoriza enviar a Google el audio que grabes desde el micrófono para obtener texto limpio. La edición por voz envía también el texto que elijas editar. No activa la revisión automática mientras tecleas. Puede consumir cuota.",14);
        button(stack,"Configurar conexión de Gemini",()->openSection("gemini"));
        label(stack,"Idioma del dictado literal",16);android.widget.Spinner language=new android.widget.Spinner(this);String[] codes={"auto","es-MX","en-US"};language.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Automático si el motor lo admite","Español México","English US"}));language.setSelection(java.util.Arrays.asList(codes).indexOf(dictationLanguage(this)));stack.addView(language);
        EditText vocabulary=input(stack,"Vocabulario opcional: nombres y términos",false);vocabulary.setText(preferences().getString("dictation_vocabulary",""));TextView status=label(stack,"Hasta 32 términos. No introduzcas datos sensibles.",13);
        button(stack,"Guardar opciones de voz",()->{try{java.util.List<String> terms=helium314.keyboard.latin.ai.dictation.DictationOptions.vocabulary(vocabulary.getText().toString());preferences().edit().putString("dictation_language",codes[language.getSelectedItemPosition()]).putString("dictation_vocabulary",android.text.TextUtils.join(",",terms)).apply();status.setText("Opciones guardadas.");}catch(IllegalArgumentException bad){status.setText("Usa hasta 32 términos de 48 caracteres, sin datos sensibles.");}});
        label(stack,"Captura adaptada de Ramblr · © 2026 Trevor NK · GPLv3+. Proyecto independiente de Rambler de Google. github.com/trevornk/ramblr",12);
    }
    private void privacy(LinearLayout stack){
        label(stack,"Privacidad y datos",26);toggle(stack,"Escritura inteligente local","intelligent_typing",true);toggle(stack,"Personalización local de sugerencias","personal_style_consent",false);
        label(stack,"La escritura local conserva sus diccionarios y aprendizaje útil. Gemini solo usa la red para las funciones que autorices. Los campos protegidos y aplicaciones excluidas quedan fuera.",14);
        label(stack,"Aplicaciones excluidas",16);EditText excluded=input(stack,"Paquetes separados por coma",false);excluded.setText(preferences().getString("excluded_apps",""));TextView status=label(stack,"Estas exclusiones se comparten entre las funciones de asistencia.",13);
        button(stack,"Guardar exclusiones",()->{preferences().edit().putString("excluded_apps",excluded.getText().toString().trim()).apply();status.setText("Exclusiones guardadas.");});
        button(stack,"Borrar aprendizaje local",()->new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("¿Borrar aprendizaje local?").setMessage("El diccionario personal manual se conserva.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->{clearLearnedData(this);status.setText("Aprendizaje local borrado.");}).show());
        button(stack,"Respaldo cifrado del aprendizaje",()->startActivity(new android.content.Intent(this,helium314.keyboard.latin.ai.cloud.TypingCloudActivity.class)));
    }
}
