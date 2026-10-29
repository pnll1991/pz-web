package org.teavm.runtime.fs.memory;

import java.io.IOException;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;

public class VirtualFileImpl implements VirtualFile {
   private InMemoryVirtualFileSystem fs;
   private String path;

   public VirtualFileImpl(InMemoryVirtualFileSystem fs, String path) {
      this.fs = fs;
      this.path = path;
   }

   @Override
   public String getName() {
      return this.path.substring(this.path.lastIndexOf(47) + 1);
   }

   @Override
   public boolean isDirectory() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.isDirectory();
   }

   @Override
   public boolean isFile() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.isFile();
   }

   @Override
   public String[] listFiles() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null ? inMemory.listFiles() : null;
   }

   @Override
   public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null ? inMemory.createAccessor(readable, writable, append) : null;
   }

   @Override
   public boolean createFile(String fileName) throws IOException {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      if (inMemory == null) {
         throw new IOException("Directory does not exist");
      } else {
         return inMemory.createFile(fileName) != null;
      }
   }

   @Override
   public boolean createDirectory(String fileName) {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.createDirectory(fileName) != null;
   }

   @Override
   public boolean delete() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.delete();
   }

   @Override
   public boolean adopt(VirtualFile file, String fileName) {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      if (inMemory == null) {
         return false;
      }

      AbstractInMemoryVirtualFile fileInMemory = ((VirtualFileImpl)file).findInMemory();
      return fileInMemory == null ? false : inMemory.adopt(fileInMemory, fileName);
   }

   @Override
   public boolean canRead() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.canRead();
   }

   @Override
   public boolean canWrite() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.canWrite();
   }

   @Override
   public long lastModified() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null ? inMemory.lastModified() : 0L;
   }

   @Override
   public boolean setLastModified(long lastModified) {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.setLastModified(lastModified);
   }

   @Override
   public boolean setReadOnly(boolean readOnly) {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null && inMemory.setReadOnly(readOnly);
   }

   @Override
   public int length() {
      AbstractInMemoryVirtualFile inMemory = this.findInMemory();
      return inMemory != null ? inMemory.length() : 0;
   }

   AbstractInMemoryVirtualFile findInMemory() {
      AbstractInMemoryVirtualFile file = this.fs.root;
      int i = 0;
      if (this.path.startsWith("/")) {
         i++;
      }

      while (i < this.path.length()) {
         int next = this.path.indexOf(47, i);
         if (next < 0) {
            next = this.path.length();
         }

         if (!file.isDirectory()) {
            return null;
         }

         file = file.getChildFile(this.path.substring(i, next));
         if (file == null) {
            break;
         }

         i = next + 1;
      }

      return file;
   }
}
