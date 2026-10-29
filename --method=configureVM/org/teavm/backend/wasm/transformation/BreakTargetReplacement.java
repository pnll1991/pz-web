package org.teavm.backend.wasm.transformation;

import java.util.function.Function;
import org.teavm.backend.wasm.model.instruction.WasmBranch;
import org.teavm.backend.wasm.model.instruction.WasmBreak;
import org.teavm.backend.wasm.model.instruction.WasmCastBranch;
import org.teavm.backend.wasm.model.instruction.WasmDefaultInstructionVisitor;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.backend.wasm.model.instruction.WasmNullBranch;
import org.teavm.backend.wasm.model.instruction.WasmSwitch;

public class BreakTargetReplacement extends WasmDefaultInstructionVisitor {
   private Function<WasmInstructionList, WasmInstructionList> mapping;

   public BreakTargetReplacement(Function<WasmInstructionList, WasmInstructionList> mapping) {
      this.mapping = mapping;
   }

   @Override
   public void visit(WasmBreak instruction) {
      instruction.setTarget(this.map(instruction.getTarget()));
      super.visit(instruction);
   }

   @Override
   public void visit(WasmBranch instruction) {
      instruction.setTarget(this.map(instruction.getTarget()));
      super.visit(instruction);
   }

   @Override
   public void visit(WasmCastBranch instruction) {
      instruction.setTarget(this.map(instruction.getTarget()));
      super.visit(instruction);
   }

   @Override
   public void visit(WasmNullBranch instruction) {
      instruction.setTarget(this.map(instruction.getTarget()));
      super.visit(instruction);
   }

   @Override
   public void visit(WasmSwitch instruction) {
      instruction.setDefaultTarget(this.map(instruction.getDefaultTarget()));

      for (int i = 0; i < instruction.getTargets().size(); i++) {
         instruction.getTargets().set(i, this.map(instruction.getTargets().get(i)));
      }

      super.visit(instruction);
   }

   private WasmInstructionList map(WasmInstructionList list) {
      WasmInstructionList result = this.mapping.apply(list);
      return result != null ? result : list;
   }
}
