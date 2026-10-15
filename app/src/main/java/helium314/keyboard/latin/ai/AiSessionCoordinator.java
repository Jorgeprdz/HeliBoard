// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded job; both transport and queued UI delivery are invalidated by cancel. */
public final class AiSessionCoordinator implements AutoCloseable {
    public interface Dispatcher { void post(Runnable action); }
    public interface Work { String run(Cancellation cancellation) throws Exception; }
    public interface Callback { void complete(String result, Exception error); }
    public static final class Cancellation {
        private boolean cancelled;
        private Runnable abort;
        private final AtomicBoolean delivered = new AtomicBoolean();
        public synchronized boolean cancelled() { return cancelled || Thread.currentThread().isInterrupted(); }
        public void onCancel(Runnable action) {
            boolean now;
            synchronized (this) { abort = action; now = cancelled; }
            if (now && action != null) action.run();
        }
        void abort() {
            Runnable action;
            synchronized (this) { if (cancelled) return; cancelled = true; action = abort; abort = null; }
            if (action != null) try { action.run(); } catch (RuntimeException ignored) { /* cleanup only */ }
        }
    }
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> { Thread t = new Thread(action, "HeliBoard-AI"); t.setDaemon(true); return t; });
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(action -> {
        Thread t = new Thread(action, "HeliBoard-AI-deadline"); t.setDaemon(true); return t;
    });
    private final Dispatcher dispatcher;
    private long generation;
    private boolean closed;
    private Cancellation cancellation;
    private Future<?> future;
    private ScheduledFuture<?> deadline;

    public AiSessionCoordinator(Dispatcher dispatcher) { this.dispatcher = dispatcher; }

    public synchronized void submit(Work work, Callback callback, long timeoutMillis) {
        if (closed) throw new IllegalStateException("Session closed");
        cancel();
        final long ticket = generation;
        final Cancellation scope = new Cancellation(); cancellation = scope;
        future = worker.submit(() -> {
            String value = null; Exception error = null;
            try { if (!scope.cancelled()) value = work.run(scope); }
            catch (Exception failure) { error = failure; }
            if (!scope.cancelled() && scope.delivered.compareAndSet(false, true)) {
                final String result = value; final Exception failure = error;
                dispatcher.post(() -> { if (current(ticket)) callback.complete(result, failure); });
            }
        });
        final Future<?> submitted = future;
        deadline = deadlines.schedule(() -> {
            if (scope.delivered.compareAndSet(false, true)) {
                scope.abort(); submitted.cancel(true);
                dispatcher.post(() -> { if (current(ticket)) callback.complete(null, new TimeoutException("AI deadline exceeded")); });
            }
        }, Math.max(1, Math.min(timeoutMillis, 60000)), TimeUnit.MILLISECONDS);
    }

    private synchronized boolean current(long ticket) { return !closed && ticket == generation; }
    public synchronized void cancel() {
        generation++;
        if (deadline != null) deadline.cancel(false);
        if (cancellation != null) cancellation.abort();
        if (future != null) future.cancel(true);
        worker.getQueue().clear();
        cancellation = null; future = null; deadline = null;
    }
    @Override public synchronized void close() {
        cancel(); closed = true; worker.shutdownNow(); deadlines.shutdownNow();
    }
}
