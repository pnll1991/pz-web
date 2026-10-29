package org.teavm.model.util;

import org.teavm.model.Instruction;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class AssignmentExtractor extends AbstractInstructionVisitor {
   private Variable result;

   public Variable getResult() {
      return this.result;
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(CastInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(InvokeInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(BoundCheckInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(GetElementInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(CloneArrayInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(ConstructInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      this.result = insn.getReceiver();
   }

   @Override
   public void visitDefault(Instruction insn) {
      this.result = null;
   }
}
