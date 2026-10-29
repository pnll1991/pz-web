package org.teavm.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ClassReader extends ElementReader {
   GenericTypeParameter[] getGenericParameters();

   String getParent();

   Set<String> getInterfaces();

   GenericValueType.Object getGenericParent();

   Set<GenericValueType.Object> getGenericInterfaces();

   MethodReader getMethod(MethodDescriptor var1);

   Collection<? extends MethodReader> getMethods();

   FieldReader getField(String var1);

   Collection<? extends FieldReader> getFields();

   String getOwnerName();

   String getSimpleName();

   String getDeclaringClassName();

   List<? extends String> getInnerClasses();
}
