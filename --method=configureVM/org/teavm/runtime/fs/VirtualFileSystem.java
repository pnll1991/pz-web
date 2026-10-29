package org.teavm.runtime.fs;

public interface VirtualFileSystem {
   String getUserDir();

   VirtualFile getFile(String var1);

   boolean isWindows();

   default boolean isCaseSensitive() {
      return true;
   }

   String canonicalize(String var1);

   String[] getRoots();
}
