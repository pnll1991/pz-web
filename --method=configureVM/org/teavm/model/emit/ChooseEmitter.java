package org.teavm.model.emit;

import org.teavm.model.BasicBlock;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;

public class ChooseEmitter {
   private ProgramEmitter pe;
   private SwitchInstruction insn;
   private BasicBlock joinBlock;

   ChooseEmitter(ProgramEmitter pe, SwitchInstruction insn, BasicBlock joinBlock) {
      this.pe = pe;
      this.insn = insn;
      this.joinBlock = joinBlock;
      insn.setDefaultTarget(joinBlock);
   }

   public ChooseEmitter option(int value, FragmentEmitter fragment) {
      SwitchTableEntry entry = new SwitchTableEntry();
      entry.setCondition(value);
      entry.setTarget(this.pe.prepareBlock());
      this.pe.enter(entry.getTarget());
      this.pe.emitAndJump(fragment, this.joinBlock);
      this.pe.enter(this.joinBlock);
      this.insn.getEntries().add(entry);
      return this;
   }

   public ProgramEmitter otherwise(FragmentEmitter fragment) {
      this.insn.setDefaultTarget(this.pe.prepareBlock());
      this.pe.enter(this.insn.getDefaultTarget());
      this.pe.emitAndJump(fragment, this.joinBlock);
      this.pe.enter(this.joinBlock);
      return this.pe;
   }

   public FragmentEmitter breakChoise() {
      return () -> this.pe.jump(this.joinBlock);
   }
}
