package org.teavm.runtime;

import org.teavm.interop.StaticInit;
import org.teavm.interop.Structure;

@StaticInit
public class RuntimeObject extends Structure {
   public static final int GC_MARKED = Integer.MIN_VALUE;
   public static final int GC_OLD_GENERATION = 1073741824;
   public static int nextId;
   public int classReference;
   public int hashCode;
}
