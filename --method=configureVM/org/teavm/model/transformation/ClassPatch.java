package org.teavm.model.transformation;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

public class ClassPatch implements ClassHolderTransformer {
   private FieldReference platformClassField = new FieldReference("java.lang.Class", "platformClass");

   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals("java.lang.Class")) {
         for (MethodHolder method : cls.getMethods()) {
            this.patchProgram(method.getProgram());
         }
      }
   }

   private void patchProgram(Program program) {
      if (program != null) {
         for (int i = 0; i < program.basicBlockCount(); i++) {
            for (Instruction instruction : program.basicBlockAt(i)) {
               if (instruction instanceof GetFieldInstruction getField) {
                  if (getField.getField().equals(this.platformClassField)) {
                     AssignInstruction replacement = new AssignInstruction();
                     replacement.setReceiver(getField.getReceiver());
                     replacement.setAssignee(getField.getInstance());
                     replacement.setLocation(instruction.getLocation());
                     instruction.replace(replacement);
                  }
               } else if (instruction instanceof PutFieldInstruction putField && putField.getField().equals(this.platformClassField)) {
                  EmptyInstruction replacement = new EmptyInstruction();
                  replacement.setLocation(instruction.getLocation());
                  instruction.replace(replacement);
               }
            }
         }
      }
   }
}
