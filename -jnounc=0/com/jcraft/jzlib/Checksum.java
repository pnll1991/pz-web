package com.jcraft.jzlib;

interface Checksum {
   void update(byte[] var1, int var2, int var3);

   void reset();

   void reset(long var1);

   long getValue();

   Checksum copy();
}
