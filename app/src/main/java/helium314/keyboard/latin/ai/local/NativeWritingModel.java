// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.local;
import android.content.Context;import java.io.File;import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;
import helium314.keyboard.latin.ai.AiSessionCoordinator;
/** JNI runtime packaged and signed in APK. A single worker owns load/inference, never the IME thread. */
public final class NativeWritingModel implements AutoCloseable {
 private final AtomicLong handle=new AtomicLong();
 private long lifetime;
 private static final ExecutorService cleanup=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Writing-model-release");t.setDaemon(true);return t;});
 private static synchronized void loadLibrary(){System.loadLibrary("heliboard_writing");}
 private static native long open(byte[] path);
 private static native void reset(long handle);
 private static native void cancel(long handle);
 private static native byte[] generate(long handle,byte[] prompt);
 private static native void release(long handle);
 public String infer(Context context,String prompt,AiSessionCoordinator.Cancellation cancellation)throws Exception{
  if(!WritingModelFiles.enabled(context)||!WritingModelFiles.available(context)||cancellation.cancelled())return "";
  if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("worker required");
  android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
  final long ticket;synchronized(this){ticket=lifetime;}
  long id=handle.get();
  if(id==0){
   File file=WritingModelFiles.file(context);if(!WritingModelFiles.verify(file,cancellation))return "";
   try{loadLibrary();id=open(file.getAbsolutePath().getBytes(StandardCharsets.UTF_8));}catch(UnsatisfiedLinkError unsupported){return "";}
   if(id==0)return "";
   synchronized(this){if(ticket!=lifetime){final long abandoned=id;cleanup.execute(()->release(abandoned));return "";}handle.set(id);}
  }
  final long active=id;reset(active);cancellation.onCancel(()->cancel(active));
  if(cancellation.cancelled()||!WritingModelFiles.enabled(context))return "";
  byte[] output=generate(active,prompt.getBytes(StandardCharsets.UTF_8));
  return cancellation.cancelled()?"":new String(output,StandardCharsets.UTF_8).trim();
 }
 @Override public void close(){final long id;synchronized(this){lifetime++;id=handle.getAndSet(0);}if(id!=0){cancel(id);cleanup.execute(()->release(id));}}
}
