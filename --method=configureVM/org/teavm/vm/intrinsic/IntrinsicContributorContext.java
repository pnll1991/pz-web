package org.teavm.vm.intrinsic;

import java.util.Properties;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.parsing.resource.ResourceProvider;

public interface IntrinsicContributorContext {
   ClassHierarchy hierarchy();

   ListableClassReaderSource classes();

   ClassLoader classLoader();

   ResourceProvider resources();

   String entryPoint();

   Diagnostics diagnostics();

   ClassInitializerInfo classInitInfo();

   DependencyInfo dependency();

   boolean isAsyncMethod(MethodReference var1);

   Properties properties();
}
