// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.cloud;
import android.app.Activity;import android.content.*;import android.os.*;import android.widget.*;import android.view.WindowManager;
import android.accounts.AccountManager;import com.google.android.gms.auth.api.identity.*;import com.google.android.gms.common.api.Scope;
import java.util.*;import java.util.concurrent.*;
/** Initial consent and portable recovery unlock. No access token or recovery phrase in preferences. */
public final class TypingCloudActivity extends Activity{
 private final ExecutorService background=Executors.newSingleThreadExecutor();private EditText phrase;private TextView status;
 @Override public void onCreate(Bundle b){helium314.keyboard.latin.ai.AiMenuTheme.applyActivity(this);super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  LinearLayout stack=new LinearLayout(this);stack.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);stack.setPadding(pad,pad,pad,pad);
  TextView title=new TextView(this);title.setText("Personal Typing Cloud");title.setTextSize(26);stack.addView(title);
  TextView detail=new TextView(this);detail.setText("Solo asociaciones de palabras aprendidas y rechazos, cifrados antes de subir a Drive privado. Guarda fuera del teléfono una frase larga de recuperación (mínimo 20 caracteres). Usa la misma frase en tus dispositivos. Nunca se envían API keys, conversaciones, audio ni pulsaciones. La sincronización comienza tras autorizar y desbloquear. Se necesita registrar este paquete y certificado en Google Cloud y activar Drive API.");stack.addView(detail);
  phrase=new com.google.android.material.textfield.TextInputEditText(this);phrase.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);phrase.setHint("Frase de recuperación");stack.addView(phrase);
  status=new TextView(this);status.setText(TypingSync.prefs(this).getString("status","Sin configurar"));stack.addView(status);
  Button enable=new com.google.android.material.button.MaterialButton(this);enable.setText("Autorizar Google y activar respaldo");enable.setEnabled(Build.VERSION.SDK_INT>=26);
  enable.setOnClickListener(v->{
   if(phrase.length()<20){status.setText("Escribe una frase larga y guárdala para recuperar tus datos.");return;}
   startActivityForResult(AccountManager.newChooseAccountIntent(null,null,new String[]{"com.google"},null,null,null,null),710);
  });stack.addView(enable);
  Button disable=new com.google.android.material.button.MaterialButton(this);disable.setText("Desactivar y revocar Google Drive");disable.setOnClickListener(v->{
   TypingSync.disable(this);String account=TypingSync.prefs(this).getString("account","");
   if(!account.isEmpty())Identity.getAuthorizationClient(this).revokeAccess(RevokeAccessRequest.builder()
    .setAccount(new android.accounts.Account(account,"com.google")).setScopes(Collections.singletonList(new Scope(DriveAppData.SCOPE))).build())
    .addOnSuccessListener(unused->{TypingSync.prefs(this).edit().remove("account").apply();status.setText("Acceso revocado; respaldo remoto conservado.");})
    .addOnFailureListener(error->status.setText("Sincronización detenida. Revoca acceso también desde tu cuenta Google si no hay conexión."));
   background.execute(()->RecoveryVault.erase(TypingSync.storage(this)));
  });stack.addView(disable);
  ScrollView scroll=new ScrollView(this);scroll.addView(stack);setContentView(scroll);
  helium314.keyboard.latin.ai.AiMenuTheme.resolve(this).styleSurface(stack);
 }
 @Override protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);if(result!=RESULT_OK||data==null)return;
  if(code==710){
   String account=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);if(account==null||account.isEmpty())return;
   TypingSync.prefs(this).edit().putString("account",account).apply();
   Identity.getAuthorizationClient(this).authorize(TypingSync.request(this)).addOnSuccessListener(auth->{
    if(auth.hasResolution()){try{startIntentSenderForResult(auth.getPendingIntent().getIntentSender(),711,null,0,0,0);}catch(IntentSender.SendIntentException e){status.setText("No se pudo abrir OAuth");}}
    else activate(auth);
   }).addOnFailureListener(error->status.setText("OAuth no disponible. Verifica registro Android del paquete/certificado y Drive API."));
  }else if(code==711){try{activate(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));}catch(Exception e){status.setText("Autorización no completada");}}
 }
 private void activate(AuthorizationResult authorization){
  if(authorization.getAccessToken()==null){status.setText("Google no autorizó Drive");return;}
  char[] secret=new char[phrase.length()];phrase.getText().getChars(0,phrase.length(),secret,0);phrase.setText("");
  background.execute(()->{try{
   Context c=TypingSync.storage(this);RecoveryVault.save(c,secret);
   TypingSync.prefs(c).edit().putBoolean("enabled",true).putString("status","Autorizado; verificando respaldo y frase").commit();
   TypingSync.activate(c);runOnUiThread(()->status.setText("Sincronización programada. Revisa el estado al volver a esta pantalla."));
  }catch(Exception e){runOnUiThread(()->status.setText("No se pudo proteger la frase; sincronización desactivada."));}
  finally{Arrays.fill(secret,'\0');}});
 }
 @Override protected void onDestroy(){background.shutdown();super.onDestroy();}
}
