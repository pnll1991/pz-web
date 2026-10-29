package org.teavm.model.emit;

import java.util.HashMap;
import java.util.Map;
import org.teavm.model.BasicBlock;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.SwitchTableEntry;

public class StringChooseEmitter {
   private ProgramEmitter pe;
   private ValueEmitter testValue;
   private SwitchInstruction insn;
   private BasicBlock joinBlock;
   private Map<Integer, ForkEmitter> hashForks = new HashMap<>();
   private BasicBlock otherwiseBlock;

   StringChooseEmitter(ProgramEmitter pe, ValueEmitter testValue, SwitchInstruction insn, BasicBlock joinBlock) {
      this.pe = pe;
      this.insn = insn;
      this.joinBlock = joinBlock;
      this.otherwiseBlock = pe.prepareBlock();
      this.testValue = testValue;
      insn.setCondition(testValue.invokeVirtual("hashCode", int.class).getVariable());
      insn.setDefaultTarget(this.otherwiseBlock);
      pe.addInstruction(insn);
      pe.enter(this.otherwiseBlock);
      pe.jump(joinBlock);
      pe.enter(joinBlock);
   }

   public StringChooseEmitter option(String value, FragmentEmitter fragment) {
      int hash = value.hashCode();
      BasicBlock block = this.pe.prepareBlock();
      ForkEmitter fork = this.hashForks.get(hash);
      if (fork == null) {
         SwitchTableEntry entry = new SwitchTableEntry();
         entry.setCondition(hash);
         entry.setTarget(block);
         this.pe.enter(entry.getTarget());
         this.insn.getEntries().add(entry);
      } else {
         fork.setElse(block);
      }

      this.pe.enter(block);
      fork = this.testValue.invokeVirtual("equals", boolean.class, this.pe.constant(value).cast(Object.class)).fork(BranchingCondition.NOT_EQUAL);
      this.hashForks.put(hash, fork);
      block = this.pe.prepareBlock();
      fork.setThen(block);
      fork.setElse(this.otherwiseBlock);
      this.pe.enter(block);
      this.pe.emitAndJump(fragment, this.joinBlock);
      this.pe.enter(this.joinBlock);
      return this;
   }

   public ProgramEmitter otherwise(FragmentEmitter fragment) {
      this.otherwiseBlock.removeAllInstructions();
      this.pe.enter(this.otherwiseBlock);
      this.pe.emitAndJump(fragment, this.joinBlock);
      this.pe.enter(this.joinBlock);
      return this.pe;
   }
}
