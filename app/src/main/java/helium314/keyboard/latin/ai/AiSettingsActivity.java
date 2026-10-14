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
    private static final String PREF = "keyboard_ai_private";
    private static final String ALIAS = "heliboard_gemini_key_v1";

    static String readModel(Context ctx) {
        return ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString("model", "gemini-2.5-flash");
    }
    static String readKey(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return "";
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
        if (Build.VERSION.SDK_INT < 23) return false;
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
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        TextView details = new TextView(this);
        details.setText("IA opcional. El teclado solo envía el texto seleccionado cuando tocas una acción de Gemini. Nunca guardamos lo que escribes. La clave se cifra con Android Keystore.");
        details.setPadding(0,pad/2,0,pad/2);
        stack.addView(details);
        TextView label = new TextView(this);
        label.setText("API key de Gemini:");
        stack.addView(label);
        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint(readKey(this).isEmpty() ? "Pega tu API key (no se sube a GitHub)" : "•••••••• (clave guardada)");
        stack.addView(key);
        TextView modelLabel = new TextView(this);
        modelLabel.setText("Modelo Gemini (configurable):");
        stack.addView(modelLabel);
        EditText model = new EditText(this);
        model.setSingleLine(true);
        model.setText(readModel(this));
        stack.addView(model);
        TextView status = new TextView(this);
        status.setPadding(0, pad/2, 0, pad/2);
        stack.addView(status);
        Button save = new Button(this);
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
        Button forget = new Button(this);
        forget.setText("Borrar clave");
        forget.setOnClickListener(v -> {
            getSharedPreferences(PREF, MODE_PRIVATE).edit().remove("key").apply();
            status.setText("Clave eliminada.");
            key.setText("");
        });
        stack.addView(forget);
        setContentView(scroll);
    }
}
