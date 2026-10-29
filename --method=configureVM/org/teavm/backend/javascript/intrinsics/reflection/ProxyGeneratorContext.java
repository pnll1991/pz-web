package org.teavm.backend.javascript.intrinsics.reflection;

import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.model.MethodReference;

public class ProxyGeneratorContext {
   private Map<MethodReference, String> properties = new HashMap<>();
   private int indexGen;

   public String getPropertyName(MethodReference methodRef) {
      return this.properties.computeIfAbsent(methodRef, ref -> {
         String result;
         do {
            result = RenderingUtil.indexToId(this.indexGen++);
         } while (RenderingUtil.KEYWORDS.contains(result));

         return result;
      });
   }
}
