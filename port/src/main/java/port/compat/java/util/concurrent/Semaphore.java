package port.compat.java.util.concurrent;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
public class Semaphore {
    private int permits;
    private final boolean fair;
    private final ArrayDeque<Object> waiters=new ArrayDeque<>();
    public Semaphore(int permits) { this(permits,false); }
    public Semaphore(int permits,boolean fair) { this.permits=permits;this.fair=fair; }
    private static void check(int n) { if(n<0)throw new IllegalArgumentException(); }
    public synchronized int availablePermits() { return permits; }
    public boolean isFair() { return fair; }
    public synchronized int drainPermits() { int n=permits; permits=0;return n; }
    public synchronized void release(int n) { check(n); if((long)permits+n>Integer.MAX_VALUE)throw new Error("Semaphore permit overflow"); permits+=n;notifyAll(); }
    public void release() { release(1); }
    public synchronized boolean tryAcquire(int n) { check(n);if(permits<n)return false;permits-=n;return true; }
    public boolean tryAcquire() { return tryAcquire(1); }
    public void acquire() throws InterruptedException { acquire(1); }
    public void acquire(int n) throws InterruptedException { waitFor(n,0,false,true); }
    public void acquireUninterruptibly() { acquireUninterruptibly(1); }
    public void acquireUninterruptibly(int n) { try { waitFor(n,0,false,false); } catch(InterruptedException impossible) { throw new AssertionError(impossible); } }
    public boolean tryAcquire(long timeout,TimeUnit unit) throws InterruptedException { return tryAcquire(1,timeout,unit); }
    public boolean tryAcquire(int n,long timeout,TimeUnit unit) throws InterruptedException { return waitFor(n,unit.toNanos(timeout),true,true); }
    private synchronized boolean waitFor(int n,long nanos,boolean timed,boolean interruptible) throws InterruptedException {
        check(n); if(interruptible&&Thread.interrupted())throw new InterruptedException();
        Object ticket=new Object(); waiters.add(ticket); long start=System.nanoTime(); boolean interrupted=false;
        try {
            while(permits<n || fair&&waiters.peek()!=ticket) {
                try { if(!timed)wait(); else { long left=nanos-(System.nanoTime()-start);if(left<=0)return false;TimeUnit.NANOSECONDS.timedWait(this,left); } }
                catch(InterruptedException e) { if(interruptible)throw e;interrupted=true; }
            }
            permits-=n;return true;
        } finally { waiters.remove(ticket);notifyAll();if(interrupted)Thread.currentThread().interrupt(); }
    }
}
