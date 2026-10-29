package org.teavm.backend.c.intrinsic.reflection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.model.MethodReference;

public class ProxyIntrinsicContext {
   private Map<MethodReference, Integer> methodIndices = new LinkedHashMap<>();
   private int indexCounter;

   public int getMethodIndex(MethodReference methodRef) {
      return this.methodIndices.computeIfAbsent(methodRef, ref -> this.indexCounter++);
   }

   public Map<MethodReference, Integer> getMethodIndices() {
      return Collections.unmodifiableMap(this.methodIndices);
   }

   public int getMethodCount() {
      return this.indexCounter;
   }
}
