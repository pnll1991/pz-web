package org.teavm.backend.javascript.spi;

import java.util.Properties;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.parsing.resource.ResourceProvider;

public interface GeneratorContext extends ServiceRepository {
   String getParameterName(int var1);

   String importModule(String var1);

   ClassReaderSource getInitialClassSource();

   ResourceProvider getResourceProvider();

   ListableClassReaderSource getClassSource();

   ClassLoader getClassLoader();

   Properties getProperties();

   boolean isAsync();

   boolean isAsync(MethodReference var1);

   Diagnostics getDiagnostics();

   DependencyInfo getDependency();

   int lookupString(String var1);

   boolean isDynamicInitializer(String var1);
}
