package org.teavm.backend.c.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.Unmanaged;
import org.teavm.interop.c.Include;

public final class Memory {
   private Memory() {
   }

   @Include("stdlib.h")
   @Import(name = "malloc")
   @Unmanaged
   public static native Address malloc(int var0);

   @Include("stdlib.h")
   @Import(name = "free")
   @Unmanaged
   public static native void free(Address var0);

   @Include("string.h")
   @Import(name = "memcpy")
   @Unmanaged
   public static native void memcpy(Address var0, Address var1, int var2);
}
