package org.teavm.runtime.fs;

import org.teavm.runtime.fs.memory.InMemoryVirtualFileSystem;

public final class VirtualFileSystemProvider {
   private static VirtualFileSystem instance;

   private VirtualFileSystemProvider() {
   }

   public static VirtualFileSystem getInstance() {
      if (instance == null) {
         instance = create();
      }

      return instance;
   }

   private static VirtualFileSystem create() {
      return new InMemoryVirtualFileSystem();
   }

   public static void setInstance(VirtualFileSystem instance) {
      VirtualFileSystemProvider.instance = instance;
   }
}
