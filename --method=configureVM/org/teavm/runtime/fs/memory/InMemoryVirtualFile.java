package org.teavm.runtime.fs.memory;

import java.io.IOException;
import java.util.Arrays;
import org.teavm.runtime.fs.VirtualFileAccessor;

public class InMemoryVirtualFile extends AbstractInMemoryVirtualFile {
   byte[] data = new byte[0];
   int size;

   InMemoryVirtualFile(String name) {
      super(name);
   }

   @Override
   public boolean isDirectory() {
      return false;
   }

   @Override
   public boolean isFile() {
      return true;
   }

   @Override
   public String[] listFiles() {
      return null;
   }

   @Override
   public AbstractInMemoryVirtualFile getChildFile(String fileName) {
      throw new UnsupportedOperationException();
   }

   @Override
   public VirtualFileAccessor createAccessor(boolean readable, final boolean writable, final boolean append) {
      if (this.parent == null) {
         return null;
      } else {
         return writable && this.readOnly ? null : new VirtualFileAccessor() {
            private int pos;

            {
               if (append) {
                  this.pos = InMemoryVirtualFile.this.size;
               } else if (writable) {
                  InMemoryVirtualFile.this.size = 0;
               }
            }

            @Override
            public int read(byte[] buffer, int offset, int limit) {
               limit = Math.max(0, Math.min(InMemoryVirtualFile.this.size - this.pos, limit));
               if (limit > 0) {
                  System.arraycopy(InMemoryVirtualFile.this.data, this.pos, buffer, offset, limit);
                  this.pos += limit;
               }

               return limit;
            }

            @Override
            public void write(byte[] buffer, int offset, int limit) {
               InMemoryVirtualFile.this.expandData(this.pos + limit);
               System.arraycopy(buffer, offset, InMemoryVirtualFile.this.data, this.pos, limit);
               this.pos += limit;
               if (this.pos > InMemoryVirtualFile.this.size) {
                  InMemoryVirtualFile.this.size = this.pos;
               }

               InMemoryVirtualFile.this.modify();
            }

            @Override
            public int tell() throws IOException {
               return this.pos;
            }

            @Override
            public void seek(int target) throws IOException {
               this.pos = target;
            }

            @Override
            public void skip(int amount) throws IOException {
               this.pos += amount;
            }

            @Override
            public int size() {
               return InMemoryVirtualFile.this.size;
            }

            @Override
            public void resize(int size) {
               InMemoryVirtualFile.this.expandData(size);
               InMemoryVirtualFile.this.size = size;
               InMemoryVirtualFile.this.modify();
            }

            @Override
            public void close() {
            }

            @Override
            public void flush() {
            }
         };
      }
   }

   @Override
   public InMemoryVirtualFile createFile(String fileName) throws IOException {
      throw new IOException("Can't create file " + fileName + " since parent path denotes regular file");
   }

   @Override
   public InMemoryVirtualDirectory createDirectory(String fileName) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean adopt(AbstractInMemoryVirtualFile file, String fileName) {
      return false;
   }

   @Override
   public int length() {
      return this.size;
   }

   private void expandData(int newSize) {
      if (newSize > this.data.length) {
         int newCapacity = Math.max(newSize, this.data.length) * 3 / 2;
         this.data = Arrays.copyOf(this.data, newCapacity);
      }
   }
}
