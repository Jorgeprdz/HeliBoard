// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gifs;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.gifs.*;
import helium314.keyboard.latin.settings.Settings;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Owns its query, results and keys entirely inside the IME. Never commits query text. */
public final class GifPanel extends LinearLayout implements InternalInputPanel {
    private final LatinIME ime;
    private final InputConnection connection;
    private final EditorInfo editor;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),new ThreadPoolExecutor.DiscardPolicy());
    private final List<Future<?>> work=new ArrayList<>();
    private final Set<GiphyClient> clients=Collections.synchronizedSet(new HashSet<>());
    private final List<GifPreviewView> previews=new ArrayList<>();
    private final GifPolicy.Generation generation=new GifPolicy.Generation();
    private final GifHistory history;
    private final TextView queryView,typingQuery,status;
    private final LinearLayout grid,browse,compact;
    private final Button next,gifTab,stickerTab;
    private final ScrollView results;
    private int browseScroll;
    private final StringBuilder query=new StringBuilder();
    private boolean closed,stickers,typing;private int offset;private long lastSearch;private String historyCategory;
    private int background,foreground,surface,accent;
    private static final int CONTROL_HEIGHT=40, CONTROL_RADIUS=12;
    public GifPanel(LatinIME ime){this(ime,ime);}
    GifPanel(Context context,LatinIME ime){
        super(context);this.ime=ime;connection=ime.getCurrentInputConnection();editor=ime.getCurrentInputEditorInfo();history=new GifHistory(context);
        setOrientation(VERTICAL);setClickable(true);
        try{background=Settings.getValues().mColors.get(ColorType.MAIN_BACKGROUND);foreground=Settings.getValues().mColors.get(ColorType.KEY_TEXT);}
        catch(RuntimeException unavailable){background=0xfff4f1f7;foreground=0xff29232d;}
        // Panel-only contrast surfaces derive from the keyboard palette; native keys are untouched.
        surface=androidx.core.graphics.ColorUtils.blendARGB(background,foreground,.075f);
        accent=androidx.core.graphics.ColorUtils.blendARGB(background,foreground,.14f);
        setBackgroundColor(background);
        browse=new LinearLayout(context);browse.setOrientation(VERTICAL);addView(browse,new LayoutParams(-1,-1));
        LinearLayout header=row();header.setPadding(dp(4),0,dp(4),0);
        fixedButton(header,"ABC","Volver al teclado",v->ime.closeInternalInputPanel(),44);
        queryView=label("⌕  Buscar GIF y stickers");queryView.setTag("gif-query");queryView.setTextSize(16);queryView.setSingleLine(true);
        queryView.setEllipsize(android.text.TextUtils.TruncateAt.END);queryView.setPadding(dp(14),0,dp(10),0);
        queryView.setBackground(shape(surface,CONTROL_RADIUS));queryView.setContentDescription("Buscar GIF y stickers");queryView.setOnClickListener(v->setTyping(true));
        LayoutParams queryParams=new LayoutParams(0,controlHeight(),1);queryParams.setMargins(dp(4),0,dp(4),0);header.addView(queryView,queryParams);
        fixedButton(header,"⚙","Configurar GIPHY",v->openSettings(),CONTROL_HEIGHT);browse.addView(header,new LayoutParams(-1,controlHeight()+dp(4)));
        HorizontalScrollView navigation=new HorizontalScrollView(context);navigation.setHorizontalScrollBarEnabled(true);navigation.setScrollbarFadingEnabled(false);navigation.setContentDescription("Categorías GIF: desliza horizontalmente para ver todas");
        LinearLayout tabs=row();tabs.setPadding(dp(6),dp(2),dp(6),dp(2));
        gifTab=fixedButton(tabs,"GIF","GIF",v->{stickers=false;updateSegments();search(0);},54);
        stickerTab=fixedButton(tabs,"Stickers","Stickers",v->{stickers=true;updateSegments();search(0);},80);
        fixedButton(tabs,"Tendencias","Tendencias",v->{query.setLength(0);updateQuery();search(0);},96);
        fixedButton(tabs,"Recientes","Recientes",v->showHistory("recent"),86);
        fixedButton(tabs,"Favoritos","Favoritos",v->showHistory("favorites"),86);
        navigation.addView(tabs,new HorizontalScrollView.LayoutParams(-2,-1));browse.addView(navigation,new LayoutParams(-1,controlHeight()+dp(8)));updateSegments();
        results=new ScrollView(context);results.setFillViewport(true);results.setClipToPadding(false);results.setTag("gif-scroll");
        grid=new LinearLayout(context);grid.setTag("gif-results");grid.setPadding(dp(8),dp(4),dp(8),dp(4));grid.setOrientation(VERTICAL);results.addView(grid,new ScrollView.LayoutParams(-1,-2));
        browse.addView(results,new LayoutParams(-1,0,1));
        next=new Button(context);style(next);next.setText("Más resultados");next.setOnClickListener(v->{if(historyCategory!=null)showHistory(historyCategory,offset+12);else search(offset+12);});next.setVisibility(GONE);
        LayoutParams moreParams=new LayoutParams(-1,controlHeight());moreParams.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(next,moreParams);
        LinearLayout footer=row();footer.setPadding(dp(12),0,dp(12),0);footer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        status=label("");status.setTextSize(10);status.setMaxLines(3);footer.addView(status,new LayoutParams(0,-2,1));
        ImageView mark=new ImageView(context);mark.setContentDescription("Powered By GIPHY");mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try(java.io.InputStream logo=context.getAssets().open("giphy/powered_by_"+(androidx.core.graphics.ColorUtils.calculateLuminance(background)<.45?"black":"white")+".png")){
            mark.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(logo,"GIPHY attribution"));
        }catch(java.io.IOException ignored){}
        footer.addView(mark,new LayoutParams(dp(82),dp(14)));browse.addView(footer,new LayoutParams(-1,-2));
        compact=row();compact.setTag("gif-native-search");compact.setBackgroundColor(background);compact.setGravity(android.view.Gravity.CENTER_VERTICAL);
        fixedButton(compact,"‹","Volver a resultados GIF",v->setTyping(false),40);
        typingQuery=label("Buscar GIF…");typingQuery.setTextSize(16);typingQuery.setSingleLine(true);typingQuery.setEllipsize(android.text.TextUtils.TruncateAt.START);typingQuery.setPadding(dp(12),0,dp(4),0);
        typingQuery.setBackground(shape(surface,CONTROL_RADIUS));compact.addView(typingQuery,new LayoutParams(0,controlHeight(),1));
        fixedButton(compact,"×","Borrar consulta",v->{query.setLength(0);updateQuery();invalidateResults();},CONTROL_HEIGHT);
        fixedButton(compact,"⌕","Buscar GIF",v->search(0),CONTROL_HEIGHT);compact.setVisibility(GONE);addView(compact,new LayoutParams(-1,-1));
        main.post(()->search(0));
    }
    private int controlHeight(){return Math.max(dp(CONTROL_HEIGHT),(int)Math.ceil(13*getResources().getDisplayMetrics().scaledDensity*1.35)+dp(8));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout row=new LinearLayout(getContext());row.setOrientation(HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);return row;}
    private android.graphics.drawable.GradientDrawable shape(int color,int radius){android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private TextView label(String text){TextView view=new TextView(getContext());view.setTextColor(foreground);view.setText(text);view.setGravity(android.view.Gravity.CENTER_VERTICAL);return view;}
    private void style(Button button){button.setTextColor(foreground);button.setTextSize(13);button.setAllCaps(false);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(dp(8),0,dp(8),0);button.setGravity(android.view.Gravity.CENTER);button.setStateListAnimator(null);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(accent),shape(surface,CONTROL_RADIUS),null));}
    private Button button(LinearLayout parent,String text,OnClickListener click){Button button=new Button(getContext());style(button);button.setText(text);button.setContentDescription(text);button.setOnClickListener(click);parent.addView(button,new LayoutParams(0,controlHeight(),1));return button;}
    private Button fixedButton(LinearLayout parent,String text,String description,OnClickListener click,int width){Button button=button(parent,text,click);button.setContentDescription(description);button.setLayoutParams(new LayoutParams(Math.max(dp(width),(int)Math.ceil(button.getPaint().measureText(text))+dp(16)),controlHeight()));return button;}
    private void updateSegments(){gifTab.setBackground(shape(stickers?surface:accent,CONTROL_RADIUS));stickerTab.setBackground(shape(stickers?accent:surface,CONTROL_RADIUS));gifTab.setSelected(!stickers);stickerTab.setSelected(stickers);}
    private void openSettings(){ime.startActivity(new Intent(ime,GiphySettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public boolean isSearchTyping(){return typing;}
    private void setTyping(boolean enabled){if(closed)return;if(enabled&&!typing)browseScroll=results.getScrollY();typing=enabled;browse.setVisibility(typing?GONE:VISIBLE);compact.setVisibility(typing?VISIBLE:GONE);ime.setGifSearchTyping(this,typing);updateQuery();if(!typing)results.post(()->{if(!closed&&!typing)results.scrollTo(0,browseScroll);});}
    private void clearResults(){grid.removeAllViews();browseScroll=0;results.scrollTo(0,0);}
    private void attachMore(){if(next.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)next.getParent()).removeView(next);grid.addView(next);}
    @Override public void onTextInput(String text){
        if(closed||text==null)return;
        String value=TextStickerText.appendWithinLimit(query.toString(),text,50);
        query.setLength(0);query.append(value);updateQuery();invalidateResults();
    }
    @Override public void onCodeInput(int code){
        if(code==KeyCode.DELETE){String value=TextStickerText.deleteLast(query.toString());query.setLength(0);query.append(value);updateQuery();invalidateResults();}
        else if(code==10)search(0);else if(code>0&&Character.isValidCodePoint(code)&&!Character.isISOControl(code))onTextInput(new String(Character.toChars(code)));
    }
    private void updateQuery(){queryView.setText(query.length()==0?"⌕  Buscar GIF y stickers":query.toString());typingQuery.setText(query.length()==0?"Buscar GIF…":query.toString()+" ▏");}
    private void emptyState(String title,String detail,String action,OnClickListener click){
        clearResults();grid.setGravity(android.view.Gravity.CENTER_VERTICAL);LinearLayout state=new LinearLayout(getContext());state.setTag("gif-empty-state");state.setOrientation(VERTICAL);state.setGravity(android.view.Gravity.CENTER);state.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView illustration=label("GIF");illustration.setTextSize(24);illustration.setTypeface(null,android.graphics.Typeface.BOLD);illustration.setGravity(android.view.Gravity.CENTER);illustration.setBackground(shape(accent,18));state.addView(illustration,new LayoutParams(dp(72),dp(48)));
        TextView headline=label(title);headline.setTextSize(16);headline.setTypeface(null,android.graphics.Typeface.BOLD);headline.setGravity(android.view.Gravity.CENTER);headline.setPadding(0,dp(10),0,dp(4));state.addView(headline,new LayoutParams(-1,-2));
        TextView explanation=label(detail);explanation.setTextSize(12);explanation.setGravity(android.view.Gravity.CENTER);state.addView(explanation,new LayoutParams(-1,-2));
        if(action!=null){Button cta=new Button(getContext());style(cta);cta.setText(action);cta.setBackground(shape(accent,CONTROL_RADIUS));cta.setOnClickListener(click);LayoutParams params=new LayoutParams(-2,controlHeight());params.topMargin=dp(10);state.addView(cta,params);}
        grid.addView(state,new LayoutParams(-1,-2));
    }
    private void stopWork(){generation.next();for(Future<?> future:work)future.cancel(true);work.clear();synchronized(clients){for(GiphyClient client:clients)client.cancel();clients.clear();}worker.getQueue().clear();for(GifPreviewView preview:previews)preview.stop();previews.clear();}
    private void invalidateResults(){stopWork();clearResults();next.setVisibility(GONE);status.setText("Pulsa Buscar para ver resultados");}
    private boolean current(int token){
        EditorInfo active=ime.getCurrentInputEditorInfo();
        return !closed&&generation.current(token)&&ime.getCurrentInputConnection()==connection&&editor!=null&&active!=null
            &&Objects.equals(editor.packageName,active.packageName)&&editor.fieldId==active.fieldId&&editor.inputType==active.inputType
            &&editor.imeOptions==active.imeOptions&&ime.isInternalInputPanelShowing();
    }
    private void search(int start){
        if(closed)return;
        setTyping(false);
        String key=GiphySettings.readKey(ime);if(key.isEmpty()){invalidateResults();emptyState("GIF para cada momento","Configura GIPHY una sola vez para explorar y buscar.","Conectar GIPHY",v->openSettings());status.setText("Sin configurar");return;}
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastSearch<1200){status.setText("Espera un momento antes de buscar · Powered By GIPHY");return;}lastSearch=now;
        historyCategory=null;stopWork();clearResults();next.setVisibility(GONE);offset=start;int token=generation.next();String text=query.toString();boolean mode=stickers;
        status.setText("Buscando… · Powered By GIPHY");
        work.add(worker.submit(()->{
            GiphyClient client=new GiphyClient();clients.add(client);
            try{GiphyClient.Page page=client.search(key,text,mode,start);main.post(()->{if(current(token)){render(page.items,token);next.setVisibility(page.more?VISIBLE:GONE);status.setText(page.items.isEmpty()?"Sin resultados · Powered By GIPHY":"Toca para enviar · ★ favoritos · Powered By GIPHY");}});}
            catch(Exception e){main.post(()->{if(current(token))status.setText("RATE_LIMIT".equals(e.getMessage())?"Cuota GIPHY agotada; prueba más tarde · Powered By GIPHY":"KEY_REJECTED".equals(e.getMessage())?"GIPHY rechazó la clave; revisa ⚙ · Powered By GIPHY":"No se pudo consultar GIPHY · Powered By GIPHY");});}
            finally{clients.remove(client);}
        }));
    }
    private void showHistory(String category){showHistory(category,0);}
    private void showHistory(String category,int start){
        setTyping(false);stopWork();clearResults();historyCategory=category;offset=start;int token=generation.next();
        List<GifItem> items=history.read(category);int end=Math.min(start+12,items.size());
        render(items.subList(Math.min(start,items.size()),end),token);next.setVisibility(end<items.size()?VISIBLE:GONE);
        if(items.isEmpty())emptyState("favorites".equals(category)?"Tus favoritos, aquí":"Tus últimos GIF, aquí","favorites".equals(category)?"Guarda un GIF con la estrella para encontrarlo después.":"Los GIF que la app acepte aparecerán aquí.",null,null);
        status.setText(items.isEmpty()?"":"Guardados localmente");
    }
    private void render(List<GifItem> items,int token){
        grid.setGravity(android.view.Gravity.TOP);boolean allowNetwork=!GiphySettings.readKey(ime).isEmpty();
        if(items.isEmpty()){emptyState("No encontramos resultados","Prueba otra palabra o combinación de emoji.","Cambiar búsqueda",v->setTyping(true));return;}
        LinearLayout columns=row();LinearLayout[] column={new LinearLayout(getContext()),new LinearLayout(getContext())};
        for(LinearLayout list:column){list.setOrientation(VERTICAL);columns.addView(list,new LayoutParams(0,-2,1));}grid.addView(columns,new LayoutParams(-1,-2));
        for(int index=0;index<items.size();index++){
            GifItem item=items.get(index);LinearLayout cell=new LinearLayout(getContext());cell.setOrientation(VERTICAL);cell.setBackground(shape(surface,12));cell.setClipToOutline(true);
            GifPreviewView preview=new GifPreviewView(getContext());previews.add(preview);preview.setContentDescription(item.title+" · enviar GIF");preview.setOnClickListener(v->send(item));
            preview.dimensions(item.width,item.height,dp(240));
            cell.addView(preview,new LayoutParams(-1,-2));
            LinearLayout actions=row();actions.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(background,225));Button favorite=fixedButton(actions,history.contains("favorites",item)?"★":"☆","Favorito",v->{history.put("favorites",item,true);((Button)v).setText(history.contains("favorites",item)?"★":"☆");},CONTROL_HEIGHT);favorite.setContentDescription("Favorito: "+item.title);
            button(actions,"Enlace",v->{ClipboardManager clipboard=(ClipboardManager)ime.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("GIPHY",item.link));status.setText("Enlace copiado");}});
            if(getResources().getConfiguration().fontScale>1.3f){actions.setOrientation(VERTICAL);for(int a=0;a<actions.getChildCount();a++)actions.getChildAt(a).setLayoutParams(new LayoutParams(-1,controlHeight()));}
            cell.addView(actions,new LayoutParams(-1,-2));LayoutParams cellParams=new LayoutParams(-1,-2);cellParams.setMargins(dp(4),0,dp(4),dp(8));column[index%2].addView(cell,cellParams);
            if(allowNetwork)work.add(worker.submit(()->{GiphyClient client=new GiphyClient();clients.add(client);try{byte[] bytes=client.media(item.preview,true);main.post(()->{if(current(token))preview.load(bytes);});}catch(Exception ignored){}finally{clients.remove(client);}}));
        }
        attachMore();
    }
    private void send(GifItem item){
        if(closed)return;
        if(GiphySettings.readKey(ime).isEmpty()){status.setText("Configura tu clave GIPHY en ⚙");return;}
        if(editor==null||connection==null||!GifPolicy.supportsGif(EditorInfoCompat.getContentMimeTypes(editor))){status.setText("Esta app no admite GIF. Puedes copiar Enlace · Powered By GIPHY");return;}
        stopWork();int token=generation.next();status.setText("Preparando GIF… · Powered By GIPHY");
        work.add(worker.submit(()->{
            GiphyClient client=new GiphyClient();clients.add(client);
            try{
                byte[] bytes=client.media(item.url,false);File directory=new File(ime.getCacheDir(),"gif-share");if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException();prune(directory);
                File file=File.createTempFile("giphy-",".gif",directory);try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(bytes);}
                main.post(()->{
                    if(!current(token)){file.delete();return;}
                    try{
                        Uri uri=FileProvider.getUriForFile(ime,ime.getPackageName()+".gifcontent",file);
                        InputContentInfoCompat content=new InputContentInfoCompat(uri,new ClipDescription(item.title,new String[]{"image/gif"}),Uri.parse(item.link));
                        ime.grantUriPermission(editor.packageName,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        boolean accepted=InputConnectionCompat.commitContent(connection,editor,content,InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null);
                        if(accepted){if(!Settings.getValues().mIncognitoModeEnabled)history.put("recent",item,false);status.setText("La app aceptó el GIF · Powered By GIPHY");}
                        else{ime.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);file.delete();status.setText("La app rechazó el GIF. Puedes copiar Enlace · Powered By GIPHY");}
                    }catch(Exception e){file.delete();status.setText("No se pudo compartir el GIF. Puedes copiar Enlace · Powered By GIPHY");}
                });
            }catch(Exception e){main.post(()->{if(current(token))status.setText("No se pudo descargar el GIF · Powered By GIPHY");});}
            finally{clients.remove(client);}
        }));
    }
    private static void prune(File directory){File[] files=directory.listFiles(file->file.isFile()&&file.getName().startsWith("giphy-")&&file.getName().endsWith(".gif"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=0;i<files.length;i++)if(i>=8||System.currentTimeMillis()-files[i].lastModified()>86400000L)files[i].delete();}
    @Override public void close(){if(closed)return;closed=true;stopWork();worker.shutdownNow();main.removeCallbacksAndMessages(null);grid.removeAllViews();}
}
