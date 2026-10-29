package org.teavm.model;

import org.teavm.cache.IncrementalDependencyRegistration;
import org.teavm.diagnostics.Diagnostics;

public interface ClassHolderTransformerContext {
   ClassHierarchy getHierarchy();

   Diagnostics getDiagnostics();

   IncrementalDependencyRegistration getIncrementalCache();

   boolean isObfuscated();

   boolean isStrict();

   String getEntryPoint();

   void submit(ClassHolder var1);
}
