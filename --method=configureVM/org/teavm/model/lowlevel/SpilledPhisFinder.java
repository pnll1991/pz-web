package org.teavm.model.lowlevel;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import org.teavm.common.DominatorTree;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;

class SpilledPhisFinder {
   private static final byte VISITING = 1;
   private static final byte VISITED = 2;
   private DominatorTree dom;
   boolean[] autoSpilled;
   byte[] status;
   int[][] variableSpilledBlocks;
   Phi[] definingPhis;
   int variableCount;
   private int[] variableClasses;
   private int[] colors;

   SpilledPhisFinder(List<Map<Instruction, BitSet>> liveInInformation, DominatorTree dom, Program program, int[] variableClasses, int[] colors) {
      this.dom = dom;
      this.variableCount = program.variableCount();
      this.autoSpilled = new boolean[this.variableCount];
      this.status = new byte[this.variableCount];
      this.variableSpilledBlocks = variableSpilledBlocks(liveInInformation, this.variableCount, variableClasses);
      this.definingPhis = findPhis(program, variableClasses);
      this.variableClasses = variableClasses;
      this.colors = colors;
   }

   private static int[][] variableSpilledBlocks(List<Map<Instruction, BitSet>> liveInInformation, int count, int[] variableClasses) {
      IntSet[] builder = new IntSet[count];

      for (int b = 0; b < liveInInformation.size(); b++) {
         Map<Instruction, BitSet> blockLiveIn = liveInInformation.get(b);

         for (BitSet liveVarsSet : blockLiveIn.values()) {
            for (int v = liveVarsSet.nextSetBit(0); v >= 0; v = liveVarsSet.nextSetBit(v + 1)) {
               int cls = variableClasses[v];
               if (builder[cls] == null) {
                  builder[cls] = new IntHashSet();
               }

               builder[cls].add(b);
            }
         }
      }

      int[][] result = new int[builder.length][];

      for (int v = 0; v < result.length; v++) {
         if (builder[v] != null) {
            result[v] = builder[v].toArray();
            Arrays.sort(result[v]);
         }
      }

      return result;
   }

   private static Phi[] findPhis(Program program, int[] variableClasses) {
      Phi[] result = new Phi[program.variableCount()];

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Phi phi : block.getPhis()) {
            result[variableClasses[phi.getReceiver().getIndex()]] = phi;
         }
      }

      return result;
   }

   boolean[] find() {
      for (int v = 0; v < this.variableCount; v++) {
         this.isAutoSpilled(v);
      }

      return this.autoSpilled;
   }

   private boolean isAutoSpilled(int v) {
      v = this.variableClasses[v];
      if (this.status[v] == 2) {
         return this.autoSpilled[v];
      }

      if (this.status[v] == 1) {
         return false;
      }

      Phi definingPhi = this.definingPhis[v];
      if (definingPhi == null) {
         this.status[v] = 2;
         return false;
      }

      this.status[v] = 1;
      boolean result = true;

      for (Incoming incoming : definingPhi.getIncomings()) {
         if (this.colors[incoming.getValue().getIndex()] != this.colors[definingPhi.getReceiver().getIndex()]) {
            result = false;
            break;
         }

         if (!this.isAutoSpilled(incoming.getValue().getIndex())) {
            int[] spilledAt = this.variableSpilledBlocks[incoming.getValue().getIndex()];
            result = false;
            if (spilledAt != null) {
               for (int spilledAtBlock : spilledAt) {
                  if (this.dom.dominates(spilledAtBlock, incoming.getSource().getIndex())) {
                     result = true;
                     break;
                  }
               }
            }

            if (!result) {
               break;
            }
         }
      }

      this.autoSpilled[v] = result;
      this.status[v] = 2;
      return result;
   }
}
