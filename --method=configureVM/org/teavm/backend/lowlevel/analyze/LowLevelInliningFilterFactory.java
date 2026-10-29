package org.teavm.backend.lowlevel.analyze;

import org.teavm.model.MethodReference;
import org.teavm.model.lowlevel.Characteristics;
import org.teavm.model.optimization.InliningFilter;
import org.teavm.model.optimization.InliningFilterFactory;

public class LowLevelInliningFilterFactory implements InliningFilterFactory {
   private Characteristics characteristics;

   public LowLevelInliningFilterFactory(Characteristics characteristics) {
      this.characteristics = characteristics;
   }

   @Override
   public InliningFilter createFilter(MethodReference methodReference) {
      return this.characteristics.isManaged(methodReference) ? this.characteristics::isManaged : m -> false;
   }
}
