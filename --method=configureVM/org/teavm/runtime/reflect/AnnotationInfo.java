package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class AnnotationInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native AnnotationData data();

   @Unmanaged
   @Intrinsified
   public native AnnotationConstructor constructor();
}
