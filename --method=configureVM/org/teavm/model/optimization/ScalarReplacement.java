package org.teavm.model.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.BasicBlock;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.analysis.EscapeAnalysis;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.util.PhiUpdater;

public class ScalarReplacement implements MethodOptimization {
   @Override
   public boolean optimize(MethodOptimizationContext context, Program program) {
      boolean changed = false;

      while (this.performOnce(context, program)) {
         changed = true;
      }

      return changed;
   }

   private boolean performOnce(MethodOptimizationContext context, Program program) {
      List<Map<FieldReference, Variable>> fieldMappings = new ArrayList<>(Collections.nCopies(program.variableCount(), null));
      MethodReference methodReference = context.getMethod().getReference();
      EscapeAnalysis escapeAnalysis = new EscapeAnalysis();
      escapeAnalysis.analyze(program, methodReference);
      boolean canPerform = false;

      for (int i = 0; i < fieldMappings.size(); i++) {
         FieldReference[] fields = escapeAnalysis.getFields(i);
         if (!escapeAnalysis.escapes(i) && fields != null) {
            Variable instanceVar = program.variableAt(i);
            Map<FieldReference, Variable> fieldMapping = new LinkedHashMap<>();

            for (FieldReference field : fields) {
               Variable var = program.createVariable();
               if (instanceVar.getDebugName() != null) {
                  var.setDebugName(instanceVar.getDebugName() + "$" + field.getFieldName());
               }

               if (instanceVar.getLabel() != null) {
                  var.setLabel(instanceVar.getLabel() + "$" + field.getFieldName());
               }

               fieldMapping.put(field, var);
            }

            fieldMappings.set(i, fieldMapping);
            canPerform = true;
         }
      }

      if (!canPerform) {
         return false;
      }

      ScalarReplacement.ScalarReplacementVisitor visitor = new ScalarReplacement.ScalarReplacementVisitor(escapeAnalysis, fieldMappings);

      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            instruction.acceptVisitor(visitor);
         }

         List<Phi> additionalPhis = new ArrayList<>();

         for (int i = 0; i < block.getPhis().size(); i++) {
            Phi phi = block.getPhis().get(i);
            if (!escapeAnalysis.escapes(phi.getReceiver().getIndex())) {
               FieldReference[] fields = escapeAnalysis.getFields(phi.getReceiver().getIndex());
               if (fields != null) {
                  for (FieldReference field : fields) {
                     Phi phiReplacement = new Phi();
                     phiReplacement.setReceiver(fieldMappings.get(phi.getReceiver().getIndex()).get(field));

                     for (Incoming incoming : phi.getIncomings()) {
                        Incoming incomingReplacement = new Incoming();
                        incomingReplacement.setSource(incoming.getSource());
                        incomingReplacement.setValue(fieldMappings.get(incoming.getValue().getIndex()).get(field));
                        phiReplacement.getIncomings().add(incomingReplacement);
                     }

                     additionalPhis.add(phiReplacement);
                  }
               } else {
                  boolean isClass = phi.getIncomings().stream().anyMatch(incomingx -> escapeAnalysis.getFields(incomingx.getValue().getIndex()) != null);
                  if (!isClass) {
                     continue;
                  }
               }

               block.getPhis().remove(i--);
            }
         }

         block.getPhis().addAll(additionalPhis);
      }

      Variable[] arguments = new Variable[methodReference.parameterCount() + 1];

      for (int i = 0; i < arguments.length; i++) {
         arguments[i] = program.variableAt(i);
      }

      new PhiUpdater().updatePhis(program, arguments);
      return true;
   }

   static class ScalarReplacementVisitor extends AbstractInstructionVisitor {
      private EscapeAnalysis escapeAnalysis;
      private List<Map<FieldReference, Variable>> fieldMappings;

      ScalarReplacementVisitor(EscapeAnalysis escapeAnalysis, List<Map<FieldReference, Variable>> fieldMappings) {
         this.escapeAnalysis = escapeAnalysis;
         this.fieldMappings = fieldMappings;
      }

      @Override
      public void visit(ConstructInstruction insn) {
         int var = insn.getReceiver().getIndex();
         if (!this.escapeAnalysis.escapes(var) && this.escapeAnalysis.getFields(var) != null) {
            for (FieldReference fieldRef : this.escapeAnalysis.getFields(var)) {
               ValueType fieldType = this.escapeAnalysis.getFieldType(fieldRef);
               Variable receiver = this.fieldMappings.get(insn.getReceiver().getIndex()).get(fieldRef);
               Instruction initializer = this.generateDefaultValue(fieldType, receiver);
               initializer.setLocation(initializer.getLocation());
               insn.insertPrevious(initializer);
            }

            insn.delete();
         }
      }

      @Override
      public void visit(NullCheckInstruction insn) {
         this.transfer(insn, insn.getValue(), insn.getReceiver());
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         if (insn.getInstance() != null && !this.escapeAnalysis.escapes(insn.getInstance().getIndex())) {
            Variable var = this.fieldMappings.get(insn.getInstance().getIndex()).get(insn.getField());
            AssignInstruction assignment = new AssignInstruction();
            assignment.setReceiver(insn.getReceiver());
            assignment.setAssignee(var);
            assignment.setLocation(insn.getLocation());
            insn.replace(assignment);
         }
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         if (insn.getInstance() != null && !this.escapeAnalysis.escapes(insn.getInstance().getIndex())) {
            Variable var = this.fieldMappings.get(insn.getInstance().getIndex()).get(insn.getField());
            AssignInstruction assignment = new AssignInstruction();
            assignment.setReceiver(var);
            assignment.setAssignee(insn.getValue());
            assignment.setLocation(insn.getLocation());
            insn.replace(assignment);
         }
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.transfer(insn, insn.getAssignee(), insn.getReceiver());
      }

      private void transfer(Instruction insn, Variable from, Variable to) {
         if (!this.escapeAnalysis.escapes(from.getIndex())) {
            FieldReference[] fields = this.escapeAnalysis.getFields(to.getIndex());
            if (fields != null) {
               for (FieldReference field : fields) {
                  Variable assignee = this.fieldMappings.get(from.getIndex()).get(field);
                  if (assignee != null) {
                     Variable receiver = this.fieldMappings.get(to.getIndex()).get(field);
                     AssignInstruction assignment = new AssignInstruction();
                     assignment.setReceiver(receiver);
                     assignment.setAssignee(assignee);
                     assignment.setLocation(insn.getLocation());
                     insn.insertPrevious(assignment);
                  }
               }

               insn.delete();
            }
         }
      }

      private Instruction generateDefaultValue(ValueType type, Variable receiver) {
         if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)type).getKind()) {
               case BOOLEAN:
               case BYTE:
               case SHORT:
               case CHARACTER:
               case INTEGER: {
                  IntegerConstantInstruction insn = new IntegerConstantInstruction();
                  insn.setReceiver(receiver);
                  return insn;
               }
               case LONG: {
                  LongConstantInstruction insn = new LongConstantInstruction();
                  insn.setReceiver(receiver);
                  return insn;
               }
               case FLOAT: {
                  FloatConstantInstruction insn = new FloatConstantInstruction();
                  insn.setReceiver(receiver);
                  return insn;
               }
               case DOUBLE: {
                  DoubleConstantInstruction insn = new DoubleConstantInstruction();
                  insn.setReceiver(receiver);
                  return insn;
               }
            }
         }

         NullConstantInstruction insn = new NullConstantInstruction();
         insn.setReceiver(receiver);
         return insn;
      }
   }
}
