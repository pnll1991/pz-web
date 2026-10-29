package org.teavm.model.optimization;

import org.teavm.dependency.DependencyInfo;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;

public interface MethodOptimizationContext {
   MethodReader getMethod();

   DependencyInfo getDependencyInfo();

   ClassReaderSource getClassSource();

   ClassHierarchy getHierarchy();
}
