package org.teavm.model;

import java.util.ArrayList;
import java.util.List;

public class Program implements ProgramReader {
   private List<BasicBlock> basicBlocks = new ArrayList<>(2);
   private List<Variable> variables = new ArrayList<>();
   private boolean packed;
   private int lastUsedRegister;
   private AnnotationContainer annotations = new AnnotationContainer();

   public BasicBlock createBasicBlock() {
      BasicBlock block = new BasicBlock(this, this.basicBlocks.size());
      this.basicBlocks.add(block);
      return block;
   }

   public Variable createVariable() {
      Variable variable = new Variable(this);
      variable.setIndex(this.variables.size());
      this.variables.add(variable);
      variable.setRegister(this.lastUsedRegister++);
      return variable;
   }

   public void deleteBasicBlock(int index) {
      BasicBlock basicBlock = this.basicBlocks.get(index);
      if (basicBlock != null) {
         this.basicBlocks.set(index, null);
         basicBlock.setIndex(-1);
         basicBlock.clearProgram();
         this.packed = false;
      }
   }

   @Override
   public int basicBlockCount() {
      return this.basicBlocks.size();
   }

   public BasicBlock basicBlockAt(int index) {
      return this.basicBlocks.get(index);
   }

   @Override
   public Iterable<BasicBlock> getBasicBlocks() {
      return this.basicBlocks;
   }

   public void rearrangeBasicBlocks(List<BasicBlock> basicBlocks) {
      if (!this.isPacked()) {
         throw new IllegalStateException("This operation is not supported on unpacked programs");
      }

      if (basicBlocks.size() != this.basicBlocks.size()) {
         throw new IllegalArgumentException("New list of basic blocks has wrong size (" + basicBlocks.size() + ", expected " + this.basicBlockCount() + ")");
      }

      boolean[] indexes = new boolean[basicBlocks.size()];

      for (BasicBlock block : basicBlocks) {
         if (block.getProgram() != this) {
            throw new IllegalArgumentException("The list of basic blocks contains a basic block from another program");
         }

         if (indexes[block.getIndex()]) {
            throw new IllegalArgumentException("The list of basic blocks contains same basic block twice");
         }

         indexes[block.getIndex()] = true;
      }

      this.basicBlocks.clear();
      this.basicBlocks.addAll(basicBlocks);

      for (int i = 0; i < this.basicBlocks.size(); i++) {
         this.basicBlocks.get(i).setIndex(i);
      }
   }

   public void deleteVariable(int index) {
      Variable variable = this.variables.get(index);
      if (variable != null) {
         this.variables.set(index, null);
         variable.setIndex(-1);
         variable.setProgram(null);
         this.packed = false;
      }
   }

   public boolean isPacked() {
      return this.packed;
   }

   public void pack() {
      if (!this.packed) {
         int sz = 0;

         for (int i = 0; i < this.basicBlocks.size(); i++) {
            BasicBlock block = this.basicBlocks.get(i);
            if (block != null) {
               block.setIndex(sz);
               this.basicBlocks.set(sz++, block);
            }
         }

         while (this.basicBlocks.size() > sz) {
            this.basicBlocks.remove(this.basicBlocks.size() - 1);
         }

         sz = 0;

         for (int i = 0; i < this.variables.size(); i++) {
            Variable var = this.variables.get(i);
            if (var != null) {
               var.setIndex(sz);
               this.variables.set(sz++, var);
            }
         }

         while (this.variables.size() > sz) {
            this.variables.remove(this.variables.size() - 1);
         }

         this.packed = true;
      }
   }

   @Override
   public int variableCount() {
      return this.variables.size();
   }

   public Variable variableAt(int index) {
      if (index >= 0 && index < this.variables.size()) {
         return this.variables.get(index);
      } else {
         throw new IllegalArgumentException("Index " + index + " is out of range");
      }
   }

   public AnnotationContainer getAnnotations() {
      return this.annotations;
   }
}
