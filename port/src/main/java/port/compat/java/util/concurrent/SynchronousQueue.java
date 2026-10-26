package port.compat.java.util.concurrent;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Zero-capacity rendezvous: no producer completes until a consumer accepts its value. */
public class SynchronousQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>,java.io.Serializable {
    private static final class Transfer<E> {E value;boolean matched;Transfer(E value){this.value=value;}}
    private final ArrayDeque<Transfer<E>> producers=new ArrayDeque<>(),consumers=new ArrayDeque<>();
    private final boolean fair;
    public SynchronousQueue(){this(false);}public SynchronousQueue(boolean fair){this.fair=fair;}
    private Transfer<E> remove(ArrayDeque<Transfer<E>> queue){return fair?queue.pollFirst():queue.pollLast();}
    public int size(){return 0;}public int remainingCapacity(){return 0;}public E peek(){return null;}public Iterator<E> iterator(){return Collections.emptyIterator();}
    public boolean contains(Object value){return false;}public boolean remove(Object value){return false;}public void clear(){}
    public synchronized boolean offer(E value){Objects.requireNonNull(value);var consumer=remove(consumers);if(consumer==null)return false;consumer.value=value;consumer.matched=true;notifyAll();return true;}
    public synchronized E poll(){var producer=remove(producers);if(producer==null)return null;producer.matched=true;notifyAll();return producer.value;}
    public void put(E value)throws InterruptedException{send(value,0,false);}
    public boolean offer(E value,long timeout,TimeUnit unit)throws InterruptedException{return send(value,unit.toNanos(timeout),true);}
    private synchronized boolean send(E value,long nanos,boolean timed)throws InterruptedException{
        Objects.requireNonNull(value);if(Thread.interrupted())throw new InterruptedException();if(offer(value))return true;if(timed&&nanos<=0)return false;
        var transfer=new Transfer<E>(value);producers.addLast(transfer);long start=System.nanoTime();
        try{while(!transfer.matched){if(!timed)wait();else{long left=nanos-(System.nanoTime()-start);if(left<=0)return false;TimeUnit.NANOSECONDS.timedWait(this,left);}}return true;}
        catch(InterruptedException error){if(!transfer.matched)throw error;Thread.currentThread().interrupt();return true;}
        finally{producers.remove(transfer);}
    }
    public E take()throws InterruptedException{return receive(0,false);}
    public E poll(long timeout,TimeUnit unit)throws InterruptedException{return receive(unit.toNanos(timeout),true);}
    private synchronized E receive(long nanos,boolean timed)throws InterruptedException{
        if(Thread.interrupted())throw new InterruptedException();E ready=poll();if(ready!=null)return ready;if(timed&&nanos<=0)return null;
        var transfer=new Transfer<E>(null);consumers.addLast(transfer);long start=System.nanoTime();
        try{while(!transfer.matched){if(!timed)wait();else{long left=nanos-(System.nanoTime()-start);if(left<=0)return null;TimeUnit.NANOSECONDS.timedWait(this,left);}}return transfer.value;}
        catch(InterruptedException error){if(!transfer.matched)throw error;Thread.currentThread().interrupt();return transfer.value;}
        finally{consumers.remove(transfer);}
    }
    public int drainTo(Collection<? super E> target){return drainTo(target,Integer.MAX_VALUE);}
    public int drainTo(Collection<? super E> target,int maximum){Objects.requireNonNull(target);if(target==this)throw new IllegalArgumentException();int count=0;E value;while(count<maximum&&(value=poll())!=null){target.add(value);count++;}return count;}
}
