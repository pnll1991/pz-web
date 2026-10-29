package org.teavm.backend.lowlevel.generate;

import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public interface NameProvider {
   String forMethod(MethodReference var1);

   String forVirtualMethod(MethodDescriptor var1);

   String forStaticField(FieldReference var1);

   String forMemberField(FieldReference var1);

   String forClass(String var1);

   String forClassInitializer(String var1);

   String forClassSystemInitializer(ValueType var1);

   String forClassClass(String var1);

   String forClassInstance(ValueType var1);

   String forSupertypeFunction(ValueType var1);

   String createTopLevelName(String var1);
}
