package org.teavm.dependency;

import java.util.BitSet;

/** Compiler-only seen set for types interned by ONE TeaVM dependency analyzer.
 * Package access is intentional and pinned to TeaVM 0.16.0. Never shared across
 * analyses. The source preparation verifies the unique type-index invariant.
 */
public final class CompactTypeSet {
    private final BitSet seen = new BitSet();

    public boolean add(DependencyType type) {
        int index = type.index;
        if (seen.get(index)) return false;
        seen.set(index);
        return true;
    }
}
