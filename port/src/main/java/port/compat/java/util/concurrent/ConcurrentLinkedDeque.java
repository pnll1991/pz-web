package port.compat.java.util.concurrent;

import java.util.*;

/** Cooperative-thread deque. Snapshots retain node identity for iterator.remove. */
public class ConcurrentLinkedDeque<E> extends AbstractCollection<E> implements Deque<E> {
    private static final class Entry<E> { final E value; Entry(E value) { this.value = Objects.requireNonNull(value); } }
    private final ArrayDeque<Entry<E>> entries = new ArrayDeque<>();
    public ConcurrentLinkedDeque() { }
    public ConcurrentLinkedDeque(Collection<? extends E> values) { addAll(values); }
    public synchronized int size() { return entries.size(); }
    public synchronized void clear() { entries.clear(); }
    public synchronized void addFirst(E value) { entries.addFirst(new Entry<>(value)); }
    public synchronized void addLast(E value) { entries.addLast(new Entry<>(value)); }
    public boolean offerFirst(E value) { addFirst(value); return true; }
    public boolean offerLast(E value) { addLast(value); return true; }
    private E value(Entry<E> entry) { return entry == null ? null : entry.value; }
    public synchronized E pollFirst() { return value(entries.pollFirst()); }
    public synchronized E pollLast() { return value(entries.pollLast()); }
    public synchronized E peekFirst() { return value(entries.peekFirst()); }
    public synchronized E peekLast() { return value(entries.peekLast()); }
    private E required(E value) { if (value == null) throw new NoSuchElementException(); return value; }
    public E removeFirst() { return required(pollFirst()); }
    public E removeLast() { return required(pollLast()); }
    public E getFirst() { return required(peekFirst()); }
    public E getLast() { return required(peekLast()); }
    public boolean add(E value) { return offerLast(value); }
    public boolean offer(E value) { return offerLast(value); }
    public E remove() { return removeFirst(); }
    public E poll() { return pollFirst(); }
    public E element() { return getFirst(); }
    public E peek() { return peekFirst(); }
    public void push(E value) { addFirst(value); }
    public E pop() { return removeFirst(); }
    public boolean remove(Object value) { return removeFirstOccurrence(value); }
    public boolean removeFirstOccurrence(Object value) { return removeOccurrence(value, false); }
    public boolean removeLastOccurrence(Object value) { return removeOccurrence(value, true); }
    private boolean removeOccurrence(Object value, boolean reverse) {
        if (value == null) return false;
        ArrayList<Entry<E>> copy;
        synchronized (this) { copy = new ArrayList<>(entries); }
        if (reverse) Collections.reverse(copy);
        // Equality can yield or call user code. Report true only if this call
        // actually removed the node, even when another thread polled it first.
        for (var entry : copy) if (value.equals(entry.value)) {
            synchronized (this) { if (entries.remove(entry)) return true; }
        }
        return false;
    }
    public Iterator<E> iterator() { return snapshot(false); }
    public Iterator<E> descendingIterator() { return snapshot(true); }
    private synchronized Iterator<E> snapshot(boolean reverse) {
        var copy = new ArrayList<>(entries);
        if (reverse) Collections.reverse(copy);
        var iterator = copy.iterator();
        return new Iterator<>() {
            Entry<E> last;
            public boolean hasNext() { return iterator.hasNext(); }
            public E next() { last = iterator.next(); return last.value; }
            public void remove() {
                if (last == null) throw new IllegalStateException();
                synchronized (ConcurrentLinkedDeque.this) { entries.remove(last); }
                last = null;
            }
        };
    }
}
