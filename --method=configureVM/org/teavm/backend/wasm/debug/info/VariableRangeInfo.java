package org.teavm.backend.wasm.debug.info;

public abstract class VariableRangeInfo {
   public abstract VariableInfo variable();

   public abstract int start();

   public abstract int end();

   public abstract int index();
}
