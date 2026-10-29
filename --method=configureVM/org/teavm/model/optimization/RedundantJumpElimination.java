package org.teavm.model.optimization;

import java.util.Arrays;
import org.teavm.common.Graph;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.TransitionExtractor;

public class RedundantJumpElimination implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      return optimize(program);
   }

   public static boolean optimize(Program program) {
      Graph cfg = ProgramUtils.buildControlFlowGraph(program);
      int[] incomingCount = new int[cfg.size()];
      Arrays.setAll(incomingCount, cfg::incomingEdgesCount);
      boolean changed = false;
      TransitionExtractor transitionExtractor = new TransitionExtractor();

      for (int i = 1; i < program.basicBlockCount(); i++) {
         BasicBlock block = program.basicBlockAt(i);
         if (block != null) {
            Instruction insn = block.getLastInstruction();
            if (insn instanceof JumpInstruction) {
               BasicBlock target = ((JumpInstruction)insn).getTarget();
               if (incomingCount[target.getIndex()] <= 1 && block.getTryCatchBlocks().isEmpty() && target.getTryCatchBlocks().isEmpty()) {
                  block.getLastInstruction().delete();

                  for (Phi phi : target.getPhis()) {
                     if (!phi.getIncomings().isEmpty()) {
                        Incoming incoming = phi.getIncomings().get(0);
                        AssignInstruction assign = new AssignInstruction();
                        assign.setReceiver(phi.getReceiver());
                        assign.setAssignee(incoming.getValue());
                        block.add(assign);
                     }
                  }

                  Instruction lastInsn = target.getLastInstruction();
                  if (lastInsn != null) {
                     lastInsn.acceptVisitor(transitionExtractor);
                     BasicBlock[] successors = transitionExtractor.getTargets();
                     if (successors != null) {
                        for (BasicBlock successor : successors) {
                           successor.getPhis()
                              .stream()
                              .flatMap(phi -> phi.getIncomings().stream())
                              .filter(incoming -> incoming.getSource() == target)
                              .forEach(incoming -> incoming.setSource(block));
                        }
                     }
                  }

                  while (target.getFirstInstruction() != null) {
                     Instruction instruction = target.getFirstInstruction();
                     instruction.delete();
                     block.add(instruction);
                  }

                  for (TryCatchBlock tryCatch : target.getTryCatchBlocks()) {
                     for (Phi phi : tryCatch.getHandler().getPhis()) {
                        phi.getIncomings().removeIf(incoming -> incoming.getSource() == target);
                     }
                  }

                  incomingCount[target.getIndex()] = 2;
                  program.deleteBasicBlock(target.getIndex());
                  i--;
                  changed = true;
               }
            }
         }
      }

      if (changed) {
         program.pack();
      }

      return changed;
   }
}
