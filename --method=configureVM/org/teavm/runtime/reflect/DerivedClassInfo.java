package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class DerivedClassInfo extends ReflectionInfo {
   @Unmanaged
   @Intrinsified
   public native ClassInfo classInfo();

   @Unmanaged
   @Intrinsified
   public native int arrayDegree();
}
