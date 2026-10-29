package org.teavm.dependency;

public abstract class AbstractDependencyListener implements DependencyListener {
   @Override
   public void started(DependencyAgent agent) {
   }

   @Override
   public void classReached(DependencyAgent agent, String className) {
   }

   @Override
   public void methodReached(DependencyAgent agent, MethodDependency method) {
   }

   @Override
   public void fieldReached(DependencyAgent agent, FieldDependency field) {
   }

   @Override
   public void completing(DependencyAgent agent) {
   }

   @Override
   public void complete() {
   }
}
