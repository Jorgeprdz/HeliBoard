// SPDX-License-Identifier: GPL-3.0-only
package tactic.experimental;
import java.util.concurrent.*;import java.util.function.Consumer;
/** One running clause plus latest pending clause. Appended typing does not cancel running work. */
public final class ClauseReviewQueue implements AutoCloseable {
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
  new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Tactic-clause-prototype");t.setDaemon(true);return t;},
  new ThreadPoolExecutor.DiscardOldestPolicy());
 private long epoch;private boolean closed;private String latestKey;
 public synchronized void offer(String contextDependencyKey,Callable<String> work,Consumer<String> delivery){
  if(closed||contextDependencyKey.equals(latestKey))return;
  latestKey=contextDependencyKey;long ticket=epoch;
  worker.execute(()->{
   try {String value=work.call();synchronized(this){if(!closed&&epoch==ticket)delivery.accept(value);}}
   catch(InterruptedException stop){Thread.currentThread().interrupt();}
   catch(Exception error){/* Prototype failure abstains; ordinary typing is outside this executor. */}
  });
 }
 public synchronized void invalidate(){epoch++;latestKey=null;worker.getQueue().clear();}
 public void close(){synchronized(this){closed=true;invalidate();}worker.shutdownNow();}
}
