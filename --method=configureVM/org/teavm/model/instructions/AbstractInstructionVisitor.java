package org.teavm.model.instructions;

import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;

public abstract class AbstractInstructionVisitor implements InstructionVisitor {
   @Override
   public void visit(EmptyInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(CastInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(BranchingInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(BinaryBranchingInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(JumpInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(SwitchInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(ExitInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(RaiseInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(ConstructInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(PutFieldInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(CloneArrayInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(GetElementInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(PutElementInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(InvokeInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(InvokeDynamicInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(InitClassInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(MonitorEnterInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(MonitorExitInstruction insn) {
      this.visitDefault(insn);
   }

   @Override
   public void visit(BoundCheckInstruction insn) {
      this.visitDefault(insn);
   }

   public void visitDefault(Instruction insn) {
   }
}
