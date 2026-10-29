package org.teavm.backend.wasm.transformation;

import java.lang.ref.ReferenceQueue;
import org.teavm.interop.Import;
import org.teavm.interop.Intrinsified;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReference;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

public class BaseClassesTransformation implements ClassHolderTransformer {
   private static final MethodReference GET_MONITOR = new MethodReference(Object.class.getName(), "getMonitor", ValueType.object("java.lang.Object$Monitor"));
   private static final MethodReference SET_MONITOR = new MethodReference(
      Object.class.getName(), "setMonitor", ValueType.object("java.lang.Object$Monitor"), ValueType.VOID
   );
   private static final FieldReference MONITOR = new FieldReference(Object.class.getName(), "monitor");

   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals("java.lang.Object")) {
         for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() != null) {
               this.transformMonitorFieldAccess(method.getProgram());
            }
         }

         MethodHolder getMonitorMethod = new MethodHolder(GET_MONITOR.getDescriptor());
         getMonitorMethod.setLevel(AccessLevel.PRIVATE);
         getMonitorMethod.getModifiers().add(ElementModifier.NATIVE);
         cls.addMethod(getMonitorMethod);
         MethodHolder setMonitorMethod = new MethodHolder(SET_MONITOR.getDescriptor());
         setMonitorMethod.setLevel(AccessLevel.PRIVATE);
         setMonitorMethod.getModifiers().add(ElementModifier.NATIVE);
         cls.addMethod(setMonitorMethod);
      } else if (cls.getName().equals("java.lang.System")) {
         Program arrayCopyProgram = null;
         MethodDescriptor arrayCopyDescriptor = null;

         for (MethodHolder method : cls.getMethods()) {
            switch (method.getName()) {
               case "arraycopy":
                  arrayCopyProgram = method.getProgram();
                  method.setProgram(null);
                  method.getModifiers().add(ElementModifier.NATIVE);
                  arrayCopyDescriptor = method.getDescriptor();
                  break;
               case "currentTimeMillis":
                  AnnotationHolder annotation = new AnnotationHolder(Import.class.getName());
                  annotation.getValues().put("module", new AnnotationValue("teavm"));
                  annotation.getValues().put("name", new AnnotationValue("currentTimeMillis"));
                  method.getAnnotations().add(annotation);
            }
         }

         if (arrayCopyProgram != null) {
            MethodHolder arrayCopyImpl = new MethodHolder(new MethodDescriptor("arrayCopyImpl", arrayCopyDescriptor.getSignature()));
            arrayCopyImpl.setProgram(arrayCopyProgram);
            arrayCopyImpl.getModifiers().add(ElementModifier.STATIC);
            cls.addMethod(arrayCopyImpl);
         }
      } else if (cls.getName().equals("java.lang.ref.WeakReference")) {
         MethodHolder constructor = cls.getMethod(new MethodDescriptor("<init>", Object.class, ReferenceQueue.class, void.class));
         constructor.getModifiers().add(ElementModifier.NATIVE);
         constructor.getAnnotations().add(new AnnotationHolder(Intrinsified.class.getName()));
         constructor.setProgram(null);
         MethodHolder get = cls.getMethod(new MethodDescriptor("get", Object.class));
         get.getModifiers().add(ElementModifier.NATIVE);
         get.getAnnotations().add(new AnnotationHolder(Intrinsified.class.getName()));
         get.setProgram(null);
         MethodHolder clear = cls.getMethod(new MethodDescriptor("clear", void.class));
         clear.getModifiers().add(ElementModifier.NATIVE);
         clear.getAnnotations().add(new AnnotationHolder(Intrinsified.class.getName()));
         clear.setProgram(null);
      } else if (cls.getName().equals("java.lang.Integer") || cls.getName().equals("java.lang.Long")) {
         for (MethodHolder method : cls.getMethods()) {
            switch (method.getName()) {
               case "numberOfLeadingZeros":
               case "numberOfTrailingZeros":
               case "bitCount":
                  method.setProgram(null);
                  method.getModifiers().add(ElementModifier.NATIVE);
                  method.getAnnotations().add(new AnnotationHolder(Intrinsified.class.getName()));
            }
         }
      }
   }

   private void transformMonitorFieldAccess(Program program) {
      for (BasicBlock block : program.getBasicBlocks()) {
         for (Instruction instruction : block) {
            if (instruction instanceof GetFieldInstruction getField) {
               if (getField.getField().equals(MONITOR)) {
                  InvokeInstruction invocation = new InvokeInstruction();
                  invocation.setType(InvocationType.SPECIAL);
                  invocation.setInstance(getField.getInstance());
                  invocation.setMethod(GET_MONITOR);
                  invocation.setReceiver(getField.getReceiver());
                  invocation.setLocation(getField.getLocation());
                  getField.replace(invocation);
               }
            } else if (instruction instanceof PutFieldInstruction putField && putField.getField().equals(MONITOR)) {
               InvokeInstruction invocation = new InvokeInstruction();
               invocation.setType(InvocationType.SPECIAL);
               invocation.setInstance(putField.getInstance());
               invocation.setMethod(SET_MONITOR);
               invocation.setArguments(putField.getValue());
               invocation.setLocation(putField.getLocation());
               putField.replace(invocation);
            }
         }
      }
   }
}
