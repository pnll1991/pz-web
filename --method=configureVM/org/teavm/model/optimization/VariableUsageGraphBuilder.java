package org.teavm.model.optimization;

import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public final class VariableUsageGraphBuilder {
   private VariableUsageGraphBuilder() {
   }

   public static Graph build(Program program) {
      GraphBuilder builder = new GraphBuilder(program.variableCount());
      VariableUsageGraphBuilder.InstructionAnalyzer analyzer = new VariableUsageGraphBuilder.InstructionAnalyzer(builder);

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);

         for (Instruction insn : block) {
            insn.acceptVisitor(analyzer);
         }

         for (Phi phi : block.getPhis()) {
            for (Incoming incoming : phi.getIncomings()) {
               builder.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
            }
         }
      }

      return builder.build();
   }

   static class InstructionAnalyzer extends AbstractInstructionVisitor {
      private GraphBuilder builder;

      InstructionAnalyzer(GraphBuilder builder) {
         this.builder = builder;
      }

      private void use(Variable receiver, Variable... arguments) {
         for (Variable arg : arguments) {
            this.builder.addEdge(arg.getIndex(), receiver.getIndex());
         }
      }

      @Override
      public void visit(BinaryInstruction insn) {
         this.use(insn.getReceiver(), insn.getFirstOperand(), insn.getSecondOperand());
      }

      @Override
      public void visit(NegateInstruction insn) {
         this.use(insn.getReceiver(), insn.getOperand());
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.use(insn.getReceiver(), insn.getAssignee());
      }

      @Override
      public void visit(CastInstruction insn) {
         this.use(insn.getReceiver(), insn.getValue());
      }

      @Override
      public void visit(CastNumberInstruction insn) {
         this.use(insn.getReceiver(), insn.getValue());
      }

      @Override
      public void visit(CastIntegerInstruction insn) {
         this.use(insn.getReceiver(), insn.getValue());
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         this.use(insn.getReceiver(), insn.getSize());
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         this.use(insn.getReceiver(), insn.getDimensions().toArray(new Variable[0]));
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() != null) {
            this.use(insn.getReceiver(), insn.getInstance());
         }
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         this.use(insn.getReceiver(), insn.getArray());
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.use(insn.getReceiver(), insn.getArray());
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.use(insn.getReceiver(), insn.getArray());
      }

      @Override
      public void visit(GetElementInstruction insn) {
         this.use(insn.getReceiver(), insn.getArray(), insn.getIndex());
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         this.use(insn.getReceiver(), insn.getValue());
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.use(insn.getReceiver(), insn.getValue());
      }

      @Override
      public void visit(BoundCheckInstruction insn) {
         if (insn.getArray() != null) {
            this.use(insn.getReceiver(), insn.getIndex(), insn.getArray());
         } else {
            this.use(insn.getReceiver(), insn.getIndex());
         }
      }
   }
}
