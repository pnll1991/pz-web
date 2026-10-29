package org.teavm.backend.wasm.model.instruction;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.teavm.model.TextLocation;

public class WasmInstructionList implements Iterable<WasmInstruction> {
   WasmInstruction breakTarget;
   WasmInstruction first;
   WasmInstruction last;

   public WasmInstructionList() {
      this(null);
   }

   WasmInstructionList(WasmInstruction breakTarget) {
      this.breakTarget = breakTarget;
   }

   public WasmInstruction getFirst() {
      return this.first;
   }

   public WasmInstruction getLast() {
      return this.last;
   }

   public boolean isEmpty() {
      return this.first == null;
   }

   public WasmInstruction getBreakTarget() {
      return this.breakTarget;
   }

   public void clear() {
      for (WasmInstruction insn = this.first; insn != null; insn = insn.next) {
         insn.owner = null;
      }

      this.first = null;
      this.last = null;
   }

   public void add(WasmInstruction instruction) {
      instruction.checkAddable();
      instruction.owner = this;
      if (this.last == null) {
         this.first = instruction;
         this.last = instruction;
      } else {
         this.last.next = instruction;
         instruction.previous = this.last;
         this.last = instruction;
      }
   }

   public void add(WasmInstruction instruction, TextLocation location) {
      this.add(instruction);
      instruction.setLocation(location);
   }

   public void addFirst(WasmInstruction instruction) {
      instruction.checkAddable();
      instruction.owner = this;
      if (this.first == null) {
         this.first = instruction;
         this.last = instruction;
      } else {
         this.first.previous = instruction;
         instruction.next = this.first;
         this.first = instruction;
      }
   }

   public void addFirst(WasmInstructionList src) {
      if (src.first != null) {
         for (WasmInstruction insn = src.first; insn != null; insn = insn.next) {
            insn.owner = this;
         }

         if (this.last == null) {
            this.first = src.first;
         } else {
            this.first.previous = src.last;
         }

         src.first.previous = this.last;
         this.last = src.last;
         src.first = null;
         src.last = null;
      }
   }

   public void transferFrom(WasmInstructionList src) {
      if (src.first != null) {
         for (WasmInstruction insn = src.first; insn != null; insn = insn.next) {
            insn.owner = this;
         }

         if (this.last == null) {
            this.first = src.first;
         } else {
            this.last.next = src.first;
         }

         src.first.previous = this.last;
         this.last = src.last;
         src.first = null;
         src.last = null;
      }
   }

   @Override
   public Iterator<WasmInstruction> iterator() {
      return new Iterator<WasmInstruction>() {
         private WasmInstruction current = WasmInstructionList.this.first;

         @Override
         public boolean hasNext() {
            return this.current != null;
         }

         public WasmInstruction next() {
            if (this.current == null) {
               throw new NoSuchElementException();
            }

            WasmInstruction result = this.current;
            this.current = this.current.next;
            return result;
         }
      };
   }

   public WasmInstructionBuilder builder() {
      return new WasmInstructionBuilder(this);
   }
}
