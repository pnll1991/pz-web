package port.compat.java.util.concurrent;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
public interface BlockingQueue<E> extends Queue<E> {
    void put(E e) throws InterruptedException;
    boolean offer(E e,long timeout,TimeUnit unit) throws InterruptedException;
    E take() throws InterruptedException;
    E poll(long timeout,TimeUnit unit) throws InterruptedException;
    int remainingCapacity();
    int drainTo(Collection<? super E> collection);
    int drainTo(Collection<? super E> collection,int maximum);
}
