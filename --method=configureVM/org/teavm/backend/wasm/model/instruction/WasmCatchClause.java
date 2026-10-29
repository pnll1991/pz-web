package org.teavm.backend.wasm.model.instruction;

import java.util.Objects;
import org.teavm.backend.wasm.model.WasmTag;

public class WasmCatchClause {
   private WasmTag tag;
   private boolean ref;
   private WasmInstructionList target;

   public WasmCatchClause(WasmTag tag, boolean ref, WasmInstructionList target) {
      this.tag = tag;
      this.ref = ref;
      this.target = Objects.requireNonNull(target);
   }

   public WasmTag getTag() {
      return this.tag;
   }

   public void setTag(WasmTag tag) {
      this.tag = tag;
   }

   public boolean isRef() {
      return this.ref;
   }

   public void setRef(boolean ref) {
      this.ref = ref;
   }

   public WasmInstructionList getTarget() {
      return this.target;
   }

   public void setTarget(WasmInstructionList target) {
      this.target = Objects.requireNonNull(target);
   }
}
