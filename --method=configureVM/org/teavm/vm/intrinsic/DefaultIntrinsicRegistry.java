package org.teavm.vm.intrinsic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.interop.Intrinsified;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;

public class DefaultIntrinsicRegistry<I> implements IntrinsicRegistry<I>, IntrinsicProvider<I> {
   private Map<MethodReference, DefaultIntrinsicRegistry.Holder<I>> intrinsics = new HashMap<>();
   private Map<String, DefaultIntrinsicRegistry.ClassScopedIntrinsic<I>> intrinsicsByClass = new HashMap<>();
   private List<IntrinsicProvider<I>> providedIntrinsics = new ArrayList<>();
   private ClassReaderSource classes;

   public DefaultIntrinsicRegistry(ClassReaderSource classes) {
      this.classes = classes;
   }

   @Override
   public void registerIntrinsic(MethodReference method, I intrinsic) {
      this.intrinsics.put(method, new DefaultIntrinsicRegistry.Holder<>(intrinsic));
   }

   @Override
   public void registerIntrinsic(String className, I intrinsic, String... methods) {
      this.intrinsicsByClass.put(className, new DefaultIntrinsicRegistry.ClassScopedIntrinsic<>(intrinsic, Set.of(methods)));
   }

   @Override
   public void registerIntrinsic(IntrinsicProvider<I> provider) {
      this.providedIntrinsics.add(provider);
   }

   @Override
   public I getIntrinsic(MethodReference method) {
      DefaultIntrinsicRegistry.Holder<I> holder = this.intrinsics.computeIfAbsent(method, m -> new DefaultIntrinsicRegistry.Holder<>(this.resolveIntrinsic(m)));
      return holder.value;
   }

   private I resolveIntrinsic(MethodReference method) {
      DefaultIntrinsicRegistry.ClassScopedIntrinsic<I> classIntrinsic = this.intrinsicsByClass.get(method.getClassName());
      if (classIntrinsic != null) {
         MethodReader resolvedMethod = this.classes.resolve(method);
         if (resolvedMethod != null
            && resolvedMethod.hasModifier(ElementModifier.NATIVE)
            && (resolvedMethod.getAnnotations().get(Intrinsified.class.getName()) != null || classIntrinsic.methodNames.contains(resolvedMethod.getName()))) {
            return classIntrinsic.intrinsic;
         }
      }

      for (IntrinsicProvider<I> provider : this.providedIntrinsics) {
         I intrinsic = provider.getIntrinsic(method);
         if (intrinsic != null) {
            return intrinsic;
         }
      }

      return null;
   }

   private static class ClassScopedIntrinsic<I> {
      I intrinsic;
      Set<String> methodNames;

      ClassScopedIntrinsic(I intrinsic, Set<String> methodNames) {
         this.intrinsic = intrinsic;
         this.methodNames = methodNames;
      }
   }

   private static class Holder<T> {
      T value;

      Holder(T value) {
         this.value = value;
      }
   }
}
