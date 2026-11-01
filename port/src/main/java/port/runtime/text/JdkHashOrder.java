package port.runtime.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The iteration order a java.util.HashMap has on the JDK, reproduced from an insertion-ordered map.
 *
 * Why: the multiplayer server validates a SHA-256 over script content that some scripts build by iterating a HashMap
 * (SpriteOverlayConfigScript.allStyles). TeaVM's HashMap iterates in another order, so the client hash never matched the
 * server's ("Script version mismatch with server"). OpenJDK order: table of power-of-two size (16 initially, doubled while
 * size &gt; 0.75 * length), bucket = (h ^ h &gt;&gt;&gt; 16) &amp; (length - 1), buckets ascending, insertion order inside a
 * bucket (a resize split keeps relative order; treeified bins keep their next-chain order).
 * The map passed in must be a LinkedHashMap (port.compiler.HashOrderCompatibility makes the game's field one).
 */
public final class JdkHashOrder {
    private JdkHashOrder() { }

    public static <K, V> Collection<V> values(Map<K, V> insertionOrdered) {
        if (!(insertionOrdered instanceof LinkedHashMap))
            throw new IllegalStateException("JdkHashOrder needs an insertion-ordered map, got " + insertionOrdered.getClass().getName());
        int size = insertionOrdered.size();
        int length = 16;
        while (size > length * 3 / 4) length <<= 1;
        List<List<V>> buckets = new ArrayList<>(length);
        for (int i = 0; i < length; i++) buckets.add(null);
        for (Map.Entry<K, V> entry : insertionOrdered.entrySet()) {
            int h = entry.getKey() == null ? 0 : entry.getKey().hashCode();
            int index = (h ^ (h >>> 16)) & (length - 1);
            List<V> bucket = buckets.get(index);
            if (bucket == null) { bucket = new ArrayList<>(2); buckets.set(index, bucket); }
            bucket.add(entry.getValue());
        }
        List<V> ordered = new ArrayList<>(size);
        for (List<V> bucket : buckets) if (bucket != null) ordered.addAll(bucket);
        return ordered;
    }
}
