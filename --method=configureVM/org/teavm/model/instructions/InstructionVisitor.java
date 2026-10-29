package org.teavm.model.instructions;

import org.teavm.model.InvokeDynamicInstruction;

public interface InstructionVisitor {
   void visit(EmptyInstruction var1);

   void visit(ClassConstantInstruction var1);

   void visit(NullConstantInstruction var1);

   void visit(IntegerConstantInstruction var1);

   void visit(LongConstantInstruction var1);

   void visit(FloatConstantInstruction var1);

   void visit(DoubleConstantInstruction var1);

   void visit(StringConstantInstruction var1);

   void visit(BinaryInstruction var1);

   void visit(NegateInstruction var1);

   void visit(AssignInstruction var1);

   void visit(CastInstruction var1);

   void visit(CastNumberInstruction var1);

   void visit(CastIntegerInstruction var1);

   void visit(BranchingInstruction var1);

   void visit(BinaryBranchingInstruction var1);

   void visit(JumpInstruction var1);

   void visit(SwitchInstruction var1);

   void visit(ExitInstruction var1);

   void visit(RaiseInstruction var1);

   void visit(ConstructArrayInstruction var1);

   void visit(ConstructInstruction var1);

   void visit(ConstructMultiArrayInstruction var1);

   void visit(GetFieldInstruction var1);

   void visit(PutFieldInstruction var1);

   void visit(ArrayLengthInstruction var1);

   void visit(CloneArrayInstruction var1);

   void visit(UnwrapArrayInstruction var1);

   void visit(GetElementInstruction var1);

   void visit(PutElementInstruction var1);

   void visit(InvokeInstruction var1);

   void visit(InvokeDynamicInstruction var1);

   void visit(IsInstanceInstruction var1);

   void visit(InitClassInstruction var1);

   void visit(NullCheckInstruction var1);

   void visit(MonitorEnterInstruction var1);

   void visit(MonitorExitInstruction var1);

   void visit(BoundCheckInstruction var1);
}
