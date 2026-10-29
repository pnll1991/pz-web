package org.teavm.model.optimization;

import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.SwitchInstruction;

public final class VariableEscapeAnalyzer {
   private VariableEscapeAnalyzer() {
   }

   public static boolean[] findEscapingVariables(Program program) {
      boolean[] escaping = new boolean[program.variableCount()];
      VariableEscapeAnalyzer.InstructionAnalyzer analyzer = new VariableEscapeAnalyzer.InstructionAnalyzer(escaping);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         for (Instruction insn : program.basicBlockAt(i)) {
            insn.acceptVisitor(analyzer);
         }
      }

      return escaping;
   }

   private static class InstructionAnalyzer extends AbstractInstructionVisitor {
      private boolean[] escaping;

      InstructionAnalyzer(boolean[] escaping) {
         this.escaping = escaping;
      }

      @Override
      public void visit(BranchingInstruction insn) {
         this.escaping[insn.getOperand().getIndex()] = true;
      }

      @Override
      public void visit(BinaryBranchingInstruction insn) {
         this.escaping[insn.getFirstOperand().getIndex()] = true;
         this.escaping[insn.getSecondOperand().getIndex()] = true;
      }

      @Override
      public void visit(SwitchInstruction insn) {
         this.escaping[insn.getCondition().getIndex()] = true;
      }

      @Override
      public void visit(ExitInstruction insn) {
         if (insn.getValueToReturn() != null) {
            this.escaping[insn.getValueToReturn().getIndex()] = true;
         }
      }

      @Override
      public void visit(RaiseInstruction insn) {
         this.escaping[insn.getException().getIndex()] = true;
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null) {
            this.escaping[insn.getInstance().getIndex()] = true;
         }

         this.escaping[insn.getValue().getIndex()] = true;
      }

      @Override
      public void visit(PutElementInstruction insn) {
         this.escaping[insn.getArray().getIndex()] = true;
         this.escaping[insn.getIndex().getIndex()] = true;
         this.escaping[insn.getValue().getIndex()] = true;
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getInstance() != null) {
            this.escaping[insn.getInstance().getIndex()] = true;
         }

         for (Variable arg : insn.getArguments()) {
            this.escaping[arg.getIndex()] = true;
         }
      }

      @Override
      public void visit(InvokeDynamicInstruction insn) {
         if (insn.getInstance() != null) {
            this.escaping[insn.getInstance().getIndex()] = true;
         }

         for (Variable arg : insn.getArguments()) {
            this.escaping[arg.getIndex()] = true;
         }
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         this.escaping[insn.getObjectRef().getIndex()] = true;
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         this.escaping[insn.getObjectRef().getIndex()] = true;
      }

      @Override
      public void visit(BoundCheckInstruction insn) {
         if (insn.getArray() != null) {
            this.escaping[insn.getArray().getIndex()] = true;
         }

         if (insn.getReceiver() != null) {
            this.escaping[insn.getReceiver().getIndex()] = true;
         }
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         if (insn.getValue() != null) {
            this.escaping[insn.getValue().getIndex()] = true;
         }

         if (insn.getReceiver() != null) {
            this.escaping[insn.getReceiver().getIndex()] = true;
         }
      }
   }
}
