// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.provider.*;import android.view.WindowManager;import android.widget.*;
import helium314.keyboard.latin.ai.entities.*;import helium314.keyboard.latin.ai.provider.*;import java.util.*;
/** Only OS-delivered, explicitly selected/shared text. No IME, clipboard polling or Accessibility. */
public final class AiSharedTextActivity extends Activity {
 private final AiSessionCoordinator jobs=new AiSessionCoordinator(r->new Handler(Looper.getMainLooper()).post(r));
 private String source="",proposed="";private boolean complete;private TextView result,status;private Button accept;private Spinner tone,language;
 private final String[] tones={"Professional","Casual","Friendly","Formal","Direct","Concise","Persuasive","Consultative"};
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  String action=getIntent().getAction();CharSequence text=getIntent().getCharSequenceExtra(Intent.ACTION_PROCESS_TEXT.equals(action)?Intent.EXTRA_PROCESS_TEXT:Intent.EXTRA_TEXT);
  if(!ExplicitTextPolicy.accepts(action,getIntent().getType(),text)){finish();return;}source=text.toString();
  ScrollView scroll=new ScrollView(this);LinearLayout stack=new LinearLayout(this);stack.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);stack.setPadding(pad,pad,pad,pad);scroll.addView(stack);
  TextView original=new TextView(this);original.setText(source);original.setTextIsSelectable(true);original.setSaveEnabled(false);stack.addView(original);
  status=new TextView(this);stack.addView(status);
  tone=new Spinner(this);tone.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,tones));stack.addView(tone);
  language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Español México","English US"}));stack.addView(language);
  String[] labels={"Ortografía","Gramática","Reescribir","Resumir","Traducir","Acortar","Expandir","Claridad","Generar respuesta"};GeminiOperation[] actions=GeminiOperation.values();
  Button tools=new Button(this);tools.setText("Elegir herramienta Gemini (envía este texto a Google)");tools.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Acción explícita de Gemini").setItems(labels,(d,i)->request(actions[i])).setNegativeButton("Cancelar",null).show());stack.addView(tools);
  Button settings=new Button(this);settings.setText("Configurar IA");settings.setOnClickListener(v->startActivity(new Intent(this,AiSettingsActivity.class)));stack.addView(settings);
  for(EntityRecognizer.Entity entity:new EntityRecognizer().scan(source)){
   Button copy=new Button(this);copy.setText("Copiar "+entity.kind+": "+entity.value);copy.setOnClickListener(v->copy(entity.value));stack.addView(copy);
   if(entity.kind==EntityRecognizer.Kind.PHONE||entity.kind==EntityRecognizer.Kind.ADDRESS||entity.kind==EntityRecognizer.Kind.URL||entity.kind==EntityRecognizer.Kind.EMAIL||entity.kind==EntityRecognizer.Kind.DATE){Button open=new Button(this);open.setText("Abrir acción: "+entity.value);open.setOnClickListener(v->open(entity));stack.addView(open);}
   if(entity.kind==EntityRecognizer.Kind.ADDRESS){Button directions=new Button(this);directions.setText("Obtener indicaciones: "+entity.value);directions.setOnClickListener(v->directions(entity.value));stack.addView(directions);}
  }
  result=new TextView(this);result.setTextIsSelectable(true);result.setSaveEnabled(false);stack.addView(result);
  accept=new Button(this);accept.setText("Aceptar resultado y volver");accept.setEnabled(false);
  accept.setVisibility(ExplicitTextPolicy.mayReturn(action,getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true))?android.view.View.VISIBLE:android.view.View.GONE);
  accept.setOnClickListener(v->{if(complete&&!proposed.isEmpty()&&ExplicitTextPolicy.mayReturn(getIntent().getAction(),getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true))){setResult(RESULT_OK,new Intent().putExtra(Intent.EXTRA_PROCESS_TEXT,proposed));finish();}});stack.addView(accept);
  Button copy=new Button(this);copy.setText("Copiar resultado");copy.setOnClickListener(v->{if(!proposed.isEmpty())copy(proposed);});stack.addView(copy);
  Button cancel=new Button(this);cancel.setText("Cancelar");cancel.setOnClickListener(v->{setResult(RESULT_CANCELED);finish();});stack.addView(cancel);setContentView(scroll);
 }
 private void request(GeminiOperation operation){
  if(!AiSettingsActivity.networkAllowed(this)){status.setText("Activa IA y el consentimiento de red en Configurar.");return;}
  String caller=getCallingPackage();if(caller!=null&&AiSettingsActivity.excludedApp(this,caller)){status.setText("Aplicación excluida.");return;}
  if(SensitiveInputPolicy.sensitiveText(source)){status.setText("Texto sensible: no se envía a Google.");return;}
  String key=AiSettingsActivity.readKey(this),model=AiSettingsActivity.readModel(this);if(key.isEmpty()||model.isEmpty()){status.setText("Configura una clave y elige un modelo disponible.");return;}
  final String selectedTone=tones[tone.getSelectedItemPosition()],destination=language.getSelectedItemPosition()==0?"es-MX":"en-US";
  complete=false;proposed="";accept.setEnabled(false);result.setText("");status.setText("Procesando…");final boolean[] finished={false};
  jobs.submit(cancel->{GeminiProvider.Result response=new GeminiProvider().generate(key,model,operation,selectedTone,destination,source,new GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}});finished[0]=response.complete;return response.text;},(value,error)->{
   if(isFinishing()||isDestroyed()||!AiSettingsActivity.networkAllowed(this))return;
   if(error!=null){status.setText("No fue posible procesar. Revisa conexión, modelo, clave y cuota.");return;}
   proposed=value==null?"":value;complete=finished[0]&&proposed.length()<=16384;result.setText(proposed);accept.setEnabled(complete&&!proposed.isEmpty());
   AiResultReview check=new AiResultReview();check.offer(new EditorSnapshot(this,getPackageName(),0,0,0,0,source.length(),source),proposed,complete);
   status.setText(!complete?"Resultado incompleto; no puede reemplazar el original.":check.meaningRisk()?"Revisa cifras y negaciones antes de aceptar.":"Revisa todo el resultado antes de aceptar.");
  },35000);
 }
 private void copy(String value){ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(manager==null)return;ClipData clip=ClipData.newPlainText("Texto explícito",value);if(Build.VERSION.SDK_INT>=24&&SensitiveInputPolicy.sensitiveText(value)){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}manager.setPrimaryClip(clip);}
 private void open(EntityRecognizer.Entity entity){Intent i;
  switch(entity.kind){
   case PHONE:i=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+entity.value));break;
   case ADDRESS:i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(entity.value)));break;
   case EMAIL:i=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+entity.value));break;
   case URL:i=new Intent(Intent.ACTION_VIEW,Uri.parse(entity.value));break;
   case DATE:i=calendarIntent();break;
   default:return;
  }
  try{startActivity(i);}catch(ActivityNotFoundException e){status.setText("No hay una aplicación compatible.");}
 }
 private void directions(String address){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(address))));}catch(ActivityNotFoundException e){status.setText("No hay una aplicación de navegación compatible.");}}
 private Intent calendarIntent(){
  Intent intent=new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE,"Evento");
  String date="",time="",location="",duration="";int dates=0,times=0,durations=0;
  for(EntityRecognizer.Entity item:new EntityRecognizer().scan(source)){
   if(item.kind==EntityRecognizer.Kind.DATE){date=item.value;dates++;}
   else if(item.kind==EntityRecognizer.Kind.TIME){time=item.value;times++;}
   else if(item.kind==EntityRecognizer.Kind.DURATION){duration=item.value;durations++;}
   else if(item.kind==EntityRecognizer.Kind.ADDRESS&&location.isEmpty())location=item.value;
  }
  if(!location.isEmpty())intent.putExtra(CalendarContract.Events.EVENT_LOCATION,location);
  Long start=dates==1&&times==1?CalendarResolver.resolve(date,time,System.currentTimeMillis(),TimeZone.getDefault()):null;
  if(start!=null){intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start.longValue());Long length=durations==1?CalendarResolver.durationMillis(duration):null;if(length!=null)intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,start+length);}
  else intent.putExtra(CalendarContract.Events.DESCRIPTION,"Confirmar fecha y hora detectadas: "+date+" "+time);
  return intent;
 }
 @Override protected void onPause(){jobs.cancel();complete=false;proposed="";if(accept!=null)accept.setEnabled(false);if(result!=null)result.setText("");super.onPause();}
 @Override protected void onDestroy(){jobs.close();source="";proposed="";super.onDestroy();}
}
