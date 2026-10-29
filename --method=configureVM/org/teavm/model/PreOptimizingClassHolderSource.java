package org.teavm.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.model.optimization.GlobalValueNumbering;
import org.teavm.model.optimization.UnusedVariableElimination;
import org.teavm.model.transformation.NoSuchFieldCatchElimination;

public class PreOptimizingClassHolderSource implements ClassHolderSource {
   private ClassHolderSource innerClassSource;
   private Map<String, ClassHolder> cache = new LinkedHashMap<>();

   public PreOptimizingClassHolderSource(ClassHolderSource innerClassSource) {
      this.innerClassSource = innerClassSource;
   }

   @Override
   public ClassHolder get(String name) {
      ClassHolder cls = this.cache.get(name);
      if (cls == null) {
         cls = optimize(this.innerClassSource, name);
         if (cls == null) {
            return null;
         }

         this.cache.put(name, cls);
      }

      return cls;
   }

   public static ClassHolder optimize(ClassHolderSource innerSource, String name) {
      ClassHolder cls = innerSource.get(name);
      if (cls == null) {
         return cls;
      }

      NoSuchFieldCatchElimination noSuchFieldCatchElimination = new NoSuchFieldCatchElimination();

      for (MethodHolder method : cls.getMethods()) {
         if (method.getProgram() != null) {
            noSuchFieldCatchElimination.apply(method.getProgram());
            new GlobalValueNumbering(true).optimize(method.getProgram());
            new UnusedVariableElimination().optimize(method, method.getProgram());
         }
      }

      return cls;
   }
}
