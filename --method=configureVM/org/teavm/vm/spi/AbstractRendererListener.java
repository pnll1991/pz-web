package org.teavm.vm.spi;

import org.teavm.backend.javascript.rendering.RenderingManager;
import org.teavm.vm.BuildTarget;

public abstract class AbstractRendererListener implements RendererListener {
   @Override
   public void begin(RenderingManager manager, BuildTarget buildTarget) {
   }

   @Override
   public void complete() {
   }
}
