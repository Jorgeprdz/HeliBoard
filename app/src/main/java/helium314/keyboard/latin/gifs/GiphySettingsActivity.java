// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;

/** Initial setup only. Ordinary GIF browsing never opens this Activity. */
public final class GiphySettingsActivity extends Activity {
    @Override public void onCreate(Bundle state){
        helium314.keyboard.latin.ai.AiMenuTheme.applyActivity(this);super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(20*getResources().getDisplayMetrics().density);content.setPadding(padding,padding,padding,padding);
        TextView title=new TextView(this);title.setText("GIF y stickers");title.setTextSize(28);content.addView(title);
        TextView description=new TextView(this);
        description.setText("GIPHY · Configuración inicial\nLa búsqueda y las vistas previas se conectan a GIPHY. Se envía únicamente la consulta de este panel; no el borrador del chat. Necesitas tu propia clave de developers.giphy.com. Las claves beta tienen cuota de 100 llamadas por hora. Producción puede requerir aprobación y pago. Android 6 o superior permite guardar la clave cifrada.");
        content.addView(description);
        EditText key=new EditText(this);key.setSingleLine(true);key.setSaveEnabled(false);
        if(android.os.Build.VERSION.SDK_INT>=26)key.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint("Clave API de GIPHY");content.addView(key);
        TextView status=new TextView(this);content.addView(status);
        Button save=new Button(this);save.setText("Guardar y permitir búsquedas en GIPHY");
        save.setOnClickListener(v->{if(GiphySettings.saveKey(this,key.getText().toString().trim())){key.setText("");status.setText("Clave cifrada guardada. Vuelve al teclado y abre GIF.");}else status.setText("No se pudo guardar. Comprueba formato, desbloqueo del dispositivo y Android 6 o superior.");});content.addView(save);
        Button erase=new Button(this);erase.setText("Borrar clave y desactivar conexión");
        erase.setOnClickListener(v->{GiphySettings.forget(this);key.setText("");status.setText("Clave eliminada.");});content.addView(erase);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
        helium314.keyboard.latin.ai.AiMenuTheme.resolve(this).styleSurface(content);
    }
}
