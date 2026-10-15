// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.suggestions.SuggestionStripView;
import androidx.appcompat.app.AlertDialog;
import android.os.Build;
import android.os.PersistableBundle;
import android.text.SpannedString;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import helium314.keyboard.latin.utils.DialogUtilsKt;
import helium314.keyboard.latin.ai.entities.EntityRecognizer;
import java.util.*;

/** Opt-in AI assistant. Never reads the global clipboard nor texts outside explicit selection. */
public final class AiKeyboardAssistant {
    private final LatinIME ime;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AiSessionCoordinator session = new AiSessionCoordinator(action -> main.post(action));
    private int selectionStart = -1, selectionEnd = -1;
    private int generation = 0;
    private String selected = "";
    private String proposed = "";
    private String address = "";
    private String phone = "";
    private String clabe = "";
    private String email = "";
    private SuggestionStripView strip;
    private final AiResultReview review = new AiResultReview();
    private final AiEditTransaction edits = new AiEditTransaction();
    private final helium314.keyboard.latin.ai.dictation.DictationSession dictation=new helium314.keyboard.latin.ai.dictation.DictationSession();
    private helium314.keyboard.latin.ai.dictation.LocalSpeechRecognizer speech;
    private helium314.keyboard.latin.ai.dictation.RamblrAudioCapture audioCapture;
    private java.io.File recordedAudio;
    private void startAudioCapture(){
        if(!AiSettingsActivity.dictationEnabled(ime)||privateField()||!AiSettingsActivity.networkAllowed(ime)){show("Configurar","Cancelar");return;}
        boolean permission=Build.VERSION.SDK_INT<23||ime.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(!permission){ime.startActivity(new Intent(ime,helium314.keyboard.latin.ai.dictation.DictationPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        dictationBase=capture();if(dictationBase==null||SensitiveInputPolicy.sensitiveText(dictationBase.original)){show("No grabar sobre texto protegido","Cancelar");return;}
        dictationTicket=dictation.begin(dictationBase,true,privateField());if(dictationTicket<0){show("Dictado ocupado","Cancelar");return;}
        final long ticket=dictationTicket;
        try {
            audioCapture=new helium314.keyboard.latin.ai.dictation.RamblrAudioCapture(ime);
            boolean started=audioCapture.start(new helium314.keyboard.latin.ai.dictation.RamblrAudioCapture.Callback(){
                public void audio(java.io.File file){
                    if(!AiSettingsActivity.dictationEnabled(ime)||privateField()||dictationTicket!=ticket||dictationBase==null||!dictationBase.matches(capture())){file.delete();return;}
                    dictation.stop(ticket);recordedAudio=file;
                    show("Audio grabado; enviar a Google requiere tu acción","Enviar audio a Gemini","Cancelar");
                }
                public void failed(){dictation.cancel();show("No fue posible grabar audio","Cancelar");}
            });
            if(started)show("Grabando audio localmente…","Detener grabación","Cancelar");else {audioCapture.close();dictation.cancel();show("Micrófono no disponible","Cancelar");}
        }catch(RuntimeException e){if(audioCapture!=null)audioCapture.close();dictation.cancel();show("Micrófono no disponible","Cancelar");}
    }
    private void transcribeAudio(){
        if(!AiSettingsActivity.networkAllowed(ime)||!AiSettingsActivity.dictationEnabled(ime)||privateField()||recordedAudio==null||dictationBase==null||!dictationBase.matches(capture())){reset(false);return;}
        final String key=AiSettingsActivity.readKey(ime),model=AiSettingsActivity.readModel(ime);
        if(key.isEmpty()||model.isEmpty()){show("Configurar","Cancelar");return;}
        final java.io.File pcm=recordedAudio;final EditorSnapshot base=dictationBase;final long ticket=dictationTicket;
        final boolean[] complete={false};show("Transcribiendo con Gemini…","Cancelar");
        session.submit(cancel->{
            try {
                long length=pcm.length();if(length<=0||length>3840000||length%2!=0)throw new java.io.IOException("Audio limit");
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream((int)length+44);
                bytes.write(helium314.keyboard.latin.ai.dictation.ramblr.WavWriter.INSTANCE.header(length,16000,1,16));
                try(java.io.InputStream in=new java.io.FileInputStream(pcm)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(cancel.cancelled())throw new java.util.concurrent.CancellationException();bytes.write(buffer,0,count);if(bytes.size()>3840044)throw new java.io.IOException("Audio limit");}}
                String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
                helium314.keyboard.latin.ai.provider.GeminiProvider.Result result=new helium314.keyboard.latin.ai.provider.GeminiProvider().transcribeWav(key,model,encoded,AiSettingsActivity.readVocabulary(ime).toArray(new String[0]),
                    new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}});
                complete[0]=result.complete;return result.text;
            }finally{pcm.delete();}
        },(value,error)->{
            if(recordedAudio==pcm)recordedAudio=null;
            if(privateField()||!AiSettingsActivity.networkAllowed(ime)||!AiSettingsActivity.dictationEnabled(ime)||!base.matches(capture()))return;
            if(error!=null){dictation.cancel();show(providerError(error),"Cancelar");return;}
            if(!dictation.result(ticket,capture(),value)){reset(false);return;}
            review.offer(base,value,complete[0]);show(complete[0]?"Dictado listo":"Transcripción incompleta","Revisar resultado","Mejorar dictado","Copiar resultado","Cancelar");
        },35000);
    }
    private void improveDictation(){
        if(!AiSettingsActivity.networkAllowed(ime)||privateField()||review.snapshot()==null||!review.snapshot().matches(capture())||SensitiveInputPolicy.sensitiveText(review.result())){show("No enviar este dictado; revisa permisos y privacidad","Cancelar");return;}
        final EditorSnapshot base=review.snapshot();final String text=review.result(),key=AiSettingsActivity.readKey(ime),model=AiSettingsActivity.readModel(ime);
        if(key.isEmpty()||model.isEmpty()){show("Configurar","Cancelar");return;}
        final boolean[] complete={false};show("Mejorando dictado con Gemini…","Cancelar");
        session.submit(cancel->{helium314.keyboard.latin.ai.provider.GeminiProvider.Result response=new helium314.keyboard.latin.ai.provider.GeminiProvider().generate(key,model,helium314.keyboard.latin.ai.provider.GeminiOperation.CLARITY,"casual",null,text,
            new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}});complete[0]=response.complete;return response.text;
        },(value,error)->{if(privateField()||!AiSettingsActivity.networkAllowed(ime)||!base.matches(capture()))return;if(error!=null){show(providerError(error),"Cancelar");return;}review.offer(base,value,complete[0]);show(complete[0]?"Dictado mejorado":"Resultado incompleto","Revisar resultado","Copiar resultado","Cancelar");},35000);
    }
    private long dictationTicket;
    private EditorSnapshot dictationBase;
    private void startLocalDictation(){
        if(!AiSettingsActivity.dictationEnabled(ime)||privateField()){show("Dictado no disponible en este campo","Cerrar");return;}
        boolean permission=Build.VERSION.SDK_INT<23||ime.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(!permission){ime.startActivity(new Intent(ime,helium314.keyboard.latin.ai.dictation.DictationPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
        dictationBase=capture();
        if(dictationBase==null||SensitiveInputPolicy.sensitiveText(dictationBase.original)){show("No dictar sobre texto protegido","Cerrar");return;}
        dictationTicket=dictation.begin(dictationBase,true,privateField());
        if(dictationTicket<0){show("Dictado ocupado","Cancelar");return;}
        final long ticket=dictationTicket;
        try {
            speech=new helium314.keyboard.latin.ai.dictation.LocalSpeechRecognizer(ime,new helium314.keyboard.latin.ai.dictation.LocalSpeechRecognizer.Callback(){
                public void transcript(String text){dictation.stop(ticket);if(!privateField()&&AiSettingsActivity.dictationEnabled(ime)&&dictation.result(ticket,capture(),text)){
                    EditorSnapshot base=dictationBase;finishAutomatic(base,text);
                }else reset(false);}
                public void failed(int code){if(dictationTicket!=ticket)return;dictation.cancel();dictationBase=null;
                    if(speech!=null){speech.close();speech=null;}
                    show("Dictado local no disponible ("+code+"). Revisa el motor y sus idiomas sin conexión.","Configurar voz local","Cerrar");}
            });
            speech.start(AiSettingsActivity.dictationLanguage(ime),AiSettingsActivity.readVocabulary(ime));show("Escuchando en el dispositivo…","Detener dictado","Cancelar");
        }catch(RuntimeException e){if(speech!=null)speech.close();speech=null;dictation.cancel();dictationBase=null;show("Instala o configura un motor de reconocimiento sin conexión","Configurar voz local","Cerrar");}
    }
    private final helium314.keyboard.latin.ai.local.PersonalTypingStore personal;
    private String automaticOriginal="",automaticLanguage="";
    private String typingLanguage(){
        java.util.Locale locale=helium314.keyboard.latin.RichInputMethodManager.getInstance().getCurrentSubtypeLocale();
        return "es".equals(locale.getLanguage())?"es-MX":"en".equals(locale.getLanguage())?"en-US":"";
    }
    /** Called only for an explicit candidate selection, before native commit. */
    public void onExplicitCandidate(String typed,String chosen){
        if(AiSettingsActivity.contextualEnabled(ime)&&!privateField())personal.accept(typed,chosen,typingLanguage());
    }
    private void finishAutomatic(EditorSnapshot base,String value){
        boolean accepted=base.hasSelection()?automaticEdits.apply(base,value,target(base.original)):
            automaticEdits.applySpan(base,base.start,base.end,"",value,target(""));
        dictation.cancel();dictationBase=null;
        if(speech!=null){speech.close();speech=null;}
        if(!accepted){show("El editor rechazó el dictado","Cerrar");return;}
        automaticReplacement=value;automaticAfter=capture();
        CharSequence after=ime.getAiInputConnection().getTextBeforeCursor(2048,0);
        automaticContext=after==null?"":after.toString();
        if(strip!=null)strip.closeAiChoices();
    }
    private long revision;
    private boolean ownEdit;
    private String undoText = "";
    private AlertDialog reviewDialog;
    private String pendingAction = "", tone = "Professional", language = "en-US";
    private final helium314.keyboard.latin.ai.local.ObservedLexicon lexicon=new helium314.keyboard.latin.ai.local.ObservedLexicon();
    private final AiSessionCoordinator localSession=new AiSessionCoordinator(action->main.post(action));
    private EditorSnapshot localBase;
    private helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction localCorrection;
    private String localContext="";
    private int localOffset;
    private CharSequence localOriginal;
    private final Runnable localDebounce=this::reviewLocal;
    public void onPredictions(helium314.keyboard.latin.SuggestedWords words,SuggestionStripView view) {
        if(!AiSettingsActivity.contextualEnabled(ime)||privateField())return;
        if(!words.isPrediction()&&words.size()>0) {
            String typed=words.mTypedWordInfo==null?words.getWord(0):words.mTypedWordInfo.mWord;
            List<String> candidates=new ArrayList<>();
            for(int i=0;i<words.size();i++) {
                helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo info=words.getInfo(i);
                if(info.isKindOf(helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_CORRECTION)
                        &&info.mSourceDict!=null) candidates.add(info.mWord);
            }
            lexicon.record(typed,words.mTypedWordValid,candidates,!words.mIsObsoleteSuggestions&&words.mTypedWordInfo!=null);
            for(String word:candidates)if(!word.equals(typed))lexicon.record(word,true,Collections.emptyList());
        }
        if(view==null||view.isAiActive())return;
        strip=view;main.removeCallbacks(localDebounce);main.post(localDebounce);
    }
    private void reviewLocal() {
        if(!AiSettingsActivity.contextualEnabled(ime)||privateField()||strip==null||strip.isAiActive())return;
        EditorSnapshot base=capture();if(base==null||base.start!=base.end||base.start<0)return;
        CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(512,InputConnection.GET_TEXT_WITH_STYLES);
        if(before==null||before.length()==0||SensitiveInputPolicy.sensitiveText(before))return;
        String context=before.toString();int offset=base.start-context.length();if(offset<0)return;
        helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon vocabulary=lexicon.snapshot();
        Set<String> rejected=AiSettingsActivity.rejectedCorrections(ime);
        final List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction>[] proposals=new List[]{Collections.emptyList()};
        localSession.submit(cancel->{proposals[0]=new helium314.keyboard.latin.ai.local.LocalContextReviewer().review(context,vocabulary,rejected,cancel::cancelled);return "";},(unused,error)->{
            if(error!=null||!AiSettingsActivity.contextualEnabled(ime)||privateField()||!base.matches(capture())||proposals[0].isEmpty()||strip==null||strip.isAiActive())return;
            localBase=base;localContext=context;localOffset=offset;localCorrection=proposals[0].get(0);localOriginal=SpannedString.valueOf(before.subSequence(localCorrection.start,localCorrection.end));
            show(localCorrection.original+" → "+localCorrection.replacement,"Aplicar corrección","Rechazar corrección","Cerrar");
        },1000);
    }
    private void applyLocal() {
        if(localBase==null||localCorrection==null||!localBase.matches(capture())){show("El texto cambió","Cerrar");return;}
        CharSequence latest=ime.getCurrentInputConnection().getTextBeforeCursor(512,InputConnection.GET_TEXT_WITH_STYLES);
        if(latest==null||!localContext.contentEquals(latest)){show("El contexto cambió","Cerrar");return;}
        CharSequence original=SpannedString.valueOf(latest.subSequence(localCorrection.start,localCorrection.end));String replacement=localCorrection.replacement;
        if(edits.applySpan(localBase,localOffset+localCorrection.start,localOffset+localCorrection.end,original,replacement,target(original))) {
            personal.accept(localCorrection.original,replacement,typingLanguage());
            undoText=replacement;localCorrection=null;localBase=null;show("Corrección aplicada","Deshacer","Cerrar");
        } else show("El editor rechazó la corrección","Cerrar");
    }

    private final helium314.keyboard.latin.ai.local.LocalContextReviewer boundaryReviewer = new helium314.keyboard.latin.ai.local.LocalContextReviewer();
    private final AiEditTransaction automaticEdits = new AiEditTransaction();
    private EditorSnapshot automaticAfter;
    private String automaticContext = "", automaticReplacement = "";
    private void clearAutomaticUndo() {
        automaticEdits.clear(); automaticAfter=null;
        automaticContext=""; automaticReplacement=""; automaticOriginal=""; automaticLanguage="";
    }

    /** Runs only at an IME word boundary, after native autocorrection; no network/dictionary IO. */
    public void onWordBoundary() {
        if(!AiSettingsActivity.contextualEnabled(ime)||privateField()
                ||(strip!=null&&strip.isAiActive())||ime.getAiInputConnection().hasSlowInputConnection())return;
        EditorSnapshot base=capture();
        if(base==null||base.start!=base.end||base.start<0
                ||ime.getAiInputConnection().hasSlowInputConnection())return;
        CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(512,InputConnection.GET_TEXT_WITH_STYLES);
        if(before==null||before.length()==0||SensitiveInputPolicy.sensitiveText(before))return;
        String context=before.toString();
        int offset=base.start-context.length();
        if(offset<0)return;
        // A truncated prefix may begin inside a quote, URL, name or token. Fail closed.
        if(offset>0)return;
        final long deadline=System.nanoTime()+8_000_000L;
        java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> corrections=
            boundaryReviewer.review(
                context,lexicon.snapshot(),AiSettingsActivity.rejectedCorrections(ime),
                ()->System.nanoTime()>deadline);
        if(System.nanoTime()>deadline)return;
        java.util.regex.Matcher last=java.util.regex.Pattern.compile("([\\p{Ll}\\p{M}]{2,48})[ \\t.,!?;:]+$").matcher(context);
        if(last.find()&&(last.start()==0||Character.isWhitespace(context.charAt(last.start()-1)))){
            String typed=last.group(1);
            helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon known=lexicon.snapshot();
            String replacement=known.knownInvalid(typed)?personal.automatic(typed,typingLanguage(),known.valid(typed)):null;
            if(replacement!=null&&boundaryReviewer.allowsPersonal(context,last.start(1),last.end(1),replacement,AiSettingsActivity.rejectedCorrections(ime))){
                java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> all=new java.util.ArrayList<>();
                all.add(new helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction(last.start(1),last.end(1),typed,replacement,"personal-explicit-evidence",.99,true));
                all.addAll(corrections);corrections=all;
            }
        }
        if(System.nanoTime()>deadline)return;
        for(helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction correction:corrections) {
            if(!correction.automaticAllowed)continue;
            if(!base.matches(capture()))return;
            CharSequence latest=ime.getCurrentInputConnection().getTextBeforeCursor(512,InputConnection.GET_TEXT_WITH_STYLES);
            if(latest==null||!context.contentEquals(latest))return;
            CharSequence original=SpannedString.valueOf(latest.subSequence(correction.start,correction.end));
            if(automaticEdits.applySpan(base,offset+correction.start,offset+correction.end,
                    original,correction.replacement,target(original))) {
                automaticOriginal=correction.original;automaticLanguage=typingLanguage();
                automaticReplacement=correction.replacement;
                automaticAfter=capture();
                CharSequence after=ime.getCurrentInputConnection().getTextBeforeCursor(2048,0);
                automaticContext=after==null?"":after.toString();
                localSession.cancel();main.removeCallbacks(localDebounce);
                localBase=null;localCorrection=null;
            }
            return; // One confirmed transaction per boundary; never race native composition.
        }
    }

    public boolean undoAutomaticOnBackspace() {
        if(!automaticEdits.hasUndo())return false;
        CharSequence before=ime.getCurrentInputConnection()==null?null:ime.getCurrentInputConnection().getTextBeforeCursor(2048,0);
        boolean valid=!privateField()&&automaticAfter!=null&&automaticAfter.matches(capture())
            &&before!=null&&automaticContext.contentEquals(before);
        boolean undone=valid&&automaticEdits.undo(target(automaticReplacement));
        if(undone&&!automaticOriginal.isEmpty()){
            personal.reject(automaticOriginal,automaticReplacement,automaticLanguage);
            AiSettingsActivity.rejectCorrection(ime,automaticOriginal+"→"+automaticReplacement);
        }
        clearAutomaticUndo();
        return undone;
    }

    private final Map<String,EntityRecognizer.Entity> entities = new LinkedHashMap<>();

    private EditorSnapshot capture() {
        InputConnection ic=ime.getCurrentInputConnection(); EditorInfo info=ime.getCurrentInputEditorInfo();
        if(ic==null||info==null)return null;
        int start=ime.getAiInputConnection().getExpectedSelectionStart(),end=ime.getAiInputConnection().getExpectedSelectionEnd();
        CharSequence raw=ic.getSelectedText(InputConnection.GET_TEXT_WITH_STYLES);
        if(raw==null&&start==end)raw="";
        return new EditorSnapshot(ic,info.packageName,info.fieldId,generation,revision,start,end,
            raw==null?null:SpannedString.valueOf(raw));
    }
    private boolean replace(int start,int end,CharSequence expected,CharSequence replacement) {
        ownEdit=true;
        try {
            boolean accepted=ime.applyAiReplacement(start,end,expected,replacement);
            if(accepted)revision++;
            return accepted;
        } finally {ownEdit=false;}
    }
    private AiEditTransaction.Target target(CharSequence expected) {
        return new AiEditTransaction.Target() {
            public EditorSnapshot capture(){return AiKeyboardAssistant.this.capture();}
            public boolean replace(int start,int end,CharSequence value){return AiKeyboardAssistant.this.replace(start,end,expected,value);}
        };
    }
    private void copy(String label,String value) {
        ClipboardManager manager=(ClipboardManager)ime.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if(manager==null||value==null||value.isEmpty())return;
        ClipData clip=ClipData.newPlainText(label,value);
        if(Build.VERSION.SDK_INT>=24&&SensitiveInputPolicy.sensitiveText(value)) {
            PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);
        }
        manager.setPrimaryClip(clip);
    }
    private void fullReview() {
        if(review.result().isEmpty()||review.snapshot()==null||!review.snapshot().matches(capture())) {show("La selección cambió","Cerrar");return;}
        android.content.Context dialogContext=AiMenuTheme.expressiveContext(ime);
        AiMenuTheme aiTheme=AiMenuTheme.resolve(ime);
        ScrollView scroll=new ScrollView(dialogContext);TextView text=new TextView(dialogContext);
        scroll.setBackgroundColor(aiTheme.surface);text.setTextColor(aiTheme.onSurface);
        text.setTextSize(18);
        text.setText((review.meaningRisk()?"Revisa cambios en cifras o negaciones.\n\n":"")+review.result());
        text.setTextIsSelectable(true);int pad=(int)(16*ime.getResources().getDisplayMetrics().density);text.setPadding(pad,pad,pad,pad);scroll.addView(text);
        final AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(dialogContext)
            .setTitle(review.meaningRisk()?"Revisa cifras, negaciones y significado":"Resultado")
            .setView(scroll)
            .setPositiveButton("Aplicar",(d,w)->{if(!privateField()&&review.reviewed(capture()))onAction("Usar",strip);})
            .setNeutralButton("Copiar",(d,w)->copy("Respuesta IA",review.result()))
            .setNegativeButton("Cancelar",(d,w)->reset(false)).create();
        reviewDialog=dialog;
        WindowManager.LayoutParams params=dialog.getWindow().getAttributes();
        params.token=ime.getWindow().getWindow().getDecorView().getWindowToken();params.type=WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        dialog.getWindow().setAttributes(params);dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_SECURE);
        try {dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(review.complete());aiTheme.styleDialog(dialog);}catch(WindowManager.BadTokenException e){reviewDialog=null;show("No se pudo abrir la revisión","Cancelar");}
    }

    public AiKeyboardAssistant(LatinIME ime) { this.ime = ime; personal=helium314.keyboard.latin.ai.local.PersonalTypingStore.get(ime); }

    private void show(String... choices) {
        if (strip != null) strip.setAiChoices(choices);
    }
    private boolean privateField() {
        EditorInfo editor = ime.getCurrentInputEditorInfo();
        android.app.KeyguardManager lock=(android.app.KeyguardManager)ime.getSystemService(android.content.Context.KEYGUARD_SERVICE);
        return (lock!=null&&lock.isKeyguardLocked()) || editor == null || SensitiveInputPolicy.protectedEditor(editor.inputType, editor.imeOptions,
            helium314.keyboard.latin.settings.Settings.getValues().mIncognitoModeEnabled,
            AiSettingsActivity.excludedApp(ime, editor.packageName));
    }
    private String currentSelection() {
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence value = ic.getSelectedText(0);
        if (value == null) return "";
        return value.toString();
    }
    public void onNewEditor() { reset(true); }
    private void reset(boolean forgetWords) {
        clearAutomaticUndo();
        generation++; revision++;dictation.cancel();dictationBase=null;if(audioCapture!=null){audioCapture.close();audioCapture=null;}if(recordedAudio!=null){recordedAudio.delete();recordedAudio=null;}if(speech!=null){speech.close();speech=null;} session.cancel();localSession.cancel();main.removeCallbacks(localDebounce);localBase=null;localCorrection=null;localOriginal=null;localContext="";if(forgetWords)lexicon.clear();review.clear();edits.clear();undoText="";pendingAction="";entities.clear();
        if(reviewDialog!=null){AlertDialog old=reviewDialog;reviewDialog=null;old.dismiss();}
        selected = ""; proposed = ""; address = ""; phone = ""; clabe = ""; email = "";
        if (strip != null) strip.closeAiChoices();
        strip = null;
    }
    public void close() { onNewEditor(); session.close();localSession.close(); }
    public void onUserInput() { reset(false); }
    public void onUserInput(boolean backspace) {
        if(backspace&&automaticEdits.hasUndo())return;
        reset(false);
    }
    public void onSelectionChanged(int start,int end,int composingStart,int composingEnd) {
        if(!ownEdit&&composingStart>=0&&composingEnd>=composingStart)clearAutomaticUndo();
        onSelectionChanged(start,end);
    }
    public void onSelectionChanged(int start, int end) {
        if(ownEdit)return;
        if(automaticEdits.hasUndo()&&(automaticAfter==null||!automaticAfter.matches(capture())
                ||start!=automaticAfter.start||end!=automaticAfter.end))clearAutomaticUndo();
        if(dictationBase!=null&&(start!=dictationBase.start||end!=dictationBase.end)){reset(false);return;}
        if(localBase!=null&&(start!=localBase.start||end!=localBase.end)){reset(false);return;}
        if(edits.hasUndo()) {
            EditorSnapshot now=capture();if(now==null||start!=now.start||end!=now.end){onNewEditor();return;}
        }
        if (!selected.isEmpty() && (start != selectionStart || end != selectionEnd)) onNewEditor();
    }
    public void onAction(String action, SuggestionStripView view) {
        strip = view;
        if("Dictado".equals(action)){
            if(dictation.state()==helium314.keyboard.latin.ai.dictation.DictationSession.State.RECORDING){
                dictation.stop(dictationTicket);if(speech!=null)speech.stop();return;
            }
            reset(false);strip=view;startLocalDictation();return;
        }
        if("Configurar voz local".equals(action)){launch(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS));return;}
        if("Mejorar dictado".equals(action)){show("Enviar texto dictado a Gemini","Cancelar");return;}
        if("Enviar texto dictado a Gemini".equals(action)){improveDictation();return;}
        if("Grabar audio para Gemini".equals(action)){startAudioCapture();return;}
        if("Detener grabación".equals(action)){if(audioCapture!=null)audioCapture.stop();dictation.stop(dictationTicket);return;}
        if("Enviar audio a Gemini".equals(action)){transcribeAudio();return;}
        if("Dictado local".equals(action)){startLocalDictation();return;}
        if("Detener dictado".equals(action)){dictation.stop(dictationTicket);if(speech!=null)speech.stop();return;}
        if ("IA".equals(action)) {
            reset(false);strip=view;
            if (!AiSettingsActivity.isEnabled(ime)) { show("Configurar", "Cerrar"); return; }
            show("Corregir", "Gramática", "Reescribir", "Resumir", "Traducir", "Acortar", "Expandir", "Claridad", "Respuesta", "Leer texto", "Configurar", "Cerrar");
            return;
        }
        if ("Cerrar".equals(action) || "Cancelar".equals(action)) {
            onNewEditor();
            return;
        }
        if ("Configurar".equals(action)) {
            Intent i = new Intent(ime, AiSettingsActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ime.startActivity(i);
            return;
        }
        if (privateField()) {
            show("IA no disponible en campos protegidos", "Cerrar");
            return;
        }
        if ("Leer texto".equals(action)) {
            smartRead();
            return;
        }
        if(!AiSettingsActivity.isEnabled(ime)){onNewEditor();return;}
        if("Aplicar corrección".equals(action)){applyLocal();return;}
        if("Rechazar corrección".equals(action)){if(localCorrection!=null){
            personal.reject(localCorrection.original,localCorrection.replacement,typingLanguage());
            AiSettingsActivity.rejectCorrection(ime,localCorrection.original+"→"+localCorrection.replacement);
        }reset(false);return;}
        if("Revisar resultado".equals(action)){fullReview();return;}
        if("Deshacer".equals(action)) {
            if(edits.undo(target(undoText))){undoText="";show("Cambio deshecho","Cerrar");}
            else show("Ya no es seguro deshacer","Cerrar");return;
        }
        if ("Usar".equals(action)) {
            EditorSnapshot now=capture();
            if(!review.canApply(now)){show("Revisa el resultado y conserva la selección","Revisar resultado","Cancelar");return;}
            String replacement=review.result();EditorSnapshot original=review.snapshot();
            if(!(original.hasSelection()?edits.apply(original,replacement,target(original.original)):edits.applySpan(original,original.start,original.end,"",replacement,target("")))){show("El editor rechazó el cambio","Cancelar");return;}
            undoText=replacement;selected="";proposed="";review.clear();dictation.cancel();dictationBase=null;
            if(strip!=null)strip.closeAiChoices();return;
        }
        if ("Copiar resultado".equals(action)) {
            if(!review.result().isEmpty()){copy("Respuesta IA",review.result());show("Resultado copiado","Revisar resultado","Cancelar");}return;
        }
        if(entities.containsKey(action)){entityAction(action,entities.get(action));return;}
        if(action.startsWith("Tono: ")){tone=action.substring(6);show("Enviar a Gemini","Cambiar tono","Cancelar");return;}
        if("Cambiar tono".equals(action)){show("Tono: Professional","Tono: Casual","Tono: Friendly","Tono: Formal","Tono: Direct","Tono: Concise","Tono: Persuasive","Tono: Consultative","Cancelar");return;}
        if("Español México".equals(action)||"English US".equals(action)){language="Español México".equals(action)?"es-MX":"en-US";requestGemini(pendingAction);return;}
        if("Enviar a Gemini".equals(action)){if(operation(pendingAction)!=null)requestGemini(pendingAction);return;}
        if (operation(action) != null) {
            pendingAction=action;
            if("Traducir".equals(action))show("Español México","English US","Cancelar");
            else requestGemini(action);
            return;
        }
        if (strip != null && action.startsWith("IA no")) strip.closeAiChoices();
    }

    private void smartRead() {
        entities.clear();selected=currentSelection();
        if(selected.isEmpty()){show("Selecciona texto primero","Cerrar");return;}
        List<String> choices=new ArrayList<>();int index=0;
        for(EntityRecognizer.Entity entity:new EntityRecognizer().scan(selected)) {
            String label;
            switch(entity.kind){
                case ADDRESS:label="Mapa";break;case PHONE:label="Marcar";break;case EMAIL:label="Correo";break;
                case URL:label="Abrir enlace";break;case DATE:case TIME:label="Calendario";break;
                default:label="Copiar "+entity.kind.name();break;
            }
            String key=label+" · "+(++index);entities.put(key,entity);choices.add(key);
            if(entity.kind==EntityRecognizer.Kind.PHONE){String contact="Crear contacto · "+index;entities.put(contact,entity);choices.add(contact);}
            if(entity.kind==EntityRecognizer.Kind.ADDRESS){String directions="Indicaciones · "+index;entities.put(directions,entity);choices.add(directions);}
            if(entity.kind==EntityRecognizer.Kind.ADDRESS||entity.kind==EntityRecognizer.Kind.PHONE||entity.kind==EntityRecognizer.Kind.EMAIL){String cp="Copiar · "+index;entities.put(cp,entity);choices.add(cp);}
        }
        if(choices.isEmpty())choices.add("Sin acciones reconocidas");choices.add("Cerrar");show(choices.toArray(new String[0]));
    }
    private void entityAction(String action,EntityRecognizer.Entity entity) {
        if(action.startsWith("Copiar")){copy(entity.kind.name(),entity.value);return;}
        if(action.startsWith("Crear contacto")){Intent i=new Intent(Intent.ACTION_INSERT).setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);i.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE,entity.value);launch(i);return;}
        if(action.startsWith("Indicaciones")){launch(new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q="+Uri.encode(entity.value))));return;}
        switch(entity.kind){
            case ADDRESS:launch(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(entity.value))));return;
            case PHONE:launch(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+entity.value)));return;
            case EMAIL:launch(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+entity.value)));return;
            case URL:launch(new Intent(Intent.ACTION_VIEW,Uri.parse(entity.value)));return;
            case DATE:case TIME:openCalendar();return;
            default:return;
        }
    }
    private void openCalendar() {
        Intent i = new Intent(Intent.ACTION_INSERT);
        i.setData(CalendarContract.Events.CONTENT_URI);
        i.putExtra(CalendarContract.Events.TITLE, SensitiveInputPolicy.sensitiveText(selected)?"Evento":(selected.length() > 160 ? selected.substring(0,160) : selected));
        String date="",time="",location="",duration="";int dates=0,times=0,durations=0;
        for(EntityRecognizer.Entity entity:new EntityRecognizer().scan(selected)){
            if(entity.kind==EntityRecognizer.Kind.DATE){date=entity.value;dates++;}
            if(entity.kind==EntityRecognizer.Kind.TIME){time=entity.value;times++;}
            if(entity.kind==EntityRecognizer.Kind.DURATION){duration=entity.value;durations++;}
            if(entity.kind==EntityRecognizer.Kind.ADDRESS)location=entity.value;
        }
        if(!location.isEmpty())i.putExtra(CalendarContract.Events.EVENT_LOCATION,location);
        Long start=dates==1&&times==1?helium314.keyboard.latin.ai.entities.CalendarResolver.resolve(date,time,System.currentTimeMillis(),java.util.TimeZone.getDefault()):null;
        if(start!=null){i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start.longValue());Long length=durations==1?helium314.keyboard.latin.ai.entities.CalendarResolver.durationMillis(duration):null;if(length!=null)i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,start+length);}
        else if(!date.isEmpty()||!time.isEmpty())i.putExtra(CalendarContract.Events.DESCRIPTION,"Confirmar fecha y hora detectadas: "+date+" "+time);
        // Never saves directly; the calendar application owns editable confirmation.
        launch(i);
    }
    private void launch(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ime.startActivity(intent); }
        catch (Exception e) { show("No hay aplicación compatible", "Cerrar"); }
    }
    private static helium314.keyboard.latin.ai.provider.GeminiOperation operation(String action) {
        switch (action) {
            case "Corregir": return helium314.keyboard.latin.ai.provider.GeminiOperation.SPELLING;
            case "Gramática": return helium314.keyboard.latin.ai.provider.GeminiOperation.GRAMMAR;
            case "Reescribir": return helium314.keyboard.latin.ai.provider.GeminiOperation.REWRITE;
            case "Resumir": return helium314.keyboard.latin.ai.provider.GeminiOperation.SUMMARIZE;
            case "Traducir": return helium314.keyboard.latin.ai.provider.GeminiOperation.TRANSLATE;
            case "Acortar": return helium314.keyboard.latin.ai.provider.GeminiOperation.SHORTEN;
            case "Expandir": return helium314.keyboard.latin.ai.provider.GeminiOperation.EXPAND;
            case "Claridad": return helium314.keyboard.latin.ai.provider.GeminiOperation.CLARITY;
            case "Respuesta": return helium314.keyboard.latin.ai.provider.GeminiOperation.REPLY;
            default: return null;
        }
    }
    private static String providerError(Exception error){
        if(error instanceof helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException){
            helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException failure=(helium314.keyboard.latin.ai.provider.GeminiProvider.ProviderException)error;
            switch(failure.error){
                case RATE_LIMITED:return "Cuota o límite de Gemini alcanzado; reintenta después";
                case UNAUTHORIZED:case FORBIDDEN:case NO_KEY:return "Revisa la clave y sus permisos en Configurar";
                case MODEL_UNAVAILABLE:return "Modelo no disponible; elige otro en Configurar";
                case OFFLINE:return "Sin conexión; la escritura local sigue disponible";
                case TIMEOUT:return "Gemini tardó demasiado; solicitud cancelada";
                case SAFETY_BLOCKED:return "El proveedor no devolvió un resultado utilizable";
                case SENSITIVE_INPUT:return "Texto sensible: no se envía a Gemini";
                default:return "Gemini no pudo completar la solicitud ("+failure.error+")";
            }
        }
        return "No fue posible procesar la solicitud";
    }
    private void requestGemini(String action) {
        final String text = currentSelection();
        EditorInfo info = ime.getCurrentInputEditorInfo();
        if (!AiSettingsActivity.networkAllowed(ime)) { show("Configurar", "Cerrar"); return; }
        if (privateField() || info == null || SensitiveInputPolicy.sensitiveText(text)) {
            show("Texto protegido: no se envía", "Cerrar"); return;
        }
        if (text.isEmpty()) { show("Selecciona texto para IA", "Cerrar"); return; }
        final String apiKey = AiSettingsActivity.readKey(ime);
        if (apiKey.isEmpty()) { show("Configurar", "Cerrar"); return; }
        session.cancel();final EditorSnapshot requestSnapshot=capture();
        if(requestSnapshot==null||!requestSnapshot.hasSelection()){show("Selección no disponible","Cancelar");return;}
        final int request = generation;
        final InputConnection editor = ime.getCurrentInputConnection();
        selectionStart = ime.getAiInputConnection().getExpectedSelectionStart();
        selectionEnd = ime.getAiInputConnection().getExpectedSelectionEnd();
        selected = text; proposed = "";
        show("Gemini…", "Cancelar");
        final String model = AiSettingsActivity.readModel(ime);
        final String requestedTone=tone,requestedLanguage=language;
        final boolean[] complete = {false};
        session.submit(cancel -> {
            helium314.keyboard.latin.ai.provider.GeminiProvider provider = new helium314.keyboard.latin.ai.provider.GeminiProvider();
            helium314.keyboard.latin.ai.provider.GeminiProvider.Result result = provider.generate(apiKey, model,
                operation(action), requestedTone, requestedLanguage, text,
                new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation() {
                    public boolean cancelled() { return cancel.cancelled(); }
                    public void onCancel(Runnable abort) { cancel.onCancel(abort); }
                });
            complete[0] = result.complete; return result.text;
        }, (value, error) -> {
            if (generation != request || ime.getCurrentInputConnection() != editor
                || privateField() || !AiSettingsActivity.networkAllowed(ime) || !requestSnapshot.matches(capture())) return;
            if (error != null) { show(providerError(error), "Cancelar"); return; }
            proposed = value == null ? "" : value;
            review.offer(requestSnapshot,proposed,complete[0]);
            if(review.result().isEmpty()){show("Resultado vacío o demasiado largo","Cancelar");return;}
            fullReview();
        }, 35000);
    }
}
