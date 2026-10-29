package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.StringInfo;

public final class MethodInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native StringInfo name();

   @Unmanaged
   @Intrinsified
   public native int modifiers();

   @Unmanaged
   @Intrinsified
   public native DerivedClassInfo returnType();

   @Unmanaged
   @Intrinsified
   public native int parameterCount();

   @Unmanaged
   @Intrinsified
   public native DerivedClassInfo parameterType(int var1);

   @Unmanaged
   @Intrinsified
   public native int checkedExceptionCount();

   @Unmanaged
   @Intrinsified
   public native ClassInfo checkedExceptionType(int var1);

   @Unmanaged
   @Intrinsified
   public native Object call(Object var1, Object[] var2);

   @Unmanaged
   @Intrinsified
   public native MethodReflectionInfo reflection();
}
