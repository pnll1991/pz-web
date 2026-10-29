package org.teavm.model.util;

import java.util.List;
import org.teavm.model.BasicBlock;
import org.teavm.model.InvokeDynamicInstruction;
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
import org.teavm.model.instructions.SwitchTableEntry;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class TransitionExtractor implements InstructionVisitor {
   private BasicBlock[] targets;

   public BasicBlock[] getTargets() {
      return this.targets;
   }

   @Override
   public void visit(EmptyInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(ClassConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(NullConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(IntegerConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(LongConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(FloatConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(DoubleConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(StringConstantInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(BinaryInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(NegateInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(AssignInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(BranchingInstruction insn) {
      this.targets = new BasicBlock[]{insn.getConsequent(), insn.getAlternative()};
   }

   @Override
   public void visit(BinaryBranchingInstruction insn) {
      this.targets = new BasicBlock[]{insn.getConsequent(), insn.getAlternative()};
   }

   @Override
   public void visit(JumpInstruction insn) {
      this.targets = new BasicBlock[]{insn.getTarget()};
   }

   @Override
   public void visit(SwitchInstruction insn) {
      List<SwitchTableEntry> entries = insn.getEntries();
      this.targets = new BasicBlock[entries.size() + 1];

      for (int i = 0; i < entries.size(); i++) {
         this.targets[i] = entries.get(i).getTarget();
      }

      this.targets[entries.size()] = insn.getDefaultTarget();
   }

   @Override
   public void visit(ExitInstruction insn) {
      this.targets = new BasicBlock[0];
   }

   @Override
   public void visit(RaiseInstruction insn) {
      this.targets = new BasicBlock[0];
   }

   @Override
   public void visit(ConstructArrayInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(ConstructInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(ConstructMultiArrayInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(GetFieldInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(PutFieldInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(GetElementInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(PutElementInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(InvokeInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(InvokeDynamicInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(IsInstanceInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(CastInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(CastNumberInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(CastIntegerInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(ArrayLengthInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(UnwrapArrayInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(CloneArrayInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(InitClassInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(NullCheckInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(MonitorEnterInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(MonitorExitInstruction insn) {
      this.targets = null;
   }

   @Override
   public void visit(BoundCheckInstruction insn) {
      this.targets = null;
   }
}
