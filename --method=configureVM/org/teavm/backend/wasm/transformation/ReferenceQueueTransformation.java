package org.teavm.backend.wasm.transformation;

import java.lang.ref.ReferenceQueue;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.Program;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.util.ModelUtils;
import org.teavm.model.util.ProgramUtils;

public class ReferenceQueueTransformation implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(ReferenceQueue.class.getName())) {
         this.transformReferenceQueue(cls, context);
      }
   }

   private void transformReferenceQueue(ClassHolder cls, ClassHolderTransformerContext context) {
      ClassReader templateClass = context.getHierarchy().getClassSource().get(ReferenceQueueTemplate.class.getName());

      for (MethodReader method : templateClass.getMethods()) {
         if (!method.getName().equals("<init>")) {
            this.copyMethod(cls, method);
         }
      }

      for (FieldReader field : templateClass.getFields()) {
         cls.addField(ModelUtils.copyField(field));
      }
   }

   private void copyMethod(ClassHolder cls, MethodReader method) {
      MethodHolder targetMethod = cls.getMethod(method.getDescriptor());
      if (targetMethod == null) {
         targetMethod = new MethodHolder(method.getDescriptor());
         cls.addMethod(targetMethod);
         targetMethod.getModifiers().addAll(method.readModifiers());
         targetMethod.setLevel(method.getLevel());
      }

      Program targetProgram = ProgramUtils.copy(method.getProgram());
      targetMethod.setProgram(targetProgram);

      for (BasicBlock block : targetProgram.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof GetFieldInstruction getField) {
               getField.setField(this.mapField(getField.getField()));
            } else if (instruction instanceof PutFieldInstruction putField) {
               putField.setField(this.mapField(putField.getField()));
            }
         }
      }
   }

   private FieldReference mapField(FieldReference field) {
      return field.getClassName().equals(ReferenceQueueTemplate.class.getName())
         ? new FieldReference(ReferenceQueue.class.getName(), field.getFieldName())
         : field;
   }
}
