package org.teavm.runtime.fs;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;

public abstract class VirtualFileSystemProviderTransformer implements ClassHolderTransformer {
   @Override
   public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
      if (cls.getName().equals(VirtualFileSystemProvider.class.getName())) {
         MethodHolder method = cls.getMethod(new MethodDescriptor("create", VirtualFileSystem.class));
         this.transformCreateMethod(method, context);
      }
   }

   protected abstract void transformCreateMethod(MethodHolder var1, ClassHolderTransformerContext var2);
}
