package org.teavm.model.lowlevel;

import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.util.DominatorWalker;
import org.teavm.model.util.DominatorWalkerCallback;
import org.teavm.model.util.DominatorWalkerContext;
import org.teavm.runtime.GC;
import org.teavm.runtime.RuntimeObject;

public class WriteBarrierInsertion {
   private static final MethodReference BARRIER_METHOD = new MethodReference(GC.class, "writeBarrier", RuntimeObject.class, void.class);
   private Characteristics characteristics;

   public WriteBarrierInsertion(Characteristics characteristics) {
      this.characteristics = characteristics;
   }

   public void apply(Program program) {
      if (program.basicBlockCount() != 0) {
         new DominatorWalker(program).walk(new WriteBarrierInsertion.WalkerCallbackImpl(program.variableCount()));
      }
   }

   static class State {
      IntSet newBarriers = new IntHashSet();
      IntArrayList oldBarriers;
   }

   class WalkerCallbackImpl extends AbstractInstructionVisitor implements DominatorWalkerCallback<WriteBarrierInsertion.State> {
      private DominatorWalkerContext context;
      private boolean[] constantVariables;
      IntHashSet installedBarriers = new IntHashSet();
      WriteBarrierInsertion.State state;

      WalkerCallbackImpl(int variableCount) {
         this.constantVariables = new boolean[variableCount];
      }

      @Override
      public void setContext(DominatorWalkerContext context) {
         this.context = context;
      }

      public WriteBarrierInsertion.State visit(BasicBlock block) {
         this.state = new WriteBarrierInsertion.State();
         if (!this.context.isExceptionHandler(block.getIndex()) && this.context.allPredecessorsVisited(block.getIndex())) {
            for (Phi phi : block.getPhis()) {
               if (phi.getIncomings().stream().allMatch(incoming -> this.installedBarriers.contains(incoming.getValue().getIndex()))) {
                  this.markAsInstalled(phi.getReceiver().getIndex());
               }
            }
         } else {
            this.invalidateBarriers();
         }

         for (Instruction instruction : block) {
            instruction.acceptVisitor(this);
         }

         return this.state;
      }

      public void endVisit(BasicBlock block, WriteBarrierInsertion.State state) {
         if (state.oldBarriers != null) {
            this.installedBarriers.clear();
            this.installedBarriers.addAll(state.oldBarriers);
         } else {
            for (IntCursor cursor : state.newBarriers) {
               this.installedBarriers.remove(cursor.value);
            }
         }
      }

      @Override
      public void visit(NullConstantInstruction insn) {
         this.constantVariables[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.constantVariables[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.constantVariables[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null && this.isManagedReferenceType(insn.getFieldType())) {
            this.installBarrier(insn, insn.getInstance(), insn.getValue());
         }
      }

      @Override
      public void visit(InvokeInstruction insn) {
         this.invalidateBarriers();
      }

      @Override
      public void visit(ConstructInstruction insn) {
         this.invalidateBarriers();
         this.markAsInstalled(insn.getReceiver().getIndex());
      }

      @Override
      public void visit(InitClassInstruction insn) {
         this.invalidateBarriers();
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.invalidateBarriers();
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         this.invalidateBarriers();
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         this.invalidateBarriers();
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         this.invalidateBarriers();
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         this.invalidateBarriers();
      }

      private boolean isManagedReferenceType(ValueType type) {
         if (type instanceof ValueType.Array) {
            return true;
         } else {
            return type instanceof ValueType.Object ? WriteBarrierInsertion.this.characteristics.isManaged(((ValueType.Object)type).getClassName()) : false;
         }
      }

      @Override
      public void visit(PutElementInstruction insn) {
         if (insn.getType() == ArrayElementType.OBJECT) {
            this.installBarrier(insn, insn.getArray(), insn.getValue());
         }
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.assign(insn.getAssignee(), insn.getReceiver());
      }

      @Override
      public void visit(CastInstruction insn) {
         this.assign(insn.getValue(), insn.getReceiver());
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.assign(insn.getValue(), insn.getReceiver());
      }

      private void assign(Variable from, Variable to) {
         if (this.installedBarriers.contains(from.getIndex())) {
            this.markAsInstalled(to.getIndex());
         }

         if (this.constantVariables[from.getIndex()]) {
            this.constantVariables[to.getIndex()] = true;
         }
      }

      private void installBarrier(Instruction instruction, Variable variable, Variable value) {
         if (!this.constantVariables[value.getIndex()]) {
            if (this.markAsInstalled(variable.getIndex())) {
               InvokeInstruction invoke = new InvokeInstruction();
               invoke.setType(InvocationType.SPECIAL);
               invoke.setMethod(WriteBarrierInsertion.BARRIER_METHOD);
               invoke.setArguments(variable);
               invoke.setLocation(instruction.getLocation());
               instruction.insertPrevious(invoke);
            }
         }
      }

      private boolean markAsInstalled(int index) {
         if (!this.installedBarriers.add(index)) {
            return false;
         }

         if (this.state.newBarriers != null) {
            this.state.newBarriers.add(index);
         }

         return true;
      }

      private void invalidateBarriers() {
         if (this.state.newBarriers != null) {
            this.state.oldBarriers = new IntArrayList();

            for (IntCursor cursor : this.installedBarriers) {
               if (!this.state.newBarriers.contains(cursor.value)) {
                  this.state.oldBarriers.add(cursor.value);
               }
            }

            this.state.newBarriers = null;
         }

         this.installedBarriers.clear();
      }
   }
}
