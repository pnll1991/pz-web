package org.teavm.backend.wasm.dependencies;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.interop.Export;
import org.teavm.model.AnnotationReader;
import org.teavm.model.MethodReader;

public class WasmGCExportDependencyListener extends AbstractDependencyListener {
   @Override
   public void classReached(DependencyAgent agent, String className) {
      for (MethodReader reader : agent.getClassSource().get(className).getMethods()) {
         AnnotationReader annotation = reader.getAnnotations().get(Export.class.getName());
         if (annotation != null) {
            agent.linkMethod(reader.getReference()).use();
         }
      }
   }
}
