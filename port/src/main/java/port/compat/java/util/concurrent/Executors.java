package port.compat.java.util.concurrent;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Bounded cooperative workers using TeaVM Threads; no busy waiting. */
public final class Executors {
    private static int poolId;
    public static ThreadFactory defaultThreadFactory() {
        int id = ++poolId;
        return new ThreadFactory() {
            int threadId;
            public Thread newThread(Runnable runnable) { return new Thread(runnable, "browser-pool-" + id + "-" + ++threadId); }
        };
    }
    public static ExecutorService newFixedThreadPool(int count) { return newFixedThreadPool(count, defaultThreadFactory()); }
    public static ExecutorService newFixedThreadPool(int count, ThreadFactory factory) { return new Pool(count, factory); }
    public static ExecutorService newSingleThreadExecutor() { return newFixedThreadPool(1); }
    public static ExecutorService newSingleThreadExecutor(ThreadFactory factory) { return newFixedThreadPool(1, factory); }
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() { return newSingleThreadScheduledExecutor(defaultThreadFactory()); }
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory factory) { return new ScheduledPool(factory); }
    public static <T> Callable<T> callable(Runnable task, T value) { Objects.requireNonNull(task); return () -> { task.run(); return value; }; }
    public static Callable<Object> callable(Runnable task) { return callable(task, null); }

    static class Pool implements ExecutorService {
        final int limit;
        ThreadFactory factory;
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        final Set<Thread> workers = new HashSet<>();
        boolean stopped, immediate;
        Pool(int limit, ThreadFactory factory) {
            if (limit <= 0) throw new IllegalArgumentException("Nonpositive worker count");
            this.limit = limit; this.factory = Objects.requireNonNull(factory);
        }
        public synchronized void execute(Runnable task) {
            Objects.requireNonNull(task);
            if (stopped) throw new RejectedExecutionException("Executor is shut down");
            if (workers.size() < limit) {
                Thread worker = factory.newThread(this::work);
                if (worker == null) throw new RejectedExecutionException("ThreadFactory returned null");
                workers.add(worker);
                try { worker.start(); } catch (Throwable error) { workers.remove(worker); throw error; }
            }
            queue.add(task); notifyAll();
        }
        private void work() {
            try {
                while (true) {
                    Runnable task;
                    synchronized (this) {
                        while (queue.isEmpty() && !stopped) try { wait(); }
                        catch (InterruptedException error) { if (immediate) return; }
                        if (immediate || queue.isEmpty() && stopped) return;
                        task = queue.removeFirst();
                    }
                    // A cancelled previous task must not poison the next worker task.
                    Thread.interrupted();
                    try { task.run(); }
                    catch (Throwable error) {
                        var handler = Thread.currentThread().getUncaughtExceptionHandler();
                        if (handler != null) handler.uncaughtException(Thread.currentThread(), error);
                        else error.printStackTrace();
                    }
                }
            } finally { synchronized (this) { workers.remove(Thread.currentThread()); notifyAll(); } }
        }
        public synchronized void shutdown() { stopped = true; notifyAll(); }
        public synchronized List<Runnable> shutdownNow() {
            stopped = immediate = true;
            var pending = new ArrayList<>(queue); queue.clear();
            for (Thread worker : workers) worker.interrupt();
            notifyAll(); return pending;
        }
        public synchronized boolean isShutdown() { return stopped; }
        public synchronized boolean isTerminated() { return stopped && workers.isEmpty(); }
        public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(timeout), start = System.nanoTime();
            while (!isTerminated()) {
                long remaining = nanos - (System.nanoTime() - start);
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            return true;
        }
        public <T> Future<T> submit(Callable<T> task) { var future = new FutureTask<>(task); execute(future); return future; }
        public <T> Future<T> submit(Runnable task, T result) { return submit(callable(task, result)); }
        public Future<?> submit(Runnable task) { return submit(task, null); }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            return all(tasks, 0, false);
        }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
            return all(tasks, unit.toNanos(timeout), true);
        }
        private <T> List<Future<T>> all(Collection<? extends Callable<T>> tasks, long nanos, boolean timed) throws InterruptedException {
            var futures = new ArrayList<Future<T>>(); long start = System.nanoTime();
            try {
                for (var task : tasks) futures.add(submit(task));
                for (var future : futures) try {
                    if (timed) future.get(nanos - (System.nanoTime() - start), TimeUnit.NANOSECONDS); else future.get();
                } catch (ExecutionException | CancellationException ignored) { }
                catch (TimeoutException elapsed) { break; }
                return futures;
            } finally { for (var future : futures) if (!future.isDone()) future.cancel(true); }
        }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
            try { return any(tasks, 0, false); } catch (TimeoutException impossible) { throw new AssertionError(impossible); }
        }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return any(tasks, unit.toNanos(timeout), true);
        }
        private <T> T any(Collection<? extends Callable<T>> tasks, long nanos, boolean timed) throws InterruptedException, ExecutionException, TimeoutException {
            if (tasks.isEmpty()) throw new IllegalArgumentException("No tasks");
            Object monitor = new Object();
            var completed = new ArrayDeque<Future<T>>();
            var futures = new ArrayList<Future<T>>();
            long start = System.nanoTime(); ExecutionException last = null;
            try {
                for (var task : tasks) {
                    var future = new FutureTask<T>(task) {
                        protected void done() { synchronized (monitor) { completed.add(this); monitor.notifyAll(); } }
                    };
                    futures.add(future); execute(future);
                }
                for (int count = 0; count < futures.size(); count++) {
                    Future<T> future;
                    synchronized (monitor) {
                        while (completed.isEmpty()) {
                            if (!timed) monitor.wait();
                            else {
                                long remaining = nanos - (System.nanoTime() - start);
                                if (remaining <= 0) throw new TimeoutException();
                                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                            }
                        }
                        future = completed.removeFirst();
                    }
                    try { return future.get(); } catch (ExecutionException error) { last = error; }
                }
                throw last;
            } finally { for (var future : futures) future.cancel(true); }
        }
    }
}
