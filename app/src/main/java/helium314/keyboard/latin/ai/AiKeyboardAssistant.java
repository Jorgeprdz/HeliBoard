// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in AI assistant. Never reads the global clipboard nor texts outside explicit selection. */
public final class AiKeyboardAssistant {
    private final LatinIME ime;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int generation = 0;
    private String selected = "";
    private String proposed = "";
    private String address = "";
    private String phone = "";
    private String clabe = "";
    private String email = "";
    private SuggestionStripView strip;

    public AiKeyboardAssistant(LatinIME ime) { this.ime = ime; }

    private void show(String... choices) {
        if (strip != null) strip.setAiChoices(choices);
    }
    private boolean privateField() {
        EditorInfo editor = ime.getCurrentInputEditorInfo();
        if (editor == null) return true;
        int cls = editor.inputType & InputType.TYPE_MASK_CLASS;
        int variation = editor.inputType & InputType.TYPE_MASK_VARIATION;
        if ((editor.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true;
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) return true;
        if (cls == InputType.TYPE_CLASS_TEXT &&
                (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) return true;
        return false;
    }
    private String currentSelection() {
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence value = ic.getSelectedText(0);
        if (value == null) return "";
        return value.toString().trim();
    }
    public void onNewEditor() {
        generation++;
        selected = ""; proposed = "";
        strip = null;
    }
    public void onAction(String action, SuggestionStripView view) {
        strip = view;
        if ("IA".equals(action)) {
            show("Corregir", "Reescribir", "Traducir", "Leer texto", "Configurar", "Cerrar");
            return;
        }
        if ("Cerrar".equals(action) || "Cancelar".equals(action)) {
            generation++; selected = ""; proposed = "";
            if (strip != null) strip.closeAiChoices();
            return;
        }
        if ("Configurar".equals(action)) {
            Intent i = new Intent(ime, AiSettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ime.startActivity(i);
            return;
        }
        if (privateField()) {
            show("IA no disponible en campos protegidos", "Cerrar");
            return;
        }
        if ("Leer texto".equals(action)) {
            smartRead();
            return;
        }
        if ("Usar".equals(action)) {
            InputConnection ic = ime.getCurrentInputConnection();
            if (ic == null || proposed.isEmpty() || selected.isEmpty()
                    || !selected.equals(currentSelection())) {
                show("La selección cambió", "Cerrar");
                return;
            }
            ic.beginBatchEdit();
            try { ic.commitText(proposed, 1); }
            finally { ic.endBatchEdit(); }
            show("Insertado", "Cerrar");
            proposed = ""; selected = "";
            return;
        }
        if ("Copiar resultado".equals(action)) {
            if (!proposed.isEmpty()) {
                ClipboardManager manager = (ClipboardManager) ime.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText("Respuesta IA", proposed));
                show("Resultado copiado", "Cerrar");
            }
            return;
        }
        if ("Calendario".equals(action)) { openCalendar(); return; }
        if ("Mapa".equals(action)) { launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode(address)))); return; }
        if ("Marcar".equals(action)) { launch(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))); return; }
        if ("Correo".equals(action)) { launch(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email))); return; }
        if ("Copiar CLABE".equals(action)) {
            if (!clabe.isEmpty()) {
                ((ClipboardManager) ime.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("CLABE", clabe));
                show("CLABE copiada", "Cerrar");
            }
            return;
        }
        if (action.startsWith("Vista:")) {
            show("Usar", "Copiar resultado", "Cancelar"); return;
        }
        if ("Corregir".equals(action) || "Reescribir".equals(action) || "Traducir".equals(action)) {
            requestGemini(action); return;
        }
        if (strip != null && action.startsWith("IA no")) strip.closeAiChoices();
    }

    private void smartRead() {
        selected = currentSelection();
        if (selected.isEmpty()) {
            show("Selecciona un texto primero", "Cerrar"); return;
        }
        if (selected.length() > 2000) {
            show("Selecciona menos de 2000 caracteres", "Cerrar"); return;
        }
        String lower = selected.toLowerCase(Locale.ROOT);
        java.util.ArrayList<String> choices = new java.util.ArrayList<>();
        if (lower.matches("(?s).*(mañana|pasado mañana|lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo|reunión|reunion|cita|nos vemos|[0-9]{1,2}/[0-9]{1,2}).*"))
            choices.add("Calendario");
        if (lower.matches("(?s).*(calle |av\\.? |avenida |insurgentes|dirección|direccion|colonia |blvd |boulevard ).*")) {
            address = selected;
            choices.add("Mapa");
        }
        Matcher m = Pattern.compile("(?<![0-9])(?:\\+?52[ -]?)?([0-9][ ()-]?){10}(?![0-9])").matcher(selected);
        if (m.find()) {
            phone = m.group().replaceAll("[^0-9+]", "");
            choices.add("Marcar");
        }
        m = Pattern.compile("(?<![0-9])\\d{18}(?![0-9])").matcher(selected);
        if (m.find() && validClabe(m.group())) { clabe = m.group(); choices.add("Copiar CLABE"); }
        m = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(selected);
        if (m.find()) { email = m.group(); choices.add("Correo"); }
        if (choices.isEmpty()) {
            show("Sin acciones reconocidas", "Corregir", "Cerrar"); return;
        }
        choices.add("Cerrar");
        show(choices.toArray(new String[0]));
    }
    private static boolean validClabe(String value) {
        int[] weights = {3,7,1};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += ((value.charAt(i)-'0') * weights[i%3]) % 10;
        return (10 - (sum % 10)) % 10 == value.charAt(17)-'0';
    }
    private void openCalendar() {
        Intent i = new Intent(Intent.ACTION_INSERT);
        i.setData(CalendarContract.Events.CONTENT_URI);
        i.putExtra(CalendarContract.Events.TITLE, selected.length() > 160 ? selected.substring(0,160) : selected);
        // No invented date or time: Calendar handles user's final input and confirmation.
        launch(i);
    }
    private void launch(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ime.startActivity(intent); }
        catch (Exception e) { show("No hay aplicación compatible", "Cerrar"); }
    }
    private void requestGemini(String action) {
        final String text = currentSelection();
        if (text.isEmpty()) { show("Selecciona texto para IA", "Cerrar"); return; }
        if (text.length() > 4000) { show("Límite: 4000 caracteres", "Cerrar"); return; }
        if (text.matches("(?s).*\\b[0-9]{18}\\b.*")) {
            show("No enviar números bancarios a la nube", "Cerrar"); return;
        }
        final String apiKey = AiSettingsActivity.readKey(ime);
        if (apiKey.isEmpty()) { show("Configurar", "Cerrar"); return; }
        final int request = ++generation;
        final InputConnection editor = ime.getCurrentInputConnection();
        selected = text;
        proposed = "";
        show("⏳ Gemini...", "Cancelar");
        final String model = AiSettingsActivity.readModel(ime);
        worker.submit(() -> {
            try {
                String instruction = "Corregir".equals(action)
                    ? "Corrige ortografía y puntuación en español. Conserva exactamente el significado. Devuelve solo el resultado."
                    : "Traducir".equals(action)
                    ? "Traduce al inglés si el texto está en español, o al español si está en inglés. Devuelve solo la traducción."
                    : "Reescribe el texto de forma profesional, natural y breve sin inventar datos. Devuelve solo el resultado.";
                JSONObject part = new JSONObject().put("text", instruction + "\n\nTexto:\n" + text);
                JSONObject body = new JSONObject().put("contents",
                    new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(part))));
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" +
                    Uri.encode(model) + ":generateContent");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setConnectTimeout(12000); conn.setReadTimeout(25000);
                    conn.setRequestMethod("POST"); conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("x-goog-api-key", apiKey);
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int status = conn.getResponseCode();
                    if (status != 200) throw new Exception("Gemini HTTP " + status);
                    StringBuilder response = new StringBuilder();
                    try (InputStream in = conn.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line; while ((line = reader.readLine()) != null) {
                            if (response.length() > 64000) throw new Exception("Respuesta demasiado larga");
                            response.append(line);
                        }
                    }
                    JSONObject json = new JSONObject(response.toString());
                    String value = json.getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
                    main.post(() -> {
                        if (generation != request || ime.getCurrentInputConnection() != editor
                            || !text.equals(currentSelection())) return;
                        proposed = value;
                        String preview = value.length() > 34 ? value.substring(0,34) + "…" : value;
                        show("Vista:" + preview, "Usar", "Copiar resultado", "Cancelar");
                    });
                } finally { conn.disconnect(); }
            } catch (Exception error) {
                final String reason = error.getMessage() == null ? "Error de red" : error.getMessage();
                main.post(() -> { if (generation == request) show(reason.length() > 28 ?
                    reason.substring(0,28) : reason, "Cancelar"); });
            }
        });
    }
}
