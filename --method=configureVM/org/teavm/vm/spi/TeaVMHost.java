package org.teavm.vm.spi;

import java.util.Properties;
import java.util.function.Predicate;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.BootstrapMethodSubstitutor;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.DependencyPlugin;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.MethodReference;
import org.teavm.parsing.resource.ResourceProvider;

public interface TeaVMHost extends ServiceRepository {
   void add(DependencyListener var1);

   void add(ClassHolderTransformer var1);

   void add(MethodReference var1, BootstrapMethodSubstitutor var2);

   void add(MethodReference var1, DependencyPlugin var2);

   void addVirtualMethods(Predicate<MethodReference> var1);

   void addClassFilter(ClassFilter var1);

   <T extends TeaVMHostExtension> T getExtension(Class<T> var1);

   <T> void registerService(Class<T> var1, T var2);

   ClassLoader getClassLoader();

   ResourceProvider getResourceProvider();

   Properties getProperties();

   String[] getPlatformTags();
}
