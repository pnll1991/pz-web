package org.teavm.model;

import java.util.Collections;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.InstructionVisitor;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class InstructionReadVisitor implements InstructionVisitor {
   InstructionReader reader;

   public InstructionReadVisitor(InstructionReader reader) {
      this.reader = reader;
   }

   @Override
   public void visit(EmptyInstruction insn) {
      this.reader.nop();
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.reader.classConstant(insn.getReceiver(), insn.getConstant());
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.reader.nullConstant(insn.getReceiver());
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.reader.integerConstant(insn.getReceiver(), insn.getConstant());
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.reader.longConstant(insn.getReceiver(), insn.getConstant());
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.reader.floatConstant(insn.getReceiver(), insn.getConstant());
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.reader.doubleConstant(insn.getReceiver(), insn.getConstant());
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.reader.stringConstant(insn.getReceiver(), insn.getConstant());
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.reader.binary(insn.getOperation(), insn.getReceiver(), insn.getFirstOperand(), insn.getSecondOperand(), insn.getOperandType());
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.reader.negate(insn.getReceiver(), insn.getOperand(), insn.getOperandType());
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.reader.assign(insn.getReceiver(), insn.getAssignee());
   }

   @Override
   public void visit(CastInstruction insn) {
      this.reader.cast(insn.getReceiver(), insn.getValue(), insn.getTargetType(), insn.isWeak());
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.reader.cast(insn.getReceiver(), insn.getValue(), insn.getSourceType(), insn.getTargetType());
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.reader.cast(insn.getReceiver(), insn.getValue(), insn.getTargetType(), insn.getDirection());
   }

   @Override
   public void visit(BranchingInstruction insn) {
      this.reader.jumpIf(insn.getCondition(), insn.getOperand(), insn.getConsequent(), insn.getAlternative());
   }

   @Override
   public void visit(BinaryBranchingInstruction insn) {
      this.reader.jumpIf(insn.getCondition(), insn.getFirstOperand(), insn.getSecondOperand(), insn.getConsequent(), insn.getAlternative());
   }

   @Override
   public void visit(JumpInstruction insn) {
      this.reader.jump(insn.getTarget());
   }

   @Override
   public void visit(SwitchInstruction insn) {
      this.reader.choose(insn.getCondition(), Collections.unmodifiableList(insn.getEntries()), insn.getDefaultTarget());
   }

   @Override
   public void visit(ExitInstruction insn) {
      this.reader.exit(insn.getValueToReturn());
   }

   @Override
   public void visit(RaiseInstruction insn) {
      this.reader.raise(insn.getException());
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      this.reader.createArray(insn.getReceiver(), insn.getItemType(), insn.getSize());
   }

   @Override
   public void visit(ConstructInstruction insn) {
      this.reader.create(insn.getReceiver(), insn.getType());
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      this.reader.createArray(insn.getReceiver(), insn.getItemType(), Collections.unmodifiableList(insn.getDimensions()));
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      this.reader.getField(insn.getReceiver(), insn.getInstance(), insn.getField(), insn.getFieldType());
   }

   @Override
   public void visit(PutFieldInstruction insn) {
      this.reader.putField(insn.getInstance(), insn.getField(), insn.getValue(), insn.getFieldType());
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.reader.arrayLength(insn.getReceiver(), insn.getArray());
   }

   @Override
   public void visit(CloneArrayInstruction insn) {
      this.reader.cloneArray(insn.getReceiver(), insn.getArray());
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.reader.unwrapArray(insn.getReceiver(), insn.getArray(), insn.getElementType());
   }

   @Override
   public void visit(GetElementInstruction insn) {
      this.reader.getElement(insn.getReceiver(), insn.getArray(), insn.getIndex(), insn.getType());
   }

   @Override
   public void visit(PutElementInstruction insn) {
      this.reader.putElement(insn.getArray(), insn.getIndex(), insn.getValue(), insn.getType());
   }

   @Override
   public void visit(InvokeInstruction insn) {
      this.reader.invoke(insn.getReceiver(), insn.getInstance(), insn.getMethod(), insn.getArguments(), insn.getType());
   }

   @Override
   public void visit(InvokeDynamicInstruction insn) {
      this.reader
         .invokeDynamic(
            insn.getReceiver(),
            insn.getInstance(),
            insn.getMethod(),
            Collections.unmodifiableList(insn.getArguments()),
            insn.getBootstrapMethod(),
            Collections.unmodifiableList(insn.getBootstrapArguments())
         );
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.reader.isInstance(insn.getReceiver(), insn.getValue(), insn.getType());
   }

   @Override
   public void visit(InitClassInstruction insn) {
      this.reader.initClass(insn.getClassName());
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.reader.nullCheck(insn.getReceiver(), insn.getValue());
   }

   @Override
   public void visit(MonitorEnterInstruction insn) {
      this.reader.monitorEnter(insn.getObjectRef());
   }

   @Override
   public void visit(MonitorExitInstruction insn) {
      this.reader.monitorExit(insn.getObjectRef());
   }

   @Override
   public void visit(BoundCheckInstruction insn) {
      this.reader.boundCheck(insn.getReceiver(), insn.getIndex(), insn.getArray(), insn.isLower());
   }
}
