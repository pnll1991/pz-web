package org.teavm.backend.wasm.debug.info;

import java.util.List;
import org.teavm.common.CollectionUtil;

public class LineInfoUnpackedSequence {
   private int startAddress;
   private int endAddress;
   private MethodInfo method;
   private List<? extends InstructionLocation> locations;

   LineInfoUnpackedSequence(int startAddress, int endAddress, MethodInfo method, List<? extends InstructionLocation> locations) {
      this.startAddress = startAddress;
      this.endAddress = endAddress;
      this.method = method;
      this.locations = locations;
   }

   public int startAddress() {
      return this.startAddress;
   }

   public int endAddress() {
      return this.endAddress;
   }

   public MethodInfo method() {
      return this.method;
   }

   public List<? extends InstructionLocation> locations() {
      return this.locations;
   }

   public InstructionLocation find(int address) {
      int index = this.findIndex(address);
      return index >= 0 ? this.locations.get(index) : null;
   }

   public int findIndex(int address) {
      if (address >= this.startAddress && address < this.endAddress && !this.locations.isEmpty()) {
         int index = CollectionUtil.binarySearch(this.locations, address, InstructionLocation::address);
         if (index < 0) {
            index = -index - 2;
         }

         return index;
      } else {
         return -1;
      }
   }
}
