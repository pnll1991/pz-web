package org.teavm.backend.c.transform;

import org.teavm.backend.c.runtime.fs.CFileSystem;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodHolder;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.runtime.fs.VirtualFileSystemProviderTransformer;

public class CFileSystemTransformer extends VirtualFileSystemProviderTransformer {
   @Override
   protected void transformCreateMethod(MethodHolder method, ClassHolderTransformerContext context) {
      ProgramEmitter pe = ProgramEmitter.create(method, context.getHierarchy());
      pe.construct(CFileSystem.class).returnValue();
   }
}
