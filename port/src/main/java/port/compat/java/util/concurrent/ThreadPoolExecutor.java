package port.compat.java.util.concurrent;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Queue and worker bounds follow ThreadPoolExecutor, on cooperative TeaVM threads. */
public class ThreadPoolExecutor extends Executors.Pool {
    private final int core,maximum;
    private final long keepAlive;
    private final BlockingQueue<Runnable> tasks;
    private final RejectedExecutionHandler rejection;
    private final Set<Thread> idle=new HashSet<>();
    public ThreadPoolExecutor(int core,int maximum,long keepAlive,TimeUnit unit,BlockingQueue<Runnable> queue,ThreadFactory factory){this(core,maximum,keepAlive,unit,queue,factory,new AbortPolicy());}
    public ThreadPoolExecutor(int core,int maximum,long keepAlive,TimeUnit unit,BlockingQueue<Runnable> queue,ThreadFactory factory,RejectedExecutionHandler rejection){
        super(maximum,factory);if(core<0||core>maximum||keepAlive<0)throw new IllegalArgumentException("Pool bounds");
        this.core=core;this.maximum=maximum;this.keepAlive=Objects.requireNonNull(unit).toNanos(keepAlive);tasks=Objects.requireNonNull(queue);this.rejection=Objects.requireNonNull(rejection);
    }
    @Override public void execute(Runnable task){Objects.requireNonNull(task);boolean rejected=false;synchronized(this){if(stopped)rejected=true;else if(workers.size()<core)startWorker(task);else if(tasks.offer(task)){if(workers.isEmpty())startWorker(null);}else if(workers.size()<maximum)startWorker(task);else rejected=true;}if(rejected)rejection.rejectedExecution(task,this);}
    private void startWorker(Runnable first){Thread worker=factory.newThread(()->work(first));if(worker==null)throw new RejectedExecutionException("ThreadFactory returned null");workers.add(worker);try{worker.start();}catch(Throwable error){workers.remove(worker);throw error;}}
    private void work(Runnable first){try{Runnable task=first;while(true){if(task==null){boolean timed;synchronized(this){if(immediate||stopped&&tasks.isEmpty())return;timed=workers.size()>core;idle.add(Thread.currentThread());}
                    try{task=timed?tasks.poll(keepAlive,TimeUnit.NANOSECONDS):tasks.take();}catch(InterruptedException interrupted){continue;}finally{synchronized(this){idle.remove(Thread.currentThread());}}
                    if(task==null){synchronized(this){if(workers.size()>core)return;}continue;}}
                Thread.interrupted();try{task.run();}catch(Throwable error){var handler=Thread.currentThread().getUncaughtExceptionHandler();if(handler!=null)handler.uncaughtException(Thread.currentThread(),error);else error.printStackTrace();}task=null;
            }}finally{synchronized(this){workers.remove(Thread.currentThread());idle.remove(Thread.currentThread());if(!immediate&&!tasks.isEmpty()&&workers.isEmpty())startWorker(null);notifyAll();}}}
    @Override public synchronized void shutdown(){stopped=true;for(var thread:new ArrayList<>(idle))thread.interrupt();notifyAll();}
    @Override public synchronized List<Runnable> shutdownNow(){stopped=immediate=true;var pending=new ArrayList<Runnable>();tasks.drainTo(pending);for(var thread:new ArrayList<>(workers))thread.interrupt();notifyAll();return pending;}
    public BlockingQueue<Runnable> getQueue(){return tasks;}
    public synchronized ThreadFactory getThreadFactory(){return factory;}
    public synchronized void setThreadFactory(ThreadFactory value){factory=Objects.requireNonNull(value);}
    public static class AbortPolicy implements RejectedExecutionHandler {public void rejectedExecution(Runnable task,ThreadPoolExecutor executor){throw new RejectedExecutionException("ThreadPoolExecutor queue or pool is closed/full");}}
    public static class CallerRunsPolicy implements RejectedExecutionHandler {public void rejectedExecution(Runnable task,ThreadPoolExecutor executor){if(!executor.isShutdown())task.run();}}
    public static class DiscardPolicy implements RejectedExecutionHandler {public void rejectedExecution(Runnable task,ThreadPoolExecutor executor){}}
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {public void rejectedExecution(Runnable task,ThreadPoolExecutor executor){if(!executor.isShutdown()){executor.getQueue().poll();executor.execute(task);}}}
}
