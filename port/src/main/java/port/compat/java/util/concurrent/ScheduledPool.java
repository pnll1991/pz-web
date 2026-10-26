package port.compat.java.util.concurrent;
import java.util.*;
import java.util.concurrent.*;

/** One timer/worker preserves single-thread scheduled executor ordering and non-overlap. */
final class ScheduledPool extends Executors.Pool implements ScheduledExecutorService {
    private final PriorityQueue<Task<?>> delayed=new PriorityQueue<>();
    private Thread timer;
    private boolean shutdown,stopNow;
    private long sequence;
    ScheduledPool(ThreadFactory factory){super(1,factory);}
    public ScheduledFuture<?> schedule(Runnable work,long delay,TimeUnit unit){return schedule(Executors.callable(work),delay,unit);}
    public <V> ScheduledFuture<V> schedule(Callable<V> work,long delay,TimeUnit unit){return enqueue(work,Math.max(0,unit.toNanos(delay)),0);}
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable work,long delay,long period,TimeUnit unit){if(period<=0)throw new IllegalArgumentException();return enqueue(Executors.callable(work),Math.max(0,unit.toNanos(delay)),unit.toNanos(period));}
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable work,long delay,long period,TimeUnit unit){if(period<=0)throw new IllegalArgumentException();return enqueue(Executors.callable(work),Math.max(0,unit.toNanos(delay)),-unit.toNanos(period));}
    private synchronized <V> Task<V> enqueue(Callable<V> work,long delay,long period){
        if(shutdown)throw new RejectedExecutionException("Scheduled executor shut down");
        var task=new Task<V>(Objects.requireNonNull(work),System.nanoTime()+Math.min(delay,Long.MAX_VALUE/2),period,sequence++);
        if(timer==null){Thread candidate=factory.newThread(this::loop);if(candidate==null)throw new RejectedExecutionException("ThreadFactory returned null");timer=candidate;try{candidate.start();}catch(Throwable e){timer=null;throw e;}}
        delayed.add(task);notifyAll();return task;
    }
    @Override public void execute(Runnable work){schedule(work,0,TimeUnit.NANOSECONDS);}
    private void loop(){try{while(true){Task<?> task;synchronized(this){while(true){if(stopNow||shutdown&&delayed.isEmpty())return;if(delayed.isEmpty()){try{wait();}catch(InterruptedException e){}continue;}task=delayed.peek();if(task.isCancelled()){delayed.remove();continue;}long left=task.deadline-System.nanoTime();if(left>0){try{TimeUnit.NANOSECONDS.timedWait(this,left);}catch(InterruptedException e){}continue;}delayed.remove();break;}}Thread.interrupted();task.run();}}finally{synchronized(this){timer=null;notifyAll();}}}
    @Override public synchronized void shutdown(){shutdown=true;for(var task:new ArrayList<>(delayed))if(task.period!=0)task.cancel(false);notifyAll();}
    @Override public synchronized List<Runnable> shutdownNow(){shutdown=true;stopNow=true;var pending=new ArrayList<Runnable>(delayed);for(var task:new ArrayList<>(delayed))task.cancel(false);delayed.clear();if(timer!=null)timer.interrupt();notifyAll();return pending;}
    @Override public synchronized boolean isShutdown(){return shutdown;}
    @Override public synchronized boolean isTerminated(){return shutdown&&timer==null;}
    private final class Task<V> extends FutureTask<V> implements ScheduledFuture<V> {
        long deadline;final long period,order;
        Task(Callable<V> work,long deadline,long period,long order){super(work);this.deadline=deadline;this.period=period;this.order=order;}
        public long getDelay(TimeUnit unit){return unit.convert(deadline-System.nanoTime(),TimeUnit.NANOSECONDS);}
        public int compareTo(Delayed other){if(other==this)return 0;if(other instanceof ScheduledPool.Task<?> task){long diff=deadline-task.deadline;return diff==0?Long.compare(order,task.order):diff<0?-1:1;}return Long.compare(getDelay(TimeUnit.NANOSECONDS),other.getDelay(TimeUnit.NANOSECONDS));}
        @Override public void run(){if(period==0){super.run();return;}if(runAndReset()){synchronized(ScheduledPool.this){if(shutdown){cancel(false);return;}deadline=period>0?deadline+period:System.nanoTime()-period;delayed.add(this);ScheduledPool.this.notifyAll();}}}
        @Override public boolean cancel(boolean interrupt){boolean changed=super.cancel(interrupt);synchronized(ScheduledPool.this){delayed.remove(this);ScheduledPool.this.notifyAll();}return changed;}
    }
}
