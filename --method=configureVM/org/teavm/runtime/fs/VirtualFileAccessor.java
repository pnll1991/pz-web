package org.teavm.runtime.fs;

import java.io.IOException;

public interface VirtualFileAccessor {
   int read(byte[] var1, int var2, int var3) throws IOException;

   void write(byte[] var1, int var2, int var3) throws IOException;

   int tell() throws IOException;

   void seek(int var1) throws IOException;

   void skip(int var1) throws IOException;

   int size() throws IOException;

   void resize(int var1) throws IOException;

   void close() throws IOException;

   void flush() throws IOException;
}
