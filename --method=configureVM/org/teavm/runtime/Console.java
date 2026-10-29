package org.teavm.runtime;

import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.interop.Import;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
public final class Console {
   private Console() {
   }

   public static native void printString(String var0);

   @Import(name = "teavm_printInt")
   @RuntimeInclude("log.h")
   public static native void printInt(int var0);
}
