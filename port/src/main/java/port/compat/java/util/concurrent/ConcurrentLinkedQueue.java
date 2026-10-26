package port.compat.java.util.concurrent;

import java.util.Collection;

public class ConcurrentLinkedQueue<E> extends ConcurrentLinkedDeque<E> {
    public ConcurrentLinkedQueue() { }
    public ConcurrentLinkedQueue(Collection<? extends E> values) { super(values); }
}
