package org.teavm.model.util;

import org.teavm.common.GraphSplittingBackend;
import org.teavm.hppc.IntIntHashMap;
import org.teavm.hppc.IntIntMap;
import org.teavm.model.BasicBlock;
import org.teavm.model.Program;

public class ProgramNodeSplittingBackend implements GraphSplittingBackend {
   private Program program;

   public ProgramNodeSplittingBackend(Program program) {
      this.program = program;
   }

   @Override
   public int[] split(int[] remaining, int[] toCopy) {
      int[] copies = new int[toCopy.length];
      IntIntMap map = new IntIntHashMap();

      for (int i = 0; i < toCopy.length; i++) {
         int node = toCopy[i];
         BasicBlock block = this.program.basicBlockAt(node);
         BasicBlock blockCopy = this.program.createBasicBlock();
         ProgramUtils.copyBasicBlock(block, blockCopy);
         copies[i] = blockCopy.getIndex();
         map.put(toCopy[i], copies[i] + 1);
      }

      BasicBlockMapper copyBlockMapper = new BasicBlockMapper(blockx -> {
         int mappedIndex = map.get(blockx);
         return mappedIndex == 0 ? blockx : mappedIndex - 1;
      });

      for (int copy : copies) {
         copyBlockMapper.transform(this.program.basicBlockAt(copy));
      }

      for (int domainNode : remaining) {
         copyBlockMapper.transform(this.program.basicBlockAt(domainNode));
      }

      return copies;
   }
}
