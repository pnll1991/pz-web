package org.teavm.dependency;

import java.util.Properties;
import java.util.function.Supplier;
import org.teavm.common.ServiceRepository;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ReferenceCache;
import org.teavm.parsing.resource.ResourceProvider;

public interface DependencyAnalyzerFactory {
   DependencyAnalyzer create(
      ClassReaderSource var1,
      ResourceProvider var2,
      ClassLoader var3,
      ServiceRepository var4,
      Diagnostics var5,
      ReferenceCache var6,
      String[] var7,
      Supplier<Properties> var8
   );
}
