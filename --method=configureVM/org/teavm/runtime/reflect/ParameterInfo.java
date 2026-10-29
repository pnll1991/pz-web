package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class ParameterInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo genericType();

   @Unmanaged
   @Intrinsified
   public native int annotationCount();

   @Unmanaged
   @Intrinsified
   public native AnnotationInfo annotation(int var1);
}
