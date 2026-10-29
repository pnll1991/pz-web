package org.teavm.model.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.common.IntegerArray;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntDeque;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntObjectHashMap;
import org.teavm.hppc.IntObjectMap;
import org.teavm.hppc.IntSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.Outgoing;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Sigma;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
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
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InstructionVisitor;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
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

public class PhiUpdater {
   private Program program;
   private Graph cfg;
   private DominatorTree domTree;
   private Graph domGraph;
   private int[][] domFrontiers;
   private Variable[] variableMap;
   private boolean[] variableDefined;
   private int[] variableDefinitionCount;
   private List<List<Variable>> definedVersions = new ArrayList<>();
   private BasicBlock currentBlock;
   private Phi[][] phiMap;
   private int[][] phiIndexMap;
   private List<List<Phi>> synthesizedPhisByBlock = new ArrayList<>();
   private IntObjectMap<Phi> phisByReceiver = new IntObjectHashMap();
   private BitSet usedPhis = new BitSet();
   private boolean[] usedDefinitions;
   private IntegerArray variableToSourceMap = new IntegerArray(10);
   private List<Phi> synthesizedPhis = new ArrayList<>();
   private Sigma[][] sigmas;
   private Predicate<Instruction> sigmaPredicate = instruction -> false;
   private int[][][] frontierVariableCache;
   private Variable[][] pendingVariableMap;
   private InstructionVisitor consumer = new AbstractInstructionVisitor() {
      @Override
      public void visit(ClassConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(NullConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(IntegerConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(LongConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(FloatConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(DoubleConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(BinaryInstruction insn) {
         insn.setFirstOperand(PhiUpdater.this.use(insn.getFirstOperand()));
         insn.setSecondOperand(PhiUpdater.this.use(insn.getSecondOperand()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(NegateInstruction insn) {
         insn.setOperand(PhiUpdater.this.use(insn.getOperand()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(AssignInstruction insn) {
         insn.setAssignee(PhiUpdater.this.use(insn.getAssignee()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(BranchingInstruction insn) {
         insn.setOperand(PhiUpdater.this.use(insn.getOperand()));
      }

      @Override
      public void visit(BinaryBranchingInstruction insn) {
         insn.setFirstOperand(PhiUpdater.this.use(insn.getFirstOperand()));
         insn.setSecondOperand(PhiUpdater.this.use(insn.getSecondOperand()));
      }

      @Override
      public void visit(SwitchInstruction insn) {
         insn.setCondition(PhiUpdater.this.use(insn.getCondition()));
      }

      @Override
      public void visit(ExitInstruction insn) {
         if (insn.getValueToReturn() != null) {
            insn.setValueToReturn(PhiUpdater.this.use(insn.getValueToReturn()));
         }
      }

      @Override
      public void visit(RaiseInstruction insn) {
         insn.setException(PhiUpdater.this.use(insn.getException()));
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         insn.setSize(PhiUpdater.this.use(insn.getSize()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(ConstructInstruction insn) {
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         List<Variable> dimensions = insn.getDimensions();

         for (int i = 0; i < dimensions.size(); i++) {
            dimensions.set(i, PhiUpdater.this.use(dimensions.get(i)));
         }

         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() != null) {
            insn.setInstance(PhiUpdater.this.use(insn.getInstance()));
         }

         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null) {
            insn.setInstance(PhiUpdater.this.use(insn.getInstance()));
         }

         insn.setValue(PhiUpdater.this.use(insn.getValue()));
      }

      @Override
      public void visit(GetElementInstruction insn) {
         insn.setArray(PhiUpdater.this.use(insn.getArray()));
         insn.setIndex(PhiUpdater.this.use(insn.getIndex()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(PutElementInstruction insn) {
         insn.setArray(PhiUpdater.this.use(insn.getArray()));
         insn.setIndex(PhiUpdater.this.use(insn.getIndex()));
         insn.setValue(PhiUpdater.this.use(insn.getValue()));
      }

      @Override
      public void visit(InvokeInstruction insn) {
         insn.replaceArguments(v -> PhiUpdater.this.use(v));
         if (insn.getInstance() != null) {
            insn.setInstance(PhiUpdater.this.use(insn.getInstance()));
         }

         if (insn.getReceiver() != null) {
            insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
         }
      }

      @Override
      public void visit(InvokeDynamicInstruction insn) {
         List<Variable> args = insn.getArguments();

         for (int i = 0; i < args.size(); i++) {
            args.set(i, PhiUpdater.this.use(args.get(i)));
         }

         if (insn.getInstance() != null) {
            insn.setInstance(PhiUpdater.this.use(insn.getInstance()));
         }

         if (insn.getReceiver() != null) {
            insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
         }
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         insn.setValue(PhiUpdater.this.use(insn.getValue()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(CastInstruction insn) {
         insn.setValue(PhiUpdater.this.use(insn.getValue()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(CastNumberInstruction insn) {
         insn.setValue(PhiUpdater.this.use(insn.getValue()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(CastIntegerInstruction insn) {
         insn.setValue(PhiUpdater.this.use(insn.getValue()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(ArrayLengthInstruction insn) {
         insn.setArray(PhiUpdater.this.use(insn.getArray()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         insn.setArray(PhiUpdater.this.use(insn.getArray()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         insn.setArray(PhiUpdater.this.use(insn.getArray()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         insn.setValue(PhiUpdater.this.use(insn.getValue()));
         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         insn.setObjectRef(PhiUpdater.this.use(insn.getObjectRef()));
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         insn.setObjectRef(PhiUpdater.this.use(insn.getObjectRef()));
      }

      @Override
      public void visit(BoundCheckInstruction insn) {
         insn.setIndex(PhiUpdater.this.use(insn.getIndex()));
         if (insn.getArray() != null) {
            insn.setArray(PhiUpdater.this.use(insn.getArray()));
         }

         insn.setReceiver(PhiUpdater.this.define(insn.getReceiver()));
      }
   };

   public int getSourceVariable(int var) {
      return var >= this.variableToSourceMap.size() ? -1 : this.variableToSourceMap.get(var);
   }

   public List<Phi> getSynthesizedPhis() {
      return this.synthesizedPhis;
   }

   public void updatePhis(Program program, int parameterCount) {
      Variable[] parameters = new Variable[parameterCount];

      for (int i = 0; i < parameters.length; i++) {
         parameters[i] = program.variableAt(i);
      }

      this.updatePhis(program, parameters);
   }

   public Sigma[] getSigmasAt(int blockIndex) {
      Sigma[] result = this.sigmas[blockIndex];
      return result != null ? (Sigma[])result.clone() : null;
   }

   public void setSigmaPredicate(Predicate<Instruction> sigmaPredicate) {
      this.sigmaPredicate = sigmaPredicate;
   }

   public void updatePhis(Program program, Variable[] parameters) {
      if (program.basicBlockCount() != 0) {
         this.frontierVariableCache = new int[program.basicBlockCount()][][];
         this.program = program;
         this.phisByReceiver.clear();
         this.cfg = ProgramUtils.buildControlFlowGraph(program);
         this.domTree = GraphUtils.buildDominatorTree(this.cfg);
         this.domGraph = GraphUtils.buildDominatorGraph(this.domTree, program.basicBlockCount());
         this.pendingVariableMap = new Variable[program.basicBlockCount()][];
         this.variableMap = new Variable[program.variableCount()];
         this.usedDefinitions = new boolean[program.variableCount()];
         this.variableDefinitionCount = new int[program.variableCount()];

         for (int i = 0; i < parameters.length; i++) {
            this.variableMap[i] = parameters[i];
            this.usedDefinitions[i] = true;
            this.variableDefinitionCount[i] = 1;
         }

         for (int i = 0; i < program.variableCount(); i++) {
            this.variableToSourceMap.add(-1);
         }

         this.definedVersions.addAll(Collections.nCopies(program.variableCount(), null));
         this.phiMap = new Phi[program.basicBlockCount()][];
         this.phiIndexMap = new int[program.basicBlockCount()][];

         for (int i = 0; i < this.phiMap.length; i++) {
            this.phiMap[i] = new Phi[program.variableCount()];
            this.phiIndexMap[i] = new int[program.variableCount()];
         }

         this.domFrontiers = GraphUtils.findDominanceFrontiers(this.cfg, this.domTree);
         this.synthesizedPhisByBlock.clear();

         for (int i = 0; i < program.basicBlockCount(); i++) {
            this.synthesizedPhisByBlock.add(new ArrayList<>());
         }

         this.estimateSigmas();
         this.estimatePhis();
         this.renameVariables();
         this.propagatePhiUsageInformation();
         this.addSynthesizedPhis();
      }
   }

   private void estimateSigmas() {
      TransitionExtractor transitionExtractor = new TransitionExtractor();
      UsageExtractor usageExtractor = new UsageExtractor();
      this.sigmas = new Sigma[this.program.basicBlockCount()][];

      for (int i = 0; i < this.sigmas.length; i++) {
         BasicBlock block = this.program.basicBlockAt(i);
         Instruction instruction = block.getLastInstruction();
         if (instruction != null) {
            instruction.acceptVisitor(transitionExtractor);
            BasicBlock[] targets = transitionExtractor.getTargets();
            if (targets != null && targets.length >= 2 && this.sigmaPredicate.test(instruction)) {
               instruction.acceptVisitor(usageExtractor);
               Variable[] variables = usageExtractor.getUsedVariables();
               Sigma[] sigmasInBlock = new Sigma[variables.length];

               for (int j = 0; j < sigmasInBlock.length; j++) {
                  Sigma sigma = new Sigma(block, variables[j]);
                  sigmasInBlock[j] = sigma;

                  for (BasicBlock target : targets) {
                     Outgoing outgoing = new Outgoing(variables[j], target);
                     sigma.getOutgoings().add(outgoing);
                  }
               }

               this.sigmas[i] = sigmasInBlock;
            }
         }
      }
   }

   private void estimatePhis() {
      this.calculateDefinitions();
      DefinitionExtractor definitionExtractor = new DefinitionExtractor();
      this.variableDefined = new boolean[this.program.variableCount()];
      IntDeque stack = new IntArrayDeque();
      stack.addLast(0);

      while (!stack.isEmpty()) {
         int i = stack.removeLast();
         this.currentBlock = this.program.basicBlockAt(i);
         if (this.currentBlock.getExceptionVariable() != null) {
            this.markAssignment(this.currentBlock.getExceptionVariable(), this.currentBlock);
         }

         for (Phi phi : this.currentBlock.getPhis()) {
            this.markAssignment(phi.getReceiver(), this.currentBlock);
         }

         for (Instruction insn : this.currentBlock) {
            this.currentBlock = this.program.basicBlockAt(i);
            insn.acceptVisitor(definitionExtractor);

            for (Variable var : definitionExtractor.getDefinedVariables()) {
               this.markAssignment(var, this.currentBlock);
            }
         }

         Sigma[] sigmasAtBlock = this.sigmas[i];
         if (sigmasAtBlock != null) {
            for (Sigma sigma : sigmasAtBlock) {
               for (Outgoing outgoing : sigma.getOutgoings()) {
                  this.markAssignment(outgoing.getValue(), outgoing.getTarget());
               }
            }
         }

         for (int successor : this.domGraph.outgoingEdges(i)) {
            stack.addLast(successor);
         }
      }
   }

   private void calculateDefinitions() {
      DefinitionExtractor defExtractor = new DefinitionExtractor();

      for (BasicBlock block : this.program.getBasicBlocks()) {
         this.increaseDefinitionCount(block.getExceptionVariable());

         for (Phi phi : block.getPhis()) {
            this.increaseDefinitionCount(phi.getReceiver());
         }

         for (Instruction instruction : block) {
            instruction.acceptVisitor(defExtractor);

            for (Variable definedVar : defExtractor.getDefinedVariables()) {
               this.increaseDefinitionCount(definedVar);
            }
         }

         Sigma[] sigmas = this.getSigmasAt(block.getIndex());
         if (sigmas != null) {
            for (Sigma sigma : sigmas) {
               for (int i = 0; i < sigma.getOutgoings().size(); i++) {
                  this.increaseDefinitionCount(sigma.getValue());
               }
            }
         }
      }
   }

   private void increaseDefinitionCount(Variable var) {
      if (var != null) {
         this.variableDefinitionCount[var.getIndex()]++;
      }
   }

   private void renameVariables() {
      Deque<BasicBlock> stack = new ArrayDeque<>();

      for (int i = 0; i < this.program.basicBlockCount(); i++) {
         if (this.domGraph.incomingEdgesCount(i) == 0) {
            stack.push(this.program.basicBlockAt(i));
            this.pendingVariableMap[i] = (Variable[])this.variableMap.clone();
         }
      }

      while (!stack.isEmpty()) {
         Collections.fill(this.definedVersions, null);
         this.currentBlock = stack.pop();
         int index = this.currentBlock.getIndex();
         this.variableMap = this.pendingVariableMap[index];
         this.pendingVariableMap[index] = null;
         if (this.currentBlock.getExceptionVariable() != null) {
            this.currentBlock.setExceptionVariable(this.define(this.currentBlock.getExceptionVariable()));
         }

         for (Phi phi : this.synthesizedPhisByBlock.get(index)) {
            Variable var = this.program.createVariable();
            var.setDebugName(phi.getReceiver().getDebugName());
            var.setLabel(phi.getReceiver().getLabel());
            this.mapVariable(phi.getReceiver().getIndex(), var);
            this.phisByReceiver.put(var.getIndex(), phi);
            phi.setReceiver(var);
         }

         for (Phi phi : this.currentBlock.getPhis()) {
            phi.setReceiver(this.define(phi.getReceiver()));
         }

         for (Instruction insn : this.currentBlock) {
            insn.acceptVisitor(this.consumer);
         }

         int[] successors = this.domGraph.outgoingEdges(index);
         Sigma[] nextSigmas = this.sigmas[index];
         if (nextSigmas != null) {
            for (Sigma sigma : this.sigmas[index]) {
               sigma.setValue(this.use(sigma.getValue()));
            }
         }

         Variable[] oldVariableMap = this.variableMap;

         for (int j = successors.length - 1; j >= 0; j--) {
            int successor = successors[j];
            Variable[] variables = (Variable[])oldVariableMap.clone();
            this.pendingVariableMap[successor] = variables;
            stack.push(this.program.basicBlockAt(successor));
            this.variableMap = variables;
         }

         IntSet exceptionHandlingSuccessors = new IntHashSet();

         for (TryCatchBlock tryCatch : this.currentBlock.getTryCatchBlocks()) {
            exceptionHandlingSuccessors.add(tryCatch.getHandler().getIndex());
         }

         for (int successor : this.cfg.outgoingEdges(index)) {
            this.variableMap = this.domTree.immediateDominatorOf(successor) == this.currentBlock.getIndex()
               ? this.pendingVariableMap[successor]
               : (Variable[])oldVariableMap.clone();
            if (nextSigmas != null) {
               for (Sigma sigma : nextSigmas) {
                  for (Outgoing outgoing : sigma.getOutgoings()) {
                     if (outgoing.getTarget().getIndex() == successor) {
                        outgoing.setValue(this.define(outgoing.getValue()));
                     }
                  }
               }
            }

            this.renameOutgoingPhis(successor, exceptionHandlingSuccessors.contains(successor));
         }
      }
   }

   private void addSynthesizedPhis() {
      for (int i = 0; i < this.program.basicBlockCount(); i++) {
         for (Phi phi : this.synthesizedPhisByBlock.get(i)) {
            if (this.usedPhis.get(phi.getReceiver().getIndex()) && !phi.getIncomings().isEmpty()) {
               this.program.basicBlockAt(i).getPhis().add(phi);
               this.synthesizedPhis.add(phi);
            }
         }
      }
   }

   private void propagatePhiUsageInformation() {
      IntDeque worklist = new IntArrayDeque();

      for (int receiverIndex : this.phisByReceiver.keys().toArray()) {
         if (this.usedPhis.get(receiverIndex)) {
            worklist.addLast(receiverIndex);
         }
      }

      IntSet visited = new IntHashSet();

      while (!worklist.isEmpty()) {
         int varIndex = worklist.removeFirst();
         if (visited.add(varIndex)) {
            this.usedPhis.set(varIndex);
            Phi phi = (Phi)this.phisByReceiver.get(varIndex);
            if (phi != null) {
               for (Incoming incoming : phi.getIncomings()) {
                  if (!visited.contains(incoming.getValue().getIndex())) {
                     worklist.addLast(incoming.getValue().getIndex());
                  }
               }
            }
         }
      }
   }

   private void renameOutgoingPhis(int successor, boolean allVersions) {
      int[] phiIndexes = this.phiIndexMap[successor];
      List<Phi> phis = this.synthesizedPhisByBlock.get(successor);

      for (int j = 0; j < phis.size(); j++) {
         Phi phi = phis.get(j);
         Variable var = this.variableMap[phiIndexes[j]];
         if (var != null) {
            List<Variable> versions = this.definedVersions.get(phiIndexes[j]);
            if (versions != null && allVersions) {
               for (Variable version : versions) {
                  Incoming incoming = new Incoming();
                  incoming.setSource(this.currentBlock);
                  incoming.setValue(version);
                  phi.getIncomings().add(incoming);
               }
            }

            Incoming incoming = new Incoming();
            incoming.setSource(this.currentBlock);
            incoming.setValue(var);
            phi.getIncomings().add(incoming);
            phi.getReceiver().setDebugName(var.getDebugName());
         }
      }

      for (Phi phi : this.program.basicBlockAt(successor).getPhis()) {
         for (Incoming incoming : phi.getIncomings()) {
            if (incoming.getSource() == this.currentBlock) {
               Variable value = this.variableMap[incoming.getValue().getIndex()];
               if (value != null) {
                  incoming.setValue(value);
                  this.usedPhis.set(value.getIndex());
               } else {
                  this.usedPhis.set(incoming.getValue().getIndex());
               }
            }
         }
      }
   }

   private void markAssignment(Variable var, BasicBlock targetBlock) {
      if (this.variableDefinitionCount[var.getIndex()] < 2) {
         this.variableDefined[var.getIndex()] = true;
      } else {
         Deque<BasicBlock> worklist = new ArrayDeque<>();
         worklist.push(targetBlock);
         boolean isCurrentlyFrontier = this.currentBlock != targetBlock && this.cfg.incomingEdgesCount(targetBlock.getIndex()) > 1;
         if (this.variableDefined[var.getIndex()]) {
            for (TryCatchBlock tryCatch : this.currentBlock.getTryCatchBlocks()) {
               this.placePhi(tryCatch.getHandler().getIndex(), var, this.currentBlock, worklist);
            }
         } else {
            this.variableDefined[var.getIndex()] = true;
         }

         while (!worklist.isEmpty()) {
            BasicBlock block = worklist.pop();
            int[] frontiers = isCurrentlyFrontier ? new int[]{block.getIndex()} : this.domFrontiers[block.getIndex()];
            isCurrentlyFrontier = false;
            if (frontiers != null) {
               for (int frontier : frontiers) {
                  this.placePhi(frontier, var, block, worklist);
               }
            }
         }
      }
   }

   private int[][] getIncomingVariablesForFrontier(int frontier) {
      int[][] result = this.frontierVariableCache[frontier];
      if (result == null) {
         List<List<Variable>> builder = new ArrayList<>(Collections.nCopies(this.program.basicBlockCount(), null));

         for (Phi phi : this.program.basicBlockAt(frontier).getPhis()) {
            for (Incoming incoming : phi.getIncomings()) {
               List<Variable> variables = builder.get(incoming.getSource().getIndex());
               if (variables == null) {
                  variables = new ArrayList<>();
                  builder.set(incoming.getSource().getIndex(), variables);
               }

               variables.add(incoming.getValue());
            }
         }

         result = new int[this.program.basicBlockCount()][];

         for (int i = 0; i < result.length; i++) {
            List<Variable> builderVariables = builder.get(i);
            if (builderVariables != null) {
               int[] resultVariables = new int[builderVariables.size()];

               for (int j = 0; j < resultVariables.length; j++) {
                  resultVariables[j] = builderVariables.get(j).getIndex();
               }

               result[i] = resultVariables;
            }
         }

         this.frontierVariableCache[frontier] = result;
      }

      return result;
   }

   private void placePhi(int frontier, Variable var, BasicBlock block, Deque<BasicBlock> worklist) {
      BasicBlock frontierBlock = this.program.basicBlockAt(frontier);
      if (frontierBlock.getExceptionVariable() != var) {
         int[] frontierIncomingVariables = this.getIncomingVariablesForFrontier(frontier)[block.getIndex()];
         if (frontierIncomingVariables != null) {
            for (int incoming : frontierIncomingVariables) {
               if (incoming == var.getIndex()) {
                  return;
               }
            }
         }

         Phi phi = this.phiMap[frontier][var.getIndex()];
         if (phi == null) {
            phi = new Phi();
            phi.setReceiver(var);
            this.phiIndexMap[frontier][this.synthesizedPhisByBlock.get(frontier).size()] = var.getIndex();
            this.synthesizedPhisByBlock.get(frontier).add(phi);
            this.phiMap[frontier][var.getIndex()] = phi;
            worklist.push(frontierBlock);
         }
      }
   }

   private Variable define(Variable var) {
      Variable old = this.variableMap[var.getIndex()];
      if (old != null) {
         if (this.definedVersions.get(var.getIndex()) == null) {
            this.definedVersions.set(var.getIndex(), new ArrayList<>());
         }

         this.definedVersions.get(var.getIndex()).add(old);
      }

      Variable original = var;
      var = this.introduce(var);
      this.mapVariable(original.getIndex(), var);
      return var;
   }

   private void mapVariable(int index, Variable var) {
      this.variableMap[index] = var;

      while (this.variableToSourceMap.size() <= var.getIndex()) {
         this.variableToSourceMap.add(-1);
      }

      this.variableToSourceMap.set(var.getIndex(), index);
   }

   private Variable introduce(Variable var) {
      if (!this.usedDefinitions[var.getIndex()]) {
         this.usedDefinitions[var.getIndex()] = true;
      } else {
         Variable old = var;
         var = this.program.createVariable();
         var.setDebugName(old.getDebugName());
         var.setLabel(old.getLabel());
      }

      return var;
   }

   private Variable use(Variable var) {
      Variable mappedVar = this.variableMap[var.getIndex()];
      if (mappedVar == null) {
         throw new AssertionError("Variable used before definition: @" + var.getDisplayLabel() + " at $" + this.currentBlock.getIndex());
      }

      this.usedPhis.set(mappedVar.getIndex());
      return mappedVar;
   }
}
