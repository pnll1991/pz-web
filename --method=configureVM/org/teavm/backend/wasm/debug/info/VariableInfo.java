package org.teavm.backend.wasm.debug.info;

import java.util.Collection;

public abstract class VariableInfo {
   public abstract String name();

   public abstract VariableType type();

   public abstract Collection<? extends VariableRangeInfo> ranges();
}
