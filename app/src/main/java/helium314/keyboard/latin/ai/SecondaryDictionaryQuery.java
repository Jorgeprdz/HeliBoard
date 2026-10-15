// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.util.concurrent.*;import java.util.function.Consumer;
/** Publish before releasing the waiter, including exceptions from a secondary dictionary. */
public final class SecondaryDictionaryQuery {
 private SecondaryDictionaryQuery(){}
 public static <T> boolean execute(Callable<T> query,Consumer<T> sink,CountDownLatch latch){
  try {sink.accept(query.call());return true;}catch(Exception failure){return false;}finally{if(latch!=null)latch.countDown();}
 }
 public static boolean await(CountDownLatch latch,long timeoutMillis){
  if(latch==null)return true;
  try{return latch.await(Math.max(1,Math.min(timeoutMillis,250)),TimeUnit.MILLISECONDS);}
  catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}
 }
}
