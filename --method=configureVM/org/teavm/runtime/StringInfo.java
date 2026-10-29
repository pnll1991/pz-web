package org.teavm.runtime;

import org.teavm.interop.Intrinsified;
import org.teavm.interop.NoSideEffects;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.reflect.ReflectionInfo;

public final class StringInfo extends ReflectionInfo {
   @Unmanaged
   @NoSideEffects
   @Intrinsified
   public native String getStringObject();
}
