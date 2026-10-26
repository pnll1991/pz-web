package port.compat.java.util.concurrent;
/** Cooperative executor; no CPU parallelism or work-stealing is claimed. */
public class ForkJoinPool extends Executors.Pool {
    private static final ForkJoinPool COMMON=new ForkJoinPool(1);
    public ForkJoinPool(){this(1);} public ForkJoinPool(int parallelism){super(parallelism,Executors.defaultThreadFactory());}
    public static ForkJoinPool commonPool(){return COMMON;}
    public int getParallelism(){return limit;}
    public interface ManagedBlocker { boolean block()throws InterruptedException;boolean isReleasable(); }
    public static void managedBlock(ManagedBlocker blocker)throws InterruptedException{while(!blocker.isReleasable()&&!blocker.block())Thread.yield();}
    @Override public void shutdown(){if(this!=COMMON)super.shutdown();}
    @Override public java.util.List<Runnable> shutdownNow(){return this==COMMON?java.util.List.of():super.shutdownNow();}
}
