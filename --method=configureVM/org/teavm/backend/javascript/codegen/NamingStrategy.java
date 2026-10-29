package org.teavm.backend.javascript.codegen;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public interface NamingStrategy {
   ScopedName className(String var1);

   String instanceMethodName(MethodDescriptor var1);

   ScopedName initializerName(MethodReference var1);

   ScopedName methodName(MethodReference var1);

   String instanceFieldName(FieldReference var1);

   ScopedName fieldName(FieldReference var1);

   ScopedName functionName(String var1);

   ScopedName classInitializerName(String var1);

   String additionalScopeName();

   void reserveName(String var1);
}
