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
            review.offer(base,value,complete[0]);fullReview();
        },35000);
    }
    private void improveDictation(){
        if(!AiSettingsActivity.networkAllowed(ime)||privateField()||review.snapshot()==null||!review.snapshot().matches(capture())||SensitiveInputPolicy.sensitiveText(review.result())){show("No enviar este dictado; revisa permisos y privacidad","Cancelar");return;}
        final EditorSnapshot base=review.snapshot();final String text=review.result(),key=AiSettingsActivity.readKey(ime),model=AiSettingsActivity.readModel(ime);
        if(key.isEmpty()||model.isEmpty()){show("Configurar","Cancelar");return;}
        final boolean[] complete={false};show("Mejorando dictado con Gemini…","Cancelar");
        session.submit(cancel->{helium314.keyboard.latin.ai.provider.GeminiProvider.Result response=new helium314.keyboard.latin.ai.provider.GeminiProvider().generate(key,model,helium314.keyboard.latin.ai.provider.GeminiOperation.CLARITY,"casual",null,text,
            new helium314.keyboard.latin.ai.provider.GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable abort){cancel.onCancel(abort);}});complete[0]=response.complete;return response.text;
        },(value,error)->{if(privateField()||!AiSettingsActivity.networkAllowed(ime)||!base.matches(capture()))return;if(error!=null){show(providerError(error),"Cancelar");return;}review.offer(base,value,complete[0]);fullReview();},35000);
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
        if(!AiSettingsActivity.typingEnabled(ime)||typingProtected())return;
        CharSequence context=ime.getAiInputConnection().getTextBeforeCursor(512,0);
        if(context==null||SensitiveInputPolicy.sensitiveText(context))return;
        if(typed.equals(chosen))personal.keepWord(chosen,typingLanguage());
        else personal.accept(typed,chosen,typingLanguage());
    }
    private boolean finishAutomatic(EditorSnapshot base,String value){
        automaticOriginal="";automaticLanguage="";
        boolean accepted=base.hasSelection()?automaticEdits.apply(base,value,target(base.original)):
            automaticEdits.applySpan(base,base.start,base.end,"",value,target(""));
        dictation.cancel();dictationBase=null;
        if(speech!=null){speech.close();speech=null;}
        if(!accepted){show("El editor rechazó el cambio","Cerrar");return false;}
        automaticReplacement=value;automaticAfter=capture();
        CharSequence after=ime.getAiInputConnection().getTextBeforeCursor(2048,0);
        automaticContext=after==null?"":after.toString();
        if(strip!=null)strip.closeAiChoices();
        return true;
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
    private final AiSessionCoordinator continuousSession=new AiSessionCoordinator(action->main.post(action));
    private final helium314.keyboard.latin.ai.local.NativeWritingModel writingModel=new helium314.keyboard.latin.ai.local.NativeWritingModel();
    private final Runnable releaseWritingModel=writingModel::close;
    private long continuousEpoch;
    private boolean continuousRunning;
    private QueuedReview latestReview;
    private static final class QueuedReview {
        final EditorSnapshot base;final String context;
        final helium314.keyboard.latin.ai.local.ContinuousReviewAnchor anchor;
        QueuedReview(EditorSnapshot b,String c,helium314.keyboard.latin.ai.local.ContinuousReviewAnchor a){base=b;context=c;anchor=a;}
    }
    private final LinkedHashSet<Integer> offeredSpans=new LinkedHashSet<>(),examinedSources=new LinkedHashSet<>();
    private final java.util.ArrayDeque<PendingReview> pendingReviews=new java.util.ArrayDeque<>();
    private static final class PendingReview {
        final helium314.keyboard.latin.ai.local.ContinuousReviewAnchor anchor;
        final java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> corrections;
        PendingReview(helium314.keyboard.latin.ai.local.ContinuousReviewAnchor a,java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> c){anchor=a;corrections=c;}
    }
    private void invalidateContinuous(){
        continuousEpoch++;continuousSession.cancel();continuousRunning=false;latestReview=null;pendingReviews.clear();examinedSources.clear();
        main.removeCallbacks(releaseWritingModel);main.postDelayed(releaseWritingModel,45000);
    }
    private long modelRetryAfter;
    public void onMemoryPressure(){invalidateContinuous();writingModel.close();modelRetryAfter=android.os.SystemClock.elapsedRealtime()+60000;}
    public void onInputSubmitted(){invalidateContinuous();writingModel.close();}
    private helium314.keyboard.latin.ai.local.ContinuousReviewAnchor anchor(EditorSnapshot base,String context){
        return new helium314.keyboard.latin.ai.local.ContinuousReviewAnchor(base.editor,base.packageName,base.fieldId,
            continuousEpoch,base.start-context.length(),context);
    }
    private boolean anchorMatches(helium314.keyboard.latin.ai.local.ContinuousReviewAnchor anchor,EditorSnapshot now,String before){
        return now!=null&&anchor.matches(now.editor,now.packageName,now.fieldId,continuousEpoch,now.start,now.end,before);
    }
    private void queueContinuous(EditorSnapshot base,String context){
        if(context.length()>512||base.start<context.length())return;
        if(continuousRunning){latestReview=new QueuedReview(base,context,anchor(base,context));return;}
        final helium314.keyboard.latin.DictionaryFacilitator dictionary=ime.getAiDictionary();
        if(dictionary==null||!dictionary.hasAtLeastOneInitializedMainDictionary())return;
        final helium314.keyboard.latin.ai.local.ContinuousReviewAnchor anchor=anchor(base,context);
        final helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon observed=personalLexicon();
        final Set<String> rejected=AiSettingsActivity.rejectedCorrections(ime);
        final boolean model=android.os.SystemClock.elapsedRealtime()>=modelRetryAfter
            &&helium314.keyboard.latin.ai.local.WritingModelFiles.enabled(ime)
            &&helium314.keyboard.latin.ai.local.WritingModelFiles.available(ime)
            &&context.trim().split("\\s+").length>=4;
        String register=AiSettingsActivity.styleEnabled(ime)?AiSettingsActivity.styleTone(ime):"original";
        if("auto".equals(register))register=helium314.keyboard.latin.ai.local.PersonalStyleProfile.preferredRegister(context,personal.styleSnapshot());
        StringBuilder traits=new StringBuilder(register);
        if(AiSettingsActivity.styleEnabled(ime)){
            Map<String,Long> profile=personal.styleSnapshot();
            for(String feature:Arrays.asList("ending.ellipsis","ending.question","clause.coordination","clause.subordination","language.mixed"))
                if(profile.getOrDefault(feature,0L)>=5)traits.append("; ").append(feature);
        }
        final String style=traits.toString();
        final Set<Integer> examined=new HashSet<>(examinedSources);
        final java.util.concurrent.atomic.AtomicReference<Integer> examinedKey=new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction>> found=
            new java.util.concurrent.atomic.AtomicReference<>(Collections.emptyList());
        continuousRunning=true;if(model)main.removeCallbacks(releaseWritingModel);
        continuousSession.submit(cancel->{
            long deadline=System.nanoTime()+100_000_000L;
            helium314.keyboard.latin.ai.local.LocalContextReviewer.Cancellation stop=()->cancel.cancelled()||System.nanoTime()>deadline;
            helium314.keyboard.latin.ai.local.BoundedDictionaryLexicon vocabulary=
                new helium314.keyboard.latin.ai.local.BoundedDictionaryLexicon(observed,dictionary::isValidSpellingWord,stop);
            found.set(boundaryReviewer.review(context,vocabulary,rejected,stop));
            for(helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction repair:found.get())
                if(repair.automaticAllowed)return ""; // Local repairs never wait for generation.
            if(model&&!cancel.cancelled()){
                helium314.keyboard.latin.ai.local.BoundedDictionaryLexicon focusLexicon=
                    new helium314.keyboard.latin.ai.local.BoundedDictionaryLexicon(observed,dictionary::isValidSpellingWord,cancel::cancelled);
                for(helium314.keyboard.latin.ai.local.CorrectionFocus focus:
                    helium314.keyboard.latin.ai.local.CorrectionFocus.targets(context,focusLexicon,cancel::cancelled)){
                    int key=Objects.hash(anchor.start+focus.start,focus.original);if(examined.contains(key))continue;
                    examinedKey.set(key);
                    String generated=writingModel.infer(ime,
                        helium314.keyboard.latin.ai.local.CorrectionFocus.prompt(context,focus.original,style),cancel);
                    if(!generated.isEmpty()){
                        List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> all=new ArrayList<>();
                        for(helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction edit:
                            helium314.keyboard.latin.ai.local.SemanticEditGuard.proposals(focus.original,generated,focusLexicon,rejected,cancel::cancelled))
                            all.add(new helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction(
                                focus.start+edit.start,focus.start+edit.end,edit.original,edit.replacement,edit.reason,0,false));
                        all.addAll(found.get());found.set(all);
                    }
                    break; // One targeted decode per coalesced request, never one per key.
                }
            }
            return "";
        },(unused,error)->{
            continuousRunning=false;if(model)main.postDelayed(releaseWritingModel,45000);
            boolean followFocus=false;
            try {
            if(error!=null||typingProtected()||!AiSettingsActivity.typingEnabled(ime))return;
            CharSequence current=ime.getAiInputConnection().getTextBeforeCursor(2048,0);
            if(current==null||!anchorMatches(anchor,capture(),current.toString()))return;
            if(examinedKey.get()!=null){followFocus=true;examinedSources.add(examinedKey.get());while(examinedSources.size()>64)examinedSources.remove(examinedSources.iterator().next());}
            java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> safe=new ArrayList<>();
            for(helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction correction:found.get())
                if(!"local-model-explicit-choice".equals(correction.reason)||helium314.keyboard.latin.ai.local.WritingModelFiles.enabled(ime))safe.add(correction);
            if(safe.isEmpty())return;
            pendingReviews.add(new PendingReview(anchor,safe));
            while(pendingReviews.size()>8)pendingReviews.removeFirst();
            // While composing, keep the verified result for the next boundary. Never finish or
            // recreate an active native composition just to apply a background result.
            if(!ime.hasAiComposition())applyPendingReview();
            } finally {
                if(followFocus&&latestReview==null&&anchor.epoch==continuousEpoch)
                    latestReview=new QueuedReview(base,context,anchor);
                drainLatestReview();
            }
        },18000);
    }
    private void drainLatestReview(){
        QueuedReview latest=latestReview;latestReview=null;
        if(latest==null||typingProtected()||!AiSettingsActivity.typingEnabled(ime))return;
        CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(2048,0);
        if(before!=null&&anchorMatches(latest.anchor,capture(),before.toString()))queueContinuous(latest.base,latest.context);
    }
    private boolean applyPendingReview(){
        if(typingProtected()||ime.hasAiComposition()||!AiSettingsActivity.typingEnabled(ime))return false;
        while(!pendingReviews.isEmpty()){
            PendingReview pending=pendingReviews.removeFirst();
            EditorSnapshot now=capture();CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(2048,0);
            if(before==null||!anchorMatches(pending.anchor,now,before.toString()))continue;
            String current=before.toString();int baseOffset=now.start-current.length();
            for(helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction c:pending.corrections){
                if("local-model-explicit-choice".equals(c.reason)&&!helium314.keyboard.latin.ai.local.WritingModelFiles.enabled(ime))continue;
                int start=pending.anchor.start+c.start-baseOffset,end=pending.anchor.start+c.end-baseOffset;
                if(start<0||end>current.length()||!current.substring(start,end).equals(c.original))continue;
                if(AiSettingsActivity.rejectedCorrections(ime).contains(c.original+"→"+c.replacement))continue;
                helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction moved=
                    new helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction(start,end,c.original,c.replacement,c.reason,c.confidence,c.automaticAllowed);
                if(c.automaticAllowed)return applyBoundary(now,current,moved);
                if(!offeredSpans.add(Objects.hash(pending.anchor.start+c.start,c.original,c.replacement)))continue;
                while(offeredSpans.size()>32)offeredSpans.remove(offeredSpans.iterator().next());
                offerContextCorrection(now,current,moved);
                // One visible proposal per dependency anchor, never repeated inference per key.
                return false;
            }
        }
        return false;
    }

    private helium314.keyboard.latin.SuggestedWords nativeSuggestions;
    public void onPredictions(helium314.keyboard.latin.SuggestedWords words,SuggestionStripView view) {
        if(!AiSettingsActivity.typingEnabled(ime)||typingProtected())return;
        if(!words.isPrediction()&&words.size()>0) {
            String typed=words.mTypedWordInfo==null?words.getWord(0):words.mTypedWordInfo.mWord;
            List<String> candidates=new ArrayList<>();
            for(int i=0;i<words.size();i++) {
                helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo info=words.getInfo(i);
                if(info.isKindOf(helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_CORRECTION)
                        &&info.mSourceDict!=null) candidates.add(info.mWord);
            }
            lexicon.record(typed,words.mTypedWordValid,candidates,!words.mIsObsoleteSuggestions&&words.mTypedWordInfo!=null);
            Map<String,Double> priors=new HashMap<>();
            // Use native ordering, which already combines lexical frequency and active languages.
            for(int i=0;i<candidates.size();i++)priors.put(candidates.get(i),.12/(i+1));
            lexicon.nativePriors(typed,priors);
            updateKeyGeometry();
            for(String word:candidates)if(!word.equals(typed))lexicon.record(word,true,Collections.emptyList());
        }
        if(view==null||view.isAiActive())return;
        strip=view;nativeSuggestions=words;
        // Keep native candidates while a bounded word-boundary review is pending.
    }
    public helium314.keyboard.latin.SuggestedWords protectNativeSuggestions(helium314.keyboard.latin.SuggestedWords words){
        if(!AiSettingsActivity.typingEnabled(ime)||typingProtected()||!words.mWillAutoCorrect||words.isPrediction()
            ||words.mTypedWordInfo==null||words.size()<2)return words;
        // User-defined shortcuts retain their explicit native expansion policy.
        if(words.getInfo(1).isKindOf(helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_SHORTCUT))return words;
        String typed=words.mTypedWordInfo.mWord,replacement=words.getWord(1);
        boolean veto=words.mTypedWordValid||personal.protectedWord(typed)||personal.rejected(typed,replacement,typingLanguage())
            ||!typed.matches("[\\p{Ll}\\p{M}]{2,48}")||!replacement.matches("[\\p{Ll}\\p{M}]{2,48}")
            ||AiSettingsActivity.rejectedCorrections(ime).contains(typed+"→"+replacement);
        if(!veto)return words;
        ArrayList<helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo> keep=new ArrayList<>();
        for(int i=0;i<words.size();i++)keep.add(words.getInfo(i));
        return new helium314.keyboard.latin.SuggestedWords(keep,words.mRawSuggestions,words.mTypedWordInfo,
            words.mTypedWordValid||personal.protectedWord(typed),false,words.mIsObsoleteSuggestions,words.mInputStyle,words.mSequenceNumber);
    }
    public void onNativeCorrectionReverted(String typed,String replacement){
        if(!AiSettingsActivity.typingEnabled(ime)||typingProtected()
            ||!typed.matches("[\\p{Ll}\\p{M}]{2,48}")||!replacement.matches("[\\p{Ll}\\p{M}]{2,48}"))return;
        CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(512,0);
        if(before==null||SensitiveInputPolicy.sensitiveText(before))return;
        personal.reject(typed,replacement,typingLanguage());
        AiSettingsActivity.rejectCorrection(ime,typed+"→"+replacement);
    }
    private Object rankedKeyboard;
    private int styleBoundary=-1;
    private void updateKeyGeometry(){
        helium314.keyboard.keyboard.Keyboard keyboard=helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().getKeyboard();
        if(keyboard==null||keyboard==rankedKeyboard)return;
        Map<Character,double[]> positions=new HashMap<>();
        for(char key:"abcdefghijklmnopqrstuvwxyzñ ".toCharArray()){
            helium314.keyboard.keyboard.Key value=keyboard.getKey(key);
            if(value!=null)positions.put(key,new double[]{value.getX()+value.getWidth()/2.0,value.getY()+value.getHeight()/2.0,value.getWidth(),value.getHeight()});
        }
        lexicon.keyboard(positions);rankedKeyboard=keyboard;
    }
    private helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon personalLexicon(){
        final helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon base=lexicon.snapshot();
        final String language=typingLanguage();
        return new helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon(){
            public boolean preserved(String word){return personal.protectedWord(word);}
            public boolean spaceNeighbor(char c){return base.spaceNeighbor(c);}
            public boolean valid(String word){return personal.protectedWord(word)||base.valid(word);}
            public boolean knownInvalid(String word){return !personal.protectedWord(word)&&base.knownInvalid(word);}
            public List<String> candidates(String word){
                List<String> normalized=new ArrayList<>();
                for(String candidate:base.candidates(word))normalized.add(candidate.toLowerCase(Locale.ROOT));
                return normalized;
            }
            public double prior(String source,String candidate){return base.prior(source,candidate);}
            public double keyDistance(char a,char b){return base.keyDistance(a,b);}
            public double personalPreference(String source,String candidate){return personal.preference(source,candidate,language);}
            public boolean rejected(String source,String candidate){return personal.rejected(source,candidate,language);}
        };
    }
    /** Reorders existing next-word predictions only. Never generates content or enables auto-commit. */
    public helium314.keyboard.latin.SuggestedWords rankStylePredictions(helium314.keyboard.latin.SuggestedWords words){
        if(!AiSettingsActivity.styleEnabled(ime)||typingProtected()||!words.isPrediction()||words.mIsObsoleteSuggestions||words.size()<2)return words;
        CharSequence context=ime.getAiInputConnection().getTextBeforeCursor(256,0);
        if(context==null||SensitiveInputPolicy.sensitiveText(context))return words;
        final Map<String,Long> profile=personal.styleSnapshot();
        String detected=AiSettingsActivity.styleTone(ime);
        if("auto".equals(detected))detected=helium314.keyboard.latin.ai.local.PersonalStyleProfile.preferredRegister(context.toString(),profile);
        final String register=detected;
        ArrayList<helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo> ranked=new ArrayList<>();
        final Map<helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo,Double> scores=new IdentityHashMap<>();
        for(int i=0;i<words.size();i++){
            helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo word=words.getInfo(i);
            if(!word.isKindOf(helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_PREDICTION))return words;
            ranked.add(word);scores.put(word,-i*.03+helium314.keyboard.latin.ai.local.PersonalStyleProfile.preference(word.mWord,register,profile,context.toString().trim().isEmpty())
                +(personal.protectedWord(word.mWord)?.06:0));
        }
        ranked.sort((a,b)->Double.compare(scores.get(b),scores.get(a)));
        return new helium314.keyboard.latin.SuggestedWords(ranked,words.mRawSuggestions,words.mTypedWordInfo,words.mTypedWordValid,words.mWillAutoCorrect,words.mIsObsoleteSuggestions,words.mInputStyle,words.mSequenceNumber);
    }
    private void observeCompletedStyle(String context,int cursor){
        if(!AiSettingsActivity.styleEnabled(ime)||cursor==styleBoundary)return;
        String sentence=context.trim();if(sentence.isEmpty()||".!?…".indexOf(sentence.charAt(sentence.length()-1))<0)return;
        int contentEnd=sentence.length();
        while(contentEnd>0&&".!?…".indexOf(sentence.charAt(contentEnd-1))>=0)contentEnd--;
        if(contentEnd==0)return;
        int start=-1;
        for(int i=0;i<contentEnd;i++)if(".!?".indexOf(sentence.charAt(i))>=0||sentence.charAt(i)==10)start=i;
        sentence=sentence.substring(start+1).trim();
        if(sentence.isEmpty())return;
        personal.observeStyle(sentence,typingLanguage(),start<0);styleBoundary=cursor;
    }

    private void reviewLocal() {
        if(typingProtected()||!AiSettingsActivity.typingEnabled(ime)||ime.hasAiComposition())return;
        EditorSnapshot base=capture();if(base==null||base.start!=base.end)return;
        CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(512,0);
        if(before==null||SensitiveInputPolicy.sensitiveText(before)||base.start!=before.length())return;
        queueContinuous(base,before.toString());
    }
    /** A contextual candidate is never committed by native word insertion, including stale taps. */
    public static final class ContextSuggestion extends helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo {
        final AiKeyboardAssistant owner;final EditorSnapshot base;final String context;
        final helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction correction;
        ContextSuggestion(AiKeyboardAssistant owner,EditorSnapshot base,String context,
                helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction correction){
            super(correction.original+" → "+correction.replacement,"",0,KIND_CORRECTION,helium314.keyboard.latin.dictionary.Dictionary.DICTIONARY_HARDCODED,-1,-1);
            this.owner=owner;this.base=base;this.context=context;this.correction=correction;
        }
    }
    private void offerContextCorrection(EditorSnapshot base,String context,
            helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction correction){
        if(nativeSuggestions==null||(strip!=null&&strip.isAiActive()))return;
        ArrayList<helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo> candidates=new ArrayList<>();
        candidates.add(new ContextSuggestion(this,base,context,correction));
        if(nativeSuggestions.isPrediction())for(int i=0;i<nativeSuggestions.size()&&candidates.size()<18;i++)
            if(nativeSuggestions.getInfo(i).isKindOf(helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_PREDICTION))candidates.add(nativeSuggestions.getInfo(i));
        ime.showAiContextSuggestions(new helium314.keyboard.latin.SuggestedWords(candidates,null,null,
            true,false,false,helium314.keyboard.latin.SuggestedWords.INPUT_STYLE_PREDICTION,nativeSuggestions.mSequenceNumber));
    }
    public void acceptContextSuggestion(ContextSuggestion suggestion){
        if(suggestion.owner!=this||("local-model-explicit-choice".equals(suggestion.correction.reason)
            &&!helium314.keyboard.latin.ai.local.WritingModelFiles.enabled(ime)))return;
        if(applyBoundary(suggestion.base,suggestion.context,suggestion.correction))
            personal.accept(suggestion.correction.original.toLowerCase(Locale.ROOT),
                suggestion.correction.replacement.toLowerCase(Locale.ROOT),typingLanguage());
    }
    private boolean applyBoundary(EditorSnapshot base,String context,
            helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction correction){
        if(typingProtected()||!AiSettingsActivity.typingEnabled(ime)||ime.hasAiComposition()||!base.matches(capture())
            ||(strip!=null&&strip.isAiActive()))return false;
        CharSequence latest=ime.getAiInputConnection().getTextBeforeCursor(context.length(),InputConnection.GET_TEXT_WITH_STYLES);
        if(latest==null||!context.contentEquals(latest))return false;
        CharSequence original=SpannedString.valueOf(latest.subSequence(correction.start,correction.end));
        // Preserve character styling and restore the original spans exactly on Undo. Native
        // suggestion/composing spans must not survive changed text.
        android.text.SpannableString replacement=new android.text.SpannableString(correction.replacement);
        if(original instanceof android.text.Spanned){
            android.text.Spanned styled=(android.text.Spanned)original;
            for(android.text.style.CharacterStyle span:styled.getSpans(0,styled.length(),android.text.style.CharacterStyle.class)){
                int flags=styled.getSpanFlags(span);
                if(span instanceof android.text.style.SuggestionSpan||(flags&android.text.Spanned.SPAN_COMPOSING)!=0)continue;
                int left=Math.max(0,Math.min(replacement.length(),styled.getSpanStart(span)));
                int right=styled.getSpanEnd(span)==styled.length()?replacement.length():Math.min(replacement.length(),styled.getSpanEnd(span));
                if(right>left)replacement.setSpan(android.text.style.CharacterStyle.wrap(span),left,right,flags);
            }
        }
        int offset=base.start-context.length();
        if(offset<0||!automaticEdits.applySpan(base,offset+correction.start,offset+correction.end,original,replacement,target(original)))return false;
        automaticOriginal=correction.original;automaticLanguage=typingLanguage();automaticReplacement=correction.replacement;
        automaticAfter=capture();
        CharSequence after=ime.getAiInputConnection().getTextBeforeCursor(2048,0);
        automaticContext=after==null?"":after.toString();
        invalidateContinuous();
        localSession.cancel();main.removeCallbacks(localDebounce);localBase=null;localCorrection=null;
        return true;
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
        if(!AiSettingsActivity.typingEnabled(ime)||typingProtected()
                ||(strip!=null&&strip.isAiActive())||ime.getAiInputConnection().hasSlowInputConnection())return;
        if(applyPendingReview())return;
        EditorSnapshot base=capture();
        if(base==null||base.start!=base.end||base.start<0
                ||ime.getAiInputConnection().hasSlowInputConnection())return;
        CharSequence before=ime.getAiInputConnection().getTextBeforeCursor(2048,InputConnection.GET_TEXT_WITH_STYLES);
        if(before==null||before.length()==0||base.start!=before.length()||SensitiveInputPolicy.sensitiveText(before))return;
        int windowStart=0;
        if(before.length()>512){
            String complete=before.toString();
            // Only start after a complete sentence/line whose prefix is known. Unbalanced
            // quoting/code cannot be inferred from a truncated window, so abstain.
            if(complete.matches("(?s).*[\\\"'`«»“”].*"))return;
            java.util.regex.Matcher boundary=java.util.regex.Pattern.compile("[.!?\\n]\\s+").matcher(complete);
            while(boundary.find())if(complete.length()-boundary.end()<=512){windowStart=boundary.end();break;}
            if(windowStart==0)return;
            before=before.subSequence(windowStart,before.length());
        }
        String context=before.toString();
        int offset=base.start-context.length();
        if(offset<0)return;
        observeCompletedStyle(context,base.start);
        queueContinuous(base,context);
        final long deadline=System.nanoTime()+8_000_000L;
        java.util.List<helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction> corrections=
            boundaryReviewer.review(
                context,personalLexicon(),AiSettingsActivity.rejectedCorrections(ime),
                ()->System.nanoTime()>deadline);
        if(System.nanoTime()>deadline)return;
        java.util.regex.Matcher last=java.util.regex.Pattern.compile("([\\p{Ll}\\p{M}]{2,48})[ \\t.,!?;:]+$").matcher(context);
        if(last.find()&&(last.start()==0||Character.isWhitespace(context.charAt(last.start()-1)))){
            String typed=last.group(1);
            helium314.keyboard.latin.ai.local.LocalContextReviewer.Lexicon known=personalLexicon();
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
            applyBoundary(base,context,correction);
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
        invalidateContinuous();clearAutomaticUndo();
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
            .setTitle(!review.complete()?"Resultado incompleto":review.meaningRisk()?"Revisa cifras, negaciones y significado":"Resultado")
            .setView(scroll)
            .setPositiveButton("Aplicar",(d,w)->{if(!privateField()&&review.reviewed(capture()))onAction("Usar",strip);})
            .setNeutralButton("Copiar",(d,w)->{copy("Respuesta IA",review.result());reset(false);})
            .setNegativeButton("Cancelar",(d,w)->reset(false)).setOnCancelListener(d->reset(false)).create();
        reviewDialog=dialog;
        dialog.setOnDismissListener(d->{if(reviewDialog==dialog)reviewDialog=null;});
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
    private boolean typingProtected(){
        EditorInfo editor=ime.getCurrentInputEditorInfo();
        return privateField()||editor==null||"com.termux".equals(editor.packageName)
            ||(editor.inputType&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0;
    }
    private String currentSelection() {
        InputConnection ic = ime.getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence value = ic.getSelectedText(0);
        if (value == null) return "";
        return value.toString();
    }
    public void onNewEditor() { invalidateContinuous();offeredSpans.clear();examinedSources.clear();writingModel.close();reset(true); }
    private void reset(boolean forgetWords) {
        clearAutomaticUndo();
        generation++; revision++;dictation.cancel();dictationBase=null;if(audioCapture!=null){audioCapture.close();audioCapture=null;}if(recordedAudio!=null){recordedAudio.delete();recordedAudio=null;}if(speech!=null){speech.close();speech=null;} session.cancel();localSession.cancel();main.removeCallbacks(localDebounce);localBase=null;localCorrection=null;localOriginal=null;localContext="";if(forgetWords){lexicon.clear();rankedKeyboard=null;styleBoundary=-1;nativeSuggestions=null;}review.clear();edits.clear();undoText="";pendingAction="";entities.clear();
        if(reviewDialog!=null){AlertDialog old=reviewDialog;reviewDialog=null;old.dismiss();}
        selected = ""; proposed = ""; address = ""; phone = ""; clabe = ""; email = "";
        if (strip != null) strip.closeAiChoices();
        strip = null;
    }
    public void close() { onNewEditor(); session.close();localSession.close();continuousSession.close();main.removeCallbacks(releaseWritingModel); }
    public void onUserInput() { invalidateContinuous();reset(false); }
    public void onUserInput(boolean backspace) {
        if(!helium314.keyboard.latin.ai.local.WritingModelFiles.enabled(ime))writingModel.close();
        if(backspace){invalidateContinuous();if(automaticEdits.hasUndo())return;}
        reset(false);
    }
    public void onSelectionChanged(int start,int end,int composingStart,int composingEnd) {
        if(!ownEdit&&composingStart>=0&&composingEnd>=composingStart)clearAutomaticUndo();
        onSelectionChanged(start,end);
    }
    public void onSelectionChanged(int start, int end) {
        if(ownEdit)return;
        // Selection movement is not an append. Normal selection notifications at the expected
        // insertion cursor are allowed; the full dependency prefix is still checked on delivery.
        if(start!=end)invalidateContinuous();
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
        invalidateContinuous();
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
        if("Avanzado".equals(action)){show("Grabar audio para Gemini","Configurar","Cerrar");return;}
        if("Grabar audio para Gemini".equals(action)){startAudioCapture();return;}
        if("Detener grabación".equals(action)){if(audioCapture!=null)audioCapture.stop();dictation.stop(dictationTicket);return;}
        if("Enviar audio a Gemini".equals(action)){transcribeAudio();return;}
        if("Dictado local".equals(action)){startLocalDictation();return;}
        if("Detener dictado".equals(action)){dictation.stop(dictationTicket);if(speech!=null)speech.stop();return;}
        if ("IA".equals(action)) {
            reset(false);strip=view;
            if (!AiSettingsActivity.isEnabled(ime)) { show("Configurar", "Cerrar"); return; }
            show("Corregir", "Gramática", "Reescribir", "Resumir", "Traducir", "Acortar", "Expandir", "Claridad", "Respuesta", "Leer texto", "Avanzado", "Configurar", "Cerrar");
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
            if(!finishAutomatic(original,replacement))return;
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
