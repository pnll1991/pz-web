package org.teavm.runtime;

import org.teavm.interop.Function;
import org.teavm.interop.Unmanaged;

public abstract class InitFunction extends Function {
   @Unmanaged
   public abstract void run();
}
