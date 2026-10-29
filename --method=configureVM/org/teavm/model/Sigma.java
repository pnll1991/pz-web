package org.teavm.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Sigma {
   private BasicBlock basicBlock;
   private Variable value;
   private List<Outgoing> outgoings = new ArrayList<>();
   private List<Outgoing> safeOutgoings = new AbstractList<Outgoing>() {
      public Outgoing get(int index) {
         return Sigma.this.outgoings.get(index);
      }

      public Outgoing set(int index, Outgoing element) {
         if (element.getSigma() != null) {
            throw new IllegalArgumentException("This outgoing is already in some sigma");
         }

         element.setSigma(Sigma.this);
         Outgoing old = Sigma.this.outgoings.get(index);
         old.setSigma(null);
         Sigma.this.outgoings.set(index, element);
         return old;
      }

      public void add(int index, Outgoing element) {
         if (element.getSigma() != null) {
            throw new IllegalArgumentException("This outgoing is already in some sigma");
         }

         element.setSigma(Sigma.this);
         Sigma.this.outgoings.add(index, element);
      }

      public Outgoing remove(int index) {
         Outgoing outgoing = Sigma.this.outgoings.remove(index);
         outgoing.setSigma(null);
         return outgoing;
      }

      @Override
      public void clear() {
         for (Outgoing outgoing : Sigma.this.outgoings) {
            outgoing.setSigma(null);
         }

         Sigma.this.outgoings.clear();
      }

      @Override
      public int size() {
         return Sigma.this.outgoings.size();
      }
   };

   public Sigma(BasicBlock basicBlock, Variable value) {
      this.basicBlock = basicBlock;
      this.value = value;
   }

   public BasicBlock getBasicBlock() {
      return this.basicBlock;
   }

   public void setBasicBlock(BasicBlock basicBlock) {
      this.basicBlock = basicBlock;
   }

   public Variable getValue() {
      return this.value;
   }

   public void setValue(Variable value) {
      this.value = value;
   }

   public List<Outgoing> getOutgoings() {
      return this.safeOutgoings;
   }
}
