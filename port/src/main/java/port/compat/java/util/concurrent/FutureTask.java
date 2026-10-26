package port.compat.java.util.concurrent;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Monitor waits suspend TeaVM coroutines rather than spinning the browser. */
public class FutureTask<V> implements RunnableFuture<V> {
    private final Callable<V> callable;
    private boolean complete, cancelled, started;
    private V result;
    private Throwable failure;
    private Thread runner;
    public FutureTask(Callable<V> callable) { this.callable = Objects.requireNonNull(callable); }
    public FutureTask(Runnable runnable, V result) {
        Objects.requireNonNull(runnable);
        callable = () -> { runnable.run(); return result; };
    }
    public void run() {
        synchronized (this) {
            if (complete || started) return;
            started = true; runner = Thread.currentThread();
        }
        try { set(callable.call()); } catch (Throwable error) { setException(error); }
        finally { synchronized (this) { runner = null; } }
    }
    protected void set(V value) {
        synchronized (this) { if (complete) return; result = value; complete = true; notifyAll(); }
        done();
    }
    protected void setException(Throwable error) {
        synchronized (this) { if (complete) return; failure = error; complete = true; notifyAll(); }
        done();
    }
    protected void done() { }
    public boolean cancel(boolean interrupt) {
        synchronized (this) {
            if (complete) return false;
            cancelled = complete = true;
            if (interrupt && runner != null) runner.interrupt();
            notifyAll();
        }
        done(); return true;
    }
    public synchronized boolean isDone() { return complete; }
    public synchronized boolean isCancelled() { return cancelled; }
    private V result() throws ExecutionException {
        if (cancelled) throw new CancellationException();
        if (failure != null) throw new ExecutionException(failure);
        return result;
    }
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (!complete) wait();
        return result();
    }
    public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long remaining = unit.toNanos(timeout), start = System.nanoTime();
        while (!complete) {
            if (remaining <= 0) throw new TimeoutException();
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
            remaining = unit.toNanos(timeout) - (System.nanoTime() - start);
        }
        return result();
    }
}
