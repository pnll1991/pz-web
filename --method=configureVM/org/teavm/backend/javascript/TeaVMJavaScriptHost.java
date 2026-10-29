package org.teavm.backend.javascript;

import java.util.function.Function;
import org.teavm.backend.javascript.spi.Generator;
import org.teavm.backend.javascript.spi.Injector;
import org.teavm.backend.javascript.spi.MethodContributor;
import org.teavm.model.MethodReference;
import org.teavm.vm.spi.RendererListener;
import org.teavm.vm.spi.TeaVMHostExtension;

public interface TeaVMJavaScriptHost extends TeaVMHostExtension {
   void add(MethodReference var1, Generator var2);

   void add(MethodReference var1, Injector var2);

   void addGeneratorProvider(Function<ProviderContext, Generator> var1);

   void addInjectorProvider(Function<ProviderContext, Injector> var1);

   void add(RendererListener var1);

   void addVirtualMethods(MethodContributor var1);

   void addForcedFunctionMethods(MethodContributor var1);
}
