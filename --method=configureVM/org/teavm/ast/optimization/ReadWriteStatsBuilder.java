package org.teavm.ast.optimization;

import java.util.Arrays;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.common.IntegerStack;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.UsageExtractor;

class ReadWriteStatsBuilder {
   public int[] reads;
   public int[] writes;
   public Object[] constants;

   private ReadWriteStatsBuilder() {
   }

   public ReadWriteStatsBuilder(int variableCount) {
      this.reads = new int[variableCount];
      this.writes = new int[variableCount];
      this.constants = new Object[variableCount];
   }

   public ReadWriteStatsBuilder copy() {
      ReadWriteStatsBuilder result = new ReadWriteStatsBuilder();
      result.reads = Arrays.copyOf(this.reads, this.reads.length);
      result.writes = Arrays.copyOf(this.writes, this.writes.length);
      return result;
   }

   public void analyze(Program program) {
      Graph cfg = ProgramUtils.buildControlFlowGraph(program);
      Graph dom = GraphUtils.buildDominatorGraph(GraphUtils.buildDominatorTree(cfg), cfg.size());
      DefinitionExtractor defExtractor = new DefinitionExtractor();
      UsageExtractor useExtractor = new UsageExtractor();
      IntegerStack stack = new IntegerStack(program.basicBlockCount());
      stack.push(0);
      ReadWriteStatsBuilder.ConstantExtractor constantExtractor = new ReadWriteStatsBuilder.ConstantExtractor(this.constants);

      while (!stack.isEmpty()) {
         int node = stack.pop();
         BasicBlock block = program.basicBlockAt(node);
         if (block.getExceptionVariable() != null) {
            this.writes[block.getExceptionVariable().getIndex()]++;
            this.reads[block.getExceptionVariable().getIndex()]++;
         }

         for (Instruction insn : block) {
            insn.acceptVisitor(defExtractor);
            insn.acceptVisitor(useExtractor);

            for (Variable var : defExtractor.getDefinedVariables()) {
               this.writes[var.getIndex()]++;
            }

            for (Variable var : useExtractor.getUsedVariables()) {
               this.reads[var.getIndex()]++;
            }

            insn.acceptVisitor(constantExtractor);
         }

         for (Phi phi : block.getPhis()) {
            this.writes[phi.getReceiver().getIndex()] += phi.getIncomings().size();

            for (Incoming incoming : phi.getIncomings()) {
               if (this.writes[incoming.getValue().getIndex()] == 0) {
                  this.reads[incoming.getValue().getIndex()]++;
               }
            }
         }

         for (int succ : dom.outgoingEdges(node)) {
            stack.push(succ);
         }
      }
   }

   static class ConstantExtractor extends AbstractInstructionVisitor {
      private Object[] constants;

      ConstantExtractor(Object[] constants) {
         this.constants = constants;
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.constants[insn.getReceiver().getIndex()] = insn.getConstant();
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.constants[insn.getReceiver().getIndex()] = insn.getConstant();
      }

      @Override
      public void visit(IntegerConstantInstruction insn) {
         this.constants[insn.getReceiver().getIndex()] = insn.getConstant();
      }

      @Override
      public void visit(LongConstantInstruction insn) {
         this.constants[insn.getReceiver().getIndex()] = insn.getConstant();
      }

      @Override
      public void visit(FloatConstantInstruction insn) {
         this.constants[insn.getReceiver().getIndex()] = insn.getConstant();
      }

      @Override
      public void visit(DoubleConstantInstruction insn) {
         this.constants[insn.getReceiver().getIndex()] = insn.getConstant();
      }
   }
}
