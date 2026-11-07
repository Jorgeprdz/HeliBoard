// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;

import android.content.Context;
import android.widget.*;
import helium314.keyboard.keyboard.gifs.InternalInputPanel;
import helium314.keyboard.latin.ai.panels.PanelViews;

/** One surface for listening, processing and recoverable errors; never a provisional editor. */
public final class DictationPanel extends LinearLayout implements InternalInputPanel {
    public interface Actions {void finish();void cancel();void literal();void edit();void retry();void settings();}
    private final Actions actions;private final TextView state;private final Button finish,edit,retry;private boolean closed;
    public DictationPanel(Context context,Actions actions){
        super(context);this.actions=actions;PanelViews.style(this);
        LinearLayout header=PanelViews.row(context);PanelViews.button(header,"ABC",actions::cancel);header.addView(PanelViews.label(context,"Dictado inteligente"),new LayoutParams(0,-2,2));PanelViews.button(header,"Ajustes",actions::settings);addView(header);
        ScrollView scroll=new ScrollView(context);LinearLayout content=new LinearLayout(context);content.setOrientation(VERTICAL);content.setPadding(12,12,12,12);scroll.addView(content);addView(scroll,new LayoutParams(-1,0,1));
        state=PanelViews.label(context,"Habla con naturalidad. El texto aparecerá al terminar.");state.setTextSize(20);content.addView(state);
        content.addView(PanelViews.label(context,"La edición por voz se activa con Editar texto. En modo dictado, tus palabras se conservan como contenido."));
        LinearLayout modes=PanelViews.row(context);edit=PanelViews.button(modes,"Editar texto",actions::edit);PanelViews.button(modes,"Dictado literal",actions::literal);addView(modes);
        LinearLayout footer=PanelViews.row(context);finish=PanelViews.button(footer,"Terminar",actions::finish);retry=PanelViews.button(footer,"Volver a dictar",actions::retry);retry.setVisibility(GONE);PanelViews.button(footer,"Cancelar",actions::cancel);addView(footer);
    }
    public void listening(boolean editing){state.setText(editing?"Di el cambio que quieres hacer en el texto seleccionado o en tu borrador.":"Escuchando… Habla con naturalidad; termina cuando estés listo.");finish.setEnabled(true);edit.setEnabled(!editing);retry.setVisibility(GONE);}
    public void processing(){state.setText("Procesando el audio…");finish.setEnabled(false);edit.setEnabled(false);retry.setVisibility(GONE);}
    public void error(String value){state.setText(value+" La escritura normal sigue disponible.");finish.setEnabled(false);edit.setEnabled(true);retry.setVisibility(VISIBLE);}
    @Override public void onCodeInput(int code){}
    @Override public void onTextInput(String text){}
    @Override public void close(){if(closed)return;closed=true;actions.cancel();}
}
