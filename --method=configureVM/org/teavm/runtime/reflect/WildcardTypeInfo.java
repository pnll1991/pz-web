package org.teavm.runtime.reflect;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.Unmanaged;

public final class WildcardTypeInfo extends GenericTypeInfo {
   @Unmanaged
   @Intrinsified
   public native GenericTypeInfo bound();
}
