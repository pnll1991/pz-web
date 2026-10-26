package port;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import port.compat.java.util.concurrent.*;
import port.compat.java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.*;
import java.util.concurrent.TimeUnit;
class Task4ConcurrencyTest {
    @Test void synchronousQueueHandoffTimeoutAndInterruption()throws Exception{
        var queue=new SynchronousQueue<Integer>(true);assertFalse(queue.offer(1));assertNull(queue.poll(500_000,TimeUnit.NANOSECONDS));
        var finished=new java.util.concurrent.CountDownLatch(1);Thread producer=new Thread(()->{try{queue.put(19);finished.countDown();}catch(InterruptedException e){throw new AssertionError(e);}});producer.start();assertEquals(0,queue.size());assertFalse(finished.await(5,TimeUnit.MILLISECONDS));assertEquals(19,queue.take());assertTrue(finished.await(2,TimeUnit.SECONDS));producer.join();
        var interrupted=new java.util.concurrent.CountDownLatch(1);Thread waiting=new Thread(()->{try{queue.take();throw new AssertionError("No interruption");}catch(InterruptedException expected){interrupted.countDown();}});waiting.start();waiting.interrupt();assertTrue(interrupted.await(2,TimeUnit.SECONDS));waiting.join();assertFalse(queue.offer(3));
    }

    @Test void blockingQueueWakesProducerAndPreservesBound()throws Exception{
        var queue=new LinkedBlockingQueue<Integer>(1);queue.put(1);var done=new java.util.concurrent.CountDownLatch(1);
        Thread producer=new Thread(()->{try{queue.put(2);done.countDown();}catch(InterruptedException e){throw new AssertionError(e);}});producer.start();
        assertEquals(0,queue.remainingCapacity());assertFalse(queue.offer(3,1,TimeUnit.MILLISECONDS));assertEquals(1,queue.take());assertTrue(done.await(2,TimeUnit.SECONDS));assertEquals(2,queue.take());assertNull(queue.poll(1,TimeUnit.MILLISECONDS));producer.join();
    }
    @Test void futuresComposeRecoverAndDoNotRunCancelledDependents()throws Exception{
        var first=new CompletableFuture<Integer>();var composed=first.thenApply(n->n+1).thenCompose(n->CompletableFuture.completedFuture(n*2));assertTrue(first.complete(3));assertFalse(first.complete(9));assertEquals(8,composed.get());
        var failed=new CompletableFuture<Integer>();var recovered=failed.exceptionally(e->42);failed.completeExceptionally(new IllegalArgumentException("fixture"));assertEquals(42,recovered.join());assertThrows(CompletionException.class,failed::join);
        var second=new CompletableFuture<Integer>();var both=CompletableFuture.allOf(first,second);assertFalse(both.isDone());second.complete(8);assertNull(both.join());
        int[] calls={0};var source=new CompletableFuture<Integer>();var cancelled=source.thenRun(()->calls[0]++);cancelled.cancel(false);source.complete(1);assertEquals(0,calls[0]);
        var waiting=new CompletableFuture<Integer>();assertThrows(TimeoutException.class,()->waiting.get(1,TimeUnit.MILLISECONDS));
    }
    @Test void readWriteOwnershipDowngradeAndCondition()throws Exception{
        var lock=new ReentrantReadWriteLock(true);lock.writeLock().lock();lock.readLock().lock();lock.writeLock().unlock();assertEquals(1,lock.getReadLockCount());
        var acquired=new java.util.concurrent.CountDownLatch(1);Thread writer=new Thread(()->{lock.writeLock().lock();try{acquired.countDown();}finally{lock.writeLock().unlock();}});writer.start();assertFalse(acquired.await(5,TimeUnit.MILLISECONDS));lock.readLock().unlock();assertTrue(acquired.await(2,TimeUnit.SECONDS));writer.join();
        var condition=lock.writeLock().newCondition();boolean[] signalled={false};lock.writeLock().lock();lock.writeLock().lock();
        Thread other=new Thread(()->{lock.writeLock().lock();try{signalled[0]=true;condition.signal();}finally{lock.writeLock().unlock();}});other.start();
        while(!signalled[0])assertTrue(condition.await(2,TimeUnit.SECONDS));assertEquals(2,lock.getWriteHoldCount());lock.writeLock().unlock();lock.writeLock().unlock();other.join();assertThrows(IllegalMonitorStateException.class,()->lock.readLock().unlock());
    }
    @Test void scheduledCancellationFailureAndShutdown()throws Exception{
        var executor=Executors.newSingleThreadScheduledExecutor();
        try{
            List<Integer> order=Collections.synchronizedList(new ArrayList<>());var a=executor.schedule(()->order.add(1),1,TimeUnit.MILLISECONDS);var b=executor.schedule(()->order.add(2),10,TimeUnit.MILLISECONDS);a.get(2,TimeUnit.SECONDS);b.get(2,TimeUnit.SECONDS);assertEquals(List.of(1,2),order);
            var ticks=new java.util.concurrent.CountDownLatch(3);var periodic=executor.scheduleAtFixedRate(ticks::countDown,0,1,TimeUnit.MILLISECONDS);assertTrue(ticks.await(2,TimeUnit.SECONDS));periodic.cancel(false);assertTrue(periodic.isCancelled());
            var failed=executor.scheduleWithFixedDelay(()->{throw new IllegalStateException("fixture");},0,1,TimeUnit.MILLISECONDS);assertThrows(java.util.concurrent.ExecutionException.class,()->failed.get(2,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void semaphoreInterruptAndTimeout()throws Exception{
        var semaphore=new Semaphore(0,true);assertFalse(semaphore.tryAcquire(1,TimeUnit.MILLISECONDS));Thread.currentThread().interrupt();assertThrows(InterruptedException.class,semaphore::acquire);assertFalse(Thread.currentThread().isInterrupted());semaphore.release(2);semaphore.acquire(2);assertEquals(0,semaphore.availablePermits());
    }
}
