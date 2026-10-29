package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;

public final class AnnotationConstructor extends ReflectionInfo {
   @Intrinsified
   public native Object createAnnotation(AnnotationData var1);
}
