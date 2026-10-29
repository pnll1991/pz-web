package org.teavm.reflection;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.runtime.reflect.ProxyImplementor;

class ProxyDependencySupport {
   private ReflectionDependencyListener reflection;
   private DependencyAgent agent;
   private Map<MethodReference, MethodReference> implementedMethods = new HashMap<>();
   private Map<MethodReference, MethodReference> methodProviders = new HashMap<>();
   private Set<MethodReference> usedMethods = new LinkedHashSet<>();
   private Set<String> generatedClasses = new HashSet<>();
   private MethodDependency getDeclaredMethod;

   ProxyDependencySupport(ReflectionDependencyListener reflection, DependencyAgent agent) {
      this.reflection = reflection;
      this.agent = agent;
      this.getDeclaredMethod = agent.linkMethod(new MethodReference(Class.class, "getDeclaredMethod", String.class, Class[].class, Method.class));
      this.getDeclaredMethod.getVariable(0).propagate(agent.getType(ValueType.parse(Class.class)));
      this.getDeclaredMethod.getVariable(1).propagate(agent.getType(ValueType.parse(String.class)));
      this.getDeclaredMethod.getVariable(2).propagate(agent.getType(ValueType.parse(Class[].class)));
      this.getDeclaredMethod.getVariable(2).getArrayItem().propagate(agent.getType(ValueType.parse(Class.class)));
      this.getDeclaredMethod.use();
   }

   boolean isGeneratedClass(String className) {
      return this.generatedClasses.contains(className);
   }

   Set<MethodReference> getUsedMethods() {
      return this.usedMethods;
   }

   MethodReference getWorkerAcquireMethod(MethodReference method) {
      return this.methodProviders.get(method);
   }

   void onMethodAdded(MethodDependency method) {
      MethodReference implementedMethod = this.implementedMethods.get(method.getReference());
      if (implementedMethod != null) {
         this.agent.linkMethod(implementedMethod);
         this.reflection.addReflectableMethod(this.agent, this.agent.getClassSource().getMethod(implementedMethod));
         this.reflection.addAccessibleMethod(implementedMethod);

         for (ValueType param : implementedMethod.getParameterTypes()) {
            this.getDeclaredMethod.getVariable(2).getArrayItem().getClassValueNode().propagate(this.agent.getType(param));
         }
      }

      if (this.generatedClasses.contains(method.getReference().getClassName())
         && method.getMethod().hasModifier(ElementModifier.STATIC)
         && method.getMethod().getName().startsWith("_acquireMethod_")) {
         method.getResult().propagate(this.agent.getType(ValueType.parse(Method.class)));
      }
   }

   void generateProxyClass(List<? extends String> interfaces, String className) {
      this.generatedClasses.add(className);
      ClassHolder cls = new ClassHolder(className);
      cls.setParent(Proxy.class.getName());
      cls.getInterfaces().addAll(interfaces);
      cls.setLevel(AccessLevel.PUBLIC);
      cls.getModifiers().add(ElementModifier.FINAL);
      cls.getAnnotations().add(new AnnotationHolder(ProxyImplementor.class.getName()));
      this.generateConstructor(cls);
      HashSet<ProxyDependencySupport.Signature> generatedMethods = new HashSet<>();
      generatedMethods.add(new ProxyDependencySupport.Signature("equals", ValueType.parse(Object.class)));
      generatedMethods.add(new ProxyDependencySupport.Signature("hashCode"));
      generatedMethods.add(new ProxyDependencySupport.Signature("toString"));
      this.generateWorkerMethod(new MethodReference(Object.class, "equals", Object.class, boolean.class), cls, 0);
      this.generateWorkerMethod(new MethodReference(Object.class, "hashCode", int.class), cls, 1);
      this.generateWorkerMethod(new MethodReference(Object.class, "toString", String.class), cls, 2);
      this.getDeclaredMethod.getVariable(0).getClassValueNode().propagate(this.agent.getType(ValueType.object("java.lang.Object")));
      ProxyDependencySupport.MethodIdHolder methodIdHolder = new ProxyDependencySupport.MethodIdHolder();
      methodIdHolder.id = 3;
      HashSet<String> visited = new HashSet<>();

      for (String itfName : interfaces) {
         this.generateWorkersForInterface(itfName, cls, generatedMethods, methodIdHolder, visited);
      }

      this.agent.submitClass(cls);
      this.agent.linkClass(className);
      this.agent.linkMethod(new MethodReference(className, "<init>", ValueType.VOID)).propagate(0, this.agent.getType(ValueType.object(className))).use();
      this.agent
         .linkMethod(new MethodReference(Proxy.class, "wrapDependency", Object.class, Object.class))
         .getResult()
         .propagate(this.agent.getType(ValueType.object(className)));
   }

   private void generateWorkersForInterface(
      String itfName,
      ClassHolder cls,
      Set<ProxyDependencySupport.Signature> generatedMethods,
      ProxyDependencySupport.MethodIdHolder methodIdHolder,
      Set<String> visited
   ) {
      if (visited.add(itfName)) {
         ClassReader itf = this.agent.getClassSource().get(itfName);

         for (MethodReader method : itf.getMethods()) {
            if (!method.hasModifier(ElementModifier.BRIDGE)) {
               ProxyDependencySupport.Signature signature = new ProxyDependencySupport.Signature(method.getName(), method.getDescriptor().getParameterTypes());
               if (generatedMethods.add(signature)) {
                  this.generateWorkerMethod(method, cls, methodIdHolder.id++);
                  this.getDeclaredMethod.getVariable(0).getClassValueNode().propagate(this.agent.getType(ValueType.object(itfName)));
               }
            }
         }

         for (String parent : itf.getInterfaces()) {
            this.generateWorkersForInterface(parent, cls, generatedMethods, methodIdHolder, visited);
         }
      }
   }

   private void generateConstructor(ClassHolder cls) {
      MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
      ctor.setLevel(AccessLevel.PUBLIC);
      ProgramEmitter pe = ProgramEmitter.create(ctor, this.agent.getClassHierarchy());
      ValueEmitter thisVar = pe.var(0, ValueType.object(cls.getName()));
      thisVar.invokeSpecial(Proxy.class, "<init>", thisVar.cast(Proxy.class));
      pe.exit();
   }

   private void generateWorkerMethod(MethodReference originalMethod, ClassHolder cls, int id) {
      this.generateWorkerMethod(this.agent.getClassSource().getMethod(originalMethod), cls, id);
   }

   private void generateWorkerMethod(MethodReader originalMethod, ClassHolder cls, int id) {
      MethodHolder acquireMethod = new MethodHolder(new MethodDescriptor("_acquireMethod_" + id, Method.class));
      acquireMethod.setLevel(AccessLevel.PRIVATE);
      acquireMethod.getModifiers().addAll(List.of(ElementModifier.NATIVE, ElementModifier.STATIC));
      cls.addMethod(acquireMethod);
      this.methodProviders.put(acquireMethod.getReference(), originalMethod.getReference());
      this.usedMethods.add(acquireMethod.getReference());
      MethodHolder method = new MethodHolder(originalMethod.getDescriptor());
      this.implementedMethods.put(new MethodReference(cls.getName(), originalMethod.getDescriptor()), originalMethod.getReference());
      ProgramEmitter pe = ProgramEmitter.create(method, this.agent.getClassHierarchy());
      ValueEmitter thisVar = pe.var(0, ValueType.object(cls.getName()));
      ValueEmitter methodVar = pe.invoke(acquireMethod.getReference());
      ValueEmitter argsVar;
      if (originalMethod.parameterCount() == 0) {
         argsVar = pe.constantNull(Object[].class);
      } else {
         argsVar = pe.constructArray(Object.class, originalMethod.parameterCount());

         for (int i = 0; i < originalMethod.parameterCount(); i++) {
            ValueEmitter arg = pe.var(i + 1, originalMethod.parameterType(i));
            argsVar.setElement(i, this.boxIfNecessary(pe, arg));
         }
      }

      ValueEmitter result = thisVar.getField("h", InvocationHandler.class)
         .invokeVirtual("invoke", Object.class, thisVar.cast(Object.class), methodVar, argsVar);
      if (method.getResultType() == ValueType.VOID) {
         pe.exit();
      } else {
         this.unboxIfNecessary(result, method.getResultType()).returnValue();
      }

      BasicBlock mainBlock = pe.getBlock();
      BasicBlock passThoughBlock = pe.prepareBlock();
      ValueEmitter exVar = pe.newVar(Throwable.class);

      for (String exception : originalMethod.getThrownTypes()) {
         TryCatchBlock tryCatch = new TryCatchBlock();
         tryCatch.setExceptionType(exception);
         tryCatch.setHandler(passThoughBlock);
         mainBlock.getTryCatchBlocks().add(tryCatch);
      }

      TryCatchBlock tryCatch = new TryCatchBlock();
      tryCatch.setHandler(passThoughBlock);
      tryCatch.setExceptionType(RuntimeException.class.getName());
      mainBlock.getTryCatchBlocks().add(tryCatch);
      passThoughBlock.setExceptionVariable(exVar.getVariable());
      pe.enter(passThoughBlock);
      RaiseInstruction throwInsn = new RaiseInstruction();
      throwInsn.setException(exVar.getVariable());
      pe.addInstruction(throwInsn);
      BasicBlock wrapBlock = pe.prepareBlock();
      TryCatchBlock wrapTryCatch = new TryCatchBlock();
      wrapTryCatch.setHandler(wrapBlock);
      wrapTryCatch.setExceptionType(Exception.class.getName());
      mainBlock.getTryCatchBlocks().add(wrapTryCatch);
      exVar = pe.newVar(Exception.class);
      wrapBlock.setExceptionVariable(exVar.getVariable());
      pe.enter(wrapBlock);
      ValueEmitter wrapperVar = pe.construct(UndeclaredThrowableException.class, exVar.cast(Throwable.class));
      throwInsn = new RaiseInstruction();
      throwInsn.setException(wrapperVar.getVariable());
      pe.addInstruction(throwInsn);
      cls.addMethod(method);
   }

   private ValueEmitter boxIfNecessary(ProgramEmitter pe, ValueEmitter value) {
      if (value.getType() instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)value.getType()).getKind()) {
            case BOOLEAN:
               return pe.invoke(Boolean.class, "valueOf", Boolean.class, value);
            case CHARACTER:
               return pe.invoke(Character.class, "valueOf", Character.class, value);
            case BYTE:
               return pe.invoke(Byte.class, "valueOf", Byte.class, value);
            case SHORT:
               return pe.invoke(Short.class, "valueOf", Short.class, value);
            case INTEGER:
               return pe.invoke(Integer.class, "valueOf", Integer.class, value);
            case LONG:
               return pe.invoke(Long.class, "valueOf", Long.class, value);
            case FLOAT:
               return pe.invoke(Float.class, "valueOf", Float.class, value);
            case DOUBLE:
               return pe.invoke(Double.class, "valueOf", Double.class, value);
         }
      }

      return value.cast(Object.class);
   }

   private ValueEmitter unboxIfNecessary(ValueEmitter value, ValueType type) {
      if (type instanceof ValueType.Primitive) {
         switch (((ValueType.Primitive)type).getKind()) {
            case BOOLEAN:
               return value.cast(Boolean.class).invokeSpecial("booleanValue", boolean.class);
            case CHARACTER:
               return value.cast(Character.class).invokeSpecial("charValue", char.class);
            case BYTE:
               return value.cast(Byte.class).invokeSpecial("byteValue", byte.class);
            case SHORT:
               return value.cast(Short.class).invokeSpecial("shortValue", short.class);
            case INTEGER:
               return value.cast(Integer.class).invokeSpecial("intValue", int.class);
            case LONG:
               return value.cast(Long.class).invokeSpecial("longValue", long.class);
            case FLOAT:
               return value.cast(Float.class).invokeSpecial("floatValue", float.class);
            case DOUBLE:
               return value.cast(Double.class).invokeSpecial("doubleValue", double.class);
         }
      }

      return value.cast(type);
   }

   private static class MethodIdHolder {
      int id;
   }

   private static final class Signature {
      String name;
      ValueType[] args;

      Signature(String name, ValueType... args) {
         this.name = name;
         this.args = args;
      }

      @Override
      public boolean equals(Object object) {
         if (this == object) {
            return true;
         } else {
            return !(object instanceof ProxyDependencySupport.Signature signature)
               ? false
               : Objects.equals(this.name, signature.name) && Objects.deepEquals(this.args, signature.args);
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.name, Arrays.hashCode(this.args));
      }
   }
}
