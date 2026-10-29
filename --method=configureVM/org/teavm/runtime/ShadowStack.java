package org.teavm.runtime;

import org.teavm.interop.Address;
import org.teavm.interop.StaticInit;
import org.teavm.interop.Unmanaged;

@Unmanaged
@StaticInit
public final class ShadowStack {
   private ShadowStack() {
   }

   public static native void allocStack(int var0);

   public static native void registerGCRoot(int var0, Object var1);

   public static native void removeGCRoot(int var0);

   public static native void releaseStack(int var0);

   public static native Address getStackTop();

   public static native Address getNextStackFrame(Address var0);

   public static native int getStackRootCount(Address var0);

   public static native Address getStackRootPointer(Address var0);

   public static native int getCallSiteId(Address var0);

   public static native void registerCallSite(int var0);

   public static native int getExceptionHandlerId();

   public static native void setExceptionHandlerId(Address var0, int var1);

   public static native void setExceptionHandlerSkip(Address var0);

   public static native void setExceptionHandlerRestore(Address var0);
}
