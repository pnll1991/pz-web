package org.teavm.model.optimization;

import org.teavm.model.analysis.NullnessInformation;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class LoopInvariantAnalyzer extends AbstractInstructionVisitor {
   private NullnessInformation nullness;
   public boolean canMove;
   public boolean constant;
   public boolean sideEffect;

   public LoopInvariantAnalyzer(NullnessInformation nullness) {
      this.nullness = nullness;
   }

   public void reset() {
      this.canMove = false;
      this.constant = false;
      this.sideEffect = false;
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.constant = true;
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.canMove = true;
      if (insn.getOperation() == BinaryOperation.DIVIDE) {
         this.sideEffect = insn.getOperandType() == NumericOperandType.INT || insn.getOperandType() == NumericOperandType.LONG;
      }
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.canMove = true;
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.canMove = true;
   }

   @Override
   public void visit(CastInstruction insn) {
      this.canMove = true;
      this.sideEffect = true;
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.canMove = true;
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.canMove = true;
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.canMove = true;
      if (!this.nullness.isNotNull(insn.getArray())) {
         this.sideEffect = true;
      }
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.canMove = true;
      if (!this.nullness.isNotNull(insn.getArray())) {
         this.sideEffect = true;
      }
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.canMove = true;
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.canMove = true;
      if (!this.nullness.isNotNull(insn.getValue())) {
         this.sideEffect = true;
      }
   }
}
