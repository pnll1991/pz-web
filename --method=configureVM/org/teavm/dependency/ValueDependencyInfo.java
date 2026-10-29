package org.teavm.dependency;

import org.teavm.model.ValueType;

public interface ValueDependencyInfo {
   ValueType[] getTypes();

   boolean hasType(ValueType var1);

   boolean hasMoreTypesThan(int var1);

   boolean hasArrayType();

   ValueDependencyInfo getArrayItem();

   ValueDependencyInfo getClassValueNode();
}
