package org.teavm.model.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.common.Loop;
import org.teavm.common.LoopGraph;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntIntHashMap;
import org.teavm.hppc.IntIntMap;
import org.teavm.hppc.IntSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.Variable;
import org.teavm.model.analysis.NullnessInformation;
import org.teavm.model.util.BasicBlockMapper;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.InstructionCopyReader;
import org.teavm.model.util.PhiUpdater;
import org.teavm.model.util.ProgramUtils;
import org.teavm.model.util.UsageExtractor;

class LoopInversionImpl {
   private final Program program;
   private final MethodReference method;
   private final int parameterCount;
   private Graph cfg;
   private DominatorTree dom;
   private boolean postponed;
   private boolean changed;
   private BasicBlock[] definitionPlaces;
   private boolean affected;

   LoopInversionImpl(Program program, MethodReference method, int parameterCount) {
      this.program = program;
      this.method = method;
      this.parameterCount = parameterCount;
      this.definitionPlaces = ProgramUtils.getVariableDefinitionPlaces(program);
   }

   boolean apply() {
      do {
         this.cfg = ProgramUtils.buildControlFlowGraph(this.program);
         LoopGraph loopGraph = new LoopGraph(this.cfg);
         this.dom = GraphUtils.buildDominatorTree(this.cfg);
         List<LoopInversionImpl.LoopWithExits> loops = this.getLoopsWithExits(loopGraph);
         this.postponed = false;
         if (!loops.isEmpty()) {
            for (LoopInversionImpl.LoopWithExits loop : loops) {
               loop.invert();
            }

            if (this.changed) {
               this.affected = true;
               Variable[] inputs = new Variable[this.parameterCount];

               for (int i = 0; i < inputs.length; i++) {
                  inputs[i] = this.program.variableAt(i);
               }

               new PhiUpdater().updatePhis(this.program, inputs);
            }
         }
      } while (this.postponed);

      return this.affected;
   }

   private List<LoopInversionImpl.LoopWithExits> getLoopsWithExits(LoopGraph cfg) {
      Map<Loop, LoopInversionImpl.LoopWithExits> loops = new HashMap<>();

      for (int node = 0; node < cfg.size(); node++) {
         for (Loop loop = cfg.loopAt(node); loop != null; loop = loop.getParent()) {
            LoopInversionImpl.LoopWithExits loopWithExits = this.getLoopWithExits(loops, loop);
            loopWithExits.nodes.add(node);

            for (int successor : cfg.outgoingEdges(node)) {
               Loop successorLoop = cfg.loopAt(successor);
               if (successorLoop == null || !successorLoop.isChildOf(loop)) {
                  loopWithExits.exits.add(node);
                  break;
               }
            }
         }
      }

      List<LoopInversionImpl.LoopWithExits> resultList = new ArrayList<>();
      Set<LoopInversionImpl.LoopWithExits> visited = new HashSet<>();

      for (LoopInversionImpl.LoopWithExits loop : loops.values()) {
         this.sortLoops(loop, visited, resultList);
      }

      Collections.reverse(resultList);
      return resultList;
   }

   private LoopInversionImpl.LoopWithExits getLoopWithExits(Map<Loop, LoopInversionImpl.LoopWithExits> cache, Loop loop) {
      return cache.computeIfAbsent(loop, k -> {
         LoopInversionImpl.LoopWithExits parent = loop.getParent() != null ? this.getLoopWithExits(cache, loop.getParent()) : null;
         return new LoopInversionImpl.LoopWithExits(loop.getHead(), parent);
      });
   }

   private void sortLoops(LoopInversionImpl.LoopWithExits loop, Set<LoopInversionImpl.LoopWithExits> visited, List<LoopInversionImpl.LoopWithExits> target) {
      if (visited.add(loop)) {
         if (loop.parent != null) {
            this.sortLoops(loop.parent, visited, target);
         }

         target.add(loop);
      }
   }

   private class LoopWithExits {
      final int head;
      final LoopInversionImpl.LoopWithExits parent;
      final IntSet nodes = new IntHashSet();
      final IntSet nodesAndCopies = new IntHashSet();
      final IntSet exits = new IntHashSet();
      int bodyStart;
      int headCopy;
      final IntIntMap copiedNodes = new IntIntHashMap();
      boolean shouldSkip;

      LoopWithExits(int head, LoopInversionImpl.LoopWithExits parent) {
         this.head = head;
         this.parent = parent;
      }

      void invert() {
         if (this.tryInvert()) {
            for (LoopInversionImpl.LoopWithExits ancestor = this.parent; ancestor != null; ancestor = ancestor.parent) {
               ancestor.shouldSkip = true;
            }
         }
      }

      private boolean tryInvert() {
         if (this.shouldSkip) {
            LoopInversionImpl.this.postponed = true;
            return false;
         }

         if (this.findCondition() && this.bodyStart >= 0) {
            IntSet nodesToCopy = this.nodesToCopy();
            NullnessInformation nullness = NullnessInformation.build(LoopInversionImpl.this.program, LoopInversionImpl.this.method.getDescriptor());
            boolean profitable = this.isInversionProfitable(nodesToCopy, nullness);
            nullness.dispose();
            if (!profitable) {
               return false;
            }

            this.copyBasicBlocks(nodesToCopy);
            this.copyCondition();
            this.moveBackEdges();
            this.removeInternalPhiInputsFromCondition();
            this.removeExternalPhiInputsFromConditionCopy();
            LoopInversionImpl.this.changed = true;
            return true;
         } else {
            return false;
         }
      }

      private boolean isInversionProfitable(IntSet nodesToCopy, NullnessInformation nullness) {
         UsageExtractor useExtractor = new UsageExtractor();
         DefinitionExtractor defExtractor = new DefinitionExtractor();
         LoopInvariantAnalyzer invariantAnalyzer = new LoopInvariantAnalyzer(nullness);

         for (int node : this.nodes.toArray()) {
            if (!nodesToCopy.contains(node)) {
               BasicBlock block = LoopInversionImpl.this.program.basicBlockAt(node);
               Set<Variable> currentInvariants = new HashSet<>();

               for (Instruction insn : block) {
                  invariantAnalyzer.reset();
                  insn.acceptVisitor(invariantAnalyzer);
                  if (invariantAnalyzer.canMove || invariantAnalyzer.constant) {
                     insn.acceptVisitor(useExtractor);
                     boolean invariant = Arrays.stream(useExtractor.getUsedVariables())
                        .allMatch(
                           var -> {
                              if (currentInvariants.contains(var)) {
                                 return true;
                              }

                              BasicBlock definedAt = var.getIndex() < LoopInversionImpl.this.definitionPlaces.length
                                 ? LoopInversionImpl.this.definitionPlaces[var.getIndex()]
                                 : null;
                              return definedAt == null || LoopInversionImpl.this.dom.dominates(definedAt.getIndex(), this.head);
                           }
                        );
                     if (invariant) {
                        if (invariantAnalyzer.sideEffect) {
                           return true;
                        }

                        insn.acceptVisitor(defExtractor);
                        currentInvariants.addAll(Arrays.asList(defExtractor.getDefinedVariables()));
                     }
                  }
               }
            }
         }

         return false;
      }

      private boolean findCondition() {
         IntSet tailNodes = new IntHashSet(LoopInversionImpl.this.program.basicBlockCount());

         for (int tailCandidate : LoopInversionImpl.this.cfg.incomingEdges(this.head)) {
            if (this.nodes.contains(tailCandidate)) {
               tailNodes.add(tailCandidate);
            }
         }

         this.bodyStart = LoopInversionImpl.this.dom.commonDominatorOf(tailNodes.toArray());

         int candidate;
         for (candidate = this.bodyStart; this.bodyStart != this.head; candidate = LoopInversionImpl.this.dom.immediateDominatorOf(candidate)) {
            int currentCandidate = candidate;
            if (Arrays.stream(this.exits.toArray()).anyMatch(exit -> LoopInversionImpl.this.dom.dominates(currentCandidate, exit))) {
               break;
            }

            this.bodyStart = candidate;
         }

         return candidate != this.bodyStart;
      }

      private void copyBasicBlocks(IntSet nodesToCopy) {
         int[] nodes = this.nodes.toArray();
         Arrays.sort(nodes);

         for (int node : nodes) {
            this.nodesAndCopies.add(node);
            if (nodesToCopy.contains(node)) {
               int copy = LoopInversionImpl.this.program.createBasicBlock().getIndex();
               if (this.head == node) {
                  this.headCopy = copy;
               }

               this.copiedNodes.put(node, copy);
               this.nodesAndCopies.add(copy);
            }
         }
      }

      private IntSet nodesToCopy() {
         IntSet result = new IntHashSet();

         for (int node : this.nodes.toArray()) {
            if (node == this.head || node != this.bodyStart && !LoopInversionImpl.this.dom.dominates(this.bodyStart, node)) {
               result.add(node);
            }
         }

         return result;
      }

      private void copyCondition() {
         BasicBlockMapper blockMapper = new BasicBlockMapper(block -> this.copiedNodes.getOrDefault(block, block));
         InstructionCopyReader copier = new InstructionCopyReader(LoopInversionImpl.this.program);

         for (int node : this.copiedNodes.keys().toArray()) {
            BasicBlock sourceBlock = LoopInversionImpl.this.program.basicBlockAt(node);
            BasicBlock targetBlock = LoopInversionImpl.this.program.basicBlockAt(this.copiedNodes.get(node));
            targetBlock.setExceptionVariable(sourceBlock.getExceptionVariable());
            copier.resetLocation();

            for (Instruction insn : ProgramUtils.copyInstructions(sourceBlock.getFirstInstruction(), null, targetBlock.getProgram())) {
               insn.acceptVisitor(blockMapper);
               targetBlock.add(insn);
            }

            for (Phi phi : sourceBlock.getPhis()) {
               Phi phiCopy = new Phi();
               phiCopy.setReceiver(phi.getReceiver());

               for (Incoming incoming : phi.getIncomings()) {
                  Incoming incomingCopy = new Incoming();
                  int source = incoming.getSource().getIndex();
                  incomingCopy.setSource(LoopInversionImpl.this.program.basicBlockAt(this.copiedNodes.getOrDefault(source, source)));
                  incomingCopy.setValue(incoming.getValue());
                  phiCopy.getIncomings().add(incomingCopy);
               }

               targetBlock.getPhis().add(phiCopy);
            }

            for (TryCatchBlock tryCatch : sourceBlock.getTryCatchBlocks()) {
               TryCatchBlock tryCatchCopy = new TryCatchBlock();
               int handler = tryCatch.getHandler().getIndex();
               tryCatchCopy.setExceptionType(tryCatch.getExceptionType());
               tryCatchCopy.setHandler(LoopInversionImpl.this.program.basicBlockAt(this.copiedNodes.getOrDefault(handler, handler)));
               targetBlock.getTryCatchBlocks().add(tryCatchCopy);
            }
         }

         for (int node = 0; node < LoopInversionImpl.this.cfg.size(); node++) {
            BasicBlock block = LoopInversionImpl.this.program.basicBlockAt(node);
            if (!this.copiedNodes.containsKey(block.getIndex())) {
               for (Phi phi : block.getPhis()) {
                  for (Incoming incoming : phi.getIncomings().toArray(new Incoming[0])) {
                     int source = incoming.getSource().getIndex();
                     if (this.copiedNodes.containsKey(source)) {
                        Incoming incomingCopy = new Incoming();
                        incomingCopy.setValue(incoming.getValue());
                        incomingCopy.setSource(LoopInversionImpl.this.program.basicBlockAt(this.copiedNodes.get(source)));
                        phi.getIncomings().add(incomingCopy);
                     }
                  }
               }
            }
         }
      }

      private void moveBackEdges() {
         BasicBlockMapper mapper = new BasicBlockMapper(blockx -> blockx == this.head ? this.headCopy : blockx);

         for (int node : this.nodes.toArray()) {
            BasicBlock block = LoopInversionImpl.this.program.basicBlockAt(node);
            Instruction last = block.getLastInstruction();
            if (last != null) {
               last.acceptVisitor(mapper);
            }
         }
      }

      private void removeInternalPhiInputsFromCondition() {
         BasicBlock block = LoopInversionImpl.this.program.basicBlockAt(this.head);

         for (Phi phi : block.getPhis()) {
            List<Incoming> incomings = phi.getIncomings();

            for (int i = 0; i < incomings.size(); i++) {
               Incoming incoming = incomings.get(i);
               if (this.nodes.contains(incoming.getSource().getIndex())) {
                  incomings.remove(i--);
               }
            }
         }
      }

      private void removeExternalPhiInputsFromConditionCopy() {
         BasicBlock block = LoopInversionImpl.this.program.basicBlockAt(this.headCopy);

         for (Phi phi : block.getPhis()) {
            List<Incoming> incomings = phi.getIncomings();

            for (int i = 0; i < incomings.size(); i++) {
               Incoming incoming = incomings.get(i);
               if (!this.nodesAndCopies.contains(incoming.getSource().getIndex())) {
                  incomings.remove(i--);
               }
            }
         }
      }
   }
}
