package port.compat.java.util.concurrent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteArraySet<E> extends AbstractSet<E> {
    private final CopyOnWriteArrayList<E> values = new CopyOnWriteArrayList<>();
    public CopyOnWriteArraySet() { }
    public CopyOnWriteArraySet(Collection<? extends E> source) { addAll(source); }
    public int size() { return values.size(); }
    public boolean add(E value) { return values.addIfAbsent(value); }
    public boolean contains(Object value) { return values.contains(value); }
    public boolean remove(Object value) { return values.remove(value); }
    public void clear() { values.clear(); }
    public Iterator<E> iterator() { return values.iterator(); }
    public boolean removeAll(Collection<?> source) { return values.removeAll(source); }
    public boolean retainAll(Collection<?> source) { return values.retainAll(source); }
    public boolean removeIf(java.util.function.Predicate<? super E> predicate) { return values.removeIf(predicate); }
}
