package org.teavm.model.util;

import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.Variable;
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

public class UsageExtractor implements InstructionVisitor {
   private Variable[] usedVariables;

   public Variable[] getUsedVariables() {
      return this.usedVariables;
   }

   @Override
   public void visit(EmptyInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.usedVariables = new Variable[]{insn.getFirstOperand(), insn.getSecondOperand()};
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.usedVariables = new Variable[]{insn.getOperand()};
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.usedVariables = new Variable[]{insn.getAssignee()};
   }

   @Override
   public void visit(CastInstruction insn) {
      this.usedVariables = new Variable[]{insn.getValue()};
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.usedVariables = new Variable[]{insn.getValue()};
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.usedVariables = new Variable[]{insn.getValue()};
   }

   @Override
   public void visit(BranchingInstruction insn) {
      this.usedVariables = new Variable[]{insn.getOperand()};
   }

   @Override
   public void visit(BinaryBranchingInstruction insn) {
      this.usedVariables = new Variable[]{insn.getFirstOperand(), insn.getSecondOperand()};
   }

   @Override
   public void visit(JumpInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(SwitchInstruction insn) {
      this.usedVariables = new Variable[]{insn.getCondition()};
   }

   @Override
   public void visit(ExitInstruction insn) {
      this.usedVariables = insn.getValueToReturn() != null ? new Variable[]{insn.getValueToReturn()} : new Variable[0];
   }

   @Override
   public void visit(RaiseInstruction insn) {
      this.usedVariables = new Variable[]{insn.getException()};
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      this.usedVariables = new Variable[]{insn.getSize()};
   }

   @Override
   public void visit(ConstructInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      this.usedVariables = insn.getDimensions().toArray(new Variable[0]);
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      this.usedVariables = insn.getInstance() != null ? new Variable[]{insn.getInstance()} : new Variable[0];
   }

   @Override
   public void visit(PutFieldInstruction insn) {
      this.usedVariables = insn.getInstance() != null ? new Variable[]{insn.getInstance(), insn.getValue()} : new Variable[]{insn.getValue()};
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.usedVariables = new Variable[]{insn.getArray()};
   }

   @Override
   public void visit(CloneArrayInstruction insn) {
      this.usedVariables = new Variable[]{insn.getArray()};
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.usedVariables = new Variable[]{insn.getArray()};
   }

   @Override
   public void visit(GetElementInstruction insn) {
      this.usedVariables = new Variable[]{insn.getArray(), insn.getIndex()};
   }

   @Override
   public void visit(PutElementInstruction insn) {
      this.usedVariables = new Variable[]{insn.getArray(), insn.getIndex(), insn.getValue()};
   }

   @Override
   public void visit(InvokeInstruction insn) {
      if (insn.getInstance() != null) {
         this.usedVariables = new Variable[insn.getArguments().size() + 1];
         insn.getArguments().toArray(this.usedVariables);
         this.usedVariables[insn.getArguments().size()] = insn.getInstance();
      } else {
         this.usedVariables = new Variable[insn.getArguments().size()];
         insn.getArguments().toArray(this.usedVariables);
      }
   }

   @Override
   public void visit(InvokeDynamicInstruction insn) {
      if (insn.getInstance() != null) {
         this.usedVariables = new Variable[insn.getArguments().size() + 1];
         insn.getArguments().toArray(this.usedVariables);
         this.usedVariables[insn.getArguments().size()] = insn.getInstance();
      } else {
         this.usedVariables = new Variable[insn.getArguments().size()];
         insn.getArguments().toArray(this.usedVariables);
      }
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.usedVariables = new Variable[]{insn.getValue()};
   }

   @Override
   public void visit(InitClassInstruction insn) {
      this.usedVariables = new Variable[0];
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.usedVariables = new Variable[]{insn.getValue()};
   }

   @Override
   public void visit(MonitorEnterInstruction insn) {
      this.usedVariables = new Variable[]{insn.getObjectRef()};
   }

   @Override
   public void visit(MonitorExitInstruction insn) {
      this.usedVariables = new Variable[]{insn.getObjectRef()};
   }

   @Override
   public void visit(BoundCheckInstruction insn) {
      if (insn.getArray() != null) {
         this.usedVariables = new Variable[]{insn.getArray(), insn.getIndex()};
      } else {
         this.usedVariables = new Variable[]{insn.getIndex()};
      }
   }
}
