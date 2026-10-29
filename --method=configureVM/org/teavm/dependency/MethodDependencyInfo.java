package org.teavm.dependency;

import org.teavm.model.MethodReference;

public interface MethodDependencyInfo {
   ValueDependencyInfo[] getVariables();

   int getVariableCount();

   ValueDependencyInfo getVariable(int var1);

   int getParameterCount();

   ValueDependencyInfo getResult();

   ValueDependencyInfo getThrown();

   MethodReference getReference();

   boolean isUsed();

   boolean isCalled();

   boolean isMissing();
}
