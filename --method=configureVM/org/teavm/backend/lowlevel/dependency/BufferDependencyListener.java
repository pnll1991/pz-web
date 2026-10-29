package org.teavm.backend.lowlevel.dependency;

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.model.FieldReference;

public class BufferDependencyListener extends AbstractDependencyListener {
   @Override
   public void classReached(DependencyAgent agent, String className) {
      if (className.equals("java.nio.Buffer")) {
         agent.linkField(new FieldReference("java.nio.Buffer", "nextRef"));
      }
   }
}
