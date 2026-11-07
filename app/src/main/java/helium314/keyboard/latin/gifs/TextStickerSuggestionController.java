// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gifs;

import android.app.KeyguardManager;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.ai.AiSettingsActivity;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import java.io.File;
import java.util.concurrent.*;

/** Local, debounced sticker rendering for one existing native suggestion slot. No editor writes. */
public final class TextStickerSuggestionController implements AutoCloseable {
 private final LatinIME ime;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final TextStickerStore store;
 private volatile long generation;
 private boolean closed;
 private SuggestionStripView strip;
 private Future<?> task;
 private Runnable debounce;
 private TextStickerSuggestionPolicy.Target requested;
 private Preview shown;
 private static final class Preview {
  final TextStickerSuggestionPolicy.Target target;
  final byte[] png;
  final File file;
  final Bitmap bitmap;
  Preview(TextStickerSuggestionPolicy.Target target,byte[] png,File file,Bitmap bitmap){this.target=target;this.png=png;this.file=file;this.bitmap=bitmap;}
 }
 public TextStickerSuggestionController(LatinIME ime){this.ime=ime;store=new TextStickerStore(ime.getCacheDir(),ime.getFilesDir());}
 /** Only invoked after an ordinary native suggestion update while a word is composing. */
 public void refresh(SuggestionStripView view){
  if(closed)return;
  if(strip!=null&&strip!=view)strip.clearTextStickerSuggestion();strip=view;
  TextStickerSuggestionPolicy.Target target=capture();
  if(target==null){invalidate();return;}
  if(shown!=null&&shown.target.matches(target)){display(shown);return;}
  if(requested!=null&&requested.matches(target))return;
  invalidate();target=capture();if(target==null)return;
  requested=target;final TextStickerSuggestionPolicy.Target source=target;
  debounce=()->{
   if(!current(source))return;
   task=worker.submit(()->{
    try{
     if(source.generation!=generation||Thread.currentThread().isInterrupted())return;
     byte[] png=TextStickerRenderer.render(source.text);
     if(source.generation!=generation||Thread.currentThread().isInterrupted())return;
     File file=store.preview(png);
     BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;
     Bitmap bitmap=BitmapFactory.decodeByteArray(png,0,png.length,options);
     if(bitmap==null){file.delete();return;}
     Preview preview=new Preview(source,png,file,bitmap);
     main.post(()->{
      if(!current(source)){file.delete();bitmap.recycle();return;}
      shown=preview;display(preview);
     });
    }catch(Exception unavailable){main.post(()->{if(current(source)){requested=null;strip.clearTextStickerSuggestion();}});}
   });
  };
  main.postDelayed(debounce,250);
 }
 private void display(Preview preview){
  if(current(preview.target))strip.showTextStickerSuggestion(preview.bitmap,()->send(preview),()->save(preview));
 }
 private TextStickerSuggestionPolicy.Target capture(){
  try{return captureChecked();}catch(RuntimeException unavailable){return null;}
 }
 private TextStickerSuggestionPolicy.Target captureChecked(){
  if(closed||strip==null||!strip.canShowTextStickerSuggestion()||!ime.hasAiComposition())return null;
  InputConnection connection=ime.getCurrentInputConnection();EditorInfo editor=ime.getCurrentInputEditorInfo();
  if(connection==null||editor==null||editor.packageName==null)return null;
  KeyguardManager lock=(KeyguardManager)ime.getSystemService(Context.KEYGUARD_SERVICE);
  if(lock!=null&&lock.isKeyguardLocked())return null;
  if(!TextStickerSuggestionPolicy.allowed(editor.inputType,editor.imeOptions,Settings.getValues().mIncognitoModeEnabled,
          AiSettingsActivity.excludedApp(ime,editor.packageName),Settings.getValues().mSuggestionsEnabled,
          strip.isAiActive(),ime.isInternalInputPanelShowing()))return null;
  int start=ime.getAiInputConnection().getExpectedSelectionStart(),end=ime.getAiInputConnection().getExpectedSelectionEnd();
  if(start<0||start!=end)return null;
  CharSequence rawBefore=connection.getTextBeforeCursor(256,0),rawAfter=connection.getTextAfterCursor(128,0);
  if(rawBefore==null||rawAfter==null)return null;
  String before=rawBefore.toString(),after=rawAfter.toString();
  String text=TextStickerSuggestionPolicy.source("",before,after);
  if(text.isEmpty())return null;
  return new TextStickerSuggestionPolicy.Target(connection,editor,generation,editor.packageName,editor.fieldId,editor.inputType,editor.imeOptions,start,end,before,after,text);
 }
 private boolean current(TextStickerSuggestionPolicy.Target target){return !closed&&target.generation==generation&&target.matches(capture());}
 private void send(Preview preview){
  if(!current(preview.target))return;
  EditorInfo editor=ime.getCurrentInputEditorInfo();
  if(editor==null)return;
  boolean supported=false;
  for(String mime:EditorInfoCompat.getContentMimeTypes(editor))if(ClipDescription.compareMimeTypes("image/png",mime)){supported=true;break;}
  if(!supported){toast("Esta app no admite stickers PNG");return;}
  try{
   if(!current(preview.target))return;
   Uri uri=FileProvider.getUriForFile(ime,ime.getPackageName()+".gifcontent",preview.file);
   InputContentInfoCompat content=new InputContentInfoCompat(uri,new ClipDescription("Sticker de texto",new String[]{"image/png"}),null);
   ime.grantUriPermission(editor.packageName,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
   boolean accepted=InputConnectionCompat.commitContent((InputConnection)preview.target.connection,editor,content,
           InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,null);
   if(!accepted)ime.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
   toast(accepted?"La app aceptó el sticker":"La app rechazó el sticker");
  }catch(RuntimeException unavailable){toast("No se pudo compartir el sticker");}
 }
 private void save(Preview preview){
  if(!current(preview.target))return;
  worker.submit(()->{
   try{
    if(preview.target.generation!=generation)return;
    store.save(preview.png);
    main.post(()->{if(current(preview.target))toast("Sticker guardado");});
   }catch(Exception unavailable){main.post(()->{if(current(preview.target))toast("No se pudo guardar el sticker");});}
  });
 }
 private void toast(String message){Toast.makeText(ime,message,Toast.LENGTH_SHORT).show();}
 public void invalidate(){
  ++generation;
  if(debounce!=null)main.removeCallbacks(debounce);debounce=null;
  if(task!=null)task.cancel(true);task=null;
  requested=null;shown=null;
  if(strip!=null)strip.clearTextStickerSuggestion();
 }
 @Override public void close(){invalidate();closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);strip=null;}
}
