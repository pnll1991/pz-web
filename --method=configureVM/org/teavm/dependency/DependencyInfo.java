package org.teavm.dependency;

import java.util.Collection;
import org.teavm.callgraph.CallGraph;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodReference;

public interface DependencyInfo {
   ClassReaderSource getClassSource();

   ClassLoader getClassLoader();

   Collection<MethodReference> getReachableMethods();

   Collection<FieldReference> getReachableFields();

   Collection<String> getReachableClasses();

   FieldDependencyInfo getField(FieldReference var1);

   MethodDependencyInfo getMethod(MethodReference var1);

   MethodDependencyInfo getMethodImplementation(MethodReference var1);

   ClassDependencyInfo getClass(String var1);

   CallGraph getCallGraph();

   boolean isPrecise();
}
