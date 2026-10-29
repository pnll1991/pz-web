package org.teavm.classlib;

import java.util.Collection;
import java.util.Collections;
import org.teavm.model.MethodDescriptor;

@Deprecated
public interface ReflectionSupplier {
   default Collection<String> getAccessibleFields(ReflectionContext context, String className) {
      return Collections.emptyList();
   }

   default Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
      return Collections.emptyList();
   }

   default boolean isClassFoundByName(ReflectionContext context, String name) {
      return false;
   }

   default ProxyListener getProxyInterfaces(ReflectionContext context, ProxyInterfaceConsumer consumer) {
      return null;
   }
}
