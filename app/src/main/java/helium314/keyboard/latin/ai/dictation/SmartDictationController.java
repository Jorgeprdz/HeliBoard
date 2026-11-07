// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.inputmethod.InputConnection;
import helium314.keyboard.latin.LatinIME;
import helium314.keyboard.latin.ai.*;
import helium314.keyboard.latin.ai.provider.GeminiProvider;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;

/** Owns one explicit mic session. No provisional commits or inferred external actions. */
public final class SmartDictationController implements AutoCloseable {
    public interface Host {
        EditorSnapshot capture(); boolean protectedField();
        boolean apply(EditorSnapshot expected,String original,String replacement);
        void mount(DictationPanel panel);void dismiss(DictationPanel panel);void literal();void settings();
    }
    private final LatinIME ime;private final Host host;private final DictationSession lease=new DictationSession();
    private final AiSessionCoordinator worker=new AiSessionCoordinator(action->new Handler(Looper.getMainLooper()).post(action));
    private RamblrAudioCapture capture;private DictationPanel panel;private File audio;private long ticket;private EditorSnapshot base;private String draft="";private boolean editing;
    public SmartDictationController(LatinIME ime,Host host){this.ime=ime;this.host=host;}
    public boolean active(){return panel!=null;}
    public void editorChanged(){if(panel!=null&&base!=null&&!base.matches(host.capture()))cancel();}
    public void start(boolean edit){
        cancel();editing=edit;base=host.capture();draft="";
        panel=new DictationPanel(ime,new DictationPanel.Actions(){
            public void finish(){finishCapture();}public void cancel(){SmartDictationController.this.cancel();}
            public void literal(){SmartDictationController.this.cancel();host.literal();}
            public void edit(){start(true);}public void retry(){start(editing);}
            public void settings(){SmartDictationController.this.cancel();host.settings();}
        });host.mount(panel);
        if(host.protectedField()||base==null){error("El dictado no está disponible en este campo.");return;}
        if(!AiSettingsActivity.voiceNetworkAllowed(ime)){error("Activa el dictado inteligente y su permiso de red en Ajustes.");return;}
        if(AiSettingsActivity.readKey(ime).isEmpty()||AiSettingsActivity.readModel(ime).isEmpty()){error("Configura la clave y comprueba la conexión de Gemini en Ajustes.");return;}
        if(editing){
            if(base.hasSelection())draft=base.original.toString();
            else {
                InputConnection ic=ime.getCurrentInputConnection();CharSequence before=ic==null?null:ic.getTextBeforeCursor(2049,0),after=ic==null?null:ic.getTextAfterCursor(1,0);
                if(base.start!=base.end||before==null||before.length()!=base.start||before.length()>2048||after==null||after.length()!=0){error("Selecciona el texto que quieras editar por voz.");return;}
                draft=before.toString();
            }
            if(draft.trim().isEmpty()||SensitiveInputPolicy.sensitiveText(draft)){error("Selecciona un texto no protegido para editar.");return;}
        }
        if(Build.VERSION.SDK_INT>=23&&ime.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            ime.startActivity(new Intent(ime,DictationPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));error("Permite el micrófono y vuelve a dictar.");return;
        }
        ticket=lease.begin(base,true,false);final long token=ticket;
        if(token<0){error("No se pudo iniciar el dictado.");return;}
        try{
            capture=new RamblrAudioCapture(ime);panel.listening(editing);
            if(!capture.start(new RamblrAudioCapture.Callback(){
                public void audio(File file){
                    if(panel==null||token!=ticket||!valid()){file.delete();return;}
                    lease.stop(token);audio=file;panel.processing();transcribe(token,file);
                }
                public void failed(){if(panel!=null&&token==ticket)error("No se pudo capturar el audio. Puedes volver a dictar o usar dictado literal.");}
            }))error("El micrófono no está disponible.");
        }catch(RuntimeException failure){error("No se pudo iniciar el micrófono.");}
    }
    private boolean valid(){return panel!=null&&base!=null&&!host.protectedField()&&AiSettingsActivity.voiceNetworkAllowed(ime)&&base.matches(host.capture());}
    private void finishCapture(){if(panel!=null&&capture!=null&&lease.state()==DictationSession.State.RECORDING){panel.processing();lease.stop(ticket);capture.stop();}}
    private void transcribe(long token,File file){
        final EditorSnapshot expected=base;final boolean edit=editing;final String source=draft;
        final String key=AiSettingsActivity.readKey(ime),model=AiSettingsActivity.readModel(ime);
        AtomicReference<GeminiProvider.VoiceResult> response=new AtomicReference<>();
        worker.submit(cancel->{
            try{
                long length=file.length();if(length<=0||length>3840000||length%2!=0)throw new IOException("Audio limit");
                ByteArrayOutputStream wav=new ByteArrayOutputStream((int)length+44);wav.write(helium314.keyboard.latin.ai.dictation.ramblr.WavWriter.INSTANCE.header(length,16000,1,16));
                try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(cancel.cancelled())throw new java.util.concurrent.CancellationException();wav.write(buffer,0,count);if(wav.size()>3840044)throw new IOException("Audio limit");}}
                String encoded=android.util.Base64.encodeToString(wav.toByteArray(),android.util.Base64.NO_WRAP);
                response.set(new GeminiProvider().transcribeSmartWav(key,model,encoded,AiSettingsActivity.readVocabulary(ime).toArray(new String[0]),edit,source,new GeminiProvider.Cancellation(){public boolean cancelled(){return cancel.cancelled();}public void onCancel(Runnable action){cancel.onCancel(action);}}));
                return "";
            }finally{file.delete();}
        },(unused,failure)->{
            if(audio==file)audio=null;
            if(token!=ticket||!valid()||!expected.matches(host.capture()))return;
            if(failure!=null){String reason=failure instanceof GeminiProvider.ProviderException?((GeminiProvider.ProviderException)failure).error.name():"CONNECTION_CHECK";error(AiConnectionStatus.explain(reason));return;}
            GeminiProvider.VoiceResult value=response.get();if(value==null){error("No se recibió una transcripción completa.");return;}
            String result=edit?(VoiceEditPolicy.acceptEdit(source,value.transcript,value.text)?value.text:null):VoiceEditPolicy.dictation(value.transcript,value.text);
            if(result==null){error("No se pudo validar el cambio. El borrador se conserva; prueba una instrucción más precisa.");return;}
            if(!lease.result(token,host.capture(),result)){error("El texto cambió; el resultado se descartó.");return;}
            if(!host.apply(expected,source,result)){error("El editor no aceptó el cambio. El borrador se conserva.");return;}
            cancel();
        },35000);
    }
    private void error(String value){lease.cancel();if(capture!=null){capture.close();capture=null;}if(panel!=null)panel.error(value);}
    public void cancel(){DictationPanel current=panel;panel=null;ticket++;worker.cancel();lease.cancel();base=null;draft="";if(capture!=null){capture.close();capture=null;}if(audio!=null){audio.delete();audio=null;}if(current!=null)host.dismiss(current);}
    @Override public void close(){cancel();worker.close();}
}
