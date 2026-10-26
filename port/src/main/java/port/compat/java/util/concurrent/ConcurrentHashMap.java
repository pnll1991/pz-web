package port.compat.java.util.concurrent;

import java.util.AbstractSet;
import java.util.Iterator;

/** Only this nested view is substituted; TeaVM supplies the actual concurrent map. */
public final class ConcurrentHashMap {
    public static final class KeySetView<K,V> extends AbstractSet<K> {
        private final java.util.concurrent.ConcurrentHashMap<K,V> map;
        private final V mappedValue;
        @SuppressWarnings("unchecked") public KeySetView() { map = new java.util.concurrent.ConcurrentHashMap<>(); mappedValue = (V) Boolean.TRUE; }
        @SuppressWarnings("unchecked") public KeySetView(int capacity) { map = new java.util.concurrent.ConcurrentHashMap<>(capacity); mappedValue = (V) Boolean.TRUE; }
        public java.util.concurrent.ConcurrentHashMap<K,V> getMap() { return map; }
        public V getMappedValue() { return mappedValue; }
        public int size() { return map.size(); }
        public boolean contains(Object value) { return map.containsKey(value); }
        public boolean add(K value) { return map.putIfAbsent(value,mappedValue) == null; }
        public boolean remove(Object value) { return map.remove(value) != null; }
        public void clear() { map.clear(); }
        public Iterator<K> iterator() { return map.keySet().iterator(); }
    }
}
