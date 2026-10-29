package org.teavm.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.util.TransitionExtractor;

public class BasicBlock implements BasicBlockReader, Iterable<Instruction> {
   private Program program;
   private int index;
   private List<Phi> phis;
   private List<TryCatchBlock> tryCatchBlocks;
   private Variable exceptionVariable;
   private String label;
   Instruction firstInstruction;
   Instruction lastInstruction;
   int cachedSize;
   private List<Phi> safePhis = new AbstractList<Phi>() {
      public Phi get(int index) {
         if (BasicBlock.this.phis == null) {
            throw new IndexOutOfBoundsException();
         } else {
            return BasicBlock.this.phis.get(index);
         }
      }

      @Override
      public int size() {
         return BasicBlock.this.phis != null ? BasicBlock.this.phis.size() : 0;
      }

      public void add(int index, Phi e) {
         if (e.getBasicBlock() != null) {
            throw new IllegalArgumentException("This phi is already in some basic block");
         }

         e.setBasicBlock(BasicBlock.this);
         if (BasicBlock.this.phis == null) {
            BasicBlock.this.phis = new ArrayList<>(1);
         }

         BasicBlock.this.phis.add(index, e);
      }

      public Phi set(int index, Phi element) {
         if (element.getBasicBlock() != null) {
            throw new IllegalArgumentException("This phi is already in some basic block");
         }

         if (BasicBlock.this.phis == null) {
            BasicBlock.this.phis = new ArrayList<>(1);
         }

         Phi oldPhi = BasicBlock.this.phis.get(index);
         oldPhi.setBasicBlock(null);
         element.setBasicBlock(BasicBlock.this);
         return BasicBlock.this.phis.set(index, element);
      }

      public Phi remove(int index) {
         if (BasicBlock.this.phis == null) {
            throw new IndexOutOfBoundsException();
         }

         Phi phi = BasicBlock.this.phis.remove(index);
         phi.setBasicBlock(null);
         return phi;
      }

      @Override
      public void clear() {
         if (BasicBlock.this.phis != null) {
            for (Phi phi : BasicBlock.this.phis) {
               phi.setBasicBlock(null);
            }

            BasicBlock.this.phis = null;
         }
      }
   };
   private List<Phi> immutablePhis = new AbstractList<Phi>() {
      public Phi get(int index) {
         if (BasicBlock.this.phis == null) {
            throw new IndexOutOfBoundsException();
         } else {
            return BasicBlock.this.phis.get(index);
         }
      }

      @Override
      public int size() {
         return BasicBlock.this.phis != null ? BasicBlock.this.phis.size() : 0;
      }
   };
   private List<TryCatchBlock> immutableTryCatchBlocks = new AbstractList<TryCatchBlock>() {
      public TryCatchBlock get(int index) {
         if (BasicBlock.this.tryCatchBlocks == null) {
            throw new IndexOutOfBoundsException();
         } else {
            return BasicBlock.this.tryCatchBlocks.get(index);
         }
      }

      @Override
      public int size() {
         return BasicBlock.this.tryCatchBlocks != null ? BasicBlock.this.tryCatchBlocks.size() : 0;
      }
   };
   private List<TryCatchBlock> safeTryCatchBlocks = new AbstractList<TryCatchBlock>() {
      public TryCatchBlock get(int index) {
         if (BasicBlock.this.tryCatchBlocks == null) {
            throw new IndexOutOfBoundsException();
         } else {
            return BasicBlock.this.tryCatchBlocks.get(index);
         }
      }

      @Override
      public int size() {
         return BasicBlock.this.tryCatchBlocks != null ? BasicBlock.this.tryCatchBlocks.size() : 0;
      }

      public void add(int index, TryCatchBlock element) {
         if (element.protectedBlock == BasicBlock.this) {
            throw new IllegalStateException("This try/catch block is already added to basic block");
         }

         element.protectedBlock = BasicBlock.this;
         if (BasicBlock.this.tryCatchBlocks == null) {
            BasicBlock.this.tryCatchBlocks = new ArrayList<>(1);
         }

         BasicBlock.this.tryCatchBlocks.add(index, element);
      }

      public TryCatchBlock remove(int index) {
         if (BasicBlock.this.tryCatchBlocks == null) {
            throw new IndexOutOfBoundsException();
         }

         TryCatchBlock tryCatch = BasicBlock.this.tryCatchBlocks.remove(index);
         tryCatch.protectedBlock = null;
         return tryCatch;
      }

      public TryCatchBlock set(int index, TryCatchBlock element) {
         TryCatchBlock oldTryCatch = BasicBlock.this.tryCatchBlocks != null ? BasicBlock.this.tryCatchBlocks.get(index) : null;
         if (oldTryCatch == element) {
            return oldTryCatch;
         }

         if (element.protectedBlock == BasicBlock.this) {
            throw new IllegalStateException("This try/catch block is already added to basic block");
         }

         oldTryCatch.protectedBlock = null;
         element.protectedBlock = BasicBlock.this;
         if (BasicBlock.this.tryCatchBlocks == null) {
            BasicBlock.this.tryCatchBlocks = new ArrayList<>(1);
         }

         BasicBlock.this.tryCatchBlocks.set(index, element);
         return oldTryCatch;
      }

      @Override
      public void clear() {
         if (BasicBlock.this.tryCatchBlocks != null) {
            for (TryCatchBlock tryCatch : BasicBlock.this.tryCatchBlocks) {
               tryCatch.protectedBlock = null;
            }

            BasicBlock.this.tryCatchBlocks = null;
         }
      }
   };

   BasicBlock(Program program, int index) {
      this.program = program;
      this.index = index;
   }

   public Program getProgram() {
      return this.program;
   }

   @Override
   public int getIndex() {
      return this.index;
   }

   void setIndex(int index) {
      this.index = index;
   }

   void clearProgram() {
      this.program = null;
   }

   public Instruction getFirstInstruction() {
      return this.firstInstruction;
   }

   public Instruction getLastInstruction() {
      return this.lastInstruction;
   }

   @Override
   public Iterator<Instruction> iterator() {
      return new Iterator<Instruction>() {
         Instruction instruction = BasicBlock.this.firstInstruction;
         private boolean removed;

         @Override
         public boolean hasNext() {
            return this.instruction != null;
         }

         public Instruction next() {
            if (this.instruction == null) {
               throw new NoSuchElementException();
            }

            Instruction result = this.instruction;
            this.instruction = this.instruction.next;
            this.removed = false;
            return result;
         }

         @Override
         public void remove() {
            if (this.removed) {
               throw new IllegalStateException();
            }

            if (this.instruction == null) {
               throw new NoSuchElementException();
            }

            Instruction next = this.instruction.next;
            this.instruction.delete();
            this.instruction = next;
            this.removed = true;
         }
      };
   }

   public void addFirst(Instruction instruction) {
      instruction.checkAddable();
      if (this.firstInstruction == null) {
         this.firstInstruction = instruction;
         this.lastInstruction = instruction;
      } else {
         instruction.next = this.firstInstruction;
         this.firstInstruction.previous = instruction;
         this.firstInstruction = instruction;
      }

      this.cachedSize++;
      instruction.basicBlock = this;
   }

   public void addAll(Iterable<Instruction> instructions) {
      for (Instruction instruction : instructions) {
         this.add(instruction);
      }
   }

   public void add(Instruction instruction) {
      instruction.checkAddable();
      if (this.firstInstruction == null) {
         this.firstInstruction = instruction;
         this.lastInstruction = instruction;
      } else {
         instruction.previous = this.lastInstruction;
         this.lastInstruction.next = instruction;
         this.lastInstruction = instruction;
      }

      this.cachedSize++;
      instruction.basicBlock = this;
   }

   public void addFirstAll(Iterable<Instruction> instructions) {
      Iterator<Instruction> iterator = instructions.iterator();
      if (iterator.hasNext()) {
         Instruction last = iterator.next();
         this.addFirst(last);

         while (iterator.hasNext()) {
            Instruction insn = iterator.next();
            last.insertNext(insn);
            last = insn;
         }
      }
   }

   public void removeAllInstructions() {
      Instruction instruction = this.firstInstruction;

      while (instruction != null) {
         Instruction next = instruction.next;
         instruction.previous = null;
         instruction.next = null;
         instruction.basicBlock = null;
         instruction = next;
      }

      this.firstInstruction = null;
      this.lastInstruction = null;
      this.cachedSize = 0;
   }

   public List<Phi> getPhis() {
      return this.safePhis;
   }

   @Override
   public List<? extends PhiReader> readPhis() {
      return this.immutablePhis;
   }

   @Override
   public int instructionCount() {
      return this.cachedSize;
   }

   @Override
   public InstructionIterator iterateInstructions() {
      return new InstructionIterator() {
         TextLocation location;
         Instruction instruction = BasicBlock.this.firstInstruction;
         Instruction readInstruction;
         InstructionReadVisitor visitor = new InstructionReadVisitor(null);

         @Override
         public boolean hasNext() {
            return this.instruction != null;
         }

         @Override
         public void next() {
            this.readInstruction = this.instruction;
            this.instruction = this.instruction.next;
         }

         @Override
         public boolean hasPrevious() {
            return this.instruction != null && this.instruction.previous != null;
         }

         @Override
         public void previous() {
            this.instruction = this.instruction.previous;
            this.readInstruction = this.instruction;
         }

         @Override
         public void read(InstructionReader reader) {
            this.visitor.reader = reader;
            if (!Objects.equals(this.readInstruction.getLocation(), this.location)) {
               this.location = this.readInstruction.getLocation();
               reader.location(this.location);
            }

            this.readInstruction.acceptVisitor(this.visitor);
            this.visitor.reader = null;
         }
      };
   }

   @Override
   public void readAllInstructions(InstructionReader reader) {
      InstructionReadVisitor visitor = new InstructionReadVisitor(reader);
      TextLocation location = null;

      for (Instruction insn : this) {
         if (!Objects.equals(location, insn.getLocation())) {
            location = insn.getLocation();
            reader.location(location);
         }

         insn.acceptVisitor(visitor);
      }
   }

   public void removeIncomingsFrom(BasicBlock predecessor) {
      for (Phi phi : this.getPhis()) {
         List<Incoming> incomings = phi.getIncomings();

         for (int i = 0; i < incomings.size(); i++) {
            if (incomings.get(i).getSource() == predecessor) {
               incomings.remove(i--);
            }
         }
      }
   }

   @Override
   public List<TryCatchBlock> readTryCatchBlocks() {
      return this.immutableTryCatchBlocks;
   }

   public List<TryCatchBlock> getTryCatchBlocks() {
      return this.safeTryCatchBlocks;
   }

   public Variable getExceptionVariable() {
      return this.exceptionVariable;
   }

   public void setExceptionVariable(Variable exceptionVariable) {
      this.exceptionVariable = exceptionVariable;
   }

   public String getLabel() {
      return this.label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public void detachSuccessors() {
      Instruction lastInstruction = this.getLastInstruction();
      if (lastInstruction != null) {
         TransitionExtractor transitionExtractor = new TransitionExtractor();
         lastInstruction.acceptVisitor(transitionExtractor);
         if (transitionExtractor.getTargets() != null) {
            for (BasicBlock successor : transitionExtractor.getTargets()) {
               List<Phi> phis = successor.getPhis();

               for (int i = 0; i < phis.size(); i++) {
                  Phi phi = phis.get(i);

                  for (int j = 0; j < phi.getIncomings().size(); j++) {
                     if (phi.getIncomings().get(j).getSource() == this) {
                        phi.getIncomings().remove(j--);
                     }
                  }

                  if (phi.getIncomings().isEmpty()) {
                     phis.remove(i--);
                  }
               }
            }
         }
      }
   }
}
