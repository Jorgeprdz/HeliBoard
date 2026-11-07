// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.panels;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import helium314.keyboard.keyboard.gifs.InternalInputPanel;
import helium314.keyboard.latin.ai.AiSessionCoordinator;
import helium314.keyboard.latin.ai.AiSettingsActivity;
import helium314.keyboard.latin.ai.SensitiveInputPolicy;
import helium314.keyboard.latin.ai.provider.GeminiOperation;
import helium314.keyboard.latin.ai.provider.GeminiProvider;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;

/** Keyboard-local draft. Typing never calls the application's InputConnection. */
public final class TranslationPanel extends LinearLayout implements InternalInputPanel {
 private final PanelHost host;
 private final PanelDraft draft;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final AiSessionCoordinator jobs=new AiSessionCoordinator(action->main.post(action));
 private final TextView source,preview,status;
 private final Button use,language;
 private String target="en-US";
 private boolean closed,shift,symbols;
 private final LinearLayout keyRows;
 private final Runnable translate=this::translate;
 public TranslationPanel(Context context,PanelHost host){
  super(context);this.host=host;draft=new PanelDraft(host.editorToken());PanelViews.style(this);
  LinearLayout top=PanelViews.row(context);addView(top);
  PanelViews.button(top,"ABC",host::closePanel);
  language=PanelViews.button(top,"ES → EN",()->{target="en-US".equals(target)?"es-MX":"en-US";languageLabel();changed();});
  PanelViews.button(top,"Limpiar",()->{draft.setSource("");changed();});
  ScrollView reviewScroll=new ScrollView(context);reviewScroll.setFillViewport(true);addView(reviewScroll,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0,1));
  LinearLayout review=new LinearLayout(context);review.setOrientation(VERTICAL);reviewScroll.addView(review);
  source=PanelViews.label(context,"Escribe aquí · Gemini traduce al escribir");review.addView(source);
  preview=PanelViews.label(context,"");review.addView(preview);
  status=PanelViews.label(context,"Vista previa con Gemini al escribir");status.setTextSize(12);review.addView(status);
  LinearLayout actions=PanelViews.row(context);addView(actions);
  PanelViews.button(actions,"Traducir",this::translate);
  use=PanelViews.button(actions,"Usar traducción",this::apply);use.setEnabled(false);
  keyRows=new LinearLayout(context);keyRows.setOrientation(VERTICAL);addView(keyRows);rebuildKeys();
  LinearLayout bottom=PanelViews.row(context);addView(bottom);
  PanelViews.button(bottom,"⇧",()->{shift=!shift;rebuildKeys();});PanelViews.button(bottom,"123",()->{symbols=!symbols;rebuildKeys();});
  PanelViews.button(bottom,"Espacio",()->onTextInput(" "));PanelViews.button(bottom,".",()->onTextInput("."));PanelViews.button(bottom,"⌫",()->onCodeInput(KeyCode.DELETE));
 }
 private void rebuildKeys(){
  keyRows.removeAllViews();
  String[] rows=symbols?new String[]{"1234567890","@#€%&*()-",",?!:;'/áéíóú"}:new String[]{"qwertyuiop","asdfghjklñ","zxcvbnm"};
  for(String keys:rows){LinearLayout row=PanelViews.row(getContext());keyRows.addView(row);for(int i=0;i<keys.length();i++){String key=keys.substring(i,i+1);final String value=shift?key.toUpperCase(java.util.Locale.ROOT):key;PanelViews.button(row,value,()->onTextInput(value));}}
 }
 private void languageLabel(){language.setText("en-US".equals(target)?"ES → EN":"EN → ES");}
 @Override public void onTextInput(String text){if(closed)return;draft.append(text);changed();}
 @Override public void onCodeInput(int code){if(closed)return;if(code==KeyCode.DELETE)draft.delete();else if(code=='\n')draft.append("\n");else if(Character.isValidCodePoint(code)&&code>0)draft.append(new String(Character.toChars(code)));changed();}
 private void changed(){
  jobs.cancel();main.removeCallbacks(translate);draft.invalidate();preview.setText("");use.setEnabled(false);source.setText(draft.source().isEmpty()?"Escribe aquí…":draft.source());
  status.setText(draft.source().isEmpty()?"Vista previa con Gemini al escribir":"Traduciendo al dejar de escribir…");
  if(!draft.source().trim().isEmpty())main.postDelayed(translate,700);
 }
 private void translate(){
  main.removeCallbacks(translate);if(closed||draft.source().trim().isEmpty())return;
  if(!draft.current(host.editorToken())){close();host.closePanel();return;}
  if(!host.canUseNetwork()||(!AiSettingsActivity.networkAllowed(getContext())&&!AiSettingsActivity.automaticNetworkAllowed(getContext()))){status.setText("Activa Gemini y el consentimiento de red en ajustes");return;}
  if(SensitiveInputPolicy.sensitiveText(draft.source())){status.setText("Texto protegido: no se envía");return;}
  String key=AiSettingsActivity.readKey(getContext());if(key.isEmpty()){status.setText("Configura tu clave de Gemini en ajustes");return;}
  final String text=draft.source(),model=AiSettingsActivity.readModel(getContext()),language=target;
  final long ticket=draft.ticket();final boolean[] complete={false};status.setText("Gemini…");use.setEnabled(false);
  jobs.submit(cancel->{GeminiProvider.Result r=new GeminiProvider().generate(key,model,GeminiOperation.TRANSLATE,"Professional",language,text,new GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}});complete[0]=r.complete;return r.text;},(value,error)->{
   if(closed||!draft.current(host.editorToken())||!host.canUseNetwork())return;
   if(error!=null){status.setText("No se pudo traducir. Revisa conexión, clave o cuota y reintenta.");return;}
   if(draft.offer(ticket,host.editorToken(),value,complete[0])){preview.setText(value);status.setText("Revisa y pulsa Usar traducción");use.setEnabled(true);}else status.setText("Traducción incompleta. Reintenta.");
  },35000);
 }
 private void apply(){String value=draft.result(host.editorToken());if(closed||value.isEmpty()||!host.canUseNetwork())return;if(host.commitText(value))host.closePanel();else{use.setEnabled(false);status.setText("El editor cambió. Vuelve a abrir la traducción.");}}
 @Override public void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);jobs.close();draft.close();source.setText("");preview.setText("");use.setEnabled(false);}
}
