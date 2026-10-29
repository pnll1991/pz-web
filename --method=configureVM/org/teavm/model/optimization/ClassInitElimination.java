package org.teavm.model.optimization;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.util.ProgramUtils;

public class ClassInitElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      Graph cfg = ProgramUtils.buildControlFlowGraph(program);
      DominatorTree dom = GraphUtils.buildDominatorTree(cfg);
      Graph domGraph = GraphUtils.buildDominatorGraph(dom, program.basicBlockCount());
      ClassInitElimination.Step start = new ClassInitElimination.Step(0);
      Deque<ClassInitElimination.Step> stack = new ArrayDeque<>();
      stack.push(start);

      while (!stack.isEmpty()) {
         ClassInitElimination.Step step = stack.pop();
         int node = step.node;
         BasicBlock block = program.basicBlockAt(node);
         Instruction insn = block.getFirstInstruction();

         while (insn != null) {
            Instruction nextInsn = insn.getNext();
            if (insn instanceof InitClassInstruction initClass) {
               if (!step.initializedClasses.add(initClass.getClassName())) {
                  insn.delete();
               }
            } else if (insn instanceof InvokeInstruction invoke) {
               step.initializedClasses.add(invoke.getMethod().getClassName());
            }

            insn = nextInsn;
         }

         for (int successor : domGraph.outgoingEdges(node)) {
            ClassInitElimination.Step next = new ClassInitElimination.Step(successor);
            next.initializedClasses.addAll(step.initializedClasses);
            stack.push(next);
         }
      }

      return false;
   }

   static class Step {
      int node;
      Set<String> initializedClasses = new HashSet<>();

      Step(int node) {
         this.node = node;
      }
   }
}
