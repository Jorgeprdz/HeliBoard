// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.util.concurrent.*;
/** Publish before releasing the waiter, including exceptions from a secondary dictionary. */
public final class SecondaryDictionaryQuery {
 public static final long TYPING_WAIT_MILLIS=40;
 private SecondaryDictionaryQuery(){}
 public interface Sink<T>{void accept(T value);}
 public static <T> boolean execute(Callable<T> query,Sink<T> sink,CountDownLatch latch){
  try {sink.accept(query.call());return true;}catch(Exception failure){return false;}finally{if(latch!=null)latch.countDown();}
 }
 public static boolean await(CountDownLatch latch,long timeoutMillis){
  if(latch==null)return true;
  try{return latch.await(Math.max(1,Math.min(timeoutMillis,250)),TimeUnit.MILLISECONDS);}
  catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}
 }
}
