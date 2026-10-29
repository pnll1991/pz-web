package org.teavm.model.emit;

import org.teavm.model.BasicBlock;

public class ConditionEmitter {
   private ProgramEmitter pe;
   ForkEmitter fork;

   ConditionEmitter(ProgramEmitter pe, ForkEmitter fork) {
      this.pe = pe;
      this.fork = fork;
   }

   public ConditionEmitter and(ConditionProducer other) {
      BasicBlock block = this.pe.prepareBlock();
      this.pe.enter(block);
      ConditionEmitter otherEmitter = other.produce();
      ForkEmitter newFork = this.fork.and(block, otherEmitter.fork);
      return new ConditionEmitter(this.pe, newFork);
   }

   public ConditionEmitter or(ConditionProducer other) {
      BasicBlock block = this.pe.prepareBlock();
      this.pe.enter(block);
      ConditionEmitter otherEmitter = other.produce();
      ForkEmitter newFork = this.fork.or(block, otherEmitter.fork);
      return new ConditionEmitter(this.pe, newFork);
   }

   public ConditionEmitter not() {
      return new ConditionEmitter(this.pe, this.fork.not());
   }
}
