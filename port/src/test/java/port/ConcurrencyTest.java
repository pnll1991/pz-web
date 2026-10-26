package port;

import org.junit.jupiter.api.Test;
import port.compat.java.util.concurrent.*;
import port.compat.java.util.concurrent.locks.ReentrantLock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {
    @Test void snapshotIteratorAndIdentityRemoval() {
        var queue = new ConcurrentLinkedQueue<String>();
        queue.addAll(List.of("a", "b", "a"));
        var iterator = queue.iterator();
        assertEquals("a", iterator.next()); queue.poll(); queue.add("c");
        iterator.remove(); // Must not remove the second equal node.
        assertEquals(List.of("b", "a", "c"), new ArrayList<>(queue));
        assertEquals("b", iterator.next());
        assertThrows(NullPointerException.class, () -> queue.add(null));
        var set = new CopyOnWriteArraySet<Integer>(List.of(1, 2, 1));
        var snapshot = set.iterator(); set.add(3); set.remove(1);
        assertEquals(List.of(1, 2), toList(snapshot));
        assertThrows(UnsupportedOperationException.class, snapshot::remove);
    }
    private static <T> List<T> toList(Iterator<T> iterator) { var result = new ArrayList<T>(); iterator.forEachRemaining(result::add); return result; }
    @Test void futuresReportTimeoutFailureAndCancellation() throws Exception {
        var future = new FutureTask<Integer>(() -> 42);
        assertThrows(TimeoutException.class, () -> future.get(1, TimeUnit.MILLISECONDS));
        new Thread(future).start(); assertEquals(42, future.get(2, TimeUnit.SECONDS));
        var failure = new FutureTask<Integer>(() -> { throw new IllegalStateException("fixture"); });
        failure.run(); assertEquals("fixture", assertThrows(ExecutionException.class, failure::get).getCause().getMessage());
        var cancelled = new FutureTask<Integer>(() -> { fail("cancelled task ran"); return 0; });
        assertTrue(cancelled.cancel(true)); cancelled.run();
        assertThrows(java.util.concurrent.CancellationException.class, cancelled::get);
    }
    @Test void concurrentRemovalDoesNotClaimAnotherThreadsNode() throws Exception {
        var queue = new ConcurrentLinkedQueue<String>(); queue.add("one");
        var comparing = new java.util.concurrent.CountDownLatch(1); var resume = new java.util.concurrent.CountDownLatch(1);
        boolean[] removed = {true};
        Object query = new Object() {
            public boolean equals(Object value) {
                comparing.countDown();
                try { if (!resume.await(2,TimeUnit.SECONDS)) throw new AssertionError("comparison not released"); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                return "one".equals(value);
            }
        };
        Thread remover = new Thread(()->removed[0]=queue.remove(query)); remover.start();
        try { assertTrue(comparing.await(2,TimeUnit.SECONDS)); assertEquals("one",queue.poll()); }
        finally { resume.countDown(); }
        remover.join(2000); assertFalse(remover.isAlive()); assertFalse(removed[0]);
    }
    @Test void executorPreservesOrderAndTerminates() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        var order = new ArrayList<Integer>();
        for (int i = 0; i < 20; i++) { int id = i; pool.submit(() -> order.add(id)); }
        assertEquals(21, pool.submit(() -> 21).get(2, TimeUnit.SECONDS));
        pool.shutdown(); assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(java.util.stream.IntStream.range(0,20).boxed().toList(), order);
        assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> {}));
    }
    @Test void invokeAnyWaitsForSuccessfulCompletion() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            int result = pool.<Integer>invokeAny(List.of(() -> { Thread.sleep(200); return 99; }, () -> 7), 2, TimeUnit.SECONDS);
            assertEquals(7, result);
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS)); }
    }
    @Test void conditionRestoresReentrancyAndChecksOwnership() throws Exception {
        var lock = new ReentrantLock(true); var condition = lock.newCondition();
        boolean[] ready = {false};
        lock.lock(); lock.lock();
        Thread thread = new Thread(() -> { lock.lock(); try { ready[0] = true; condition.signal(); } finally { lock.unlock(); } });
        thread.start();
        while (!ready[0]) assertTrue(condition.await(2, TimeUnit.SECONDS));
        assertEquals(2, lock.getHoldCount()); lock.unlock(); lock.unlock(); thread.join(2000);
        assertFalse(thread.isAlive()); assertFalse(lock.isLocked());
        assertThrows(IllegalMonitorStateException.class, condition::signal);
        assertThrows(IllegalMonitorStateException.class, lock::unlock);
    }
    @Test void uninterruptibleFairLockKeepsItsQueuePosition() throws Exception {
        var lock = new ReentrantLock(true); var order = new ArrayList<Integer>(); boolean[] restored = {false};
        lock.lock();
        Thread first = new Thread(() -> { lock.lock(); try { order.add(1); restored[0] = Thread.currentThread().isInterrupted(); } finally { lock.unlock(); } });
        Thread second = new Thread(() -> { lock.lock(); try { order.add(2); } finally { lock.unlock(); } });
        try {
            first.start(); waitForQueue(lock,1); second.start(); waitForQueue(lock,2); first.interrupt();
        } finally { lock.unlock(); }
        first.join(2000); second.join(2000);
        assertFalse(first.isAlive()); assertFalse(second.isAlive()); assertTrue(restored[0]); assertEquals(List.of(1,2),order);
    }
    private static void waitForQueue(ReentrantLock lock, int size) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (lock.getQueueLength() < size && System.nanoTime() < until) Thread.sleep(1);
        assertEquals(size,lock.getQueueLength());
    }
}
