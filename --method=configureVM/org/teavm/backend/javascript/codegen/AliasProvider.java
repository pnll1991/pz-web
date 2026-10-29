package org.teavm.backend.javascript.codegen;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public interface AliasProvider {
   String getFieldAlias(FieldReference var1);

   ScopedName getStaticFieldAlias(FieldReference var1);

   ScopedName getStaticMethodAlias(MethodReference var1);

   String getMethodAlias(MethodDescriptor var1);

   ScopedName getClassAlias(String var1);

   ScopedName getFunctionAlias(String var1);

   ScopedName getClassInitAlias(String var1);

   String getAdditionalScopeName();

   void reserveName(String var1);
}
