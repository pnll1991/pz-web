package org.teavm.model.transformation;

import java.util.function.BiConsumer;
import java.util.function.Function;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.DominatorWalker;
import org.teavm.model.util.DominatorWalkerCallback;
import org.teavm.model.util.PhiUpdater;

public class NullCheckInsertion {
   private NullCheckFilter filter;

   public NullCheckInsertion(NullCheckFilter filter) {
      this.filter = filter;
   }

   public void transformProgram(Program program, MethodReference methodReference) {
      if (this.filter.apply(methodReference) && program.basicBlockCount() != 0) {
         NullCheckInsertion.InsertionVisitor visitor = new NullCheckInsertion.InsertionVisitor(program.variableCount());
         new DominatorWalker(program).walk(visitor);
         if (visitor.changed) {
            new PhiUpdater().updatePhis(program, methodReference.parameterCount() + 1);
         }
      }
   }

   static class BlockNullness {
      IntSet notNullVariables = new IntHashSet();
   }

   class InsertionVisitor extends AbstractInstructionVisitor implements DominatorWalkerCallback<NullCheckInsertion.BlockNullness> {
      boolean changed;
      boolean[] notNullVariables;
      NullCheckInsertion.BlockNullness blockNullness;

      InsertionVisitor(int variableCount) {
         this.notNullVariables = new boolean[variableCount];
         if (variableCount > 0) {
            this.notNullVariables[0] = true;
         }
      }

      public NullCheckInsertion.BlockNullness visit(BasicBlock block) {
         this.blockNullness = new NullCheckInsertion.BlockNullness();
         if (block.getExceptionVariable() != null) {
            this.notNullVariables[block.getExceptionVariable().getIndex()] = true;
            this.blockNullness.notNullVariables.add(block.getExceptionVariable().getIndex());
         }

         for (Instruction instruction : block) {
            instruction.acceptVisitor(this);
         }

         return this.blockNullness;
      }

      public void endVisit(BasicBlock block, NullCheckInsertion.BlockNullness state) {
         for (int variable : state.notNullVariables.toArray()) {
            this.notNullVariables[variable] = false;
         }
      }

      @Override
      public void visit(RaiseInstruction insn) {
         this.addGuard(insn, RaiseInstruction::getException, RaiseInstruction::setException);
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (NullCheckInsertion.this.filter.apply(insn.getField())) {
            this.addGuard(insn, GetFieldInstruction::getInstance, GetFieldInstruction::setInstance);
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (NullCheckInsertion.this.filter.apply(insn.getField())) {
            this.addGuard(insn, PutFieldInstruction::getInstance, PutFieldInstruction::setInstance);
         }
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.addGuard(insn, CloneArrayInstruction::getArray, CloneArrayInstruction::setArray);
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.addGuard(insn, UnwrapArrayInstruction::getArray, UnwrapArrayInstruction::setArray);
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (NullCheckInsertion.this.filter.apply(insn.getMethod())) {
            this.addGuard(insn, InvokeInstruction::getInstance, InvokeInstruction::setInstance);
         }
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         this.addGuard(insn, MonitorEnterInstruction::getObjectRef, MonitorEnterInstruction::setObjectRef);
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         this.addGuard(insn, MonitorExitInstruction::getObjectRef, MonitorExitInstruction::setObjectRef);
      }

      private <T extends Instruction> void addGuard(T instruction, Function<T, Variable> get, BiConsumer<T, Variable> set) {
         Variable value = get.apply(instruction);
         if (value != null) {
            if (!this.notNullVariables[value.getIndex()]) {
               this.notNullVariables[value.getIndex()] = true;
               this.blockNullness.notNullVariables.add(value.getIndex());
               NullCheckInstruction nullCheck = new NullCheckInstruction();
               nullCheck.setValue(value);
               nullCheck.setReceiver(value);
               nullCheck.setLocation(instruction.getLocation());
               set.accept(instruction, nullCheck.getReceiver());
               instruction.insertPrevious(nullCheck);
               this.changed = true;
            }
         }
      }
   }
}
