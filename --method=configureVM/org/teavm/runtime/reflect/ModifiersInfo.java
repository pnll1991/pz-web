package org.teavm.runtime.reflect;

public final class ModifiersInfo {
   public static final int PUBLIC = 1;
   public static final int PRIVATE = 2;
   public static final int PROTECTED = 4;
   public static final int STATIC = 8;
   public static final int FINAL = 16;
   public static final int SYNCHRONIZED = 32;
   public static final int VOLATILE = 64;
   public static final int TRANSIENT = 128;
   public static final int NATIVE = 256;
   public static final int INTERFACE = 512;
   public static final int ABSTRACT = 1024;
   public static final int STRICT = 2048;
   public static final int JVM_FLAGS_MASK = 4095;
   public static final int VARARGS = 4096;
   public static final int ANNOTATION = 8192;
   public static final int INHERITED_ANNOTATION = 16384;
   public static final int SYNTHETIC = 32768;
   public static final int ENUM = 65536;
   public static final int BRIDGE = 131072;
   public static final int PROXY = 262144;

   private ModifiersInfo() {
   }
}
