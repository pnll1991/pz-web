package org.teavm.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Phi implements PhiReader {
   private BasicBlock basicBlock;
   private Variable receiver;
   private List<Incoming> incomings = new ArrayList<>(2);
   private List<Incoming> safeIncomings = new AbstractList<Incoming>() {
      public Incoming get(int index) {
         return Phi.this.incomings.get(index);
      }

      public Incoming set(int index, Incoming element) {
         if (element.getPhi() != null) {
            throw new IllegalArgumentException("This incoming is already in some phi");
         }

         element.setPhi(Phi.this);
         Incoming old = Phi.this.incomings.get(index);
         old.setPhi(null);
         Phi.this.incomings.set(index, element);
         return old;
      }

      public void add(int index, Incoming element) {
         if (element.getPhi() != null) {
            throw new IllegalArgumentException("This incoming is already in some phi");
         }

         element.setPhi(Phi.this);
         Phi.this.incomings.add(index, element);
      }

      public Incoming remove(int index) {
         Incoming incoming = Phi.this.incomings.remove(index);
         incoming.setPhi(null);
         return incoming;
      }

      @Override
      public void clear() {
         for (Incoming incoming : Phi.this.incomings) {
            incoming.setPhi(null);
         }

         Phi.this.incomings.clear();
      }

      @Override
      public int size() {
         return Phi.this.incomings.size();
      }
   };
   private List<? extends IncomingReader> immutableIncomings = Collections.unmodifiableList(this.incomings);

   public BasicBlock getBasicBlock() {
      return this.basicBlock;
   }

   void setBasicBlock(BasicBlock basicBlock) {
      this.basicBlock = basicBlock;
   }

   public Variable getReceiver() {
      return this.receiver;
   }

   public void setReceiver(Variable receiver) {
      this.receiver = receiver;
   }

   public List<Incoming> getIncomings() {
      return this.safeIncomings;
   }

   @Override
   public List<? extends IncomingReader> readIncomings() {
      return this.immutableIncomings;
   }
}
