package org.teavm.runtime.fs.memory;

import java.io.IOException;
import org.teavm.runtime.fs.VirtualFileAccessor;

public abstract class AbstractInMemoryVirtualFile {
   String name;
   InMemoryVirtualDirectory parent;
   long lastModified = System.currentTimeMillis();
   boolean readOnly;

   AbstractInMemoryVirtualFile(String name) {
      this.name = name;
   }

   public String getName() {
      return this.name;
   }

   public boolean delete() {
      if (this.parent != null && (!this.isDirectory() || this.listFiles().length <= 0)) {
         if (this.parent != null && !this.parent.canWrite()) {
            return false;
         }

         this.parent.children.remove(this.name);
         this.parent.modify();
         this.parent = null;
         return true;
      } else {
         return false;
      }
   }

   public abstract boolean isDirectory();

   public abstract boolean isFile();

   public abstract String[] listFiles();

   public boolean canRead() {
      return true;
   }

   public boolean canWrite() {
      return !this.readOnly;
   }

   public long lastModified() {
      return this.lastModified;
   }

   public boolean setLastModified(long lastModified) {
      if (this.readOnly) {
         return false;
      }

      this.lastModified = lastModified;
      return true;
   }

   public boolean setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
      return true;
   }

   void modify() {
      this.lastModified = System.currentTimeMillis();
   }

   public abstract AbstractInMemoryVirtualFile getChildFile(String var1);

   public abstract VirtualFileAccessor createAccessor(boolean var1, boolean var2, boolean var3);

   public abstract InMemoryVirtualFile createFile(String var1) throws IOException;

   public abstract InMemoryVirtualDirectory createDirectory(String var1);

   public abstract boolean adopt(AbstractInMemoryVirtualFile var1, String var2);

   public int length() {
      return 0;
   }
}
