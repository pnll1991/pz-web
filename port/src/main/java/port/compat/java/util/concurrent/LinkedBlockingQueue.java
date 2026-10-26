package port.compat.java.util.concurrent;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded FIFO; waits yield on TeaVM's coroutine monitors. */
public class LinkedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {
    private final int capacity;
    private final ArrayDeque<E> items = new ArrayDeque<>();
    public LinkedBlockingQueue() { this(Integer.MAX_VALUE); }
    public LinkedBlockingQueue(int capacity) { if(capacity<=0) throw new IllegalArgumentException(); this.capacity=capacity; }
    public LinkedBlockingQueue(Collection<? extends E> source) { this(); addAll(source); }
    public synchronized int size() { return items.size(); }
    public synchronized int remainingCapacity() { return capacity-items.size(); }
    public synchronized boolean offer(E item) { Objects.requireNonNull(item); if(items.size()==capacity)return false; items.addLast(item); notifyAll(); return true; }
    public synchronized E poll() { E value=items.pollFirst(); if(value!=null)notifyAll(); return value; }
    public synchronized E peek() { return items.peekFirst(); }
    public synchronized void put(E item) throws InterruptedException { Objects.requireNonNull(item); if(Thread.interrupted())throw new InterruptedException(); while(items.size()==capacity)wait(); offer(item); }
    public synchronized E take() throws InterruptedException { if(Thread.interrupted())throw new InterruptedException(); while(items.isEmpty())wait(); return poll(); }
    public synchronized boolean offer(E item,long timeout,TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(item); long nanos=unit.toNanos(timeout),start=System.nanoTime();
        if(Thread.interrupted())throw new InterruptedException();
        while(items.size()==capacity) { long left=nanos-(System.nanoTime()-start); if(left<=0)return false; TimeUnit.NANOSECONDS.timedWait(this,left); }
        return offer(item);
    }
    public synchronized E poll(long timeout,TimeUnit unit) throws InterruptedException {
        long nanos=unit.toNanos(timeout),start=System.nanoTime(); if(Thread.interrupted())throw new InterruptedException();
        while(items.isEmpty()) { long left=nanos-(System.nanoTime()-start); if(left<=0)return null; TimeUnit.NANOSECONDS.timedWait(this,left); }
        return poll();
    }
    public synchronized int drainTo(Collection<? super E> target) { return drainTo(target,Integer.MAX_VALUE); }
    public synchronized int drainTo(Collection<? super E> target,int maximum) {
        Objects.requireNonNull(target); if(target==this)throw new IllegalArgumentException(); int n=0;
        try { while(n<maximum&&!items.isEmpty()) { target.add(items.peekFirst()); items.removeFirst(); n++; } return n; }
        finally { if(n>0)notifyAll(); }
    }
    public synchronized boolean remove(Object value) { boolean changed=items.remove(value); if(changed)notifyAll(); return changed; }
    public synchronized boolean contains(Object value) { return items.contains(value); }
    public synchronized void clear() { items.clear(); notifyAll(); }
    public synchronized Iterator<E> iterator() { return Collections.unmodifiableList(new ArrayList<>(items)).iterator(); }
}
