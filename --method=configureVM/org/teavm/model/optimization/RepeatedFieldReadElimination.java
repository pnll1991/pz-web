package org.teavm.model.optimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.teavm.common.DominatorTree;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntDeque;
import org.teavm.hppc.IntObjectHashMap;
import org.teavm.hppc.IntObjectMap;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.IntObjectCursor;
import org.teavm.hppc.cursors.ObjectIntCursor;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.analysis.AliasAnalysis;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.util.ProgramUtils;

public class RepeatedFieldReadElimination implements MethodOptimization {
   private static final int ENTER = 0;
   private static final int EXIT = 1;
   private ClassReaderSource classSource;
   private boolean[] everythingInvalid;
   private List<IntObjectHashMap<Set<FieldReference>>> invalidFields = new ArrayList<>();
   private AliasAnalysis aliasAnalysis;
   private boolean changed;

   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      this.classSource = context.getClassSource();
      Graph cfg = ProgramUtils.buildControlFlowGraph(program);
      DominatorTree dom = GraphUtils.buildDominatorTree(cfg);
      this.aliasAnalysis = new AliasAnalysis();
      this.aliasAnalysis.analyze(program, context.getMethod().getDescriptor());
      this.insertInvalidationPoints(cfg, dom, program);
      new RepeatedFieldReadElimination.Traversal(program, dom, cfg).perform();
      return this.changed;
   }

   private void insertInvalidationPoints(Graph cfg, DominatorTree dom, Program program) {
      int[][] domFrontiers = GraphUtils.findDominanceFrontiers(cfg, dom);
      this.everythingInvalid = new boolean[program.basicBlockCount()];
      this.invalidFields.addAll(Collections.nCopies(program.basicBlockCount(), null));
      boolean[] exceptionHandlers = new boolean[program.basicBlockCount()];

      for (BasicBlock block : program.getBasicBlocks()) {
         for (TryCatchBlock tryCatchBlock : block.getTryCatchBlocks()) {
            exceptionHandlers[tryCatchBlock.getHandler().getIndex()] = true;
         }
      }

      IntArrayDeque worklist = new IntArrayDeque();
      RepeatedFieldReadElimination.InstructionAnalyzer instructionAnalyzer = new RepeatedFieldReadElimination.InstructionAnalyzer();

      for (BasicBlock block : program.getBasicBlocks()) {
         int[] frontiers = domFrontiers[block.getIndex()];
         if (frontiers.length != 0) {
            Set<RepeatedFieldReadElimination.FieldAndInstance> fieldsToInvalidate = new LinkedHashSet<>();
            boolean allInvalid = false;
            if (exceptionHandlers[block.getIndex()]) {
               allInvalid = true;
            } else {
               for (Instruction instruction : block) {
                  instructionAnalyzer.reset();
                  instruction.acceptVisitor(instructionAnalyzer);
                  if (instructionAnalyzer.invalidatesAll) {
                     allInvalid = true;
                     fieldsToInvalidate.clear();
                     break;
                  }

                  if (instructionAnalyzer.invalidatedField != null && !this.isVolatile(instructionAnalyzer.invalidatedField)) {
                     fieldsToInvalidate.add(
                        new RepeatedFieldReadElimination.FieldAndInstance(instructionAnalyzer.invalidatedField, instructionAnalyzer.instance)
                     );
                  }
               }
            }

            if (allInvalid) {
               worklist.addLast(frontiers);

               while (!worklist.isEmpty()) {
                  int target = worklist.removeFirst();
                  if (!this.everythingInvalid[target]) {
                     this.everythingInvalid[target] = true;
                     this.invalidFields.set(target, null);
                     worklist.addLast(domFrontiers[target]);
                  }
               }
            } else {
               for (RepeatedFieldReadElimination.FieldAndInstance fieldAndInstance : fieldsToInvalidate) {
                  worklist.addLast(frontiers);
                  int instance = fieldAndInstance.instance;
                  FieldReference field = fieldAndInstance.field;

                  while (!worklist.isEmpty()) {
                     int target = worklist.removeFirst();
                     if (!this.everythingInvalid[target]) {
                        IntObjectHashMap<Set<FieldReference>> invalidFieldsByBlock = this.invalidFields.get(target);
                        if (invalidFieldsByBlock == null) {
                           invalidFieldsByBlock = new IntObjectHashMap();
                           this.invalidFields.set(target, invalidFieldsByBlock);
                        }

                        Set<FieldReference> invalidFieldsByVar = (Set<FieldReference>)invalidFieldsByBlock.get(instance);
                        if (invalidFieldsByVar == null) {
                           invalidFieldsByVar = new HashSet<>();
                           invalidFieldsByBlock.put(instance, invalidFieldsByVar);
                        }

                        if (invalidFieldsByVar.add(field)) {
                           worklist.addLast(domFrontiers[target]);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isVolatile(FieldReference fieldRef) {
      FieldReader field = this.classSource.resolve(fieldRef);
      return field == null || field.hasModifier(ElementModifier.VOLATILE);
   }

   static class FieldAndInstance {
      final FieldReference field;
      final int instance;

      FieldAndInstance(FieldReference field, int instance) {
         this.field = field;
         this.instance = instance;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof RepeatedFieldReadElimination.FieldAndInstance that) ? false : this.instance == that.instance && this.field.equals(that.field);
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.field, this.instance);
      }
   }

   static class InstructionAnalyzer extends AbstractInstructionVisitor {
      boolean invalidatesAll;
      FieldReference invalidatedField;
      int instance;
      int newValue;

      void reset() {
         this.invalidatesAll = false;
         this.invalidatedField = null;
         this.instance = -1;
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         this.invalidatedField = insn.getField();
         this.instance = insn.getInstance() != null ? insn.getInstance().getIndex() : -1;
         this.newValue = insn.getValue().getIndex();
      }

      @Override
      public void visit(InvokeInstruction insn) {
         this.invalidatesAll = true;
      }
   }

   static class State {
      IntObjectMap<ObjectIntMap<FieldReference>> removedCacheFields = new IntObjectHashMap();
      IntObjectMap<Set<FieldReference>> addedCacheFields = new IntObjectHashMap();
   }

   class Traversal {
      Program program;
      IntDeque worklist = new IntArrayDeque();
      IntObjectMap<ObjectIntMap<FieldReference>> cacheVars = new IntObjectHashMap();
      Deque<RepeatedFieldReadElimination.State> stateStack = new ArrayDeque<>();
      Graph domGraph;
      RepeatedFieldReadElimination.InstructionAnalyzer instructionAnalyzer = new RepeatedFieldReadElimination.InstructionAnalyzer();

      Traversal(Program program, DominatorTree dom, Graph cfg) {
         this.program = program;
         this.worklist.addLast(0);
         this.worklist.addLast(0);
         this.domGraph = GraphUtils.buildDominatorGraph(dom, cfg.size());
      }

      void perform() {
         while (!this.worklist.isEmpty()) {
            int operation = this.worklist.removeLast();
            int blockIndex = this.worklist.removeLast();
            BasicBlock block = this.program.basicBlockAt(blockIndex);
            switch (operation) {
               case 0:
                  this.enterBlock(block);
                  break;
               case 1:
                  this.exitBlock();
            }
         }
      }

      private void enterBlock(BasicBlock block) {
         this.stateStack.addLast(new RepeatedFieldReadElimination.State());
         this.invalidatePreparedFields(block);

         for (Instruction instruction : block) {
            if (instruction instanceof GetFieldInstruction) {
               this.handleGetField((GetFieldInstruction)instruction);
            } else {
               this.instructionAnalyzer.reset();
               instruction.acceptVisitor(this.instructionAnalyzer);
               if (this.instructionAnalyzer.invalidatesAll) {
                  this.invalidateAllFields();
               } else if (this.instructionAnalyzer.invalidatedField != null) {
                  FieldReference field = this.instructionAnalyzer.invalidatedField;
                  int instance = this.instructionAnalyzer.instance;
                  if (!RepeatedFieldReadElimination.this.isVolatile(field)) {
                     this.invalidateField(instance, field);
                     this.storeIntoCache(instance, field, this.instructionAnalyzer.newValue);
                  }
               }
            }
         }

         this.worklist.addLast(block.getIndex());
         this.worklist.addLast(1);

         for (int successor : this.domGraph.outgoingEdges(block.getIndex())) {
            this.worklist.addLast(successor);
            this.worklist.addLast(0);
         }
      }

      private void invalidatePreparedFields(BasicBlock block) {
         if (RepeatedFieldReadElimination.this.everythingInvalid[block.getIndex()]) {
            this.invalidateAllFields();
         } else {
            IntObjectHashMap<Set<FieldReference>> invalidFieldsByBlock = RepeatedFieldReadElimination.this.invalidFields.get(block.getIndex());
            if (invalidFieldsByBlock != null) {
               for (IntObjectCursor<Set<FieldReference>> cursor : invalidFieldsByBlock) {
                  int instance = cursor.key;

                  for (FieldReference field : (Set)cursor.value) {
                     this.invalidateField(instance, field);
                  }
               }
            }
         }
      }

      private void handleGetField(GetFieldInstruction instruction) {
         FieldReference field = instruction.getField();
         if (!RepeatedFieldReadElimination.this.isVolatile(field)) {
            int instanceIndex = instruction.getInstance() != null ? instruction.getInstance().getIndex() : -1;
            ObjectIntMap<FieldReference> cacheVarsByInstance = (ObjectIntMap<FieldReference>)this.cacheVars.get(instanceIndex);
            if (cacheVarsByInstance == null) {
               cacheVarsByInstance = new ObjectIntHashMap();
               this.cacheVars.put(instanceIndex, cacheVarsByInstance);
            }

            int cachedVar = cacheVarsByInstance.getOrDefault(field, -1);
            if (cachedVar >= 0) {
               AssignInstruction assign = new AssignInstruction();
               assign.setReceiver(instruction.getReceiver());
               assign.setAssignee(this.program.variableAt(cachedVar));
               assign.setLocation(instruction.getLocation());
               instruction.replace(assign);
               RepeatedFieldReadElimination.this.changed = true;
            } else {
               cachedVar = instruction.getReceiver().getIndex();
               cacheVarsByInstance.put(field, cachedVar);
               this.markFieldAsAdded(instanceIndex, field);
            }
         }
      }

      private void storeIntoCache(int instance, FieldReference field, int value) {
         ObjectIntMap<FieldReference> cacheVarsByInstance = (ObjectIntMap<FieldReference>)this.cacheVars.get(instance);
         if (cacheVarsByInstance == null) {
            cacheVarsByInstance = new ObjectIntHashMap();
            this.cacheVars.put(instance, cacheVarsByInstance);
         }

         cacheVarsByInstance.put(field, value);
         this.markFieldAsAdded(instance, field);
      }

      private void markFieldAsAdded(int instance, FieldReference field) {
         RepeatedFieldReadElimination.State state = this.currentState();
         ObjectIntMap<FieldReference> removedFieldsByInstance = (ObjectIntMap<FieldReference>)state.removedCacheFields.get(instance);
         if (removedFieldsByInstance == null || !removedFieldsByInstance.containsKey(field)) {
            Set<FieldReference> fields = (Set<FieldReference>)state.addedCacheFields.get(instance);
            if (fields == null) {
               fields = new HashSet<>();
               state.addedCacheFields.put(instance, fields);
            }

            fields.add(field);
         }
      }

      private void invalidateAllFields() {
         RepeatedFieldReadElimination.State state = this.currentState();

         for (IntObjectCursor<ObjectIntMap<FieldReference>> instanceCursor : this.cacheVars) {
            int instance = instanceCursor.key;

            for (ObjectIntCursor<FieldReference> fieldCursor : (ObjectIntMap)instanceCursor.value) {
               FieldReference field = (FieldReference)fieldCursor.key;
               int value = fieldCursor.value;
               this.markFieldAsRemoved(state, instance, field, value);
            }
         }

         this.cacheVars.clear();
      }

      private void invalidateField(int instance, FieldReference field) {
         if (instance == -1) {
            this.invalidateSingleField(instance, field);
         } else {
            if (RepeatedFieldReadElimination.this.aliasAnalysis.affectsEverything(instance)) {
               this.invalidateFieldOnAllInstances(field);
            } else {
               for (int affectedVar : RepeatedFieldReadElimination.this.aliasAnalysis.affectedVariables(instance)) {
                  this.invalidateSingleField(affectedVar, field);
               }

               for (int affectedVar : RepeatedFieldReadElimination.this.aliasAnalysis.getExternalObjects()) {
                  this.invalidateSingleField(affectedVar, field);
               }
            }
         }
      }

      private void invalidateSingleField(int instance, FieldReference field) {
         ObjectIntMap<FieldReference> cacheVarsByInstance = (ObjectIntMap<FieldReference>)this.cacheVars.get(instance);
         if (cacheVarsByInstance != null && cacheVarsByInstance.containsKey(field)) {
            int value = cacheVarsByInstance.remove(field);
            RepeatedFieldReadElimination.State state = this.currentState();
            this.markFieldAsRemoved(state, instance, field, value);
         }
      }

      private void invalidateFieldOnAllInstances(FieldReference field) {
         for (IntObjectCursor<ObjectIntMap<FieldReference>> instanceCursor : this.cacheVars) {
            int instance = instanceCursor.key;
            int value = ((ObjectIntMap)instanceCursor.value).getOrDefault(field, -1);
            if (value >= 0) {
               ((ObjectIntMap)instanceCursor.value).remove(field);
               RepeatedFieldReadElimination.State state = this.currentState();
               this.markFieldAsRemoved(state, instance, field, value);
            }
         }
      }

      private void markFieldAsRemoved(RepeatedFieldReadElimination.State state, int instance, FieldReference field, int value) {
         Set<FieldReference> addedFieldsByInstance = (Set<FieldReference>)state.addedCacheFields.get(instance);
         if (addedFieldsByInstance == null || !addedFieldsByInstance.contains(field)) {
            ObjectIntMap<FieldReference> removedFieldsByInstance = (ObjectIntMap<FieldReference>)state.removedCacheFields.get(instance);
            if (removedFieldsByInstance == null) {
               removedFieldsByInstance = new ObjectIntHashMap();
               state.removedCacheFields.put(instance, removedFieldsByInstance);
            }

            if (!removedFieldsByInstance.containsKey(field)) {
               removedFieldsByInstance.put(field, value);
            }
         }
      }

      private void exitBlock() {
         RepeatedFieldReadElimination.State state = this.stateStack.removeLast();

         for (IntObjectCursor<Set<FieldReference>> instanceCursor : state.addedCacheFields) {
            int instance = instanceCursor.key;
            ObjectIntMap<FieldReference> cachedFieldsByInstances = (ObjectIntMap<FieldReference>)this.cacheVars.get(instance);
            if (cachedFieldsByInstances != null) {
               for (FieldReference field : (Set)instanceCursor.value) {
                  cachedFieldsByInstances.remove(field);
               }

               if (cachedFieldsByInstances.isEmpty()) {
                  this.cacheVars.remove(instance);
               }
            }
         }

         for (IntObjectCursor<ObjectIntMap<FieldReference>> instanceCursor : state.removedCacheFields) {
            int instance = instanceCursor.key;
            ObjectIntMap<FieldReference> cacheVarsByInstance = (ObjectIntMap<FieldReference>)this.cacheVars.get(instance);
            if (cacheVarsByInstance == null) {
               cacheVarsByInstance = new ObjectIntHashMap();
               this.cacheVars.put(instance, cacheVarsByInstance);
            }

            for (ObjectIntCursor<FieldReference> fieldCursor : (ObjectIntMap)instanceCursor.value) {
               FieldReference field = (FieldReference)fieldCursor.key;
               int value = fieldCursor.value;
               cacheVarsByInstance.put(field, value);
            }
         }
      }

      private RepeatedFieldReadElimination.State currentState() {
         return this.stateStack.getLast();
      }
   }
}
