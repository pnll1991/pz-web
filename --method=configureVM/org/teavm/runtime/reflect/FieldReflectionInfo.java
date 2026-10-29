package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class FieldReflectionInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native int annotationCount();

   @Unmanaged
   @Intrinsified
   public native AnnotationInfo annotation(int var1);

   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo genericType();
}
