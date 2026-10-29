package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class TypeVariableReference extends GenericTypeInfo {
   @Unmanaged
   @Intrinsified
   public native short level();

   @Unmanaged
   @Intrinsified
   public native short index();
}
