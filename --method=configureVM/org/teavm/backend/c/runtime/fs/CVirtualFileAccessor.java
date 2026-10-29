package org.teavm.backend.c.runtime.fs;

import java.io.IOException;
import org.teavm.runtime.fs.VirtualFileAccessor;

public class CVirtualFileAccessor implements VirtualFileAccessor {
   private long file;

   public CVirtualFileAccessor(long file) {
      this.file = file;
   }

   @Override
   public int read(byte[] buffer, int offset, int limit) throws IOException {
      return CFileSystem.read(this.file, buffer, offset, limit);
   }

   @Override
   public void write(byte[] buffer, int offset, int limit) throws IOException {
      int bytesWritten = CFileSystem.write(this.file, buffer, offset, limit);
      if (bytesWritten < limit) {
         throw new IOException();
      }
   }

   @Override
   public int tell() throws IOException {
      return CFileSystem.tell(this.file);
   }

   @Override
   public void skip(int amount) throws IOException {
      CFileSystem.seek(this.file, 0, amount);
   }

   @Override
   public void seek(int target) throws IOException {
      CFileSystem.seek(this.file, 0, target);
   }

   @Override
   public int size() throws IOException {
      int current = CFileSystem.tell(this.file);
      if (!CFileSystem.seek(this.file, 2, 0)) {
         throw new IOException();
      } else {
         int result = CFileSystem.tell(this.file);
         if (!CFileSystem.seek(this.file, 0, current)) {
            throw new IOException();
         } else {
            return result;
         }
      }
   }

   @Override
   public void resize(int size) throws IOException {
      if (!CFileSystem.seek(this.file, 2, 0)) {
         throw new IOException();
      }

      int position = CFileSystem.tell(this.file);
      if (position < size) {
         byte[] zeros = new byte[4096];

         while (position < size) {
            int bytesToWrite = Math.min(zeros.length, size - position);
            this.write(zeros, 0, bytesToWrite);
            position += bytesToWrite;
         }
      } else if (!CFileSystem.truncate(this.file, size)) {
         throw new IOException();
      }
   }

   @Override
   public void close() throws IOException {
      long file = this.file;
      this.file = 0L;
      if (!CFileSystem.close(file)) {
         throw new IOException();
      }
   }

   @Override
   public void flush() throws IOException {
      if (!CFileSystem.flush(this.file)) {
         throw new IOException();
      }
   }
}
