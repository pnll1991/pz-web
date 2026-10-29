package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class MethodReflectionInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo genericReturnType();

   @Unmanaged
   @Intrinsified
   public native int parameterInfoCount();

   @Unmanaged
   @Intrinsified
   public native ParameterInfo parameterInfo(int var1);

   @Unmanaged
   @Intrinsified
   public native int annotationCount();

   @Unmanaged
   @Intrinsified
   public native AnnotationInfo annotation(int var1);

   @Unmanaged
   @Intrinsified
   public native int typeParameterCount();

   @Unmanaged
   @Intrinsified
   public native TypeVariableInfo typeParameter(int var1);
}
