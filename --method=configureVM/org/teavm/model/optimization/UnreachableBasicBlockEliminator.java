package org.teavm.model.optimization;

import java.util.List;
import org.teavm.common.IntegerStack;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.util.TransitionExtractor;

public class UnreachableBasicBlockEliminator {
   public void removeUnreachableInstructions(Program program) {
      TransitionExtractor transitionExtractor = new TransitionExtractor();

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            instruction.acceptVisitor(transitionExtractor);
            if (transitionExtractor.getTargets() != null) {
               while (instruction.getNext() != null) {
                  instruction.getNext().delete();
               }
            }
         }
      }
   }

   public void optimize(Program program) {
      if (program.basicBlockCount() != 0) {
         TransitionExtractor transitionExtractor = new TransitionExtractor();
         boolean[] reachable = new boolean[program.basicBlockCount()];
         IntegerStack stack = new IntegerStack(program.basicBlockCount());
         stack.push(0);

         while (!stack.isEmpty()) {
            int i = stack.pop();
            if (!reachable[i]) {
               reachable[i] = true;
               BasicBlock block = program.basicBlockAt(i);
               block.getLastInstruction().acceptVisitor(transitionExtractor);

               for (BasicBlock successor : transitionExtractor.getTargets()) {
                  if (!reachable[successor.getIndex()]) {
                     stack.push(successor.getIndex());
                  }
               }

               for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
                  stack.push(tryCatch.getHandler().getIndex());
               }
            }
         }

         for (int i = 0; i < reachable.length; i++) {
            if (!reachable[i]) {
               BasicBlock block = program.basicBlockAt(i);
               if (block.getLastInstruction() != null) {
                  block.getLastInstruction().acceptVisitor(transitionExtractor);

                  for (BasicBlock successor : transitionExtractor.getTargets()) {
                     successor.removeIncomingsFrom(block);
                  }
               }

               for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
                  tryCatch.getHandler().removeIncomingsFrom(block);
               }

               program.deleteBasicBlock(i);
            }
         }

         for (int i = 0; i < program.basicBlockCount(); i++) {
            BasicBlock block = program.basicBlockAt(i);
            if (block != null) {
               for (Phi phi : block.getPhis()) {
                  List<Incoming> incomingList = phi.getIncomings();

                  for (int j = 0; j < incomingList.size(); j++) {
                     Incoming incoming = incomingList.get(j);
                     if (!reachable[incoming.getSource().getIndex()]) {
                        incomingList.remove(j--);
                     }
                  }
               }
            }
         }

         program.pack();
      }
   }
}
