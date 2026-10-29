package org.teavm.model;

import java.util.Iterator;
import org.teavm.model.instructions.InstructionVisitor;

public abstract class Instruction {
   BasicBlock basicBlock;
   private TextLocation location;
   Instruction next;
   Instruction previous;

   public BasicBlock getBasicBlock() {
      return this.basicBlock;
   }

   public Program getProgram() {
      return this.basicBlock != null ? this.basicBlock.getProgram() : null;
   }

   public TextLocation getLocation() {
      return this.location;
   }

   public void setLocation(TextLocation location) {
      this.location = location;
   }

   public abstract void acceptVisitor(InstructionVisitor var1);

   public Instruction getNext() {
      return this.next;
   }

   public Instruction getPrevious() {
      return this.previous;
   }

   public boolean delete() {
      if (this.basicBlock == null) {
         return false;
      }

      if (this.next != null) {
         this.next.previous = this.previous;
      } else {
         this.basicBlock.lastInstruction = this.previous;
      }

      if (this.previous != null) {
         this.previous.next = this.next;
      } else {
         this.basicBlock.firstInstruction = this.next;
      }

      this.basicBlock.cachedSize--;
      this.basicBlock = null;
      this.next = null;
      this.previous = null;
      return true;
   }

   public boolean replace(Instruction other) {
      this.checkInBasicBlock();
      other.checkAddable();
      if (this.next != null) {
         this.next.previous = other;
      } else {
         this.basicBlock.lastInstruction = other;
      }

      other.next = this.next;
      if (this.previous != null) {
         this.previous.next = other;
      } else {
         this.basicBlock.firstInstruction = other;
      }

      other.previous = this.previous;
      other.basicBlock = this.basicBlock;
      this.basicBlock = null;
      this.next = null;
      this.previous = null;
      return true;
   }

   public void insertNext(Instruction other) {
      this.checkInBasicBlock();
      other.checkAddable();
      if (this.next != null) {
         this.next.previous = other;
      } else {
         this.basicBlock.lastInstruction = other;
      }

      other.next = this.next;
      other.previous = this;
      this.next = other;
      this.basicBlock.cachedSize++;
      other.basicBlock = this.basicBlock;
   }

   public void insertNextAll(Iterable<Instruction> other) {
      Iterator<Instruction> iterator = other.iterator();
      Instruction last = this;

      while (iterator.hasNext()) {
         Instruction insn = iterator.next();
         last.insertNext(insn);
         last = insn;
      }
   }

   public void insertPrevious(Instruction other) {
      this.checkInBasicBlock();
      other.checkAddable();
      if (this.previous != null) {
         this.previous.next = other;
      } else {
         this.basicBlock.firstInstruction = other;
      }

      other.previous = this.previous;
      other.next = this;
      this.previous = other;
      this.basicBlock.cachedSize++;
      other.basicBlock = this.basicBlock;
   }

   public void insertPreviousAll(Iterable<Instruction> other) {
      for (Instruction instruction : other) {
         this.insertPrevious(instruction);
      }
   }

   private void checkInBasicBlock() {
      if (this.getBasicBlock() == null) {
         throw new IllegalArgumentException("This instruction is not in basic block");
      }
   }

   void checkAddable() {
      if (this.getBasicBlock() != null) {
         throw new IllegalArgumentException("This instruction is in some basic block");
      }
   }
}
