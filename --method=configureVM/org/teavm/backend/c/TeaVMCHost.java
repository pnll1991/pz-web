package org.teavm.backend.c;

import org.teavm.backend.c.generators.GeneratorFactory;
import org.teavm.backend.c.intrinsic.IntrinsicFactory;
import org.teavm.vm.spi.TeaVMHostExtension;

public interface TeaVMCHost extends TeaVMHostExtension {
   void addIntrinsic(IntrinsicFactory var1);

   void addGenerator(GeneratorFactory var1);
}
