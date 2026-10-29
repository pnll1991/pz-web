package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Import;

public class MemoryTrace {
   private MemoryTrace() {
   }

   @Import(name = "allocate", module = "teavmHeapTrace")
   public static native void allocate(Address var0, int var1);

   @Import(name = "free", module = "teavmHeapTrace")
   public static native void free(Address var0, int var1);

   @Import(name = "assertFree", module = "teavmHeapTrace")
   public static native void assertFree(Address var0, int var1);

   @Import(name = "checkIsFree", module = "teavmHeapTrace")
   public static native void checkIsFree(Address var0, int var1);

   @Import(name = "markStarted", module = "teavmHeapTrace")
   public static native void markStarted();

   @Import(name = "mark", module = "teavmHeapTrace")
   public static native void mark(Address var0);

   @Import(name = "reportDirtyRegion", module = "teavmHeapTrace")
   public static native void reportDirtyRegion(Address var0);

   @Import(name = "markCompleted", module = "teavmHeapTrace")
   public static native void markCompleted();

   @Import(name = "move", module = "teavmHeapTrace")
   public static native void move(Address var0, Address var1, int var2);

   @Import(name = "gcStarted", module = "teavmHeapTrace")
   public static native void gcStarted(boolean var0);

   @Import(name = "sweepStarted", module = "teavmHeapTrace")
   public static native void sweepStarted();

   @Import(name = "sweepCompleted", module = "teavmHeapTrace")
   public static native void sweepCompleted();

   @Import(name = "defragStarted", module = "teavmHeapTrace")
   public static native void defragStarted();

   @Import(name = "defragCompleted", module = "teavmHeapTrace")
   public static native void defragCompleted();

   @Import(name = "writeHeapDump", module = "teavmHeapTrace")
   public static native void writeHeapDump();

   @Import(name = "gcCompleted", module = "teavmHeapTrace")
   public static native void gcCompleted();
}
