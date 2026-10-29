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

public class DefinitionExtractor implements InstructionVisitor {
   private Variable[] definedVariables;

   public Variable[] getDefinedVariables() {
      return this.definedVariables;
   }

   @Override
   public void visit(EmptyInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(BranchingInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(BinaryBranchingInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(JumpInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(SwitchInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(ExitInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(RaiseInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(ConstructInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(PutFieldInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(GetElementInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(PutElementInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(InvokeInstruction insn) {
      if (insn.getReceiver() == null) {
         this.definedVariables = new Variable[0];
      } else {
         this.definedVariables = new Variable[]{insn.getReceiver()};
      }
   }

   @Override
   public void visit(InvokeDynamicInstruction insn) {
      if (insn.getReceiver() == null) {
         this.definedVariables = new Variable[0];
      } else {
         this.definedVariables = new Variable[]{insn.getReceiver()};
      }
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(CastInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(CloneArrayInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(InitClassInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }

   @Override
   public void visit(MonitorEnterInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(MonitorExitInstruction insn) {
      this.definedVariables = new Variable[0];
   }

   @Override
   public void visit(BoundCheckInstruction insn) {
      this.definedVariables = new Variable[]{insn.getReceiver()};
   }
}
