package org.teavm.model.emit;

import org.teavm.model.BasicBlock;

public class IfEmitter {
   private ProgramEmitter pe;
   private ForkEmitter fork;
   private BasicBlock join;

   IfEmitter(ProgramEmitter pe, ForkEmitter fork, BasicBlock join) {
      this.pe = pe;
      this.fork = fork;
      this.join = join;
      fork.setThen(join);
      fork.setElse(join);
   }

   public IfEmitter thenDo(FragmentEmitter fragment) {
      BasicBlock block = this.pe.prepareBlock();
      this.fork.setThen(block);
      this.pe.enter(block);
      this.pe.emitAndJump(fragment, this.join);
      this.pe.enter(this.join);
      return this;
   }

   public IfEmitter elseDo(FragmentEmitter fragment) {
      BasicBlock block = this.pe.prepareBlock();
      this.fork.setElse(block);
      this.pe.enter(block);
      this.pe.emitAndJump(fragment, this.join);
      this.pe.enter(this.join);
      return this;
   }
}
