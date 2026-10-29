package org.teavm.backend.wasm.debug.info;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.teavm.common.CollectionUtil;

public class LineInfo {
   private LineInfoSequence[] sequences;
   private List<? extends LineInfoSequence> sequenceList;

   public LineInfo(LineInfoSequence[] sequences) {
      this.sequences = (LineInfoSequence[])sequences.clone();
      this.sequenceList = Collections.unmodifiableList(Arrays.asList(this.sequences));
   }

   public List<? extends LineInfoSequence> sequences() {
      return this.sequenceList;
   }

   public DeobfuscatedLocation[] deobfuscate(int[] addresses) {
      ArrayList<DeobfuscatedLocation> result = new ArrayList<>();

      for (int address : addresses) {
         DeobfuscatedLocation[] part = this.deobfuscateSingle(address);
         if (part != null) {
            result.addAll(List.of(part));
         }
      }

      return result.toArray(new DeobfuscatedLocation[0]);
   }

   public DeobfuscatedLocation[] deobfuscateSingle(int address) {
      LineInfoSequence sequence = this.find(address);
      if (sequence == null) {
         return null;
      }

      InstructionLocation instructionLoc = sequence.unpack().find(address);
      if (instructionLoc == null) {
         return this.returnForSequence(sequence);
      }

      Location location = instructionLoc.location();
      if (location == null) {
         return this.returnForSequence(sequence);
      }

      DeobfuscatedLocation[] result = new DeobfuscatedLocation[location.depth()];
      MethodInfo method = sequence.method();

      int i;
      for (i = 0; i < result.length - 1; location = location.inlining().location()) {
         InliningLocation inlining = location.inlining();
         result[i++] = new DeobfuscatedLocation(location.file(), inlining.method(), location.line());
      }

      result[i] = new DeobfuscatedLocation(location.file(), method, location.line());
      return result;
   }

   private DeobfuscatedLocation[] returnForSequence(LineInfoSequence sequence) {
      return new DeobfuscatedLocation[]{new DeobfuscatedLocation(null, sequence.method(), -1)};
   }

   public LineInfoSequence find(int address) {
      int index = CollectionUtil.binarySearch(this.sequenceList, address, LineInfoSequence::endAddress);
      if (index < 0) {
         index = -index - 1;
      }

      if (index >= this.sequenceList.size()) {
         return null;
      }

      LineInfoSequence sequence = this.sequenceList.get(index);
      return address >= sequence.startAddress() ? sequence : null;
   }

   public void dump(PrintStream out) {
      for (int i = 0; i < this.sequences.length; i++) {
         LineInfoSequence sequence = this.sequences[i];
         out.println(
            "Sequence "
               + i
               + ": "
               + sequence.method().fullName()
               + " ["
               + Integer.toHexString(sequence.startAddress())
               + ".."
               + Integer.toHexString(sequence.endAddress())
               + ")"
         );

         for (InstructionLocation location : sequence.unpack().locations()) {
            out.print("  at " + Integer.toHexString(location.address()) + " ");
            if (location.location() != null) {
               out.println(location.location().file().fullName() + ":" + location.location().line());
            } else {
               out.println("<unmapped>");
            }
         }
      }
   }
}
