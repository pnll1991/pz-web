package org.teavm.model.optimization;

import org.teavm.common.Graph;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodReader;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class UnusedVariableElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      return this.optimize(context.getMethod(), program);
   }

   public boolean optimize(MethodReader method, Program program) {
      Graph graph = VariableUsageGraphBuilder.build(program);
      boolean[] escaping = VariableEscapeAnalyzer.findEscapingVariables(program);
      boolean[] used = new boolean[escaping.length];

      for (int i = 0; i <= method.parameterCount(); i++) {
         used[i] = true;
      }

      int[] stack = new int[graph.size() * 2];
      int top = 0;

      for (int i = 0; i < used.length; i++) {
         if (escaping[i]) {
            stack[top++] = i;
         }
      }

      while (top > 0) {
         int var = stack[--top];
         if (!used[var]) {
            used[var] = true;

            for (int arg : graph.incomingEdges(var)) {
               if (!used[arg]) {
                  stack[top++] = arg;
               }
            }
         }
      }

      UnusedVariableElimination.InstructionOptimizer insnOptimizer = new UnusedVariableElimination.InstructionOptimizer(used);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         if (block.getExceptionVariable() != null && !used[block.getExceptionVariable().getIndex()]) {
            block.setExceptionVariable(null);
         }

         for (Instruction insn : block) {
            insnOptimizer.eliminate = false;
            insn.acceptVisitor(insnOptimizer);
            if (insnOptimizer.eliminate) {
               insn.delete();
            }
         }

         for (int j = 0; j < block.getPhis().size(); j++) {
            Phi phi = block.getPhis().get(j);
            if (!used[phi.getReceiver().getIndex()]) {
               block.getPhis().remove(j--);
            }
         }
      }

      for (int i = 0; i < program.variableCount(); i++) {
         if (!used[i]) {
            program.deleteVariable(i);
         }
      }

      program.pack();
      return false;
   }

   static class InstructionOptimizer extends AbstractInstructionVisitor {
      private boolean[] used;
      boolean eliminate;

      InstructionOptimizer(boolean[] used) {
         this.used = used;
      }

      private void requestUsage(Variable var) {
         if (!this.used[var.getIndex()]) {
            this.eliminate = true;
         }
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(NullConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(IntegerConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(LongConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(FloatConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(DoubleConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(BinaryInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(NegateInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(CastInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(CastNumberInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(CastIntegerInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(ConstructInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(GetElementInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getReceiver() != null && !this.used[insn.getReceiver().getIndex()]) {
            insn.setReceiver(null);
         }
      }

      @Override
      public void visit(InvokeDynamicInstruction insn) {
         if (insn.getReceiver() != null && !this.used[insn.getReceiver().getIndex()]) {
            insn.setReceiver(null);
         }
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         this.requestUsage(insn.getReceiver());
      }
   }
}
