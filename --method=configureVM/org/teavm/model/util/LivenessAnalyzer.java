package org.teavm.model.util;

import java.util.BitSet;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.hppc.IntStack;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;

public class LivenessAnalyzer {
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
      DominatorTree dominatorTree = GraphUtils.buildDominatorTree(cfg);
      BitSet[] visited = new BitSet[program.basicBlockCount()];
      this.liveVars = new BitSet[program.basicBlockCount()];
      this.liveOutVars = new BitSet[program.basicBlockCount()];

      for (int i = 0; i < this.liveVars.length; i++) {
         visited[i] = new BitSet(program.variableCount());
         this.liveVars[i] = new BitSet(program.variableCount());
         this.liveOutVars[i] = new BitSet(program.variableCount());
      }

      UsageExtractor usageExtractor = new UsageExtractor();
      DefinitionExtractor defExtractor = new DefinitionExtractor();
      IntStack stack = new IntStack();
      int[] definitions = new int[program.variableCount()];

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         BitSet usedVariables = new BitSet(program.variableCount());

         for (Instruction insn = block.getLastInstruction(); insn != null; insn = insn.getPrevious()) {
            insn.acceptVisitor(defExtractor);

            for (Variable var : defExtractor.getDefinedVariables()) {
               definitions[var.getIndex()] = i;
               usedVariables.clear(var.getIndex());
            }

            insn.acceptVisitor(usageExtractor);

            for (Variable var : usageExtractor.getUsedVariables()) {
               usedVariables.set(var.getIndex());
            }
         }

         for (Phi phi : block.getPhis()) {
            definitions[phi.getReceiver().getIndex()] = i;
            usedVariables.clear(phi.getReceiver().getIndex());

            for (Incoming incoming : phi.getIncomings()) {
               stack.push(incoming.getSource().getIndex());
               stack.push(incoming.getValue().getIndex());
            }
         }

         if (block.getExceptionVariable() != null) {
            definitions[block.getExceptionVariable().getIndex()] = i;
            usedVariables.clear(block.getExceptionVariable().getIndex());
         }

         if (i == 0) {
            for (int v = 0; v < parameterCount; v++) {
               definitions[v] = 0;
               usedVariables.clear(v);
            }
         }

         int[] predecessors = cfg.incomingEdges(i);

         for (int v = usedVariables.nextSetBit(0); v >= 0; v = usedVariables.nextSetBit(v + 1)) {
            this.liveVars[i].set(v);

            for (int pred : predecessors) {
               stack.push(pred);
               stack.push(v);
            }
         }
      }

      while (!stack.isEmpty()) {
         int variable = stack.pop();
         int block = stack.pop();
         BitSet blockVisited = visited[block];
         if (!blockVisited.get(variable)) {
            blockVisited.set(variable);
            if (definitions[variable] != block && dominatorTree.dominates(definitions[variable], block)) {
               this.liveVars[block].set(variable, true);

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

         BasicBlock block = program.basicBlockAt(i);

         for (Phi phi : block.getPhis()) {
            for (Incoming incoming : phi.getIncomings()) {
               this.liveOutVars[incoming.getSource().getIndex()].set(incoming.getValue().getIndex());
            }
         }
      }
   }
}
