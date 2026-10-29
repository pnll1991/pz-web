package org.teavm.debugging.information;

import org.teavm.model.MethodReference;

public interface DeferredCallSite {
   void setVirtualMethod(MethodReference var1);

   void setStaticMethod(MethodReference var1);

   void clean();
}
