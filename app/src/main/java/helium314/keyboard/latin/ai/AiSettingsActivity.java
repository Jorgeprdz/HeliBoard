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
    public static boolean contextualEnabled(Context ctx) { return isEnabled(ctx) && ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("local_context", false); }
    public static boolean dictationEnabled(Context ctx) { return isEnabled(ctx) && ctx.getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("dictation", false); }
    public static boolean excludedApp(Context ctx, String name) {
        if (name == null) return true;
        for (String value : ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString("excluded_apps", "").split("[,;\\s]+"))
            if (name.equals(value)) return true;
        return false;
    }
    public static String dictationLanguage(Context ctx){String value=ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("dictation_language","auto");return "es-MX".equals(value)||"en-US".equals(value)?value:"auto";}
    public static java.util.List<String> readVocabulary(Context ctx){try{return helium314.keyboard.latin.ai.dictation.DictationOptions.vocabulary(ctx.getSharedPreferences(PREF,MODE_PRIVATE).getString("dictation_vocabulary",""));}catch(IllegalArgumentException e){return java.util.Collections.emptyList();}}
    public static java.util.Set<String> rejectedCorrections(Context ctx) {
        return new java.util.HashSet<>(ctx.getSharedPreferences(PREF,MODE_PRIVATE).getStringSet("rejected_corrections",java.util.Collections.emptySet()));
    }
    public static void rejectCorrection(Context ctx,String correction) {
        if(correction==null||correction.length()>100||SensitiveInputPolicy.sensitiveText(correction))return;
        java.util.Set<String> values=rejectedCorrections(ctx);if(values.size()>=128)values.clear();values.add(correction);
        ctx.getSharedPreferences(PREF,MODE_PRIVATE).edit().putStringSet("rejected_corrections",values).apply();
    }
    /** Clears automatically learned history and AI rejection preferences, not manual dictionaries. */
    public static void clearLearnedData(Context ctx) {
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
    private void addToggle(LinearLayout stack, String label, String pref) {
        CheckBox box = new CheckBox(this); box.setText(label);
        box.setChecked(getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(pref, false));
        box.setOnCheckedChangeListener((button, enabled) -> getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean(pref, enabled).apply());
        stack.addView(box);
    }
    @Override protected void onCreate(Bundle state) {
        AiMenuTheme.applyActivity(this);
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        stack.setPadding(pad,pad,pad,pad);
        scroll.addView(stack);
        TextView title = new TextView(this);
        title.setText("✨ HeliBoard AI · Gemini");
        title.setTextSize(22);
        stack.addView(title);
        addToggle(stack, "AI Keyboard (desactivado por defecto)", "enabled");
        addToggle(stack, "Permitir Google solo al elegir una acción", "network_consent");
        addToggle(stack, "Revisión contextual local ES/EN", "local_context");
        addToggle(stack, "Dictado integrado en el micrófono existente", "dictation");
        TextView details = new TextView(this);
        details.setText("IA opcional. El texto seleccionado se envía a Google solo al elegir una herramienta y dar permiso. El dictado de nube también requiere ese permiso. Puede consumir cuota. No se envían campos protegidos ni datos detectados como sensibles. La clave se cifra con Android Keystore. Cancelar descarta el resultado, pero no puede retirar datos que Google ya recibió. El menú conserva el portapapeles; ✨ utiliza el acceso fijado sin modificar atajos personalizados.");
        details.setPadding(0,pad/2,0,pad/2);
        stack.addView(details);
        TextView label = new TextView(this);
        label.setText("API key de Gemini:");
        stack.addView(label);
        EditText key = new com.google.android.material.textfield.TextInputEditText(this);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint(readKey(this).isEmpty() ? "Pega tu API key (no se sube a GitHub)" : "•••••••• (clave guardada)");
        stack.addView(key);
        TextView modelLabel = new TextView(this);
        modelLabel.setText("Modelo Gemini (configurable):");
        stack.addView(modelLabel);
        EditText model = new com.google.android.material.textfield.TextInputEditText(this);
        model.setSingleLine(true);model.setHint("Consultar modelos disponibles y elegir uno");
        model.setText(readModel(this));
        stack.addView(model);
        TextView status = new TextView(this);
        status.setPadding(0, pad/2, 0, pad/2);
        stack.addView(status);
        Button refresh=new com.google.android.material.button.MaterialButton(this);refresh.setText("Consultar modelos disponibles (Google)");
        refresh.setOnClickListener(v->{
            if(!networkAllowed(this)){status.setText("Activa el consentimiento de red antes de consultar modelos.");return;}
            final String token=readKey(this);if(token.isEmpty()){status.setText("Guarda una clave válida primero.");return;}
            final java.util.concurrent.atomic.AtomicReference<String[]> names=new java.util.concurrent.atomic.AtomicReference<>();
            status.setText("Consultando modelos…");
            discovery.submit(cancel->{names.set(new helium314.keyboard.latin.ai.provider.GeminiProvider().listModels(token,
                new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}}));return "";},(unused,error)->{
                if(isFinishing()||isDestroyed()||!networkAllowed(this))return;
                if(error!=null){status.setText("No fue posible consultar modelos: "+error.getClass().getSimpleName());return;}
                String[] values=names.get();if(values==null||values.length==0){status.setText("La clave no tiene modelos disponibles.");return;}
                new android.app.AlertDialog.Builder(this).setTitle("Modelos disponibles").setItems(values,(dialog,index)->model.setText(values[index])).setNegativeButton("Cancelar",null).show();
            },35000);
        });stack.addView(refresh);
        Button save = new com.google.android.material.button.MaterialButton(this);
        save.setText("Guardar configuración");
        save.setOnClickListener(v -> {
            String token = key.getText().toString().trim();
            if (!token.isEmpty() && !saveKey(this, token)) {
                status.setText("No fue posible cifrar la clave. Android 6 o superior es necesario.");
                return;
            }
            String selectedModel = model.getText().toString().trim();
            if (selectedModel.matches("[A-Za-z0-9._-]{3,60}")) {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("model", selectedModel).apply();
                status.setText("Guardado. Vuelve a tu teclado y selecciona texto.");
                key.setText("");
            } else status.setText("Modelo inválido.");
        });
        stack.addView(save);
        Button forget = new com.google.android.material.button.MaterialButton(this);
        forget.setText("Borrar clave");
        forget.setOnClickListener(v -> {
            getSharedPreferences(PREF, MODE_PRIVATE).edit().remove("key").apply();
            if (Build.VERSION.SDK_INT >= 23) try {
                KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null); store.deleteEntry(ALIAS);
            } catch (Exception ignored) { /* No credential contents in logs. */ }
            status.setText("Clave eliminada.");
            key.setText("");
        });
        stack.addView(forget);
        TextView excludedLabel = new TextView(this); excludedLabel.setText("Excluir aplicaciones (paquetes separados por coma):"); stack.addView(excludedLabel);
        EditText excluded = new com.google.android.material.textfield.TextInputEditText(this); excluded.setSingleLine(true);
        excluded.setText(getSharedPreferences(PREF, MODE_PRIVATE).getString("excluded_apps", "")); stack.addView(excluded);
        Button saveExcluded = new com.google.android.material.button.MaterialButton(this); saveExcluded.setText("Guardar exclusiones");
        saveExcluded.setOnClickListener(v -> getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("excluded_apps", excluded.getText().toString().trim()).apply());
        stack.addView(saveExcluded);
        Button resetLearning=new com.google.android.material.button.MaterialButton(this);resetLearning.setText("Borrar aprendizaje local");
        resetLearning.setOnClickListener(v->{clearLearnedData(this);status.setText("Historial aprendido y rechazos contextuales borrados. El diccionario personal manual se conserva.");});stack.addView(resetLearning);
        TextView voiceLabel=new TextView(this);voiceLabel.setText("Idioma del dictado y vocabulario explícito (máximo 32 términos). El modo local requiere reconocimiento instalado; no descarga modelos ni usa nube como alternativa silenciosa.");stack.addView(voiceLabel);
        android.widget.Spinner voiceLanguage=new android.widget.Spinner(this);String[] languageCodes={"auto","es-MX","en-US"};voiceLanguage.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"ES/EN si el motor local lo admite","Español México","English US"}));voiceLanguage.setSelection(java.util.Arrays.asList(languageCodes).indexOf(dictationLanguage(this)));stack.addView(voiceLanguage);
        EditText vocabulary=new com.google.android.material.textfield.TextInputEditText(this);vocabulary.setHint("Carlos, HeliBoard, vocabulario técnico…");vocabulary.setText(getSharedPreferences(PREF,MODE_PRIVATE).getString("dictation_vocabulary",""));stack.addView(vocabulary);
        Button saveVoice=new com.google.android.material.button.MaterialButton(this);saveVoice.setText("Guardar opciones de dictado");saveVoice.setOnClickListener(v->{try{java.util.List<String> terms=helium314.keyboard.latin.ai.dictation.DictationOptions.vocabulary(vocabulary.getText().toString());getSharedPreferences(PREF,MODE_PRIVATE).edit().putString("dictation_language",languageCodes[voiceLanguage.getSelectedItemPosition()]).putString("dictation_vocabulary",android.text.TextUtils.join(",",terms)).apply();status.setText("Opciones guardadas. Los términos se usan localmente o se envían con el audio únicamente al elegir Gemini.");}catch(IllegalArgumentException invalid){status.setText("Usa hasta 32 términos de 48 caracteres, sin datos sensibles.");}});stack.addView(saveVoice);
        TextView credit=new TextView(this);credit.setText("Grabación adaptada de Ramblr · © 2026 Trevor NK · GPLv3+. Código y atribuciones: github.com/trevornk/ramblr");stack.addView(credit);
        TextView keyStatus = new TextView(this); keyStatus.setText("Credencial: " + keyState(this)); stack.addView(keyStatus);
        Button cloud=new com.google.android.material.button.MaterialButton(this);cloud.setText("Personal Typing Cloud · respaldo cifrado");
        cloud.setOnClickListener(v->startActivity(new android.content.Intent(this,helium314.keyboard.latin.ai.cloud.TypingCloudActivity.class)));stack.addView(cloud);
        AiMenuTheme.resolve(this).styleSurface(stack);
        setContentView(scroll);
    }
}
