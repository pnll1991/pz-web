package org.teavm.common;

public interface AsyncInputStream {
   Promise<Integer> skip(int var1);

   Promise<Integer> read(byte[] var1, int var2, int var3);
}
