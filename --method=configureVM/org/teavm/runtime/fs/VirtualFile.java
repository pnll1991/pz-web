package org.teavm.runtime.fs;

import java.io.IOException;

public interface VirtualFile {
   String getName();

   boolean isDirectory();

   boolean isFile();

   default boolean exists() {
      return this.isDirectory() || this.isFile();
   }

   String[] listFiles();

   VirtualFileAccessor createAccessor(boolean var1, boolean var2, boolean var3);

   boolean createFile(String var1) throws IOException;

   boolean createDirectory(String var1);

   boolean delete();

   boolean adopt(VirtualFile var1, String var2);

   boolean canRead();

   boolean canWrite();

   long lastModified();

   boolean setLastModified(long var1);

   boolean setReadOnly(boolean var1);

   int length();
}
