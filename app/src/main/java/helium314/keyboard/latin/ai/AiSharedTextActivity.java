// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.app.*;import android.content.*;import android.os.*;import android.view.WindowManager;import android.widget.*;
import helium314.keyboard.latin.ai.provider.*;
/** Only explicit OS-selected/shared text. No clipboard polling, entity extraction or external actions. */
public final class AiSharedTextActivity extends Activity {
 private final AiSessionCoordinator jobs=new AiSessionCoordinator(r->new Handler(Looper.getMainLooper()).post(r));
 private String source="",proposed="";private boolean complete;private TextView result,status;private Button accept,copyResult;private Spinner language;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView label(LinearLayout parent,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setPadding(0,dp(8),0,dp(8));parent.addView(t);return t;}
 private Button button(LinearLayout parent,String title,Runnable action){Button b=new com.google.android.material.button.MaterialButton(this);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(48));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->action.run());return b;}
 @Override public void onCreate(Bundle state){
  AiMenuTheme.applyActivity(this);super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  String action=getIntent().getAction();CharSequence text=getIntent().getCharSequenceExtra(Intent.ACTION_PROCESS_TEXT.equals(action)?Intent.EXTRA_PROCESS_TEXT:Intent.EXTRA_TEXT);
  if(!ExplicitTextPolicy.accepts(action,getIntent().getType(),text)){finish();return;}source=text.toString();
  ScrollView scroll=new ScrollView(this);LinearLayout stack=new LinearLayout(this);stack.setOrientation(LinearLayout.VERTICAL);stack.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(stack);
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
  label(stack,"Texto seleccionado",26);TextView original=label(stack,source,16);original.setTextIsSelectable(true);original.setSaveEnabled(false);
  status=label(stack,"Elige una acción. Se envía únicamente este texto a Google con tu autorización.",14);
  button(stack,"Corregir",()->request(GeminiOperation.SPELLING));
  button(stack,"Reformular",()->request(GeminiOperation.REWRITE));
  label(stack,"Traducir a",15);language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"English US","Español México"}));stack.addView(language);
  button(stack,"Traducir",()->request(GeminiOperation.TRANSLATE));
  button(stack,"Permisos de acciones con Gemini",()->startActivity(new Intent(this,AiSettingsActivity.class).putExtra("section","advanced")));
  result=label(stack,"",16);result.setTextIsSelectable(true);result.setSaveEnabled(false);
  accept=button(stack,"Aceptar resultado y volver",()->{if(complete&&!proposed.isEmpty()&&ExplicitTextPolicy.mayReturn(getIntent().getAction(),getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true))){setResult(RESULT_OK,new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT,proposed));finish();}});accept.setEnabled(false);
  accept.setVisibility(ExplicitTextPolicy.mayReturn(action,getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true))?android.view.View.VISIBLE:android.view.View.GONE);
  copyResult=button(stack,"Copiar resultado",()->{if(complete&&!proposed.isEmpty())copy(proposed);});copyResult.setEnabled(false);
  button(stack,"Cancelar",()->{setResult(RESULT_CANCELED);finish();});
  AiMenuTheme.resolveKeyboard(this).styleSurface(stack);setContentView(scroll);androidx.core.view.ViewCompat.requestApplyInsets(scroll);
 }
 private void request(GeminiOperation operation){
  if(!AiSettingsActivity.networkAllowed(this)){status.setText("Autoriza las acciones manuales en Permisos de acciones con Gemini. No se ha enviado el texto.");return;}
  String caller=getCallingPackage();if(caller!=null&&AiSettingsActivity.excludedApp(this,caller)){status.setText("Aplicación excluida.");return;}
  if(SensitiveInputPolicy.sensitiveText(source)){status.setText("Texto sensible: no se envía a Google.");return;}
  String key=AiSettingsActivity.readKey(this),model=AiSettingsActivity.readModel(this);if(key.isEmpty()||model.isEmpty()){status.setText("Configura la clave y comprueba la conexión en Gemini.");return;}
  final String destination=language.getSelectedItemPosition()==0?"en-US":"es-MX";
  complete=false;proposed="";accept.setEnabled(false);copyResult.setEnabled(false);result.setText("");status.setText("Procesando…");final boolean[] finished={false};
  jobs.submit(cancel->{GeminiProvider.Result response=new GeminiProvider().generate(key,model,operation,"",destination,source,new GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}});finished[0]=response.complete;return response.text;},(value,error)->{
   if(isFinishing()||isDestroyed()||!AiSettingsActivity.networkAllowed(this))return;
   String currentCaller=getCallingPackage();if(currentCaller!=null&&AiSettingsActivity.excludedApp(this,currentCaller))return;
   if(error!=null){status.setText(AiConnectionStatus.explain(error instanceof GeminiProvider.ProviderException?((GeminiProvider.ProviderException)error).error.name():"CONNECTION_CHECK"));return;}
   proposed=value==null?"":value;complete=finished[0]&&proposed.length()<=16384;result.setText(proposed);accept.setEnabled(complete&&!proposed.isEmpty());copyResult.setEnabled(complete&&!proposed.isEmpty());
   AiResultReview check=new AiResultReview();check.offer(new EditorSnapshot(this,getPackageName(),0,0,0,0,source.length(),source),proposed,complete);
   status.setText(!complete?"Resultado incompleto; no puede reemplazar el original.":check.meaningRisk()?"Revisa cifras y negaciones antes de aceptar.":"Revisa el resultado antes de aceptar.");
  },35000);
 }
 private void copy(String value){ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(manager==null)return;ClipData clip=ClipData.newPlainText("Texto explícito",value);if(Build.VERSION.SDK_INT>=24&&SensitiveInputPolicy.sensitiveText(value)){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}manager.setPrimaryClip(clip);}
 @Override protected void onPause(){jobs.cancel();complete=false;proposed="";if(accept!=null)accept.setEnabled(false);if(copyResult!=null)copyResult.setEnabled(false);if(result!=null)result.setText("");super.onPause();}
 @Override protected void onDestroy(){jobs.close();source="";proposed="";super.onDestroy();}
}
