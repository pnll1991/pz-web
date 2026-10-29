package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class ParameterizedTypeInfo extends GenericTypeInfo {
   @Unmanaged
   @Intrinsified
   public native ClassInfo rawType();

   @Unmanaged
   @Intrinsified
   public native int actualTypeArgumentCount();

   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo actualTypeArgument(int var1);

   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo ownerType();
}
