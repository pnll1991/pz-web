package org.teavm.backend.wasm.transformation;

import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.runtime.Fiber;
import org.teavm.vm.TeaVM;

public class EntryPointTransformation implements ClassHolderTransformer {
   private static final MethodDescriptor MAIN_METHOD = new MethodDescriptor("main", String[].class, void.class);
   private String entryPoint;
   private String entryPointName;

   public void setEntryPoint(String entryPoint) {
      this.entryPoint = entryPoint;
   }

   public void setEntryPointName(String entryPointName) {
      this.entryPointName = entryPointName;
   }

   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(this.entryPoint)) {
         MethodReader mainMethod = this.getMainMethod(cls);
         if (mainMethod != null) {
            this.generateMainMethodCaller(mainMethod, cls, context);
         }
      } else if (cls.getName().equals(Fiber.class.getName())) {
         MethodHolder setCurrentThread = cls.getMethod(new MethodDescriptor("setCurrentThread", Thread.class, void.class));
         setCurrentThread.getModifiers().remove(ElementModifier.NATIVE);
         ProgramEmitter pe = ProgramEmitter.create(setCurrentThread, context.getHierarchy());
         pe.invoke(Thread.class, "setCurrentThread", pe.var(1, Thread.class));
         pe.exit();
         MethodReader mainMethod = this.getMainMethod(context.getHierarchy().getClassSource().get(this.entryPoint));
         if (mainMethod == null) {
            return;
         }

         MethodHolder runMain = cls.getMethod(new MethodDescriptor("runMain", String[].class, void.class));
         runMain.getModifiers().remove(ElementModifier.NATIVE);
         pe = ProgramEmitter.create(runMain, context.getHierarchy());
         ValueEmitter[] args = mainMethod.parameterCount() == 1 ? new ValueEmitter[]{pe.var(1, String[].class)} : new ValueEmitter[0];
         if (mainMethod.hasModifier(ElementModifier.STATIC)) {
            pe.invoke(new MethodReference(this.entryPoint, MAIN_METHOD), args);
         } else {
            pe.construct(this.entryPoint).invokeSpecial("main", args);
         }

         pe.exit();
      }
   }

   private void generateMainMethodCaller(MethodReader mainMethod, ClassHolder cls, ClassHolderTransformerContext context) {
      MethodHolder mainMethodCaller = new MethodHolder(mainMethod.getName() + "_$caller", mainMethod.getSignature());
      mainMethodCaller.setLevel(AccessLevel.PUBLIC);
      mainMethodCaller.getModifiers().add(ElementModifier.STATIC);
      cls.addMethod(mainMethodCaller);
      mainMethodCaller.getAnnotations().add(new AnnotationHolder("org.teavm.jso.JSExport"));
      AnnotationHolder methodAnnot = new AnnotationHolder("org.teavm.jso.JSMethod");
      methodAnnot.getValues().put("value", new AnnotationValue(this.entryPointName));
      mainMethodCaller.getAnnotations().add(methodAnnot);
      ProgramEmitter pe = ProgramEmitter.create(mainMethodCaller, context.getHierarchy());
      ValueEmitter arg = mainMethod.parameterCount() == 1 ? pe.var(1, String[].class) : pe.constantNull(String[].class);
      pe.invoke(Fiber.class, "startMain", arg);
      pe.exit();
   }

   private MethodReader getMainMethod(ClassReader cls) {
      MethodReader mainMethod = cls.getMethod(TeaVM.MAIN_METHOD_DESC);
      return mainMethod != null ? mainMethod : cls.getMethod(TeaVM.SHORT_MAIN_METHOD_DESC);
   }
}
