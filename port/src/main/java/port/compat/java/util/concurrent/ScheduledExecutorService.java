package port.compat.java.util.concurrent;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
public interface ScheduledExecutorService extends ExecutorService {
    ScheduledFuture<?> schedule(Runnable task,long delay,TimeUnit unit);
    <V> ScheduledFuture<V> schedule(Callable<V> task,long delay,TimeUnit unit);
    ScheduledFuture<?> scheduleAtFixedRate(Runnable task,long delay,long period,TimeUnit unit);
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable task,long delay,long period,TimeUnit unit);
}
