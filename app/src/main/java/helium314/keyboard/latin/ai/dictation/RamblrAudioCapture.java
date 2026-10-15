// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import android.content.Context;import android.os.Handler;import android.os.Looper;
import java.io.File;import kotlin.Unit;
import helium314.keyboard.latin.ai.dictation.ramblr.RecordingEngine;
import helium314.keyboard.latin.ai.dictation.ramblr.RecordingStateMachine;
/** Main-thread bridge; Ramblr's reader alone drains, stops and releases AudioRecord. */
public final class RamblrAudioCapture implements AutoCloseable {
 public interface Callback {void audio(File pcm);void failed();}
 private static boolean cleaned;
 private final RecordingStateMachine state=new RecordingStateMachine();private final RecordingEngine engine;
 private final Handler main=new Handler(Looper.getMainLooper());private boolean closed;
 public RamblrAudioCapture(Context ctx){File cache=new File(ctx.getCacheDir(),"heliboard_ai_audio");if(!cache.isDirectory()&&!cache.mkdirs())throw new IllegalStateException("Audio cache unavailable");synchronized(RamblrAudioCapture.class){if(!cleaned){AudioOrphanCleaner.clean(cache);cleaned=true;}}engine=new RecordingEngine(cache,state);}
 public boolean start(Callback callback){return engine.start(result->{main.post(()->{
  File pcm=result.getPcmFile();
  if(closed||result.getDiscarded()||result.getStopReason()==RecordingEngine.StopReason.ERROR||pcm==null){if(pcm!=null)pcm.delete();if(!closed)callback.failed();}
  else callback.audio(pcm);
 });return Unit.INSTANCE;},(bytes,count)->Unit.INSTANCE);}
 public void stop(){state.tryStartTranscribing();}
 @Override public void close(){closed=true;state.reset();}
}
