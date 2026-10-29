package org.teavm.model.emit;

import org.teavm.model.BasicBlock;

public abstract class ForkEmitter {
   private ProgramEmitter pe;

   public ForkEmitter(ProgramEmitter pe) {
      this.pe = pe;
   }

   public abstract ForkEmitter setThen(BasicBlock var1);

   public abstract ForkEmitter setElse(BasicBlock var1);

   public ForkEmitter and(BasicBlock block, final ForkEmitter other) {
      this.setThen(block);
      return new ForkEmitter(this.pe) {
         @Override
         public ForkEmitter setThen(BasicBlock block) {
            other.setThen(block);
            return this;
         }

         @Override
         public ForkEmitter setElse(BasicBlock block) {
            ForkEmitter.this.setElse(block);
            other.setElse(block);
            return this;
         }
      };
   }

   public ForkEmitter or(BasicBlock block, final ForkEmitter other) {
      this.setElse(block);
      return new ForkEmitter(this.pe) {
         @Override
         public ForkEmitter setThen(BasicBlock block) {
            ForkEmitter.this.setThen(block);
            other.setThen(block);
            return this;
         }

         @Override
         public ForkEmitter setElse(BasicBlock block) {
            other.setElse(block);
            return this;
         }
      };
   }

   public ForkEmitter not() {
      return new ForkEmitter(this.pe) {
         @Override
         public ForkEmitter setThen(BasicBlock block) {
            ForkEmitter.this.setElse(block);
            return this;
         }

         @Override
         public ForkEmitter setElse(BasicBlock block) {
            ForkEmitter.this.setThen(block);
            return this;
         }
      };
   }
}
