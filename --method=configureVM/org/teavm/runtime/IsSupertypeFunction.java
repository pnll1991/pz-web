package org.teavm.runtime;

import org.teavm.interop.Function;
import org.teavm.interop.Unmanaged;

public abstract class IsSupertypeFunction extends Function {
   @Unmanaged
   public abstract boolean apply(RuntimeClass var1, RuntimeClass var2);
}
