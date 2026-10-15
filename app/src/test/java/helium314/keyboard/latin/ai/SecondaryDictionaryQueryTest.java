// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import java.util.concurrent.*;import org.junit.Test;import static org.junit.Assert.*;
public class SecondaryDictionaryQueryTest {
 private void run(Callable<String> query,java.util.function.Consumer<String> sink,CountDownLatch latch){SecondaryDictionaryQuery.execute(query,sink::accept,latch);}
 @Test public void failureAlwaysReleasesWaiter(){CountDownLatch latch=new CountDownLatch(1);run(()->{throw new IllegalStateException("dictionary unavailable");},v->fail("must not publish a failed result"),latch);assertEquals(0,latch.getCount());}
 @Test public void publishesBeforeReleasingWaiter(){CountDownLatch latch=new CountDownLatch(1);String[] out={null};run(()->"word",v->{assertEquals(1,latch.getCount());out[0]=v;},latch);assertEquals("word",out[0]);assertEquals(0,latch.getCount());}
 @Test public void boundedWaitCannotFreezeSuggestionThread(){CountDownLatch latch=new CountDownLatch(1);long start=System.nanoTime();assertFalse(SecondaryDictionaryQuery.await(latch,20));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000);}
 @Test public void completedWaitReturnsImmediately(){assertTrue(SecondaryDictionaryQuery.await(new CountDownLatch(0),200));}
 @Test public void typingBudgetStaysBelowOneFrameAtTwentyFps(){assertTrue(SecondaryDictionaryQuery.TYPING_WAIT_MILLIS>0);assertTrue(SecondaryDictionaryQuery.TYPING_WAIT_MILLIS<=50);}
}
