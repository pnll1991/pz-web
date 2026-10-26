package port.compat.java.util.concurrent.locks;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Reentrant ownership and condition waits for a cooperative Java scheduler. */
public class ReentrantLock implements Lock {
    private Thread owner;
    private int holds;
    private final boolean fair;
    private final ArrayDeque<Thread> waiters = new ArrayDeque<>();
    public ReentrantLock() { this(false); }
    public ReentrantLock(boolean fair) { this.fair = fair; }
    public boolean isFair() { return fair; }
    public synchronized boolean isLocked() { return owner != null; }
    public synchronized boolean isHeldByCurrentThread() { return owner == Thread.currentThread(); }
    public synchronized int getHoldCount() { return isHeldByCurrentThread() ? holds : 0; }
    public synchronized boolean hasQueuedThreads() { return !waiters.isEmpty(); }
    public synchronized int getQueueLength() { return waiters.size(); }
    public void lock() {
        try { acquire(0, false, false); } catch (InterruptedException impossible) { throw new AssertionError(impossible); }
    }
    public void lockInterruptibly() throws InterruptedException { acquire(0, false, true); }
    public synchronized boolean tryLock() {
        Thread thread = Thread.currentThread();
        if (owner != null && owner != thread) return false;
        owner = thread; holds++; return true;
    }
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException { return acquire(unit.toNanos(time), true, true); }
    private synchronized boolean acquire(long nanos, boolean timed, boolean interruptible) throws InterruptedException {
        if (interruptible && Thread.interrupted()) throw new InterruptedException();
        Thread thread = Thread.currentThread();
        if (owner == thread) { holds++; return true; }
        waiters.add(thread); long start = System.nanoTime(); boolean interrupted = false;
        try {
            while (owner != null || fair && waiters.peekFirst() != thread) {
                try { if (timed) {
                    long left = nanos - (System.nanoTime() - start);
                    if (left <= 0) return false;
                    TimeUnit.NANOSECONDS.timedWait(this, left);
                } else wait(); }
                catch (InterruptedException error) { if (interruptible) throw error; interrupted = true; }
            }
            owner = thread; holds = 1; return true;
        } finally { waiters.remove(thread); notifyAll(); if (interrupted) thread.interrupt(); }
    }
    private void checkOwner() { if (owner != Thread.currentThread()) throw new IllegalMonitorStateException(); }
    public synchronized void unlock() { checkOwner(); if (--holds == 0) { owner = null; notifyAll(); } }
    public Condition newCondition() { return new WaitCondition(); }
    private static final class Ticket { boolean signalled; }
    private final class WaitCondition implements Condition {
        final ArrayDeque<Ticket> tickets = new ArrayDeque<>();
        public void await() throws InterruptedException { pause(0, false, true); }
        public void awaitUninterruptibly() {
            try { pause(0, false, false); } catch (InterruptedException impossible) { throw new AssertionError(impossible); }
        }
        public long awaitNanos(long nanos) throws InterruptedException {
            long start = System.nanoTime(); pause(nanos, true, true); return nanos - (System.nanoTime() - start);
        }
        public boolean await(long time, TimeUnit unit) throws InterruptedException { return pause(unit.toNanos(time), true, true); }
        public boolean awaitUntil(Date deadline) throws InterruptedException { return await(deadline.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS); }
        private boolean pause(long nanos, boolean timed, boolean interruptible) throws InterruptedException {
            boolean interrupted = false; int depth; var ticket = new Ticket();
            synchronized (ReentrantLock.this) {
                checkOwner();
                if (interruptible && Thread.interrupted()) throw new InterruptedException();
                depth = holds; tickets.add(ticket); holds = 0; owner = null; ReentrantLock.this.notifyAll();
                long start = System.nanoTime();
                try {
                    while (!ticket.signalled) try {
                        if (!timed) ReentrantLock.this.wait();
                        else {
                            long left = nanos - (System.nanoTime() - start);
                            if (left <= 0) break;
                            TimeUnit.NANOSECONDS.timedWait(ReentrantLock.this, left);
                        }
                    } catch (InterruptedException error) { interrupted = true; if (interruptible) break; }
                } finally {
                    tickets.remove(ticket);
                    // If interruption wins after a signal, redirect the consumed signal.
                    if (interruptible && interrupted && ticket.signalled) {
                        Ticket next = tickets.pollFirst(); if (next != null) next.signalled = true;
                        ReentrantLock.this.notifyAll();
                    }
                }
            }
            lock();
            synchronized (ReentrantLock.this) { holds = depth; }
            if (interrupted) {
                if (interruptible) throw new InterruptedException();
                Thread.currentThread().interrupt();
            }
            return ticket.signalled;
        }
        public void signal() { synchronized (ReentrantLock.this) {
            checkOwner(); Ticket ticket = tickets.pollFirst();
            if (ticket != null) ticket.signalled = true;
            ReentrantLock.this.notifyAll();
        } }
        public void signalAll() { synchronized (ReentrantLock.this) {
            checkOwner(); for (var ticket : tickets) ticket.signalled = true;
            tickets.clear(); ReentrantLock.this.notifyAll();
        } }
    }
}
