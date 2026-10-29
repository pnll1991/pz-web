package org.teavm.backend.c.runtime.fs;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.teavm.backend.c.intrinsic.RuntimeInclude;
import org.teavm.backend.c.runtime.Memory;
import org.teavm.backend.c.runtime.StringList;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileSystem;

public class CFileSystem implements VirtualFileSystem {
   private Map<String, CFileSystem.Entry> cache = new HashMap<>();
   private ReferenceQueue<? super CVirtualFile> referenceQueue = new ReferenceQueue<>();

   @Override
   public String getUserDir() {
      Address resultPtr = Memory.malloc(Address.sizeOf());
      int length = workDirectory(resultPtr);
      Address result = resultPtr.getAddress();
      Memory.free(resultPtr);
      int realLength = length > 0 ? length : -length - 1;
      char[] chars = new char[realLength];
      Memory.memcpy(Address.ofData(chars), result, chars.length * 2);
      Memory.free(result);
      String s = new String(chars);
      if (length < 0) {
         throw new RuntimeException(s);
      } else {
         return s;
      }
   }

   @Override
   public VirtualFile getFile(String path) {
      return this.getByPath(path);
   }

   CVirtualFile getByPath(String path) {
      CFileSystem.Entry entry = this.cache.get(path);
      if (entry == null || entry.get() == null) {
         entry = new CFileSystem.Entry(new CVirtualFile(this, path), this.referenceQueue);
         this.cache.put(path, entry);
      }

      while (true) {
         CFileSystem.Entry staleEntry = (CFileSystem.Entry)this.referenceQueue.poll();
         if (staleEntry == null) {
            return entry.get();
         }

         if (!staleEntry.path.equals(path)) {
            this.cache.remove(staleEntry.path);
         }
      }
   }

   @Override
   public boolean isWindows() {
      return isWindowsNative();
   }

   @Override
   public boolean isCaseSensitive() {
      return isCaseSensitiveNative();
   }

   @Override
   public String canonicalize(String path) {
      if (!this.isWindows()) {
         return path;
      }

      char[] pathChars = path.toCharArray();
      Address resultPtr = Memory.malloc(Address.sizeOf());
      int resultSize = canonicalizeNative(pathChars, pathChars.length, resultPtr);
      Address result = resultPtr.getAddress();
      Memory.free(resultPtr);
      if (resultSize < 0) {
         return path;
      }

      char[] chars = new char[resultSize];
      Memory.memcpy(Address.ofData(chars), result, chars.length * 2);
      Memory.free(result);
      return new String(chars);
   }

   @Override
   public String[] getRoots() {
      return this.isWindows() ? new String[]{"C:"} : new String[]{""};
   }

   @Import(name = "teavm_file_homeDirectory")
   @Unmanaged
   @RuntimeInclude("file.h")
   public static native int homeDirectory(Address var0);

   @Import(name = "teavm_file_tempDirectory")
   @Unmanaged
   @RuntimeInclude("file.h")
   public static native int tempDirectory(Address var0);

   @Import(name = "teavm_file_workDirectory")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int workDirectory(Address var0);

   @Import(name = "teavm_file_isDir")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean isDir(char[] var0, int var1);

   @Import(name = "teavm_file_isFile")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean isFile(char[] var0, int var1);

   @Import(name = "teavm_file_exists")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean exists(char[] var0, int var1);

   @Import(name = "teavm_file_canRead")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean canRead(char[] var0, int var1);

   @Import(name = "teavm_file_canWrite")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean canWrite(char[] var0, int var1);

   @Import(name = "teavm_file_setReadonly")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean setReadonly(char[] var0, int var1, boolean var2);

   @Import(name = "teavm_file_listFiles")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native StringList listFiles(char[] var0, int var1);

   @Import(name = "teavm_file_createDirectory")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean createDirectory(char[] var0, int var1);

   @Import(name = "teavm_file_createFile")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int createFile(char[] var0, int var1);

   @Import(name = "teavm_file_delete")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean delete(char[] var0, int var1);

   @Import(name = "teavm_file_rename")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean rename(char[] var0, int var1, char[] var2, int var3);

   @Import(name = "teavm_file_length")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int length(char[] var0, int var1);

   @Import(name = "teavm_file_lastModified")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native long lastModified(char[] var0, int var1);

   @Import(name = "teavm_file_setLastModified")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean setLastModified(char[] var0, int var1, long var2);

   @Import(name = "teavm_file_open")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native long open(char[] var0, int var1, int var2);

   @Import(name = "teavm_file_close")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean close(long var0);

   @Import(name = "teavm_file_flush")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean flush(long var0);

   @Import(name = "teavm_file_seek")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean seek(long var0, int var2, int var3);

   @Import(name = "teavm_file_tell")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int tell(long var0);

   @Import(name = "teavm_file_read")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int read(long var0, byte[] var2, int var3, int var4);

   @Import(name = "teavm_file_write")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int write(long var0, byte[] var2, int var3, int var4);

   @Import(name = "teavm_file_truncate")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean truncate(long var0, int var2);

   @Import(name = "teavm_file_isWindows")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean isWindowsNative();

   @Import(name = "teavm_file_isCaseSensitive")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native boolean isCaseSensitiveNative();

   @Import(name = "teavm_file_canonicalize")
   @Unmanaged
   @RuntimeInclude("file.h")
   static native int canonicalizeNative(char[] var0, int var1, Address var2);

   static class Entry extends WeakReference<CVirtualFile> {
      String path;

      Entry(CVirtualFile referent, ReferenceQueue<? super CVirtualFile> q) {
         super(referent, q);
         this.path = referent.path;
      }
   }
}
