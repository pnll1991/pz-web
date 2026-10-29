package org.teavm.vm;

import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import org.teavm.cache.CacheStatus;
import org.teavm.common.ServiceRepository;
import org.teavm.dependency.DependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.extension.ExtensionEnvironment;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.parsing.resource.ResourceProvider;

public interface TeaVMTargetController {
   boolean wasCancelled();

   ClassLoader getClassLoader();

   ClassReaderSource getUnprocessedClassSource();

   ResourceProvider getResourceProvider();

   CacheStatus getCacheStatus();

   DependencyInfo getDependencyInfo();

   Diagnostics getDiagnostics();

   Properties getProperties();

   ServiceRepository getServices();

   TeaVMOptimizationLevel getOptimizationLevel();

   boolean isFriendlyToDebugger();

   String getEntryPoint();

   String getEntryPointName();

   Set<? extends String> getPreservedClasses();

   boolean isVirtual(MethodReference var1);

   TeaVMProgressFeedback reportProgress(int var1);

   void addVirtualMethods(Predicate<MethodReference> var1);

   ClassInitializerInfo getClassInitializerInfo();

   ExtensionEnvironment extensionEnvironment();
}
