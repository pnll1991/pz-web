package org.teavm.model.util;

import java.util.BitSet;
import org.teavm.common.Graph;
import org.teavm.hppc.IntStack;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Program;
import org.teavm.model.Variable;

public class NonSsaLivenessAnalyzer {
   private BitSet[] liveVars;
   private BitSet[] liveOutVars;

   public boolean liveIn(int block, int var) {
      return this.liveVars[block].get(var);
   }

   public BitSet liveIn(int block) {
      return (BitSet)this.liveVars[block].clone();
   }

   public BitSet liveOut(int block) {
      return (BitSet)this.liveOutVars[block].clone();
   }

   public void analyze(Program program, MethodDescriptor descriptor) {
      this.analyze(program, descriptor.parameterCount() + 1);
   }

   public void analyze(Program program, int parameterCount) {
      Graph cfg = ProgramUtils.buildControlFlowGraph(program);
      BitSet[] definitions = new BitSet[program.basicBlockCount()];
      BitSet[] definedVars = new BitSet[program.basicBlockCount()];
      BitSet[] visited = new BitSet[program.basicBlockCount()];
      this.liveVars = new BitSet[program.basicBlockCount()];
      this.liveOutVars = new BitSet[program.basicBlockCount()];

      for (int i = 0; i < this.liveVars.length; i++) {
         definitions[i] = new BitSet(program.variableCount());
         definedVars[i] = new BitSet(program.variableCount());
         visited[i] = new BitSet(program.variableCount());
         this.liveVars[i] = new BitSet(program.variableCount());
         this.liveOutVars[i] = new BitSet(program.variableCount());
      }

      UsageExtractor usageExtractor = new UsageExtractor();
      DefinitionExtractor defExtractor = new DefinitionExtractor();
      IntStack stack = new IntStack();
      IntStack defStack = new IntStack();

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         BitSet usedVariables = new BitSet();
         BitSet definedVariables = definitions[i];

         for (Instruction insn = block.getLastInstruction(); insn != null; insn = insn.getPrevious()) {
            insn.acceptVisitor(defExtractor);

            for (Variable var : defExtractor.getDefinedVariables()) {
               definedVariables.set(var.getIndex());
               usedVariables.clear(var.getIndex());
            }

            insn.acceptVisitor(usageExtractor);

            for (Variable var : usageExtractor.getUsedVariables()) {
               usedVariables.set(var.getIndex());
            }
         }

         assert block.getPhis().isEmpty();
         if (block.getExceptionVariable() != null) {
            definedVariables.set(block.getExceptionVariable().getIndex());
            usedVariables.clear(block.getExceptionVariable().getIndex());
         }

         if (i == 0) {
            for (int v = 0; v < parameterCount; v++) {
               definedVariables.set(v);
               usedVariables.clear(v);
            }
         }

         for (int v = usedVariables.nextSetBit(0); v >= 0; v = usedVariables.nextSetBit(v + 1)) {
            this.liveVars[i].set(v);
            visited[i].set(v);

            for (int pred : cfg.incomingEdges(i)) {
               stack.push(pred);
               stack.push(v);
            }
         }

         for (int v = definedVariables.nextSetBit(0); v >= 0; v = definedVariables.nextSetBit(v + 1)) {
            defStack.push(i);
            defStack.push(v);
         }
      }

      while (!defStack.isEmpty()) {
         int variable = defStack.pop();
         int block = defStack.pop();
         BitSet blockDefinedVars = definedVars[block];
         if (!blockDefinedVars.get(variable)) {
            blockDefinedVars.set(variable);

            for (int succ : cfg.outgoingEdges(block)) {
               if (!definedVars[succ].get(variable)) {
                  defStack.push(succ);
                  defStack.push(variable);
               }
            }
         }
      }

      while (!stack.isEmpty()) {
         int variable = stack.pop();
         int block = stack.pop();
         BitSet blockVisited = visited[block];
         if (!blockVisited.get(variable)) {
            blockVisited.set(variable);
            if (!definitions[block].get(variable) && definedVars[block].get(variable)) {
               this.liveVars[block].set(variable);

               for (int pred : cfg.incomingEdges(block)) {
                  if (!visited[pred].get(variable)) {
                     stack.push(pred);
                     stack.push(variable);
                  }
               }
            }
         }
      }

      for (int i = 0; i < this.liveVars.length; i++) {
         for (int j : cfg.incomingEdges(i)) {
            this.liveOutVars[j].or(this.liveVars[i]);
         }
      }
   }
}
