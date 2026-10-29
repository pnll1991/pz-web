package org.teavm.vm.intrinsic;

import org.teavm.model.MethodReference;

public interface IntrinsicRegistry<I> {
   void registerIntrinsic(MethodReference var1, I var2);

   void registerIntrinsic(String var1, I var2, String... var3);

   default void registerIntrinsic(Class<?> cls, I intrinsic, String... methods) {
      this.registerIntrinsic(cls.getName(), intrinsic, methods);
   }

   void registerIntrinsic(IntrinsicProvider<I> var1);
}
