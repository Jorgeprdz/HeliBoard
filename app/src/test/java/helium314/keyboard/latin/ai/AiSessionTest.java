// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiSessionTest {
    private Class<?> nested(String name) {
        try { return Class.forName("helium314.keyboard.latin.ai.AiSessionCoordinator$" + name); }
        catch (ClassNotFoundException e) { throw new AssertionError("Cancellable session is missing", e); }
    }
    private Object proxy(String name, java.lang.reflect.InvocationHandler handler) {
        Class<?> type = nested(name);
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private Object session(BlockingQueue<Runnable> queue) {
        try {
            Object dispatcher = proxy("Dispatcher", (p,m,a) -> { queue.add((Runnable) a[0]); return null; });
            return Class.forName("helium314.keyboard.latin.ai.AiSessionCoordinator")
                    .getConstructor(nested("Dispatcher")).newInstance(dispatcher);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void invoke(Object session, String name) throws Exception { session.getClass().getMethod(name).invoke(session); }
    private void submit(Object session, Object work, Object callback, long timeout) throws Exception {
        session.getClass().getMethod("submit", nested("Work"), nested("Callback"), long.class)
                .invoke(session, work, callback, timeout);
    }
    @Test public void callbackQueuedBeforeCancelIsDiscarded() throws Exception {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(); Object session = session(queue);
        AtomicInteger deliveries = new AtomicInteger();
        try {
            Object work = proxy("Work", (p,m,a) -> "result");
            Object callback = proxy("Callback", (p,m,a) -> { deliveries.incrementAndGet(); return null; });
            submit(session, work, callback, 1000);
            Runnable queued = queue.poll(2, TimeUnit.SECONDS); assertNotNull(queued);
            invoke(session, "cancel"); queued.run(); assertEquals(0, deliveries.get());
        } finally { invoke(session, "close"); }
    }
    @Test public void cancelCallsTransportAbortAndAllowsNewWork() throws Exception {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(); Object session = session(queue);
        CountDownLatch entered = new CountDownLatch(1); AtomicInteger aborts = new AtomicInteger();
        try {
            Object work = proxy("Work", (p,m,a) -> {
                a[0].getClass().getMethod("onCancel", Runnable.class).invoke(a[0], (Runnable) aborts::incrementAndGet);
                entered.countDown(); new CountDownLatch(1).await(); return "old";
            });
            Object callback = proxy("Callback", (p,m,a) -> { assertEquals("new", a[0]); return null; });
            submit(session, work, callback, 1000); assertTrue(entered.await(2, TimeUnit.SECONDS));
            invoke(session, "cancel"); assertEquals(1, aborts.get());
            submit(session, proxy("Work", (p,m,a) -> "new"), callback, 1000);
            Runnable queued = queue.poll(2, TimeUnit.SECONDS); assertNotNull(queued); queued.run();
        } finally { invoke(session, "close"); }
    }
    @Test public void deadlineEmitsOneTimeoutAndAborts() throws Exception {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(); Object session = session(queue);
        CountDownLatch entered = new CountDownLatch(1); AtomicInteger deliveries = new AtomicInteger();
        try {
            Object work = proxy("Work", (p,m,a) -> { entered.countDown(); new CountDownLatch(1).await(); return "late"; });
            Object callback = proxy("Callback", (p,m,a) -> {
                assertNull(a[0]); assertTrue(a[1] instanceof TimeoutException); deliveries.incrementAndGet(); return null;
            });
            submit(session, work, callback, 100); assertTrue(entered.await(2, TimeUnit.SECONDS));
            Runnable queued = queue.poll(2, TimeUnit.SECONDS); assertNotNull(queued); queued.run();
            assertEquals(1, deliveries.get()); assertNull(queue.poll(50, TimeUnit.MILLISECONDS));
        } finally { invoke(session, "close"); }
    }
}
