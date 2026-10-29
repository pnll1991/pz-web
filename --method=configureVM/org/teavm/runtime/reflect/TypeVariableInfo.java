package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.StringInfo;

public final class TypeVariableInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native StringInfo name();

   @Unmanaged
   @Intrinsified
   public native int boundCount();

   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo bound(int var1);
}
