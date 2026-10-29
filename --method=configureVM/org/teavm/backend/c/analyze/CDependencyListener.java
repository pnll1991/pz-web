package org.teavm.backend.c.analyze;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.interop.DelegateTo;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;

public class CDependencyListener extends AbstractDependencyListener implements ClassHolderTransformer {
   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
      AnnotationReader delegateAnnot = method.getMethod().getAnnotations().get(DelegateTo.class.getName());
      if (delegateAnnot != null) {
         String delegateMethodName = delegateAnnot.getValue("value").getString();
         ClassReader cls = agent.getClassSource().get(method.getReference().getClassName());

         for (MethodReader delegate : cls.getMethods()) {
            if (delegate.getName().equals(delegateMethodName) && delegate != method.getMethod()) {
               MethodDependency delegateDep = agent.linkMethod(delegate.getReference());
               method.addLocationListener(delegateDep::addLocation);
               delegateDep.use();
            }
         }
      }
   }

   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      for (MethodHolder method : cls.getMethods()) {
         AnnotationReader delegateAnnot = method.getAnnotations().get(DelegateTo.class.getName());
         if (delegateAnnot != null) {
            method.setProgram(null);
            method.getModifiers().add(ElementModifier.NATIVE);
         }
      }
   }
}
