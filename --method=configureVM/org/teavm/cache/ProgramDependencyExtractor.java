package org.teavm.cache;

import java.util.LinkedHashSet;
import java.util.Set;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodHandle;
import org.teavm.model.Program;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

public class ProgramDependencyExtractor extends AbstractInstructionVisitor {
   private final ProgramDependencyExtractor.AnalyzingVisitor visitor = new ProgramDependencyExtractor.AnalyzingVisitor();

   public String[] extractDependencies(Program program) {
      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            instruction.acceptVisitor(this.visitor);
         }
      }

      String[] result = this.visitor.dependencies.toArray(new String[0]);
      this.visitor.dependencies.clear();
      return result;
   }

   static class AnalyzingVisitor extends AbstractInstructionVisitor {
      Set<String> dependencies = new LinkedHashSet<>();

      @Override
      public void visit(GetFieldInstruction insn) {
         this.dependencies.add(insn.getField().getClassName());
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         this.dependencies.add(insn.getField().getClassName());
      }

      @Override
      public void visit(InvokeInstruction insn) {
         this.dependencies.add(insn.getMethod().getClassName());
      }

      @Override
      public void visit(InvokeDynamicInstruction insn) {
         for (RuntimeConstant cst : insn.getBootstrapArguments()) {
            if (cst.getKind() == 7) {
               MethodHandle handle = cst.getMethodHandle();
               this.dependencies.add(handle.getClassName());
            }
         }
      }
   }
}
