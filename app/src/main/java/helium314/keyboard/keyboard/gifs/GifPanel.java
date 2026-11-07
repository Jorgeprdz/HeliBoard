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
    private final TextView queryView,status;
    private final LinearLayout grid,keys;
    private final Button next;
    private final StringBuilder query=new StringBuilder();
    private boolean closed,stickers;private int offset;private long lastSearch;private String historyCategory;
    private int background,foreground;
    public GifPanel(LatinIME ime){
        super(ime);this.ime=ime;connection=ime.getCurrentInputConnection();editor=ime.getCurrentInputEditorInfo();history=new GifHistory(ime);
        setOrientation(VERTICAL);
        keys=new LinearLayout(ime);keys.setOrientation(VERTICAL);
        background=Settings.getValues().mColors.get(ColorType.MAIN_BACKGROUND);
        foreground=Settings.getValues().mColors.get(ColorType.KEY_TEXT);
        setBackgroundColor(background);
        LinearLayout header=row();button(header,"ABC",v->ime.closeInternalInputPanel());
        button(header,"GIF",v->{stickers=false;search(0);});button(header,"Stickers",v->{stickers=true;search(0);});
        button(header,"Traducir",v->ime.onAiAction("Traducir en vivo"));button(header,"Tinta",v->ime.onAiAction("Escritura a mano"));
        button(header,"⚙",v->ime.startActivity(new Intent(ime,GiphySettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        addView(header,new LayoutParams(-1,dp(36)));
        LinearLayout search=row();queryView=label("Buscar en GIPHY…");queryView.setSingleLine(true);queryView.setOnClickListener(v->keysVisibility());
        search.addView(queryView,new LayoutParams(0,-1,1));button(search,"Buscar",v->search(0));
        button(search,"⌨",v->keysVisibility());addView(search,new LayoutParams(-1,dp(36)));
        LinearLayout tabs=row();button(tabs,"Tendencias",v->{query.setLength(0);updateQuery();search(0);});
        button(tabs,"Recientes",v->showHistory("recent"));button(tabs,"Favoritos",v->showHistory("favorites"));
        button(tabs,"Emoji",v->{keys.setVisibility(VISIBLE);showEmojiKeys();});
        addView(tabs,new LayoutParams(-1,dp(34)));
        LinearLayout attribution=row();
        ImageView mark=new ImageView(ime);mark.setContentDescription("Powered By GIPHY");mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try(java.io.InputStream logo=ime.getAssets().open("giphy/powered_by_"+(androidx.core.graphics.ColorUtils.calculateLuminance(background)<.45?"black":"white")+".png")){
            mark.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(logo,"GIPHY attribution"));
        }catch(java.io.IOException ignored){}
        attribution.addView(mark,new LayoutParams(dp(100),dp(30)));
        status=label("GIPHY");status.setTextSize(10);attribution.addView(status,new LayoutParams(0,dp(30),1));addView(attribution,new LayoutParams(-1,dp(30)));
        ScrollView results=new ScrollView(ime);grid=new LinearLayout(ime);grid.setOrientation(VERTICAL);results.addView(grid);
        addView(results,new LayoutParams(-1,0,1));
        next=new Button(ime);style(next);next.setText("Más resultados");next.setOnClickListener(v->{if(historyCategory!=null)showHistory(historyCategory,offset+12);else search(offset+12);});next.setVisibility(GONE);
        addView(next,new LayoutParams(-1,dp(28)));
        addView(keys,new LayoutParams(-1,-2));showLetterKeys();keys.setVisibility(VISIBLE);
        main.post(()->search(0));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout row(){LinearLayout row=new LinearLayout(getContext());row.setOrientation(HORIZONTAL);return row;}
    private TextView label(String text){TextView view=new TextView(getContext());view.setTextColor(foreground);view.setText(text);view.setGravity(android.view.Gravity.CENTER_VERTICAL);view.setPadding(dp(4),0,dp(4),0);return view;}
    private void style(Button button){button.setTextColor(foreground);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);button.setPadding(dp(3),0,dp(3),0);button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));}
    private Button button(LinearLayout parent,String text,OnClickListener click){Button button=new Button(getContext());style(button);button.setText(text);button.setContentDescription(text);button.setOnClickListener(click);parent.addView(button,new LayoutParams(0,-1,1));return button;}
    private void keysVisibility(){keys.setVisibility(keys.getVisibility()==VISIBLE?GONE:VISIBLE);}
    private void showLetterKeys(){
        keys.removeAllViews();for(String letters:new String[]{"qwertyuiop","asdfghjklñ","zxcvbnm"}){
            LinearLayout line=row();for(int i=0;i<letters.length();i++){final int cp=letters.charAt(i);button(line,String.valueOf((char)cp),v->onCodeInput(cp));}keys.addView(line,new LayoutParams(-1,dp(28)));
        }
        LinearLayout line=row();button(line,"😊",v->showEmojiKeys());button(line,"espacio",v->onTextInput(" "));button(line,"⌫",v->onCodeInput(KeyCode.DELETE));
        button(line,"123",v->showSymbolKeys());button(line,"Buscar",v->search(0));keys.addView(line,new LayoutParams(-1,dp(28)));
    }
    private void showSymbolKeys(){keys.removeAllViews();for(String letters:new String[]{"1234567890","áéíóúü¿?¡!","@#.,-+&()"}){
        LinearLayout line=row();for(int i=0;i<letters.length();i++){final int cp=letters.charAt(i);button(line,String.valueOf((char)cp),v->onCodeInput(cp));}keys.addView(line,new LayoutParams(-1,dp(28)));}
        LinearLayout line=row();button(line,"abc",v->showLetterKeys());button(line,"⌫",v->onCodeInput(KeyCode.DELETE));button(line,"Buscar",v->search(0));keys.addView(line,new LayoutParams(-1,dp(28)));
    }
    private void showEmojiKeys(){keys.removeAllViews();for(String[] emoji:new String[][]{{"😊","😂","❤️","😭","🥰","😍","😎","😡"},{"🔥","✨","🎉","👍","🙏","🐱","🐶","💀"}}){
        LinearLayout line=row();for(String item:emoji)button(line,item,v->onTextInput(item));keys.addView(line,new LayoutParams(-1,dp(36)));}
        TextView info=label("Combina emoji para buscar stickers de GIPHY");info.setTextSize(11);keys.addView(info);
        LinearLayout line=row();button(line,"abc",v->showLetterKeys());button(line,"⌫",v->onCodeInput(KeyCode.DELETE));button(line,"Buscar stickers",v->{stickers=true;search(0);});keys.addView(line,new LayoutParams(-1,dp(36)));
    }
    @Override public void onTextInput(String text){if(closed||text==null)return;int available=50-query.length();if(available<=0)return;query.append(text,0,Math.min(available,text.length()));updateQuery();invalidateResults();}
    @Override public void onCodeInput(int code){if(code==KeyCode.DELETE){if(query.length()>0)query.delete(query.offsetByCodePoints(query.length(),-1),query.length());updateQuery();invalidateResults();}
        else if(code==10||code==10)search(0);else if(code>0&&Character.isValidCodePoint(code))onTextInput(new String(Character.toChars(code)));}
    private void updateQuery(){queryView.setText(query.length()==0?"Buscar en GIPHY…":query.toString()+" ▏");}
    private void stopWork(){generation.next();for(Future<?> future:work)future.cancel(true);work.clear();synchronized(clients){for(GiphyClient client:clients)client.cancel();clients.clear();}worker.getQueue().clear();for(GifPreviewView preview:previews)preview.stop();previews.clear();}
    private void invalidateResults(){stopWork();grid.removeAllViews();next.setVisibility(GONE);status.setText("Pulsa Buscar · Powered By GIPHY");}
    private boolean current(int token){return !closed&&generation.current(token)&&ime.getCurrentInputConnection()==connection&&ime.getCurrentInputEditorInfo()==editor&&ime.isInternalInputPanelShowing();}
    private void search(int start){
        if(closed)return;
        String key=GiphySettings.readKey(ime);if(key.isEmpty()){invalidateResults();status.setText("Configura tu clave GIPHY en ⚙ · Powered By GIPHY");return;}
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastSearch<1200){status.setText("Espera un momento antes de buscar · Powered By GIPHY");return;}lastSearch=now;
        historyCategory=null;stopWork();grid.removeAllViews();next.setVisibility(GONE);offset=start;int token=generation.next();String text=query.toString();boolean mode=stickers;
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
        stopWork();grid.removeAllViews();historyCategory=category;offset=start;int token=generation.next();
        List<GifItem> items=history.read(category);int end=Math.min(start+12,items.size());
        render(items.subList(Math.min(start,items.size()),end),token);next.setVisibility(end<items.size()?VISIBLE:GONE);
        status.setText(items.isEmpty()?"Aún no hay elementos · Powered By GIPHY":"Metadatos locales · Powered By GIPHY");
    }
    private void render(List<GifItem> items,int token){
        boolean allowNetwork=!GiphySettings.readKey(ime).isEmpty();
        for(int index=0;index<items.size();index+=3){LinearLayout line=row();
            for(GifItem item:items.subList(index,Math.min(index+3,items.size()))){
                LinearLayout cell=new LinearLayout(ime);cell.setOrientation(VERTICAL);
                GifPreviewView preview=new GifPreviewView(ime);previews.add(preview);preview.setContentDescription(item.title+" · enviar GIF");preview.setOnClickListener(v->send(item));
                cell.addView(preview,new LayoutParams(-1,dp(60)));
                LinearLayout actions=row();Button favorite=button(actions,history.contains("favorites",item)?"★":"☆",v->{history.put("favorites",item,true);((Button)v).setText(history.contains("favorites",item)?"★":"☆");});favorite.setContentDescription("Favorito: "+item.title);
                button(actions,"Enlace",v->{ClipboardManager clipboard=(ClipboardManager)ime.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("GIPHY",item.link));status.setText("Enlace copiado · Powered By GIPHY");}});
                cell.addView(actions,new LayoutParams(-1,dp(26)));line.addView(cell,new LayoutParams(0,-2,1));
                if(allowNetwork)work.add(worker.submit(()->{GiphyClient client=new GiphyClient();clients.add(client);try{byte[] bytes=client.media(item.preview,true);main.post(()->{if(current(token))preview.load(bytes);});}catch(Exception ignored){}finally{clients.remove(client);}}));
            }grid.addView(line,new LayoutParams(-1,-2));
        }
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
    private static void prune(File directory){File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=0;i<files.length;i++)if(i>=8||System.currentTimeMillis()-files[i].lastModified()>86400000L)files[i].delete();}
    @Override public void close(){if(closed)return;closed=true;stopWork();worker.shutdownNow();main.removeCallbacksAndMessages(null);grid.removeAllViews();}
}
