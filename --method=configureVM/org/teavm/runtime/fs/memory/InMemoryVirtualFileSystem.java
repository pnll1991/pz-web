package org.teavm.runtime.fs.memory;

import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileSystem;

public class InMemoryVirtualFileSystem implements VirtualFileSystem {
   InMemoryVirtualDirectory root = new InMemoryVirtualDirectory("");
   private String userDir = "/";

   @Override
   public VirtualFile getFile(String path) {
      return new VirtualFileImpl(this, path);
   }

   @Override
   public String getUserDir() {
      return this.userDir;
   }

   public void setUserDir(String userDir) {
      this.userDir = userDir;
   }

   @Override
   public boolean isWindows() {
      return false;
   }

   @Override
   public String canonicalize(String path) {
      return path;
   }

   @Override
   public String[] getRoots() {
      return new String[]{""};
   }
}
