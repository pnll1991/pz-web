package org.teavm.dependency;

public interface DependencyListener {
   void started(DependencyAgent var1);

   void classReached(DependencyAgent var1, String var2);

   void methodReached(DependencyAgent var1, MethodDependency var2);

   void fieldReached(DependencyAgent var1, FieldDependency var2);

   void completing(DependencyAgent var1);

   void complete();
}
