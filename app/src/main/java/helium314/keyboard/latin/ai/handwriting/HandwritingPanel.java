// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.handwriting;
import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import helium314.keyboard.keyboard.gifs.InternalInputPanel;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.ai.panels.PanelDraft;
import helium314.keyboard.latin.ai.panels.PanelHost;
import helium314.keyboard.latin.ai.panels.PanelViews;
/** Optional downloaded offline ink model; candidates require explicit selection. */
public final class HandwritingPanel extends LinearLayout implements InternalInputPanel {
 private final PanelHost host;private final PanelDraft session;private final InkPadView pad;
 private final TextView status;private final LinearLayout candidates;private final Button download,recognize,language;
 private DigitalInkEngine engine;private String tag="es-MX";private boolean closed,ready;private long generation,modelGeneration;
 public HandwritingPanel(Context c,PanelHost host){
  super(c);this.host=host;session=new PanelDraft(host.editorToken());PanelViews.style(this);
  LinearLayout top=PanelViews.row(c);addView(top);PanelViews.button(top,"ABC",host::closePanel);
  language=PanelViews.button(top,"Español",()->{tag="es-MX".equals(tag)?"en-US":"es-MX";languageLabel();setup();});
  PanelViews.button(top,"Deshacer trazo",()->padUndo());PanelViews.button(top,"Limpiar",()->padClear());
  status=PanelViews.label(c,"Escribe una línea y elige un resultado");status.setTextSize(13);addView(status);
  candidates=PanelViews.row(c);addView(candidates);
  pad=new InkPadView(c,this::invalidateCandidates);addView(pad,new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0,1));
  LinearLayout actions=PanelViews.row(c);addView(actions);
  download=PanelViews.button(actions,"Descargar modelo (~20 MB)",this::download);
  recognize=PanelViews.button(actions,"Reconocer",this::recognize);
  setup();
 }
 private void languageLabel(){language.setText("es-MX".equals(tag)?"Español":"English");}
 private void padUndo(){pad.undo();}private void padClear(){pad.clear();}
 private boolean current(long ticket){return !closed&&ticket==generation&&session.current(host.editorToken());}
 private void invalidateCandidates(){generation++;candidates.removeAllViews();if(recognize!=null)recognize.setEnabled(ready);}
 private void setup(){
  modelGeneration++;invalidateCandidates();pad.clear();ready=false;download.setEnabled(false);recognize.setEnabled(false);if(engine!=null){engine.close();engine=null;}
  try{
   if(Os.sysconf(OsConstants._SC_PAGESIZE)>4096){status.setText("Escritura a mano no disponible en dispositivos con páginas de 16 KB");return;}
   engine=new DigitalInkEngine(tag);final long ticket=modelGeneration;status.setText("Comprobando modelo local…");
   engine.available((found,error)->{if(closed||ticket!=modelGeneration||!session.current(host.editorToken()))return;ready=found;download.setEnabled(!found);recognize.setEnabled(found);status.setText(found?"Modelo listo · reconocimiento sin conexión":"Descarga el modelo una vez para reconocer sin conexión");});
  }catch(Exception|LinkageError error){status.setText("Reconocimiento local no disponible en este dispositivo");}
 }
 private void download(){if(closed||engine==null)return;final long ticket=modelGeneration;download.setEnabled(false);status.setText("Descargando modelo…");engine.download((found,error)->{if(closed||ticket!=modelGeneration||!session.current(host.editorToken()))return;ready=found;download.setEnabled(!found);recognize.setEnabled(found);status.setText(found?"Modelo listo · dibuja una línea":"No se descargó. Revisa conexión y pulsa Descargar para reintentar.");});}
 private void recognize(){
  if(closed||!ready||engine==null||pad.strokes().isEmpty())return;final long ticket=generation;recognize.setEnabled(false);status.setText("Reconociendo en el dispositivo…");
  engine.recognize(pad.strokes(),pad.getWidth(),pad.getHeight(),(texts,error)->{
   if(!current(ticket))return;recognize.setEnabled(true);candidates.removeAllViews();status.setText(error!=null?"No se pudo reconocer. Reintenta.":texts.isEmpty()?"Sin resultados. Prueba con otra escritura.":"Toca un resultado para insertarlo");
   for(String value:texts)PanelViews.button(candidates,value,()->{if(current(ticket)&&host.commitText(value))host.closePanel();});
  });
 }
 @Override public void onTextInput(String ignored){} // Full overlay consumes typing; no host-editor leakage.
 @Override public void onCodeInput(int code){if(code==KeyCode.DELETE&&!closed)pad.undo();}
 @Override public void close(){if(closed)return;closed=true;generation++;session.close();if(engine!=null){engine.close();engine=null;}pad.clear();candidates.removeAllViews();}
}
