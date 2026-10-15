// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import android.content.Context;import android.content.Intent;import android.os.Build;import android.os.Bundle;import android.os.Handler;import android.os.Looper;
import android.speech.*;import java.util.*;
/** Strict on-device recognition. Never falls back to a recognition service that may use cloud. */
public final class LocalSpeechRecognizer implements AutoCloseable,RecognitionListener {
 public interface Callback {void transcript(String text);void failed(int code);}
 private SpeechRecognizer recognizer;private final Callback callback;private final Handler main=new Handler(Looper.getMainLooper());
 private final Runnable deadline=()->failed(SpeechRecognizer.ERROR_SPEECH_TIMEOUT);
 public LocalSpeechRecognizer(Context context,Callback callback){this.callback=callback;
  if(Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(context))throw new IllegalStateException("On-device recognition unavailable");
  recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);recognizer.setRecognitionListener(this);
 }
 public void start(String language){start(language,Collections.emptyList());}
 public void start(String language,List<String> vocabulary){Intent intent=buildIntent(language,vocabulary,Build.VERSION.SDK_INT);
  recognizer.startListening(intent);main.postDelayed(deadline,120000);
 }
 static Intent buildIntent(String language,List<String> vocabulary,int sdk){Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
  intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"auto".equals(language)?"es-MX":language);
  intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
  if(sdk>=33&&!vocabulary.isEmpty())intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,new ArrayList<>(vocabulary));
  if(sdk>=33)intent.putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING,RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY);
  if(sdk>=34&&"auto".equals(language)){
   ArrayList<String> languages=new ArrayList<>(Arrays.asList("es-MX","en-US"));
   intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION,true);
   intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,languages);
   intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION);
   intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,new ArrayList<>(languages));
  }
  return intent;
 }
 public void stop(){if(recognizer!=null)recognizer.stopListening();}
 private void failed(int code){close();callback.failed(code);}
 @Override public void close(){main.removeCallbacks(deadline);if(recognizer!=null){SpeechRecognizer old=recognizer;recognizer=null;old.cancel();old.destroy();}}
 public void onReadyForSpeech(Bundle params){}public void onBeginningOfSpeech(){}public void onRmsChanged(float rms){}public void onBufferReceived(byte[] buffer){}public void onEndOfSpeech(){}
 public void onError(int error){if(recognizer!=null)failed(error);}
 public void onResults(Bundle results){if(recognizer==null)return;ArrayList<String> values=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);close();if(values==null||values.isEmpty())callback.failed(SpeechRecognizer.ERROR_NO_MATCH);else callback.transcript(values.get(0));}
 public void onPartialResults(Bundle results){}public void onEvent(int type,Bundle params){}
}
