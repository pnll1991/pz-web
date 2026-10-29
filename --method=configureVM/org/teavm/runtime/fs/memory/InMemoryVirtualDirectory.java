package org.teavm.runtime.fs.memory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.runtime.fs.VirtualFileAccessor;

public class InMemoryVirtualDirectory extends AbstractInMemoryVirtualFile {
   final Map<String, AbstractInMemoryVirtualFile> children = new LinkedHashMap<>();

   InMemoryVirtualDirectory(String name) {
      super(name);
   }

   @Override
   public boolean isDirectory() {
      return true;
   }

   @Override
   public boolean isFile() {
      return false;
   }

   @Override
   public String[] listFiles() {
      return this.children.keySet().toArray(new String[0]);
   }

   @Override
   public AbstractInMemoryVirtualFile getChildFile(String fileName) {
      if (fileName.equals(".")) {
         return this;
      } else {
         return fileName.equals("..") ? this.parent : this.children.get(fileName);
      }
   }

   @Override
   public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
      return null;
   }

   @Override
   public InMemoryVirtualFile createFile(String fileName) throws IOException {
      if (!this.canWrite()) {
         throw new IOException("Directory is read-only");
      }

      if (this.children.containsKey(fileName)) {
         return null;
      }

      InMemoryVirtualFile file = new InMemoryVirtualFile(fileName);
      this.adoptFile(file);
      return file;
   }

   @Override
   public InMemoryVirtualDirectory createDirectory(String fileName) {
      if (this.canWrite() && this.getChildFile(fileName) == null) {
         InMemoryVirtualDirectory file = new InMemoryVirtualDirectory(fileName);
         this.adoptFile(file);
         return file;
      } else {
         return null;
      }
   }

   @Override
   public boolean adopt(AbstractInMemoryVirtualFile file, String fileName) {
      if (!this.canWrite()) {
         return false;
      }

      if (!file.parent.canWrite()) {
         return false;
      }

      file.parent.children.remove(file.name);
      file.parent = this;
      this.children.put(fileName, file);
      file.name = fileName;
      return true;
   }

   @Override
   public int length() {
      return 0;
   }

   private void adoptFile(AbstractInMemoryVirtualFile file) {
      if (this.children.containsKey(file.name)) {
         throw new IllegalArgumentException("File " + file.getName() + " already exists");
      }

      file.parent = this;
      this.children.put(file.name, file);
      this.modify();
   }
}
