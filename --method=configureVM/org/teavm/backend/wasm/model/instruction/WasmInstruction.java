package org.teavm.backend.wasm.model.instruction;

import org.teavm.model.TextLocation;

public abstract class WasmInstruction {
   WasmInstructionList owner;
   WasmInstruction next;
   WasmInstruction previous;
   private TextLocation location;

   public WasmInstructionList getOwner() {
      return this.owner;
   }

   public WasmInstruction getNext() {
      return this.next;
   }

   public WasmInstruction getPrevious() {
      return this.previous;
   }

   public TextLocation getLocation() {
      return this.location;
   }

   public void setLocation(TextLocation location) {
      this.location = location;
   }

   public abstract void acceptVisitor(WasmInstructionVisitor var1);

   public boolean delete() {
      if (this.owner == null) {
         return false;
      }

      if (this.next != null) {
         this.next.previous = this.previous;
      } else {
         this.owner.last = this.previous;
      }

      if (this.previous != null) {
         this.previous.next = this.next;
      } else {
         this.owner.first = this.next;
      }

      this.owner = null;
      this.next = null;
      this.previous = null;
      return true;
   }

   public void insertNext(WasmInstruction other) {
      this.checkInList();
      other.checkAddable();
      if (this.next != null) {
         this.next.previous = other;
      } else {
         this.owner.last = other;
      }

      other.next = this.next;
      other.previous = this;
      this.next = other;
      other.owner = this.owner;
   }

   public void insertNext(WasmInstructionList other) {
      this.checkInList();
      WasmInstruction insn = this;

      while (other.getFirst() != null) {
         WasmInstruction next = other.getFirst();
         next.delete();
         insn.insertNext(next);
         insn = next;
      }
   }

   public void insertPrevious(WasmInstruction other) {
      this.checkInList();
      other.checkAddable();
      if (this.previous != null) {
         this.previous.next = other;
      } else {
         this.owner.first = other;
      }

      other.previous = this.previous;
      other.next = this;
      this.previous = other;
      other.owner = this.owner;
   }

   private void checkInList() {
      if (this.owner == null) {
         throw new IllegalStateException("This instruction is not in a list");
      }
   }

   void checkAddable() {
      if (this.owner != null) {
         throw new IllegalArgumentException("This instruction is already in a list");
      }
   }

   public boolean isSuspend() {
      return false;
   }

   public boolean isTerminating() {
      return false;
   }

   public static <T extends WasmInstruction> T withLocation(T insn, TextLocation location) {
      insn.setLocation(location);
      return insn;
   }
}
