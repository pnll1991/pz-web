package org.teavm.model.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.teavm.common.DisjointSet;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntDeque;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.LivenessAnalyzer;
import org.teavm.model.util.UsageExtractor;

public class EscapeAnalysis {
   private int[] definitionClasses;
   private boolean[] escapingVars;
   private FieldReference[][] fields;
   private Map<FieldReference, ValueType> fieldTypes;

   public void analyze(Program program, MethodReference methodReference) {
      EscapeAnalysis.InstructionEscapeVisitor visitor = new EscapeAnalysis.InstructionEscapeVisitor(program.variableCount());

      for (int i = 0; i <= methodReference.parameterCount(); i++) {
         visitor.escapingVars[i] = true;
      }

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction insn : block) {
            insn.acceptVisitor(visitor);
         }

         if (block.getExceptionVariable() != null) {
            visitor.escapingVars[block.getExceptionVariable().getIndex()] = true;
         }
      }

      this.definitionClasses = visitor.definitionClasses.pack(program.variableCount());
      this.escapingVars = new boolean[program.variableCount()];
      this.fieldTypes = visitor.fieldTypes;

      for (int i = 0; i < program.variableCount(); i++) {
         if (visitor.escapingVars[i]) {
            this.escapingVars[this.definitionClasses[i]] = true;
         }
      }

      this.analyzePhis(program, methodReference.getDescriptor());
      this.propagateFields(program, visitor.fields);
      this.fields = this.packFields(visitor.fields);
   }

   public boolean escapes(int var) {
      return this.escapingVars[this.definitionClasses[var]];
   }

   public ValueType getFieldType(FieldReference field) {
      return this.fieldTypes.get(field);
   }

   public FieldReference[] getFields(int var) {
      FieldReference[] varFields = this.fields[this.definitionClasses[var]];
      return varFields != null ? (FieldReference[])varFields.clone() : null;
   }

   private void analyzePhis(Program program, MethodDescriptor methodDescriptor) {
      LivenessAnalyzer livenessAnalyzer = new LivenessAnalyzer();
      livenessAnalyzer.analyze(program, methodDescriptor);
      GraphBuilder graphBuilder = new GraphBuilder(program.variableCount());
      IntDeque queue = new IntArrayDeque();

      for (BasicBlock block : program.getBasicBlocks()) {
         BitSet usedVars = this.getUsedVarsInBlock(livenessAnalyzer, block);
         UsageExtractor useExtractor = new UsageExtractor();
         DefinitionExtractor defExtractor = new DefinitionExtractor();

         for (Instruction insn = block.getLastInstruction(); insn != null; insn = insn.getPrevious()) {
            if (insn instanceof AssignInstruction assign) {
               if (usedVars.get(assign.getAssignee().getIndex())) {
                  queue.addLast(this.definitionClasses[assign.getAssignee().getIndex()]);
               }
            } else if (insn instanceof NullCheckInstruction nullCheck) {
               if (usedVars.get(nullCheck.getValue().getIndex())) {
                  queue.addLast(this.definitionClasses[nullCheck.getValue().getIndex()]);
               }
            } else if (insn instanceof UnwrapArrayInstruction unwrapArray && usedVars.get(unwrapArray.getArray().getIndex())) {
               queue.addLast(this.definitionClasses[unwrapArray.getArray().getIndex()]);
            }

            insn.acceptVisitor(useExtractor);
            insn.acceptVisitor(defExtractor);

            for (Variable var : useExtractor.getUsedVariables()) {
               usedVars.set(var.getIndex());
            }

            for (Variable var : defExtractor.getDefinedVariables()) {
               usedVars.clear(var.getIndex());
            }
         }

         IntSet sharedIncomingVars = new IntHashSet();

         for (Phi phi : block.getPhis()) {
            if (this.escapes(phi.getReceiver().getIndex())) {
               queue.addLast(this.definitionClasses[phi.getReceiver().getIndex()]);
            }

            for (Incoming incoming : phi.getIncomings()) {
               int var = incoming.getValue().getIndex();
               graphBuilder.addEdge(this.definitionClasses[var], this.definitionClasses[phi.getReceiver().getIndex()]);
               if (this.escapes(var) || !sharedIncomingVars.add(var) || usedVars.get(var)) {
                  queue.addLast(this.definitionClasses[var]);
               }
            }
         }
      }

      Graph graph = graphBuilder.build();
      IntSet visited = new IntHashSet();

      while (!queue.isEmpty()) {
         int var = queue.removeFirst();
         if (visited.add(var)) {
            this.escapingVars[var] = true;

            for (int successor : graph.outgoingEdges(var)) {
               queue.addLast(successor);
            }

            for (int predecessor : graph.incomingEdges(var)) {
               queue.addLast(predecessor);
            }
         }
      }
   }

   private BitSet getUsedVarsInBlock(LivenessAnalyzer liveness, BasicBlock block) {
      return liveness.liveOut(block.getIndex());
   }

   private void propagateFields(Program program, List<Set<FieldReference>> fields) {
      class Task {
         int index;
         FieldReference field;

         Task(int index, FieldReference field) {
            this.index = index;
            this.field = field;
         }
      }

      Queue<Task> queue = new ArrayDeque<>();
      GraphBuilder graphBuilder = new GraphBuilder(program.variableCount());

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction insn : block) {
            if (insn instanceof AssignInstruction assign) {
               graphBuilder.addEdge(assign.getReceiver().getIndex(), assign.getAssignee().getIndex());
            } else if (insn instanceof NullCheckInstruction nullCheck) {
               graphBuilder.addEdge(nullCheck.getReceiver().getIndex(), nullCheck.getValue().getIndex());
            } else if (insn instanceof UnwrapArrayInstruction unwrapArray) {
               graphBuilder.addEdge(unwrapArray.getReceiver().getIndex(), unwrapArray.getArray().getIndex());
            }
         }

         for (Phi phi : block.getPhis()) {
            for (Incoming incoming : phi.getIncomings()) {
               graphBuilder.addEdge(phi.getReceiver().getIndex(), incoming.getValue().getIndex());
            }
         }
      }

      Graph graph = graphBuilder.build();

      for (int i = 0; i < program.variableCount(); i++) {
         Set<FieldReference> receiverFields = fields.get(i);
         if (receiverFields != null) {
            for (FieldReference field : receiverFields) {
               queue.add(new Task(i, field));
            }

            receiverFields.clear();
         }
      }

      while (!queue.isEmpty()) {
         Task task = queue.remove();
         Set<FieldReference> taskFields = fields.get(task.index);
         if (taskFields == null) {
            taskFields = new LinkedHashSet<>();
            fields.set(task.index, taskFields);
         }

         if (taskFields.add(task.field)) {
            for (int successor : graph.outgoingEdges(task.index)) {
               queue.add(new Task(successor, task.field));
            }
         }
      }
   }

   private FieldReference[][] packFields(List<Set<FieldReference>> fields) {
      List<Set<FieldReference>> joinedFields = new ArrayList<>(Collections.nCopies(fields.size(), null));

      for (int i = 0; i < fields.size(); i++) {
         if (fields.get(i) != null) {
            int j = this.definitionClasses[i];
            Set<FieldReference> fieldSet = joinedFields.get(j);
            if (fieldSet == null) {
               fieldSet = new LinkedHashSet<>();
               joinedFields.set(j, fieldSet);
            }

            fieldSet.addAll(fields.get(i));
         }
      }

      FieldReference[][] packedFields = new FieldReference[fields.size()][];

      for (int i = 0; i < packedFields.length; i++) {
         if (joinedFields.get(i) != null) {
            packedFields[i] = joinedFields.get(i).toArray(new FieldReference[0]);
         }
      }

      return packedFields;
   }

   static class InstructionEscapeVisitor extends AbstractInstructionVisitor {
      DisjointSet definitionClasses;
      boolean[] escapingVars;
      List<Set<FieldReference>> fields;
      Map<FieldReference, ValueType> fieldTypes = new HashMap<>();

      InstructionEscapeVisitor(int variableCount) {
         this.fields = new ArrayList<>(Collections.nCopies(variableCount, null));
         this.definitionClasses = new DisjointSet();

         for (int i = 0; i < variableCount; i++) {
            this.definitionClasses.create();
         }

         this.escapingVars = new boolean[variableCount];
      }

      @Override
      public void visit(NullConstantInstruction insn) {
         this.escapingVars[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         this.escapingVars[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         this.escapingVars[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.escapingVars[insn.getArray().getIndex()] = true;
         this.escapingVars[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.definitionClasses.union(insn.getReceiver().getIndex(), insn.getArray().getIndex());
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.definitionClasses.union(insn.getReceiver().getIndex(), insn.getAssignee().getIndex());
      }

      @Override
      public void visit(CastInstruction insn) {
         this.escapingVars[insn.getReceiver().getIndex()] = true;
         this.escapingVars[insn.getValue().getIndex()] = true;
      }

      @Override
      public void visit(ExitInstruction insn) {
         if (insn.getValueToReturn() != null) {
            this.escapingVars[insn.getValueToReturn().getIndex()] = true;
         }
      }

      @Override
      public void visit(RaiseInstruction insn) {
         this.escapingVars[insn.getException().getIndex()] = true;
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         this.escapingVars[insn.getReceiver().getIndex()] = true;
         this.addField(insn.getInstance(), insn.getField(), insn.getFieldType());
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         this.escapingVars[insn.getValue().getIndex()] = true;
         this.addField(insn.getInstance(), insn.getField(), insn.getFieldType());
      }

      private void addField(Variable instance, FieldReference field, ValueType fieldType) {
         if (instance != null) {
            Set<FieldReference> fieldSet = this.fields.get(instance.getIndex());
            if (fieldSet == null) {
               fieldSet = new LinkedHashSet<>();
               this.fields.set(instance.getIndex(), fieldSet);
            }

            fieldSet.add(field);
            this.fieldTypes.put(field, fieldType);
         }
      }

      @Override
      public void visit(GetElementInstruction insn) {
         this.escapingVars[insn.getReceiver().getIndex()] = true;
      }

      @Override
      public void visit(PutElementInstruction insn) {
         this.escapingVars[insn.getValue().getIndex()] = true;
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getInstance() != null) {
            this.escapingVars[insn.getInstance().getIndex()] = true;
         }

         for (Variable arg : insn.getArguments()) {
            this.escapingVars[arg.getIndex()] = true;
         }

         if (insn.getReceiver() != null) {
            this.escapingVars[insn.getReceiver().getIndex()] = true;
         }
      }

      @Override
      public void visit(IsInstanceInstruction insn) {
         this.escapingVars[insn.getValue().getIndex()] = true;
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.definitionClasses.union(insn.getValue().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(MonitorEnterInstruction insn) {
         this.escapingVars[insn.getObjectRef().getIndex()] = true;
      }

      @Override
      public void visit(MonitorExitInstruction insn) {
         this.escapingVars[insn.getObjectRef().getIndex()] = true;
      }

      @Override
      public void visit(BranchingInstruction insn) {
         switch (insn.getCondition()) {
            case NULL:
            case NOT_NULL:
               this.escapingVars[insn.getOperand().getIndex()] = true;
         }
      }

      @Override
      public void visit(BinaryBranchingInstruction insn) {
         switch (insn.getCondition()) {
            case REFERENCE_EQUAL:
            case REFERENCE_NOT_EQUAL:
               this.escapingVars[insn.getFirstOperand().getIndex()] = true;
               this.escapingVars[insn.getSecondOperand().getIndex()] = true;
         }
      }
   }
}
