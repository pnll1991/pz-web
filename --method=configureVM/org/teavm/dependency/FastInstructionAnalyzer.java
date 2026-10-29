package org.teavm.dependency;

import java.util.List;
import org.teavm.model.AccessLevel;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.VariableReader;

class FastInstructionAnalyzer extends AbstractInstructionAnalyzer {
   private FastDependencyAnalyzer dependencyAnalyzer;
   private CallLocation impreciseLocation;

   FastInstructionAnalyzer(FastDependencyAnalyzer dependencyAnalyzer) {
      this.dependencyAnalyzer = dependencyAnalyzer;
   }

   @Override
   protected void invokeSpecial(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments) {
      if (instance != null) {
         this.invokeGetClass(method);
      }

      CallLocation callLocation = this.impreciseLocation;
      if (instance == null) {
         this.dependencyAnalyzer.linkClass(method.getClassName()).initClass(callLocation);
      }

      MethodDependency methodDep = this.dependencyAnalyzer.linkMethod(method);
      methodDep.addLocation(callLocation);
      methodDep.use(false);
   }

   @Override
   protected void invokeVirtual(VariableReader receiver, VariableReader instance, MethodReference method, List<? extends VariableReader> arguments) {
      ClassReader cls = this.dependencyAnalyzer.getClassSource().get(method.getClassName());
      if (cls != null) {
         MethodReader methodHolder = cls.getMethod(method.getDescriptor());
         if (methodHolder != null && methodHolder.getLevel() == AccessLevel.PRIVATE) {
            this.invokeSpecial(receiver, instance, method, arguments);
            return;
         }
      }

      this.invokeGetClass(method);
      FastVirtualCallConsumer consumer = this.dependencyAnalyzer.getVirtualCallConsumer(method);
      consumer.addLocation(this.impreciseLocation);
   }

   private void invokeGetClass(MethodReference method) {
      if (method.getName().equals("getClass") && method.parameterCount() == 0 && method.getReturnType().isObject(Class.class)) {
         this.dependencyAnalyzer.instancesNode.connect(this.dependencyAnalyzer.classesNode);
      }
   }

   @Override
   public void cloneArray(VariableReader receiver, VariableReader array) {
      MethodDependency cloneDep = this.getAnalyzer().linkMethod(CLONE_METHOD);
      cloneDep.addLocation(this.impreciseLocation);
      cloneDep.use();
   }

   @Override
   protected DependencyNode getNode(VariableReader variable) {
      return this.dependencyAnalyzer.instancesNode;
   }

   @Override
   protected DependencyAnalyzer getAnalyzer() {
      return this.dependencyAnalyzer;
   }

   @Override
   protected CallLocation getCallLocation() {
      return this.impreciseLocation;
   }

   @Override
   public void setCaller(MethodReference caller) {
      super.setCaller(caller);
      this.impreciseLocation = new CallLocation(caller);
   }
}
