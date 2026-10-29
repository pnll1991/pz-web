package org.teavm.vm.spi;

import org.teavm.backend.javascript.rendering.RenderingManager;
import org.teavm.vm.BuildTarget;

public interface RendererListener {
   void begin(RenderingManager var1, BuildTarget var2);

   void complete();
}
