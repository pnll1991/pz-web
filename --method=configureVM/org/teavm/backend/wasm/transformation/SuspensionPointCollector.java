package org.teavm.backend.wasm.transformation;

import java.util.HashSet;
import java.util.Set;
import org.teavm.backend.wasm.model.instruction.WasmDefaultInstructionVisitor;
import org.teavm.backend.wasm.model.instruction.WasmInstruction;

public class SuspensionPointCollector extends WasmDefaultInstructionVisitor {
   private Set<WasmInstruction> suspending = new HashSet<>();

   public boolean isSuspending(WasmInstruction insn) {
      return this.suspending.contains(insn);
   }

   @Override
   public void visitDefault(WasmInstruction instruction) {
      int sizeBefore = this.suspending.size();
      super.visitDefault(instruction);
      if (instruction.isSuspend() || this.suspending.size() > sizeBefore) {
         this.suspending.add(instruction);
      }
   }
}
