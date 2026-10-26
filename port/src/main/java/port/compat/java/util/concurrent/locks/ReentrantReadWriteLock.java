package port.compat.java.util.concurrent.locks;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Cooperative reentrant reader/writer ownership. Read to write upgrades wait, as in JDK. */
public class ReentrantReadWriteLock implements ReadWriteLock {
    private final ReentrantLock gate;
    private final Condition changed;
    private final Map<Thread,Integer> readers=new HashMap<>();
    private record Request(Thread thread,boolean writing) {}
    private final ArrayDeque<Request> waiters=new ArrayDeque<>();
    private Thread writer;
    private int writes, pendingWriters;
    private final ReadLock read=new ReadLock(this);
    private final WriteLock write=new WriteLock(this);
    public ReentrantReadWriteLock() { this(false); }
    public ReentrantReadWriteLock(boolean fair) { gate=new ReentrantLock(fair);changed=gate.newCondition(); }
    public ReadLock readLock() { return read; } public WriteLock writeLock() { return write; }
    public boolean isFair() { return gate.isFair(); }
    public int getReadHoldCount() { gate.lock();try{return readers.getOrDefault(Thread.currentThread(),0);}finally{gate.unlock();} }
    public int getReadLockCount() { gate.lock();try{return readers.values().stream().mapToInt(Integer::intValue).sum();}finally{gate.unlock();} }
    public int getWriteHoldCount() { gate.lock();try{return writer==Thread.currentThread()?writes:0;}finally{gate.unlock();} }
    public boolean isWriteLockedByCurrentThread() { return getWriteHoldCount()>0; }
    public boolean isWriteLocked() { gate.lock();try{return writer!=null;}finally{gate.unlock();} }
    private boolean allowed(boolean writing,boolean barge) {
        Thread current=Thread.currentThread(); if(writer==current)return true;
        if(writing)return writer==null&&readers.isEmpty();
        return writer==null&&(barge||pendingWriters==0||readers.containsKey(current));
    }
    private boolean acquire(boolean writing,long nanos,boolean timed,boolean interruptible,boolean barge) throws InterruptedException {
        long start=System.nanoTime(); boolean interrupted=false;
        if(barge){if(!gate.tryLock())return false;}else if(timed){if(!gate.tryLock(nanos,TimeUnit.NANOSECONDS))return false;}else if(interruptible)gate.lockInterruptibly();else gate.lock();
        try {
            if(writing)pendingWriters++;
            boolean reentrant=writer==Thread.currentThread()||!writing&&readers.containsKey(Thread.currentThread());
            Request ticket=new Request(Thread.currentThread(),writing);
            if(!reentrant&&!barge)waiters.add(ticket);
            try {
                while(!allowed(writing,barge||isFair()) || isFair()&&!barge&&!reentrant&&!turn(ticket)) {
                    if(timed) { long left=nanos-(System.nanoTime()-start);if(left<=0)return false;
                        try { changed.awaitNanos(left); } catch(InterruptedException e){if(interruptible)throw e;interrupted=true;}
                    } else if(interruptible)changed.await();else changed.awaitUninterruptibly();
                }
                if(writing){writer=Thread.currentThread();writes++;}else readers.merge(Thread.currentThread(),1,Integer::sum);
                return true;
            } finally {waiters.remove(ticket);if(writing)pendingWriters--;changed.signalAll();}
        } finally {gate.unlock();if(interrupted)Thread.currentThread().interrupt();}
    }
    private boolean turn(Request ticket){for(Request earlier:waiters){if(earlier==ticket)return true;if(ticket.writing||earlier.writing)return false;}return true;}
    public static class ReadLock implements Lock {
        final ReentrantReadWriteLock rw; final boolean writing;
        protected ReadLock(ReentrantReadWriteLock rw){this(rw,false);} ReadLock(ReentrantReadWriteLock rw,boolean writing){this.rw=rw;this.writing=writing;}
        public void lock(){try{rw.acquire(writing,0,false,false,false);}catch(InterruptedException e){throw new AssertionError(e);}}
        public void lockInterruptibly()throws InterruptedException{rw.acquire(writing,0,false,true,false);}
        public boolean tryLock(){try{return rw.acquire(writing,0,true,false,true);}catch(InterruptedException e){throw new AssertionError(e);}}
        public boolean tryLock(long time,TimeUnit unit)throws InterruptedException{return rw.acquire(writing,unit.toNanos(time),true,true,false);}
        public void unlock(){rw.gate.lock();try{
            Thread current=Thread.currentThread();
            if(writing){if(rw.writer!=current)throw new IllegalMonitorStateException();if(--rw.writes==0)rw.writer=null;}
            else {int n=rw.readers.getOrDefault(current,0);if(n==0)throw new IllegalMonitorStateException();if(n==1)rw.readers.remove(current);else rw.readers.put(current,n-1);}
            rw.changed.signalAll();
        }finally{rw.gate.unlock();}}
        public Condition newCondition(){throw new UnsupportedOperationException("ReadLock.newCondition");}
    }
    public static class WriteLock extends ReadLock {
        protected WriteLock(ReentrantReadWriteLock rw){super(rw,true);}
        public boolean isHeldByCurrentThread(){return rw.isWriteLockedByCurrentThread();}
        public int getHoldCount(){return rw.getWriteHoldCount();}
        public Condition newCondition(){return rw.new WriteCondition();}
    }
    private final class WriteCondition implements Condition {
        private final Condition condition=gate.newCondition();
        private boolean pause(long nanos,boolean timed,boolean interruptible)throws InterruptedException {
            gate.lock();int depth=0,reads=0;boolean signalled=false;InterruptedException failure=null;
            try {
                if(writer!=Thread.currentThread())throw new IllegalMonitorStateException();
                if(interruptible&&Thread.interrupted())throw new InterruptedException();
                depth=writes;reads=readers.getOrDefault(writer,0);readers.remove(writer);writer=null;writes=0;changed.signalAll();
                try {if(!interruptible){condition.awaitUninterruptibly();signalled=true;}else if(timed)signalled=condition.await(nanos,TimeUnit.NANOSECONDS);else{condition.await();signalled=true;}}
                catch(InterruptedException e){failure=e;}
                acquire(true,0,false,false,false);
                writer=Thread.currentThread();writes=depth;if(reads>0)readers.put(writer,reads);
                if(failure!=null)throw failure;return signalled;
            } finally {gate.unlock();}
        }
        public void await()throws InterruptedException{pause(0,false,true);}
        public void awaitUninterruptibly(){try{pause(0,false,false);}catch(InterruptedException e){throw new AssertionError(e);}}
        public long awaitNanos(long nanos)throws InterruptedException{long start=System.nanoTime();pause(nanos,true,true);return nanos-(System.nanoTime()-start);}
        public boolean await(long time,TimeUnit unit)throws InterruptedException{return pause(unit.toNanos(time),true,true);}
        public boolean awaitUntil(Date date)throws InterruptedException{return await(date.getTime()-System.currentTimeMillis(),TimeUnit.MILLISECONDS);}
        public void signal(){gate.lock();try{if(writer!=Thread.currentThread())throw new IllegalMonitorStateException();condition.signal();}finally{gate.unlock();}}
        public void signalAll(){gate.lock();try{if(writer!=Thread.currentThread())throw new IllegalMonitorStateException();condition.signalAll();}finally{gate.unlock();}}
    }
}
