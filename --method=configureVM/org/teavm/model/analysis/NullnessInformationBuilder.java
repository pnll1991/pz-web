package org.teavm.model.analysis;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.common.GraphUtils;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntDeque;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Sigma;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.DominatorWalker;
import org.teavm.model.util.DominatorWalkerCallback;
import org.teavm.model.util.PhiUpdater;

class NullnessInformationBuilder {
   private Program program;
   private MethodDescriptor methodDescriptor;
   BitSet synthesizedVariables = new BitSet();
   PhiUpdater phiUpdater;
   private List<NullCheckInstruction> notNullInstructions = new ArrayList<>();
   private Graph assignmentGraph;
   private int[] notNullPredecessorsLeft;
   NullnessInformationBuilder.Nullness[] statuses;
   private int[][] variablePairs;

   NullnessInformationBuilder(Program program, MethodDescriptor methodDescriptor) {
      this.program = program;
      this.methodDescriptor = methodDescriptor;
   }

   void build() {
      this.extendProgram();
      this.buildVariablePairs();
      this.buildAssignmentGraph();
      this.propagateNullness();
   }

   private void buildVariablePairs() {
      List<IntSet> pairsBuilder = new ArrayList<>(Collections.nCopies(this.program.variableCount(), null));

      for (BasicBlock block : this.program.getBasicBlocks()) {
         if (block.getLastInstruction() instanceof BinaryBranchingInstruction branching) {
            this.addVariablePair(pairsBuilder, branching.getFirstOperand(), branching.getSecondOperand());
            this.addVariablePair(pairsBuilder, branching.getSecondOperand(), branching.getFirstOperand());
         }
      }

      this.variablePairs = new int[pairsBuilder.size()][];

      for (int i = 0; i < this.variablePairs.length; i++) {
         IntSet itemBuilder = pairsBuilder.get(i);
         this.variablePairs[i] = itemBuilder != null ? itemBuilder.toArray() : null;
      }
   }

   private void addVariablePair(List<IntSet> target, Variable first, Variable second) {
      IntSet pairs = target.get(first.getIndex());
      if (pairs == null) {
         pairs = new IntHashSet();
         target.set(first.getIndex(), pairs);
      }

      pairs.add(second.getIndex());
   }

   private void extendProgram() {
      this.insertAdditionalVariables();
      this.phiUpdater = new PhiUpdater();
      this.phiUpdater.setSigmaPredicate(instruction -> {
         if (instruction instanceof BinaryBranchingInstruction) {
            switch (((BinaryBranchingInstruction)instruction).getCondition()) {
               case REFERENCE_EQUAL:
               case REFERENCE_NOT_EQUAL:
                  return true;
            }
         } else if (instruction instanceof BranchingInstruction) {
            switch (((BranchingInstruction)instruction).getCondition()) {
               case NULL:
               case NOT_NULL:
                  return true;
            }
         }

         return false;
      });
      this.phiUpdater.updatePhis(this.program, this.methodDescriptor.parameterCount() + 1);
      this.collectAdditionalVariables();
   }

   private void insertAdditionalVariables() {
      DominatorWalker walker = new DominatorWalker(this.program);
      NullnessInformationBuilder.NullExtensionVisitor ev = new NullnessInformationBuilder.NullExtensionVisitor();
      walker.walk(ev);
   }

   private void collectAdditionalVariables() {
      for (NullCheckInstruction notNullInstruction : this.notNullInstructions) {
         this.synthesizedVariables.set(notNullInstruction.getReceiver().getIndex());
      }

      this.notNullInstructions.clear();
   }

   private void buildAssignmentGraph() {
      GraphBuilder builder = new GraphBuilder(this.program.variableCount());

      for (BasicBlock block : this.program.getBasicBlocks()) {
         for (Phi phi : block.getPhis()) {
            for (Incoming incoming : phi.getIncomings()) {
               builder.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
            }
         }

         for (Instruction instruction : block) {
            if (instruction instanceof AssignInstruction assignment) {
               builder.addEdge(assignment.getAssignee().getIndex(), assignment.getReceiver().getIndex());
            }
         }
      }

      this.assignmentGraph = this.removeLoops(builder.build());
      this.notNullPredecessorsLeft = new int[this.assignmentGraph.size()];

      for (int i = 0; i < this.assignmentGraph.size(); i++) {
         this.notNullPredecessorsLeft[i] = this.assignmentGraph.incomingEdgesCount(i);
      }
   }

   private Graph removeLoops(Graph graph) {
      int[][] sccs = GraphUtils.findStronglyConnectedComponents(graph);
      if (sccs.length == 0) {
         return graph;
      }

      int[] backMap = new int[graph.size()];
      int i = 0;

      while (i < backMap.length) {
         backMap[i] = i++;
      }

      boolean hasNonTrivialSccs = false;
      GraphBuilder builder = new GraphBuilder(graph.size());

      for (int[] scc : sccs) {
         if (scc.length != 1) {
            hasNonTrivialSccs = true;

            for (int ix = 1; ix < scc.length; ix++) {
               backMap[scc[ix]] = scc[0];
               builder.addEdge(scc[0], scc[ix]);
            }
         }
      }

      if (!hasNonTrivialSccs) {
         return graph;
      }

      for (int ix = 0; ix < graph.size(); ix++) {
         for (int j : graph.outgoingEdges(ix)) {
            if (backMap[j] != ix) {
               builder.addEdge(ix, backMap[j]);
            }
         }
      }

      return builder.build();
   }

   private void initNullness(IntDeque queue) {
      NullnessInformationBuilder.NullnessInitVisitor visitor = new NullnessInformationBuilder.NullnessInitVisitor(queue);

      for (BasicBlock block : this.program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            instruction.acceptVisitor(visitor);
         }

         if (block.getLastInstruction() instanceof BranchingInstruction branching) {
            Sigma[] sigmas = this.phiUpdater.getSigmasAt(block.getIndex());
            if (sigmas != null) {
               Sigma sigma = null;

               for (int i = 0; i < sigmas.length; i++) {
                  if (sigmas[i].getValue() == branching.getOperand()) {
                     sigma = sigmas[i];
                     break;
                  }
               }

               if (sigma != null) {
                  Variable falseVar;
                  Variable trueVar;
                  if (sigma.getOutgoings().get(0).getTarget() == branching.getConsequent()) {
                     trueVar = sigma.getOutgoings().get(0).getValue();
                     falseVar = sigma.getOutgoings().get(1).getValue();
                  } else {
                     trueVar = sigma.getOutgoings().get(1).getValue();
                     falseVar = sigma.getOutgoings().get(0).getValue();
                  }

                  switch (branching.getCondition()) {
                     case NULL:
                        queue.addLast(trueVar.getIndex());
                        queue.addLast(0);
                        queue.addLast(falseVar.getIndex());
                        queue.addLast(1);
                        break;
                     case NOT_NULL:
                        queue.addLast(trueVar.getIndex());
                        queue.addLast(1);
                        queue.addLast(falseVar.getIndex());
                        queue.addLast(0);
                  }
               }
            }
         }
      }

      queue.addLast(0);
      queue.addLast(1);
   }

   private void propagateNullness() {
      this.statuses = new NullnessInformationBuilder.Nullness[this.program.variableCount()];
      IntDeque deque = new IntArrayDeque();
      this.initNullness(deque);

      while (!deque.isEmpty()) {
         int node = deque.removeFirst();
         if (this.statuses[node] != null) {
            deque.removeFirst();
         } else {
            NullnessInformationBuilder.Nullness status = deque.removeFirst() == 1
               ? NullnessInformationBuilder.Nullness.NOT_NULL
               : NullnessInformationBuilder.Nullness.NULL;
            this.statuses[node] = status;
            if (status == NullnessInformationBuilder.Nullness.NULL) {
               int[] pairs = this.variablePairs[node];
               if (pairs != null) {
                  for (int pair : pairs) {
                     deque.addLast(pair);
                     deque.addLast(1);
                  }
               }
            }

            label52:
            for (int successor : this.assignmentGraph.outgoingEdges(node)) {
               if (--this.notNullPredecessorsLeft[successor] == 0) {
                  for (int sibling : this.assignmentGraph.incomingEdges(successor)) {
                     if (this.statuses[sibling] != this.statuses[node]) {
                        continue label52;
                     }
                  }

                  deque.addLast(successor);
                  deque.addLast(this.statuses[node] == NullnessInformationBuilder.Nullness.NULL ? 0 : 1);
               }
            }
         }
      }
   }

   class NullExtensionVisitor extends AbstractInstructionVisitor implements DominatorWalkerCallback<NullnessInformationBuilder.State> {
      NullnessInformationBuilder.State currentState;
      BasicBlock currentBlock;
      BitSet notNullVariables = new BitSet();

      NullExtensionVisitor() {
         this.notNullVariables.set(0);
      }

      public NullnessInformationBuilder.State visit(BasicBlock block) {
         this.currentState = new NullnessInformationBuilder.State();
         if (block.getExceptionVariable() != null) {
            this.notNullVariables.set(block.getExceptionVariable().getIndex());
         }

         this.currentBlock = block;

         for (Instruction insn : block) {
            insn.acceptVisitor(this);
         }

         return this.currentState;
      }

      public void endVisit(BasicBlock block, NullnessInformationBuilder.State state) {
         for (int rollbackToNull : state.newlyNonNull.toArray()) {
            this.notNullVariables.clear(rollbackToNull);
         }
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() != null) {
            this.insertNotNullInstruction(insn, insn.getInstance());
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null) {
            this.insertNotNullInstruction(insn, insn.getInstance());
         }
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         this.insertNotNullInstruction(insn, insn.getArray());
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.insertNotNullInstruction(insn, insn.getArray());
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.insertNotNullInstruction(insn, insn.getArray());
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getInstance() != null) {
            this.insertNotNullInstruction(insn, insn.getInstance());
         }
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         this.insertNotNullInstruction(insn, insn.getObjectRef());
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         this.insertNotNullInstruction(insn, insn.getObjectRef());
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.markAsNotNull(insn.getReceiver());
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.markAsNotNull(insn.getReceiver());
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         this.markAsNotNull(insn.getReceiver());
      }

      @Override
      public void visit(ConstructInstruction insn) {
         this.markAsNotNull(insn.getReceiver());
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         this.markAsNotNull(insn.getReceiver());
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.markAsNotNull(insn.getReceiver());
      }

      @Override
      public void visit(BoundCheckInstruction insn) {
         if (insn.getArray() != null) {
            this.markAsNotNull(insn.getArray());
         }
      }

      private void insertNotNullInstruction(Instruction currentInstruction, Variable var) {
         if (!this.notNullVariables.get(var.getIndex())) {
            NullCheckInstruction insn = new NullCheckInstruction();
            insn.setReceiver(var);
            insn.setValue(var);
            NullnessInformationBuilder.this.notNullInstructions.add(insn);
            if (currentInstruction != null) {
               currentInstruction.insertNext(insn);
            } else {
               this.currentBlock.addFirst(insn);
            }

            this.markAsNotNull(var);
            this.currentState.newlyNonNull.add(var.getIndex());
         }
      }

      private void markAsNotNull(Variable var) {
         this.notNullVariables.set(var.getIndex());
      }
   }

   enum Nullness {
      NULL,
      NOT_NULL;
   }

   static class NullnessInitVisitor extends AbstractInstructionVisitor {
      private IntDeque queue;

      NullnessInitVisitor(IntDeque queue) {
         this.queue = queue;
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(1);
      }

      @Override
      public void visit(NullConstantInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(0);
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(1);
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(1);
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(1);
      }

      @Override
      public void visit(ConstructInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(1);
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         this.queue.addLast(insn.getReceiver().getIndex());
         this.queue.addLast(1);
      }
   }

   static class State {
      IntSet newlyNonNull = new IntHashSet();
   }
}
